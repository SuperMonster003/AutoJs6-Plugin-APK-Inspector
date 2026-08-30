package io.github.supermonster003.autojs6.plugin.apkinspector

internal data class CopyableReportField(
    val displayText: String,
    val copyValue: String,
)

internal data class InspectionReportTextSection(
    val title: CharSequence,
    val body: CharSequence,
)

internal object InspectionReportTextFormatter {

    fun format(
        appLabel: CharSequence,
        fileSummary: CharSequence,
        sections: List<InspectionReportTextSection>,
    ): String = buildList {
        add("$appLabel\n$fileSummary")
        sections.forEach { section ->
            add("${section.title}\n${section.body}")
        }
    }.joinToString("\n\n")
}
