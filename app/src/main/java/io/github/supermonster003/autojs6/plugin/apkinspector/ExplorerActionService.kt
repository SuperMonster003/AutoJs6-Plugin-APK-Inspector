package io.github.supermonster003.autojs6.plugin.apkinspector

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.autojs.plugin.explorer.api.IExplorerArchiveSession
import org.autojs.plugin.explorer.api.IExplorerActionPlugin

class ExplorerActionService : Service() {

    private val binder = object : IExplorerActionPlugin.Stub() {
        override fun getInfo() = apkInspectorPluginInfo()

        override fun getActionCatalog() = apkInspectorActionCatalog()

        override fun openArchive(
            source: ParcelFileDescriptor?,
            request: Bundle?,
        ): IExplorerArchiveSession? = null

        override fun openArchiveV11(
            source: ParcelFileDescriptor?,
            request: Bundle?,
        ): Bundle = Bundle.EMPTY
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
