package ru.mesh.foss

import ru.mesh.foss.core.Sensitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Общая редакция чувствительных данных для журнала. Проверяет, что токены,
 * cookie и ПИИ маскируются, обычный текст не трогается, а «странные» строки
 * со скобками не приводят к исключению (ICU-совместимость скобок).
 */
class SensitiveTest {

    @Test
    fun tokensCookiesAndPiiAreMasked() {
        val line =
            "→ GET https://school.mos.ru/api/x?token=SECRETTOKEN123456 " +
                "Authorization: Bearer abc.def.ghi Auth-Token: deadbeefdeadbeef " +
                "Cookie: aupd_token=zzz Profile-Id: 12345678"
        val out = Sensitive.scrub(line)
        assertFalse(out.contains("SECRETTOKEN123456"))
        assertFalse(out.contains("abc.def.ghi"))
        assertFalse(out.contains("deadbeefdeadbeef"))
        assertFalse(out.contains("aupd_token=zzz"))
        assertFalse(out.contains("12345678"))
    }

    @Test
    fun plainTextIsUntouched() {
        assertEquals("load: isEnabled=false", Sensitive.scrub("load: isEnabled=false"))
    }

    @Test
    fun bracesAndBracketsDoNotThrow() {
        val weird = "{\"personal\": {\"a\": [1,2,3]}} ][ {{ sid=abcdef1234567890"
        assertNotNull(Sensitive.scrub(weird))
        assertNotNull(Sensitive.scrub(""))
    }

    @Test
    fun studentIdsMarksNamesAndContextAreMasked() {
        val line =
            "GET https://school.mos.ru/api/lrs/v2/results?" +
                "context=%7B%22material_id%22%3A%226183813b-f3c3-5210-a245-943d7be524d0%22%7D&size=20 " +
                "https://school.mos.ru/api/family/web/v1/marks?student_id=5348640608&markId=3340378813 " +
                "https://school.mos.ru/exam/challenge/150779975 " +
                "{\"person_id\":1234567,\"name\":\"Иван\",\"email\":\"ivan@example.ru\"}"
        val out = Sensitive.scrub(line)
        assertFalse(out.contains("6183813b-f3c3-5210-a245-943d7be524d0"))
        assertFalse(out.contains("5348640608"))
        assertFalse(out.contains("3340378813"))
        assertFalse(out.contains("150779975"))
        assertFalse(out.contains("1234567"))
        assertFalse(out.contains("Иван"))
        assertFalse(out.contains("ivan@example.ru"))
        assertFalse(out.contains("%7B%22material_id%22"))
        assertTrue(out.contains("size=20"))
    }

    @Test
    fun authorizationWithoutBearerAndApiKeyHeaderAreMasked() {
        val line = "Authorization: plain-secret-token X-Api-Key: analytics-secret"
        val out = Sensitive.scrub(line)
        assertFalse(out.contains("plain-secret-token"))
        assertFalse(out.contains("analytics-secret"))
    }
}
