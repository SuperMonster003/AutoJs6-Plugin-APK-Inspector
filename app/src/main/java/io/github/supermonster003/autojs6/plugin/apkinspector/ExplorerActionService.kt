package io.github.supermonster003.autojs6.plugin.apkinspector

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerHostFileInfoKeys
import org.autojs.plugin.explorer.api.ExplorerHostFileInfoValues
import org.autojs.plugin.explorer.api.IExplorerArchiveSession
import org.autojs.plugin.explorer.api.IExplorerActionPlugin

class ExplorerActionService : Service() {

    private val binder = object : IExplorerActionPlugin.Stub() {
        override fun getInfo() = apkInspectorPluginInfo().apply { supportedAbis = emptyArray() }

        override fun getActionCatalog() = apkInspectorActionCatalog()

        override fun openArchive(
            source: ParcelFileDescriptor?,
            request: Bundle?,
        ): IExplorerArchiveSession? = null

        override fun openArchiveV11(
            source: ParcelFileDescriptor?,
            request: Bundle?,
        ): Bundle = Bundle.EMPTY

        override fun getHostFileInfo(
            source: ParcelFileDescriptor?,
            request: Bundle?,
        ): Bundle = provideHostFileInfo(source, request)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun provideHostFileInfo(
        source: ParcelFileDescriptor?,
        requestBundle: Bundle?,
    ): Bundle {
        source ?: return hostFileInfoError(ExplorerHostFileInfoValues.ERROR_SOURCE_UNAVAILABLE)
        return try {
            source.use { descriptor ->
                val request = HostFileInfoRequestPolicy.resolve(requestBundle)
                    ?: return hostFileInfoError(ExplorerHostFileInfoValues.ERROR_INVALID_REQUEST)
                runBlocking(Dispatchers.IO) {
                    val staged = HostFileInfoStager.stage(
                        context = this@ExplorerActionService,
                        source = descriptor,
                        request = request,
                    )
                    try {
                        if (!staged.sha256.equals(request.expectedSourceSha256, ignoreCase = true)) {
                            return@runBlocking hostFileInfoError(
                                ExplorerHostFileInfoValues.ERROR_SOURCE_MISMATCH,
                            )
                        }
                        HostFileInfoInspector.inspect(this@ExplorerActionService, staged)
                    } finally {
                        staged.file.parentFile?.deleteRecursively()
                    }
                }
            }
        } catch (error: Exception) {
            hostFileInfoError(
                errorCode = ExplorerHostFileInfoValues.ERROR_INSPECTION_FAILED,
                error = error,
            )
        }
    }

    private fun hostFileInfoError(
        errorCode: Int,
        error: Exception? = null,
    ): Bundle = Bundle().apply {
        putInt(ExplorerHostFileInfoKeys.VERSION, ExplorerActionProtocol.HOST_FILE_INFO_VERSION)
        putInt(ExplorerHostFileInfoKeys.ERROR_CODE, errorCode)
        error?.message
            ?.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(ExplorerActionProtocol.MAX_HOST_FILE_INFO_ERROR_MESSAGE_LENGTH)
            ?.takeIf(String::isNotEmpty)
            ?.let { message -> putString(ExplorerHostFileInfoKeys.ERROR_MESSAGE, message) }
    }
}
