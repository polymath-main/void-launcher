package com.polymath.voidlauncher.core

import android.content.Context
import android.util.Log
import com.polymath.voidlauncher.model.FocusBlock
import org.json.JSONArray
import java.io.File
import java.util.Calendar

/**
 * Focus Mode Scheduler (F-10).
 *
 * Automates time-boxed context switching and persona activation
 * based on schedule configuration (~/.voidlauncher/focus_schedule.json).
 *
 * impl-06 step 5 rules (pure logic lives in FocusRules):
 * - Midnight-crossing blocks stay active past 00:00 (on the next day).
 * - Overlapping blocks: highest priority wins, ties keep file order,
 *   and the overlap is logged as a warning.
 * - Restore-on-exit: the persona active before the first block of a
 *   run started is restored on exit — UNLESS the user manually
 *   switched persona during the block (detected on the next tick by
 *   the active persona differing from the block's persona), in which
 *   case the user's choice stands and nothing is restored.
 * - Invalid blocks are skipped with a logged reason, never crashing
 *   the scheduler; unknown persona ids are skipped the same way at
 *   apply time (checked then, not at load, because config personas
 *   may register after the schedule loads).
 */
class FocusScheduler(
    private val context: Context,
    private val personaManager: PersonaManager,
    private val glyphChoreographer: GlyphChoreographer
) {

    companion object {
        private const val TAG = "FocusScheduler"
    }

    private val blocks = mutableListOf<FocusBlock>()
    private var lastActiveBlockId: String? = null
    private var personaBeforeBlock: String? = null
    private var manualSwitchDuringBlock: Boolean = false
    private val unknownPersonaWarned = mutableSetOf<String>()
    private val skipped = mutableListOf<Pair<FocusBlock, String>>()

    /** Blocks skipped at load, with reasons — surfaced by :focus. */
    fun getSkippedBlocks(): List<Pair<FocusBlock, String>> = skipped.toList()

    fun getBlocks(): List<FocusBlock> = blocks.toList()

    fun loadSchedule(schedulePath: String): List<FocusBlock> {
        blocks.clear()
        skipped.clear()
        unknownPersonaWarned.clear()
        try {
            val file = File(schedulePath)
            if (!file.exists() || !file.canRead()) return emptyList()

            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "block_$i")
                val label = obj.optString("label", id)
                val personaId = obj.optString("persona", "void")
                val startHour = obj.optInt("start_hour", 9)
                val startMin = obj.optInt("start_min", 0)
                val duration = obj.optInt("duration_min", 60)
                val glyph = obj.optString("glyph_pattern", "FOCUS_MODE_ON")
                val enabled = obj.optBoolean("enabled", true)
                val priority = obj.optInt("priority", 0)
                val daysArr = obj.optJSONArray("days_of_week")
                val days = if (daysArr != null && daysArr.length() > 0) {
                    (0 until daysArr.length()).map { daysArr.optInt(it, 1) }
                } else {
                    listOf(1, 2, 3, 4, 5, 6, 7)
                }

                val block = FocusBlock(
                    id = id,
                    label = label,
                    personaId = personaId,
                    startHour = startHour,
                    startMinute = startMin,
                    durationMinutes = duration,
                    daysOfWeek = days,
                    glyphPatternName = glyph,
                    isEnabled = enabled,
                    priority = priority
                )
                val problem = FocusRules.validate(block)
                if (problem != null) {
                    Log.w(TAG, "Skipping focus block ${block.id}: $problem")
                    skipped.add(block to problem)
                    continue
                }
                blocks.add(block)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading focus schedule: ${e.message}")
        }
        return blocks
    }

    /**
     * Evaluates current system time against all scheduled focus blocks.
     * Automatically applies persona and glyph cues on state transition.
     */
    fun checkAndApplySchedule(): FocusBlock? {
        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        val currentTimeMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return checkAndApplyScheduleAt(currentDay, currentTimeMinutes)
    }

    /** Testable entry: same logic, explicit day/time. */
    internal fun checkAndApplyScheduleAt(dayOfWeek: Int, minutesSinceMidnight: Int): FocusBlock? {
        val resolution = FocusRules.resolve(blocks, dayOfWeek, minutesSinceMidnight)
        if (resolution.overlapping.isNotEmpty()) {
            Log.w(
                TAG,
                "Overlapping focus blocks: ${resolution.active?.id} wins " +
                    "(priority ${resolution.active?.priority}); " +
                    "overlapped: ${resolution.overlapping.joinToString { it.id }}"
            )
        }
        val block = resolution.active
        if (block != null) {
            // Unknown persona: skip with a logged reason, never crash,
            // and never enter the block (warned once per load).
            if (personaManager.getAllPersonas().none { it.id.equals(block.personaId, ignoreCase = true) }) {
                if (unknownPersonaWarned.add(block.id)) {
                    Log.w(TAG, "Skipping focus block ${block.id}: unknown persona '${block.personaId}'")
                }
                return exitBlockIfNeeded()
            }
            if (lastActiveBlockId != block.id) {
                // Entering a run (not a block-to-block hop): remember
                // the persona active before the run started. A hop
                // keeps the original, so the final exit restores it.
                if (lastActiveBlockId == null) {
                    personaBeforeBlock = personaManager.getActivePersona().id
                    manualSwitchDuringBlock = false
                }
                lastActiveBlockId = block.id
                Log.i(TAG, "Entering scheduled focus block: ${block.label} (persona=${block.personaId})")
                personaManager.selectPersona(block.personaId)
                glyphChoreographer.play(block.glyphPatternName)
            } else if (!manualSwitchDuringBlock &&
                !personaManager.getActivePersona().id.equals(block.personaId, ignoreCase = true)
            ) {
                // The user switched away mid-block: respect it. The
                // block is NOT re-applied (lastActiveBlockId already
                // matches), and exit will NOT restore over the choice.
                manualSwitchDuringBlock = true
                Log.i(TAG, "Manual persona switch during focus block ${block.id}; restore on exit disabled")
            }
            return block
        }
        return exitBlockIfNeeded()
    }

    private fun exitBlockIfNeeded(): FocusBlock? {
        if (lastActiveBlockId != null) {
            Log.i(TAG, "Exited focus block $lastActiveBlockId")
            val restoreTo = personaBeforeBlock
            lastActiveBlockId = null
            if (!manualSwitchDuringBlock && restoreTo != null) {
                Log.i(TAG, "Restoring pre-block persona $restoreTo")
                personaManager.selectPersona(restoreTo)
            }
            personaBeforeBlock = null
            manualSwitchDuringBlock = false
        }
        return null
    }

    /** Next upcoming block (for :focus preview); null when none scheduled. */
    fun getNextBlock(): FocusRules.Upcoming? {
        val now = Calendar.getInstance()
        return FocusRules.nextBlock(
            blocks,
            now.get(Calendar.DAY_OF_WEEK),
            now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        )
    }
}
