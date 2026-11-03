package com.polymath.voidlauncher.core.services

import android.app.Activity
import android.content.Intent
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.voidlauncher.core.DockSlotData
import com.polymath.voidlauncher.core.DockSlotOps
import com.polymath.voidlauncher.core.ThemeEngine
import com.polymath.voidlauncher.model.AppEntry
import com.polymath.voidlauncher.model.DockStyle
import com.polymath.voidlauncher.model.DockSlot
import com.polymath.voidlauncher.model.LauncherConfig
import com.polymath.voidlauncher.model.defaultDockSlots
import java.util.Locale

/**
 * Micro-service managing interactive Bare-Integer Dock Slot [1..7] assignment,
 * app binding, built-in quick actions, renaming, and reset flows across
 * both Home and Settings screens, formatted according to the active [DockStyle].
 */
class DockSlotMicroService(private val activity: Activity) {

    private val builtInActions = listOf(
        Triple("TERMINAL", "In-App VoidShell", "open_voidterm"),
        Triple("FILES", "Void File Explorer", "open_files"),
        Triple("APPS DRAWER", "Installed Apps A-Z", "open_drawer"),
        Triple("BROWSER", "Web Search & URL", "open_browser"),
        Triple("PHONE", "Dialer & Contacts", "open_dialer"),
        Triple("SETTINGS", "Launcher Config", "open_settings")
    )

    /**
     * Formats a slot label & subtitle matching the active theme's [DockStyle].
     */
    fun formatSlotDisplay(
        slot: DockSlot,
        style: DockStyle = ThemeEngine.currentTheme.dockStyle
    ): Pair<String, String> {
        val label = when (style) {
            DockStyle.NUMERIC_BRACKET -> "[${slot.index}] ${slot.label}"
            DockStyle.ASCII_TTY -> "> 0${slot.index} ${slot.label.lowercase(Locale.US)}"
            DockStyle.EDITORIAL_SERIF -> "${slot.index} · ${slot.label.lowercase(Locale.US).replaceFirstChar { it.uppercase() }}"
            DockStyle.CHAMFERED_HUD -> "// 0${slot.index} // ${slot.label.uppercase(Locale.US)}"
            DockStyle.CAD_WIREFRAME -> "[0${slot.index}/07] ${slot.label.uppercase(Locale.US)}"
        }
        val sub = slot.subtitle.ifBlank { slot.action }
        return Pair(label, sub)
    }

