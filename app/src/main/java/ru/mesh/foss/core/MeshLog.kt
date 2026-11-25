package ru.mesh.foss.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Общий журнал приложения (а не только экрана «Москвёнок»).
 *
 * Ядро не зависит от Android: куда писать (файл + logcat), решает Android-обёртка
 * [ru.mesh.foss.data.MeshLogStore], которая выставляет [sink] и [enabled].
 * Каждая строка проходит через [Sensitive.scrub], поэтому токены, cookie и ПИИ
 * в журнал не попадают. Когда журнал выключен, вызовы почти бесплатны.
 */
object MeshLog {

    @Volatile
    var enabled: Boolean = false

    /** Куда отдавать готовые строки (файл + logcat). */
    @Volatile
    var sink: ((String) -> Unit)? = null

    /**
     * Отдельный канал для ошибок. Вызывается даже при выключенном локальном
     * журнале: аналитика должна знать об ошибках, но не зависит от файла лога.
     */
    @Volatile
    var errorSink: ((String, Throwable) -> Unit)? = null

    private val stamp = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    fun log(tag: String, message: String) {
        if (!enabled) return
        val time = runCatching { stamp.get()?.format(Date()) }.getOrNull().orEmpty()
        val line = if (time.isEmpty()) "[$tag] $message" else "$time [$tag] $message"
        runCatching { sink?.invoke(Sensitive.scrub(line)) }
    }

    /** Ленивая запись: строка собирается, только если журнал включён. */
    inline fun trace(tag: String, build: () -> String) {
        if (enabled) log(tag, build())
    }

    fun error(tag: String, error: Throwable) {
        // Аналитика ошибок не зависит от локального журнала и его настройки.
        runCatching { errorSink?.invoke(tag, error) }
        runCatching {
            log(tag, "error: ${error.javaClass.name}: ${error.message}")
            error.stackTrace?.take(8)?.forEach { frame ->
                log(tag, "  at ${frame.className}.${frame.methodName}:${frame.lineNumber}")
            }
            var cause = error.cause
            var depth = 0
            while (cause != null && depth < 4) {
                log(tag, "caused by: ${cause.javaClass.name}: ${cause.message}")
                cause = cause.cause
                depth++
            }
        }
    }
}
