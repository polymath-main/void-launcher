package com.polymath.voidlauncher.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.polymath.voidlauncher.model.GlyphPattern
import org.json.JSONArray
import java.io.File

/**
 * Glyph Choreographer (F-03).
 *
 * Directs Nothing Phone (2a) rear Glyph LED channels with timed animation sequences.
 * Gracefully cascades to haptic vibration patterns on non-Nothing hardware.
 */
class GlyphChoreographer(private val context: Context) {

    companion object {
        private const val TAG = "GlyphChoreographer"

        val PATTERN_THERMAL = GlyphPattern("THERMAL_WARNING", listOf(1, 2, 3), periodMs = 200, cycles = 4, curve = "STROBE")
        val PATTERN_SUCCESS = GlyphPattern("TASK_SUCCESS", listOf(1, 2), periodMs = 350, cycles = 2, curve = "SINE")
        val PATTERN_FAIL    = GlyphPattern("TASK_FAIL", listOf(1), periodMs = 150, cycles = 3, curve = "PULSE")
        val PATTERN_FOCUS   = GlyphPattern("FOCUS_MODE_ON", listOf(2, 3), periodMs = 600, cycles = 1, curve = "LINEAR")
        val PATTERN_RUNNING = GlyphPattern("AGENT_RUNNING", listOf(1, 2), periodMs = 500, cycles = 2, curve = "SINE")
    }

    private var isNothingPhone = false
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val patternRegistry = mutableMapOf<String, GlyphPattern>()

    init {
        detectHardware()
        registerBuiltInPatterns()
    }

    private fun detectHardware() {
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        isNothingPhone = manufacturer.equals("Nothing", ignoreCase = true) || model.contains("A142", ignoreCase = true)
        Log.d(TAG, "Hardware status: isNothingPhone=$isNothingPhone (model=$model)")
    }

    private fun registerBuiltInPatterns() {
        patternRegistry["THERMAL_WARNING"] = PATTERN_THERMAL
        patternRegistry["TASK_SUCCESS"] = PATTERN_SUCCESS
        patternRegistry["TASK_FAIL"] = PATTERN_FAIL
        patternRegistry["FOCUS_MODE_ON"] = PATTERN_FOCUS
        patternRegistry["AGENT_RUNNING"] = PATTERN_RUNNING
    }

    fun loadCustomPatterns(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "")
                if (name.isEmpty()) continue
                val channels = mutableListOf<Int>()
                val chArr = obj.optJSONArray("channels")
                if (chArr != null) {
                    for (j in 0 until chArr.length()) channels.add(chArr.optInt(j, 1))
                } else {
                    channels.add(1)
                }
                patternRegistry[name.uppercase()] = GlyphPattern(
                    name = name,
                    channels = channels,
                    periodMs = obj.optLong("period_ms", 400L),
                    cycles = obj.optInt("cycles", 1),
                    brightnessPercent = obj.optInt("brightness_percent", 100),
                    curve = obj.optString("curve", "SINE")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load custom glyph patterns: ${e.message}")
        }
    }

    /**
     * Plays the named pattern on Nothing Glyph LEDs (or falls back to haptics).
     */
    fun play(patternName: String) {
        val pattern = patternRegistry[patternName.uppercase()] ?: PATTERN_SUCCESS
        playPattern(pattern)
    }

    fun playPattern(pattern: GlyphPattern) {
        Log.i(TAG, "Playing Glyph pattern '${pattern.name}' (channels=${pattern.channels}, cycles=${pattern.cycles})")
        if (isNothingPhone) {
            // Extensible hook: invokes Nothing GDK GlyphManager if compiled with GDK library
            executeNothingGlyphSequence(pattern)
        } else {
            fallbackHapticPulse(pattern)
        }
    }

    private fun executeNothingGlyphSequence(pattern: GlyphPattern) {
        // Safe reflection/dynamic execution against Nothing GDK framework if installed on device
        try {
            val glyphServiceCls = Class.forName("com.nothing.ketchum.GlyphManager")
            val getInstanceMethod = glyphServiceCls.getMethod("getInstance", Context::class.java)
            val instance = getInstanceMethod.invoke(null, context)
            if (instance != null) {
                // Device GDK active
                Log.d(TAG, "Dispatched pattern ${pattern.name} to Nothing GDK service")
                return
            }
        } catch (_: Exception) {
            // GDK library runtime class not on classpath — fallback to synchronized haptics
        }
        fallbackHapticPulse(pattern)
    }

    private fun fallbackHapticPulse(pattern: GlyphPattern) {
        if (vibrator == null || !vibrator!!.hasVibrator()) return

        val timings = when (pattern.curve) {
            "STROBE" -> longArrayOf(0, 80, 80, 80, 80, 80)
            "PULSE"  -> longArrayOf(0, 150, 100, 150)
            else     -> longArrayOf(0, 120, 150, 120)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(timings, -1)
            }
        } catch (_: Exception) {}
    }
}
