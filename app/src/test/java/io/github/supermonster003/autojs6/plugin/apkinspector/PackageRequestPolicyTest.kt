package io.github.supermonster003.autojs6.plugin.apkinspector

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.autojs.plugin.explorer.api.ExplorerActionIntentValues
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues

class PackageRequestPolicyTest {

    @Test
    fun acceptsOnlySupportedPackageNames() {
        listOf("apk", "apks", "xapk", "apkm", "apkz", "aab").forEach { extension ->
            assertEquals("sample.$extension", PackageRequestPolicy.validateDisplayName("sample.$extension"))
        }
        assertNull(PackageRequestPolicy.validateDisplayName("sample.zip"))
        assertNull(PackageRequestPolicy.validateDisplayName("../sample.apk"))
        assertNull(PackageRequestPolicy.validateDisplayName("sample\u202E.apk"))
        assertNull(PackageRequestPolicy.validateDisplayName("sample\n.apk"))
    }

    @Test
    fun externalMimeTypesStayNarrowAndExtensionSpecific() {
        assertTrue(
            PackageRequestPolicy.isExternalMimeCompatible(
                "sample.apk",
                "application/vnd.android.package-archive",
            ),
        )
        assertTrue(PackageRequestPolicy.isExternalMimeCompatible("sample.apks", "application/x-apks"))
        assertTrue(PackageRequestPolicy.isExternalMimeCompatible("sample.aab", "application/vnd.android.aab"))
        assertFalse(PackageRequestPolicy.isExternalMimeCompatible("sample.apks", "application/zip"))
        assertFalse(PackageRequestPolicy.isExternalMimeCompatible("sample.apk", "application/octet-stream"))
        assertFalse(PackageRequestPolicy.isExternalMimeCompatible("sample.apk", "application/x-apks"))
    }

    @Test
    fun mimeNormalizationRejectsAmbiguousForms() {
        assertEquals(
            "application/vnd.android.package-archive",
            PackageRequestPolicy.normalizeMimeType("application/vnd.android.package-archive"),
        )
        assertEquals("*/*", PackageRequestPolicy.normalizeMimeType("*/*"))
        assertNull(PackageRequestPolicy.normalizeMimeType("APPLICATION/X-APKS"))
        assertNull(PackageRequestPolicy.normalizeMimeType(" application/x-apks"))
        assertNull(PackageRequestPolicy.normalizeMimeType("application/x-apks; charset=binary"))
        assertNull(PackageRequestPolicy.normalizeMimeType("application/"))
    }

    @Test
    fun explorerMimeCompatibilityDoesNotWidenExternalGateway() {
        assertTrue(
            PackageRequestPolicy.mimeTypesAreCompatible(
                "*/*",
                "application/vnd.android.package-archive",
                fromExplorer = true,
            ),
        )
        assertTrue(
            PackageRequestPolicy.mimeTypesAreCompatible(
                "application/octet-stream",
                "application/zip",
                fromExplorer = true,
            ),
        )
        assertFalse(
            PackageRequestPolicy.mimeTypesAreCompatible(
                "application/x-apks",
                "image/png",
                fromExplorer = true,
            ),
        )
        assertFalse(
            PackageRequestPolicy.mimeTypesAreCompatible(
                "application/x-apks",
                "application/zip",
                fromExplorer = false,
            ),
        )
    }

    @Test
    fun declaredSizeIsBoundedAtFourGiB() {
        assertTrue(PackageRequestPolicy.isDeclaredSizeAccepted(0L))
        assertTrue(PackageRequestPolicy.isDeclaredSizeAccepted(PackageRequestPolicy.MAX_PACKAGE_BYTES))
        assertFalse(PackageRequestPolicy.isDeclaredSizeAccepted(-1L))
        assertFalse(PackageRequestPolicy.isDeclaredSizeAccepted(PackageRequestPolicy.MAX_PACKAGE_BYTES + 1L))
    }

