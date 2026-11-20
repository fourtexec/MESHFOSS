package ru.mesh.foss

import ru.mesh.foss.data.UpdateChecker

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-тесты проверки обновлений: сравнение семантических версий и выбор
 * релиза по каналу (releases / prereleases, nightly — только при ручной
 * проверке).
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

    private fun release(tag: String, prerelease: Boolean, draft: Boolean = false, code: Long? = null): String {
        // \\n — экранированные переносы: JSON-строка не может содержать настоящие переводы строк.
        val body = if (code != null) " notes\\n\\n- versionCode: `$code`\\n- commit: `abc`" else "notes"
        return """{"tag_name":"$tag","prerelease":$prerelease,"draft":$draft,"html_url":"https://x/$tag","body":"$body",
            "assets":[{"name":"app.apk","browser_download_url":"https://x/a.apk"}]}"""
    }

    @Test
    fun pickUpdateSkipsDraftsAndOlder() {
        val releases = JSONArray(
            "[${release("v1.0.50", prerelease = false, draft = true)}," +
                "${release("v1.0.52", prerelease = false)}," +
                "${release("v2.0.0", prerelease = false)}]"
        )
        val found = UpdateChecker.pickUpdate(releases, "1.0.93", 93, withPrereleases = false, includeNightly = false)
        assertNotNull(found)
        assertEquals("2.0.0", found!!.versionName)
    }

    @Test
    fun releasesChannelHidesPrereleases() {
        val releases = JSONArray("[${release("v2.0.1-beta.1", prerelease = true)}]")
        assertNull(UpdateChecker.pickUpdate(releases, "2.0.0", 96, withPrereleases = false, includeNightly = true))
        val found = UpdateChecker.pickUpdate(releases, "2.0.0", 96, withPrereleases = true, includeNightly = false)
        assertNotNull(found)
        assertEquals("2.0.1-beta.1", found!!.versionName)
    }

    @Test
    fun nightlyOnlyOnManualCheckWithPrereleaseChannel() {
        val releases = JSONArray(
            "[${release("v2.0.0", prerelease = false, code = 96)}," +
                "${release("nightly", prerelease = true, code = 97)}]"
        )
        // Авто-предупреждение: nightly не предлагается, стабильный релиз не новее.
        assertNull(UpdateChecker.pickUpdate(releases, "2.0.0", 96, withPrereleases = true, includeNightly = false))
        // Ручная проверка на канале «Пререлизы»: nightly из main (versionCode растёт).
        val found = UpdateChecker.pickUpdate(releases, "2.0.0", 96, withPrereleases = true, includeNightly = true)
        assertNotNull(found)
        assertEquals("nightly", found!!.versionName)
        assertEquals("https://x/a.apk", found.downloadUrl)
    }

    @Test
    fun versionCodeBeatsSemverForNightly() {
        // Семантически 2.0.0-dev.97 младше 2.0.0, но versionCode новее —
        // на канале «Пререлизы» nightly всё равно предлагается.
        val releases = JSONArray("[${release("nightly", prerelease = true, code = 97)}]")
        val found = UpdateChecker.pickUpdate(releases, "2.0.0", 96, withPrereleases = true, includeNightly = true)
        assertNotNull(found)
        assertEquals("nightly", found!!.versionName)
        // Обратная сторона: старый nightly (меньше код) не предлагается.
        assertNull(UpdateChecker.pickUpdate(releases, "2.0.0", 98, withPrereleases = true, includeNightly = true))
    }

    @Test
    fun noUpdateWhenInstalledIsNewer() {
        val releases = JSONArray("[${release("v2.0.0", prerelease = false)}]")
        assertNull(UpdateChecker.pickUpdate(releases, "2.0.1", 100, withPrereleases = true, includeNightly = true))
    }
}
