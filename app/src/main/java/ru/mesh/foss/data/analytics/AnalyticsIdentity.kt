package ru.mesh.foss.data.analytics

import java.io.File
import java.util.UUID

/**
 * Анонимный id установки.
 *
 * Файл лежит в `noBackupFilesDir`: переживает обновление приложения, но
 * исчезает при переустановке и не попадает в облачный backup/перенос.
 */
class AnalyticsIdentity(private val file: File) {

    data class Value(val uuid: String, val created: Boolean)

    @Synchronized
    fun get(): Value {
        val existing = runCatching { file.takeIf { it.isFile }?.readText()?.trim() }.getOrNull()
        if (existing != null && isUuid(existing)) return Value(existing, created = false)

        val fresh = UUID.randomUUID().toString()
        write(fresh)
        return Value(fresh, created = true)
    }

    private fun write(value: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(value)
            if (!tmp.renameTo(file)) {
                file.writeText(value)
                tmp.delete()
            }
        }
    }

    companion object {
        private val UUID_RE = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            RegexOption.IGNORE_CASE
        )

        fun isUuid(value: String): Boolean = UUID_RE.matches(value)
    }
}
