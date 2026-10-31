package com.polymath.voidlauncher.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

class BareIntegerRouter(
    private val context: Context,
    private val termuxIpc: TermuxIpcDispatcher
) {

    companion object {
        const val PKG_POLYMATH_FS = "com.aistudio.filesystem.pmfsxz"
        const val PKG_TERMUX = "com.termux"
    }

    /**
     * Executes the target action mapped to a single integer digit (1..5).
     */
    fun routeInteger(slot: Int): Boolean {
        return when (slot) {
            1 -> {
                // [1] Terminal (Termux / VoidTerm)
                termuxIpc.launchTermuxInteractive()
                true
            }
            2 -> {
                // [2] Polymath Filesystem
                launchPackageOrFallback(
                    PKG_POLYMATH_FS,
                    fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("content://media/external/file")
                    }
                )
                true
            }
            3 -> {
                // [3] Browser (Web-Auth & Links)
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                true
            }
            4 -> {
                // [4] Comms (Dialer)
                val dialerIntent = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialerIntent)
                true
            }
            5 -> {
                // [5] System Settings
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                true
            }
            else -> false
        }
    }

    private fun launchPackageOrFallback(pkgName: String, fallbackIntent: Intent? = null) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkgName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else if (fallbackIntent != null) {
            try {
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Target package not found: $pkgName", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Package not found: $pkgName", Toast.LENGTH_SHORT).show()
        }
    }
}
