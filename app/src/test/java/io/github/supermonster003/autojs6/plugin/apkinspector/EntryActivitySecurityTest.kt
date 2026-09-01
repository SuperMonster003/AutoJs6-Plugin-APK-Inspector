package io.github.supermonster003.autojs6.plugin.apkinspector

import android.app.Activity
import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.autojs.plugin.explorer.api.ExplorerActionHostSessionKeys
import org.autojs.plugin.explorer.api.ExplorerActionIntentExtras
import org.autojs.plugin.explorer.api.ExplorerActionIntentValues
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionTargetKeys
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.autojs.plugin.explorer.api.IExplorerActionHostSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowContentResolver
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class EntryActivitySecurityTest {

    @Before
    fun resetActivityProbes() {
        RejectProbeExplorerActivity.stageCalls.set(0)
        RejectProbeExternalActivity.stageCalls.set(0)
        SuspendingExplorerActivity.reset()
        SuspendingExternalActivity.reset()
    }

    @Test
    fun explorerEntryRejectsSpoofedIntentBeforeStaging() {
        val intent = validExplorerIntent(RecordingHostSession()).apply {
            action = Intent.ACTION_VIEW
        }

        val controller = createActivity(RejectProbeExplorerActivity::class.java, intent)

        assertRejected(controller.get())
        assertEquals(0, RejectProbeExplorerActivity.stageCalls.get())
        controller.destroy()
    }

    @Test
    fun externalEntryRejectsSpoofedIntentBeforeStaging() {
        val intent = validExternalIntent().apply {
            action = Intent.ACTION_SEND
        }

        val controller = createActivity(RejectProbeExternalActivity::class.java, intent)

        assertRejected(controller.get())
        assertEquals(0, RejectProbeExternalActivity.stageCalls.get())
        controller.destroy()
    }

    @Test
    fun explorerEntryRejectsEveryForbiddenGrantBeforeStaging() {
        listOf(
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        ).forEach { forbiddenGrant ->
            val controller = createActivity(
                RejectProbeExplorerActivity::class.java,
                validExplorerIntent(RecordingHostSession()).addFlags(forbiddenGrant),
            )

            assertRejected(controller.get())
            controller.destroy()
        }

        assertEquals(0, RejectProbeExplorerActivity.stageCalls.get())
    }

    @Test
    fun externalEntryRejectsEveryForbiddenGrantBeforeStaging() {
        listOf(
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        ).forEach { forbiddenGrant ->
            val controller = createActivity(
                RejectProbeExternalActivity::class.java,
                validExternalIntent().addFlags(forbiddenGrant),
            )

            assertRejected(controller.get())
            controller.destroy()
        }

        assertEquals(0, RejectProbeExternalActivity.stageCalls.get())
    }

    @Test
    fun explorerEntryRejectsOversizedDeclarationBeforeStaging() {
        val controller = createActivity(
            RejectProbeExplorerActivity::class.java,
            validExplorerIntent(
                hostSession = RecordingHostSession(),
                declaredSize = PackageRequestPolicy.MAX_PACKAGE_BYTES + 1L,
            ),
        )

        assertRejected(controller.get())
        assertEquals(0, RejectProbeExplorerActivity.stageCalls.get())
        controller.destroy()
    }

    @Test
    fun externalEntryRejectsOversizedProviderMetadataBeforeOpeningContent() {
        val provider = OversizedPackageProvider()
        ShadowContentResolver.registerProviderInternal(EXTERNAL_AUTHORITY, provider)
        val controller = Robolectric.buildActivity(
            ExternalViewerActivity::class.java,
            validExternalIntent(EXTERNAL_URI),
        ).create().start().resume()
        val activity = controller.get()

        awaitFinishing(activity)

        assertRejected(activity)
        val providerTrace = "queries=${provider.queriedColumns}, opens=${provider.contentOpenCalls.get()}"
        assertTrue(providerTrace, provider.queriedColumns.contains(OpenableColumns.SIZE))
        assertTrue(providerTrace, !provider.queriedColumns.contains(OpenableColumns.DISPLAY_NAME))
        assertEquals(
            "oversized metadata must fail before the provider content is opened",
            0,
            provider.contentOpenCalls.get(),
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun destroyingConcurrentEntriesCancelsBothStagingJobsWithoutLaunching() {
        val hostSession = RecordingHostSession()
        val explorerController = Robolectric.buildActivity(
            SuspendingExplorerActivity::class.java,
            validExplorerIntent(hostSession),
        ).create().start().resume()
        val externalController = Robolectric.buildActivity(
            SuspendingExternalActivity::class.java,
            validExternalIntent(),
        ).create().start().resume()
        val explorerActivity = explorerController.get()
        val externalActivity = externalController.get()

        assertTrue(SuspendingExplorerActivity.started.isCompleted)
        assertTrue(SuspendingExternalActivity.started.isCompleted)
        assertNull(shadowOf(explorerActivity).peekNextStartedActivity())
        assertNull(shadowOf(externalActivity).peekNextStartedActivity())

        explorerController.pause().stop().destroy()
        externalController.pause().stop().destroy()

        assertTrue(SuspendingExplorerActivity.cancelled.isCompleted)
        assertTrue(SuspendingExternalActivity.cancelled.isCompleted)
        assertEquals(1, hostSession.closeCalls.get())
        assertNull(shadowOf(explorerActivity).peekNextStartedActivity())
        assertNull(shadowOf(externalActivity).peekNextStartedActivity())
    }

    private fun <T : Activity> createActivity(
        activityClass: Class<T>,
        intent: Intent,
    ): ActivityController<T> = Robolectric.buildActivity(activityClass, intent).create()

    private fun assertRejected(activity: Activity) {
        assertTrue("entry Activity must finish after rejection", activity.isFinishing)
        assertNull("rejected input must not launch the inspector", shadowOf(activity).peekNextStartedActivity())
    }

    private fun awaitFinishing(activity: Activity) {
        val deadline = System.nanoTime() + ASYNC_TIMEOUT_NANOS
        while (!activity.isFinishing && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5L)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("entry Activity did not finish before timeout", activity.isFinishing)
    }

    private fun validExplorerIntent(
        hostSession: RecordingHostSession,
        declaredSize: Long = VALID_PACKAGE_SIZE,
    ): Intent {
        val target = Bundle().apply {
            putString(ExplorerActionTargetKeys.ID, "target-1")
            putParcelable(ExplorerActionTargetKeys.URI, EXPLORER_TARGET_URI)
            putString(ExplorerActionTargetKeys.DISPLAY_NAME, DISPLAY_NAME)
            putInt(ExplorerActionTargetKeys.KIND, ExplorerActionValues.TARGET_FILE)
            putString(ExplorerActionTargetKeys.MIME_TYPE, PACKAGE_MIME)
            putLong(ExplorerActionTargetKeys.SIZE, declaredSize)
            putLong(ExplorerActionTargetKeys.LAST_MODIFIED, 1L)
        }
        val hostSessionBundle = Bundle().apply {
            putBinder(ExplorerActionHostSessionKeys.BINDER, hostSession.binder)
        }
        return Intent(ExplorerActionPluginActions.EXECUTE).apply {
            setDataAndType(EXPLORER_TARGET_URI, PACKAGE_MIME)
            clipData = ClipData.newRawUri("target", EXPLORER_TARGET_URI)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(ExplorerActionIntentExtras.ACTION_ID, ApkInspectorPlugin.ACTION_ID)
            putExtra(ExplorerActionIntentExtras.PROTOCOL_VERSION, ExplorerActionProtocol.VERSION)
            putExtra(ExplorerActionIntentExtras.SOURCE_SURFACE, ExplorerActionIntentValues.SOURCE_SURFACE_MAIN)
            putExtra(ExplorerActionIntentExtras.REQUEST_ID, REQUEST_ID)
            putExtra(ExplorerActionIntentExtras.HOST_VERSION_CODE, ApkInspectorPlugin.REQUIRED_HOST_VERSION)
            putExtra(ExplorerActionIntentExtras.PARENT_URI, EXPLORER_PARENT_URI)
            putExtra(ExplorerActionIntentExtras.DISPLAY_NAME, DISPLAY_NAME)
            putExtra(ExplorerActionIntentExtras.SIZE, declaredSize)
            putParcelableArrayListExtra(ExplorerActionIntentExtras.TARGETS, arrayListOf(target))
            putExtra(ExplorerActionIntentExtras.HOST_SESSION, hostSessionBundle)
        }
    }

    private fun validExternalIntent(uri: Uri = EXTERNAL_URI): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, PACKAGE_MIME)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private class RecordingHostSession {
        val closeCalls = AtomicInteger()
        val binder = Binder()

        private val localInterface = Proxy.newProxyInstance(
            IExplorerActionHostSession::class.java.classLoader,
            arrayOf(IExplorerActionHostSession::class.java),
        ) { proxy, method, arguments ->
            when (method.name) {
                "asBinder" -> binder
                "close" -> {
                    closeCalls.incrementAndGet()
                    null
                }
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "RecordingHostSession"
                else -> null
            }
        } as IExplorerActionHostSession

        init {
            binder.attachInterface(localInterface, IExplorerActionHostSession.DESCRIPTOR)
        }
    }

    private class OversizedPackageProvider : ContentProvider() {
        val contentOpenCalls = AtomicInteger()
        val queriedColumns = ConcurrentLinkedQueue<String>()

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val columns = projection?.toList().orEmpty()
            queriedColumns.addAll(columns)
            return MatrixCursor(columns.toTypedArray()).apply {
                addRow(columns.map { column ->
                    when (column) {
                        OpenableColumns.DISPLAY_NAME -> DISPLAY_NAME
                        OpenableColumns.SIZE -> PackageRequestPolicy.MAX_PACKAGE_BYTES + 1L
                        else -> null
                    }
                })
            }
        }

        override fun getType(uri: Uri): String = PACKAGE_MIME

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            contentOpenCalls.incrementAndGet()
            throw AssertionError("oversized content must not be opened")
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private companion object {
        const val PACKAGE_MIME = "application/vnd.android.package-archive"
        const val DISPLAY_NAME = "sample.apk"
        const val VALID_PACKAGE_SIZE = 42L
        const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000"
        const val EXTERNAL_AUTHORITY = "external-entry-fixture.example"
        const val ASYNC_TIMEOUT_NANOS = 5_000_000_000L

        val EXPLORER_PARENT_URI: Uri = Uri.parse("content://explorer-entry-fixture.example/root")
        val EXPLORER_TARGET_URI: Uri = Uri.parse("content://explorer-entry-fixture.example/root/$DISPLAY_NAME")
        val EXTERNAL_URI: Uri = Uri.parse("content://$EXTERNAL_AUTHORITY/root/$DISPLAY_NAME")
    }
}

class RejectProbeExplorerActivity : ExplorerActionActivity() {
    override suspend fun stagePackage(seed: PackageInputSeed): StagedPackage? {
        stageCalls.incrementAndGet()
        return null
    }

    companion object {
        val stageCalls = AtomicInteger()
    }
}

class RejectProbeExternalActivity : ExternalViewerActivity() {
    override suspend fun stagePackage(seed: PackageInputSeed): StagedPackage? {
        stageCalls.incrementAndGet()
        return null
    }

    companion object {
        val stageCalls = AtomicInteger()
    }
}

class SuspendingExplorerActivity : ExplorerActionActivity() {
    override suspend fun stagePackage(seed: PackageInputSeed): StagedPackage? {
        started.complete(Unit)
        try {
            awaitCancellation()
        } finally {
            cancelled.complete(Unit)
        }
    }

    companion object {
        lateinit var started: CompletableDeferred<Unit>
        lateinit var cancelled: CompletableDeferred<Unit>

        fun reset() {
            started = CompletableDeferred()
            cancelled = CompletableDeferred()
        }
    }
}

class SuspendingExternalActivity : ExternalViewerActivity() {
    override suspend fun stagePackage(seed: PackageInputSeed): StagedPackage? {
        started.complete(Unit)
        try {
            awaitCancellation()
        } finally {
            cancelled.complete(Unit)
        }
    }

    companion object {
        lateinit var started: CompletableDeferred<Unit>
        lateinit var cancelled: CompletableDeferred<Unit>

        fun reset() {
            started = CompletableDeferred()
            cancelled = CompletableDeferred()
        }
    }
}
