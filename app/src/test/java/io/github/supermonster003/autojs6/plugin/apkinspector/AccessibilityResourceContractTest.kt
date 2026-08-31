package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

class AccessibilityResourceContractTest {

    @Test
    fun primaryLayoutsFollowLocaleDirectionAndNameNavigationActions() {
        PRIMARY_LAYOUTS.forEach { layout ->
            val document = parseResource("layout/$layout")

            assertEquals("locale", document.documentElement.androidAttribute("layoutDirection"))
            val toolbar = document.elements(MATERIAL_TOOLBAR).single()
            assertEquals(
                "@string/action_navigate_up",
                toolbar.appAttribute("navigationContentDescription"),
            )
        }
    }

    @Test
    fun reportSectionHeadingsAreBoundToCompatHeadingSemantics() {
        val document = parseResource("layout/activity_apk_inspector.xml")
        val textViewIds = document.elements("TextView")
            .map { element -> element.androidAttribute("id") }
            .toSet()
        HEADING_IDS.forEach { id -> assertTrue("Missing section heading $id", id in textViewIds) }

        val source = projectFile(
            "src/main/java/io/github/supermonster003/autojs6/plugin/apkinspector/ApkInspectorActivity.kt",
        ).let(Files::readAllBytes).toString(Charsets.UTF_8)
        val accessibilitySetup = source
            .substringAfter("private fun configureAccessibility()")
            .substringBefore("private fun inspect(")
        assertTrue(accessibilitySetup.contains("ViewCompat.setAccessibilityHeading"))
        HEADING_BINDINGS.forEach { binding ->
            assertTrue("Missing compat heading binding $binding", accessibilitySetup.contains(binding))
        }
        assertTrue(accessibilitySetup.contains("ViewCompat.setAccessibilityPaneTitle"))
    }

    @Test
    fun customInteractiveViewsMeetTouchAndDescriptionContracts() {
        val copyableField = parseResource("layout/item_copyable_report_field.xml").documentElement
        assertEquals("48dp", copyableField.androidAttribute("minHeight"))
        assertEquals("center_vertical|start", copyableField.androidAttribute("gravity"))
        assertEquals("viewStart", copyableField.androidAttribute("textAlignment"))

        val spinnerItem = parseResource("layout/item_device_simulation_spinner.xml").documentElement
        assertEquals("48dp", spinnerItem.androidAttribute("minHeight"))
        assertEquals("center_vertical|start", spinnerItem.androidAttribute("gravity"))
        assertEquals("viewStart", spinnerItem.androidAttribute("textAlignment"))

        val report = parseResource("layout/activity_apk_inspector.xml")
        report.elements("androidx.appcompat.widget.AppCompatSpinner").forEach { spinner ->
            assertEquals("48dp", spinner.androidAttribute("minHeight"))
        }
        val appIcon = report.elements("ImageView").single {
            it.androidAttribute("id") == "@+id/app_icon"
        }
        assertTrue(appIcon.androidAttribute("contentDescription").startsWith("@string/"))

        val manifest = parseResource("layout/activity_manifest_viewer.xml")
        val imageButtons = manifest.elements("androidx.appcompat.widget.AppCompatImageButton")
        assertTrue(imageButtons.isNotEmpty())
        imageButtons.forEach { button ->
            assertEquals("48dp", button.androidAttribute("layout_width"))
            assertEquals("48dp", button.androidAttribute("layout_height"))
            assertTrue(button.androidAttribute("contentDescription").startsWith("@string/"))
        }
    }

    @Test
    fun iconMenusAndBackArrowExposeAccessibleBehavior() {
        listOf("menu_apk_inspector.xml", "menu_manifest_viewer.xml").forEach { menu ->
            val items = parseResource("menu/$menu").elements("item")
            assertTrue(items.isNotEmpty())
            items.filter { it.androidAttribute("icon").isNotEmpty() }.forEach { item ->
                assertTrue(item.androidAttribute("title").startsWith("@string/"))
            }
        }

        val backArrow = parseResource("drawable/ic_arrow_back.xml").documentElement
        assertEquals("true", backArrow.androidAttribute("autoMirrored"))
    }

    @Test
    fun dynamicResultsAndErrorsUseLiveRegions() {
        val report = parseResource("layout/activity_apk_inspector.xml")
        val byId = report.elements("TextView").associateBy { it.androidAttribute("id") }
        assertEquals(
            "polite",
            byId.getValue("@+id/device_simulation_result")
                .androidAttribute("accessibilityLiveRegion"),
        )
        assertEquals(
            "assertive",
            byId.getValue("@+id/error").androidAttribute("accessibilityLiveRegion"),
        )

        val manifest = parseResource("layout/activity_manifest_viewer.xml")
        val searchStatus = manifest.elements("TextView").single {
            it.androidAttribute("id") == "@+id/manifest_search_status"
        }
        assertEquals("polite", searchStatus.androidAttribute("accessibilityLiveRegion"))
    }

    @Test
    fun accessibilityStringsCoverEverySupportedResourceLocale() {
        STRING_DIRECTORIES.forEach { directory ->
            val strings = parseResource("$directory/strings.xml").elements("string")
                .associateBy { it.getAttribute("name") }
            ACCESSIBILITY_STRING_NAMES.forEach { name ->
                assertNotNull("$name missing from $directory", strings[name])
                assertTrue("$name is blank in $directory", strings.getValue(name).textContent.isNotBlank())
            }
        }
    }

    private fun parseResource(relativePath: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        return Files.newInputStream(projectFile("src/main/res/$relativePath")).use { input ->
            factory.newDocumentBuilder().parse(input)
        }
    }

    private fun projectFile(moduleRelativePath: String): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val candidates = listOf(
            workingDirectory.resolve(moduleRelativePath),
            workingDirectory.resolve("app").resolve(moduleRelativePath),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("Project file not found: $moduleRelativePath from $workingDirectory")
    }

    private fun Document.elements(tagName: String): List<Element> {
        val nodes = getElementsByTagName(tagName)
        return (0 until nodes.length).map { index -> nodes.item(index) as Element }
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private fun Element.appAttribute(name: String): String =
        getAttributeNS(APP_NAMESPACE, name)

    companion object {
        private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        private const val APP_NAMESPACE = "http://schemas.android.com/apk/res-auto"
        private const val MATERIAL_TOOLBAR = "com.google.android.material.appbar.MaterialToolbar"
        private val PRIMARY_LAYOUTS = listOf(
            "activity_apk_inspector.xml",
            "activity_manifest_viewer.xml",
        )
        private val HEADING_IDS = setOf(
            "@+id/package_details_heading",
            "@+id/components_heading",
            "@+id/device_simulation_heading",
            "@+id/requested_permissions_heading",
            "@+id/findings_heading",
        )
        private val HEADING_BINDINGS = setOf(
            "binding.packageDetailsHeading",
            "binding.componentsHeading",
            "binding.deviceSimulationHeading",
            "binding.requestedPermissionsHeading",
            "binding.findingsHeading",
        )
        private val STRING_DIRECTORIES = listOf(
            "values",
            "values-en",
            "values-zh",
            "values-zh-rHK",
            "values-zh-rTW",
            "values-fr",
            "values-es",
            "values-ja",
            "values-ko",
            "values-ru",
            "values-ar",
        )
        private val ACCESSIBILITY_STRING_NAMES = setOf(
            "inspection_report_accessibility_title",
            "action_navigate_up",
        )
    }
}
