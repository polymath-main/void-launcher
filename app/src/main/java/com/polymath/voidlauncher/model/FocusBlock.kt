package com.polymath.voidlauncher.model

/**
 * Scheduled focus block for automated time-boxed context switching (F-10).
 */
data class FocusBlock(
    val id: String,
    val label: String,
    val personaId: String,
    val startHour: Int,                        // 0..23
    val startMinute: Int,                      // 0..59
    val durationMinutes: Int,
    val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1=Sunday..7=Saturday (Calendar.DAY_OF_WEEK)
    val glyphPatternName: String = "FOCUS_MODE_ON",
    val isEnabled: Boolean = true,
    // impl-06 step 5: overlap priority, default 0. Highest wins;
    // ties keep file order. Only consulted when blocks overlap.
    val priority: Int = 0
)
