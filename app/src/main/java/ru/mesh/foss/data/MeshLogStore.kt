package ru.mesh.foss.data

import ru.mesh.foss.core.MeshLog

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Android-сторона общего журнала: включает [MeshLog], дублирует строки в
 * logcat (`adb logcat -s MeshLog`) и пишет их в `filesDir/mesh.log` с усечением.
 *
 * Переключатель — в Настройках. Ключ настройки `app_log`; старый `moskvenok_log`
 * читается как запасной, чтобы не потерять выбор пользователя после обновления.
 */
object MeshLogStore {
    private const val TAG = "MeshLog"
    private const val FILE_NAME = "mesh.log"
    private const val MAX_BYTES = 512 * 1024

    private const val PREFS = "mesh_settings"
    private const val KEY_ENABLED = "app_log"
    private const val KEY_LEGACY = "moskvenok_log"

    private val lock = Any()
    private var appContext: Context? = null

    /** Подключает журнал к приложению и читает сохранённый переключатель. */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        MeshLog.enabled = prefs.getBoolean(KEY_ENABLED, prefs.getBoolean(KEY_LEGACY, false))
        MeshLog.sink = ::write
    }

    /** Переключатель из Настроек: и в память, и в prefs. */
    fun setEnabled(enabled: Boolean) {
        MeshLog.enabled = enabled
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
    }

    private fun write(line: String) {
        val context = appContext ?: return
        Log.d(TAG, line)
        synchronized(lock) {
            runCatching {
                val file = File(context.filesDir, FILE_NAME)
                if (file.length() > MAX_BYTES) {
                    // Режем половину, чтобы файл не рос бесконечно.
                    val bytes = file.readBytes()
                    file.writeBytes(bytes.copyOfRange(bytes.size / 2, bytes.size))
                }
                file.appendText(line + "\n")
            }
        }
    }

    /** Текст журнала для просмотра/копирования в Настройках. */
    fun read(context: Context): String = synchronized(lock) {
        runCatching {
            File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText().orEmpty()
        }.getOrDefault("")
    }

    fun clear() {
        val context = appContext ?: return
        synchronized(lock) { runCatching { File(context.filesDir, FILE_NAME).delete() } }
    }
}
