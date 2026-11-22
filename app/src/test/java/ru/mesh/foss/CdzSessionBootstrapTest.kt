package ru.mesh.foss

import ru.mesh.foss.data.CdzSessionBootstrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Разбор OAuth-цепочки JSON-входа; сеть здесь не нужна. */
class CdzSessionBootstrapTest {

    @Test
    fun authorizeUrlLeadsToUchebnikAuthenticate() {
        val url = CdzSessionBootstrap.authorizeUrl("state-1")
        assertTrue(url.startsWith("https://school.mos.ru/v1/oauth/sudir/authorize?"))
        assertTrue(url.contains("client_id=library"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("state=state-1"))
        assertTrue(
            "redirect_uri должен вести на /authenticate",
            url.contains("redirect_uri=https%3A%2F%2Fuchebnik.mos.ru%2Fauthenticate")
        )
        assertFalse("scope не должен кодироваться плюсами", url.contains("+"))
    }

    @Test
    fun callbackUrlUsesWorkingStudentRole() {
        val url = CdzSessionBootstrap.callbackUrl("state-1", "code-2")
        assertTrue(url.startsWith("https://uchebnik.mos.ru/acl/api/session/callback?"))
        assertTrue(url.contains("state=state-1"))
        assertTrue(url.contains("code=code-2"))
        assertTrue(url.contains("rgn=77"))
        assertTrue(url.contains("aupd_current_role=student"))
        assertFalse("2:2 не подходит для этой учётной записи", url.contains("2%3A2"))
    }

    @Test
    fun metaRefreshUrlReadsQuotedAndEscapedUrl() {
        val html = """
            <html><head>
            <meta http-equiv="refresh"
 content="0; url=https://login.mos.ru/sps/oauth/ae?response_type=code&amp;client_id=library">
            </head></html>
        """.trimIndent()
        val url = CdzSessionBootstrap.metaRefreshUrl(html)
        assertTrue(url!!.startsWith("https://login.mos.ru/sps/oauth/ae?"))
        assertTrue(url.contains("client_id=library"))
        assertFalse("HTML-сущности должны раскрываться", url.contains("&amp;"))
    }

    @Test
    fun metaRefreshUrlAlsoReadsContentBeforeHttpEquiv() {
        val html =
            "<meta content=\"0;url=https://login.mos.ru/sps/oauth/ae?x=1\" http-equiv=\"refresh\">"
        assertEquals(
            "https://login.mos.ru/sps/oauth/ae?x=1",
            CdzSessionBootstrap.metaRefreshUrl(html)
        )
    }

    @Test
    fun metaRefreshUrlIsNullWithoutMeta() {
        assertNull(CdzSessionBootstrap.metaRefreshUrl("<html><body>login</body></html>"))
    }

    @Test
    fun codeAndStateDecodedFromAuthenticateUrl() {
        val pair = CdzSessionBootstrap.codeAndState(
            "https://uchebnik.mos.ru/authenticate?backurl=%2Fexam%2F&code=a%2Bb&state=xyz"
        )
        assertEquals("a+b", pair!!.first)
        assertEquals("xyz", pair.second)
    }

    @Test
    fun codeAndStateRejectsIncompleteUrl() {
        assertNull(
            CdzSessionBootstrap.codeAndState("https://uchebnik.mos.ru/authenticate?state=xyz")
        )
    }

    @Test
    fun onlyUchebnikAuthenticateIsTheOAuthTarget() {
        assertTrue(
            CdzSessionBootstrap.isUchebnikAuth(
                "https://uchebnik.mos.ru/authenticate?code=abc&state=xyz"
            )
        )
        assertFalse(
            CdzSessionBootstrap.isUchebnikAuth(
                "https://school.mos.ru/v1/oauth/callback?code=abc"
            )
        )
        assertFalse(CdzSessionBootstrap.isUchebnikAuth("not a url"))
    }

    @Test
    fun sessionCookiesRequireBothValues() {
        assertFalse(CdzSessionBootstrap.hasSessionCookies(emptyMap()))
        assertFalse(
            CdzSessionBootstrap.hasSessionCookies(mapOf("Session-Id-External" to "a"))
        )
        assertFalse(
            CdzSessionBootstrap.hasSessionCookies(mapOf("Session-Eom-Profile" to "b"))
        )
        assertTrue(
            CdzSessionBootstrap.hasSessionCookies(
                mapOf(
                    "Session-Id-External" to "a",
                    "Session-Eom-Profile" to "b",
                )
            )
        )
    }
}
