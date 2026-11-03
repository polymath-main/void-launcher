package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.model.FocusBlock

/**
 * impl-06 step 5 — pure focus-schedule rules (JVM-testable, no Android).
 *
 * Days use Calendar.DAY_OF_WEEK: 1 = Sunday … 7 = Saturday.
 *
 * Midnight crossing: a block starting 23:00 with duration 120 min is
 * active 23:00–23:59 on its days AND 00:00–00:59 on the FOLLOWING day.
 * The pre-step-5 code compared minutes-since-midnight only, so the
 * post-midnight half was silently truncated at 23:59.
 *
 * Overlap: highest `priority` (default 0) wins; ties keep file order
 * (first wins). Overlaps are reported, not silently resolved.
 */
object FocusRules {

    const val MINUTES_PER_DAY = 1440

    fun previousDay(dayOfWeek: Int): Int = if (dayOfWeek == 1) 7 else dayOfWeek - 1

    fun isActive(block: FocusBlock, dayOfWeek: Int, minutesSinceMidnight: Int): Boolean {
        val start = block.startHour * 60 + block.startMinute
        val end = start + block.durationMinutes
        return if (end <= MINUTES_PER_DAY) {
            block.daysOfWeek.contains(dayOfWeek) && minutesSinceMidnight in start until end
        } else {
            // Crosses midnight: evening half on the block's day, morning
            // half on the next day (only if that next day's predecessor
            // — i.e. the block's day — is in daysOfWeek).
            (block.daysOfWeek.contains(dayOfWeek) && minutesSinceMidnight >= start) ||
                (block.daysOfWeek.contains(previousDay(dayOfWeek)) &&
                    minutesSinceMidnight < end - MINUTES_PER_DAY)
        }
    }

    data class Resolution(val active: FocusBlock?, val overlapping: List<FocusBlock>)

    /** Enabled + active blocks only; highest priority wins, ties → file order. */
    fun resolve(blocks: List<FocusBlock>, dayOfWeek: Int, minutesSinceMidnight: Int): Resolution {
        val active = blocks.filter { it.isEnabled && isActive(it, dayOfWeek, minutesSinceMidnight) }
        if (active.isEmpty()) return Resolution(null, emptyList())
        var best = active[0]
        for (candidate in active.drop(1)) {
            if (candidate.priority > best.priority) best = candidate
        }
        return Resolution(best, active.filter { it !== best })
    }

    data class Upcoming(val block: FocusBlock, val dayOffset: Int)

    /** Next block whose START is in the future (today, or within 6 days). */
    fun nextBlock(blocks: List<FocusBlock>, dayOfWeek: Int, minutesSinceMidnight: Int): Upcoming? {
        var best: Upcoming? = null
        for (offset in 0..6) {
            val day = ((dayOfWeek - 1 + offset) % 7) + 1
            for (block in blocks) {
                if (!block.isEnabled) continue
                if (!block.daysOfWeek.contains(day)) continue
                val start = block.startHour * 60 + block.startMinute
                if (offset == 0 && start <= minutesSinceMidnight) continue
                val candidate = Upcoming(block, offset)
                val bestStart = best?.let { it.dayOffset * MINUTES_PER_DAY + it.block.startHour * 60 + it.block.startMinute }
                val candStart = offset * MINUTES_PER_DAY + start
                if (bestStart == null || candStart < bestStart) best = candidate
            }
        }
        return best
    }

    /** Structural validation (persona existence is checked at apply time,
     *  because config personas may register after the schedule loads). */
    fun validate(block: FocusBlock): String? {
        if (block.startHour !in 0..23) return "start_hour ${block.startHour} out of range 0..23"
        if (block.startMinute !in 0..59) return "start_min ${block.startMinute} out of range 0..59"
        if (block.durationMinutes <= 0) return "duration_min ${block.durationMinutes} must be positive"
        if (block.daysOfWeek.isEmpty()) return "days_of_week is empty"
        return null
    }

    fun formatStart(block: FocusBlock): String =
        "%02d:%02d".format(block.startHour, block.startMinute)
}
