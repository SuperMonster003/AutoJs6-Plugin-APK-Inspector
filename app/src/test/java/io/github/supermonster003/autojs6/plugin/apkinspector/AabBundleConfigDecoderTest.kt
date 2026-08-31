package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

class AabBundleConfigDecoderTest {

    @Test
    fun publicBundleConfigFieldsAreDecoded() {
        val summary = AabBundleConfigDecoder.decode(bundleConfig())

        assertEquals("1.18.2", summary.bundletoolVersion)
        assertEquals(AabBundleType.ASSET_ONLY, summary.bundleType)
        assertEquals(2, summary.splitDimensions.size)
        assertEquals(AabSplitDimension.ABI, summary.splitDimensions[0].dimension)
        assertTrue(summary.splitDimensions[0].splitEnabled)
        assertEquals(AabSplitDimension.LANGUAGE, summary.splitDimensions[1].dimension)
        assertFalse(summary.splitDimensions[1].splitEnabled)
        assertEquals(true, summary.splitDimensions[1].suffixStrippingEnabled)
        assertEquals("en", summary.splitDimensions[1].defaultSuffix)
        assertEquals(2, summary.uncompressedGlobCount)
        assertEquals(AabAssetModuleCompression.COMPRESSED, summary.installTimeAssetCompression)
        assertEquals(AabApkCompressionAlgorithm.P7ZIP, summary.apkCompressionAlgorithm)
        assertEquals(true, summary.uncompressNativeLibraries)
        assertEquals(false, summary.uncompressDexFiles)
        assertEquals(true, summary.injectLocaleConfig)
        assertNull(summary.issue)
    }

    @Test
    fun emptyMessageUsesProto3Defaults() {
        val summary = AabBundleConfigDecoder.decode(byteArrayOf())

        assertEquals(AabBundleType.REGULAR, summary.bundleType)
        assertEquals(0, summary.rawBundleType)
        assertTrue(summary.splitDimensions.isEmpty())
        assertNull(summary.bundletoolVersion)
        assertNull(summary.uncompressNativeLibraries)
        assertNull(summary.issue)
    }

    @Test
    fun unknownEnumsRemainVisibleForForwardCompatibility() {
        val bytes = ProtoWriter().apply {
            varint(BUNDLE_CONFIG_TYPE_FIELD, 47)
            message(BUNDLE_CONFIG_OPTIMIZATIONS_FIELD) {
                message(OPTIMIZATIONS_SPLITS_CONFIG_FIELD) {
                    message(SPLITS_CONFIG_DIMENSION_FIELD) {
                        varint(SPLIT_DIMENSION_VALUE_FIELD, 48)
                    }
                }
            }
            message(BUNDLE_CONFIG_COMPRESSION_FIELD) {
                varint(COMPRESSION_ASSET_DEFAULT_FIELD, 49)
                varint(COMPRESSION_APK_ALGORITHM_FIELD, 50)
            }
        }.toByteArray()

        val summary = AabBundleConfigDecoder.decode(bytes)

        assertEquals(AabBundleType.UNKNOWN, summary.bundleType)
        assertEquals(47, summary.rawBundleType)
        assertEquals(AabSplitDimension.UNKNOWN, summary.splitDimensions.single().dimension)
        assertEquals(48, summary.splitDimensions.single().rawDimension)
        assertEquals(AabAssetModuleCompression.UNKNOWN, summary.installTimeAssetCompression)
        assertEquals(AabApkCompressionAlgorithm.UNKNOWN, summary.apkCompressionAlgorithm)
    }

    @Test
    fun inputAndStructuralBudgetsAreEnforced() {
        val bytes = bundleConfig()
        val defaults = AabBundleConfigDecoder.Limits()

        listOf(
            defaults.copy(maxInputBytes = bytes.size - 1),
            defaults.copy(maxFields = 1),
            defaults.copy(maxDepth = 1),
            defaults.copy(maxDecodedStringChars = 4),
            defaults.copy(maxSingleStringChars = 3),
            defaults.copy(maxSplitDimensions = 1),
            defaults.copy(maxUncompressedGlobs = 1),
        ).forEach { limits ->
            captureIOException { AabBundleConfigDecoder.decode(bytes, limits) }
        }
    }

    @Test
    fun malformedKnownFieldAndTruncatedMessageAreRejected() {
        captureIOException {
            AabBundleConfigDecoder.decode(
                byteArrayOf(fieldTag(BUNDLE_CONFIG_TYPE_FIELD, WIRE_LENGTH_DELIMITED).toByte(), 0),
            )
        }
        captureIOException {
            AabBundleConfigDecoder.decode(
                byteArrayOf(fieldTag(BUNDLE_CONFIG_BUNDLETOOL_FIELD, WIRE_LENGTH_DELIMITED).toByte(), 5, 1),
            )
        }
    }

