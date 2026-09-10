package io.github.supermonster003.autojs6.plugin.apkinspector

import android.os.Bundle
import org.autojs.plugin.explorer.api.ExplorerActionCatalogKeys
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExplorerActionCatalogTest {
    @Test
    fun primaryAndOverflowActionsDeclareTheRequiredSingleFileContract() {
        @Suppress("DEPRECATION")
        val actions = requireNotNull(apkInspectorActionCatalog()
            .getParcelableArrayList<Bundle>(ExplorerActionCatalogKeys.ACTIONS))
        assertEquals(2, actions.size)
        assertEquals(setOf(ExplorerActionValues.PLACEMENT_PRIMARY, ExplorerActionValues.PLACEMENT_OVERFLOW),
            actions.map { it.getInt(ExplorerActionCatalogKeys.PLACEMENT) }.toSet())
        assertEquals(2, actions.map { it.getString(ExplorerActionCatalogKeys.ID) }.toSet().size)
        actions.forEach { action ->
            assertTrue(ApkInspectorPlugin.acceptsActionId(action.getString(ExplorerActionCatalogKeys.ID)))
            assertEquals(ExplorerActionValues.CARDINALITY_SINGLE, action.getInt(ExplorerActionCatalogKeys.CARDINALITY, -1))
            assertEquals(ExplorerActionValues.ACCESS_READ_ONLY, action.getInt(ExplorerActionCatalogKeys.ACCESS_MODE, -1))
            assertEquals(ExplorerActionValues.TARGET_FILE, action.getInt(ExplorerActionCatalogKeys.TARGET_KIND, -1))
            assertEquals(ExplorerActionValues.OUTPUT_NONE, action.getInt(ExplorerActionCatalogKeys.OUTPUT_MODE, -1))
            assertEquals(ApkInspectorPlugin.EXTENSIONS.toSet(), action.getStringArrayList(ExplorerActionCatalogKeys.EXTENSIONS)?.toSet())
        }
    }
}