    fun showSlotPickerForApp(
        entry: AppEntry,
        config: LauncherConfig,
        onCommit: (LauncherConfig) -> Unit
    ) {
        val style = ThemeEngine.currentTheme.dockStyle
        val slotLabels = (1..7).map { index ->
            val current = config.dockSlots.firstOrNull { it.index == index }
            if (current != null) {
                val formatted = formatSlotDisplay(current, style)
                "${formatted.first} — ${formatted.second}"
            } else {
                when (style) {
                    DockStyle.CAD_WIREFRAME -> "[0$index/07] EMPTY_SLOT"
                    DockStyle.CHAMFERED_HUD -> "// 0$index // (EMPTY)"
                    DockStyle.ASCII_TTY -> "> 0$index [empty]"
                    DockStyle.EDITORIAL_SERIF -> "$index · Available"
                    DockStyle.NUMERIC_BRACKET -> "Slot $index — Empty"
                }
            }
        }.toTypedArray()

        MaterialAlertDialogBuilder(activity)
            .setTitle("Add ${entry.label} to Dock")
            .setItems(slotLabels) { _, which ->
                val index = which + 1
                val newSlot = DockSlot(
                    index = index,
                    label = entry.label,
                    subtitle = entry.packageName,
                    packageName = entry.packageName,
                    action = "launch_package"
                )
                onCommit(upsertSlot(config, newSlot))
                Toast.makeText(activity, "${entry.label} added to dock slot $index", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showEditDockSlotDialog(
        slotIndex: Int,
        config: LauncherConfig,
        appsProvider: () -> List<AppEntry>,
        onCommit: (LauncherConfig) -> Unit
    ) {
        val current = config.dockSlots.firstOrNull { it.index == slotIndex }
        // Merge: origin's theme-formatted summary + impl-02 step 7's
        // (label, code) pairs, so Move/Edit-command can be appended
        // without shifting base option indices.
        val currentSummary = current?.let {
            val formatted = formatSlotDisplay(it)
            "${formatted.first} (${it.action})"
        } ?: "Empty"
        val options = mutableListOf(
            "📱 Assign Installed App…" to 0,
            "⚡ Assign Quick Action…" to 1,
            "✏️ Rename Slot Label…" to 2,
            "🔄 Reset Slot to Default" to 3
        )
        )
        if (current != null) options.add("↔️ Move to Slot…" to 4)
        if (current?.action == "run_void_command") options.add("⌨️ Edit Command…" to 5)
        MaterialAlertDialogBuilder(activity)
            .setTitle("Customize Dock Slot [$slotIndex] — $currentSummary")
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                when (options[which].second) {
                    0 -> showAssignAppToSlotDialog(slotIndex, config, appsProvider, onCommit)
                    1 -> showAssignActionToSlotDialog(slotIndex, config, appsProvider, onCommit)
                    2 -> showRenameSlotDialog(slotIndex, config, onCommit)
                    3 -> {
                        val defaultSlot = defaultDockSlots().firstOrNull { it.index == slotIndex }
                        if (defaultSlot != null) {
                            onCommit(upsertSlot(config, defaultSlot))
                            Toast.makeText(activity, "Slot [$slotIndex] reset to ${defaultSlot.label}", Toast.LENGTH_SHORT).show()
                        } else {
                            val newSlots = config.dockSlots.filterNot { it.index == slotIndex }
                            onCommit(config.copy(dockSlots = newSlots))
                            Toast.makeText(activity, "Slot [$slotIndex] cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                    4 -> showMoveSlotDialog(slotIndex, config, onCommit)
                    5 -> showCommandSlotDialog(slotIndex, config, onCommit, existing = current)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // impl-02 step 7: move/swap via the slot picker UI (no drag in v1).
    // An empty target moves the slot; an occupied target swaps the two.
    // The commit goes through the caller's onCommit, so home rebuilds
    // router + dock + favourites + keypad together, as always.
    fun showMoveSlotDialog(
        slotIndex: Int,
        config: LauncherConfig,
        onCommit: (LauncherConfig) -> Unit
    ) {
        val targets = (1..7).filter { it != slotIndex }
        val labels = targets.map { idx ->
            val occupant = config.dockSlots.firstOrNull { it.index == idx }
            "Slot $idx — ${occupant?.label ?: "Empty"}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle("Move Slot [$slotIndex] to…")
            .setItems(labels) { _, which ->
                val target = targets[which]
                val occupied = config.dockSlots.any { it.index == target }
                val data = config.dockSlots.map {
                    DockSlotData(it.index, it.label, it.subtitle, it.packageName, it.action)
                }
                val moved = DockSlotOps.moveOrSwap(data, slotIndex, target)
                val newSlots = moved.map {
                    DockSlot(it.index, it.label, it.subtitle, it.packageName, it.action)
                }
                onCommit(config.copy(dockSlots = newSlots))
                val verb = if (occupied) "swapped with" else "moved to"
                Toast.makeText(activity, "Slot [$slotIndex] $verb slot [$target]", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Back") { _, _ ->
                showEditDockSlotDialog(slotIndex, config, { emptyList() }, onCommit)
            }
            .show()
    }

    // impl-02 step 7: create/edit a run_void_command slot. The command
    // is stored in packageName, exactly as BareIntegerRouter reads it;
    // a blank command is refused (never committed), like an empty rename.
    fun showCommandSlotDialog(
        slotIndex: Int,
        config: LauncherConfig,
        onCommit: (LauncherConfig) -> Unit,
        existing: DockSlot? = null
    ) {
        val input = EditText(activity).apply {
            setText(existing?.packageName.orEmpty())
            setSelection(text.length)
            hint = "VoidShell command (e.g. ls ~/Projects)"
        }
        AlertDialog.Builder(activity)
            .setTitle(if (existing == null) "Run Command in Slot [$slotIndex]" else "Edit Command in Slot [$slotIndex]")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val commandSlot = DockSlotOps.makeCommandSlot(slotIndex, input.text.toString())
                if (commandSlot == null) {
                    Toast.makeText(activity, "Command cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    val slot = DockSlot(
                        commandSlot.index, commandSlot.label, commandSlot.subtitle,
                        commandSlot.packageName, commandSlot.action
                    )
                    onCommit(upsertSlot(config, slot))
                    Toast.makeText(activity, "Command slot [$slotIndex] saved", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Back") { _, _ ->
                showEditDockSlotDialog(slotIndex, config, { emptyList() }, onCommit)
            }
            .show()
    }

    fun showAllSlotsManagerDialog(
        configProvider: () -> LauncherConfig,
        onCommit: (LauncherConfig) -> Unit
    ) {
        val cfg = configProvider()
        val style = ThemeEngine.currentTheme.dockStyle
        val slotLabels = (1..7).map { idx ->
            val slot = cfg.dockSlots.firstOrNull { it.index == idx }
            if (slot != null) {
                val formatted = formatSlotDisplay(slot, style)
                "${formatted.first} — ${formatted.second}"
            } else {
                when (style) {
                    DockStyle.CAD_WIREFRAME -> "[0$idx/07] EMPTY"
                    DockStyle.CHAMFERED_HUD -> "// 0$idx // [EMPTY]"
                    DockStyle.ASCII_TTY -> "> 0$idx ---"
                    DockStyle.EDITORIAL_SERIF -> "$idx · Empty"
                    DockStyle.NUMERIC_BRACKET -> "[$idx] Empty"
                }
            }
        }.toTypedArray()

        MaterialAlertDialogBuilder(activity)
            .setTitle("Customize Dock Slots (1–7)")
            .setItems(slotLabels) { _, which ->
                showEditDockSlotDialog(
                    slotIndex = which + 1,
                    config = configProvider(),
                    appsProvider = { queryInstalledAppsFallback() },
                    onCommit = { updated ->
                        onCommit(updated)
                        showAllSlotsManagerDialog(configProvider, onCommit)
                    }
                )
            }
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showAssignAppToSlotDialog(
        slotIndex: Int,
        config: LauncherConfig,
        appsProvider: () -> List<AppEntry>,
        onCommit: (LauncherConfig) -> Unit
    ) {
        val apps = appsProvider().ifEmpty { queryInstalledAppsFallback() }.sortedBy { it.label.lowercase(Locale.US) }
        if (apps.isEmpty()) {
            Toast.makeText(activity, "App index is still loading…", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map { "${it.label} (${it.packageName})" }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle("Assign App to Slot [$slotIndex]")
            .setItems(labels) { _, which ->
                val chosen = apps[which]
                val newSlot = DockSlot(
                    index = slotIndex,
                    label = chosen.label.uppercase(Locale.US),
                    subtitle = chosen.packageName,
                    packageName = chosen.packageName,
                    action = "launch_package"
                )
                onCommit(upsertSlot(config, newSlot))
                Toast.makeText(activity, "Assigned ${chosen.label} to Slot [$slotIndex]", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Back") { _, _ ->
                showEditDockSlotDialog(slotIndex, config, appsProvider, onCommit)
            }
            .show()
    }

    private fun showAssignActionToSlotDialog(
        slotIndex: Int,
        config: LauncherConfig,
        appsProvider: () -> List<AppEntry>,
        onCommit: (LauncherConfig) -> Unit
    ) {
        // impl-02 step 7: the 7th item is not a built-in action — it
        // opens the command-text dialog (run_void_command slots cannot
        // be created from a fixed action list, they need typed input).
        val labels = (builtInActions.map { "${it.first} — ${it.second}" } +
            "⚡ Run Void Command… — Shell command in this slot").toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle("Assign Action to Slot [$slotIndex]")
            .setItems(labels) { _, which ->
                if (which >= builtInActions.size) {
                    showCommandSlotDialog(slotIndex, config, onCommit)
                    return@setItems
                }
                val chosen = builtInActions[which]
                val newSlot = DockSlot(
                    index = slotIndex,
                    label = chosen.first,
                    subtitle = chosen.second,
                    packageName = "",
                    action = chosen.third
                )
                onCommit(upsertSlot(config, newSlot))
                Toast.makeText(activity, "Assigned ${chosen.first} to Slot [$slotIndex]", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Back") { _, _ ->
                showEditDockSlotDialog(slotIndex, config, appsProvider, onCommit)
            }
            .show()
    }

    private fun showRenameSlotDialog(
        slotIndex: Int,
        config: LauncherConfig,
        onCommit: (LauncherConfig) -> Unit
    ) {
        val current = config.dockSlots.firstOrNull { it.index == slotIndex }
        if (current == null) {
            Toast.makeText(activity, "Assign an app or action to Slot [$slotIndex] first", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(activity).apply {
            setText(current.label)
            setSelection(current.label.length)
            hint = "Slot label"
        }
        AlertDialog.Builder(activity)
            .setTitle("Rename Slot [$slotIndex]")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newLabel = input.text.toString().trim()
                if (newLabel.isNotEmpty()) {
                    onCommit(upsertSlot(config, current.copy(label = newLabel)))
                    Toast.makeText(activity, "Slot [$slotIndex] renamed to $newLabel", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun upsertSlot(config: LauncherConfig, newSlot: DockSlot): LauncherConfig {
        val newSlots = config.dockSlots.toMutableList()
        val existingPos = newSlots.indexOfFirst { it.index == newSlot.index }
        if (existingPos >= 0) {
            newSlots[existingPos] = newSlot
        } else {
            newSlots.add(newSlot)
        }
        return config.copy(dockSlots = newSlots.sortedBy { it.index })
    }

    private fun queryInstalledAppsFallback(): List<AppEntry> {
        return try {
            val pm = activity.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(mainIntent, 0)
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    val actName = info.activityInfo?.name ?: ""
                    val label = info.loadLabel(pm).toString().ifBlank { pkg }
                    AppEntry(label = label, packageName = pkg, activityName = actName)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase(Locale.US) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
