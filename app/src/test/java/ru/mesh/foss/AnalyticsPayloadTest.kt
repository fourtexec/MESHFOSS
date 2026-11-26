package ru.mesh.foss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mesh.foss.core.analytics.AnalyticsEvent
import ru.mesh.foss.core.analytics.AnalyticsPayload

class AnalyticsPayloadTest {

    @Test
    fun chunksRespectLimitAndKeepAllEvents() {
        val events = (1..20).map { index -> event("event-$index-" + "x".repeat(120)) }
        val maxBytes = 900
        val chunks = AnalyticsPayload.chunk(events, maxBytes)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { AnalyticsPayload.body(it).toByteArray().size <= maxBytes })
        assertEquals(events, chunks.flatten())
    }

    @Test
    fun emptyInputGivesNoChunks() {
        assertTrue(AnalyticsPayload.chunk(emptyList()).isEmpty())
    }

    private fun event(text: String) = AnalyticsEvent(
        event = text,
        timestamp = "2026-11-25T19:30:00.123Z",
        app = "ru.mesh.foss",
        version = "2.5.2",
        device = "Pixel 7",
        level = "info",
        eventType = AnalyticsEvent.ACTIVE,
        uuid = "550e8400-e29b-41d4-a716-446655440000",
    )
}
