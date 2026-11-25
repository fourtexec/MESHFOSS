package ru.mesh.foss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mesh.foss.core.analytics.AnalyticsSanitizer

class AnalyticsSanitizerTest {

    @Test
    fun externalEventContainsNoIdentifiersOrPii() {
        val line =
            "error GET https://school.mos.ru/api/lrs/v2/results?" +
                "context=%7B%22material_id%22%3A%226183813b-f3c3-5210-a245-943d7be524d0%22%7D" +
                "&student_id=5348640608 " +
                "person=550e8400-e29b-41d4-a716-446655440000 " +
                "email ivan@example.ru phone +7 (999) 123-45-67 " +
                "{\"name\":\"Иван\",\"person_id\":1234567}"
        val out = AnalyticsSanitizer.event(line)
        assertFalse(out.contains("6183813b-f3c3-5210-a245-943d7be524d0"))
        assertFalse(out.contains("5348640608"))
        assertFalse(out.contains("550e8400-e29b-41d4-a716-446655440000"))
        assertFalse(out.contains("ivan@example.ru"))
        assertFalse(out.contains("+7 (999) 123-45-67"))
        assertFalse(out.contains("Иван"))
        assertFalse(out.contains("1234567"))
        assertTrue(out.startsWith("error GET"))
    }

    @Test
    fun oneLineEventHasNoNewlines() {
        val out = AnalyticsSanitizer.event("first\nsecond\tthird")
        assertEquals("first second third", out)
    }

    @Test
    fun stackKeepsLinesButMasksSecrets() {
        val out = AnalyticsSanitizer.stack(
            "IllegalStateException: token=SECRETTOKEN123456\n  at ru.mesh.Foo.bar(Foo.kt:42)"
        )
        assertFalse(out.contains("SECRETTOKEN123456"))
        assertTrue(out.contains("\n  at ru.mesh.Foo.bar"))
    }

    @Test
    fun fingerprintIsStableForSameError() {
        val a = AnalyticsSanitizer.fingerprint("HTTP", IllegalStateException("bad request"))
        val b = AnalyticsSanitizer.fingerprint("HTTP", IllegalStateException("bad request"))
        assertEquals(a, b)
    }
}
