package com.polymath.voidlauncher.core

import android.content.Context
import com.polymath.voidlauncher.model.IntentStatus
import org.json.JSONObject
import java.io.File

class NodeOsBridge(private val context: Context) {

    private val prefs = context.getSharedPreferences("void_launcher_prefs", Context.MODE_PRIVATE)

    companion object {
        const val PREF_IDENTITY_MODE = "pref_identity_mode"
        const val IDENTITY_VOID = "VOID"
        const val IDENTITY_MAIN = "MAIN"
    }

    /**
     * Termux-removal Cycle 2: NodeOS is legacy-import-only and optional.
     * [isLegacyImportEnabled] is false by default (fresh install): the
     * bridge then reports no NodeOS status at all, and the Activity
     * shows Void daemon / VoidTaskStore status instead of a broken
     * NodeOS card. Enable it only via the opt-in config flag
     * (nodeos_legacy_import_enabled) plus user-chosen paths; no
     * NodeOS installer exists in this repo and none is invented here.
     */
    var isLegacyImportEnabled: Boolean = false

    // Configurable paths — updated from LauncherConfig at runtime, but
    // only honoured when legacy import is enabled (see pollIntentStatus).
    // Defaults are blank: no path into another app's private directory.
    var workflowPath: String = ""
    var nodeosDbPath: String = ""

    fun getIdentityMode(): String {
        return prefs.getString(PREF_IDENTITY_MODE, IDENTITY_VOID) ?: IDENTITY_VOID
    }

    fun setDefaultIdentity(identity: String) {
        if (!prefs.contains(PREF_IDENTITY_MODE)) {
            prefs.edit().putString(PREF_IDENTITY_MODE, identity).apply()
        }
    }

    fun toggleIdentityMode(): String {
        val current = getIdentityMode()
        val next = if (current == IDENTITY_VOID) IDENTITY_MAIN else IDENTITY_VOID
        prefs.edit().putString(PREF_IDENTITY_MODE, next).apply()
        return next
    }

    /**
     * Reads current intent and AST status from legacy NodeOS host files
     * ONLY when legacy import is enabled. Otherwise returns a neutral
     * "not imported" status so the UI never shows a broken NodeOS card.
     */
    fun pollIntentStatus(): IntentStatus {
        if (!isLegacyImportEnabled) {
            return IntentStatus(
                activeTaskTitle = "NodeOS import not enabled (opt-in)",
                taskStatus = "idle",
                workspacePath = "Void home",
                gitBranch = "main",
                astNodeCount = 0,
                isDaemonAlive = false,
                identityMode = getIdentityMode()
            )
        }

        var activeTask = "Sovereign Cockpit Ready"
        var taskStatus = "idle"
        var gitBranch = "main"
        var astNodes = 0
        var daemonAlive = false

        try {
            val workflowFile = File(workflowPath)
            if (workflowPath.isNotBlank() && workflowFile.exists() && workflowFile.canRead()) {
                val content = workflowFile.readText()
                val json = JSONObject(content)
                activeTask = json.optString("active_task", "Sovereign Cockpit Ready")
                taskStatus = json.optString("status", "idle")
            }
        } catch (_: Exception) {}

        try {
            val dbFile = File(nodeosDbPath)
            if (nodeosDbPath.isNotBlank() && dbFile.exists()) {
                daemonAlive = true
                // Rough AST node count from file size heuristic
                val sizeKb = dbFile.length() / 1024
                astNodes = if (sizeKb > 0) (sizeKb / 3).toInt().coerceAtLeast(1) else 0
            }
        } catch (_: Exception) {}

        return IntentStatus(
            activeTaskTitle = activeTask,
            taskStatus = taskStatus,
            workspacePath = "~/Projects",
            gitBranch = gitBranch,
            astNodeCount = astNodes,
            isDaemonAlive = daemonAlive,
            identityMode = getIdentityMode()
        )
    }
}
