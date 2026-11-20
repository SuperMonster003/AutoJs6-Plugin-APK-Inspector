package io.github.supermonster003.autojs6.plugin.apkinspector

import android.app.Activity
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P21.3 of the plugin center roadmap: the release history is a page of its own (it used to be a dialog with the raw
 * markdown). Its title is "Release history" with the plugin name as the subtitle; the top bar menu of the inspection
 * report opens it.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseHistoryPageInstrumentationTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @After fun finishActivities() {
        instrumentation.setInTouchMode(true)
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach(Activity::finish)
        }
        instrumentation.waitForIdleSync()
    }

    /** Unmerged: the count chips sit inside the clickable header, which merges its children. */
    private fun exists(tag: String): Boolean =
        runCatching { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    private fun resumed(): Activity? {
        var activity: Activity? = null
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return activity
    }

    private fun assertTitleAndSubtitle() {
        val inTopBar = hasAnyAncestor(hasTestTag("$TAG.topBar"))
        compose.onNode(hasText(context.getString(R.string.release_history)) and inTopBar).assertIsDisplayed()
        compose.onNode(hasText(context.getString(R.string.app_name)) and inTopBar).assertIsDisplayed()
    }

    @Test fun theTitleIsReleaseHistoryAndTheSubtitleThePluginName() {
        ActivityScenario.launch(ReleaseHistoryActivity::class.java).use {
            compose.waitUntil(TIMEOUT) { exists("$TAG.list") }
            assertTitleAndSubtitle()
            assertFalse(exists("$TAG.emptyState"))
        }
    }

    private fun versions(scenario: ActivityScenario<ReleaseHistoryActivity>): List<String> {
        var versions = emptyList<String>()
        scenario.onActivity { activity ->
            val candidates = ReleaseHistoryAssetPolicy.candidatesFor(activity.resources.configuration.locales[0])
            versions = loadReleaseHistory(candidates) { path ->
                activity.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
            }.map { it.version }
        }
        return versions
    }

    private fun stateOf(version: String, expanded: Boolean) {
        val state = context.getString(if (expanded) R.string.release_history_expanded else R.string.release_history_collapsed)
        compose.onNodeWithTag("$TAG.entry.$version")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))
    }

    /**
     * The newest release starts expanded, the others show their category counts; a header expands or collapses its
     * release, the top bar expands or collapses all of them, and the choice survives recreation.
     */
    @Test fun releasesExpandAndCollapse() {
        ActivityScenario.launch(ReleaseHistoryActivity::class.java).use { scenario ->
            compose.waitUntil(TIMEOUT) { exists("$TAG.list") }
            val versions = versions(scenario)
            assertTrue("releases: ${versions.size}", versions.size > 2)
            val (newest, second) = versions
            assertTrue(exists("$TAG.body.$newest"))
            assertFalse(exists("$TAG.counts.$newest"))
            assertFalse(exists("$TAG.body.$second"))
            assertTrue(exists("$TAG.counts.$second"))
            stateOf(newest, expanded = true)
            stateOf(second, expanded = false)

            compose.onNodeWithTag("$TAG.entry.$second").performClick()
            compose.waitUntil(TIMEOUT) { exists("$TAG.body.$second") }
            compose.onNodeWithTag("$TAG.entry.$newest").performClick()
            compose.waitUntil(TIMEOUT) { !exists("$TAG.body.$newest") && exists("$TAG.counts.$newest") }

            scenario.recreate()
            compose.waitUntil(TIMEOUT) { exists("$TAG.list") }
            assertTrue(exists("$TAG.body.$second"))
            assertFalse(exists("$TAG.body.$newest"))

            compose.onNodeWithTag("$TAG.toggleAll")
                .assertContentDescriptionEquals(context.getString(R.string.release_history_expand_all))
                .performClick()
            compose.waitUntil(TIMEOUT) { exists("$TAG.body.$newest") }
            compose.onNodeWithTag("$TAG.toggleAll")
                .assertContentDescriptionEquals(context.getString(R.string.release_history_collapse_all))
                .performClick()
            compose.waitUntil(TIMEOUT) { !exists("$TAG.body.$newest") && !exists("$TAG.body.$second") }
        }
    }

    /** The inspection report keeps its top bar when it has nothing to inspect, so its menu can open the page. */
    @Test fun theReportMenuOpensThePage() {
        ActivityScenario.launch(ApkInspectorActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
                assertTrue(toolbar.menu.performIdentifierAction(R.id.action_release_history, 0))
            }
            compose.waitUntil(TIMEOUT) { resumed() is ReleaseHistoryActivity && exists("$TAG.list") }
            assertTitleAndSubtitle()
        }
    }

    private fun key(code: Int) {
        instrumentation.sendKeyDownUpSync(code)
        compose.waitForIdle()
    }

    /**
     * Leaves touch mode as a keyboard user does. Newer versions take [android.app.Instrumentation.setInTouchMode] at
     * once; older ones (API 24) learn it only from a key, so an up arrow goes first.
     */
    private fun leaveTouchMode() {
        instrumentation.setInTouchMode(false)
        key(KeyEvent.KEYCODE_DPAD_UP)
    }

    /** The test tag of the focused control (or of its nearest tagged ancestor), null when nothing has focus. */
    private fun focusedTag(): String? {
        val node = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() ?: return null
        return generateSequence(node) { it.parent }.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.TestTag) } ?: UNTAGGED
    }

    /** The window with input focus as `dumpsys window` reports it; for failure messages only (it connects UiAutomation). */
    private fun focusedWindow(): String = runCatching {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys window"))
            .bufferedReader().useLines { lines -> lines.map(String::trim).filter { it.startsWith("mCurrentFocus=") }.distinct().joinToString() }
            .ifEmpty { "mCurrentFocus not in dumpsys" }
    }.getOrElse { "dumpsys failed: $it" }

    /** Keys go to the focused window: while a system dialog (an ANR, say) has focus, every Tab would be lost. */
    private fun awaitWindowFocus(scenario: ActivityScenario<*>, visited: List<String> = emptyList()) {
        var focused = false
        try {
            compose.waitUntil(TIMEOUT) { scenario.onActivity { focused = it.hasWindowFocus() }; focused }
        } catch (timeout: ComposeTimeoutException) {
            fail("the page has no window focus after $TIMEOUT ms; visited: $visited; ${focusedWindow()}")
        }
    }

    /** Presses Tab until focus returns to a control seen before; every focused control must be on screen. */
    private fun tabCycle(scenario: ActivityScenario<*>): List<String> {
        val visited = mutableListOf<String>()
        repeat(MAX_TABS) {
            awaitWindowFocus(scenario, visited)
            key(KeyEvent.KEYCODE_TAB)
            val tag = focusedTag() ?: return@repeat
            if (tag in visited) return visited
            compose.onNode(isFocused(), useUnmergedTree = true).assertIsDisplayed()
            visited += tag
        }
        return visited
    }

    /** Tab passes the back button and then every release in turn, scrolled into view, down to the oldest one. */
    @Test fun theReleaseHistoryCanBeReadToTheEndFromTheKeyboard() {
        ActivityScenario.launch(ReleaseHistoryActivity::class.java).use { scenario ->
            compose.waitUntil(TIMEOUT) { exists("$TAG.list") }
            val versions = versions(scenario)
            assertTrue("releases: ${versions.size}", versions.size > 2)
            awaitWindowFocus(scenario)
            leaveTouchMode()
            val visited = tabCycle(scenario)
            fun expect(ok: Boolean, what: String) { if (!ok) fail("$what; visited: $visited; ${focusedWindow()}") }
            expect("$TAG.navigation" in visited, "Tab never reached the back button")
            expect(UNTAGGED !in visited, "something focusable has no name")
            val expected = versions.map { "$TAG.entry.$it" }
            val entries = visited.filter { it.startsWith("$TAG.entry.") }
            expect(entries == expected, "the releases in Tab order: expected $expected, got $entries")
        }
    }

    private companion object {
        const val TAG = RELEASE_HISTORY_TAG_PREFIX
        const val TIMEOUT = 15_000L
        const val MAX_TABS = 120
        const val UNTAGGED = "(untagged)"
    }
}

