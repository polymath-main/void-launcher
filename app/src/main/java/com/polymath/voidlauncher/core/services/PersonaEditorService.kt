package com.polymath.voidlauncher.core.services

import android.app.Activity
import android.util.TypedValue
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.voidlauncher.core.PersonaEditorRules
import com.polymath.voidlauncher.core.PersonaManager
import com.polymath.voidlauncher.model.AppEntry
import com.polymath.voidlauncher.model.DockSlot
import com.polymath.voidlauncher.model.LauncherConfig
import com.polymath.voidlauncher.model.PersonaProfile

/**
 * impl-06 step 8 — JSON-free persona management.
 *
 * - Custom personas (config.personas): create / rename / recolour
 *   accent + secondary / change subtitle / change workspace / delete.
 *   Persisted to config.personas via the onCommit callback — never to
 *   external JSON files.
 * - Built-ins void / main / still: badge label override ONLY, via the
 *   existing identity*Label mechanism. Agent / client have no label
 *   override mechanism and are not offered for editing (their badge
 *   renders from the id, not the label, in NUMERIC_BRACKET style).
 * - Per-persona dock slots (custom personas only): assign app,
 *   built-in action, rename, reset a slot to the config dock, or
 *   clear the whole override. Stored in customDockSlots and applied
 *   by step 3's applyPersonaSwitch. Built-ins use the config dock;
 *   shadowing a built-in with a config persona to smuggle slots in
 *   is deliberately NOT supported.
 *
 * Every save validates first (PersonaEditorRules) and commits only
 * a fully-valid config through onCommit; the caller saves it via
 * ConfigManager (which itself writes tmp + rename).
 */
class PersonaEditorService(private val activity: Activity) {

