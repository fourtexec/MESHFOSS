package ru.mesh.foss

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-тесты проверки обновлений: сравнение семантических версий и выбор
 * релиза по каналу (releases / prereleases, без nightly).
 */
class UpdateCheckerTest {

    private fun compare(a: String, b: String) = UpdateChecker.compareVersions(a, b)

    @Test
    fun stableIsNewerThanPrereleaseWithSameCore() {
        assertTrue(compare("2.0.0", "2.0.0-beta.5")!! > 0)
        assertTrue(compare("2.0.0-beta.5", "2.0.0")!! < 0)
    }

    @Test
    fun numericPrereleaseIdentifiersCompareNumerically() {
        assertTrue(compare("2.0.0-beta.10", "2.0.0-beta.9")!! > 0)
    }

    @Test
    fun plainNumericVersions() {
        assertTrue(compare("2.0.1", "2.0.0")!! > 0)
        assertTrue(compare("1.0.93", "1.0.92")!! > 0)
        assertEquals(0, compare("v2.0.0", "2.0.0"))
    }

    @Test
    fun unparseableVersionsAreNull() {
        assertNull(compare("nightly", "2.0.0"))
        assertNull(compare("2.0.0", "dev.42"))
    }

    @Test
    fun pickUpdateSkipsNightlyAndDrafts() {
        val releases = JSONArray(
            """
            [
              {"tag_name":"nightly","prerelease":true,"draft":false,"html_url":"https://x/n"},
              {"tag_name":"v1.0.50","prerelease":false,"draft":true,"html_url":"https://x/d"},
              {"tag_name":"v2.0.0","prerelease":false,"draft":false,"html_url":"https://x/r",
               "assets":[{"name":"app.apk","browser_download_url":"https://x/a.apk"}]}
            ]
            """.trimIndent()
        )
        val found = UpdateChecker.pickUpdate(releases, installed = "1.0.93", withPrereleases = false)
        assertNotNull(found)
        assertEquals("2.0.0", found!!.versionName)
        assertEquals("https://x/a.apk", found.downloadUrl)
    }

    @Test
    fun releasesChannelHidesPrereleases() {
        val releases = JSONArray(
            """[{"tag_name":"v2.0.1-beta.1","prerelease":true,"draft":false,"html_url":"https://x/b"}]"""
        )
        assertNull(UpdateChecker.pickUpdate(releases, "2.0.0", withPrereleases = false))
        val found = UpdateChecker.pickUpdate(releases, "2.0.0", withPrereleases = true)
        assertNotNull(found)
        assertEquals("2.0.1-beta.1", found!!.versionName)
    }

    @Test
    fun noUpdateWhenInstalledIsNewer() {
        val releases = JSONArray(
            """[{"tag_name":"v2.0.0","prerelease":false,"draft":false,"html_url":"https://x/r"}]"""
        )
        assertNull(UpdateChecker.pickUpdate(releases, "2.0.1", withPrereleases = true))
    }
}
