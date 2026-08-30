package io.github.supermonster003.autojs6.plugin.apkinspector

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExplorerActionActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val seed = PackageRequestPolicy.resolveExplorer(intent)
        if (seed == null) {
            finish()
            return
        }
        stageAndOpen(seed)
    }

    private fun stageAndOpen(seed: PackageInputSeed) {
        scope.launch {
            val staged = try {
                withContext(Dispatchers.IO) {
                    PackageCacheStager.stage(this@ExplorerActionActivity, seed)
                }
            } finally {
                runCatching { seed.hostSession?.close() }
            }
            if (staged != null && !isFinishing && !isDestroyed) {
                runCatching { startActivity(ApkInspectorActivity.createIntent(this@ExplorerActionActivity, staged)) }
                    .onFailure { Toast.makeText(this@ExplorerActionActivity, R.string.error_cannot_inspect, Toast.LENGTH_LONG).show() }
            } else if (!isFinishing && !isDestroyed) {
                Toast.makeText(this@ExplorerActionActivity, R.string.error_cannot_read_package, Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
