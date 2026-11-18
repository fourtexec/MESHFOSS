package ru.mesh.foss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты разбора флоу refresh.sh: дефолтные куки, OAuth entry из HTML
 * и распознавание callback school.mos.ru. Сам HTTP не гоняется.
 */
class CookieRefreshTest {

    @Test
    fun cookieHeaderAddsDefaultFrontendCookies() {
        val header = CookieRefresh.cookieHeader(mapOf("Ltpatoken2" to "abc"))
        assertTrue(header.contains("Ltpatoken2=abc"))
        assertTrue(header.contains("auth_flag=main"))
        assertTrue(header.contains("subsystem_id=2"))
        assertTrue(header.contains("aupd_current_role=2:2"))
    }

    @Test
    fun cookieHeaderKeepsExplicitDefaults() {
        val header = CookieRefresh.cookieHeader(mapOf("auth_flag" to "other"))
        assertTrue(header.contains("auth_flag=other"))
        assertFalse(header.contains("auth_flag=main"))
    }

    @Test
    fun oauthUrlExtractedFromMetaRefreshHtml() {
        val html = """
            <html><head>
            <meta http-equiv="refresh"
 content="0; url=https://login.mos.ru/sps/oauth/ae?response_type=code&amp;access_type=offline&amp;client_id=dnevnik.mos.ru">
            </head></html>
        """.trimIndent()
        val url = CookieRefresh.oauthUrlFromHtml(html)
        assertTrue(url!!.startsWith("https://login.mos.ru/sps/oauth/ae?"))
        assertTrue(url.contains("client_id=dnevnik.mos.ru"))
        assertFalse("HTML-сущности должны раскрываться", url.contains("&amp;"))
    }

    @Test
    fun oauthUrlAbsentOnLoginPageWithoutLink() {
        assertNull(CookieRefresh.oauthUrlFromHtml("<html><body><form id=\"login\"></form></body></html>"))
    }

    @Test
    fun callbackUrlRecognized() {
        assertTrue(
            CookieRefresh.isCallbackUrl("https://school.mos.ru/v3/auth/sudir/callback?code=abc")
        )
        assertFalse(
            CookieRefresh.isCallbackUrl("https://login.mos.ru/sps/oauth/ae?response_type=code")
        )
    }
}
