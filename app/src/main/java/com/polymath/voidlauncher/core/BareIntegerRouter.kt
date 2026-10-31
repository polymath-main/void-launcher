package com.polymath.voidlauncher.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.polymath.voidlauncher.model.DockSlot
import com.polymath.voidlauncher.model.defaultDockSlots

class BareIntegerRouter(
    private val context: Context,
    private val termuxIpc: TermuxIpcDispatcher
) {

    private var slots: List<DockSlot> = defaultDockSlots()

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

    fun getSlots(): List<DockSlot> = slots

    private fun executeSlotAction(slot: DockSlot): Boolean {
        return when (slot.action) {
            "launch_termux" -> {
                termuxIpc.launchTermuxInteractive()
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
            "open_settings" -> {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                true
            }
            "termux_command" -> {
                // packageName field doubles as the command string
                termuxIpc.dispatchCommand(slot.packageName, inBackground = false)
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
