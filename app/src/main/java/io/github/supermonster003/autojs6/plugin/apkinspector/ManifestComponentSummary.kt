package io.github.supermonster003.autojs6.plugin.apkinspector

import java.util.Locale

internal enum class ManifestComponentKind {
    ACTIVITY,
    SERVICE,
    RECEIVER,
    PROVIDER,
}

internal data class ManifestComponentCounts(
    val total: Int = 0,
    val exported: Int = 0,
    val notExported: Int = 0,
    val exportedUnspecified: Int = 0,
)

internal data class ManifestComponentSummary(
    val activities: ManifestComponentCounts = ManifestComponentCounts(),
    val services: ManifestComponentCounts = ManifestComponentCounts(),
    val receivers: ManifestComponentCounts = ManifestComponentCounts(),
    val providers: ManifestComponentCounts = ManifestComponentCounts(),
    val scanLimitReached: Boolean = false,
    val failedManifestCount: Int = 0,
    val omittedManifestCount: Int = 0,
) {
    val total: Int
        get() = saturatingAdd(
            saturatingAdd(activities.total, services.total),
            saturatingAdd(receivers.total, providers.total),
        )

    val hasUnspecifiedExported: Boolean
        get() = ManifestComponentKind.entries.any { kind ->
            countsFor(kind).exportedUnspecified > 0
        }

    fun countsFor(kind: ManifestComponentKind): ManifestComponentCounts = when (kind) {
        ManifestComponentKind.ACTIVITY -> activities
        ManifestComponentKind.SERVICE -> services
        ManifestComponentKind.RECEIVER -> receivers
        ManifestComponentKind.PROVIDER -> providers
    }

    fun withManifestProblems(
        failedCount: Int = 0,
        omittedCount: Int = 0,
    ): ManifestComponentSummary = copy(
        failedManifestCount = saturatingAdd(failedManifestCount, failedCount.coerceAtLeast(0)),
        omittedManifestCount = saturatingAdd(omittedManifestCount, omittedCount.coerceAtLeast(0)),
    )

    operator fun plus(other: ManifestComponentSummary): ManifestComponentSummary =
        ManifestComponentSummary(
            activities = activities + other.activities,
            services = services + other.services,
            receivers = receivers + other.receivers,
            providers = providers + other.providers,
            scanLimitReached = scanLimitReached || other.scanLimitReached,
            failedManifestCount = saturatingAdd(failedManifestCount, other.failedManifestCount),
            omittedManifestCount = saturatingAdd(omittedManifestCount, other.omittedManifestCount),
        )

    companion object {
        fun aggregate(summaries: Iterable<ManifestComponentSummary>): ManifestComponentSummary =
            summaries.fold(ManifestComponentSummary()) { aggregate, summary -> aggregate + summary }
    }
}

internal object ManifestComponentSummaryParser {

    const val MAX_COMPONENTS_PER_MANIFEST = 4_096

