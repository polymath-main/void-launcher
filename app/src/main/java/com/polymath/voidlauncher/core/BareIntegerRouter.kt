package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.core.shell.ShellPort

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.polymath.voidlauncher.model.DockSlot
import com.polymath.voidlauncher.model.defaultDockSlots

class BareIntegerRouter(
    private val context: Context,
    private val shellPort: ShellPort
) {

    private var slots: List<DockSlot> = defaultDockSlots()

    var onOpenFilesRequested: (() -> Unit)? = null
    var onOpenDrawerRequested: (() -> Unit)? = null
    var onOpenSettingsRequested: (() -> Unit)? = null

    fun updateSlots(newSlots: List<DockSlot>) {
        slots = newSlots
    }

    /**
     * Executes the configured action mapped to a digit (1..N).
     */
    fun routeInteger(digit: Int): Boolean {
        val slot = slots.firstOrNull { it.index == digit } ?: return false
        return executeSlotAction(slot)
    }

    /**
     * Directly executes an action for a given DockSlot.
     */
    fun routeSlot(slot: DockSlot): Boolean {
        return executeSlotAction(slot)
    }

    fun getSlots(): List<DockSlot> = slots

    private fun executeSlotAction(slot: DockSlot): Boolean {
        return when (slot.action) {
            "launch_termux" -> {
                shellPort.launchInteractive()
                true
            }
            "open_files" -> {
                onOpenFilesRequested?.invoke()
                true
            }
            "open_drawer" -> {
                onOpenDrawerRequested?.invoke()
                true
            }
            "open_settings" -> {
                onOpenSettingsRequested?.invoke()
                true
            }
            "launch_package" -> {
                launchPackage(slot.packageName)
                true
            }
            "open_browser" -> {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                true
            }
            "open_dialer" -> {
                val dialerIntent = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialerIntent)
                true
            }
            "termux_command" -> {
                shellPort.dispatchCommand(slot.packageName, inBackground = false)
                true
            }
            else -> {
                if (slot.packageName.isNotBlank()) {
                    launchPackage(slot.packageName)
                    true
                } else false
            }
        }
    }

    private fun launchPackage(pkgName: String) {
        if (pkgName.isBlank()) {
            Toast.makeText(context, "No package configured", Toast.LENGTH_SHORT).show()
            return
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkgName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            Toast.makeText(context, "Package not found: $pkgName", Toast.LENGTH_SHORT).show()
        }
    }
}
