package io.github.supermonster003.autojs6.plugin.apkinspector

import org.autojs.plugin.packagearchive.PackageInspectionLimits
import org.autojs.plugin.packagearchive.PackageDeviceSpec
import org.autojs.plugin.packagearchive.AndroidPackageFormat

import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.model.ResourceEntry
import com.reandroid.arsc.value.Entry
import com.reandroid.arsc.value.ValueType
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.math.abs

internal enum class PackageResourceFallbackIssue {
    RESOURCE_TABLE_MISSING,
    RESOURCE_TABLE_LIMIT,
    NESTED_SCAN_LIMIT,
    RESOURCE_TABLE_INVALID,
    LABEL_UNRESOLVED,
    ICON_UNRESOLVED,
    ICON_LIMIT,
    ICON_INVALID,
}

internal data class PackageResourceIcon(
    val archivePath: String,
    val bytes: ByteArray,
)

internal data class PackageResourceFallback(
    val label: String? = null,
    val icon: PackageResourceIcon? = null,
    val issues: List<PackageResourceFallbackIssue> = emptyList(),
)

internal data class FallbackResourceReference(
    val packageName: String? = null,
    val type: String? = null,
    val name: String? = null,
    val id: Int? = null,
)

internal data class FallbackResourceConfiguration(
    val locale: String? = null,
    val density: Int = 0,
    val sdkVersion: Int = 0,
    val hasUnsupportedQualifiers: Boolean = false,
)

internal sealed interface FallbackResourceValue {
    data class Text(val value: String) : FallbackResourceValue
    data class Reference(val value: FallbackResourceReference) : FallbackResourceValue
}

internal data class FallbackResourceVariant(
    val configuration: FallbackResourceConfiguration,
    val value: FallbackResourceValue,
)

internal interface FallbackResourceTable {
    fun variants(reference: FallbackResourceReference): List<FallbackResourceVariant>
}

internal data class IndexedFallbackResource(
    val packageName: String?,
    val type: String,
    val name: String,
    val id: Int?,
    val variants: List<FallbackResourceVariant>,
)

internal class IndexedFallbackResourceTable(
    resources: List<IndexedFallbackResource>,
) : FallbackResourceTable {

    private data class NameKey(
        val packageName: String?,
        val type: String,
        val name: String,
    )

    private data class UnqualifiedNameKey(
        val type: String,
        val name: String,
    )

    private val byId = buildMap<Int, List<FallbackResourceVariant>> {
        resources.forEach { resource ->
            resource.id?.let { id -> putIfAbsent(id, resource.variants) }
        }
    }
    private val byName = buildMap<NameKey, List<FallbackResourceVariant>> {
        resources.forEach { resource ->
            putIfAbsent(
                NameKey(resource.packageName, resource.type, resource.name),
                resource.variants,
            )
        }
    }
    private val byUnqualifiedName = buildMap<UnqualifiedNameKey, List<FallbackResourceVariant>> {
        resources.groupBy { resource -> UnqualifiedNameKey(resource.type, resource.name) }
            .forEach { (key, matches) ->
                if (matches.size == 1) put(key, matches.single().variants)
            }
    }

    override fun variants(reference: FallbackResourceReference): List<FallbackResourceVariant> {
        reference.id?.let { id -> byId[id]?.let { return it } }
        val type = reference.type ?: return emptyList()
        val name = reference.name ?: return emptyList()
        reference.packageName?.let { packageName ->
            byName[NameKey(packageName, type, name)]?.let { return it }
        }
        return byUnqualifiedName[UnqualifiedNameKey(type, name)].orEmpty()
    }
}

