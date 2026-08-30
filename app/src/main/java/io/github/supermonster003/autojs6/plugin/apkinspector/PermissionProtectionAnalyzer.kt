package io.github.supermonster003.autojs6.plugin.apkinspector

internal enum class PermissionProtectionGroup {
    RUNTIME,
    SIGNATURE,
    NORMAL,
}

internal data class PermissionDefinition(
    val protectionLevel: Int,
    val description: String? = null,
)

internal data class RequestedPermissionAssessment(
    val name: String,
    val group: PermissionProtectionGroup,
    val protectionLevelResolved: Boolean,
    val description: String?,
)

internal data class RequestedPermissionAnalysis(
    val permissions: List<RequestedPermissionAssessment>,
    val omittedCount: Int,
) {
    fun permissionsIn(group: PermissionProtectionGroup): List<RequestedPermissionAssessment> =
        permissions.filter { permission -> permission.group == group }
}

internal object PermissionProtectionAnalyzer {

    const val MAX_DISPLAYED_PERMISSIONS = 512
    const val MAX_DESCRIPTION_CHARS = 240

    private const val MAX_SCANNED_PERMISSIONS = 2_048
    private const val MAX_PERMISSION_NAME_CHARS = 512
    private const val MAX_DESCRIPTION_SOURCE_CHARS = 4_096
    private const val PROTECTION_MASK_BASE = 0x0F
    private const val PROTECTION_NORMAL = 0x00
    private const val PROTECTION_DANGEROUS = 0x01
    private const val PROTECTION_SIGNATURE = 0x02
    private const val PROTECTION_SIGNATURE_OR_SYSTEM = 0x03
    private const val PROTECTION_INTERNAL = 0x04

    fun analyze(
        requestedPermissions: Collection<String>,
        declaredProtectionLevels: Map<String, Int> = emptyMap(),
        resolvePermission: (String) -> PermissionDefinition? = { null },
    ): RequestedPermissionAnalysis {
        val scannedPermissions = requestedPermissions.asSequence()
            .take(MAX_SCANNED_PERMISSIONS)
            .toList()
        val omittedBeyondScanLimit =
            (requestedPermissions.size - MAX_SCANNED_PERMISSIONS).coerceAtLeast(0)
        var rejectedCount = 0
        val names = buildSet {
            scannedPermissions.forEach { rawName ->
                normalizeName(rawName)?.let(::add) ?: run {
                    if (rawName.isNotBlank()) rejectedCount += 1
                }
            }
        }.sorted()
        val displayedNames = names.take(MAX_DISPLAYED_PERMISSIONS)
        val omittedCount = omittedBeyondScanLimit + rejectedCount +
            (names.size - displayedNames.size).coerceAtLeast(0)

        val assessments = displayedNames.map { name ->
            val declaredProtectionLevel = declaredProtectionLevels[name]
            val definition = declaredProtectionLevel?.let(::PermissionDefinition)
                ?: runCatching { resolvePermission(name) }.getOrNull()
            RequestedPermissionAssessment(
                name = name,
                group = classify(definition?.protectionLevel),
                protectionLevelResolved = definition != null,
                description = normalizeDescription(definition?.description),
            )
        }.sortedWith(
            compareBy<RequestedPermissionAssessment> { permission -> permission.group.ordinal }
                .thenBy { permission -> permission.name },
        )

        return RequestedPermissionAnalysis(
            permissions = assessments,
            omittedCount = omittedCount,
        )
    }

    internal fun classify(protectionLevel: Int?): PermissionProtectionGroup {
        val baseProtection = protectionLevel?.and(PROTECTION_MASK_BASE)
            ?: return PermissionProtectionGroup.NORMAL
        return when (baseProtection) {
            PROTECTION_DANGEROUS -> PermissionProtectionGroup.RUNTIME
            PROTECTION_NORMAL -> PermissionProtectionGroup.NORMAL
            PROTECTION_SIGNATURE,
            PROTECTION_SIGNATURE_OR_SYSTEM,
            PROTECTION_INTERNAL,
            -> PermissionProtectionGroup.SIGNATURE

            else -> PermissionProtectionGroup.SIGNATURE
        }
    }

    internal fun normalizeDescription(description: String?): String? {
        val collapsed = description
            ?.take(MAX_DESCRIPTION_SOURCE_CHARS)
            ?.map { character -> if (character.isISOControl()) ' ' else character }
            ?.joinToString("")
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        if (collapsed.length <= MAX_DESCRIPTION_CHARS) return collapsed
        return collapsed.take(MAX_DESCRIPTION_CHARS - 1).trimEnd() + '…'
    }

    private fun normalizeName(name: String): String? {
        val normalized = name.trim()
        return normalized.takeIf {
            it.isNotEmpty() &&
                it.length <= MAX_PERMISSION_NAME_CHARS &&
                it.none { character ->
                    character.isISOControl() ||
                        Character.getType(character) == Character.FORMAT.toInt()
                }
        }
    }

    private val WHITESPACE = Regex("\\s+")
}
