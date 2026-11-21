package ru.mesh.foss

import ru.mesh.foss.data.CookieJson

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Парсер cookie-файлов: массив браузерного экспорта и плоский jar. */
class CookieJsonTest {

    private val browserExport = """
        [
          {"name":"aupd_token","value":"TOKEN-A","domain":"school.mos.ru","path":"/"},
          {"name":"user_login","value":"ivanov","domain":"school.mos.ru","path":"/"}
        ]
    """.trimIndent()

    @Test
    fun parsesBrowserExport() {
        val jar = CookieJson.parse(browserExport)
        assertEquals("TOKEN-A", jar["aupd_token"])
        assertEquals("ivanov", jar["user_login"])
        assertEquals(2, jar.size)
    }

    @Test
    fun parsesFlatJar() {
        val jar = CookieJson.parse("""{"aupd_token":"TOKEN-B"}""")
        assertEquals("TOKEN-B", jar["aupd_token"])
        assertTrue(CookieJson.parse(null).isEmpty())
        assertTrue(CookieJson.parse("not json").isEmpty())
    }

    @Test
    fun accountKeyIsStableAndIgnoresTokenRotation() {
        val first = """[{"name":"user_login","value":"ivanov"},{"name":"aupd_token","value":"OLD"}]"""
        val second = """[{"name":"user_login","value":"ivanov"},{"name":"aupd_token","value":"NEW"}]"""
        assertEquals(CookieJson.accountKey(first), CookieJson.accountKey(second))
        assertFalse(CookieJson.accountKey(first) == CookieJson.accountKey(
            """[{"name":"user_login","value":"petrov"},{"name":"aupd_token","value":"OLD"}]"""
        ))
    }

    @Test
    fun jarJsonRoundTrip() {
        val json = CookieJson.jarJson(browserExport)
        assertEquals(2, CookieJson.parse(json).size)
    }
}
