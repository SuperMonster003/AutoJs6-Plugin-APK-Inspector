package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Test

class InspectionReportTextFormatterTest {

    @Test
    fun `formats every visible report block in screen order`() {
        val text = InspectionReportTextFormatter.format(
            appLabel = "Example App",
            fileSummary = "Size: 12 MB\nMIME: application/vnd.android.package-archive",
            sections = listOf(
                InspectionReportTextSection(
                    title = "Package details",
                    body = "Package: com.example\nVersion: 1.2 (42)\nSHA-256: abc123",
                ),
                InspectionReportTextSection(
                    title = "Components",
                    body = "APK entries: 1\n- base",
                ),
                InspectionReportTextSection(
                    title = "Requested permissions",
                    body = "Runtime / dangerous (1)\n- android.permission.CAMERA",
                ),
                InspectionReportTextSection(
                    title = "Security and compatibility findings",
                    body = "No problems detected",
                ),
            ),
        )

        assertEquals(
            """
                Example App
                Size: 12 MB
                MIME: application/vnd.android.package-archive

                Package details
                Package: com.example
                Version: 1.2 (42)
                SHA-256: abc123

                Components
                APK entries: 1
                - base

                Requested permissions
                Runtime / dangerous (1)
                - android.permission.CAMERA

                Security and compatibility findings
                No problems detected
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `preserves localized text whitespace and paragraph breaks verbatim`() {
        val text = InspectionReportTextFormatter.format(
            appLabel = "示例应用",
            fileSummary = "大小: 1 MB\nMIME: text/plain",
            sections = listOf(
                InspectionReportTextSection(
                    title = "软件包详情",
                    body = "签名方案: V2 — 验证通过\nV3 — 验证通过\n\n证书 SHA-256: 00:11",
                ),
            ),
        )

        assertEquals(
            "示例应用\n大小: 1 MB\nMIME: text/plain\n\n" +
                "软件包详情\n签名方案: V2 — 验证通过\nV3 — 验证通过\n\n" +
                "证书 SHA-256: 00:11",
            text,
        )
    }
}
