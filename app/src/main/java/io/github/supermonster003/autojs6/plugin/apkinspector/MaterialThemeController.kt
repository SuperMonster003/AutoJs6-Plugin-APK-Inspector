package io.github.supermonster003.autojs6.plugin.apkinspector

import android.app.Activity
import androidx.annotation.ColorInt
import androidx.core.view.WindowCompat
import com.google.android.material.color.MaterialColors
import kotlin.math.pow

/** Applies semantic Material colors to system chrome after the dynamic overlay is installed. */
internal object MaterialThemeController {

    fun applySystemBars(activity: Activity) {
        val surface = MaterialColors.getColor(
            activity,
            com.google.android.material.R.attr.colorSurface,
            OPAQUE_BLACK,
        )
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = surface
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = surface
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = ThemeColorContrast.prefersDarkForeground(surface)
            isAppearanceLightNavigationBars = ThemeColorContrast.prefersDarkForeground(surface)
        }
    }

    private const val OPAQUE_BLACK: Int = -0x1000000
}

/** Android's light-system-bar decision, kept platform-free so its contrast behavior is testable. */
internal object ThemeColorContrast {

    fun prefersDarkForeground(@ColorInt background: Int): Boolean =
        relativeLuminance(background) > LIGHT_BACKGROUND_LUMINANCE

    private fun relativeLuminance(@ColorInt color: Int): Double {
        val red = linearized((color ushr 16) and 0xFF)
        val green = linearized((color ushr 8) and 0xFF)
        val blue = linearized(color and 0xFF)
        return red * 0.2126 + green * 0.7152 + blue * 0.0722
    }

    private fun linearized(channel: Int): Double {
        val value = channel / 255.0
        return if (value <= 0.04045) {
            value / 12.92
        } else {
            ((value + 0.055) / 1.055).pow(2.4)
        }
    }

    private const val LIGHT_BACKGROUND_LUMINANCE = 0.5
}
