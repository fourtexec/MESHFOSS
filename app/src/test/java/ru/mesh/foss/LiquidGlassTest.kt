package ru.mesh.foss

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mesh.foss.ui.Design
import ru.mesh.foss.ui.LiquidGlassEngine

/**
 * JVM-тесты интеграции Kyant0/AndroidLiquidGlass и выбора дизайна по умолчанию.
 */
class LiquidGlassTest {

    @Test
    fun defaultDesignIsPlainM3e() {
        assertEquals(Design.PLAIN, Design.DEFAULT)
        assertEquals(Design.PLAIN, Design.resolve(savedDesign = null, legacyGlassEnabled = null))
        assertEquals(Design.PLAIN, Design.resolve(savedDesign = null, legacyGlassEnabled = false))
    }

    @Test
    fun legacyRemovedDesignsFallbackToPlainAndLiquidIsPreserved() {
        assertEquals(Design.PLAIN, Design.resolve(savedDesign = null, legacyGlassEnabled = true))
        assertEquals(Design.PLAIN, Design.resolve(savedDesign = Design.PLAIN, legacyGlassEnabled = null))
        assertEquals(Design.PLAIN, Design.resolve(savedDesign = "glass", legacyGlassEnabled = true))
        assertEquals(Design.PLAIN, Design.resolve(savedDesign = "frosted", legacyGlassEnabled = true))
        assertEquals(Design.LIQUID, Design.resolve(savedDesign = Design.LIQUID, legacyGlassEnabled = null))
    }

    @Test
    fun kyantEngineRespectsAndroidSdkAndPreviewFlags() {
        assertFalse(LiquidGlassEngine.supportsBackdrop(30))
        assertTrue(LiquidGlassEngine.supportsBackdrop(31))
        assertFalse(LiquidGlassEngine.supportsLens(32))
        assertTrue(LiquidGlassEngine.supportsLens(33))

        assertTrue(LiquidGlassEngine.useKyant(sdkInt = 33, previewEnabled = false, hasBackdrop = true))
        assertTrue(LiquidGlassEngine.useKyant(sdkInt = 31, previewEnabled = false, hasBackdrop = true))
        assertFalse(LiquidGlassEngine.useKyant(sdkInt = 30, previewEnabled = false, hasBackdrop = true))
        assertFalse(LiquidGlassEngine.useKyant(sdkInt = 33, previewEnabled = true, hasBackdrop = true))
        assertFalse(LiquidGlassEngine.useKyant(sdkInt = 33, previewEnabled = false, hasBackdrop = false))
    }

    @Test
    fun midnightThemeGetsVibrantGlowsForRefraction() {
        val midnightPrimary = Color(0xFFD1D5DB)
        val defaultTertiary = Color(0xFFEFB8C8)
        assertTrue(LiquidGlassEngine.isAchromatic(midnightPrimary))
        val (glowA, glowB, glowC) = LiquidGlassEngine.backdropGlows(midnightPrimary, defaultTertiary)
        assertNotEquals(midnightPrimary, glowA)
        assertFalse(LiquidGlassEngine.isAchromatic(glowA))
        assertFalse(LiquidGlassEngine.isAchromatic(glowB))
        assertFalse(LiquidGlassEngine.isAchromatic(glowC))

        val oceanPrimary = Color(0xFF67E8F9)
        assertFalse(LiquidGlassEngine.isAchromatic(oceanPrimary))
        val (oceanA, _, oceanC) = LiquidGlassEngine.backdropGlows(oceanPrimary, defaultTertiary)
        assertEquals(oceanPrimary, oceanA)
        assertEquals(defaultTertiary, oceanC)
    }
}