    @Test
    fun everyExplorerRequestRejectionHasAnExplicitFailClosedCase() {
        val valid = validExplorerRequest()
        val differentTarget = contentUri("root", "other.apk")
        val requestCases = listOf<Pair<PackageRequestRejection, ExplorerRequestFacts?>>(
            PackageRequestRejection.EXPLORER_INTENT_MISSING to null,
            PackageRequestRejection.EXPLORER_ACTION to valid.copy(action = "wrong"),
            PackageRequestRejection.EXPLORER_ACTION_ID to valid.copy(actionId = "wrong"),
            PackageRequestRejection.EXPLORER_PROTOCOL_VERSION to valid.copy(protocolVersion = -1),
            PackageRequestRejection.EXPLORER_SOURCE_SURFACE to valid.copy(sourceSurface = "secondary"),
            PackageRequestRejection.EXPLORER_READ_GRANT_MISSING to valid.copy(flags = 0),
            PackageRequestRejection.EXPLORER_FORBIDDEN_GRANT to valid.copy(
                flags = valid.flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ),
            PackageRequestRejection.EXPLORER_FORBIDDEN_GRANT to valid.copy(
                flags = valid.flags or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            ),
            PackageRequestRejection.EXPLORER_REQUEST_ID to valid.copy(requestId = "not-a-uuid"),
            PackageRequestRejection.EXPLORER_REQUEST_ID to valid.copy(requestId = "1-1-1-1-1"),
            PackageRequestRejection.EXPLORER_HOST_VERSION to valid.copy(hostVersionPresent = false),
            PackageRequestRejection.EXPLORER_HOST_VERSION to valid.copy(
                hostVersion = ApkInspectorPlugin.REQUIRED_HOST_VERSION - 1,
            ),
            PackageRequestRejection.EXPLORER_TARGET_URI to valid.copy(
                targetUri = valid.targetUri?.copy(scheme = "https"),
            ),
            PackageRequestRejection.EXPLORER_PARENT_URI to valid.copy(
                parentUri = valid.parentUri?.copy(query = "unsafe=true"),
            ),
            PackageRequestRejection.EXPLORER_TARGET_NOT_DESCENDANT to valid.copy(
                parentUri = contentUri("elsewhere"),
            ),
            PackageRequestRejection.EXPLORER_CLIP_DATA_MISSING to valid.copy(clipDataPresent = false),
            PackageRequestRejection.EXPLORER_CLIP_ITEM_COUNT to valid.copy(clipItemCount = 2),
            PackageRequestRejection.EXPLORER_CLIP_ITEM to valid.copy(clipItem = null),
            PackageRequestRejection.EXPLORER_CLIP_ITEM to valid.copy(
                clipItem = valid.clipItem?.copy(uri = differentTarget),
            ),
            PackageRequestRejection.EXPLORER_CLIP_ITEM to valid.copy(
                clipItem = valid.clipItem?.copy(hasText = true),
            ),
            PackageRequestRejection.EXPLORER_CLIP_ITEM to valid.copy(
                clipItem = valid.clipItem?.copy(hasHtmlText = true),
            ),
            PackageRequestRejection.EXPLORER_CLIP_ITEM to valid.copy(
                clipItem = valid.clipItem?.copy(hasIntent = true),
            ),
            PackageRequestRejection.EXPLORER_DISPLAY_NAME to valid.copy(displayName = "unsafe.zip"),
            PackageRequestRejection.EXPLORER_URI_NAME_MISMATCH to valid.withTargetUri(differentTarget),
            PackageRequestRejection.EXPLORER_SIZE_MISSING to valid.copy(sizePresent = false),
            PackageRequestRejection.EXPLORER_SIZE to valid.copy(declaredSize = -1L),
            PackageRequestRejection.EXPLORER_SIZE to valid.copy(
                declaredSize = PackageRequestPolicy.MAX_PACKAGE_BYTES + 1L,
            ),
            PackageRequestRejection.EXPLORER_MIME to valid.copy(mimeType = "image/png"),
            PackageRequestRejection.EXPLORER_TARGETS_MISSING to valid.copy(targetsPresent = false),
            PackageRequestRejection.EXPLORER_TARGET_COUNT to valid.copy(targetCount = 0, target = null),
            PackageRequestRejection.EXPLORER_TARGET_COUNT to valid.copy(target = null),
            PackageRequestRejection.EXPLORER_TARGET_ID to valid.withTarget(id = null),
            PackageRequestRejection.EXPLORER_TARGET_ID to valid.withTarget(id = ""),
            PackageRequestRejection.EXPLORER_TARGET_ID to valid.withTarget(id = "contains space"),
            PackageRequestRejection.EXPLORER_TARGET_ID to valid.withTarget(id = "control\u0000"),
            PackageRequestRejection.EXPLORER_TARGET_ID to valid.withTarget(
                id = "x".repeat(ExplorerActionProtocol.MAX_TARGET_ID_LENGTH + 1),
            ),
            PackageRequestRejection.EXPLORER_TARGET_URI_MISMATCH to valid.withTarget(
                uri = differentTarget,
            ),
            PackageRequestRejection.EXPLORER_TARGET_NAME_MISMATCH to valid.withTarget(
                displayName = "other.apk",
            ),
            PackageRequestRejection.EXPLORER_TARGET_KIND to valid.withTarget(kind = 0),
            PackageRequestRejection.EXPLORER_TARGET_MIME_MISMATCH to valid.withTarget(
                mimeType = "application/x-apks",
            ),
            PackageRequestRejection.EXPLORER_TARGET_MIME_MISMATCH to valid.withTarget(
                mimeType = "APPLICATION/VND.ANDROID.PACKAGE-ARCHIVE",
            ),
            PackageRequestRejection.EXPLORER_TARGET_SIZE_MISSING to valid.withTarget(
                sizePresent = false,
            ),
            PackageRequestRejection.EXPLORER_TARGET_SIZE_MISMATCH to valid.withTarget(size = 43L),
            PackageRequestRejection.EXPLORER_TARGET_LAST_MODIFIED_MISSING to valid.withTarget(
                lastModifiedPresent = false,
            ),
            PackageRequestRejection.EXPLORER_HOST_SESSION to valid.copy(hostSessionPresent = false),
        )

        assertNull(PackageRequestValidator.rejectExplorer(valid))
        requestCases.forEach { (expected, request) ->
            assertEquals(expected, PackageRequestValidator.rejectExplorer(request))
        }
        assertEquals(
            PackageRequestRejection.entries.filter { it.name.startsWith("EXPLORER_") }.toSet(),
            requestCases.map(Pair<PackageRequestRejection, ExplorerRequestFacts?>::first).toSet(),
        )
    }

