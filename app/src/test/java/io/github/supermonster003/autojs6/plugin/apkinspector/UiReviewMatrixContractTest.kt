package io.github.supermonster003.autojs6.plugin.apkinspector

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiReviewMatrixContractTest {

    @Test
    fun matrixCoversEveryScreenFontThemeAndDirectionCombination() {
        val matrix = resourceText(MATRIX_RESOURCE).let { json ->
            Gson().fromJson(json, UiReviewMatrix::class.java)
        }

        assertEquals(1, matrix.schemaVersion)
        assertEquals("io.github.supermonster003.autojs6.plugin.apkinspector", matrix.application.packageName)
        assertEquals("application/x-apks", matrix.fixture.mimeType)
        assertEquals(3, matrix.axes.screens.size)
        assertEquals(3, matrix.axes.fonts.size)
        assertEquals(2, matrix.axes.themes.size)
        assertEquals(2, matrix.axes.directions.size)
        assertEquals(36, matrix.caseIds().size)
        assertEquals(matrix.caseIds().size, matrix.caseIds().toSet().size)

        assertEquals(setOf(411, 320, 693), matrix.axes.screens.map(ScreenProfile::expectedWidthDp).toSet())
        assertEquals(setOf("portrait", "landscape"), matrix.axes.screens.map(ScreenProfile::orientation).toSet())
        assertEquals(setOf(1.0, 1.5, 2.0), matrix.axes.fonts.map(FontProfile::scale).toSet())
        assertEquals(setOf("no", "yes"), matrix.axes.themes.map(ThemeProfile::nightMode).toSet())
        assertEquals(setOf("ltr", "rtl"), matrix.axes.directions.map(DirectionProfile::layoutDirection).toSet())
        assertEquals(setOf("en", "ar"), matrix.axes.directions.map(DirectionProfile::locale).toSet())
    }

    @Test
    fun matrixAuditsTheCompleteInteractiveReportAndManifestViewer() {
        val matrix = Gson().fromJson(resourceText(MATRIX_RESOURCE), UiReviewMatrix::class.java)

        assertEquals(
            listOf(
                "package_details_heading",
                "components_heading",
                "device_simulation_heading",
                "requested_permissions_heading",
                "findings_heading",
            ),
            matrix.report.requiredHeadingOrder,
        )
        assertEquals(
            setOf(
                "view_manifest",
                "components_heading",
                "device_simulation_heading",
                "device_simulation_description",
                "device_simulation_language",
                "reset_device_simulation",
                "findings",
            ),
            matrix.report.checkpoints.mapNotNull(Checkpoint::targetResourceId).toSet(),
        )
        assertEquals(
            "SHA-256:",
            matrix.report.checkpoints.single { it.id == "report-identifiers" }
                .targetContentDescriptionPrefix,
        )
        assertEquals(
            16,
            matrix.report.checkpoints.single { it.id == "report-bottom" }
                .minimumVisibleHeightDp,
        )
        assertTrue(matrix.report.requiredResourceIds.contains("report_field"))
        assertTrue(matrix.report.requiredResourceIds.containsAll(REPORT_CONTROLS))
        assertTrue(matrix.manifest.requiredResourceIds.containsAll(MANIFEST_CONTROLS))
        assertEquals("device_simulation_result", matrix.report.simulationResultCheckpoint.targetResourceId)
        assertEquals("manifest_search_status", matrix.manifest.checkpoint.targetResourceId)
        assertTrue(matrix.manifest.query.length >= 2)
        assertEquals(48, matrix.driver.minimumTouchTargetDp)
        assertTrue(matrix.driver.maxScrollSteps >= 30)
    }

    private fun UiReviewMatrix.caseIds(): List<String> = buildList {
        axes.screens.forEach { screen ->
            axes.fonts.forEach { font ->
                axes.themes.forEach { theme ->
                    axes.directions.forEach { direction ->
                        add(listOf(screen.id, font.id, theme.id, direction.id).joinToString("__"))
                    }
                }
            }
        }
    }

    private fun resourceText(path: String): String =
        requireNotNull(javaClass.getResourceAsStream(path)) { "Missing test resource: $path" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private data class UiReviewMatrix(
        val schemaVersion: Int = 0,
        val application: Application = Application(),
        val fixture: Fixture = Fixture(),
        val driver: Driver = Driver(),
        val axes: Axes = Axes(),
        val report: Report = Report(),
        val manifest: Manifest = Manifest(),
    )

    private data class Application(val packageName: String = "")

    private data class Fixture(val mimeType: String = "")

    private data class Driver(
        val maxScrollSteps: Int = 0,
        val minimumTouchTargetDp: Int = 0,
    )

    private data class Axes(
        val screens: List<ScreenProfile> = emptyList(),
        val fonts: List<FontProfile> = emptyList(),
        val themes: List<ThemeProfile> = emptyList(),
        val directions: List<DirectionProfile> = emptyList(),
    )

    private data class ScreenProfile(
        val id: String = "",
        val expectedWidthDp: Int = 0,
        val orientation: String = "",
    )

    private data class FontProfile(val id: String = "", val scale: Double = 0.0)

    private data class ThemeProfile(val id: String = "", val nightMode: String = "")

    private data class DirectionProfile(
        val id: String = "",
        val locale: String = "",
        val layoutDirection: String = "",
    )

    private data class Report(
        val requiredResourceIds: List<String> = emptyList(),
        val requiredHeadingOrder: List<String> = emptyList(),
        val checkpoints: List<Checkpoint> = emptyList(),
        val simulationResultCheckpoint: Checkpoint = Checkpoint(),
    )

    private data class Manifest(
        val query: String = "",
        val requiredResourceIds: List<String> = emptyList(),
        val checkpoint: Checkpoint = Checkpoint(),
    )

    private data class Checkpoint(
        val id: String = "",
        val targetResourceId: String? = null,
        val targetContentDescriptionPrefix: String? = null,
        val minimumVisibleHeightDp: Int = 0,
    )

    companion object {
        private const val MATRIX_RESOURCE = "/ui-review-matrix/matrix.json"
        private val REPORT_CONTROLS = setOf(
            "action_share_report",
            "view_manifest",
            "device_simulation_language",
            "device_simulation_density",
            "device_simulation_abi",
            "apply_device_simulation",
            "reset_device_simulation",
        )
        private val MANIFEST_CONTROLS = setOf(
            "action_find_manifest",
            "manifest_search_input",
            "close_manifest_search",
            "previous_manifest_match",
            "next_manifest_match",
        )
    }
}
