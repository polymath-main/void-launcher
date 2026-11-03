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
import com.polymath.voidlauncher.core.ThemeEngine
import com.polymath.voidlauncher.model.AppEntry
import com.polymath.voidlauncher.model.DockStyle
import java.util.Locale

/**
 * Micro-service encapsulating application lifecycle actions:
 * Launch, App Info, Uninstall, Pin Home Shortcut, and the theme-styled
 * 5-action long-press context menu across the 5 dock styles:
 * [NUMERIC_BRACKET], [ASCII_TTY], [EDITORIAL_SERIF], [CHAMFERED_HUD], [CAD_WIREFRAME].
 */
class AppActionsMicroService(private val activity: Activity) {

    fun showAppContextMenu(
        entry: AppEntry,
        onLaunch: (AppEntry) -> Unit,
        onAddToDock: (AppEntry) -> Unit
    ) {
        val theme = ThemeEngine.currentTheme
        val style = theme.dockStyle

        val options = when (style) {
            DockStyle.ASCII_TTY -> arrayOf(
                "├── 01 EXECUTE (run)",
                "├── 02 INSPECT (app info)",
                "├── 03 PURGE (uninstall)",
                "├── 04 BIND_DOCK (add to dock)",
                "└── 05 PIN_NODE (pin shortcut)"
            )
            DockStyle.EDITORIAL_SERIF -> arrayOf(
                "I. Launch Application",
                "II. Application Details",
                "III. Remove from Device",
                "IV. Place on Home Dock",
                "V. Create Home Shortcut"
            )
            DockStyle.CHAMFERED_HUD -> arrayOf(
                "// 01.SYS // EXECUTE",
                "// 02.INF // APP_DETAILS",
                "// 03.DEL // UNINSTALL",
                "// 04.DCK // ADD_TO_DOCK",
                "// 05.PIN // PIN_SHORTCUT"
            )
            DockStyle.CAD_WIREFRAME -> arrayOf(
                "[01] EXEC_NODE",
                "[02] SPEC_INFO",
                "[03] DROP_PKG",
                "[04] ROUTE_DOCK",
                "[05] PIN_ROUTE"
            )
            DockStyle.NUMERIC_BRACKET -> arrayOf(
                "[1] Open",
                "[2] App Info",
                "[3] Uninstall",
                "[4] Add to Dock…",
                "[5] Pin Shortcut"
            )
        }

        val title = when (style) {
            DockStyle.ASCII_TTY -> "root@void:~# inspect ${entry.packageName}"
            DockStyle.EDITORIAL_SERIF -> entry.label
            DockStyle.CHAMFERED_HUD -> "[HUD] :: ${entry.label.uppercase(Locale.US)}"
            DockStyle.CAD_WIREFRAME -> "NODE_ID [${entry.label.uppercase(Locale.US)}]"
            DockStyle.NUMERIC_BRACKET -> entry.label
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onLaunch(entry)
                    1 -> openAppInfo(entry)
                    2 -> uninstallApp(entry)
                    3 -> onAddToDock(entry)
                    4 -> pinShortcut(entry)
                }
            }
            .setNegativeButton("Cancel", null)
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
