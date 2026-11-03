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

    /** Choreographer wired in by the launcher; without it, pulses are logged only. */
    var choreographer: GlyphChoreographer? = null

    init {
        detectHardware()
    }

    private fun detectHardware() {
        // impl-05 step 5: single detection owner (NothingHardware); the
        // dead supported-flag field deleted (never assigned/read).
        isNothingPhone = NothingHardware.isNothingPhone()
        Log.d(TAG, "Hardware detection: isNothingPhone=$isNothingPhone (model=${android.os.Build.MODEL})")
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
