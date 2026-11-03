package com.polymath.voidlauncher.core

import org.json.JSONObject
import java.io.File

/**
 * Command audit log (impl-04 step 8).
 *
 * Append-only JSON-lines file under Void home (one object per
 * line: timestamp, surface, input, outcome, durationMs,
 * exitCode). Input is REDACTED BEFORE WRITING (plan gate S4):
 * token/password/secret/api-key assignments and Bearer tokens
 * are replaced, and shell output is never written at all — only
 * the command text, outcome, duration, and exit code. A row is
 * therefore safe to export, unlike raw history.
 *
 * Bounded like history: appending past [maxEntries] rewrites the
 * file keeping the newest entries. Surfaces covered in this step
 * are shell (:run/:sh/:!), :eval, string-macro play, JS macro
 * runs, and unknown commands — other commands (mode, theme,
 * settings, ...) are NOT audited yet; that is a stated scope
 * limit, not an oversight to hide. Pure java.io + org.json, so
 * the JVM harness can test it with the JSON stubs.
 */
class CommandAudit(private val file: File, private val maxEntries: Int = 500) {

    data class Entry(
        val timestampMs: Long,
        val surface: String,
        val input: String,
        val outcome: String,
        val durationMs: Long,
        val exitCode: Int?
    )

    companion object {
        private val SECRET_ASSIGN = Regex(
            "(?i)(token|password|passwd|secret|api[_-]?key)\\s*[:=]\\s*\\S+"
        )
        private val BEARER = Regex("(?i)Bearer\\s+\\S+")

        /** Redacts secrets from a command string before storage. */
        fun redact(input: String): String {
            var out = SECRET_ASSIGN.replace(input) { m ->
                val key = m.value.substringBefore('=').substringBefore(':')
                "$key=***"
            }
            out = BEARER.replace(out, "Bearer ***")
            return out
        }
    }

    fun record(
        surface: String,
        input: String,
        outcome: String,
        durationMs: Long = 0,
        exitCode: Int? = null
    ) {
        try {
            file.parentFile?.mkdirs()
            val json = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("surface", surface)
                put("input", redact(input))
                put("outcome", outcome)
                put("duration_ms", durationMs)
                put("exit_code", exitCode ?: -1)
            }
            file.appendText(json.toString() + "\n")
            pruneIfNeeded()
        } catch (_: Exception) {
            // Audit must never break command dispatch.
        }
    }

    private fun pruneIfNeeded() {
        try {
            val lines = file.readLines()
            if (lines.size > maxEntries) {
                file.writeText(lines.takeLast(maxEntries).joinToString("\n") + "\n")
            }
        } catch (_: Exception) {
        }
    }

    /** Newest first, at most [limit] entries. */
    fun recent(limit: Int = 20): List<Entry> {
        if (!file.exists()) return emptyList()
        return try {
            file.readLines().asReversed().take(limit).mapNotNull { line ->
                try {
                    val json = JSONObject(line)
                    Entry(
                        timestampMs = json.optLong("timestamp", 0),
                        surface = json.optString("surface", ""),
                        input = json.optString("input", ""),
                        outcome = json.optString("outcome", ""),
                        durationMs = json.optLong("duration_ms", 0),
                        exitCode = json.optInt("exit_code", -1).takeIf { it >= 0 }
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
