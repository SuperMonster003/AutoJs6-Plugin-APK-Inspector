package io.github.supermonster003.autojs6.plugin.apkinspector

import java.io.File
import java.io.IOException

/** Replaces an app-private text snapshot while leaving the published file read-only. */
internal object ReadOnlyTextSnapshot {

    fun replace(target: File, content: String): File {
        val parent = target.parentFile
        if (parent == null || !parent.isDirectory) {
            throw IOException("Snapshot parent directory is unavailable")
        }
        if (target.exists()) {
            if (!target.setWritable(true, true)) {
                throw IOException("Unable to replace the previous text snapshot")
            }
            if (!target.delete()) {
                target.setReadOnly()
                throw IOException("Unable to replace the previous text snapshot")
            }
        }

        try {
            target.writeText(content, Charsets.UTF_8)
            if (!target.setReadOnly()) {
                throw IOException("Unable to protect the text snapshot")
            }
            return target
        } catch (error: Throwable) {
            runCatching {
                target.setWritable(true, true)
                target.delete()
            }
            throw error
        }
    }
}