internal fun parseFallbackResourceReference(value: String?): FallbackResourceReference? {
    var target = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (!target.startsWith('@') || target.startsWith("@null", ignoreCase = true)) return null
    target = target.drop(1)
    if (target.startsWith('*')) target = target.drop(1)
    if (target.startsWith('+')) target = target.drop(1)
    if (target.startsWith("0x", ignoreCase = true)) {
        val id = target.drop(2).toLongOrNull(16)
            ?.takeIf { it in 1..0xFFFF_FFFFL }
            ?.toInt()
            ?: return null
        return FallbackResourceReference(id = id)
    }
    val slash = target.indexOf('/')
    if (slash <= 0 || slash == target.lastIndex) return null
    val qualifiedType = target.substring(0, slash)
    val name = target.substring(slash + 1)
    val colon = qualifiedType.indexOf(':')
    val packageName = qualifiedType.takeIf { colon >= 0 }?.substring(0, colon)
    val type = qualifiedType.substring(colon + 1)
    if (
        packageName?.let(::isSafeResourceToken) == false ||
        !isSafeResourceToken(type) ||
        !isSafeResourceToken(name)
    ) {
        return null
    }
    return FallbackResourceReference(
        packageName = packageName,
        type = type,
        name = name,
    )
}

private fun isSafeResourceToken(value: String): Boolean =
    value.isNotEmpty() && value.length <= MAX_RESOURCE_TOKEN_CHARS && value.all { character ->
        character.isLetterOrDigit() || character == '_' || character == '.' ||
            character == '$' || character == '-'
    }

internal object PackageResourceFallbackInspector {

    const val MAX_RESOURCE_TABLE_BYTES = 64 * 1024 * 1024
    const val MAX_ICON_BYTES = 8 * 1024 * 1024
    const val MAX_NESTED_SCAN_BYTES = PackageInspectionLimits.TOTAL_SCAN_BYTES
    const val MAX_NESTED_ZIP_ENTRIES = PackageInspectionLimits.ARCHIVE_ENTRIES

    internal data class Limits(
        val maxResourceTableBytes: Int = MAX_RESOURCE_TABLE_BYTES,
        val maxIconBytes: Int = MAX_ICON_BYTES,
        val maxNestedScanBytes: Long = MAX_NESTED_SCAN_BYTES,
        val maxNestedZipEntries: Int = MAX_NESTED_ZIP_ENTRIES,
        val maxEntryNameChars: Int = PackageInspectionLimits.ENTRY_NAME_CHARS,
        val aabDecoderLimits: AabResourceTableDecoder.Limits = AabResourceTableDecoder.Limits(),
    ) {
        init {
            require(maxResourceTableBytes > 0)
            require(maxIconBytes > 0)
            require(maxNestedScanBytes > 0L)
            require(maxNestedZipEntries > 0)
            require(maxEntryNameChars > 0)
        }
    }

