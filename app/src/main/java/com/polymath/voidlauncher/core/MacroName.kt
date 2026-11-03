package com.polymath.voidlauncher.core

import java.io.File

/**
 * Macro file-name safety (impl-04 step 1, plan gap G-S1).
 *
 * The old MacroRecorder did `File(dir, "$name.json")` with no
 * validation, so a typed name like `../../x` wrote outside the macros
 * directory. Every macro file access (start/stop/load/play/delete)
 * must go through here. Pure Kotlin, JVM-testable.
 */
object MacroName {

    /** Max macro file size accepted on load (plan gate S3). */
    const val MAX_FILE_BYTES: Long = 256L * 1024L

    /** Max nested `:play` depth (plan gap G-S2). 3 = a macro may play a
     * macro that plays a macro, but no deeper; a name already playing
     * at any depth is refused, so A→B→A is refused too. */
    const val MAX_PLAY_DEPTH: Int = 3

    /** Null if the name is safe, otherwise the rejection reason. */
    fun validate(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "macro name is empty"
        if (trimmed.contains('\u0000')) return "macro name contains NUL"
        if (trimmed.contains('/') || trimmed.contains('\\')) {
            return "macro name must not contain path separators"
        }
        if (trimmed == "." || trimmed == "..") return "macro name must not be '.' or '..'"
        return null
    }

    fun isValid(name: String): Boolean = validate(name) == null

    /**
     * Resolves `<dir>/<name>.json` and returns null unless the
     * canonical path stays under the canonical macros directory
     * (defence in depth: even a caller that skipped [validate] cannot
     * escape via a symlink or a crafted directory string).
     */
    fun resolveFile(macrosDir: String, name: String): File? {
        if (validate(name) != null) return null
        return try {
            val dir = File(macrosDir).canonicalFile
            val file = File(dir, "$name.json").canonicalFile
            if (file.parentFile != dir && !file.path.startsWith(dir.path + File.separator)) {
                null
            } else {
                file
            }
        } catch (_: Exception) {
            null
        }
    }
}