    @Test
    fun everyExternalRequestRejectionHasAnExplicitFailClosedCase() {
        val valid = validExternalRequest()
        val requestCases = listOf<Pair<PackageRequestRejection, ExternalRequestFacts?>>(
            PackageRequestRejection.EXTERNAL_INTENT_MISSING to null,
            PackageRequestRejection.EXTERNAL_ACTION to valid.copy(action = "wrong"),
            PackageRequestRejection.EXTERNAL_READ_GRANT_MISSING to valid.copy(flags = 0),
            PackageRequestRejection.EXTERNAL_FORBIDDEN_GRANT to valid.copy(
                flags = valid.flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ),
            PackageRequestRejection.EXTERNAL_FORBIDDEN_GRANT to valid.copy(
                flags = valid.flags or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            ),
            PackageRequestRejection.EXTERNAL_FORBIDDEN_GRANT to valid.copy(
                flags = valid.flags or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            ),
            PackageRequestRejection.EXTERNAL_TARGET_URI to valid.copy(
                targetUri = valid.targetUri?.copy(fragment = "unsafe"),
            ),
            PackageRequestRejection.EXTERNAL_TARGET_URI to valid.copy(targetUri = null),
            PackageRequestRejection.EXTERNAL_MIME to valid.copy(mimeType = "application/zip"),
            PackageRequestRejection.EXTERNAL_MIME to valid.copy(mimeType = null),
            PackageRequestRejection.EXTERNAL_MIME to valid.copy(
                mimeType = "APPLICATION/VND.ANDROID.PACKAGE-ARCHIVE",
            ),
        )

        assertNull(PackageRequestValidator.rejectExternal(valid))
        requestCases.forEach { (expected, request) ->
            assertEquals(expected, PackageRequestValidator.rejectExternal(request))
        }
        assertEquals(
            PackageRequestRejection.entries.filter { it.name.startsWith("EXTERNAL_") }.toSet(),
            requestCases.map(Pair<PackageRequestRejection, ExternalRequestFacts?>::first).toSet(),
        )
    }

    @Test
    fun plainContentUriRejectsEveryUnsafeStructuralShape() {
        val valid = contentUri("root", "sample.apk")
        assertTrue(PackageRequestValidator.isPlainContentUri(valid))

        listOf(
            valid.copy(isHierarchical = false),
            valid.copy(scheme = "https"),
            valid.copy(authority = null),
            valid.copy(authority = ""),
            valid.copy(host = null),
            valid.copy(host = " "),
            valid.copy(userInfo = "user"),
            valid.copy(port = 443),
            valid.copy(query = "q=1"),
            valid.copy(fragment = "fragment"),
            valid.copy(encodedPath = null),
            valid.copy(encodedPath = "root/sample.apk"),
            valid.copy(encodedPath = "/", pathSegments = emptyList()),
            valid.copy(encodedPath = "/root//sample.apk", pathSegments = listOf("root", "", "sample.apk")),
            valid.copy(pathSegments = emptyList()),
            valid.copy(pathSegments = listOf("root", "", "sample.apk")),
            valid.copy(pathSegments = listOf("root", ".", "sample.apk")),
            valid.copy(pathSegments = listOf("root", "..", "sample.apk")),
            valid.copy(pathSegments = listOf("root", "nested/name.apk")),
            valid.copy(pathSegments = listOf("root", "nested\\name.apk")),
            valid.copy(pathSegments = listOf("root", "control\u0000.apk")),
            valid.copy(pathSegments = listOf("root", "format\u202E.apk")),
        ).forEach { unsafe ->
            assertFalse("URI shape should be rejected: $unsafe", PackageRequestValidator.isPlainContentUri(unsafe))
        }
    }