    fun parse(xml: String): ManifestComponentSummary {
        val content = NON_ELEMENT_CONTENT.replace(xml, "")
        val androidNamespacePrefixes = ANDROID_NAMESPACE_DECLARATION.findAll(content)
            .map { match -> match.groupValues[1] }
            .toSet()
        val applicationBody = APPLICATION_BODY.find(content)?.groupValues?.get(1)
        if (applicationBody == null) {
            val applicationTag = APPLICATION_START.find(content)?.value
            return if (applicationTag != null && !applicationTag.trimEnd().endsWith("/>")) {
                ManifestComponentSummary(failedManifestCount = 1)
            } else {
                ManifestComponentSummary()
            }
        }

        val counts = Array(ManifestComponentKind.entries.size) { IntArray(EXPORTED_STATE_COUNT) }
        var scanned = 0
        var scanLimitReached = false
        val matches = COMPONENT_TAG.findAll(applicationBody).iterator()
        while (matches.hasNext()) {
            if (scanned >= MAX_COMPONENTS_PER_MANIFEST) {
                scanLimitReached = true
                break
            }
            val match = matches.next()
            val kind = when (match.groupValues[1].lowercase(Locale.ROOT)) {
                "activity", "activity-alias" -> ManifestComponentKind.ACTIVITY
                "service" -> ManifestComponentKind.SERVICE
                "receiver" -> ManifestComponentKind.RECEIVER
                "provider" -> ManifestComponentKind.PROVIDER
                else -> continue
            }
            val attributes = match.groupValues[2]
            val exported = EXPORTED_ATTRIBUTE.findAll(attributes)
                .firstOrNull { attribute ->
                    attribute.groupValues[1] in androidNamespacePrefixes
                }
                ?.let { attribute ->
                    attribute.groupValues[2].ifEmpty { attribute.groupValues[3] }
                }
                ?.trim()
            val state = when {
                exported.isTrueValue() -> EXPORTED
                exported.isFalseValue() -> NOT_EXPORTED
                else -> EXPORTED_UNSPECIFIED
            }
            counts[kind.ordinal][state] = saturatingAdd(counts[kind.ordinal][state], 1)
            scanned += 1
        }

        fun countsFor(kind: ManifestComponentKind): ManifestComponentCounts {
            val values = counts[kind.ordinal]
            return ManifestComponentCounts(
                total = saturatingAdd(
                    saturatingAdd(values[EXPORTED], values[NOT_EXPORTED]),
                    values[EXPORTED_UNSPECIFIED],
                ),
                exported = values[EXPORTED],
                notExported = values[NOT_EXPORTED],
                exportedUnspecified = values[EXPORTED_UNSPECIFIED],
            )
        }

        return ManifestComponentSummary(
            activities = countsFor(ManifestComponentKind.ACTIVITY),
            services = countsFor(ManifestComponentKind.SERVICE),
            receivers = countsFor(ManifestComponentKind.RECEIVER),
            providers = countsFor(ManifestComponentKind.PROVIDER),
            scanLimitReached = scanLimitReached,
        )
    }

    private fun String?.isTrueValue(): Boolean =
        this.equals("true", ignoreCase = true) || this == "1" ||
            this.equals("0xffffffff", ignoreCase = true)

    private fun String?.isFalseValue(): Boolean =
        this.equals("false", ignoreCase = true) || this == "0"

    private const val EXPORTED = 0
    private const val NOT_EXPORTED = 1
    private const val EXPORTED_UNSPECIFIED = 2
    private const val EXPORTED_STATE_COUNT = 3

    private val NON_ELEMENT_CONTENT = Regex(
        """<!--.*?-->|<!\[CDATA\[.*?\]\]>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val APPLICATION_START = Regex(
        """<application(?=\s|/?>)[^>]*?/?>""",
        RegexOption.IGNORE_CASE,
    )
    private val APPLICATION_BODY = Regex(
        """<application(?=\s|>)[^>]*>(.*?)</application\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val COMPONENT_TAG = Regex(
        """<(activity(?:-alias)?|service|receiver|provider)(?=\s|/?>)([^>]*)/?>""",
        RegexOption.IGNORE_CASE,
    )
    private val ANDROID_NAMESPACE_DECLARATION = Regex(
        """(?:^|\s)xmlns:([A-Za-z_][A-Za-z0-9_.-]*)\s*=\s*(?:"http://schemas.android.com/apk/res/android"|'http://schemas.android.com/apk/res/android')""",
    )
    private val EXPORTED_ATTRIBUTE = Regex(
        """(?:^|\s)(?:([A-Za-z_][A-Za-z0-9_.-]*):)?exported\s*=\s*(?:"([^"]*)"|'([^']*)')""",
        RegexOption.IGNORE_CASE,
    )
}

private operator fun ManifestComponentCounts.plus(
    other: ManifestComponentCounts,
): ManifestComponentCounts = ManifestComponentCounts(
    total = saturatingAdd(total, other.total),
    exported = saturatingAdd(exported, other.exported),
    notExported = saturatingAdd(notExported, other.notExported),
    exportedUnspecified = saturatingAdd(exportedUnspecified, other.exportedUnspecified),
)

private fun saturatingAdd(left: Int, right: Int): Int =
    (left.toLong() + right.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
