package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
