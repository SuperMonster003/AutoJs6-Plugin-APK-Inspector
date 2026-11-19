-keep class io.github.supermonster003.autojs6.plugin.apkinspector.ExplorerActionService { *; }
-keep class io.github.supermonster003.autojs6.plugin.apkinspector.ExplorerActionActivity { *; }
-keep class io.github.supermonster003.autojs6.plugin.apkinspector.ExternalViewerActivity { *; }
-keep class io.github.supermonster003.autojs6.plugin.apkinspector.ApkInspectorActivity { *; }
-keep class io.github.supermonster003.autojs6.plugin.apkinspector.ManifestViewerActivity { *; }
-keep class io.github.supermonster003.autojs6.plugin.apkinspector.WakeActivity { *; }
-keep class org.autojs.plugin.common.api.PluginInfo { *; }
-keep class org.autojs.plugin.explorer.api.** { *; }

# ARSCLib bundles its own copies of framework classes (android.util.AttributeSet,
# android.content.res.XmlResourceParser and the org.xmlpull.v1 API). R8 treats them as program classes and
# would obfuscate them, so the code that uses them no longer refers to the framework types:
# - android.util.AttributeSet -> vb (1.1.0 - 1.2.4) changed the signature of every override that takes an
#   AttributeSet; AppCompatDelegateImpl.onCreateView no longer implemented LayoutInflater.Factory2 and
#   opening the inspector crashed with AbstractMethodError.
# - org.xmlpull.v1.XmlPullParser -> ij0 made AppCompat's Toolbar call getEventType() on an interface the
#   framework parser does not implement (IncompatibleClassChangeError once the first issue was fixed).
# Keeping their names and members makes them match the framework classes, which the device loads first.
# FrameworkClassCopyKeepTest lists them from the ARSCLib jar and fails when a copy has no rule here.
-keep class android.util.AttributeSet { *; }
-keep class android.content.res.XmlResourceParser { *; }
-keep class org.xmlpull.v1.** { *; }
