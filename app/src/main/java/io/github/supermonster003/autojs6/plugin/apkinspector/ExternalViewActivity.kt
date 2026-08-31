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

open class ExternalViewActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val seed = PackageRequestPolicy.resolveExternal(intent)
        if (seed == null) {
            finish()
            return
        }
        scope.launch {
            val staged = stagePackage(seed)
            if (staged != null && !isFinishing && !isDestroyed) {
                runCatching { startActivity(ApkInspectorActivity.createIntent(this@ExternalViewActivity, staged)) }
                    .onFailure { Toast.makeText(this@ExternalViewActivity, R.string.error_cannot_inspect, Toast.LENGTH_LONG).show() }
            } else if (!isFinishing && !isDestroyed) {
                Toast.makeText(this@ExternalViewActivity, R.string.error_cannot_read_package, Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    internal open suspend fun stagePackage(seed: PackageInputSeed): StagedPackage? =
        withContext(Dispatchers.IO) {
            PackageCacheStager.stage(this@ExternalViewActivity, seed)
        }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
