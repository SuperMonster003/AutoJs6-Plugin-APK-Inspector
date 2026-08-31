package io.github.supermonster003.autojs6.plugin.apkinspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files

class ReadOnlyTextSnapshotTest {

    @Test
    fun replacesAnExistingReadOnlySnapshotAcrossActivityRecreation() {
        val directory = Files.createTempDirectory("apk-inspector-text-snapshot").toFile()
        val target = directory.resolve("manifest.xml")
        try {
            ReadOnlyTextSnapshot.replace(target, "<manifest first=\"true\"/>")
            assertEquals("<manifest first=\"true\"/>", target.readText(Charsets.UTF_8))
            assertFalse(target.canWrite())

            ReadOnlyTextSnapshot.replace(target, "<manifest second=\"true\"/>")

            assertEquals("<manifest second=\"true\"/>", target.readText(Charsets.UTF_8))
            assertFalse(target.canWrite())
        } finally {
            target.setWritable(true, true)
            directory.deleteRecursively()
        }
    }
}
