package com.polymath.voidlauncher.core.services

import android.app.Activity
import android.content.Intent
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.voidlauncher.model.AppEntry
import com.polymath.voidlauncher.model.DockSlot
import com.polymath.voidlauncher.model.LauncherConfig
import com.polymath.voidlauncher.model.defaultDockSlots

/**
 * Micro-service managing interactive Bare-Integer Dock Slot [1..7] assignment,
 * app binding, built-in quick actions, renaming, and reset flows across
 * both Home and Settings screens.
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

    fun showSlotPickerForApp(
        entry: AppEntry,
        config: LauncherConfig,
        onCommit: (LauncherConfig) -> Unit
    ) {
        val slotLabels = (1..7).map { index ->
            val current = config.dockSlots.firstOrNull { it.index == index }
            "Slot $index — ${current?.label ?: "Empty"}"
        }.toTypedArray()

        MaterialAlertDialogBuilder(activity)
            .setTitle("Add ${entry.label} to dock")
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
            .show()
    }

    fun showEditDockSlotDialog(
        slotIndex: Int,
        config: LauncherConfig,
        appsProvider: () -> List<AppEntry>,
        onCommit: (LauncherConfig) -> Unit
    ) {
        val current = config.dockSlots.firstOrNull { it.index == slotIndex }
        val currentSummary = current?.let { "${it.label} (${it.action})" } ?: "Empty"
        val options = arrayOf(
            "📱 Assign Installed App…",
            "⚡ Assign Quick Action…",
            "✏️ Rename Slot Label…",
            "🔄 Reset Slot to Default"
        )
        MaterialAlertDialogBuilder(activity)
            .setTitle("Customize Dock Slot [$slotIndex] — $currentSummary")
            .setItems(options) { _, which ->
                when (which) {
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
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showAllSlotsManagerDialog(
        configProvider: () -> LauncherConfig,
        onCommit: (LauncherConfig) -> Unit
    ) {
        val cfg = configProvider()
        val slotLabels = (1..7).map { idx ->
            val slot = cfg.dockSlots.firstOrNull { it.index == idx }
            if (slot != null) {
                "[$idx] ${slot.label} — ${slot.subtitle.ifBlank { slot.action }}"
            } else {
                "[$idx] Empty"
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
        val apps = appsProvider().ifEmpty { queryInstalledAppsFallback() }.sortedBy { it.label.lowercase() }
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
                    label = chosen.label.uppercase(),
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
        val labels = builtInActions.map { "${it.first} — ${it.second}" }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle("Assign Action to Slot [$slotIndex]")
            .setItems(labels) { _, which ->
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
                .sortedBy { it.label.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
