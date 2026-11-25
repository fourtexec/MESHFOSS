package ru.mesh.foss.data.analytics

import android.content.Context
import android.os.Build
import ru.mesh.foss.BuildConfig
import ru.mesh.foss.core.MeshLog
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Android-обёртка аналитики.
 *
 * Ключ и версия приходят из BuildConfig. Ключ задаётся GitHub secret
 * ANALYTICS_API_KEY и в репозиторий не попадает. Если ключа нет (например,
 * локальная сборка), отправка полностью отключается.
 */
object Analytics {

    private const val ENDPOINT = "https://analytics.4mes.dpdns.org/"

    @Volatile
    private var reporter: AnalyticsReporter? = null

    fun init(context: Context) {
        if (reporter != null) return
        val app = context.applicationContext
        val key = BuildConfig.ANALYTICS_API_KEY.trim()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val instance = AnalyticsReporter(
            identity = AnalyticsIdentity(File(app.noBackupFilesDir, "analytics_id")),
            stateStore = AnalyticsStateStore(File(app.noBackupFilesDir, "analytics_state.json")),
            queue = AnalyticsQueue(File(app.noBackupFilesDir, "analytics_queue.jsonl")),
            transport = if (key.isBlank()) null else RetrofitAnalyticsTransport(ENDPOINT, key),
            app = BuildConfig.APPLICATION_ID,
            version = BuildConfig.VERSION_NAME,
            device = deviceName(),
            os = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            scope = scope,
        )
        reporter = instance
        MeshLog.errorSink = { tag, error -> instance.recordError(tag, error) }
        installCrashHandler(instance)
    }

    /** Вызывать при открытии UI, а не из фонового worker'а. */
    fun reportLaunch() {
        reporter?.reportLaunch()
    }

    fun recordError(tag: String, error: Throwable) {
        reporter?.recordError(tag, error)
    }

    private fun installCrashHandler(instance: AnalyticsReporter) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Ошибка попадёт в очередь на диске и уйдёт при следующем запуске.
            runCatching { instance.recordError("CRASH", error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    private fun deviceName(): String {
        val raw = listOf(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return raw.ifBlank { "Android" }
            .replace(Regex("\\s+"), " ")
            .take(80)
    }
}
