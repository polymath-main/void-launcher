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
        // impl-01 step 3: prefer the tracked dispatch so a cancel (agent
        // UI or thermal throttle) stops the real process via the
        // registry handle, not just a status flag. Fall back to the
        // Boolean dispatch when the executor has no process to hand out.
        // impl-06 step 2: prefer awaitResult() so outputPreview and
        // exitCode are REAL (taskOutcomeFromShellResult); only the
        // no-result fallback keeps synthesised 0/1, as before.
        io.execute {
            val outcome: TaskOutcome? = try {
                val tracked = shellPort.dispatchTracked(command, inBackground = true)
                if (tracked != null) {
                    processManager.attachHandle(voidTask.id) { tracked.cancel() }
                    try {
                        val result = tracked.awaitResult()
                        if (result != null) {
                            taskOutcomeFromShellResult(result)!!
                        } else {
                            val ok = tracked.awaitSuccess()
                            TaskOutcome(ok, "", if (ok) 0 else 1)
                        }
                    } catch (_: Exception) {
                        TaskOutcome(false, "", 1)
                    }
                } else {
                    val ok = shellPort.dispatchCommand(command, inBackground = true)
                    TaskOutcome(ok, "", if (ok) 0 else 1)
                }
            } catch (_: Exception) {
                TaskOutcome(false, "", 1)
            }
            main.post {
                finishTask(task.id, outcome)
            }
        }
        Log.i(TAG, "Dispatched agent task [${task.id}]: $title")

        onTaskDispatched?.invoke(task)
        return task
    }

    private fun finishTask(taskId: String, outcome: TaskOutcome) {
        val marked = processManager.markFinished(taskId, outcome.success)
        val existing = memoryTasks[taskId] ?: return
        if (!marked && existing.status != "running") return

        val updated = existing.copy(
            status = if (outcome.success) "completed" else "failed",
            completedAt = System.currentTimeMillis(),
            outputPreview = outcome.outputPreview,
            exitCode = outcome.exitCode
        )
        memoryTasks[taskId] = updated
        persistTasks()

        mergeWorkflowJson { json ->
            if (json.optString("id", "") == taskId) {
                json.put("status", updated.status)
                json.put("completed_at", updated.completedAt)
                json.put("exit_code", updated.exitCode)
                json.put("output_preview", updated.outputPreview)
            }
        }

        if (outcome.success) {
            onTaskCompleted?.invoke(updated)
        } else {
            onTaskFailed?.invoke(updated)
        }
    }

    // impl-06 step 2: pollWorkflow() DELETED (plan step 2.2). It had no
    // caller in the Activity; the HUD's legacy reader is NodeOsBridge
    // and its Void reader is VoidProcessManager. Keeping a second
    // reader with different semantics (prefer in-memory task for the
    // same id) was dead code posing as documentation. If a workflow
    // reader is ever needed again, it must agree with NodeOsBridge
    // first — not be added back as a parallel path.

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
                    // TIMED_OUT arrives in step 7 (task timeouts); mapped there.
                },
                startedAt = voidTask.startedAt,
                completedAt = voidTask.completedAt,
                outputPreview = mem?.outputPreview ?: "",
                exitCode = mem?.exitCode ?: 0,
                cancelReason = mem?.cancelReason ?: ""
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
                completedAt = System.currentTimeMillis(),
                cancelReason = "user"
            )
            persistTasks()
        }
        if (!cancelled && existing == null) return

        mergeWorkflowJson { json ->
            if (json.optString("id", "") == taskId || json.optString("id", "").isEmpty()) {
                json.put("status", "cancelled")
                json.put("cancel_reason", "user")
            }
        }
    }

    /**
     * impl-06 step 2, thermal-kill staleness (plan R4): ThermalGuardian
     * cancels via VoidProcessManager directly, so the registry already
     * says CANCELLED — but the orchestrator's memory copy, its persisted
     * store, and workflow.json did NOT, until this sync. The Activity
     * calls this from onThrottleTriggered with the guardian's
     * last-trip ids. No auto-retry: a thermally-cancelled task stays
     * cancelled until the user dispatches it again.
     */
    fun markExternalCancellation(taskIds: List<String>, reason: String) {
        var changed = false
        for (id in taskIds) {
            val existing = memoryTasks[id] ?: continue
            if (existing.status != "running") continue
            memoryTasks[id] = existing.copy(
                status = "cancelled",
                completedAt = System.currentTimeMillis(),
                cancelReason = reason
            )
            changed = true
        }
        if (!changed) return
        persistTasks()
        mergeWorkflowJson { json ->
            if (json.optString("id", "") in taskIds) {
                json.put("status", "cancelled")
                json.put("cancel_reason", reason)
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
                        put("cancel_reason", task.cancelReason)
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
                    exitCode = obj.optInt("exit_code", 0),
                    cancelReason = obj.optString("cancel_reason", "")
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
