package ru.mesh.foss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLanguageTest {
    @Test
    fun changingLanguagePreservesArgumentsAndLocalizesDates() {
        val expected = mapOf("ru" to "октября", "en" to "October", "hu" to "október", "es" to "octubre", "fr" to "octobre")
        try {
            for ((code, month) in expected) {
                UiLanguage.select(code)
                assertTrue(MeshCore.dateRu("2026-10-12").contains(month))
                assertTrue(Words.UpdateVersionAvailable("2.1").contains("2.1"))
                assertFalse(Words.UpdateVersionAvailable("2.1").contains("%s"))
                assertTrue(Words.entries.all { it.text.isNotBlank() })
            }
        } finally {
            UiLanguage.select("ru")
        }
    }

    @Test
    fun invalidLanguageAndPaletteUseSafeDefaults() {
        UiLanguage.select("xx")
        assertEquals("ru", UiLanguage.code)
        assertTrue(AppTheme.validCustom("67E8F9,071923,102A3A"))
        assertFalse(AppTheme.validCustom("67E8F9,071923,102A3A,FFFFFF"))
        assertFalse(AppTheme.validCustom("invalid"))
    }
}
