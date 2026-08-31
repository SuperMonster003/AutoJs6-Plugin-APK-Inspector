package io.github.supermonster003.autojs6.plugin.apkinspector

internal enum class PackageStagingRejection {
    DECLARED_MIME,
    RESOLVER_MIME,
    MIME_MISMATCH,
    DECLARED_DISPLAY_NAME,
    QUERIED_DISPLAY_NAME,
    DISPLAY_NAME_MISMATCH,
    DISPLAY_NAME_MISSING,
    EXTERNAL_EXTENSION_MIME,
    DECLARED_SIZE,
    QUERIED_SIZE,
    DESCRIPTOR_SIZE,
    QUERY_SIZE_MISMATCH,
    DESCRIPTOR_SIZE_MISMATCH,
    STORAGE_LIMIT,
    CACHE_DIRECTORY,
    SESSION_DIRECTORY,
    CONTENT_UNAVAILABLE,
    COPY_LIMIT,
    COPIED_SIZE_MISMATCH,
    SNAPSHOT_READ_ONLY,
    IDSIG_DECLARED_SIZE,
    IDSIG_COPY_LIMIT,
    IDSIG_READ_ONLY,
}

internal data class PackageStagingFacts(
    val declaredMime: String,
    val resolverMime: String?,
    val fromExplorer: Boolean,
    val declaredDisplayName: String?,
    val queriedDisplayName: String?,
    val fallbackDisplayName: String?,
    val declaredSize: Long?,
    val queriedSize: Long?,
    val descriptorSize: Long?,
)

internal sealed interface PackageStagingEvaluation {
    data class Accepted(
        val displayName: String,
        val expectedSize: Long?,
        val mimeType: String,
    ) : PackageStagingEvaluation

    data class Rejected(
        val reason: PackageStagingRejection,
    ) : PackageStagingEvaluation
}

internal class PackageStagingException(
    val rejection: PackageStagingRejection,
    message: String,
) : java.io.IOException(message)

internal object PackageStagingValidator {

    fun evaluate(facts: PackageStagingFacts): PackageStagingEvaluation {
        val declaredMime = PackageRequestPolicy.normalizeMimeType(facts.declaredMime)
            ?: return rejected(PackageStagingRejection.DECLARED_MIME)
        val resolverMime = facts.resolverMime?.let { raw ->
            PackageRequestPolicy.normalizeMimeType(raw)
                ?: return rejected(PackageStagingRejection.RESOLVER_MIME)
        }
        if (
            resolverMime != null &&
            !PackageRequestPolicy.mimeTypesAreCompatible(declaredMime, resolverMime, facts.fromExplorer)
        ) {
            return rejected(PackageStagingRejection.MIME_MISMATCH)
        }

        val queriedName = facts.queriedDisplayName?.let { raw ->
            PackageRequestPolicy.validateDisplayName(raw)
                ?: return rejected(PackageStagingRejection.QUERIED_DISPLAY_NAME)
        }
        val displayName = if (facts.fromExplorer) {
            val declared = PackageRequestPolicy.validateDisplayName(facts.declaredDisplayName)
                ?: return rejected(PackageStagingRejection.DECLARED_DISPLAY_NAME)
            if (queriedName != null && queriedName != declared) {
                return rejected(PackageStagingRejection.DISPLAY_NAME_MISMATCH)
            }
            declared
        } else {
            queriedName ?: PackageRequestPolicy.validateDisplayName(facts.fallbackDisplayName)
            ?: return rejected(PackageStagingRejection.DISPLAY_NAME_MISSING)
        }
        if (
            !facts.fromExplorer &&
            !PackageRequestPolicy.isExternalMimeCompatible(displayName, declaredMime)
        ) {
            return rejected(PackageStagingRejection.EXTERNAL_EXTENSION_MIME)
        }

        val declaredSize = facts.declaredSize?.also { size ->
            if (!PackageRequestPolicy.isDeclaredSizeAccepted(size)) {
                return rejected(PackageStagingRejection.DECLARED_SIZE)
            }
        }
        val queriedSize = facts.queriedSize?.also { size ->
            if (!PackageRequestPolicy.isDeclaredSizeAccepted(size)) {
                return rejected(PackageStagingRejection.QUERIED_SIZE)
            }
        }
        val descriptorSize = facts.descriptorSize?.also { size ->
            if (!PackageRequestPolicy.isDeclaredSizeAccepted(size)) {
                return rejected(PackageStagingRejection.DESCRIPTOR_SIZE)
            }
        }
        if (facts.fromExplorer && declaredSize != null) {
            if (queriedSize != null && queriedSize != declaredSize) {
                return rejected(PackageStagingRejection.QUERY_SIZE_MISMATCH)
            }
            if (descriptorSize != null && descriptorSize != declaredSize) {
                return rejected(PackageStagingRejection.DESCRIPTOR_SIZE_MISMATCH)
            }
        }
        return PackageStagingEvaluation.Accepted(
            displayName = displayName,
            expectedSize = declaredSize ?: queriedSize ?: descriptorSize,
            mimeType = resolverMime ?: declaredMime,
        )
    }

    fun rejectStorageLimit(expectedSize: Long?, copyLimit: Long): PackageStagingRejection? =
        PackageStagingRejection.STORAGE_LIMIT.takeIf {
            expectedSize != null && expectedSize > copyLimit.coerceAtLeast(0L)
        }

    private fun rejected(reason: PackageStagingRejection): PackageStagingEvaluation =
        PackageStagingEvaluation.Rejected(reason)
}