    private fun bundleConfig(): ByteArray = ProtoWriter().apply {
        message(BUNDLE_CONFIG_BUNDLETOOL_FIELD) {
            string(BUNDLETOOL_VERSION_FIELD, "1.18.2")
        }
        message(BUNDLE_CONFIG_OPTIMIZATIONS_FIELD) {
            message(OPTIMIZATIONS_SPLITS_CONFIG_FIELD) {
                message(SPLITS_CONFIG_DIMENSION_FIELD) {
                    varint(SPLIT_DIMENSION_VALUE_FIELD, 1)
                }
                message(SPLITS_CONFIG_DIMENSION_FIELD) {
                    varint(SPLIT_DIMENSION_VALUE_FIELD, 3)
                    varint(SPLIT_DIMENSION_NEGATE_FIELD, 1)
                    message(SPLIT_DIMENSION_SUFFIX_STRIPPING_FIELD) {
                        varint(SUFFIX_STRIPPING_ENABLED_FIELD, 1)
                        string(SUFFIX_STRIPPING_DEFAULT_FIELD, "en")
                    }
                }
            }
            message(OPTIMIZATIONS_UNCOMPRESS_NATIVE_FIELD) {
                varint(ENABLED_FIELD, 1)
                varint(2, 2)
            }
            message(OPTIMIZATIONS_UNCOMPRESS_DEX_FIELD) {
                varint(ENABLED_FIELD, 0)
                varint(2, 3)
            }
        }
        message(BUNDLE_CONFIG_COMPRESSION_FIELD) {
            string(COMPRESSION_GLOB_FIELD, "assets/**/*.tflite")
            string(COMPRESSION_GLOB_FIELD, "res/raw/**")
            varint(COMPRESSION_ASSET_DEFAULT_FIELD, 2)
            varint(COMPRESSION_APK_ALGORITHM_FIELD, 1)
        }
        varint(BUNDLE_CONFIG_TYPE_FIELD, 2)
        message(BUNDLE_CONFIG_LOCALES_FIELD) {
            varint(LOCALES_INJECT_FIELD, 1)
        }
    }.toByteArray()

    private fun captureIOException(block: () -> Unit): IOException {
        try {
            block()
            throw AssertionError("Expected IOException")
        } catch (exception: IOException) {
            return exception
        }
    }

    private class ProtoWriter {
        private val output = ByteArrayOutputStream()

        fun message(fieldNumber: Int, block: ProtoWriter.() -> Unit) {
            val nested = ProtoWriter().apply(block).toByteArray()
            tag(fieldNumber, WIRE_LENGTH_DELIMITED)
            rawVarint(nested.size.toLong())
            output.write(nested)
        }

        fun string(fieldNumber: Int, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            tag(fieldNumber, WIRE_LENGTH_DELIMITED)
            rawVarint(bytes.size.toLong())
            output.write(bytes)
        }

        fun varint(fieldNumber: Int, value: Int) {
            tag(fieldNumber, WIRE_VARINT)
            rawVarint(value.toLong() and 0xFFFF_FFFFL)
        }

        private fun tag(fieldNumber: Int, wireType: Int) {
            rawVarint(fieldTag(fieldNumber, wireType).toLong())
        }

        private fun rawVarint(value: Long) {
            var remaining = value
            while (remaining and -0x80L != 0L) {
                output.write(((remaining and 0x7F) or 0x80).toInt())
                remaining = remaining ushr 7
            }
            output.write(remaining.toInt())
        }

        fun toByteArray(): ByteArray = output.toByteArray()
    }

    private companion object {
        const val WIRE_VARINT = 0
        const val WIRE_LENGTH_DELIMITED = 2
        const val BUNDLE_CONFIG_BUNDLETOOL_FIELD = 1
        const val BUNDLE_CONFIG_OPTIMIZATIONS_FIELD = 2
        const val BUNDLE_CONFIG_COMPRESSION_FIELD = 3
        const val BUNDLE_CONFIG_TYPE_FIELD = 8
        const val BUNDLE_CONFIG_LOCALES_FIELD = 9
        const val BUNDLETOOL_VERSION_FIELD = 2
        const val OPTIMIZATIONS_SPLITS_CONFIG_FIELD = 1
        const val OPTIMIZATIONS_UNCOMPRESS_NATIVE_FIELD = 2
        const val OPTIMIZATIONS_UNCOMPRESS_DEX_FIELD = 3
        const val SPLITS_CONFIG_DIMENSION_FIELD = 1
        const val SPLIT_DIMENSION_VALUE_FIELD = 1
        const val SPLIT_DIMENSION_NEGATE_FIELD = 2
        const val SPLIT_DIMENSION_SUFFIX_STRIPPING_FIELD = 3
        const val SUFFIX_STRIPPING_ENABLED_FIELD = 1
        const val SUFFIX_STRIPPING_DEFAULT_FIELD = 2
        const val ENABLED_FIELD = 1
        const val COMPRESSION_GLOB_FIELD = 1
        const val COMPRESSION_ASSET_DEFAULT_FIELD = 2
        const val COMPRESSION_APK_ALGORITHM_FIELD = 3
        const val LOCALES_INJECT_FIELD = 1

        fun fieldTag(fieldNumber: Int, wireType: Int): Int = (fieldNumber shl 3) or wireType
    }
}
