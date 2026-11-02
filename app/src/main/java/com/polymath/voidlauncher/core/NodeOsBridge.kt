package com.polymath.voidlauncher.core

import android.content.Context
import com.polymath.voidlauncher.model.IntentStatus
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * NodeOS bridge.
 *
 * NodeOS legacy import is opt-in ([isLegacyImportEnabled] = false by default).
 * When enabled, reads `workflow.json` and queries `agy_nodeos.db` via [AstGraphRenderer]
 * on a background executor so the main thread never blocks.
 */
class NodeOsBridge(private val context: Context) {

    private val prefs = context.getSharedPreferences("void_launcher_prefs", Context.MODE_PRIVATE)
    private val astGraph = AstGraphRenderer()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val astQueryLock = Any()
    private val astCacheTtlMs = 5_000L

    @Volatile private var cachedAstNodes = 0
    @Volatile private var cachedAstAlive = false
    private var lastAstQueryMs = 0L

    companion object {
        const val PREF_IDENTITY_MODE = "pref_identity_mode"
        const val IDENTITY_VOID = "VOID"
        const val IDENTITY_MAIN = "MAIN"
    }

    var isLegacyImportEnabled: Boolean = false
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
        val gitBranch = "main"
        var astNodes = cachedAstNodes
        var daemonAlive = cachedAstAlive

        try {
            val workflowFile = File(workflowPath)
            if (workflowPath.isNotBlank() && workflowFile.exists() && workflowFile.canRead()) {
                val json = JSONObject(workflowFile.readText())
                activeTask = json.optString("active_task", "Sovereign Cockpit Ready")
                taskStatus = json.optString("status", "idle")
            }
        } catch (_: Exception) {}

        try {
            val dbFile = File(nodeosDbPath)
            if (nodeosDbPath.isNotBlank() && dbFile.exists()) {
                daemonAlive = true
                scheduleAstRefreshIfStale()
                if (cachedAstNodes > 0) {
                    astNodes = cachedAstNodes
                } else {
                    val sizeKb = dbFile.length() / 1024
                    astNodes = if (sizeKb > 0) (sizeKb / 3).toInt().coerceAtLeast(1) else 0
                }
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

    private fun scheduleAstRefreshIfStale() {
        val now = System.currentTimeMillis()
        synchronized(astQueryLock) {
            if (now - lastAstQueryMs < astCacheTtlMs) return
            lastAstQueryMs = now
        }
        val dbPath = nodeosDbPath
        if (dbPath.isBlank()) return
        ioExecutor.execute {
            try {
                val snapshot = astGraph.queryTopology(dbPath)
                if (snapshot.isAlive) {
                    cachedAstNodes = snapshot.totalNodes
                    cachedAstAlive = true
                }
            } catch (_: Exception) {}
        }
    }
}
