package com.polymath.voidlauncher.core

import android.content.Context
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.polymath.voidlauncher.model.VoidPaths
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Config Sync over the Void config store (F-12, migration Cycle 3).
 *
 * This replaces the old external-bridge sync: there is no broadcast
 * receiver and no other app involved. A FileObserver watches the
 * Void-owned config dir (VoidPaths.HOME) for config.json writes and
 * asks the Activity to hot-reload, so editing the file in any editor
 * still reloads the launcher without touching the screen.
 *
 * Import/export are stream-based so a Settings UI can drive them from
 * SAF document pickers (openInputStream/openOutputStream on the
 * user-chosen Uri) without this class knowing about SAF: import reads
 * and validates JSON, then atomically replaces the Void store file;
 * export writes the CURRENT store file, never defaults. The File
 * overloads are convenience wrappers for direct file destinations.
 * Nothing here ever writes defaults over an existing user config.
 */
class ConfigSyncService(
    private val context: Context,
    private val onReloadRequested: () -> Unit
) {

    companion object {
        private const val TAG = "ConfigSyncService"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var fileObserver: FileObserver? = null
    private var isRegistered = false

    fun register() {
        if (isRegistered) return
        isRegistered = true

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
        fileObserver?.stopWatching()
        fileObserver = null
        isRegistered = false
        Log.d(TAG, "ConfigSyncService unregistered")
    }

    /**
     * Imports a config from [input]: the text must parse as a JSON
     * object, otherwise the store is left untouched and false is
     * returned. On success the Void store file is replaced and a
     * reload is requested.
     */
    fun importConfig(input: InputStream): Boolean {
        return try {
            val text = input.bufferedReader().readText()
            JSONObject(text) // validation only; ConfigManager parses on load
            val store = File(VoidPaths.CONFIG)
            store.parentFile?.mkdirs()
            val tmp = File(store.parentFile, "config.json.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(store)) {
                store.writeText(text)
                tmp.delete()
            }
            notifyReload()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Config import failed, store untouched: ${e.message}")
            false
        }
    }

    fun importConfig(source: File): Boolean {
        if (!source.exists() || !source.canRead()) return false
        return try {
            source.inputStream().use { importConfig(it) }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Exports the CURRENT Void store config to [output]. Returns false
     * (and writes nothing) when no store file exists — it never
     * fabricates defaults to export.
     */
    fun exportConfig(output: OutputStream): Boolean {
        return try {
            val store = File(VoidPaths.CONFIG)
            if (!store.exists()) return false
            store.inputStream().use { input -> input.copyTo(output) }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Config export failed: ${e.message}")
            false
        }
    }

    fun exportConfig(dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            dest.outputStream().use { exportConfig(it) }
        } catch (_: Exception) {
            false
        }
    }

    private fun notifyReload() {
        mainHandler.post {
            onReloadRequested()
        }
    }
}
