package io.github.supermonster003.autojs6.plugin.apkinspector

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.MaterialColors

/**
 * Renders the bundled changelog for the current locale (English when that one is missing) as one group per released
 * version, under the title "Release history" with the plugin name as the subtitle. The colors come from the same
 * Material theme as the inspection report (dynamic colors included), so both pages match.
 */
internal class ReleaseHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Below API 26 the navigation bar cannot show dark icons, so it keeps a readable dark background there.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(LEGACY_NAVIGATION_BAR))
        } else {
            enableEdgeToEdge()
        }
        super.onCreate(savedInstanceState)
        val candidates = ReleaseHistoryAssetPolicy.candidatesFor(resources.configuration.locales[0])
        val entries = loadReleaseHistory(candidates) { path ->
            assets.open(path).use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
        }
        val palette = themePalette(this)
        setContent {
            PluginReleaseHistoryTheme(palette) {
                ReleaseHistoryScreen(entries = entries, onBack = ::finish)
            }
        }
    }

    private companion object {
        const val LEGACY_NAVIGATION_BAR = 0xFF202124.toInt()

        /**
         * Reads the Material 3 roles of the activity theme after the dynamic color overlay is applied; the static
         * theme colors are the fallback.
         */
        fun themePalette(context: Context): ReleaseHistoryPalette {
            fun color(attribute: Int, fallback: Int) =
                MaterialColors.getColor(context, attribute, context.getColor(fallback))
            return ReleaseHistoryPalette(
                dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES,
                primary = color(androidx.appcompat.R.attr.colorPrimary, R.color.theme_primary),
                primaryContainer = color(com.google.android.material.R.attr.colorPrimaryContainer, R.color.theme_primary_container),
                background = color(android.R.attr.colorBackground, R.color.theme_background),
                surface = color(com.google.android.material.R.attr.colorSurface, R.color.theme_surface),
                onSurface = color(com.google.android.material.R.attr.colorOnSurface, R.color.theme_on_surface),
                onSurfaceVariant = color(com.google.android.material.R.attr.colorOnSurfaceVariant, R.color.theme_on_surface_variant),
                outlineVariant = color(com.google.android.material.R.attr.colorOutlineVariant, R.color.theme_outline_variant),
            )
        }
    }
}
