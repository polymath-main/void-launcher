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
                astNodeCount = 0,
                isDaemonAlive = false,
                identityMode = getIdentityMode(),
                astProvenance = "none",
                workflowPresent = false
            )
        }

        // impl-06 step 1: the pure seam decides title / AST provenance;
        // this bridge only feeds it parsed facts. A missing workflow
        // file is "No workflow file", never the Sovereign fallback, and
        // an AST estimate is labelled as an estimate, never a count.
        var workflowExists = false
        var activeTask: String? = null
        var taskStatus: String? = null
        try {
            val workflowFile = File(workflowPath)
            if (workflowPath.isNotBlank() && workflowFile.exists() && workflowFile.canRead()) {
                workflowExists = true
                val json = JSONObject(workflowFile.readText())
                activeTask = json.optString("active_task", "").ifBlank { null }
                taskStatus = json.optString("status", "").ifBlank { null }
            }
        } catch (_: Exception) {}

        var dbExists = false
        var dbSizeKb = 0L
        var daemonAlive = cachedAstAlive
        try {
            val dbFile = File(nodeosDbPath)
            if (nodeosDbPath.isNotBlank() && dbFile.exists()) {
                dbExists = true
                dbSizeKb = dbFile.length() / 1024
                daemonAlive = true
                scheduleAstRefreshIfStale()
            }
        } catch (_: Exception) {}

        val resolved = resolveLegacyStatus(
            LegacyStatusInput(
                workflowExists = workflowExists,
                activeTask = activeTask,
                taskStatus = taskStatus,
                dbExists = dbExists,
                cachedNodes = cachedAstNodes,
                dbSizeKb = dbSizeKb
            )
        )

        return IntentStatus(
            activeTaskTitle = resolved.title,
            taskStatus = resolved.taskStatus,
            workspacePath = "~/Projects",
            astNodeCount = resolved.astCount,
            isDaemonAlive = daemonAlive,
            identityMode = getIdentityMode(),
            astProvenance = resolved.provenance.name.lowercase(),
            workflowPresent = resolved.workflowPresent
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
