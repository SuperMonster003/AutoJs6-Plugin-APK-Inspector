package io.github.supermonster003.autojs6.plugin.apkinspector

import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Bounded decoder for the AAPT2 ResourceTable protobuf stored as module/resources.pb in an AAB.
 * Only scalar strings, file paths, references, and the configuration axes available in
 * [PackageDeviceSpec] are retained.
 */
internal object AabResourceTableDecoder {

    internal data class Limits(
        val maxInputBytes: Int = PackageResourceFallbackInspector.MAX_RESOURCE_TABLE_BYTES,
        val maxFields: Int = 1_000_000,
        val maxPackages: Int = 64,
        val maxTypes: Int = 2_048,
        val maxEntries: Int = 100_000,
        val maxConfigValues: Int = 250_000,
        val maxDecodedStringChars: Int = 16 * 1024 * 1024,
        val maxSingleStringChars: Int = 16 * 1024,
    ) {
        init {
            require(maxInputBytes > 0)
            require(maxFields > 0)
            require(maxPackages > 0)
            require(maxTypes > 0)
            require(maxEntries > 0)
            require(maxConfigValues > 0)
            require(maxDecodedStringChars > 0)
            require(maxSingleStringChars > 0)
        }
    }

    fun decode(
        bytes: ByteArray,
        limits: Limits = Limits(),
    ): FallbackResourceTable {
        if (bytes.isEmpty()) throw malformed("resource table is empty")
        if (bytes.size > limits.maxInputBytes) {
            throw IOException("AAB resource table exceeds the fallback size limit")
        }
        return Decoder(bytes, limits).decode()
    }

