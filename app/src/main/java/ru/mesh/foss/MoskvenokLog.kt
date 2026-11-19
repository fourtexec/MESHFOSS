package ru.mesh.foss

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Лог работы экрана «Москвёнок»: HTTP-запросы и ответы, редиректы стадии 2 PoC,
 * ошибки и состояния экрана (включение карты, QR, обновления).
 *
 * Переключатель — в Настройках (mesh_settings / moskvenok_log); вне
 * переключателя [append] — no-op. Пишется в filesDir/moskvenok.log
 * (усечение до ~512 КБ) и дублируется в logcat: `adb logcat -s Moskvenok`.
 */
object MoskvenokLog {
    private const val TAG = "Moskvenok"
    private const val FILE_NAME = "moskvenok.log"
    private const val MAX_BYTES = 512 * 1024

    private val lock = Any()
    private var appContext: Context? = null

    @Volatile
    var enabled: Boolean = false

    /** Подключает лог к MoskvenokApi и читает состояние переключателя. */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        enabled = app.getSharedPreferences("mesh_settings", Context.MODE_PRIVATE)
            .getBoolean("moskvenok_log", false)
        MoskvenokApi.logger = ::append
    }

    /** Одна строка лога. Чувствительное (сессии, куки, ПИИ) убирается на входе. */
    fun append(line: String) {
        if (!enabled) return
        val context = appContext ?: return
        synchronized(lock) {
            val stamped =
                "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())} ${MoskvenokApi.scrub(line)}"
            Log.d(TAG, stamped)
            runCatching {
                val file = File(context.filesDir, FILE_NAME)
                if (file.length() > MAX_BYTES) {
                    // Режем половину, чтобы файл не рос бесконечно.
                    val bytes = file.readBytes()
                    file.writeBytes(bytes.copyOfRange(bytes.size / 2, bytes.size))
                }
                file.appendText(stamped + "\n")
            }
        }
    }

    /** Текст лога для просмотра в Настройках. */
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
