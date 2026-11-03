package com.polymath.voidlauncher.core.services

import android.app.Activity
import android.util.TypedValue
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.voidlauncher.core.FocusRules
import com.polymath.voidlauncher.core.PersonaEditorRules
import com.polymath.voidlauncher.core.PersonaManager
import com.polymath.voidlauncher.model.FocusBlock
import java.io.File

/**
 * impl-06 step 8 — JSON-free focus schedule editor.
 *
 * Lists, adds, edits, and deletes blocks from focus_schedule.json.
 * Every save validates first (FocusRules.validate); the file is
 * written temp + rename in its own directory, so a failed write never
 * leaves a partially-valid schedule behind. Unknown persona ids are
 * warned about but allowed (step 5 skips them at runtime with a
 * logged reason — they are not a save-time error).
 *
 * Day picker labels show the Calendar.DAY_OF_WEEK mapping explicitly
 * (Sunday = 1 … Saturday = 7), as the plan requires.
 */
class FocusScheduleEditorService(private val activity: Activity) {

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), activity.resources.displayMetrics).toInt()

    private fun field(label: String, initial: String): EditText {
        return EditText(activity).apply {
            hint = label
            setText(initial)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
    }

    /** Reads the schedule file as blocks (structural parse only). */
    fun readBlocks(schedulePath: String): List<FocusBlock> {
        return try {
            val file = File(schedulePath)
            if (!file.exists()) return emptyList()
            val arr = org.json.JSONArray(file.readText())
            val out = mutableListOf<FocusBlock>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val daysArr = obj.optJSONArray("days_of_week")
                val days = if (daysArr != null && daysArr.length() > 0) {
                    (0 until daysArr.length()).map { daysArr.optInt(it, 1) }
                } else listOf(1, 2, 3, 4, 5, 6, 7)
                out.add(
                    FocusBlock(
                        id = obj.optString("id", "block_$i"),
                        label = obj.optString("label", "block_$i"),
                        personaId = obj.optString("persona", "void"),
                        startHour = obj.optInt("start_hour", 9),
                        startMinute = obj.optInt("start_min", 0),
                        durationMinutes = obj.optInt("duration_min", 60),
                        daysOfWeek = days,
                        glyphPatternName = obj.optString("glyph_pattern", "FOCUS_MODE_ON"),
                        isEnabled = obj.optBoolean("enabled", true),
                        priority = obj.optInt("priority", 0)
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Validated temp + rename write; false = file left untouched. */
    fun writeBlocks(schedulePath: String, blocks: List<FocusBlock>): Boolean {
        for (block in blocks) {
            if (FocusRules.validate(block) != null) return false
        }
        return try {
            val file = File(schedulePath)
            file.parentFile?.mkdirs()
            val arr = org.json.JSONArray()
            for (b in blocks) {
                arr.put(org.json.JSONObject().apply {
                    put("id", b.id)
                    put("label", b.label)
                    put("persona", b.personaId)
                    put("start_hour", b.startHour)
                    put("start_min", b.startMinute)
                    put("duration_min", b.durationMinutes)
                    put("glyph_pattern", b.glyphPatternName)
                    put("enabled", b.isEnabled)
                    put("priority", b.priority)
                    val days = org.json.JSONArray()
                    for (d in b.daysOfWeek) days.put(d)
                    put("days_of_week", days)
                })
            }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(arr.toString(2))
            if (tmp.renameTo(file)) true else {
                tmp.delete()
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun showScheduleDialog(
        schedulePath: String,
        personaManager: PersonaManager,
        onSaved: () -> Unit
    ) {
        val blocks = readBlocks(schedulePath)
        val items = blocks.map { b ->
            val state = if (b.isEnabled) "●" else "○"
            // A pre-existing invalid block blocks every save (write
            // validates all blocks, never partially-valid), so it is
            // flagged here instead of silently bricking other edits —
            // the user can fix or delete it from this same list.
            val problem = FocusRules.validate(b)
            val flag = if (problem != null) " ⚠ INVALID: $problem" else ""
            "$state ${b.label} (${b.personaId}) ${FocusRules.formatStart(b)} +${b.durationMinutes}m$flag"
        } + "➕ Add block"
        MaterialAlertDialogBuilder(activity)
            .setTitle("Focus schedule (JSON-free)")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == blocks.size) {
                    showBlockEditDialog(schedulePath, null, blocks, personaManager, onSaved)
                } else {
                    val block = blocks[which]
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Block: ${block.label}")
                        .setItems(arrayOf("Edit block", "Delete block")) { _, action ->
                            if (action == 0) {
                                showBlockEditDialog(schedulePath, block, blocks, personaManager, onSaved)
                            } else {
                                val kept = blocks.filter { it.id != block.id }
                                if (writeBlocks(schedulePath, kept)) {
                                    Toast.makeText(activity, "Deleted block ${block.id}", Toast.LENGTH_SHORT).show()
                                    onSaved()
                                } else {
                                    Toast.makeText(activity, "Delete failed: schedule file untouched", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .setNegativeButton("Close", null)
                        .show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showBlockEditDialog(
        schedulePath: String,
        existing: FocusBlock?,
        allBlocks: List<FocusBlock>,
        personaManager: PersonaManager,
        onSaved: () -> Unit
    ) {
        val isCreate = existing == null
        val idField = field("id (lowercase, e.g. morning)", existing?.id ?: "")
        idField.isEnabled = isCreate
        val labelField = field("Label", existing?.label ?: "")
        val hourField = field("Start hour (0-23)", existing?.startHour?.toString() ?: "9")
        val minField = field("Start minute (0-59)", existing?.startMinute?.toString() ?: "0")
        val durationField = field("Duration minutes", existing?.durationMinutes?.toString() ?: "60")
        val priorityField = field("Priority (default 0, highest wins)", existing?.priority?.toString() ?: "0")
        val enabledBox = CheckBox(activity).apply {
            text = "Enabled"
            isChecked = existing?.isEnabled ?: true
        }
        val personas = personaManager.getAllPersonas()
        val personaNames = personas.map { "${it.label} (${it.id})" }.toTypedArray()
        var personaIdx = personas.indexOfFirst { it.id.equals(existing?.personaId, ignoreCase = true) }.takeIf { it >= 0 } ?: 0

        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        fun addRow(label: String, view: android.view.View) {
            col.addView(TextView(activity).apply { text = label })
            col.addView(view)
        }
        addRow("Id", idField)
        addRow("Label", labelField)
        addRow("Start hour", hourField)
        addRow("Start minute", minField)
        addRow("Duration (minutes)", durationField)
        addRow("Priority", priorityField)
        col.addView(enabledBox)
        col.addView(TextView(activity).apply { text = "Persona (tap to change):" })
        val personaView = TextView(activity).apply {
            text = personaNames.getOrNull(personaIdx) ?: "void"
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        personaView.setOnClickListener {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Pick persona")
                .setItems(personaNames) { _, idx ->
                    personaIdx = idx
                    personaView.text = personaNames[idx]
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        col.addView(personaView)
        col.addView(TextView(activity).apply { text = "Days (Calendar.DAY_OF_WEEK: Sun=1 … Sat=7):" })
        val dayBoxes = PersonaEditorRules.DAY_LABELS.mapIndexed { idx, label ->
            CheckBox(activity).apply {
                text = label
                isChecked = existing?.daysOfWeek?.contains(PersonaEditorRules.DAY_VALUES[idx]) ?: true
            }
        }
        for (box in dayBoxes) col.addView(box)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(if (isCreate) "Add focus block" else "Edit block ${existing?.id}")
            .setView(col)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val id = idField.text.toString().trim().lowercase()
                if (isCreate) {
                    if (id.isEmpty() || !Regex("^[a-z0-9][a-z0-9_-]*$").matches(id)) {
                        Toast.makeText(activity, "Not saved: id must be lowercase letters, digits, '-' or '_'", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    if (allBlocks.any { it.id == id }) {
                        Toast.makeText(activity, "Not saved: block id '$id' already exists", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                }
                val candidate = FocusBlock(
                    id = existing?.id ?: id,
                    label = labelField.text.toString().trim(),
                    personaId = personas.getOrNull(personaIdx)?.id ?: "void",
                    startHour = hourField.text.toString().toIntOrNull() ?: -1,
                    startMinute = minField.text.toString().toIntOrNull() ?: -1,
                    durationMinutes = durationField.text.toString().toIntOrNull() ?: 0,
                    daysOfWeek = dayBoxes.mapIndexedNotNull { idx, box ->
                        if (box.isChecked) PersonaEditorRules.DAY_VALUES[idx] else null
                    },
                    glyphPatternName = existing?.glyphPatternName ?: "FOCUS_MODE_ON",
                    isEnabled = enabledBox.isChecked,
                    priority = priorityField.text.toString().toIntOrNull() ?: 0
                )
                val problem = FocusRules.validate(candidate)
                if (problem != null) {
                    Toast.makeText(activity, "Not saved: $problem", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (candidate.label.isEmpty()) {
                    Toast.makeText(activity, "Not saved: label must not be empty", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                // Overlap warning inline (not a save blocker): the
                // highest-priority rule in step 5 resolves it.
                val overlaps = allBlocks.filter { other ->
                    other.id != candidate.id && other.isEnabled &&
                        (1..7).any { day ->
                            (0 until 1440 step 15).any { min ->
                                FocusRules.isActive(other, day, min) && FocusRules.isActive(candidate, day, min)
                            }
                        }
                }
                if (overlaps.isNotEmpty()) {
                    Toast.makeText(
                        activity,
                        "Note: overlaps ${overlaps.joinToString { it.label }} — highest priority wins",
                        Toast.LENGTH_LONG
                    ).show()
                }
                val updated = if (isCreate) allBlocks + candidate
                else allBlocks.map { if (it.id == candidate.id) candidate else it }
                if (writeBlocks(schedulePath, updated)) {
                    Toast.makeText(activity, "Saved block ${candidate.id}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    onSaved()
                } else {
                    Toast.makeText(activity, "Save failed: schedule file untouched", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }
}
