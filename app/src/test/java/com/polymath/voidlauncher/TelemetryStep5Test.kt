package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.GlyphHapticWaveform
import com.polymath.voidlauncher.core.GlyphPlaybackPolicy
import com.polymath.voidlauncher.core.PlayDecision
import com.polymath.voidlauncher.model.GlyphPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * impl-05 step 5: glyph playback contract, tested through the pure seams.
 * Real GDK LED behaviour cannot be tested on the JVM (no GDK artifact in
 * the repo) and remains UNKNOWN pending a device verification pass — see
 * the impl log's device-check checklist. The Android choreographer
 * delegates decisions to this policy; Handler/GDK paths are not claimed
 * from these tests.
 */
class TelemetryStep5Test {

    @Test
    fun unknownPatternIsNeverSuccessFallback() {
        val policy = GlyphPlaybackPolicy()
        policy.register("TASK_SUCCESS")
        assertEquals(PlayDecision.IGNORE_UNKNOWN, policy.request("TYPO_PATTERN"))
        assertNull(policy.currentPlaying)
        assertEquals(PlayDecision.PLAY, policy.request("TASK_SUCCESS"))
    }

    @Test
    fun duplicatePlayIsDroppedAndStopClears() {
        val policy = GlyphPlaybackPolicy()
        policy.register("THERMAL_WARNING")
        assertEquals(PlayDecision.PLAY, policy.request("THERMAL_WARNING"))
        assertEquals(PlayDecision.DROP_DUPLICATE, policy.request("thermal_warning"))
        policy.stop()
        assertNull(policy.currentPlaying)
        assertEquals(PlayDecision.PLAY, policy.request("THERMAL_WARNING"))
    }

    @Test
    fun hapticWaveformHonoursCyclesAndBrightness() {
        val pattern = GlyphPattern("TEST", listOf(1), periodMs = 200, cycles = 2, brightnessPercent = 50, curve = "PULSE")
        val (timings, amplitudes) = GlyphHapticWaveform.build(pattern)
        // PULSE base is 4 entries; 2 repetitions (second drops leading 0) = 7.
        assertEquals(7, timings.size)
        assertEquals(7, amplitudes.size)
        // 50% brightness -> amplitude 127; delays (even indices) are 0.
        assertEquals(0, amplitudes[0])
        assertEquals(127, amplitudes[1])
        assertEquals(0, amplitudes[2])
    }

    @Test
    fun hapticWaveformCapsCyclesAndClampsBrightness() {
        val pattern = GlyphPattern("TEST", listOf(1), periodMs = 200, cycles = 0, brightnessPercent = 150, curve = "SINE")
        val (timings, amplitudes) = GlyphHapticWaveform.build(pattern)
        // cycles <= 0 -> 6 repetitions of a 4-entry base (minus leading 0s) = 19.
        assertEquals(19, timings.size)
        assertTrue(amplitudes.all { it == 0 || it == 255 })
    }
}