    fun inspect(
        archive: AndroidPackageArchive,
        device: PackageDeviceSpec,
        needLabel: Boolean = true,
        needIcon: Boolean = true,
        limits: Limits = Limits(),
    ): PackageResourceFallback {
        if (!needLabel && !needIcon) return PackageResourceFallback()
        val manifest = archive.baseManifest
            ?: return PackageResourceFallback(
                issues = buildList {
                    if (needLabel) add(PackageResourceFallbackIssue.LABEL_UNRESOLVED)
                    if (needIcon) add(PackageResourceFallbackIssue.ICON_UNRESOLVED)
                },
            )
        val literalLabel = manifest.applicationLabel
            ?.takeIf { needLabel }
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.takeUnless { value -> value.startsWith('@') || value.startsWith('?') }
        val labelReference = manifest.applicationLabel
            ?.takeIf { needLabel }
            ?.let(::parseFallbackResourceReference)
        val iconReferences = if (needIcon) {
            listOfNotNull(
                parseFallbackResourceReference(manifest.applicationIcon),
                parseFallbackResourceReference(manifest.applicationRoundIcon),
            ).distinct()
        } else {
            emptyList()
        }

        if (labelReference == null && iconReferences.isEmpty()) {
            return PackageResourceFallback(
                label = literalLabel,
                issues = buildList {
                    if (needLabel && literalLabel == null) {
                        add(PackageResourceFallbackIssue.LABEL_UNRESOLVED)
                    }
                    if (needIcon) add(PackageResourceFallbackIssue.ICON_UNRESOLVED)
                },
            )
        }

        val sourceResult = when (archive.format) {
            AndroidPackageFormat.AAB -> loadAabSource(archive, limits)
            AndroidPackageFormat.APK -> loadDirectApkSource(archive, limits)
            else -> loadNestedApkSource(archive, limits)
        }
        if (sourceResult is ResourceSourceResult.Failure) {
            return PackageResourceFallback(
                label = literalLabel,
                issues = listOf(sourceResult.issue),
            )
        }
        val source = (sourceResult as ResourceSourceResult.Success).source
        val table = try {
            when (source.kind) {
                ResourceTableKind.AAB_PROTO -> AabResourceTableDecoder.decode(
                    bytes = source.tableBytes,
                    limits = limits.aabDecoderLimits.copy(
                        maxInputBytes = minOf(
                            limits.aabDecoderLimits.maxInputBytes,
                            limits.maxResourceTableBytes,
                        ),
                    ),
                )
                ResourceTableKind.APK_ARSC -> decodeArsc(source.tableBytes)
            }
        } catch (_: Exception) {
            return PackageResourceFallback(
                label = literalLabel,
                issues = listOf(PackageResourceFallbackIssue.RESOURCE_TABLE_INVALID),
            )
        }

        val resolver = FallbackResourceResolver(table, device)
        val issues = mutableListOf<PackageResourceFallbackIssue>()
        val resolvedLabel = literalLabel ?: labelReference?.let(resolver::resolveText)
        if (needLabel && resolvedLabel == null) {
            issues += PackageResourceFallbackIssue.LABEL_UNRESOLVED
        }

        val iconPath = iconReferences.firstNotNullOfOrNull(resolver::resolveRasterPath)
        val icon = when {
            !needIcon -> null
            iconPath == null -> {
                issues += PackageResourceFallbackIssue.ICON_UNRESOLVED
                null
            }
            else -> when (val entry = source.readEntry(iconPath)) {
                is EntryReadResult.Bytes -> {
                    if (isRecognizedRaster(entry.value)) {
                        PackageResourceIcon(entry.archivePath, entry.value)
                    } else {
                        issues += PackageResourceFallbackIssue.ICON_INVALID
                        null
                    }
                }
                EntryReadResult.Limit -> {
                    issues += PackageResourceFallbackIssue.ICON_LIMIT
                    null
                }
                EntryReadResult.ScanLimit -> {
                    issues += PackageResourceFallbackIssue.NESTED_SCAN_LIMIT
                    null
                }
                EntryReadResult.Missing, EntryReadResult.Invalid -> {
                    issues += PackageResourceFallbackIssue.ICON_UNRESOLVED
                    null
                }
            }
        }
        return PackageResourceFallback(
            label = resolvedLabel,
            icon = icon,
            issues = issues.distinct(),
        )
    }

    private fun loadAabSource(
        archive: AndroidPackageArchive,
        limits: Limits,
    ): ResourceSourceResult {
        val module = archive.aabModules.firstOrNull { it == "base" }
            ?: archive.aabModules.firstOrNull()
            ?: return ResourceSourceResult.Failure(
                PackageResourceFallbackIssue.RESOURCE_TABLE_MISSING,
            )
        val tablePath = "$module/resources.pb"
        val tableRead = readOuterZipEntry(
            file = archive.sourceFile,
            path = tablePath,
            maxBytes = limits.maxResourceTableBytes,
            limits = limits,
        )
        val tableBytes = when (tableRead) {
            is EntryReadResult.Bytes -> tableRead.value
            EntryReadResult.Limit -> return ResourceSourceResult.Failure(
                PackageResourceFallbackIssue.RESOURCE_TABLE_LIMIT,
            )
            EntryReadResult.Invalid -> return ResourceSourceResult.Failure(
                PackageResourceFallbackIssue.RESOURCE_TABLE_INVALID,
            )
            EntryReadResult.ScanLimit -> return ResourceSourceResult.Failure(
                PackageResourceFallbackIssue.NESTED_SCAN_LIMIT,
            )
            EntryReadResult.Missing -> return ResourceSourceResult.Failure(
                PackageResourceFallbackIssue.RESOURCE_TABLE_MISSING,
            )
        }
        return ResourceSourceResult.Success(
            ResourceSource(
                tableBytes = tableBytes,
                kind = ResourceTableKind.AAB_PROTO,
                readEntry = readEntry@{ resourcePath ->
                    val path = normalizeResourceArchivePath(resourcePath, limits)
                        ?: return@readEntry EntryReadResult.Invalid
                    val modulePath = if (path.startsWith("$module/")) path else "$module/$path"
                    readOuterZipEntry(
                        file = archive.sourceFile,
                        path = modulePath,
                        maxBytes = limits.maxIconBytes,
                        limits = limits,
                    )
                },
            ),
        )
    }