    @Test
    fun strictDescendantRequiresSameAuthorityAndADeeperSharedPath() {
        val parent = contentUri("root")
        val target = contentUri("root", "sample.apk")

        assertTrue(PackageRequestValidator.isStrictDescendant(parent, target))
        assertFalse(PackageRequestValidator.isStrictDescendant(parent.copy(scheme = "other"), target))
        assertFalse(PackageRequestValidator.isStrictDescendant(parent.copy(authority = "other"), target))
        assertFalse(PackageRequestValidator.isStrictDescendant(parent, parent))
        assertFalse(PackageRequestValidator.isStrictDescendant(contentUri("elsewhere"), target))
    }

    @Test
    fun displayNameGuardRejectsEveryUnsafeBoundary() {
        listOf<String?>(
            null,
            "",
            "   ",
            ".",
            "..",
            "a".repeat(PackageRequestPolicy.MAX_DISPLAY_NAME_LENGTH + 1) + ".apk",
            "folder/sample.apk",
            "folder\\sample.apk",
            "control\u0000.apk",
            "format\u202E.apk",
            "sample",
            "sample.zip",
        ).forEach { unsafe ->
            assertNull("display name should be rejected: $unsafe", PackageRequestPolicy.validateDisplayName(unsafe))
        }
        assertEquals("SAMPLE.APK", PackageRequestPolicy.validateDisplayName("SAMPLE.APK"))
    }

    private fun validExplorerRequest(): ExplorerRequestFacts {
        val targetUri = contentUri("root", "sample.apk")
        return ExplorerRequestFacts(
            action = ExplorerActionPluginActions.EXECUTE,
            actionId = ApkInspectorPlugin.ACTION_ID,
            protocolVersion = ExplorerActionProtocol.VERSION,
            sourceSurface = ExplorerActionIntentValues.SOURCE_SURFACE_MAIN,
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
            requestId = "123e4567-e89b-12d3-a456-426614174000",
            hostVersionPresent = true,
            hostVersion = ApkInspectorPlugin.REQUIRED_HOST_VERSION,
            targetUri = targetUri,
            parentUri = contentUri("root"),
            clipDataPresent = true,
            clipItemCount = 1,
            clipItem = ExplorerClipItemFacts(
                uri = targetUri,
                hasText = false,
                hasHtmlText = false,
                hasIntent = false,
            ),
            displayName = "sample.apk",
            sizePresent = true,
            declaredSize = 42L,
            mimeType = "application/vnd.android.package-archive",
            targetsPresent = true,
            targetCount = 1,
            target = ExplorerTargetFacts(
                id = "target-1",
                uri = targetUri,
                displayName = "sample.apk",
                kind = ExplorerActionValues.TARGET_FILE,
                mimeType = "application/vnd.android.package-archive",
                sizePresent = true,
                size = 42L,
                lastModifiedPresent = true,
            ),
            hostSessionPresent = true,
        )
    }

    private fun validExternalRequest(): ExternalRequestFacts = ExternalRequestFacts(
        action = Intent.ACTION_VIEW,
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        targetUri = contentUri("root", "sample.apk"),
        mimeType = "application/vnd.android.package-archive",
    )

    private fun ExplorerRequestFacts.withTargetUri(uri: PackageUriFacts): ExplorerRequestFacts = copy(
        targetUri = uri,
        clipItem = clipItem?.copy(uri = uri),
        target = target?.copy(uri = uri),
    )

    private fun ExplorerRequestFacts.withTarget(
        id: String? = target?.id,
        uri: PackageUriFacts? = target?.uri,
        displayName: String? = target?.displayName,
        kind: Int = target?.kind ?: 0,
        mimeType: String? = target?.mimeType,
        sizePresent: Boolean = target?.sizePresent ?: false,
        size: Long = target?.size ?: -1L,
        lastModifiedPresent: Boolean = target?.lastModifiedPresent ?: false,
    ): ExplorerRequestFacts = copy(
        target = ExplorerTargetFacts(
            id = id,
            uri = uri,
            displayName = displayName,
            kind = kind,
            mimeType = mimeType,
            sizePresent = sizePresent,
            size = size,
            lastModifiedPresent = lastModifiedPresent,
        ),
    )

    private fun contentUri(vararg segments: String): PackageUriFacts {
        val encodedPath = "/" + segments.joinToString("/")
        return PackageUriFacts(
            identity = "content://fixture.example$encodedPath",
            isHierarchical = true,
            scheme = "content",
            authority = "fixture.example",
            host = "fixture.example",
            userInfo = null,
            port = -1,
            query = null,
            fragment = null,
            encodedPath = encodedPath,
            pathSegments = segments.toList(),
        )
    }
}
