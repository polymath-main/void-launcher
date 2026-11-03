package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.model.GlyphPattern

/**
 * Pure haptic-waveform builder (impl-05 step 5).
 *
 * The old fallback played one fixed waveform per curve, ignoring period,
 * cycles, and brightness entirely, while custom-pattern JSON promised all
 * three. This builder honours cycles (repeated waveform, capped at 6
 * repetitions, matching the GDK path's <=0 -> 6 rule) and brightness
 * (scaled to a VibrationEffect amplitude 0..255). Period is NOT encoded
 * in a haptic waveform's fixed timings — the base timings per curve are
 * the device-feel constants — so period remains a GDK-path parameter;
 * that limitation is documented here rather than faked. GDK turnOn/
 * turnOff take only a channel code, so brightnessPercent still cannot
 * be passed to any GDK call; it is honoured on the haptic path only.
 */
object GlyphHapticWaveform {
    fun build(pattern: GlyphPattern): Pair<LongArray, IntArray> {
        val base = when (pattern.curve) {
            "STROBE" -> longArrayOf(0, 80, 80, 80, 80, 80)
            "PULSE" -> longArrayOf(0, 150, 100, 150)
            else -> longArrayOf(0, 120, 150, 120)
        }
        val cycles = if (pattern.cycles <= 0) 6 else pattern.cycles.coerceAtMost(6)
        val timings = mutableListOf<Long>()
        for (c in 0 until cycles) {
            // First repetition keeps the leading 0 delay; later ones drop
            // it so the pattern repeats without a dead gap.
            val segment = if (c == 0) base.toList() else base.drop(1)
            timings.addAll(segment)
        }
        val amplitude = (pattern.brightnessPercent.coerceIn(0, 100) * 255) / 100
        // Waveform timings alternate delay/vibrate starting with a delay,
        // and every base length is even, so concatenated repetitions keep
        // the same parity: even indices are delays (amplitude 0).
        val amplitudes = IntArray(timings.size) { i -> if (i % 2 == 0) 0 else amplitude }
        return timings.toLongArray() to amplitudes
    }
}