    private fun loadDirectApkSource(
        archive: AndroidPackageArchive,
        limits: Limits,
    ): ResourceSourceResult {
        val tableRead = readOuterZipEntry(
            file = archive.sourceFile,
            path = APK_RESOURCE_TABLE_ENTRY,
            maxBytes = limits.maxResourceTableBytes,
            limits = limits,
        )
        val tableBytes = when (tableRead) {
            is EntryReadResult.Bytes -> tableRead.value
            EntryReadResult.Limit -> return ResourceSourceResult.Failure(
                PackageResourceFallbackIssue.RESOURCE_TABLE_LIMIT,
            )
            EntryReadResult.Invalid -> return ResourceSourceResult.Failure(
                PackageResourceFallbackIssue.RESOURCE_TABLE_INVALID,
            )
            EntryReadResult.ScanLimit -> return ResourceSourceResult.Failure(
                PackageResourceFallbackIssue.NESTED_SCAN_LIMIT,
            )
            EntryReadResult.Missing -> return ResourceSourceResult.Failure(
                PackageResourceFallbackIssue.RESOURCE_TABLE_MISSING,
            )
        }
        return ResourceSourceResult.Success(
            ResourceSource(
                tableBytes = tableBytes,
                kind = ResourceTableKind.APK_ARSC,
                readEntry = readEntry@{ resourcePath ->
                    val path = normalizeResourceArchivePath(resourcePath, limits)
                        ?: return@readEntry EntryReadResult.Invalid
                    readOuterZipEntry(
                        file = archive.sourceFile,
                        path = path,
                        maxBytes = limits.maxIconBytes,
                        limits = limits,
                    )
                },
            ),
        )
    }

    private fun loadNestedApkSource(
        archive: AndroidPackageArchive,
        limits: Limits,
    ): ResourceSourceResult {
        val basePath = archive.baseApk?.archivePath
            ?: return ResourceSourceResult.Failure(
                PackageResourceFallbackIssue.RESOURCE_TABLE_MISSING,
            )
        val scanBudget = NestedScanBudget(limits.maxNestedScanBytes)
        val tableRead = readNestedApkEntry(
            container = archive.sourceFile,
            baseApkPath = basePath,
            nestedPath = APK_RESOURCE_TABLE_ENTRY,
            maxBytes = limits.maxResourceTableBytes,
            limits = limits,
            budget = scanBudget,
        )
        val tableBytes = when (tableRead) {
            is EntryReadResult.Bytes -> tableRead.value
            EntryReadResult.Limit -> return ResourceSourceResult.Failure(
                PackageResourceFallbackIssue.RESOURCE_TABLE_LIMIT,
            )
            EntryReadResult.ScanLimit -> return ResourceSourceResult.Failure(
                PackageResourceFallbackIssue.NESTED_SCAN_LIMIT,
            )
            EntryReadResult.Invalid -> return ResourceSourceResult.Failure(
                PackageResourceFallbackIssue.RESOURCE_TABLE_INVALID,
            )
            EntryReadResult.Missing -> return ResourceSourceResult.Failure(
                PackageResourceFallbackIssue.RESOURCE_TABLE_MISSING,
            )
        }
        return ResourceSourceResult.Success(
            ResourceSource(
                tableBytes = tableBytes,
                kind = ResourceTableKind.APK_ARSC,
                readEntry = readEntry@{ resourcePath ->
                    val path = normalizeResourceArchivePath(resourcePath, limits)
                        ?: return@readEntry EntryReadResult.Invalid
                    readNestedApkEntry(
                        container = archive.sourceFile,
                        baseApkPath = basePath,
                        nestedPath = path,
                        maxBytes = limits.maxIconBytes,
                        limits = limits,
                        budget = scanBudget,
                    )
                },
            ),
        )
    }

