package ru.mesh.foss.core.analytics

/**
 * Собирает JSON Lines в пачки ≤ 60 КБ.
 *
 * Лимит endpoint — 64 КБ. Оставляем запас на заголовки/кодировку. Если одно
 * событие внезапно больше лимита, отправляем его укороченную версию без стека.
 */
object AnalyticsPayload {
    const val MAX_BYTES = 60 * 1024

    fun body(events: List<AnalyticsEvent>): String =
        events.joinToString("\n") { it.toJsonLine() }

    fun chunk(
        events: List<AnalyticsEvent>,
        maxBytes: Int = MAX_BYTES,
    ): List<List<AnalyticsEvent>> {
        if (events.isEmpty() || maxBytes <= 0) return emptyList()
        val result = mutableListOf<List<AnalyticsEvent>>()
        var current = mutableListOf<AnalyticsEvent>()
        var currentBytes = 0

        fun flush() {
            if (current.isNotEmpty()) {
                result += current
                current = mutableListOf()
                currentBytes = 0
            }
        }

        events.forEach { source ->
            val event = fit(source, maxBytes) ?: return@forEach
            val bytes = lineBytes(event)
            if (current.isNotEmpty() && currentBytes + bytes > maxBytes) flush()
            current += event
            currentBytes += bytes
        }
        flush()
        return result
    }

    private fun fit(event: AnalyticsEvent, maxBytes: Int): AnalyticsEvent? {
        if (lineBytes(event) <= maxBytes) return event
        val compact = event.copy(
            event = event.event.take(200),
            stack = null,
            errorType = event.errorType?.take(120),
            tag = event.tag?.take(64),
            os = event.os?.take(64),
        )
        return compact.takeIf { lineBytes(it) <= maxBytes }
    }

    private fun lineBytes(event: AnalyticsEvent): Int =
        event.toJsonLine().toByteArray(Charsets.UTF_8).size + 1
}
