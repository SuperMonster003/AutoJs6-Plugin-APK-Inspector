package io.github.supermonster003.autojs6.plugin.apkinspector

import android.content.Context
import android.os.Build
import android.os.Bundle
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.explorer.api.ExplorerActionCapabilityKeys
import org.autojs.plugin.explorer.api.ExplorerActionCatalogKeys
import org.autojs.plugin.explorer.api.ExplorerActionPluginIds
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues

internal object ApkInspectorPlugin {
    const val ID = "apk-inspector"
    const val ACTION_ID = "inspect-android-package"
    const val VARIANT = "default"
    const val REQUIRED_HOST_VERSION = 5277L
    const val LABEL_RESOURCE_NAME = "action_inspect_android_package"
    const val LABEL_FALLBACK = "Inspect Android package"
    const val ACTIVITY_CLASS_NAME =
        "io.github.supermonster003.autojs6.plugin.apkinspector.ExplorerActionActivity"
    const val ACTION_PRIORITY = 120

    val MIME_TYPES = emptyArray<String>()
    val EXTENSIONS = arrayOf("aab", "apk", "apkm", "apks", "apkz", "xapk")
}

internal fun Context.apkInspectorPluginInfo(): PluginInfo {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    return PluginInfo().apply {
        name = getString(R.string.app_name)
        description = getString(R.string.plugin_description)
        instruction = null
        author = getString(R.string.plugin_author)
        collaborators = null
        versionName = packageInfo.versionName.orEmpty()
        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        versionDate = getString(R.string.plugin_version_date)
        id = ApkInspectorPlugin.ID
        engine = ExplorerActionPluginIds.ENGINE
        variant = ApkInspectorPlugin.VARIANT
        supportedAbis = emptyArray()
        capabilities = Bundle().apply {
            putLong(PluginCapabilityKeys.REQUIRES_HOST_VERSION, ApkInspectorPlugin.REQUIRED_HOST_VERSION)
            putInt(ExplorerActionCapabilityKeys.PROTOCOL_VERSION, ExplorerActionProtocol.VERSION)
            putInt(
                ExplorerActionCapabilityKeys.HOST_FILE_INFO_VERSION,
                ExplorerActionProtocol.HOST_FILE_INFO_VERSION,
            )
        }
    }
}

internal fun apkInspectorActionCatalog(): Bundle {
    val action = Bundle().apply {
        putString(ExplorerActionCatalogKeys.ID, ApkInspectorPlugin.ACTION_ID)
        putString(ExplorerActionCatalogKeys.LABEL_RESOURCE_NAME, ApkInspectorPlugin.LABEL_RESOURCE_NAME)
        putString(ExplorerActionCatalogKeys.LABEL_FALLBACK, ApkInspectorPlugin.LABEL_FALLBACK)
        putString(ExplorerActionCatalogKeys.ACTIVITY_CLASS_NAME, ApkInspectorPlugin.ACTIVITY_CLASS_NAME)
        putInt(ExplorerActionCatalogKeys.PRIORITY, ApkInspectorPlugin.ACTION_PRIORITY)
        putInt(ExplorerActionCatalogKeys.TARGET_KIND, ExplorerActionValues.TARGET_FILE)
        putInt(ExplorerActionCatalogKeys.ACCESS_MODE, ExplorerActionValues.ACCESS_READ_ONLY)
        putInt(ExplorerActionCatalogKeys.PLACEMENT, ExplorerActionValues.PLACEMENT_PRIMARY)
        putStringArrayList(ExplorerActionCatalogKeys.MIME_TYPES, arrayListOf())
        putStringArrayList(
            ExplorerActionCatalogKeys.EXTENSIONS,
            ArrayList(ApkInspectorPlugin.EXTENSIONS.asList()),
        )
        putStringArrayList(
            ExplorerActionCatalogKeys.RELATED_FILE_SUFFIXES,
            arrayListOf(".idsig"),
        )
    }
    return Bundle().apply {
        putInt(ExplorerActionCatalogKeys.PROTOCOL_VERSION, ExplorerActionProtocol.VERSION)
        putParcelableArrayList(ExplorerActionCatalogKeys.ACTIONS, arrayListOf(action))
    }
}