    private fun decodeArsc(bytes: ByteArray): FallbackResourceTable {
        if (bytes.size < ARSC_TABLE_HEADER_BYTES) throw IOException("Resource table is truncated")
        if (readUInt16(bytes, 0) != ARSC_TABLE_CHUNK_TYPE) {
            throw IOException("Resource table chunk type is invalid")
        }
        val headerSize = readUInt16(bytes, 2)
        val chunkSize = readUInt32(bytes, 4)
        if (
            headerSize < ARSC_TABLE_HEADER_BYTES ||
            headerSize > bytes.size ||
            chunkSize != bytes.size.toLong()
        ) {
            throw IOException("Resource table boundary is invalid")
        }
        return ArscFallbackResourceTable(
            TableBlock.load(ByteArrayInputStream(bytes)),
        )
    }

    private fun readOuterZipEntry(
        file: java.io.File,
        path: String,
        maxBytes: Int,
        limits: Limits,
    ): EntryReadResult = try {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(path)?.takeUnless(ZipEntry::isDirectory)
                ?: return EntryReadResult.Missing
            if (!isSafeArchivePath(entry.name, limits.maxEntryNameChars)) {
                return EntryReadResult.Invalid
            }
            if (entry.size > maxBytes) return EntryReadResult.Limit
            zip.getInputStream(entry).use { input ->
                EntryReadResult.Bytes(
                    value = input.readBounded(maxBytes) ?: return EntryReadResult.Limit,
                    archivePath = entry.name,
                )
            }
        }
    } catch (_: IOException) {
        EntryReadResult.Invalid
    }

    private fun readNestedApkEntry(
        container: java.io.File,
        baseApkPath: String,
        nestedPath: String,
        maxBytes: Int,
        limits: Limits,
        budget: NestedScanBudget,
    ): EntryReadResult = try {
        ZipFile(container).use { outerZip ->
            val baseEntry = outerZip.getEntry(baseApkPath)?.takeUnless(ZipEntry::isDirectory)
                ?: return EntryReadResult.Invalid
            outerZip.getInputStream(baseEntry).use { rawInput ->
                val boundedInput = NestedBudgetInputStream(BufferedInputStream(rawInput), budget)
                ZipInputStream(boundedInput).use { nestedZip ->
                    var entryCount = 0
                    val drainBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val entry = nestedZip.nextEntry ?: return EntryReadResult.Missing
                        entryCount++
                        if (entryCount > limits.maxNestedZipEntries) return EntryReadResult.Invalid
                        if (!isSafeArchivePath(entry.name, limits.maxEntryNameChars)) {
                            return EntryReadResult.Invalid
                        }
                        if (!entry.isDirectory && entry.name == nestedPath) {
                            if (entry.size > maxBytes) return EntryReadResult.Limit
                            val bytes = nestedZip.readBounded(maxBytes, budget)
                                ?: return EntryReadResult.Limit
                            return EntryReadResult.Bytes(bytes, entry.name)
                        }
                        if (!entry.isDirectory) {
                            nestedZip.drainEntry(budget, drainBuffer)
                        }
                        nestedZip.closeEntry()
                    }
                }
                EntryReadResult.Missing
            }
        }
    } catch (_: NestedScanLimitException) {
        EntryReadResult.ScanLimit
    } catch (_: IOException) {
        EntryReadResult.Invalid
    }

    private fun InputStream.readBounded(
        maxBytes: Int,
        scanBudget: NestedScanBudget? = null,
    ): ByteArray? {
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (read == 0) continue
            scanBudget?.consume(read.toLong())
            if (read > maxBytes - total) return null
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }

    private fun InputStream.drainEntry(
        scanBudget: NestedScanBudget,
        buffer: ByteArray,
    ) {
        while (true) {
            val read = read(buffer)
            if (read < 0) return
            if (read > 0) scanBudget.consume(read.toLong())
        }
    }

    private fun normalizeResourceArchivePath(path: String, limits: Limits): String? {
        val normalized = path.removePrefix("/")
        return normalized.takeIf {
            isSafeArchivePath(it, limits.maxEntryNameChars) &&
                it.startsWith("res/") &&
                isRasterPath(it)
        }
    }

    private fun isSafeArchivePath(path: String, maxChars: Int): Boolean =
        path.isNotBlank() &&
            path.length <= maxChars &&
            path[0] != '/' &&
            '\u0000' !in path &&
            '\\' !in path &&
            !WINDOWS_DRIVE_PATH.containsMatchIn(path) &&
            path.split('/').none { segment -> segment.isEmpty() || segment == ".." }

    private fun isRecognizedRaster(bytes: ByteArray): Boolean =
        bytes.startsWith(PNG_SIGNATURE) ||
            bytes.startsWith(JPEG_SIGNATURE) ||
            bytes.startsWith(GIF87A_SIGNATURE) ||
            bytes.startsWith(GIF89A_SIGNATURE) ||
            (
                bytes.size >= 12 &&
                    bytes.copyOfRange(0, 4).contentEquals(WEBP_RIFF_SIGNATURE) &&
                    bytes.copyOfRange(8, 12).contentEquals(WEBP_FORMAT_SIGNATURE)
                )

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

    private fun readUInt16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readUInt32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24)

    private sealed interface ResourceSourceResult {
        data class Success(val source: ResourceSource) : ResourceSourceResult
        data class Failure(val issue: PackageResourceFallbackIssue) : ResourceSourceResult
    }

    private data class ResourceSource(
        val tableBytes: ByteArray,
        val kind: ResourceTableKind,
        val readEntry: (String) -> EntryReadResult,
    )

    private enum class ResourceTableKind {
        APK_ARSC,
        AAB_PROTO,
    }

    private sealed interface EntryReadResult {
        data class Bytes(val value: ByteArray, val archivePath: String) : EntryReadResult
        data object Missing : EntryReadResult
        data object Limit : EntryReadResult
        data object ScanLimit : EntryReadResult
        data object Invalid : EntryReadResult
    }

    private class NestedScanBudget(var remaining: Long) {

        fun consume(byteCount: Long) {
            if (byteCount < 0L || byteCount > remaining) {
                remaining = 0L
                throw NestedScanLimitException()
            }
            remaining -= byteCount
        }
    }

    private class NestedBudgetInputStream(
        input: InputStream,
        private val budget: NestedScanBudget,
    ) : FilterInputStream(input) {

        override fun read(): Int {
            if (budget.remaining <= 0L) throw NestedScanLimitException()
            return super.read().also { value ->
                if (value >= 0) budget.consume(1L)
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (budget.remaining <= 0L) throw NestedScanLimitException()
            val allowed = minOf(length.toLong(), budget.remaining).toInt()
            return super.read(buffer, offset, allowed).also { read ->
                if (read > 0) budget.consume(read.toLong())
            }
        }
    }

    private class NestedScanLimitException : IOException()

    private const val APK_RESOURCE_TABLE_ENTRY = "resources.arsc"
    private const val ARSC_TABLE_CHUNK_TYPE = 0x0002
    private const val ARSC_TABLE_HEADER_BYTES = 12

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
    private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val GIF87A_SIGNATURE = "GIF87a".toByteArray(StandardCharsets.US_ASCII)
    private val GIF89A_SIGNATURE = "GIF89a".toByteArray(StandardCharsets.US_ASCII)
    private val WEBP_RIFF_SIGNATURE = "RIFF".toByteArray(StandardCharsets.US_ASCII)
    private val WEBP_FORMAT_SIGNATURE = "WEBP".toByteArray(StandardCharsets.US_ASCII)
    private val WINDOWS_DRIVE_PATH = Regex("^[A-Za-z]:")
}

