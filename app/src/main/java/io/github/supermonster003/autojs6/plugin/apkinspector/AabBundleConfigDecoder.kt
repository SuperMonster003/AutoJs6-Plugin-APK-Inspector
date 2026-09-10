package io.github.supermonster003.autojs6.plugin.apkinspector

import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal class AabBundleConfigLimitException(message: String) : IOException(message)

internal enum class AabBundleType {
    REGULAR,
    APEX,
    ASSET_ONLY,
    UNKNOWN,
}

internal enum class AabSplitDimension {
    UNSPECIFIED,
    ABI,
    SCREEN_DENSITY,
    LANGUAGE,
    TEXTURE_COMPRESSION_FORMAT,
    DEVICE_TIER,
    COUNTRY_SET,
    AI_MODEL_VERSION,
    DEVICE_GROUP,
    UNKNOWN,
}

internal enum class AabAssetModuleCompression {
    UNSPECIFIED,
    UNCOMPRESSED,
    COMPRESSED,
    UNKNOWN,
}

internal enum class AabApkCompressionAlgorithm {
    DEFAULT,
    P7ZIP,
    UNKNOWN,
}

internal data class AabSplitDimensionConfig(
    val dimension: AabSplitDimension,
    val rawDimension: Int,
    val splitEnabled: Boolean,
    val suffixStrippingEnabled: Boolean?,
    val defaultSuffix: String?,
)

internal data class AabBundleConfigSummary(
    val bundletoolVersion: String?,
    val bundleType: AabBundleType,
    val rawBundleType: Int,
    val splitDimensions: List<AabSplitDimensionConfig>,
    val uncompressedGlobCount: Int,
    val installTimeAssetCompression: AabAssetModuleCompression?,
    val rawInstallTimeAssetCompression: Int?,
    val apkCompressionAlgorithm: AabApkCompressionAlgorithm?,
    val rawApkCompressionAlgorithm: Int?,
    val uncompressNativeLibraries: Boolean?,
    val uncompressDexFiles: Boolean?,
    val injectLocaleConfig: Boolean?,
    val issue: AabMetadataIssue? = null,
) {

    companion object {
        fun unavailable(issue: AabMetadataIssue): AabBundleConfigSummary = AabBundleConfigSummary(
            bundletoolVersion = null,
            bundleType = AabBundleType.UNKNOWN,
            rawBundleType = 0,
            splitDimensions = emptyList(),
            uncompressedGlobCount = 0,
            installTimeAssetCompression = null,
            rawInstallTimeAssetCompression = null,
            apkCompressionAlgorithm = null,
            rawApkCompressionAlgorithm = null,
            uncompressNativeLibraries = null,
            uncompressDexFiles = null,
            injectLocaleConfig = null,
            issue = issue,
        )
    }
}

/** Bounded decoder for the public android.bundle.BundleConfig protobuf schema. */
internal object AabBundleConfigDecoder {

    internal data class Limits(
        val maxInputBytes: Int = PackageInspectionLimits.METADATA_BYTES,
        val maxFields: Int = 100_000,
        val maxDepth: Int = 16,
        val maxDecodedStringChars: Int = 64 * 1024,
        val maxSingleStringChars: Int = 4 * 1024,
        val maxSplitDimensions: Int = 64,
        val maxUncompressedGlobs: Int = 256,
    )

    fun decode(
        bytes: ByteArray,
        limits: Limits = Limits(),
    ): AabBundleConfigSummary {
        validateLimits(limits)
        if (bytes.size > limits.maxInputBytes) {
            throw AabBundleConfigLimitException("BundleConfig.pb exceeds the inspection size limit")
        }
        return Decoder(bytes, limits).decode()
    }

