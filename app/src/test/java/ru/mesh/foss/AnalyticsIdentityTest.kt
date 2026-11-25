package ru.mesh.foss

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.mesh.foss.data.analytics.AnalyticsIdentity

class AnalyticsIdentityTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun uuidIsCreatedOnceAndSurvivesReads() {
        val file = File(temp.root, "analytics_id")
        val identity = AnalyticsIdentity(file)
        val first = identity.get()
        assertTrue(first.created)
        assertTrue(AnalyticsIdentity.isUuid(first.uuid))
        assertTrue(file.isFile)

        val second = identity.get()
        assertFalse(second.created)
        assertEquals(first.uuid, second.uuid)
    }

    @Test
    fun corruptFileGetsNewUuid() {
        val file = File(temp.root, "analytics_id")
        file.writeText("not-a-uuid")
        val identity = AnalyticsIdentity(file)
        val value = identity.get()
        assertTrue(value.created)
        assertTrue(AnalyticsIdentity.isUuid(value.uuid))
        assertNotEquals("not-a-uuid", value.uuid)
    }
}
