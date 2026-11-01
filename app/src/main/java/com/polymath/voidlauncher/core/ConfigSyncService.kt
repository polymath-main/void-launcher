package com.polymath.voidlauncher.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * Config Sync over Termux Bridge (F-12).
 *
 * Listens for Termux broadcast triggers (`com.polymath.voidlauncher.CONFIG_CHANGED`)
 * and attaches a FileObserver on ~/.voidlauncher/config.json for real-time hot reload
 * without requiring the user to switch apps or touch the screen.
 */
class ConfigSyncService(
    private val context: Context,
    private val onReloadRequested: () -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "ConfigSyncService"
        const val ACTION_CONFIG_CHANGED = "com.polymath.voidlauncher.CONFIG_CHANGED"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var fileObserver: FileObserver? = null
    private var isRegistered = false

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_CONFIG_CHANGED) {
            Log.i(TAG, "Received CONFIG_CHANGED broadcast from external source (Termux)")
            notifyReload()
        }
    }

    fun register() {
        if (isRegistered) return

        // 1. Broadcast receiver
        val filter = IntentFilter(ACTION_CONFIG_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(this, filter)
        }
        isRegistered = true

        // 2. FileObserver on the shared-storage config dir (/sdcard/.voidlauncher)
        val dir = File(ConfigManager.EXTERNAL_CONFIG_DIR)
        try {
            if (!dir.exists()) dir.mkdirs()
        } catch (_: Exception) {}

        fileObserver = object : FileObserver(dir.absolutePath, CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null && path.endsWith("config.json")) {
                    Log.i(TAG, "FileObserver detected config.json modification: event=$event")
                    notifyReload()
                }
            }
        }.also {
            try {
                it.startWatching()
            } catch (e: Exception) {
                Log.w(TAG, "Cannot watch ${dir.absolutePath} (storage permission?): ${e.message}")
            }
        }

        Log.d(TAG, "ConfigSyncService registered and watching ${dir.absolutePath}")
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(this)
        } catch (_: Exception) {}

        fileObserver?.stopWatching()
        fileObserver = null
        isRegistered = false
        Log.d(TAG, "ConfigSyncService unregistered")
    }

    private fun notifyReload() {
        mainHandler.post {
            onReloadRequested()
        }
    }
}