/** With no usable changelog the page shows the warning icon and the load failure message instead of a list. */
@RunWith(AndroidJUnit4::class)
class ReleaseHistoryEmptyStateInstrumentationTest {

    @get:Rule
    val compose = createComposeRule()

    @Test fun showsTheLoadFailureWithoutTheToggle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val palette = ReleaseHistoryPalette(
            dark = false,
            primary = 0xFF8A4F00.toInt(),
            primaryContainer = 0xFFFFDCC0.toInt(),
            background = 0xFFFFF8F4.toInt(),
            surface = 0xFFFFF8F4.toInt(),
            onSurface = 0xFF201A17.toInt(),
            onSurfaceVariant = 0xFF51443A.toInt(),
            outlineVariant = 0xFFD6C3B5.toInt(),
        )
        compose.setContent { PluginReleaseHistoryTheme(palette) { ReleaseHistoryScreen(entries = emptyList(), onBack = {}) } }
        compose.onNodeWithTag("$RELEASE_HISTORY_TAG_PREFIX.emptyState").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.release_history_load_failed)).assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasTestTag("$RELEASE_HISTORY_TAG_PREFIX.toggleAll")).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasTestTag("$RELEASE_HISTORY_TAG_PREFIX.list")).fetchSemanticsNodes().isEmpty())
    }
}
