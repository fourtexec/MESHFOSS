package ru.mesh.foss.data.analytics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.mesh.foss.core.analytics.AnalyticsEvent
import ru.mesh.foss.core.analytics.AnalyticsPayload
import ru.mesh.foss.core.analytics.AnalyticsSanitizer
import ru.mesh.foss.core.analytics.AnalyticsTime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Оркестратор аналитики: установка/обновление/heartbeat + ошибки.
 *
 * Все данные проходят [AnalyticsSanitizer]. Очередь и id установки лежат в
 * noBackupFilesDir. Сеть трогаем только в фоне, не чаще 5 запросов в минуту.
 *
 * Install/update/error не подавляются 24-часовым окном. Heartbeat `app_active`
 * добавляется отдельно, если с прошлого heartbeat прошло не меньше суток.
 */
class AnalyticsReporter(
    private val identity: AnalyticsIdentity,
    private val stateStore: AnalyticsStateStore,
    private val queue: AnalyticsQueue,
    private val transport: AnalyticsTransport?,
    private val app: String,
    private val version: String,
    private val device: String,
    private val os: String,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val heartbeatIntervalMs: Long = 24 * 60 * 60 * 1_000L,
    private val minRequestIntervalMs: Long = 12_000L,
    private val errorDebounceMs: Long = 2_000L,
    private val autoFlushOnError: Boolean = true,
    private val maxRequestsPerRun: Int = 5,
) {
    private val mutex = Mutex()
    private val scheduled = AtomicBoolean(false)
    private val dedupeLock = Any()
    private val dedupe = mutableMapOf<String, Long>()
    private val dedupeWindowMs = 10 * 60 * 1_000L

    @Volatile
    private var cachedUuid: String? = null

    /** Вызывается при открытии UI: ставит install/update и, отдельно, heartbeat. */
    fun reportLaunch() {
        scope.launch { launchNow() }
    }

    suspend fun launchNow() {
        if (transport == null) return
        mutex.withLock {
            val now = clock()
            val identityValue = runCatching { identity.get() }.getOrNull() ?: return@withLock
            cachedUuid = identityValue.uuid
            var state = stateStore.load()
            if (identityValue.created && state.lastVersion != null) {
                // Файл id потерян/повреждён, но состояние осталось. Начинаем
                // установку заново, иначе можно смешать события разных id.
                state = AnalyticsState()
            }

            val launchEvents = mutableListOf<AnalyticsEvent>()
            var stateChanged = false

            if (state.lastVersion == null) {
                event(
                    type = AnalyticsEvent.INSTALL,
                    text = "app installed",
                    level = "info",
                )?.let {
                    launchEvents += it
                    state = state.copy(lastVersion = version)
                    stateChanged = true
                }
            } else if (state.lastVersion != version) {
                event(
                    type = AnalyticsEvent.UPDATE,
                    text = "app updated from ${state.lastVersion} to $version",
                    level = "info",
                )?.let {
                    launchEvents += it
                    state = state.copy(lastVersion = version)
                    stateChanged = true
                }
            }

            // Heartbeat не заменяет install/update, а идёт в дополнение к ним.
            if (now - state.lastActiveAt >= heartbeatIntervalMs) {
                event(
                    type = AnalyticsEvent.ACTIVE,
                    text = "client alive",
                    level = "info",
                )?.let {
                    launchEvents += it
                    state = state.copy(lastActiveAt = now)
                    stateChanged = true
                }
            }

            if (stateChanged) {
                launchEvents.forEach(queue::append)
                stateStore.save(state)
            }
            flushLocked()
        }
    }

    /**
     * Синхронно кладём ошибку в очередь: если процесс сейчас упадёт, событие
     * всё равно уйдёт при следующем запуске. Сеть и дедупликация — в фоне.
     */
    fun recordError(tag: String, error: Throwable) {
        if (transport == null) return
        val now = clock()
        val fingerprint = AnalyticsSanitizer.fingerprint(tag, error)
        if (!shouldReport(fingerprint, now)) return
        val event = buildErrorEvent(tag, error) ?: return
        queue.append(event)
        scheduleFlush()
    }

    suspend fun flushNow() {
        if (transport == null) return
        mutex.withLock { flushLocked() }
    }

    private suspend fun flushLocked() {
        val client = transport ?: return
        var state = stateStore.load()
        if (clock() < state.blockedUntil) return

        val pending = queue.load()
        if (pending.isEmpty()) return
        val chunks = AnalyticsPayload.chunk(pending).toMutableList()
        var index = 0
        var requests = 0

        while (index < chunks.size && requests < maxRequestsPerRun) {
            val chunk = chunks[index]
            val delayMs = state.lastRequestAt + minRequestIntervalMs - clock()
            if (delayMs > 0) delay(delayMs)

            val sentAt = clock()
            state = state.copy(lastRequestAt = sentAt)
            stateStore.save(state)

            when (val result = client.post(AnalyticsPayload.body(chunk))) {
                AnalyticsResult.Success -> {
                    queue.remove(chunk)
                    state = state.copy(blockedUntil = 0L)
                    stateStore.save(state)
                    requests++
                    index++
                }

                AnalyticsResult.TooLarge -> {
                    requests++
                    if (chunk.size > 1) {
                        // 413: режем пачку пополам и пробуем снова.
                        val middle = chunk.size / 2
                        chunks[index] = chunk.take(middle)
                        chunks.add(index + 1, chunk.drop(middle))
                    } else {
                        queue.remove(chunk)
                        index++
                    }
                }

                is AnalyticsResult.Retry -> {
                    state = state.copy(
                        blockedUntil = sentAt + result.afterMs.coerceAtLeast(1_000L)
                    )
                    stateStore.save(state)
                    return
                }

                is AnalyticsResult.Fatal -> {
                    // Это backoff, а не суточный фильтр событий: install,
                    // update и ошибки уже лежат в очереди и уйдут после паузы.
                    val block = when (result.status) {
                        401, 403, 415 -> 60_000L
                        else -> 5 * 60_000L
                    }
                    state = state.copy(blockedUntil = sentAt + block)
                    stateStore.save(state)
                    return
                }
            }
        }

        // Если пачек больше лимита на один запуск, остаток уйдёт следующим
        // запуском/ошибкой; очередь на диске уже сохранена.
    }

    private fun scheduleFlush() {
        if (!autoFlushOnError) return
        if (!scheduled.compareAndSet(false, true)) return
        scope.launch {
            try {
                delay(errorDebounceMs)
                flushNow()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Аналитика не должна влиять на приложение.
            } finally {
                scheduled.set(false)
            }
        }
    }

    private fun shouldReport(fingerprint: String, now: Long): Boolean {
        synchronized(dedupeLock) {
            val last = dedupe[fingerprint]
            if (last != null && now - last < dedupeWindowMs) return false
            dedupe[fingerprint] = now
            if (dedupe.size > 500) {
                val cutoff = now - dedupeWindowMs
                val iterator = dedupe.entries.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().value < cutoff) iterator.remove()
                }
            }
            return true
        }
    }

    private fun buildErrorEvent(tag: String, error: Throwable): AnalyticsEvent? {
        val message = error.message?.takeIf { it.isNotBlank() }
        val text = if (message == null) {
            "[$tag] ${error.javaClass.simpleName}"
        } else {
            "[$tag] ${error.javaClass.simpleName}: $message"
        }
        return event(
            type = AnalyticsEvent.ERROR,
            text = text,
            level = "error",
            tag = tag,
            errorType = error.javaClass.name,
            stack = stackOf(error),
        )
    }

    private fun stackOf(error: Throwable): String {
        val builder = StringBuilder()
        builder.append(error.javaClass.name)
        error.message?.takeIf { it.isNotBlank() }?.let { builder.append(": ").append(it) }
        builder.append('\n')
        error.stackTrace?.take(12)?.forEach { frame ->
            builder.append("  at ").append(frame.toString()).append('\n')
        }
        var cause = error.cause
        var depth = 0
        while (cause != null && depth < 4) {
            builder.append("caused by: ").append(cause.javaClass.name)
            cause.message?.takeIf { it.isNotBlank() }?.let { builder.append(": ").append(it) }
            builder.append('\n')
            cause = cause.cause
            depth++
        }
        return builder.toString()
    }

    private fun event(
        type: String,
        text: String,
        level: String,
        tag: String? = null,
        errorType: String? = null,
        stack: String? = null,
    ): AnalyticsEvent? {
        val id = currentUuid() ?: return null
        val safeText = AnalyticsSanitizer.event(text).ifBlank { type }
        return AnalyticsEvent(
            event = safeText,
            timestamp = AnalyticsTime.isoUtc(clock()),
            app = AnalyticsSanitizer.event(app, 80),
            version = AnalyticsSanitizer.event(version, 40),
            device = AnalyticsSanitizer.event(device, 80),
            level = level,
            eventType = type,
            uuid = id,
            tag = tag?.let { AnalyticsSanitizer.event(it, 64) },
            errorType = errorType?.let { AnalyticsSanitizer.event(it, 120) },
            stack = stack?.let { AnalyticsSanitizer.stack(it) },
            os = AnalyticsSanitizer.event(os, 80),
        )
    }

    private fun currentUuid(): String? {
        cachedUuid?.let { return it }
        val value = runCatching { identity.get().uuid }.getOrNull() ?: return null
        if (!AnalyticsIdentity.isUuid(value)) return null
        cachedUuid = value
        return value
    }
}
