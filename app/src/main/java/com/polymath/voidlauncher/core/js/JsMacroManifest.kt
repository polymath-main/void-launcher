package com.polymath.voidlauncher.core.js

/**
 * JS macro manifest (impl-04 step 7, plan §5/S5).
 *
 * First line of a `.js` macro, exactly as in the prototype:
 * a void-macro comment holding JSON with name, permissions and
 * timeout_ms (e.g. name morning, timeout 5000). The literal
 * comment delimiters are intentionally not reproduced here:
 * a nested opener inside this KDoc would swallow the file.
 *
 * Parsing is hand-rolled (no JSON library) so it is JVM-testable
 * and independent of the Android JSON stubs. A missing or invalid
 * manifest means the prototype defaults: notify, telemetry,
 * storage, mode only — NO files, shell, or apps. Permissions are
 * host-enforced by [PermissionCheckingAdapters], never by
 * convention. `Void.*` (capital V) is the only JS-facing name:
 * lowercase `void` is a reserved JS operator and is a syntax
 * error in a macro.
 */
data class JsMacroManifest(
    val name: String,
    val permissions: Set<Permission>,
    val timeoutMs: Long
) {
    enum class Permission {
        NOTIFY, TELEMETRY, STORAGE, MODE, FILES, SHELL, APPS;

        companion object {
            fun fromString(raw: String): Permission? = when (raw.trim().lowercase()) {
                "notify" -> NOTIFY
                "telemetry" -> TELEMETRY
                "storage" -> STORAGE
                "mode" -> MODE
                "files" -> FILES
                "shell" -> SHELL
                "apps" -> APPS
                else -> null
            }
        }
    }

    companion object {
        val DEFAULT_PERMISSIONS: Set<Permission> = setOf(
            Permission.NOTIFY, Permission.TELEMETRY, Permission.STORAGE, Permission.MODE
        )
        const val DEFAULT_TIMEOUT_MS: Long = 5000L
        const val MAX_TIMEOUT_MS: Long = 60000L

        fun defaults(name: String): JsMacroManifest =
            JsMacroManifest(name, DEFAULT_PERMISSIONS, DEFAULT_TIMEOUT_MS)

        /** Parses the first line of [source]; null manifest -> defaults. */
        fun parse(source: String, fallbackName: String): JsMacroManifest {
            val firstLine = source.lineSequence().firstOrNull()?.trim() ?: return defaults(fallbackName)
            if (!firstLine.startsWith("/* void-macro")) return defaults(fallbackName)
            val jsonStart = firstLine.indexOf('{')
            val jsonEnd = firstLine.lastIndexOf('}')
            if (jsonStart < 0 || jsonEnd <= jsonStart) return defaults(fallbackName)
            val json = firstLine.substring(jsonStart, jsonEnd + 1)
            val name = extractString(json, "name") ?: fallbackName
            val perms = extractStringArray(json, "permissions")
                .mapNotNull { Permission.fromString(it) }.toSet()
                .ifEmpty { DEFAULT_PERMISSIONS }
            val timeout = extractLong(json, "timeout_ms")?.coerceIn(1000L, MAX_TIMEOUT_MS)
                ?: DEFAULT_TIMEOUT_MS
            return JsMacroManifest(name, perms, timeout)
        }

        private fun extractString(json: String, key: String): String? {
            val needle = "\"$key\""
            val idx = json.indexOf(needle)
            if (idx < 0) return null
            val colon = json.indexOf(':', idx + needle.length)
            if (colon < 0) return null
            val open = json.indexOf('"', colon + 1)
            if (open < 0) return null
            val close = json.indexOf('"', open + 1)
            if (close < 0) return null
            return json.substring(open + 1, close)
        }

        private fun extractLong(json: String, key: String): Long? {
            val needle = "\"$key\""
            val idx = json.indexOf(needle)
            if (idx < 0) return null
            val colon = json.indexOf(':', idx + needle.length)
            if (colon < 0) return null
            val rest = json.substring(colon + 1).trimStart()
            val digits = rest.takeWhile { it.isDigit() }
            return digits.toLongOrNull()
        }

        private fun extractStringArray(json: String, key: String): List<String> {
            val needle = "\"$key\""
            val idx = json.indexOf(needle)
            if (idx < 0) return emptyList()
            val colon = json.indexOf(':', idx + needle.length)
            if (colon < 0) return emptyList()
            val open = json.indexOf('[', colon + 1)
            val close = json.indexOf(']', open + 1)
            if (open < 0 || close < 0) return emptyList()
            val inner = json.substring(open + 1, close)
            val out = mutableListOf<String>()
            var i = 0
            while (i < inner.length) {
                val q1 = inner.indexOf('"', i)
                if (q1 < 0) break
                val q2 = inner.indexOf('"', q1 + 1)
                if (q2 < 0) break
                out.add(inner.substring(q1 + 1, q2))
                i = q2 + 1
            }
            return out
        }
    }
}
