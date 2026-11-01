package com.polymath.voidlauncher.model

/**
 * Nothing Phone (2a) Glyph LED animation descriptor (F-03).
 */
data class GlyphPattern(
    val name: String,
    val channels: List<Int> = listOf(1),       // LED channel IDs on Nothing Phone (2a)
    val periodMs: Long = 400L,                 // Pulse duration
    val cycles: Int = 1,                       // Number of repetitions (-1 for continuous)
    val brightnessPercent: Int = 100,          // 0..100
    val curve: String = "SINE"                 // SINE | LINEAR | PULSE | STROBE
)
