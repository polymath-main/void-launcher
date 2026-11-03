package com.polymath.voidlauncher.core.js

import java.io.File

/**
 * JS file + shell sandbox rules (impl-04 step 7, plan gates S3/S6).
 *
 * Files: every path must canonically stay under the workspace
 * root (the Void files home passed in by the Activity). `..`
 * escapes and absolute paths outside the root are denied, and
 * reads are capped at 64 KiB, exactly as in the prototype.
 *
 * Shell: the prototype's first-word allowlist is the first pass
 * only, kept verbatim so prototype macros behave the same. It is
 * NOT a full Android sandbox design — that design must be written
 * and reviewed before the allowlist is ever dropped. No chaining
 * metacharacters, no `..`, no `/etc/`.
 */
object JsSandbox {
    const val MAX_READ_BYTES: Int = 64 * 1024

    val SHELL_ALLOWLIST: Set<String> = setOf(
        "echo", "ls", "pwd", "date", "uname", "whoami", "wc", "cat"
    )

    /** Null if the path is safe, otherwise the denial reason. */
    fun resolveFile(workspaceRoot: String, path: String): File? {
        return try {
            val root = File(workspaceRoot).canonicalFile
            val file = if (File(path).isAbsolute) File(path) else File(root, path)
            val canonical = file.canonicalFile
            if (canonical == root || canonical.path.startsWith(root.path + File.separator)) {
                canonical
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Null if the shell command passes the prototype allowlist. */
    fun checkShell(command: String): String? {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return "empty shell command"
        val first = trimmed.split(Regex("\\s+")).first()
        if (first !in SHELL_ALLOWLIST) return "shell command not allowlisted: $first"
        if (trimmed.contains("..")) return "shell command contains '..'"
        if (trimmed.contains("/etc/")) return "shell command references /etc/"
        for (bad in listOf(";", "&", "|", "`", "$(", "<", ">")) {
            if (trimmed.contains(bad)) return "shell command contains '$bad'"
        }
        return null
    }
}
