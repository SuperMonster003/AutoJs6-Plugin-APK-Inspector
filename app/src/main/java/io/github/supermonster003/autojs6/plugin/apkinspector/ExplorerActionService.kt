package io.github.supermonster003.autojs6.plugin.apkinspector

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.autojs.plugin.explorer.api.IExplorerActionPlugin

class ExplorerActionService : Service() {

    private val binder = object : IExplorerActionPlugin.Stub() {
        override fun getInfo() = apkInspectorPluginInfo()

        override fun getActionCatalog() = apkInspectorActionCatalog()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
