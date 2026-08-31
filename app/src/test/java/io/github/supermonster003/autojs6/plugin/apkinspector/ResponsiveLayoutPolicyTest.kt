package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutPolicyTest {

    @Test
    fun narrowScreensUseCompactHeadersWhenTextIsScaled() {
        assertTrue(ResponsiveLayoutPolicy.shouldUseCompactHeader(screenWidthDp = 320, fontScale = 1.5f))
        assertTrue(ResponsiveLayoutPolicy.shouldUseCompactHeader(screenWidthDp = 320, fontScale = 2f))
    }

    @Test
    fun ordinaryPhoneWidthUsesCompactHeaderAtMaximumFontScale() {
        assertFalse(ResponsiveLayoutPolicy.shouldUseCompactHeader(screenWidthDp = 411, fontScale = 1.5f))
        assertTrue(ResponsiveLayoutPolicy.shouldUseCompactHeader(screenWidthDp = 411, fontScale = 2f))
    }

    @Test
    fun unscaledNarrowAndWideLandscapeHeadersRemainExpanded() {
        assertFalse(ResponsiveLayoutPolicy.shouldUseCompactHeader(screenWidthDp = 320, fontScale = 1f))
        assertFalse(ResponsiveLayoutPolicy.shouldUseCompactHeader(screenWidthDp = 693, fontScale = 2f))
    }

    @Test
    fun invalidWidthFallsBackToTheSafeCompactHeader() {
        assertTrue(ResponsiveLayoutPolicy.shouldUseCompactHeader(screenWidthDp = 0, fontScale = 1f))
    }
}