private class ArscFallbackResourceTable(
    private val table: TableBlock,
) : FallbackResourceTable {

    override fun variants(reference: FallbackResourceReference): List<FallbackResourceVariant> {
        val resource = findResource(reference) ?: return emptyList()
        val result = ArrayList<FallbackResourceVariant>()
        val iterator = resource.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next() as? Entry ?: continue
            val value = entry.resValue ?: continue
            val fallbackValue = when (value.valueType) {
                ValueType.STRING -> value.valueAsString?.let(FallbackResourceValue::Text)
                ValueType.REFERENCE, ValueType.DYNAMIC_REFERENCE -> FallbackResourceValue.Reference(
                    FallbackResourceReference(id = value.data.takeIf { it != 0 }),
                )
                else -> null
            } ?: continue
            val config = entry.resConfig
            val locale = config?.locale?.takeIf(String::isNotBlank)
            val density = config?.densityValue ?: 0
            val sdkVersion = config?.sdkVersion ?: 0
            result += FallbackResourceVariant(
                configuration = FallbackResourceConfiguration(
                    locale = locale,
                    density = density,
                    sdkVersion = sdkVersion,
                    hasUnsupportedQualifiers = config?.qualifiers
                        ?.let { qualifiers ->
                            hasUnsupportedArscQualifiers(
                                qualifiers = qualifiers,
                                locale = locale,
                            )
                        }
                        ?: false,
                ),
                value = fallbackValue,
            )
        }
        return result
    }

    private fun findResource(reference: FallbackResourceReference): ResourceEntry? {
        reference.id?.let(table::getResource)?.let { return it }
        val type = reference.type ?: return null
        val name = reference.name ?: return null
        return reference.packageName?.let { packageName ->
            table.getResource(packageName, type, name)
        } ?: table.getLocalResource(type, name)
    }

    private fun hasUnsupportedArscQualifiers(
        qualifiers: String,
        locale: String?,
    ): Boolean {
        val tokens = qualifiers.trim('-').split('-').filter(String::isNotBlank).toMutableList()
        if (tokens.isEmpty()) return false
        if (!locale.isNullOrBlank()) {
            val normalizedLocale = locale.replace('_', '-')
            val localeParts = normalizedLocale.split('-')
            val languageIndex = tokens.indexOfFirst { token ->
                token.equals(localeParts.firstOrNull(), ignoreCase = true) || token.startsWith("b+")
            }
            if (languageIndex >= 0) {
                tokens.removeAt(languageIndex)
                if (languageIndex < tokens.size && tokens[languageIndex].startsWith('r')) {
                    tokens.removeAt(languageIndex)
                }
            }
        }
        return tokens.any { token ->
            !token.matches(Regex("v\\d+")) &&
                !token.matches(Regex("\\d+dpi")) &&
                token !in DENSITY_QUALIFIERS
        }
    }

    private companion object {
        val DENSITY_QUALIFIERS = setOf(
            "ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi",
            "anydpi", "nodpi",
        )
    }
}

