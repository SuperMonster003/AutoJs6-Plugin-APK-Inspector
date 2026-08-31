package io.github.supermonster003.autojs6.plugin.apkinspector

import android.app.Application
import com.google.android.material.color.DynamicColors

class ApkInspectorApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