    private class Decoder(
        private val bytes: ByteArray,
        private val limits: Limits,
    ) {
        private var fieldCount = 0
        private var decodedStringChars = 0
        private var bundletoolVersion: String? = null
        private var rawBundleType = 0
        private val splitDimensions = ArrayList<AabSplitDimensionConfig>()
        private var uncompressedGlobCount = 0
        private var rawInstallTimeAssetCompression: Int? = null
        private var rawApkCompressionAlgorithm: Int? = null
        private var uncompressNativeLibraries: Boolean? = null
        private var uncompressDexFiles: Boolean? = null
        private var injectLocaleConfig: Boolean? = null

        fun decode(): AabBundleConfigSummary {
            readBundleConfig(reader(0, bytes.size), depth = 1)
            return AabBundleConfigSummary(
                bundletoolVersion = bundletoolVersion,
                bundleType = rawBundleType.toBundleType(),
                rawBundleType = rawBundleType,
                splitDimensions = splitDimensions.toList(),
                uncompressedGlobCount = uncompressedGlobCount,
                installTimeAssetCompression = rawInstallTimeAssetCompression?.toAssetCompression(),
                rawInstallTimeAssetCompression = rawInstallTimeAssetCompression,
                apkCompressionAlgorithm = rawApkCompressionAlgorithm?.toApkCompressionAlgorithm(),
                rawApkCompressionAlgorithm = rawApkCompressionAlgorithm,
                uncompressNativeLibraries = uncompressNativeLibraries,
                uncompressDexFiles = uncompressDexFiles,
                injectLocaleConfig = injectLocaleConfig,
            )
        }

        private fun readBundleConfig(reader: ProtoReader, depth: Int) {
            requireDepth(depth)
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    BUNDLE_CONFIG_BUNDLETOOL_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        readBundletool(reader.readLengthDelimitedReader(), depth + 1)
                    }
                    BUNDLE_CONFIG_OPTIMIZATIONS_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        readOptimizations(reader.readLengthDelimitedReader(), depth + 1)
                    }
                    BUNDLE_CONFIG_COMPRESSION_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        readCompression(reader.readLengthDelimitedReader(), depth + 1)
                    }
                    BUNDLE_CONFIG_TYPE_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        rawBundleType = reader.readVarint().toInt()
                    }
                    BUNDLE_CONFIG_LOCALES_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        if (injectLocaleConfig == null) injectLocaleConfig = false
                        readLocales(reader.readLengthDelimitedReader(), depth + 1)
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
        }

        private fun readBundletool(reader: ProtoReader, depth: Int) {
            requireDepth(depth)
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == BUNDLETOOL_VERSION_FIELD) {
                    requireWire(tag, WIRE_LENGTH_DELIMITED)
                    bundletoolVersion = reader.readString("bundletool version")
                } else {
                    reader.skip(tag.wireType)
                }
            }
        }

        private fun readOptimizations(reader: ProtoReader, depth: Int) {
            requireDepth(depth)
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    OPTIMIZATIONS_SPLITS_CONFIG_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        readSplitsConfig(reader.readLengthDelimitedReader(), depth + 1)
                    }
                    OPTIMIZATIONS_UNCOMPRESS_NATIVE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        if (uncompressNativeLibraries == null) uncompressNativeLibraries = false
                        readBooleanMessage(
                            reader.readLengthDelimitedReader(),
                            depth + 1,
                        ) { enabled -> uncompressNativeLibraries = enabled }
                    }
                    OPTIMIZATIONS_UNCOMPRESS_DEX_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        if (uncompressDexFiles == null) uncompressDexFiles = false
                        readBooleanMessage(
                            reader.readLengthDelimitedReader(),
                            depth + 1,
                        ) { enabled -> uncompressDexFiles = enabled }
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
        }

        private fun readBooleanMessage(
            reader: ProtoReader,
            depth: Int,
            onEnabled: (Boolean) -> Unit,
        ) {
            requireDepth(depth)
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == ENABLED_FIELD) {
                    requireWire(tag, WIRE_VARINT)
                    onEnabled(reader.readVarint() != 0L)
                } else {
                    reader.skip(tag.wireType)
                }
            }
        }

        private fun readSplitsConfig(reader: ProtoReader, depth: Int) {
            requireDepth(depth)
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == SPLITS_CONFIG_DIMENSION_FIELD) {
                    requireWire(tag, WIRE_LENGTH_DELIMITED)
                    if (splitDimensions.size >= limits.maxSplitDimensions) {
                        throw limit("split dimension count exceeds the inspection limit")
                    }
                    splitDimensions += readSplitDimension(
                        reader.readLengthDelimitedReader(),
                        depth + 1,
                    )
                } else {
                    reader.skip(tag.wireType)
                }
            }
        }

        private fun readSplitDimension(reader: ProtoReader, depth: Int): AabSplitDimensionConfig {
            requireDepth(depth)
            var rawDimension = 0
            var negate = false
            var suffixPresent = false
            var suffixEnabled = false
            var defaultSuffix: String? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    SPLIT_DIMENSION_VALUE_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        rawDimension = reader.readVarint().toInt()
                    }
                    SPLIT_DIMENSION_NEGATE_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        negate = reader.readVarint() != 0L
                    }
                    SPLIT_DIMENSION_SUFFIX_STRIPPING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        suffixPresent = true
                        val suffix = readSuffixStripping(
                            reader.readLengthDelimitedReader(),
                            depth + 1,
                            suffixEnabled,
                            defaultSuffix,
                        )
                        suffixEnabled = suffix.first
                        defaultSuffix = suffix.second
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            return AabSplitDimensionConfig(
                dimension = rawDimension.toSplitDimension(),
                rawDimension = rawDimension,
                splitEnabled = !negate,
                suffixStrippingEnabled = suffixEnabled.takeIf { suffixPresent },
                defaultSuffix = defaultSuffix?.takeIf(String::isNotEmpty),
            )
        }

        private fun readSuffixStripping(
            reader: ProtoReader,
            depth: Int,
            initialEnabled: Boolean,
            initialDefaultSuffix: String?,
        ): Pair<Boolean, String?> {
            requireDepth(depth)
            var enabled = initialEnabled
            var defaultSuffix = initialDefaultSuffix
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    SUFFIX_STRIPPING_ENABLED_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        enabled = reader.readVarint() != 0L
                    }
                    SUFFIX_STRIPPING_DEFAULT_SUFFIX_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        defaultSuffix = reader.readString("split default suffix")
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            return enabled to defaultSuffix
        }

        private fun readCompression(reader: ProtoReader, depth: Int) {
            requireDepth(depth)
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    COMPRESSION_UNCOMPRESSED_GLOB_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        if (uncompressedGlobCount >= limits.maxUncompressedGlobs) {
                            throw limit("uncompressed glob count exceeds the inspection limit")
                        }
                        reader.readString("uncompressed glob")
                        uncompressedGlobCount++
                    }
                    COMPRESSION_ASSET_MODULE_DEFAULT_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        rawInstallTimeAssetCompression = reader.readVarint().toInt()
                    }
                    COMPRESSION_APK_ALGORITHM_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        rawApkCompressionAlgorithm = reader.readVarint().toInt()
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
        }

        private fun readLocales(reader: ProtoReader, depth: Int) {
            requireDepth(depth)
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == LOCALES_INJECT_CONFIG_FIELD) {
                    requireWire(tag, WIRE_VARINT)
                    injectLocaleConfig = reader.readVarint() != 0L
                } else {
                    reader.skip(tag.wireType)
                }
            }
        }

        private fun reader(start: Int, end: Int): ProtoReader = ProtoReader(bytes, start, end, this)

        private fun decodeString(start: Int, end: Int, label: String): String {
            val value = try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, start, end - start))
                    .toString()
            } catch (_: Exception) {
                throw malformed("$label is not valid UTF-8")
            }
            if (value.length > limits.maxSingleStringChars) {
                throw limit("$label exceeds the single-string inspection limit")
            }
            if (value.length > limits.maxDecodedStringChars - decodedStringChars) {
                throw limit("decoded strings exceed the inspection limit")
            }
            decodedStringChars += value.length
            return value
        }

        private fun countField() {
            fieldCount++
            if (fieldCount > limits.maxFields) {
                throw limit("protobuf field count exceeds the inspection limit")
            }
        }

        private fun requireDepth(depth: Int) {
            if (depth > limits.maxDepth) {
                throw limit("protobuf nesting exceeds the inspection limit")
            }
        }

        private fun requireWire(tag: Tag, expected: Int) {
            if (tag.wireType != expected) {
                throw malformed(
                    "field ${tag.fieldNumber} has wire type ${tag.wireType}, expected $expected",
                )
            }
        }

        private inner class ProtoReader(
            private val source: ByteArray,
            start: Int,
            private val end: Int,
            private val decoder: Decoder,
        ) {
            private var position = start

            init {
                if (start < 0 || end < start || end > source.size) {
                    throw malformed("message bounds exceed the input")
                }
            }

            fun isAtEnd(): Boolean = position == end

            fun readTag(): Tag {
                decoder.countField()
                val raw = readVarint()
                if (raw <= 0L || raw > Int.MAX_VALUE) throw malformed("invalid protobuf field tag")
                val value = raw.toInt()
                val fieldNumber = value ushr 3
                if (fieldNumber == 0) throw malformed("invalid protobuf field number")
                return Tag(fieldNumber, value and 0x7)
            }

            fun readVarint(): Long {
                var value = 0L
                var shift = 0
                repeat(MAX_VARINT_BYTES) {
                    val byte = readByte()
                    if (shift == 63 && byte and 0xFE != 0) {
                        throw malformed("protobuf varint overflows")
                    }
                    value = value or ((byte and 0x7F).toLong() shl shift)
                    if (byte and 0x80 == 0) return value
                    shift += 7
                }
                throw malformed("unterminated protobuf varint")
            }

            fun readString(label: String): String {
                val bounds = readLengthDelimitedBounds()
                return decoder.decodeString(bounds.first, bounds.second, label)
            }

            fun readLengthDelimitedReader(): ProtoReader {
                val bounds = readLengthDelimitedBounds()
                return decoder.reader(bounds.first, bounds.second)
            }

            fun skip(wireType: Int) {
                when (wireType) {
                    WIRE_VARINT -> readVarint()
                    WIRE_FIXED64 -> skipBytes(Long.SIZE_BYTES)
                    WIRE_LENGTH_DELIMITED -> position = readLengthDelimitedBounds().second
                    WIRE_FIXED32 -> skipBytes(Int.SIZE_BYTES)
                    else -> throw malformed("unsupported protobuf wire type $wireType")
                }
            }

            private fun readLengthDelimitedBounds(): Pair<Int, Int> {
                val rawLength = readVarint()
                if (rawLength < 0L || rawLength > Int.MAX_VALUE) {
                    throw malformed("invalid length-delimited field size")
                }
                val length = rawLength.toInt()
                if (length > end - position) {
                    throw EOFException("Unexpected end of BundleConfig.pb field")
                }
                val start = position
                position += length
                return start to position
            }

            private fun readByte(): Int {
                if (position >= end) throw EOFException("Unexpected end of BundleConfig.pb")
                return source[position++].toInt() and 0xFF
            }

            private fun skipBytes(count: Int) {
                if (count < 0 || count > end - position) {
                    throw EOFException("Unexpected end of BundleConfig.pb")
                }
                position += count
            }
        }
    }

    private data class Tag(val fieldNumber: Int, val wireType: Int)

    private fun Int.toBundleType(): AabBundleType = when (this) {
        0 -> AabBundleType.REGULAR
        1 -> AabBundleType.APEX
        2 -> AabBundleType.ASSET_ONLY
        else -> AabBundleType.UNKNOWN
    }

    private fun Int.toSplitDimension(): AabSplitDimension = when (this) {
        0 -> AabSplitDimension.UNSPECIFIED
        1 -> AabSplitDimension.ABI
        2 -> AabSplitDimension.SCREEN_DENSITY
        3 -> AabSplitDimension.LANGUAGE
        4 -> AabSplitDimension.TEXTURE_COMPRESSION_FORMAT
        6 -> AabSplitDimension.DEVICE_TIER
        7 -> AabSplitDimension.COUNTRY_SET
        8 -> AabSplitDimension.AI_MODEL_VERSION
        9 -> AabSplitDimension.DEVICE_GROUP
        else -> AabSplitDimension.UNKNOWN
    }

    private fun Int.toAssetCompression(): AabAssetModuleCompression = when (this) {
        0 -> AabAssetModuleCompression.UNSPECIFIED
        1 -> AabAssetModuleCompression.UNCOMPRESSED
        2 -> AabAssetModuleCompression.COMPRESSED
        else -> AabAssetModuleCompression.UNKNOWN
    }

    private fun Int.toApkCompressionAlgorithm(): AabApkCompressionAlgorithm = when (this) {
        0 -> AabApkCompressionAlgorithm.DEFAULT
        1 -> AabApkCompressionAlgorithm.P7ZIP
        else -> AabApkCompressionAlgorithm.UNKNOWN
    }

    private fun validateLimits(limits: Limits) {
        val values = listOf(
            limits.maxInputBytes,
            limits.maxFields,
            limits.maxDepth,
            limits.maxDecodedStringChars,
            limits.maxSingleStringChars,
            limits.maxSplitDimensions,
            limits.maxUncompressedGlobs,
        )
        require(values.all { value -> value >= 0 }) { "BundleConfig decoder limits must be non-negative" }
    }

    private fun malformed(detail: String): IOException = IOException("Invalid BundleConfig.pb: $detail")

    private fun limit(detail: String): AabBundleConfigLimitException =
        AabBundleConfigLimitException("BundleConfig.pb $detail")

    private const val MAX_VARINT_BYTES = 10
    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5

    private const val BUNDLE_CONFIG_BUNDLETOOL_FIELD = 1
    private const val BUNDLE_CONFIG_OPTIMIZATIONS_FIELD = 2
    private const val BUNDLE_CONFIG_COMPRESSION_FIELD = 3
    private const val BUNDLE_CONFIG_TYPE_FIELD = 8
    private const val BUNDLE_CONFIG_LOCALES_FIELD = 9
    private const val BUNDLETOOL_VERSION_FIELD = 2
    private const val OPTIMIZATIONS_SPLITS_CONFIG_FIELD = 1
    private const val OPTIMIZATIONS_UNCOMPRESS_NATIVE_FIELD = 2
    private const val OPTIMIZATIONS_UNCOMPRESS_DEX_FIELD = 3
    private const val ENABLED_FIELD = 1
    private const val SPLITS_CONFIG_DIMENSION_FIELD = 1
    private const val SPLIT_DIMENSION_VALUE_FIELD = 1
    private const val SPLIT_DIMENSION_NEGATE_FIELD = 2
    private const val SPLIT_DIMENSION_SUFFIX_STRIPPING_FIELD = 3
    private const val SUFFIX_STRIPPING_ENABLED_FIELD = 1
    private const val SUFFIX_STRIPPING_DEFAULT_SUFFIX_FIELD = 2
    private const val COMPRESSION_UNCOMPRESSED_GLOB_FIELD = 1
    private const val COMPRESSION_ASSET_MODULE_DEFAULT_FIELD = 2
    private const val COMPRESSION_APK_ALGORITHM_FIELD = 3
    private const val LOCALES_INJECT_CONFIG_FIELD = 1
}
