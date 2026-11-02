package com.polymath.voidlauncher.core.services

import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.voidlauncher.model.AppEntry

/**
 * Micro-service encapsulating application lifecycle actions:
 * Launch, App Info, Uninstall, Pin Home Shortcut, and Long-Press Context Menu.
 */
class AppActionsMicroService(private val activity: Activity) {

    fun showAppContextMenu(
        entry: AppEntry,
        onLaunch: (AppEntry) -> Unit,
        onAddToDock: (AppEntry) -> Unit
    ) {
        val options = arrayOf(
            "Open",
            "App info",
            "Uninstall",
            "Add to dock…",
            "Pin shortcut"
        )
        MaterialAlertDialogBuilder(activity)
            .setTitle(entry.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onLaunch(entry)
                    1 -> openAppInfo(entry)
                    2 -> uninstallApp(entry)
                    3 -> onAddToDock(entry)
                    4 -> pinShortcut(entry)
                }
            }
            .show()
    }

    fun launchApp(entry: AppEntry) {
        val intent = activity.packageManager.getLaunchIntentForPackage(entry.packageName)
        if (intent != null) {
            activity.startActivity(intent)
        } else {
            Toast.makeText(activity, "Cannot launch ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(entry: AppEntry) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", entry.packageName, null)
            )
            activity.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(activity, "Cannot open app info for ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun uninstallApp(entry: AppEntry) {
        try {
            val intent = Intent(
                Intent.ACTION_DELETE,
                Uri.fromParts("package", entry.packageName, null)
            )
            activity.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(activity, "Cannot uninstall ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun pinShortcut(entry: AppEntry) {
        val manager = activity.getSystemService(ShortcutManager::class.java)
        if (manager == null || !manager.isRequestPinShortcutSupported) {
            Toast.makeText(activity, "Pin shortcut is not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }
        val launchIntent = activity.packageManager.getLaunchIntentForPackage(entry.packageName)
        if (launchIntent == null) {
            Toast.makeText(activity, "Cannot pin ${entry.label}", Toast.LENGTH_SHORT).show()
            return
        }
        val drawable = entry.icon ?: try {
            activity.packageManager.getApplicationIcon(entry.packageName)
        } catch (_: Exception) {
            null
        }
        val shortcut = ShortcutInfo.Builder(activity, "pinned_${entry.packageName}")
            .setShortLabel(entry.label)
            .setIntent(launchIntent)
            .apply {
                drawable?.let { setIcon(drawableToIcon(it)) }
            }
            .build()
        try {
            manager.requestPinShortcut(shortcut, null)
        } catch (_: Exception) {
            Toast.makeText(activity, "Cannot pin ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawableToIcon(drawable: Drawable): Icon {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Icon.createWithBitmap(drawable.bitmap)
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return Icon.createWithBitmap(bitmap)
    }
}
