package com.polymath.voidlauncher.core

import android.content.Context
import android.util.Log

/**
 * Nothing Glyph facade.
 *
 * Detects Nothing hardware and forwards task/thermal pulses to [GlyphChoreographer],
 * which owns the actual LED sequence (with haptic fallback).
 */
class NothingGlyphManager(private val context: Context) {

    private var isNothingPhone = false
    private var isGlyphSupported = false

    /** Choreographer wired in by the launcher; without it, pulses are logged only. */
    var choreographer: GlyphChoreographer? = null

    init {
        detectHardware()
    }

    private fun detectHardware() {
        val model = android.os.Build.MODEL
        val manufacturer = android.os.Build.MANUFACTURER
        isNothingPhone = manufacturer.equals("Nothing", ignoreCase = true) || model.contains("A142", ignoreCase = true)
        Log.d(TAG, "Hardware detection: isNothingPhone=$isNothingPhone (model=$model)")
    }

    /**
     * Pulses glyph (or haptics) when an agent build finishes or a thermal alert trips.
     */
    fun triggerTaskPulse(isSuccess: Boolean) {
        if (!isNothingPhone) return
        Log.i(TAG, "Triggering Glyph pulse for task event: success=$isSuccess")
        choreographer?.play(if (isSuccess) "TASK_SUCCESS" else "TASK_FAIL")
    }

    fun triggerThermalWarning() {
        if (!isNothingPhone) return
        Log.w(TAG, "Triggering Glyph thermal warning pulse")
        choreographer?.play("THERMAL_WARNING")
    }

    companion object {
        private const val TAG = "NothingGlyphManager"
    }
}
