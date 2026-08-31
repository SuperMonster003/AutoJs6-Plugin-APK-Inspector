package io.github.supermonster003.autojs6.plugin.apkinspector

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.supermonster003.autojs6.plugin.apkinspector.databinding.ActivityManifestViewerBinding

class ManifestViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MaterialThemeController.applySystemBars(this)
        val binding = ActivityManifestViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        val manifestFile = PackageCacheStager.resolveManifestFile(this, intent.getStringExtra(EXTRA_MANIFEST_PATH))
        if (manifestFile == null || manifestFile.length() > MAX_MANIFEST_BYTES) {
            finish()
            return
        }
        binding.manifestText.text = runCatching { manifestFile.readText(Charsets.UTF_8) }.getOrElse {
            finish()
            return
        }
    }

    companion object {
        private const val EXTRA_MANIFEST_PATH =
            "io.github.supermonster003.autojs6.plugin.apkinspector.extra.MANIFEST_PATH"
        private const val MAX_MANIFEST_BYTES = 8L * 1024L * 1024L

        internal fun createIntent(context: Context, path: String): Intent =
            Intent(context, ManifestViewerActivity::class.java).putExtra(EXTRA_MANIFEST_PATH, path)
    }
}
