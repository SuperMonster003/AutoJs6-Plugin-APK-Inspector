package io.github.supermonster003.autojs6.plugin.apkinspector

import android.app.Activity
import android.app.AlertDialog
import android.widget.ScrollView
import android.widget.TextView
import java.io.IOException

internal fun releaseHistoryCandidates(language: String, country: String, script: String): List<String> {
    val code = when {
        language == "zh" && country == "HK" -> "zh-Hant-HK"
        language == "zh" && (country == "TW" || script == "Hant") -> "zh-Hant-TW"
        language == "zh" -> "zh-Hans"
        language in setOf("en", "ar", "es", "fr", "ja", "ko", "ru") -> language
        else -> "en"
    }
    return listOf("doc/CHANGELOG-$code.md", "doc/CHANGELOG-en.md").distinct()
}

internal fun loadReleaseHistory(candidates: List<String>, loader: (String) -> String): String? {
    for (path in candidates) {
        try { loader(path).takeIf { it.isNotBlank() }?.let { return it } } catch (_: IOException) { }
    }
    return null
}

internal fun Activity.showReleaseHistory() {
    val locale = resources.configuration.locales[0]
    val history = loadReleaseHistory(releaseHistoryCandidates(locale.language, locale.country, locale.script)) { path ->
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } ?: getString(R.string.release_history_unavailable)
    val content = TextView(this).apply {
        text = history
        textSize = 14f
        setTextIsSelectable(true)
        val padding = (20 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
    }
    AlertDialog.Builder(this)
        .setTitle(R.string.release_history)
        .setView(ScrollView(this).apply { addView(content) })
        .setPositiveButton(android.R.string.ok, null)
        .show()
}
