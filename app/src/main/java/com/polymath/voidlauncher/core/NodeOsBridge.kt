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

        const val DEFAULT_WORKFLOW_PATH = "/data/data/com.termux/files/home/Projects/workflow.json"
        const val DEFAULT_NODEOS_DB = "/data/data/com.termux/files/home/Projects/agy_nodeos.db"
    }

    fun getIdentityMode(): String {
        return prefs.getString(PREF_IDENTITY_MODE, IDENTITY_VOID) ?: IDENTITY_VOID
    }

    fun toggleIdentityMode(): String {
        val current = getIdentityMode()
        val next = if (current == IDENTITY_VOID) IDENTITY_MAIN else IDENTITY_VOID
        prefs.edit().putString(PREF_IDENTITY_MODE, next).apply()
        return next
    }

    /**
     * Reads current intent and AST status from host files if accessible,
     * or returns clean default operational status.
     */
    fun pollIntentStatus(): IntentStatus {
        var activeTask = "Sovereign Cockpit Ready"
        var taskStatus = "idle"
        var gitBranch = "main"
        var astNodes = 1428
        var daemonAlive = false

        // Check if workflow.json is accessible
        try {
            val workflowFile = File(DEFAULT_WORKFLOW_PATH)
            if (workflowFile.exists() && workflowFile.canRead()) {
                val content = workflowFile.readText()
                val json = JSONObject(content)
                activeTask = json.optString("active_task", "Sovereign Cockpit Ready")
                taskStatus = json.optString("status", "idle")
            }
        } catch (_: Exception) {}

        // Check agy_nodeos.db existence for daemon presence
        try {
            val dbFile = File(DEFAULT_NODEOS_DB)
            if (dbFile.exists()) {
                daemonAlive = true
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
