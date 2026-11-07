package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Resources the host resolves by name from the explorer action catalog must survive resource shrinking of release
 * builds (`isShrinkResources = true`); otherwise every language shows the English labelFallback.
 */
class ResourceShrinkKeepTest {

    @Test
    fun keepRulesCoverTheResourcesTheCatalogNamesForTheHost() {
        val kept = keptResources()
        listOf(ApkInspectorPlugin.LABEL_RESOURCE_NAME, ApkInspectorPlugin.MENU_GROUP_LABEL_RESOURCE_NAME).forEach { name ->
            assertTrue("res/raw/keep.xml does not keep @string/$name: $kept", "@string/$name" in kept)
        }
    }

    private fun keptResources(): Set<String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = Files.newInputStream(projectFile("src/main/res/raw/keep.xml")).use { input ->
            factory.newDocumentBuilder().parse(input)
        }
        return document.documentElement.getAttributeNS(TOOLS_NAMESPACE, "keep")
            .split(',').map(String::trim).filter(String::isNotEmpty).toSet()
    }

    private fun projectFile(moduleRelativePath: String): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return listOf(workingDirectory.resolve(moduleRelativePath), workingDirectory.resolve("app").resolve(moduleRelativePath))
            .firstOrNull(Files::exists)
            ?: error("Project file not found: $moduleRelativePath from $workingDirectory")
    }

    private companion object {
        const val TOOLS_NAMESPACE = "http://schemas.android.com/tools"
    }
}
