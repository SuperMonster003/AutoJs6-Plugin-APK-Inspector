package io.github.supermonster003.autojs6.plugin.apkinspector

/** Chooses compact UI variants from the text width that remains after font scaling. */
internal object ResponsiveLayoutPolicy {

    private const val HORIZONTAL_NON_TEXT_WIDTH_DP = 128f
    private const val MIN_EFFECTIVE_TEXT_WIDTH_DP = 176f
    private const val MIN_EFFECTIVE_HEIGHT_FOR_IME_DP = 400f

    fun shouldUseCompactHeader(screenWidthDp: Int, fontScale: Float): Boolean {
        if (screenWidthDp <= 0) return true
        val safeFontScale = fontScale.coerceAtLeast(1f)
        val horizontalTextWidthDp = (screenWidthDp - HORIZONTAL_NON_TEXT_WIDTH_DP)
            .coerceAtLeast(0f)
        return horizontalTextWidthDp / safeFontScale < MIN_EFFECTIVE_TEXT_WIDTH_DP
    }

    fun shouldRequestManifestSearchKeyboard(screenHeightDp: Int, fontScale: Float): Boolean {
        if (screenHeightDp <= 0) return false
        val safeFontScale = fontScale.coerceAtLeast(1f)
        return screenHeightDp / safeFontScale >= MIN_EFFECTIVE_HEIGHT_FOR_IME_DP
    }
}
