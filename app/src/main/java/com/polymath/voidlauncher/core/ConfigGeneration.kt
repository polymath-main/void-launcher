package com.polymath.voidlauncher.core

import java.io.File

/**
 * Area 07 step 5 — lightweight generation stamp for the
 * single-writer race (plan §3.1 risk 5, §4 step 5).
 *
 * Pure Kotlin (java.io only) so it runs in the JVM harness. The
 * stamp is a file's mtime + length — NOT a schema field and NOT a
 * sidecar file, so no schema change and no new store file. It is
 * checked before a Settings save: if the store changed since
 * Settings loaded it, Settings warns instead of silently
 * overwriting (overwrite-or-reload dialog on explicit SAVE,
 * skip-with-toast on leave). A missing store file counts as
 * changed (deletion is a change, not "no change").
 */
data class ConfigSnapshot(val lastModified: Long, val length: Long)

object ConfigGeneration {
    fun snapshot(file: File): ConfigSnapshot? =
        if (file.isFile) ConfigSnapshot(file.lastModified(), file.length()) else null

    fun changed(snapshot: ConfigSnapshot?, file: File): Boolean {
        val now = snapshot(file) ?: return true
        if (snapshot == null) return true
        return now != snapshot
    }
}
