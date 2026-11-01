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

    fun loadSchedule(schedulePath: String): List<FocusBlock> {
        blocks.clear()
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

                blocks.add(
                    FocusBlock(
                        id = id,
                        label = label,
                        personaId = personaId,
                        startHour = startHour,
                        startMinute = startMin,
                        durationMinutes = duration,
                        glyphPatternName = glyph,
                        isEnabled = enabled
                    )
                )
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
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTimeMinutes = currentHour * 60 + currentMinute

        for (block in blocks) {
            if (!block.isEnabled) continue
            if (!block.daysOfWeek.contains(currentDay)) continue

            val blockStart = block.startHour * 60 + block.startMinute
            val blockEnd = blockStart + block.durationMinutes

            if (currentTimeMinutes in blockStart until blockEnd) {
                if (lastActiveBlockId != block.id) {
                    lastActiveBlockId = block.id
                    Log.i(TAG, "Entering scheduled focus block: ${block.label} (persona=${block.personaId})")
                    personaManager.selectPersona(block.personaId)
                    glyphChoreographer.play(block.glyphPatternName)
                }
                return block
            }
        }

        if (lastActiveBlockId != null) {
            Log.i(TAG, "Exited focus block $lastActiveBlockId")
            lastActiveBlockId = null
        }
        return null
    }
}
