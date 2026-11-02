package com.polymath.voidlauncher.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.polymath.voidlauncher.core.process.VoidProcessManager
import com.polymath.voidlauncher.core.process.VoidTaskStatus
import com.polymath.voidlauncher.core.shell.ShellPort
import com.polymath.voidlauncher.model.AgentTask
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Agent Task Orchestrator (F-08).
 *
 * Dispatches autonomous agent workflows through the Void-owned [ShellPort] and tracks
 * their full lifecycle (`running -> completed | failed | cancelled`) via [VoidProcessManager].
 *
 * Tasks are executed asynchronously on a background worker pool and persisted to an
 * app-private JSON store so task history survives process death.
 */
class AgentOrchestrator(
    private val context: Context,
    private val shellPort: ShellPort,
    private val processManager: VoidProcessManager = VoidProcessManager()
) {

    companion object {
        private const val TAG = "AgentOrchestrator"
        private const val PERSIST_LIMIT = 50
    }

    /** Blank = no workflow.json sync. Set by the Activity to a Void-owned path. */
    var workflowPath: String = ""

    private val storeFile: File get() = File(context.filesDir, "void_tasks.json")
    private val memoryTasks = mutableMapOf<String, AgentTask>()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var onTaskDispatched: ((AgentTask) -> Unit)? = null
    var onTaskCompleted: ((AgentTask) -> Unit)? = null
    var onTaskFailed: ((AgentTask) -> Unit)? = null

    init {
        loadPersistedTasks()
    }

    /**
     * Registers a task in VoidProcessManager, syncs it to workflow.json
     * when configured, and dispatches execution through VoidShell off the UI thread.
     */
    fun dispatchTask(title: String, command: String): AgentTask {
        val voidTask = processManager.submit(title, command)
        val task = AgentTask(
            id = voidTask.id,
            title = title,
            command = command,
            status = "running",
            startedAt = voidTask.startedAt
        )
        memoryTasks[task.id] = task
        persistTasks()

        // 1. Update workflow.json (non-clobbering merge), only when a Void-owned path is configured.
        mergeWorkflowJson { json ->
            json.put("id", task.id)
            json.put("active_task", title)
            json.put("command", command)
            json.put("status", "running")
            json.put("started_at", task.startedAt)
        }

        // 2. Dispatch command asynchronously through the Void-owned shell.
        io.execute {
            val ok = try {
                shellPort.dispatchCommand(command, inBackground = true)
            } catch (_: Exception) {
                false
            }
            main.post {
                finishTask(task.id, ok)
            }
        }
        Log.i(TAG, "Dispatched agent task [${task.id}]: $title")

        onTaskDispatched?.invoke(task)
        return task
    }

    private fun finishTask(taskId: String, success: Boolean) {
        val marked = processManager.markFinished(taskId, success)
        val existing = memoryTasks[taskId] ?: return
        if (!marked && existing.status != "running") return

        val updated = existing.copy(
            status = if (success) "completed" else "failed",
            completedAt = System.currentTimeMillis(),
            exitCode = if (success) 0 else 1
        )
        memoryTasks[taskId] = updated
        persistTasks()

        mergeWorkflowJson { json ->
            if (json.optString("id", "") == taskId) {
                json.put("status", updated.status)
                json.put("completed_at", updated.completedAt)
                json.put("exit_code", updated.exitCode)
            }
        }

        if (success) {
            onTaskCompleted?.invoke(updated)
        } else {
            onTaskFailed?.invoke(updated)
        }
    }

    /**
     * Polls the configured workflow.json to inspect task state.
     * Returns null when no workflow path is configured or the file is unreadable.
     */
    fun pollWorkflow(): AgentTask? {
        if (workflowPath.isBlank()) return null
        val file = File(workflowPath)
        if (!file.exists() || !file.canRead()) return null

        return try {
            val json = JSONObject(file.readText())
            val id = json.optString("id", "current")
            val title = json.optString("active_task", "Idle")
            val cmd = json.optString("command", "")
            val status = json.optString("status", "idle")
            val startedAt = json.optLong("started_at", System.currentTimeMillis())

            val tracked = memoryTasks[id]
            if (tracked != null) return tracked

            AgentTask(
                id = id,
                title = title,
                command = cmd,
                status = status,
                startedAt = startedAt
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error reading workflow.json: ${e.message}")
            null
        }
    }

    fun getAllTasks(): List<AgentTask> {
        val live = processManager.getAll().map { voidTask ->
            val mem = memoryTasks[voidTask.id]
            AgentTask(
                id = voidTask.id,
                title = voidTask.title,
                command = voidTask.command,
                status = when (voidTask.status) {
                    VoidTaskStatus.RUNNING -> "running"
                    VoidTaskStatus.COMPLETED -> "completed"
                    VoidTaskStatus.FAILED -> "failed"
                    VoidTaskStatus.CANCELLED -> "cancelled"
                },
                startedAt = voidTask.startedAt,
                completedAt = voidTask.completedAt,
                outputPreview = mem?.outputPreview ?: "",
                exitCode = mem?.exitCode ?: 0
            )
        }
        val liveIds = live.map { it.id }.toSet()
        val persistedOnly = memoryTasks.values.filter { it.id !in liveIds }
        return (live + persistedOnly).sortedByDescending { it.startedAt }
    }

    /** Cancels a task by handle in VoidProcessManager; never shells out to pkill. */
    fun cancelTask(taskId: String) {
        val cancelled = processManager.cancel(taskId)
        val existing = memoryTasks[taskId]
        if (existing != null) {
            memoryTasks[taskId] = existing.copy(
                status = "cancelled",
                completedAt = System.currentTimeMillis()
            )
            persistTasks()
        }
        if (!cancelled && existing == null) return

        mergeWorkflowJson { json ->
            if (json.optString("id", "") == taskId || json.optString("id", "").isEmpty()) {
                json.put("status", "cancelled")
            }
        }
    }

    private fun mergeWorkflowJson(mutator: (JSONObject) -> Unit) {
        if (workflowPath.isBlank()) return
        try {
            val file = File(workflowPath)
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            val json = if (file.exists() && file.canRead()) {
                try {
                    JSONObject(file.readText())
                } catch (_: Exception) {
                    JSONObject()
                }
            } else {
                JSONObject()
            }
            mutator(json)
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing to workflow.json: ${e.message}")
        }
    }

    private fun persistTasks() {
        try {
            val array = JSONArray()
            for (task in getAllTasks().take(PERSIST_LIMIT)) {
                array.put(
                    JSONObject().apply {
                        put("id", task.id)
                        put("title", task.title)
                        put("command", task.command)
                        put("status", task.status)
                        put("started_at", task.startedAt)
                        put("completed_at", task.completedAt)
                        put("exit_code", task.exitCode)
                        put("output_preview", task.outputPreview)
                    }
                )
            }
            storeFile.writeText(array.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed persisting task store: ${e.message}")
        }
    }

    private fun loadPersistedTasks() {
        try {
            if (!storeFile.exists() || !storeFile.canRead()) return
            val array = JSONArray(storeFile.readText())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val task = AgentTask(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    command = obj.optString("command"),
                    status = obj.optString("status", "completed"),
                    startedAt = obj.optLong("started_at", 0L),
                    completedAt = obj.optLong("completed_at", 0L),
                    outputPreview = obj.optString("output_preview", ""),
                    exitCode = obj.optInt("exit_code", 0)
                )
                val reconciled = if (task.status == "running") {
                    task.copy(status = "interrupted", completedAt = System.currentTimeMillis())
                } else {
                    task
                }
                memoryTasks[reconciled.id] = reconciled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading task store: ${e.message}")
        }
    }
}
