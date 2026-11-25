package ru.mesh.foss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mesh.foss.core.analytics.AnalyticsEvent

class AnalyticsEventTest {

    @Test
    fun requiredFieldsAreSerializedAndParsed() {
        val event = AnalyticsEvent(
            event = "app installed",
            timestamp = "2026-11-25T19:30:00.123Z",
            app = "ru.mesh.foss",
            version = "2.5.2",
            device = "Google Pixel 7",
            level = "info",
            eventType = AnalyticsEvent.INSTALL,
            uuid = "550e8400-e29b-41d4-a716-446655440000",
            os = "Android 14 (API 34)",
        )
        val line = event.toJsonLine()
        assertTrue(line.contains("\"event_type\":\"app_install\""))
        assertTrue(line.contains("\"uuid\":\"550e8400-e29b-41d4-a716-446655440000\""))
        assertEquals(event, AnalyticsEvent.fromJsonLine(line))
    }

    @Test
    fun errorFieldsAreSerialized() {
        val event = AnalyticsEvent(
            event = "[HTTP] IOException",
            timestamp = "2026-11-25T19:30:00.123Z",
            app = "ru.mesh.foss",
            version = "2.5.2",
            device = "Pixel 7",
            level = "error",
            eventType = AnalyticsEvent.ERROR,
            uuid = "550e8400-e29b-41d4-a716-446655440000",
            tag = "HTTP",
            errorType = "java.io.IOException",
            stack = "java.io.IOException: boom\n  at Foo.bar(Foo.kt:1)",
        )
        val parsed = AnalyticsEvent.fromJsonLine(event.toJsonLine())
        assertEquals("HTTP", parsed?.tag)
        assertEquals("java.io.IOException", parsed?.errorType)
        assertTrue(parsed?.stack?.contains("Foo.bar") == true)
    }

    @Test
    fun incompleteLineIsRejected() {
        assertNull(AnalyticsEvent.fromJsonLine("{\"event\":\"x\"}"))
        assertNull(AnalyticsEvent.fromJsonLine("not json"))
    }
}