private class FallbackResourceResolver(
    private val table: FallbackResourceTable,
    private val device: PackageDeviceSpec,
) {

    private enum class Target {
        TEXT,
        RASTER_PATH,
    }

    fun resolveText(reference: FallbackResourceReference): String? =
        resolve(reference, Target.TEXT, LinkedHashSet(), depth = 0)

    fun resolveRasterPath(reference: FallbackResourceReference): String? =
        resolve(reference, Target.RASTER_PATH, LinkedHashSet(), depth = 0)

    private fun resolve(
        reference: FallbackResourceReference,
        target: Target,
        visited: MutableSet<FallbackResourceReference>,
        depth: Int,
    ): String? {
        if (depth >= MAX_REFERENCE_DEPTH || !visited.add(reference)) return null
        return try {
            val variants = table.variants(reference)
                .mapNotNull { variant ->
                    score(variant.configuration, target)?.let { score -> variant to score }
                }
                .sortedWith(
                    compareByDescending<Pair<FallbackResourceVariant, ResourceScore>> {
                        it.second.supportedQualifierRank
                    }.thenByDescending { it.second.localeRank }
                        .thenByDescending { it.second.densityRank }
                        .thenByDescending { it.second.sdkVersion },
                )
            variants.firstNotNullOfOrNull { (variant, _) ->
                when (val value = variant.value) {
                    is FallbackResourceValue.Reference -> resolve(
                        reference = value.value,
                        target = target,
                        visited = visited,
                        depth = depth + 1,
                    )
                    is FallbackResourceValue.Text -> when (target) {
                        Target.TEXT -> value.value.takeIf(String::isNotBlank)
                        Target.RASTER_PATH -> value.value.takeIf(::isRasterPath)
                    }
                }
            }
        } finally {
            visited.remove(reference)
        }
    }

    private fun score(
        configuration: FallbackResourceConfiguration,
        target: Target,
    ): ResourceScore? {
        if (configuration.sdkVersion > device.sdk) return null
        val localeRank = localeRank(configuration.locale) ?: return null
        val densityRank = when (target) {
            Target.TEXT -> 0
            Target.RASTER_PATH -> densityRank(configuration.density)
        }
        return ResourceScore(
            supportedQualifierRank = if (configuration.hasUnsupportedQualifiers) 0 else 1,
            localeRank = localeRank,
            densityRank = densityRank,
            sdkVersion = configuration.sdkVersion,
        )
    }

    private fun localeRank(resourceLocale: String?): Int? {
        val normalizedResource = normalizeLocale(resourceLocale)
        if (normalizedResource == null) return 1
        val resourceLanguage = normalizedResource.substringBefore('-')
        device.locales.forEachIndexed { index, locale ->
            val normalizedDevice = normalizeLocale(locale) ?: return@forEachIndexed
            val order = (device.locales.size - index).coerceAtLeast(1)
            if (normalizedResource.equals(normalizedDevice, ignoreCase = true)) {
                return 3_000 + order
            }
            if (resourceLanguage.equals(normalizedDevice.substringBefore('-'), ignoreCase = true)) {
                return 2_000 + order
            }
        }
        return null
    }

    private fun normalizeLocale(locale: String?): String? = locale
        ?.trim()
        ?.replace('_', '-')
        ?.replace("-r", "-", ignoreCase = true)
        ?.takeIf(String::isNotBlank)
        ?.let { value ->
            runCatching { Locale.forLanguageTag(value).toLanguageTag() }
                .getOrDefault(value)
                .takeUnless { it.equals("und", ignoreCase = true) }
        }

    private fun densityRank(density: Int): Int = when (density) {
        DENSITY_ANY -> 300_000
        DENSITY_NONE -> 290_000
        0 -> 1
        else -> {
            val distance = abs(density.toLong() - device.densityDpi.toLong())
                .coerceAtMost(199_999L)
                .toInt()
            200_000 - distance + if (density >= device.densityDpi) 1 else 0
        }
    }

    private data class ResourceScore(
        val supportedQualifierRank: Int,
        val localeRank: Int,
        val densityRank: Int,
        val sdkVersion: Int,
    )

    private companion object {
        const val MAX_REFERENCE_DEPTH = 16
        const val DENSITY_ANY = 0xFFFE
        const val DENSITY_NONE = 0xFFFF
    }
}

private fun isRasterPath(path: String): Boolean {
    val normalized = path.substringBefore('?').lowercase(Locale.ROOT)
    return RASTER_EXTENSIONS.any(normalized::endsWith)
}

private val RASTER_EXTENSIONS = setOf(".png", ".webp", ".jpg", ".jpeg", ".gif")
private const val MAX_RESOURCE_TOKEN_CHARS = 256
