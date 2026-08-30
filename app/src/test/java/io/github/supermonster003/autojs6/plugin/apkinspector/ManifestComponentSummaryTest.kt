package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestComponentSummaryTest {

    @Test
    fun componentKindsAndExplicitExportedStatesAreCounted() {
        val summary = ManifestComponentSummaryParser.parse(
            manifest(
                """
                <activity android:name=".MainActivity" android:exported="true" />
                <activity-alias android:name=".Alias" android:exported='false' />
                <service android:name=".SyncService" a:exported="1" />
                <receiver android:name=".BootReceiver" android:exported="0" />
                <receiver android:name=".ImplicitReceiver" />
                <provider android:name=".Files" tools:exported="true" />
                <!-- <activity android:name=".Commented" android:exported="true" /> -->
                <![CDATA[<service android:name=".TextOnly" android:exported="true" />]]>
                """.trimIndent(),
            ),
        )

        assertEquals(2, summary.activities.total)
        assertEquals(1, summary.activities.exported)
        assertEquals(1, summary.activities.notExported)
        assertEquals(1, summary.services.total)
        assertEquals(1, summary.services.exported)
        assertEquals(2, summary.receivers.total)
        assertEquals(1, summary.receivers.notExported)
        assertEquals(1, summary.receivers.exportedUnspecified)
        assertEquals(1, summary.providers.total)
        assertEquals(1, summary.providers.exportedUnspecified)
        assertEquals(6, summary.total)
        assertTrue(summary.hasUnspecifiedExported)
        assertFalse(summary.scanLimitReached)
    }

    @Test
    fun componentLikeTagsOutsideApplicationAreIgnored() {
        val summary = ManifestComponentSummaryParser.parse(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <activity android:name=".Invalid" android:exported="true" />
                <application android:label="Demo" />
                <service android:name=".AlsoInvalid" android:exported="true" />
            </manifest>
            """.trimIndent(),
        )

        assertEquals(0, summary.total)
        assertEquals(0, summary.failedManifestCount)
    }

    @Test
    fun malformedApplicationBodyOnlyFailsComponentSummary() {
        val summary = ManifestComponentSummaryParser.parse(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".Main" android:exported="true" />
            </manifest>
            """.trimIndent(),
        )

        assertEquals(0, summary.total)
        assertEquals(1, summary.failedManifestCount)
    }

    @Test
    fun perManifestComponentScanIsBounded() {
        val components = buildString {
            repeat(ManifestComponentSummaryParser.MAX_COMPONENTS_PER_MANIFEST + 1) { index ->
                append("<service android:name=\".Service$index\" android:exported=\"false\" />")
            }
        }

        val summary = ManifestComponentSummaryParser.parse(manifest(components))

        assertEquals(ManifestComponentSummaryParser.MAX_COMPONENTS_PER_MANIFEST, summary.total)
        assertEquals(
            ManifestComponentSummaryParser.MAX_COMPONENTS_PER_MANIFEST,
            summary.services.notExported,
        )
        assertTrue(summary.scanLimitReached)
    }

    @Test
    fun summariesAggregateCountsAndPartialStates() {
        val first = ManifestComponentSummaryParser.parse(
            manifest("<activity android:exported=\"true\" />"),
        )
        val second = ManifestComponentSummaryParser.parse(
            manifest("<provider android:exported=\"false\" />"),
        ).withManifestProblems(failedCount = 2, omittedCount = 3)

        val aggregate = ManifestComponentSummary.aggregate(listOf(first, second))

        assertEquals(2, aggregate.total)
        assertEquals(1, aggregate.activities.exported)
        assertEquals(1, aggregate.providers.notExported)
        assertEquals(2, aggregate.failedManifestCount)
        assertEquals(3, aggregate.omittedManifestCount)
    }

    private fun manifest(applicationBody: String): String =
        """
        <manifest
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:a="http://schemas.android.com/apk/res/android"
            xmlns:tools="http://schemas.android.com/tools">
            <application>
                $applicationBody
            </application>
        </manifest>
        """.trimIndent()
}