    private val dockService = DockSlotMicroService(activity)

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), activity.resources.displayMetrics).toInt()

    private fun field(label: String, initial: String): EditText {
        return EditText(activity).apply {
            hint = label
            setText(initial)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
    }

    private fun labeledColumn(vararg rows: Pair<String, EditText>): LinearLayout {
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        for ((label, input) in rows) {
            col.addView(TextView(activity).apply { text = label })
            col.addView(input)
        }
        return col
    }

    fun showPersonaManagerDialog(
        config: LauncherConfig,
        personaManager: PersonaManager,
        appsProvider: () -> List<AppEntry>,
        onCommit: (LauncherConfig) -> Unit
    ) {
        val customs = config.personas
        val overridables = listOf("void", "main", "still").mapNotNull { id ->
            personaManager.getAllPersonas().firstOrNull { it.id == id }
        }
        val items = mutableListOf<String>()
        items.add("➕ Create new persona")
        for (p in customs) items.add("✏️ ${p.label} (${p.id}) — custom")
        for (p in overridables) items.add("🏷️ ${p.label} (${p.id}) — badge label only")
        MaterialAlertDialogBuilder(activity)
            .setTitle("Personas (JSON-free)")
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which == 0 -> showPersonaEditDialog(config, null, onCommit)
                    which - 1 < customs.size -> {
                        val persona = customs[which - 1]
                        MaterialAlertDialogBuilder(activity)
                            .setTitle("Persona: ${persona.label}")
                            .setItems(arrayOf("Edit fields", "Edit dock slots", "Delete persona")) { _, action ->
                                when (action) {
                                    0 -> showPersonaEditDialog(config, persona, onCommit)
                                    1 -> showPersonaDockDialog(config, persona, appsProvider, onCommit)
                                    2 -> {
                                        val updated = config.copy(
                                            personas = config.personas.filter { it.id != persona.id }
                                        )
                                        onCommit(updated)
                                        Toast.makeText(activity, "Deleted persona ${persona.id}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .setNegativeButton("Close", null)
                            .show()
                    }
                    else -> {
                        val builtin = overridables[which - 1 - customs.size]
                        showBuiltinLabelDialog(config, builtin, onCommit)
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPersonaEditDialog(
        config: LauncherConfig,
        existing: PersonaProfile?,
        onCommit: (LauncherConfig) -> Unit
    ) {
        val isCreate = existing == null
        val idField = field("id (lowercase, e.g. work)", existing?.id ?: "")
        idField.isEnabled = isCreate
        val labelField = field("Label", existing?.label ?: "")
        val subtitleField = field("Subtitle", existing?.subtitle ?: "")
        val accentField = field("Accent hex (#RRGGBB)", existing?.accentColor ?: "#E11D48")
        val secondaryField = field("Secondary hex (#RRGGBB)", existing?.secondaryColor ?: "#06B6D4")
        val workspaceField = field("Workspace path", existing?.workspacePath ?: "")
        val view = labeledColumn(
            "Id" to idField,
            "Label" to labelField,
            "Subtitle" to subtitleField,
            "Accent" to accentField,
            "Secondary" to secondaryField,
            "Workspace path" to workspaceField
        )
        // Positive button is attached in onShowListener so a
        // validation failure keeps the dialog open (the default
        // positive button auto-dismisses even when nothing is saved).
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(if (isCreate) "Create persona" else "Edit persona ${existing?.id}")
            .setView(view)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val allIds = (config.personas.map { it.id.lowercase() } +
                    listOf("void", "main", "still", "agent", "client")).toSet()
                val idError = if (isCreate) {
                    PersonaEditorRules.validatePersonaId(idField.text.toString(), allIds)
                } else null
                val fieldError = PersonaEditorRules.validatePersonaFields(
                    labelField.text.toString(),
                    accentField.text.toString(),
                    secondaryField.text.toString()
                )
                val error = idError ?: fieldError
                if (error != null) {
                    Toast.makeText(activity, "Not saved: $error", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                // label has no default, so a create builds the full
                // object; an edit copies the existing one.
                val persona = if (existing != null) {
                    existing.copy(
                        label = labelField.text.toString().trim(),
                        subtitle = subtitleField.text.toString().trim(),
                        accentColor = accentField.text.toString().trim(),
                        secondaryColor = secondaryField.text.toString().trim(),
                        workspacePath = workspaceField.text.toString().trim()
                            .ifEmpty { existing.workspacePath }
                    )
                } else {
                    PersonaProfile(
                        id = idField.text.toString().trim().lowercase(),
                        label = labelField.text.toString().trim(),
                        subtitle = subtitleField.text.toString().trim(),
                        accentColor = accentField.text.toString().trim(),
                        secondaryColor = secondaryField.text.toString().trim(),
                        workspacePath = workspaceField.text.toString().trim()
                    )
                }
                val updated = if (isCreate) {
                    config.copy(personas = config.personas + persona)
                } else {
                    config.copy(personas = config.personas.map { if (it.id == persona.id) persona else it })
                }
                onCommit(updated)
                Toast.makeText(activity, "Saved persona ${persona.id}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showBuiltinLabelDialog(
        config: LauncherConfig,
        builtin: PersonaProfile,
        onCommit: (LauncherConfig) -> Unit
    ) {
        val current = when (builtin.id) {
            "void" -> config.identityVoidLabel
            "main" -> config.identityMainLabel
            else -> config.identityStillLabel
        }
        val input = field("Badge label (e.g. [VOID])", current)
        val bDialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Badge label for ${builtin.id} (NUMERIC_BRACKET style only)")
            .setView(labeledColumn("Badge label" to input))
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        bDialog.setOnShowListener {
            bDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val label = input.text.toString().trim()
                if (label.isEmpty()) {
                    Toast.makeText(activity, "Not saved: label must not be empty", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val updated = when (builtin.id) {
                    "void" -> config.copy(identityVoidLabel = label)
                    "main" -> config.copy(identityMainLabel = label)
                    else -> config.copy(identityStillLabel = label)
                }
                onCommit(updated)
                bDialog.dismiss()
            }
        }
        bDialog.show()
    }

    private fun showPersonaDockDialog(
        config: LauncherConfig,
        persona: PersonaProfile,
        appsProvider: () -> List<AppEntry>,
        onCommit: (LauncherConfig) -> Unit
    ) {
        val override = persona.customDockSlots.orEmpty()
        val items = (1..7).map { index ->
            val slot = override.firstOrNull { it.index == index }
            val configSlot = config.dockSlots.firstOrNull { it.index == index }
            when {
                slot != null -> "[$index] ${slot.label} (persona override)"
                configSlot != null -> "[$index] ${configSlot.label} (config dock)"
                else -> "[$index] — empty —"
            }
        } + "↺ Clear all persona dock overrides"
        MaterialAlertDialogBuilder(activity)
            .setTitle("Dock slots for ${persona.label}")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 7) {
                    val cleared = persona.copy(customDockSlots = null)
                    onCommit(config.copy(personas = config.personas.map { if (it.id == persona.id) cleared else it }))
                    Toast.makeText(activity, "Persona dock reset to config dock", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                val index = which + 1
                val current = override.firstOrNull { it.index == index }
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Slot $index")
                    .setItems(arrayOf("Assign app", "Assign built-in action", "Rename", "Reset slot to config dock")) { _, action ->
                        when (action) {
                            0 -> {
                                val apps = appsProvider()
                                MaterialAlertDialogBuilder(activity)
                                    .setTitle("Assign app to slot $index")
                                    .setItems(apps.map { "${it.label} (${it.packageName})" }.toTypedArray()) { _, appIdx ->
                                        val app = apps[appIdx]
                                        saveSlot(config, persona, DockSlot(index, app.label, "", app.packageName, "launch_package"), onCommit)
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                            1 -> {
                                val actions = dockService.builtInActions
                                MaterialAlertDialogBuilder(activity)
                                    .setTitle("Assign built-in action to slot $index")
                                    .setItems(actions.map { "${it.first} — ${it.second}" }.toTypedArray()) { _, actIdx ->
                                        val a = actions[actIdx]
                                        saveSlot(config, persona, DockSlot(index, a.first, a.second, "", a.third), onCommit)
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                            2 -> {
                                val input = field("Slot label", current?.label ?: "")
                                val rDialog = MaterialAlertDialogBuilder(activity)
                                    .setTitle("Rename slot $index")
                                    .setView(labeledColumn("Label" to input))
                                    .setPositiveButton("Save", null)
                                    .setNegativeButton("Cancel", null)
                                    .create()
                                rDialog.setOnShowListener {
                                    rDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                        val label = input.text.toString().trim()
                                        if (label.isEmpty()) {
                                            Toast.makeText(activity, "Not saved: label must not be empty", Toast.LENGTH_LONG).show()
                                            return@setOnClickListener
                                        }
                                        val base = current ?: DockSlot(index, label, "", "", "launch_package")
                                        saveSlot(config, persona, base.copy(label = label), onCommit)
                                        rDialog.dismiss()
                                    }
                                }
                                rDialog.show()
                            }
                            3 -> {
                                val kept = override.filter { it.index != index }
                                val updatedPersona = persona.copy(customDockSlots = kept.ifEmpty { null })
                                onCommit(config.copy(personas = config.personas.map { if (it.id == persona.id) updatedPersona else it }))
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun saveSlot(
        config: LauncherConfig,
        persona: PersonaProfile,
        slot: DockSlot,
        onCommit: (LauncherConfig) -> Unit
    ) {
        val kept = persona.customDockSlots.orEmpty().filter { it.index != slot.index } + slot
        val updatedPersona = persona.copy(customDockSlots = kept.sortedBy { it.index })
        onCommit(config.copy(personas = config.personas.map { if (it.id == persona.id) updatedPersona else it }))
        Toast.makeText(activity, "Slot ${slot.index} set to ${slot.label}", Toast.LENGTH_SHORT).show()
    }
}
