package ru.mesh.foss

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор ответов id.mos.ru (flow.txt) и схемы сессии из стадии 2 PoC.
 * Сам HTTP не гоняется.
 */
class MoskvenokApiTest {

    private val objectsJson = JSONObject(
        """
        {"personal": {"lastname": "БОРОВСКИЙ РУКИЕ", "firstname": "ДАНИИЛ"},
         "groups": [{"title": "Москвёнок", "items": [
            {"id": "combined/mesh/0345c036-12a8-4757-a00b-9555588736e1_mesh_bal",
             "title": "Москвёнок", "subtitle": "Баланс: 0,00 ₽",
             "type": "mesh", "active": true}],
           "favorite": true, "category": [2]}],
         "relatives": []}
        """.trimIndent()
    )

    @Test
    fun meshItemInGroupsMeansMoskvenokIsEnabled() {
        assertTrue(MoskvenokApi.isEnabled(objectsJson))
    }

    @Test
    fun groupsWithoutMeshItemAreDisabled() {
        val json = JSONObject(
            """{"groups": [{"title": "Карты", "items": [{"type": "bank", "id": "x"}]}]}"""
        )
        assertFalse(MoskvenokApi.isEnabled(json))
        assertFalse(MoskvenokApi.isEnabled(JSONObject("{}")))
    }

    @Test
    fun meshDocIdIsTakenFromObjectsResponse() {
        assertEquals(
            "combined/mesh/0345c036-12a8-4757-a00b-9555588736e1_mesh_bal",
            MoskvenokApi.meshDocId(objectsJson, "someone-else")
        )
    }

    @Test
    fun meshDocIdFallsBackToStudentUuid() {
        val empty = JSONObject("""{"groups": []}""")
        assertEquals(
            "combined/mesh/0345c036-12a8-4757-a00b-9555588736e1_mesh_bal",
            MoskvenokApi.meshDocId(empty, "0345c036-12a8-4757-a00b-9555588736e1")
        )
        assertNull(MoskvenokApi.meshDocId(empty, null))
        assertNull(MoskvenokApi.meshDocId(empty, "  "))
        // Числовой studentId МЭШ не годится — только UUID.
        assertNull(MoskvenokApi.meshDocId(empty, "37050212"))
    }

    @Test
    fun scrubMasksSessionAndPersonalData() {
        val line = "session: got sid=1qqfos472uya2epxu79ow36tuxjl42g0"
        assertFalse(MoskvenokApi.scrub(line).contains("1qqfos472uya2epxu79ow36tuxjl42g0"))
        val response =
            """response: {"personal": {"email": "user@example.com"}, "phone": "+79990001122"}"""
        val scrubbed = MoskvenokApi.scrub(response)
        assertFalse(scrubbed.contains("user@example.com"))
        assertFalse(scrubbed.contains("+79990001122"))
        val qr = """{"barcode_payload": "ABC123"}"""
        assertFalse(MoskvenokApi.scrub(qr).contains("ABC123"))
        // Обычные строки не ломаются.
        assertEquals("load: isEnabled=false", MoskvenokApi.scrub("load: isEnabled=false"))
    }

    @Test
    fun sessionIdExtractedFromMosidLinkInCallbackHtml() {
        val html = """
            <html><body><script>
            window.location = "mosid://auth?success=1&session_id=1qqfos472uya2epxu79ow36tuxjl42g0&favs=1&docs=1";
            </script></body></html>
        """.trimIndent()
        assertEquals(
            "1qqfos472uya2epxu79ow36tuxjl42g0",
            MoskvenokApi.sessionFromMosid(html)
        )
        assertNull(MoskvenokApi.sessionFromMosid("<html>no link</html>"))
    }

    @Test
    fun promptParamStrippedFromOauthUrlLikePoc() {
        val url =
            "https://login.mos.ru/sps/oauth/ae?client_id=dnevnik.mos.ru&prompt=login&scope=openid"
        assertEquals(
            "https://login.mos.ru/sps/oauth/ae?client_id=dnevnik.mos.ru&scope=openid",
            MoskvenokApi.stripParam(url, "prompt")
        )
        // Без параметра URL не меняется.
        val clean = "https://login.mos.ru/sps/oauth/ae?client_id=dnevnik.mos.ru"
        assertEquals(clean, MoskvenokApi.stripParam(clean, "prompt"))
    }
}
