package com.polymath.voidlauncher.core

import android.util.Log
import com.polymath.voidlauncher.model.VoidPaths
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Macro Recorder & Playback Engine (F-15).
 *
 * Records user command/interaction sequences and serializes them to
 * `<macrosDir>/<name>.json` for single-touch replay.
 *
 * impl-04 step 1 safety (plan gaps G-S1, G-S2, S3):
 * - Names are validated by [MacroName] at start/stop/load/play:
 *   separators, `..`, NUL and empty names are rejected, and every
 *   resolved file must canonically stay under the macros directory.
 *   A hand-edited caller or file cannot bypass the first check.
 * - Playback tracks the set of macros currently playing on this
 *   recorder: a name already playing, or a play depth over
 *   [MacroName.MAX_PLAY_DEPTH], aborts with [lastPlayError] instead
 *   of recursing to a stack overflow (self-play and A→B→A cycles).
 * - Load refuses files over [MacroName.MAX_FILE_BYTES].
 * Playback still re-enters the full omni pipeline per step (by
 * design for a recorder); gate re-checks at play time are the
 * Activity's job (plan gate S8), not this class's.
 */
class MacroRecorder() {

    companion object {
        private const val TAG = "MacroRecorder"
    }

    var isRecording: Boolean = false
        private set

    var currentMacroName: String? = null
        private set

    /** Directory the in-progress recording writes its partial file to. */
    private var partialDir: String? = null

    /** Max steps per string macro (impl-04 step 6). */
    val maxSteps: Int = 500

    /** Set when the last stop/play failed for a safety reason. */
    var lastError: String? = null
        private set

    private val recordedSteps = mutableListOf<String>()

    /** Macros currently being played by this recorder (synchronous). */
    private val playing = mutableListOf<String>()

    fun startRecording(name: String, macrosDir: String = VoidPaths.MACROS): Boolean {
        if (isRecording) return false
        val problem = MacroName.validate(name)
        if (problem != null) {
            lastError = problem
            Log.w(TAG, "Refused macro recording: $problem")
            return false
        }
        isRecording = true
        currentMacroName = name.trim()
        partialDir = macrosDir
        recordedSteps.clear()
        // Drop any stale partial from a previous interrupted session
        // with the same name, so recovery never shows old steps.
        MacroName.resolveFile(macrosDir, name.trim())?.let { f ->
            File(f.parentFile, "${f.nameWithoutExtension}.partial.json").takeIf { it.exists() }?.delete()
        }
        lastError = null
        Log.i(TAG, "Started recording macro: $name")
        return true
    }

    /**
     * impl-04 step 6: returns false (and sets [lastError]) when the
     * 500-step cap is hit — the step is not recorded. Every recorded
     * step is also written incrementally to `<name>.partial.json`,
     * so killing the app mid-record leaves a recoverable partial
     * instead of silently losing the recording.
     */
    fun recordStep(step: String): Boolean {
        if (!isRecording) return false
        if (recordedSteps.size >= maxSteps) {
            lastError = "macro step cap ($maxSteps steps) reached"
            Log.w(TAG, lastError!!)
            return false
        }
        recordedSteps.add(step)
        writePartial()
        Log.d(TAG, "Recorded macro step: $step")
        return true
    }

    private fun writePartial() {
        val name = currentMacroName ?: return
        val dir = partialDir ?: return
        try {
            val file = MacroName.resolveFile(dir, name) ?: return
            val partial = File(file.parentFile, "${file.nameWithoutExtension}.partial.json")
            partial.parentFile?.mkdirs()
            val json = JSONObject().apply {
                put("name", name)
                put("partial", true)
                put("created_at", System.currentTimeMillis())
                put("steps", JSONArray(recordedSteps))
            }
            partial.writeText(json.toString(2))
        } catch (_: Exception) {
            // Partial persistence is best-effort; it must never break
            // recording itself.
        }
    }

    /** Steps from an interrupted recording's partial file, or empty. */
    fun loadPartial(name: String, macrosDir: String = VoidPaths.MACROS): List<String> {
        val file = MacroName.resolveFile(macrosDir, name) ?: return emptyList()
        val partial = File(file.parentFile, "${file.nameWithoutExtension}.partial.json")
        if (!partial.exists() || !partial.canRead()) return emptyList()
        return try {
            val json = JSONObject(partial.readText())
            val stepsArr = json.optJSONArray("steps") ?: return emptyList()
            (0 until stepsArr.length()).map { stepsArr.optString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun stopRecording(macrosDir: String = VoidPaths.MACROS): String? {
        if (!isRecording) return null
        val name = currentMacroName ?: "macro_${System.currentTimeMillis()}"
        isRecording = false
        currentMacroName = null

        val file = MacroName.resolveFile(macrosDir, name)
        if (file == null) {
            lastError = MacroName.validate(name) ?: "macro file escapes macros directory"
            Log.e(TAG, "Refused macro save: $lastError")
            return null
        }
        return try {
            file.parentFile?.mkdirs()
            // A successful save promotes the partial out of existence.
            File(file.parentFile, "${file.nameWithoutExtension}.partial.json").takeIf { it.exists() }?.delete()
            val json = JSONObject().apply {
                put("name", name)
                put("created_at", System.currentTimeMillis())
                put("steps", JSONArray(recordedSteps))
            }
            file.writeText(json.toString(2))
            lastError = null
            Log.i(TAG, "Saved macro to ${file.absolutePath} (${recordedSteps.size} steps)")
            file.absolutePath
        } catch (e: Exception) {
            lastError = e.message
            Log.e(TAG, "Failed saving macro: ${e.message}")
            null
        }
    }

    fun loadMacro(name: String, macrosDir: String = VoidPaths.MACROS): List<String> {
        val file = MacroName.resolveFile(macrosDir, name) ?: return emptyList()
        if (!file.exists() || !file.canRead() || file.length() > MacroName.MAX_FILE_BYTES) {
            return emptyList()
        }

        return try {
            val json = JSONObject(file.readText())
            val stepsArr = json.optJSONArray("steps") ?: return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until stepsArr.length()) {
                list.add(stepsArr.optString(i))
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Error loading macro $name: ${e.message}")
            emptyList()
        }
    }

    fun playMacro(name: String, macrosDir: String = VoidPaths.MACROS, onStep: (String) -> Unit): Boolean {
        val trimmed = name.trim()
        if (playing.contains(trimmed) || playing.size >= MacroName.MAX_PLAY_DEPTH) {
            lastError = "macro recursion limit: '$trimmed' is already playing or depth exceeded"
            Log.w(TAG, lastError!!)
            return false
        }
        val steps = loadMacro(trimmed, macrosDir)
        if (steps.isEmpty()) return false

        Log.i(TAG, "Executing macro $trimmed (${steps.size} steps)")
        playing.add(trimmed)
        try {
            for (step in steps) {
                onStep(step)
            }
        } finally {
            playing.remove(trimmed)
        }
        lastError = null
        return true
    }

    /** Deletes a macro file, subject to the same name safety. */
    fun deleteMacro(name: String, macrosDir: String = VoidPaths.MACROS): Boolean {
        val file = MacroName.resolveFile(macrosDir, name) ?: return false
        return file.exists() && file.delete()
    }

    /** Lists macro names present in the directory (sanitized names only). */
    fun listMacros(macrosDir: String = VoidPaths.MACROS): List<String> {
        val dir = File(macrosDir)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { it.name.removeSuffix(".json").takeIf { MacroName.isValid(it) } }
            ?.sorted() ?: emptyList()
    }
}
