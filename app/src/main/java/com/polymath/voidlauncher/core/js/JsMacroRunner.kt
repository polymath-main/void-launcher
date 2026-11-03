package com.polymath.voidlauncher.core.js

import com.polymath.voidlauncher.core.MacroName
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs `.js` macros from a dedicated directory (impl-04 step 7b).
 *
 * JS macros live in a SEPARATE directory from string macros
 * (`macros-js/` by default) — the formats must never be mixed in
 * one directory or one command namespace (plan: format confusion
 * would be a security bug). Names follow the same [MacroName]
 * safety as string macros, and file size is capped at 256 KiB.
 *
 * The runner owns the gates the engine must not own: manifest
 * parsing (defaults deny files/shell/apps), permission wrapping,
 * and a wall-clock timeout enforced by joining the engine thread.
 * The native QuickJS interrupt handler is the authoritative
 * timeout on device; this join is the second, engine-independent
 * bound. A timed-out thread is interrupted and abandoned — it
 * may linger, which is why the native handler, not this join,
 * is the real timeout on device.
 */
class JsMacroRunner(
    private val engine: JsEngine,
    private val adaptersFactory: (JsMacroManifest, String) -> JsAdapters
) {

    /** Threads currently running a JS macro, by macro name, for `:void-js stop`. */
    private val runningThreads = ConcurrentHashMap<String, Thread>()

    data class RunOutcome(val name: String, val result: JsResult, val durationMs: Long)

    /** Parses the manifest of a macro file without running it (used for shell prompts). */
    fun parseManifest(name: String, jsMacrosDir: String): JsMacroManifest? {
        val file = resolveJsFile(jsMacrosDir, name) ?: return null
        if (!file.exists() || !file.canRead()) return null
        return JsMacroManifest.parse(file.readText(), name)
    }

    fun listMacros(jsMacrosDir: String): List<String> {
        val dir = File(jsMacrosDir)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".js") }
            ?.mapNotNull { it.name.removeSuffix(".js").takeIf { MacroName.isValid(it) } }
            ?.sorted() ?: emptyList()
    }

    fun deleteMacro(name: String, jsMacrosDir: String): Boolean {
        val file = resolveJsFile(jsMacrosDir, name) ?: return false
        return file.exists() && file.delete()
    }

    fun run(name: String, jsMacrosDir: String, trigger: String = "manual"): RunOutcome {
        val trimmed = name.trim()
        val file = resolveJsFile(jsMacrosDir, trimmed)
            ?: return RunOutcome(trimmed, JsResult.Failure("invalid JS macro name: $name"), 0)
        if (!file.exists() || !file.canRead()) {
            return RunOutcome(trimmed, JsResult.Failure("JS macro not found: $name"), 0)
        }
        if (file.length() > MacroName.MAX_FILE_BYTES) {
            return RunOutcome(trimmed, JsResult.Failure("JS macro file too large"), 0)
        }
        val source = try {
            file.readText()
        } catch (e: Exception) {
            return RunOutcome(trimmed, JsResult.Failure("cannot read JS macro: ${e.message}"), 0)
        }
        val manifest = JsMacroManifest.parse(source, trimmed)
        if (!engine.isAvailable) {
            return RunOutcome(
                trimmed,
                JsResult.Unavailable("QuickJS native library is not available on this build"),
                0
            )
        }
        val adapters = PermissionCheckingAdapters(adaptersFactory(manifest, trimmed), manifest)
        val started = System.currentTimeMillis()
        val resultRef = AtomicReference<JsResult>()
        val thread = Thread {
            try {
                resultRef.set(engine.run(source, manifest, adapters))
            } catch (e: JsPermissionDeniedException) {
                resultRef.set(JsResult.PermissionDenied(e.permission))
            } catch (e: Exception) {
                resultRef.set(JsResult.Failure(e.message ?: "JS macro failed"))
            }
        }
        runningThreads[trimmed] = thread
        try {
            thread.start()
            thread.join(manifest.timeoutMs)
            val result = resultRef.get()
            return if (result != null) {
                RunOutcome(trimmed, result, System.currentTimeMillis() - started)
            } else {
                thread.interrupt()
                RunOutcome(trimmed, JsResult.Timeout(manifest.timeoutMs), manifest.timeoutMs)
            }
        } finally {
            runningThreads.remove(trimmed, thread)
        }
    }

    /** Cooperative stop: interrupts the running thread, if any. Returns false if not running. */
    fun stop(name: String): Boolean {
        val thread = runningThreads[name.trim()] ?: return false
        thread.interrupt()
        return true
    }

    fun isRunning(name: String): Boolean = runningThreads.containsKey(name.trim())

    private fun resolveJsFile(jsMacrosDir: String, name: String): File? {
        if (MacroName.validate(name) != null) return null
        return try {
            val dir = File(jsMacrosDir).canonicalFile
            val file = File(dir, "$name.js").canonicalFile
            if (file.parentFile == dir || file.path.startsWith(dir.path + File.separator)) {
                file
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Per-macro key/value storage (prototype: one file per macro).
 * Stored as `key=value` lines under `<jsMacrosDir>/.void-storage/<macro>.txt`.
 * Pure java.io, JVM-testable. Keys/values are single-line; newlines
 * in values are escaped so a value cannot forge a second key.
 */
class FileJsStorage(private val jsMacrosDir: String, private val macroName: String) {
    private fun storageFile(): File {
        val dir = File(jsMacrosDir, ".void-storage")
        dir.mkdirs()
        return File(dir, "$macroName.txt")
    }

    fun get(key: String): String? {
        val file = storageFile()
        if (!file.exists()) return null
        for (line in file.readLines()) {
            val idx = line.indexOf('=')
            if (idx > 0 && line.substring(0, idx) == key) {
                return line.substring(idx + 1)
                    .replace("\\n", "\n").replace("\\\\", "\\")
            }
        }
        return null
    }

    fun set(key: String, value: String) {
        val file = storageFile()
        val entries = LinkedHashMap<String, String>()
        if (file.exists()) {
            for (line in file.readLines()) {
                val idx = line.indexOf('=')
                if (idx > 0) entries[line.substring(0, idx)] = line.substring(idx + 1)
            }
        }
        entries[key] = value.replace("\\", "\\\\").replace("\n", "\\n")
        file.writeText(entries.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }
}