    private class Decoder(
        private val bytes: ByteArray,
        private val limits: Limits,
    ) {

        private var fieldCount = 0
        private var packageCount = 0
        private var typeCount = 0
        private var entryCount = 0
        private var configValueCount = 0
        private var decodedStringChars = 0

        fun decode(): FallbackResourceTable {
            val packages = ArrayList<ParsedPackage>()
            val reader = reader(0, bytes.size)
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == RESOURCE_TABLE_PACKAGE_FIELD) {
                    requireWire(tag, WIRE_LENGTH_DELIMITED)
                    packageCount++
                    if (packageCount > limits.maxPackages) {
                        throw malformed("package count exceeds the fallback limit")
                    }
                    packages += readPackage(reader.readLengthDelimitedReader())
                } else {
                    reader.skip(tag.wireType)
                }
            }
            val resources = ArrayList<IndexedFallbackResource>()
            val names = HashSet<String>()
            val ids = HashSet<Int>()
            packages.forEach { parsedPackage ->
                parsedPackage.types.forEach { type ->
                    type.entries.forEach { entry ->
                        if (type.name.isBlank() || entry.name.isBlank() || entry.variants.isEmpty()) {
                            return@forEach
                        }
                        val id = createResourceId(
                            packageId = parsedPackage.id,
                            typeId = type.id,
                            entryId = entry.id,
                        )
                        val nameKey = "${parsedPackage.name.orEmpty()}:${type.name}/${entry.name}"
                        if (!names.add(nameKey)) {
                            throw malformed("resource table contains a duplicate resource name")
                        }
                        if (id != null && !ids.add(id)) {
                            throw malformed("resource table contains a duplicate resource ID")
                        }
                        resources += IndexedFallbackResource(
                            packageName = parsedPackage.name,
                            type = type.name,
                            name = entry.name,
                            id = id,
                            variants = entry.variants,
                        )
                    }
                }
            }
            return IndexedFallbackResourceTable(resources)
        }

        private fun readPackage(reader: ProtoReader): ParsedPackage {
            var id = 0
            var name: String? = null
            val types = ArrayList<ParsedType>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    PACKAGE_ID_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        id = readWrappedId(reader.readLengthDelimitedReader(), "package ID")
                    }
                    PACKAGE_NAME_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        name = reader.readString("package name").takeIf(String::isNotBlank)
                    }
                    PACKAGE_TYPE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        typeCount++
                        if (typeCount > limits.maxTypes) {
                            throw malformed("type count exceeds the fallback limit")
                        }
                        types += readType(reader.readLengthDelimitedReader())
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            if (id !in 0..0xFF) throw malformed("package ID is out of range")
            return ParsedPackage(id, name, types)
        }

        private fun readType(reader: ProtoReader): ParsedType {
            var id = 0
            var name = ""
            val entries = ArrayList<ParsedEntry>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    TYPE_ID_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        id = readWrappedId(reader.readLengthDelimitedReader(), "type ID")
                    }
                    TYPE_NAME_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        name = reader.readString("type name")
                    }
                    TYPE_ENTRY_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        entryCount++
                        if (entryCount > limits.maxEntries) {
                            throw malformed("entry count exceeds the fallback limit")
                        }
                        entries += readEntry(reader.readLengthDelimitedReader())
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            if (id !in 0..0xFF) throw malformed("type ID is out of range")
            return ParsedType(id, name, entries)
        }

        private fun readEntry(reader: ProtoReader): ParsedEntry {
            var id = 0
            var name = ""
            val variants = ArrayList<FallbackResourceVariant>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    ENTRY_ID_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        id = readWrappedId(reader.readLengthDelimitedReader(), "entry ID")
                    }
                    ENTRY_NAME_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        name = reader.readString("entry name")
                    }
                    ENTRY_CONFIG_VALUE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        configValueCount++
                        if (configValueCount > limits.maxConfigValues) {
                            throw malformed("configuration value count exceeds the fallback limit")
                        }
                        readConfigValue(reader.readLengthDelimitedReader())?.let(variants::add)
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            if (id !in 0..0xFFFF) throw malformed("entry ID is out of range")
            return ParsedEntry(id, name, variants)
        }

        private fun readConfigValue(reader: ProtoReader): FallbackResourceVariant? {
            var configuration = FallbackResourceConfiguration()
            var value: FallbackResourceValue? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    CONFIG_VALUE_CONFIGURATION_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        configuration = readConfiguration(reader.readLengthDelimitedReader())
                    }
                    CONFIG_VALUE_VALUE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        value = readValue(reader.readLengthDelimitedReader())
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            return value?.let { FallbackResourceVariant(configuration, it) }
        }

        private fun readConfiguration(reader: ProtoReader): FallbackResourceConfiguration {
            var locale: String? = null
            var density = 0
            var sdkVersion = 0
            var unsupported = false
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    CONFIGURATION_LOCALE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        locale = reader.readString("resource locale").takeIf(String::isNotBlank)
                    }
                    CONFIGURATION_DENSITY_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        density = reader.readVarint().toBoundedInt("resource density", 0xFFFF)
                    }
                    CONFIGURATION_SDK_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        sdkVersion = reader.readVarint().toBoundedInt("resource SDK", 10_000)
                    }
                    else -> {
                        unsupported = true
                        reader.skip(tag.wireType)
                    }
                }
            }
            return FallbackResourceConfiguration(
                locale = locale,
                density = density,
                sdkVersion = sdkVersion,
                hasUnsupportedQualifiers = unsupported,
            )
        }

        private fun readValue(reader: ProtoReader): FallbackResourceValue? {
            var result: FallbackResourceValue? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == VALUE_ITEM_FIELD) {
                    requireWire(tag, WIRE_LENGTH_DELIMITED)
                    val item = readItem(reader.readLengthDelimitedReader())
                    if (result != null && item != null) {
                        throw malformed("resource value contains multiple scalar items")
                    }
                    result = item ?: result
                } else {
                    reader.skip(tag.wireType)
                }
            }
            return result
        }

        private fun readItem(reader: ProtoReader): FallbackResourceValue? {
            var result: FallbackResourceValue? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                val item = when (tag.fieldNumber) {
                    ITEM_REFERENCE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        readReference(reader.readLengthDelimitedReader())
                            ?.let(FallbackResourceValue::Reference)
                    }
                    ITEM_STRING_FIELD, ITEM_RAW_STRING_FIELD, ITEM_STYLED_STRING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        readWrappedString(reader.readLengthDelimitedReader())
                            ?.let(FallbackResourceValue::Text)
                    }
                    ITEM_FILE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        readFileReference(reader.readLengthDelimitedReader())
                            ?.let(FallbackResourceValue::Text)
                    }
                    else -> {
                        reader.skip(tag.wireType)
                        null
                    }
                }
                if (result != null && item != null) {
                    throw malformed("resource item contains multiple values")
                }
                result = item ?: result
            }
            return result
        }

        private fun readReference(reader: ProtoReader): FallbackResourceReference? {
            var referenceType = REFERENCE_TYPE_RESOURCE
            var id: Int? = null
            var name: String? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    REFERENCE_TYPE_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        referenceType = reader.readVarint().toBoundedInt("reference type", 16)
                    }
                    REFERENCE_ID_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        id = reader.readVarint().takeIf { it in 1..0xFFFF_FFFFL }?.toInt()
                    }
                    REFERENCE_NAME_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        name = reader.readString("resource reference").takeIf(String::isNotBlank)
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            if (referenceType != REFERENCE_TYPE_RESOURCE) return null
            val named = name?.let { parseFallbackResourceReference("@$it") }
            return when {
                named != null -> named.copy(id = id ?: named.id)
                id != null -> FallbackResourceReference(id = id)
                else -> null
            }
        }

        private fun readWrappedString(reader: ProtoReader): String? {
            var value: String? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == WRAPPED_STRING_VALUE_FIELD) {
                    requireWire(tag, WIRE_LENGTH_DELIMITED)
                    value = reader.readString("resource string")
                } else {
                    reader.skip(tag.wireType)
                }
            }
            return value
        }

        private fun readFileReference(reader: ProtoReader): String? {
            var path: String? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == FILE_REFERENCE_PATH_FIELD) {
                    requireWire(tag, WIRE_LENGTH_DELIMITED)
                    path = reader.readString("resource file path")
                } else {
                    reader.skip(tag.wireType)
                }
            }
            return path?.takeIf(String::isNotBlank)
        }

        private fun readWrappedId(reader: ProtoReader, label: String): Int {
            var id = 0
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == WRAPPED_ID_VALUE_FIELD) {
                    requireWire(tag, WIRE_VARINT)
                    id = reader.readVarint().toBoundedInt(label, 0xFFFF_FFFF.toLong())
                } else {
                    reader.skip(tag.wireType)
                }
            }
            return id
        }

        private fun Long.toBoundedInt(label: String, maximum: Long): Int {
            if (this !in 0..maximum) throw malformed("$label is out of range")
            return toInt()
        }

        private fun createResourceId(
            packageId: Int,
            typeId: Int,
            entryId: Int,
        ): Int? {
            if (packageId !in 1..0xFF || typeId !in 1..0xFF || entryId !in 0..0xFFFF) {
                return null
            }
            return (packageId shl 24) or (typeId shl 16) or entryId
        }

        private fun reader(start: Int, end: Int): ProtoReader =
            ProtoReader(bytes, start, end, this)

        private fun countField() {
            fieldCount++
            if (fieldCount > limits.maxFields) {
                throw malformed("protobuf field count exceeds the fallback limit")
            }
        }

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
                throw malformed("$label exceeds the single-string fallback limit")
            }
            decodedStringChars = Math.addExact(decodedStringChars, value.length)
            if (decodedStringChars > limits.maxDecodedStringChars) {
                throw malformed("decoded strings exceed the fallback limit")
            }
            return value
        }

        private fun requireWire(tag: Tag, expected: Int) {
            if (tag.wireType != expected) {
                throw malformed(
                    "field ${tag.fieldNumber} has wire type ${tag.wireType}, expected $expected",
                )
            }
        }

        private class ProtoReader(
            private val bytes: ByteArray,
            private var position: Int,
            private val end: Int,
            private val decoder: Decoder,
        ) {

            fun isAtEnd(): Boolean = position == end

            fun readTag(): Tag {
                val raw = readVarint()
                if (raw > Int.MAX_VALUE) throw malformed("invalid protobuf field tag")
                val value = raw.toInt()
                val fieldNumber = value ushr 3
                if (fieldNumber == 0) throw malformed("invalid protobuf field number")
                decoder.countField()
                return Tag(fieldNumber, value and 0x7)
            }

            fun readVarint(): Long {
                var result = 0L
                for (index in 0 until MAX_VARINT_BYTES) {
                    val byte = readByte()
                    if (index == MAX_VARINT_BYTES - 1 && byte > 1) {
                        throw malformed("protobuf varint overflows 64 bits")
                    }
                    result = result or ((byte and 0x7F).toLong() shl (index * 7))
                    if (byte and 0x80 == 0) return result
                }
                throw malformed("protobuf varint exceeds its maximum length")
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
                    WIRE_FIXED64 -> skipBytes(8)
                    WIRE_LENGTH_DELIMITED -> {
                        val bounds = readLengthDelimitedBounds()
                        position = bounds.second
                    }
                    WIRE_FIXED32 -> skipBytes(4)
                    else -> throw malformed("unsupported protobuf wire type $wireType")
                }
            }

            private fun readLengthDelimitedBounds(): Pair<Int, Int> {
                val length = readVarint()
                if (length > Int.MAX_VALUE) {
                    throw malformed("invalid length-delimited field size")
                }
                val fieldEnd = position.toLong() + length
                if (fieldEnd > end) throw EOFException("Unexpected end of AAB resource protobuf field")
                val start = position
                position = fieldEnd.toInt()
                return start to position
            }

            private fun skipBytes(length: Int) {
                if (position > end - length) {
                    throw EOFException("Unexpected end of AAB resource protobuf field")
                }
                position += length
            }

            private fun readByte(): Int {
                if (position >= end) throw EOFException("Unexpected end of AAB resource protobuf")
                return bytes[position++].toInt() and 0xFF
            }
        }
    }

    private data class ParsedPackage(
        val id: Int,
        val name: String?,
        val types: List<ParsedType>,
    )

    private data class ParsedType(
        val id: Int,
        val name: String,
        val entries: List<ParsedEntry>,
    )

    private data class ParsedEntry(
        val id: Int,
        val name: String,
        val variants: List<FallbackResourceVariant>,
    )

    private data class Tag(val fieldNumber: Int, val wireType: Int)

    private fun malformed(detail: String): IOException =
        IOException("Invalid AAB resource table protobuf: $detail")

    private const val MAX_VARINT_BYTES = 10
    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5

    private const val RESOURCE_TABLE_PACKAGE_FIELD = 2

    private const val PACKAGE_ID_FIELD = 1
    private const val PACKAGE_NAME_FIELD = 2
    private const val PACKAGE_TYPE_FIELD = 3

    private const val TYPE_ID_FIELD = 1
    private const val TYPE_NAME_FIELD = 2
    private const val TYPE_ENTRY_FIELD = 3

    private const val ENTRY_ID_FIELD = 1
    private const val ENTRY_NAME_FIELD = 2
    private const val ENTRY_CONFIG_VALUE_FIELD = 6

    private const val CONFIG_VALUE_CONFIGURATION_FIELD = 1
    private const val CONFIG_VALUE_VALUE_FIELD = 2
    private const val VALUE_ITEM_FIELD = 4

    private const val ITEM_REFERENCE_FIELD = 1
    private const val ITEM_STRING_FIELD = 2
    private const val ITEM_RAW_STRING_FIELD = 3
    private const val ITEM_STYLED_STRING_FIELD = 4
    private const val ITEM_FILE_FIELD = 5

    private const val WRAPPED_ID_VALUE_FIELD = 1
    private const val WRAPPED_STRING_VALUE_FIELD = 1
    private const val FILE_REFERENCE_PATH_FIELD = 1

    private const val REFERENCE_TYPE_FIELD = 1
    private const val REFERENCE_ID_FIELD = 2
    private const val REFERENCE_NAME_FIELD = 3
    private const val REFERENCE_TYPE_RESOURCE = 0

    private const val CONFIGURATION_LOCALE_FIELD = 3
    private const val CONFIGURATION_DENSITY_FIELD = 18
    private const val CONFIGURATION_SDK_FIELD = 24
}
