package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.FocusRules
import com.polymath.voidlauncher.model.FocusBlock
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** impl-06 step 5: focus correctness (pure rules) + scheduler wiring. */
class AgentStep5Test {

    // Calendar.DAY_OF_WEEK: 1 = Sunday … 7 = Saturday.
    private fun block(
        id: String,
        startHour: Int,
        startMin: Int = 0,
        duration: Int = 60,
        days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
        priority: Int = 0,
        enabled: Boolean = true
    ) = FocusBlock(
        id = id, label = id, personaId = "void",
        startHour = startHour, startMinute = startMin,
        durationMinutes = duration, daysOfWeek = days,
        priority = priority, isEnabled = enabled
    )

    @Test
    fun midnightCrossingBlockIsActiveEitherSide() {
        // Friday (6) 23:00 + 120 min: active Fri 23:30 and Sat 00:30,
        // inactive Sat 01:30.
        val b = block("night", 23, duration = 120, days = listOf(6))
        assertTrue(FocusRules.isActive(b, 6, 23 * 60 + 30))
        assertTrue(FocusRules.isActive(b, 7, 30))
        assertFalse(FocusRules.isActive(b, 7, 90))
        assertFalse(FocusRules.isActive(b, 6, 22 * 60 + 59))
    }

    @Test
    fun nonCrossingBlockIsNotActiveNextDay() {
        val b = block("day", 9, duration = 60, days = listOf(2))
        assertTrue(FocusRules.isActive(b, 2, 9 * 60 + 30))
        assertFalse(FocusRules.isActive(b, 3, 9 * 60 + 30))
    }

    @Test
    fun overlapHighestPriorityWinsTiesKeepFileOrder() {
        val low = block("low", 9, duration = 120, priority = 0)
        val high = block("high", 10, duration = 60, priority = 5)
        val res = FocusRules.resolve(listOf(low, high), 2, 10 * 60 + 30)
        assertEquals("high", res.active?.id)
        assertEquals(listOf("low"), res.overlapping.map { it.id })

        val first = block("first", 9, duration = 120)
        val second = block("second", 10, duration = 60)
        val tied = FocusRules.resolve(listOf(first, second), 2, 10 * 60 + 30)
        assertEquals("first", tied.active?.id)
        assertEquals(listOf("second"), tied.overlapping.map { it.id })
    }

    @Test
    fun disabledBlocksNeverWin() {
        val disabled = block("off", 9, duration = 120, enabled = false)
        assertNull(FocusRules.resolve(listOf(disabled), 2, 10 * 60).active)
    }

    @Test
    fun nextBlockSkipsPastStartsAndWrapsWeek() {
        // Monday (2) 10:00; blocks at 09:00 (past today) and 08:00 Tue.
        val past = block("past", 9, days = listOf(2))
        val tomorrow = block("tomorrow", 8, days = listOf(3))
        val next = FocusRules.nextBlock(listOf(past, tomorrow), 2, 10 * 60)
        assertEquals("tomorrow", next?.block?.id)
        assertEquals(1, next?.dayOffset)
    }

    @Test
    fun validationRejectsBadFields() {
        assertTrue(FocusRules.validate(block("x", 24)) != null)
        assertTrue(FocusRules.validate(block("x", 9, startMin = 60)) != null)
        assertTrue(FocusRules.validate(block("x", 9, duration = 0)) != null)
        assertTrue(FocusRules.validate(block("x", 9, days = emptyList())) != null)
        assertNull(FocusRules.validate(block("x", 9)))
    }

    @Test
    fun schedulerWiresRestoreAndPreview() {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val sched = File(root, "app/src/main/java/com/polymath/voidlauncher/core/FocusScheduler.kt").readText()
        assertTrue(sched.contains("personaBeforeBlock"))
        assertTrue(sched.contains("manualSwitchDuringBlock"))
        assertTrue(sched.contains("FocusRules.resolve"))
        assertTrue(sched.contains("priority"))
        assertTrue(sched.contains("getNextBlock"))
        assertTrue(sched.contains("getSkippedBlocks"))
        val activity = File(root, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").readText()
        assertTrue(activity.contains("[FOCUS NEXT]"))
        assertTrue(activity.contains("No scheduled focus blocks"))
    }

    private fun findRepoRoot(): File? {
        var dir: File? = File("").absoluteFile
        var hops = 0
        while (dir != null && hops < 6) {
            if (File(dir, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").exists()) return dir
            dir = dir.parentFile
            hops++
        }
        return null
    }
}
