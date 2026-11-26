package ru.mesh.foss

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mesh.foss.app.NavigationMode

class NavigationModeTest {

    @Test
    fun landscapeMovesNavigationToTheSide() {
        assertTrue(NavigationMode.useSideRail(Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test
    fun portraitKeepsBottomNavigation() {
        assertFalse(NavigationMode.useSideRail(Configuration.ORIENTATION_PORTRAIT))
        assertFalse(NavigationMode.useSideRail(Configuration.ORIENTATION_UNDEFINED))
    }
}
