package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionProtectionAnalyzerTest {

    @Test
    fun protectionBaseAndFlagsAreClassifiedIntoThreeGroups() {
        assertEquals(
            PermissionProtectionGroup.RUNTIME,
            PermissionProtectionAnalyzer.classify(0x81),
        )
        assertEquals(
            PermissionProtectionGroup.SIGNATURE,
            PermissionProtectionAnalyzer.classify(0x12),
        )
        assertEquals(
            PermissionProtectionGroup.SIGNATURE,
            PermissionProtectionAnalyzer.classify(0x03),
        )
        assertEquals(
            PermissionProtectionGroup.SIGNATURE,
            PermissionProtectionAnalyzer.classify(0x04),
        )
        assertEquals(
            PermissionProtectionGroup.SIGNATURE,
            PermissionProtectionAnalyzer.classify(0x0F),
        )
        assertEquals(
            PermissionProtectionGroup.NORMAL,
            PermissionProtectionAnalyzer.classify(0x40),
        )
        assertEquals(
            PermissionProtectionGroup.NORMAL,
            PermissionProtectionAnalyzer.classify(null),
        )
    }

    @Test
    fun runtimePermissionsAreFirstAndDescriptionsAreSanitized() {
        val definitions = mapOf(
            "android.permission.INTERNET" to PermissionDefinition(0),
            "android.permission.CAMERA" to PermissionDefinition(
                protectionLevel = 1,
                description = "  Uses\n the\t camera.  ",
            ),
            "android.permission.BIND_VPN_SERVICE" to PermissionDefinition(2),
        )

        val analysis = PermissionProtectionAnalyzer.analyze(definitions.keys) { name ->
            definitions[name]
        }

        assertEquals(
            listOf(
                "android.permission.CAMERA",
                "android.permission.BIND_VPN_SERVICE",
                "android.permission.INTERNET",
            ),
            analysis.permissions.map { permission -> permission.name },
        )
        assertEquals(
            listOf(
                PermissionProtectionGroup.RUNTIME,
                PermissionProtectionGroup.SIGNATURE,
                PermissionProtectionGroup.NORMAL,
            ),
            analysis.permissions.map { permission -> permission.group },
        )
        assertEquals("Uses the camera.", analysis.permissions.first().description)
    }

    @Test
    fun archiveDeclarationTakesPrecedenceOverDeviceDefinition() {
        var resolverCalls = 0

        val analysis = PermissionProtectionAnalyzer.analyze(
            requestedPermissions = listOf("com.example.permission.LOCAL"),
            declaredProtectionLevels = mapOf("com.example.permission.LOCAL" to 1),
            resolvePermission = {
                resolverCalls += 1
                PermissionDefinition(0, "Wrong device definition")
            },
        )

        assertEquals(0, resolverCalls)
        assertEquals(PermissionProtectionGroup.RUNTIME, analysis.permissions.single().group)
        assertNull(analysis.permissions.single().description)
    }

    @Test
    fun unavailableProtectionLevelRemainsVisibleAndExplicitlyUnresolved() {
        val analysis = PermissionProtectionAnalyzer.analyze(
            listOf("com.example.permission.UNKNOWN"),
        )

        val permission = analysis.permissions.single()
        assertEquals(PermissionProtectionGroup.NORMAL, permission.group)
        assertFalse(permission.protectionLevelResolved)
    }

    @Test
    fun oneResolverFailureDoesNotDiscardOtherPermissions() {
        val analysis = PermissionProtectionAnalyzer.analyze(
            listOf("permission.broken", "permission.camera"),
        ) { name ->
            if (name == "permission.broken") error("lookup failed")
            PermissionDefinition(1, "Camera access")
        }

        assertEquals(2, analysis.permissions.size)
        assertTrue(
            analysis.permissions.single { it.name == "permission.camera" }
                .protectionLevelResolved,
        )
        assertFalse(
            analysis.permissions.single { it.name == "permission.broken" }
                .protectionLevelResolved,
        )
    }

    @Test
    fun displayedPermissionsAndResolverWorkAreBounded() {
        val requested = (0 until 600).map { index -> "permission.${index.toString().padStart(4, '0')}" }
        var resolverCalls = 0

        val analysis = PermissionProtectionAnalyzer.analyze(requested) {
            resolverCalls += 1
            PermissionDefinition(0)
        }

        assertEquals(PermissionProtectionAnalyzer.MAX_DISPLAYED_PERMISSIONS, analysis.permissions.size)
        assertEquals(PermissionProtectionAnalyzer.MAX_DISPLAYED_PERMISSIONS, resolverCalls)
        assertEquals(88, analysis.omittedCount)
    }

    @Test
    fun invalidNamesAreOmittedAndDuplicatesAreCollapsed() {
        val analysis = PermissionProtectionAnalyzer.analyze(
            listOf(
                " permission.valid ",
                "permission.valid",
                "permission.unsafe\u0000name",
                "   ",
            ),
        ) { PermissionDefinition(0) }

        assertEquals(listOf("permission.valid"), analysis.permissions.map { it.name })
        assertEquals(1, analysis.omittedCount)
    }

    @Test
    fun longDescriptionsAreTruncatedWithAnEllipsis() {
        val description = "a".repeat(PermissionProtectionAnalyzer.MAX_DESCRIPTION_CHARS + 50)

        val normalized = PermissionProtectionAnalyzer.normalizeDescription(description)

        assertEquals(PermissionProtectionAnalyzer.MAX_DESCRIPTION_CHARS, normalized?.length)
        assertTrue(normalized?.endsWith('…') == true)
    }
}
