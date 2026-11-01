package com.polymath.voidlauncher.core

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Macro Recorder & Playback Engine (F-15).
 *
 * Records user command/interaction sequences and serializes them to
 * ~/.voidlauncher/macros/<name>.json for single-touch replay.
 */
class MacroRecorder(private val context: Context) {

    companion object {
        private const val TAG = "MacroRecorder"
        private const val DEFAULT_MACROS_DIR = "/data/data/com.termux/files/home/.voidlauncher/macros"
    }

    var isRecording: Boolean = false
        private set

    var currentMacroName: String? = null
        private set

    private val recordedSteps = mutableListOf<String>()

    fun startRecording(name: String): Boolean {
        if (isRecording) return false
        isRecording = true
        currentMacroName = name
        recordedSteps.clear()
        Log.i(TAG, "Started recording macro: $name")
        return true
    }

    fun recordStep(step: String) {
        if (!isRecording) return
        recordedSteps.add(step)
        Log.d(TAG, "Recorded macro step: $step")
    }

    fun stopRecording(macrosDir: String = DEFAULT_MACROS_DIR): String? {
        if (!isRecording) return null
        val name = currentMacroName ?: "macro_${System.currentTimeMillis()}"
        isRecording = false
        currentMacroName = null

        try {
            val dir = File(macrosDir)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "$name.json")
            val json = JSONObject().apply {
                put("name", name)
                put("created_at", System.currentTimeMillis())
                put("steps", JSONArray(recordedSteps))
            }
            file.writeText(json.toString(2))
            Log.i(TAG, "Saved macro to ${file.absolutePath} (${recordedSteps.size} steps)")
            return file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving macro: ${e.message}")
            return null
        }
    }

    fun loadMacro(name: String, macrosDir: String = DEFAULT_MACROS_DIR): List<String> {
        val file = File(macrosDir, "$name.json")
        if (!file.exists() || !file.canRead()) return emptyList()

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

    fun playMacro(name: String, macrosDir: String = DEFAULT_MACROS_DIR, onStep: (String) -> Unit): Boolean {
        val steps = loadMacro(name, macrosDir)
        if (steps.isEmpty()) return false

        Log.i(TAG, "Executing macro $name (${steps.size} steps)")
        for (step in steps) {
            onStep(step)
        }
        return true
    }
}
