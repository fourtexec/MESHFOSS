package ru.mesh.foss

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.mesh.foss.core.analytics.AnalyticsEvent
import ru.mesh.foss.data.analytics.AnalyticsQueue
import ru.mesh.foss.data.analytics.AnalyticsState
import ru.mesh.foss.data.analytics.AnalyticsStateStore

class AnalyticsStorageTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun queuePersistsRemovesAndTrimsOldest() {
        val queue = AnalyticsQueue(File(temp.root, "queue.jsonl"), maxEvents = 2)
        queue.append(event("first"))
        queue.append(event("second"))
        assertEquals(listOf("first", "second"), queue.load().map { it.event })

        queue.append(event("third"))
        assertEquals(listOf("second", "third"), queue.load().map { it.event })

        queue.remove(listOf(event("second")))
        assertEquals(listOf("third"), queue.load().map { it.event })
    }

    @Test
    fun brokenQueueLineIsIgnored() {
        val file = File(temp.root, "queue.jsonl")
        file.writeText("not json\n")
        val queue = AnalyticsQueue(file)
        assertTrue(queue.load().isEmpty())
    }

    @Test
    fun stateSurvivesRoundTripAndCorruption() {
        val file = File(temp.root, "state.json")
        val store = AnalyticsStateStore(file)
        val state = AnalyticsState(
            lastVersion = "2.5.2",
            lastActiveAt = 100L,
            lastRequestAt = 200L,
            blockedUntil = 300L,
        )
        store.save(state)
        assertEquals(state, store.load())

        file.writeText("broken")
        assertEquals(AnalyticsState(), store.load())
    }

    private fun event(name: String) = AnalyticsEvent(
        event = name,
        timestamp = "2026-11-25T19:30:00.123Z",
        app = "ru.mesh.foss",
        version = "2.5.2",
        device = "Pixel 7",
        level = "info",
        eventType = AnalyticsEvent.ACTIVE,
        uuid = "550e8400-e29b-41d4-a716-446655440000",
    )
}
