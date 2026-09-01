package io.github.supermonster003.autojs6.plugin.apkinspector

import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostFileInfoRequestPolicyTest {

    @Test
    fun hostFileInfoCapabilityDoesNotRenumberTheBaseProtocol() {
        assertEquals(22, ExplorerActionProtocol.VERSION)
        assertEquals(1, ExplorerActionProtocol.HOST_FILE_INFO_VERSION)
    }

    @Test
    fun validRequestIsNormalizedWithoutBroadeningTheAction() {
        val request = HostFileInfoRequestPolicy.validate(
            version = ExplorerActionProtocol.HOST_FILE_INFO_VERSION,
            actionId = ApkInspectorPlugin.ACTION_ID,
            displayName = "release.APK",
            size = 42L,
            lastModified = 1L,
            expectedSourceSha256 = "A".repeat(64),
        ) ?: error("Expected a valid host file-information request")

        assertEquals("release.APK", request.displayName)
        assertEquals(42L, request.size)
        assertEquals("a".repeat(64), request.expectedSourceSha256)
    }

    @Test
    fun requestRejectsWrongCapabilityActionMetadataAndIdentity() {
        fun validate(
            version: Int = ExplorerActionProtocol.HOST_FILE_INFO_VERSION,
            actionId: String? = ApkInspectorPlugin.ACTION_ID,
            displayName: String? = "release.apk",
            size: Long = 42L,
            lastModified: Long = 1L,
            sha256: String? = "0".repeat(64),
        ) = HostFileInfoRequestPolicy.validate(
            version = version,
            actionId = actionId,
            displayName = displayName,
            size = size,
            lastModified = lastModified,
            expectedSourceSha256 = sha256,
        )

        assertNull(validate(version = 0))
        assertNull(validate(actionId = "other-action"))
        assertNull(validate(displayName = "../release.apk"))
        assertNull(validate(displayName = "release.zip"))
        assertNull(validate(size = -1L))
        assertNull(validate(size = PackageRequestPolicy.MAX_PACKAGE_BYTES + 1L))
        assertNull(validate(lastModified = -1L))
        assertNull(validate(sha256 = "0".repeat(63)))
        assertNull(validate(sha256 = "g".repeat(64)))
    }
}
