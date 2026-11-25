package ru.mesh.foss.data.analytics

import ru.mesh.foss.core.analytics.AnalyticsEvent
import java.io.File

/**
 * Очередь событий на диске (JSON Lines).
 *
 * Нужна, чтобы ошибки и дневная активность не терялись при отсутствии сети.
 * Файл также лежит в noBackupFilesDir и не попадает в backup/перенос.
 */
class AnalyticsQueue(
    private val file: File,
    private val maxEvents: Int = 200,
) {
    private val maxBytes = 256 * 1024L

    @Synchronized
    fun load(): List<AnalyticsEvent> {
        val events = readAll()
        if (events.size > maxEvents) {
            val trimmed = events.takeLast(maxEvents)
            save(trimmed)
            return trimmed
        }
        return events
    }

    @Synchronized
    fun append(event: AnalyticsEvent) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(event.toJsonLine() + "\n")
            if (file.length() > maxBytes) trim()
        }
    }

    @Synchronized
    fun removeFirst(count: Int) {
        if (count <= 0) return
        val events = readAll()
        if (count >= events.size) {
            file.delete()
            return
        }
        save(events.drop(count))
    }

    /** Удаляет уже отправленные события по точному JSON, не задевая новые. */
    @Synchronized
    fun remove(sent: List<AnalyticsEvent>) {
        if (sent.isEmpty()) return
        val expected = sent.map { it.toJsonLine() }.toMutableList()
        val remaining = readAll().filter { event ->
            val line = event.toJsonLine()
            val index = expected.indexOf(line)
            if (index >= 0) {
                expected.removeAt(index)
                false
            } else {
                true
            }
        }
        save(remaining)
    }

    @Synchronized
    fun clear() {
        runCatching { file.delete() }
    }

    @Synchronized
    fun size(): Int = readAll().size

    private fun readAll(): List<AnalyticsEvent> {
        if (!file.isFile) return emptyList()
        return runCatching {
            file.readLines(Charsets.UTF_8)
                .asSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { AnalyticsEvent.fromJsonLine(it) }
                .toList()
        }.getOrDefault(emptyList())
    }

    private fun trim() {
        save(readAll().takeLast(maxEvents))
    }

    private fun save(events: List<AnalyticsEvent>) {
        runCatching {
            file.parentFile?.mkdirs()
            val text = events.joinToString("") { it.toJsonLine() + "\n" }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }
}
