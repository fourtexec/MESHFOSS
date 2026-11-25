package ru.mesh.foss

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.mesh.foss.core.analytics.AnalyticsEvent
import ru.mesh.foss.data.analytics.AnalyticsIdentity
import ru.mesh.foss.data.analytics.AnalyticsQueue
import ru.mesh.foss.data.analytics.AnalyticsReporter
import ru.mesh.foss.data.analytics.AnalyticsResult
import ru.mesh.foss.data.analytics.AnalyticsStateStore
import ru.mesh.foss.data.analytics.AnalyticsTransport

class AnalyticsReporterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test
    fun installThenDailyActiveThenUpdate() = runBlocking {
        var now = 1_000_000L
        val identity = AnalyticsIdentity(File(temp.root, "id"))
        val stateStore = AnalyticsStateStore(File(temp.root, "state.json"))
        val queue = AnalyticsQueue(File(temp.root, "queue.jsonl"))
        val transport = FakeTransport()
        val reporter = reporter(identity, stateStore, queue, transport, "2.5.2") { now }

        reporter.launchNow()
        assertEquals(listOf(AnalyticsEvent.INSTALL), transport.types())
        assertTrue(queue.load().isEmpty())

        now += 23 * 60 * 60 * 1_000L
        reporter.launchNow()
        assertEquals(1, transport.bodies.size)

        now += 2 * 60 * 60 * 1_000L
        reporter.launchNow()
        assertEquals(listOf(AnalyticsEvent.INSTALL, AnalyticsEvent.ACTIVE), transport.types())

        val updated = FakeTransport()
        val next = reporter(identity, stateStore, queue, updated, "2.6.0") { now }
        next.launchNow()
        assertEquals(listOf(AnalyticsEvent.UPDATE), updated.types())
    }

    @Test
    fun errorsAreSanitizedBeforeQueueing() = runBlocking {
        var now = 1_000_000L
        val identity = AnalyticsIdentity(File(temp.root, "id"))
        val stateStore = AnalyticsStateStore(File(temp.root, "state.json"))
        val queue = AnalyticsQueue(File(temp.root, "queue.jsonl"))
        val transport = FakeTransport()
        val reporter = reporter(identity, stateStore, queue, transport, "2.5.2") { now }

        reporter.recordError(
            "HTTP",
            IllegalStateException("bad student_id=5348640608 token=SECRETTOKEN123456")
        )
        assertEquals(1, queue.load().size)
        assertTrue(transport.bodies.isEmpty())

        reporter.flushNow()
        assertEquals(1, transport.bodies.size)
        val body = transport.bodies.single()
        assertTrue(body.contains("\"event_type\":\"error\""))
        assertFalse(body.contains("5348640608"))
        assertFalse(body.contains("SECRETTOKEN123456"))
        assertTrue(queue.load().isEmpty())
    }

    @Test
    fun rateLimitKeepsQueueAndRetriesAfterBackoff() = runBlocking {
        var now = 1_000_000L
        val identity = AnalyticsIdentity(File(temp.root, "id"))
        val stateStore = AnalyticsStateStore(File(temp.root, "state.json"))
        val queue = AnalyticsQueue(File(temp.root, "queue.jsonl"))
        val transport = FakeTransport(
            mutableListOf(AnalyticsResult.Retry(60_000L, 429), AnalyticsResult.Success)
        )
        val reporter = reporter(identity, stateStore, queue, transport, "2.5.2") { now }

        reporter.launchNow()
        assertEquals(1, transport.bodies.size)
        assertEquals(1, queue.load().size)

        reporter.flushNow()
        assertEquals(1, transport.bodies.size)

        now += 60_001L
        reporter.flushNow()
        assertEquals(2, transport.bodies.size)
        assertTrue(queue.load().isEmpty())
    }

    private fun reporter(
        identity: AnalyticsIdentity,
        stateStore: AnalyticsStateStore,
        queue: AnalyticsQueue,
        transport: AnalyticsTransport,
        version: String,
        clock: () -> Long,
    ) = AnalyticsReporter(
        identity = identity,
        stateStore = stateStore,
        queue = queue,
        transport = transport,
        app = "ru.mesh.foss",
        version = version,
        device = "Pixel 7",
        os = "Android 14",
        scope = scope,
        clock = clock,
        minRequestIntervalMs = 0L,
        autoFlushOnError = false,
    )

    private class FakeTransport(
        private val results: MutableList<AnalyticsResult> = mutableListOf(),
    ) : AnalyticsTransport {
        val bodies = mutableListOf<String>()

        override suspend fun post(body: String): AnalyticsResult {
            bodies += body
            return results.removeFirstOrNull() ?: AnalyticsResult.Success
        }

        fun types(): List<String> = bodies.flatMap { body ->
            body.lineSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { AnalyticsEvent.fromJsonLine(it) }
                .map { it.eventType }
                .toList()
        }
    }
}
