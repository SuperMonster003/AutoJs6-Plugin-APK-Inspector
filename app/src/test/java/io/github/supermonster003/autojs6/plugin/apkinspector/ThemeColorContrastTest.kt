package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorContrastTest {

    @Test
    fun lightSurfacesRequestDarkSystemBarIcons() {
        assertTrue(ThemeColorContrast.prefersDarkForeground(0xFFFFFFFF.toInt()))
        assertTrue(ThemeColorContrast.prefersDarkForeground(0xFFFFF8F4.toInt()))
        assertTrue(ThemeColorContrast.prefersDarkForeground(0xFFFFB870.toInt()))
    }

    @Test
    fun darkSurfacesKeepLightSystemBarIcons() {
        assertFalse(ThemeColorContrast.prefersDarkForeground(0xFF000000.toInt()))
        assertFalse(ThemeColorContrast.prefersDarkForeground(0xFF18120E.toInt()))
        assertFalse(ThemeColorContrast.prefersDarkForeground(0xFF8A4F00.toInt()))
    }
}
