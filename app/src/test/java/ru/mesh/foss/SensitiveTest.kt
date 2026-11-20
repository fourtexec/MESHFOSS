package ru.mesh.foss

import ru.mesh.foss.core.Sensitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
                "Cookie: aupd_token=zzz Profile-Id: 36847597"
        val out = Sensitive.scrub(line)
        assertFalse(out.contains("SECRETTOKEN123456"))
        assertFalse(out.contains("abc.def.ghi"))
        assertFalse(out.contains("deadbeefdeadbeef"))
        assertFalse(out.contains("aupd_token=zzz"))
        assertFalse(out.contains("36847597"))
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
}
