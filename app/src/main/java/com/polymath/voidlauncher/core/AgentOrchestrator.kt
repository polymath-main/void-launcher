package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.core.process.VoidProcessManager
import com.polymath.voidlauncher.core.process.VoidTaskStatus
import com.polymath.voidlauncher.core.shell.ShellPort

import android.content.Context
import android.util.Log
import com.polymath.voidlauncher.model.AgentTask
import org.json.JSONObject
import java.io.File

/**
 * Agent Task Orchestrator (F-08).
 *
 * Termux-removal Cycle 2: tasks are dispatched through the Void-owned
 * [ShellPort] (VoidShellPort on Android) and tracked by handle in
 * [VoidProcessManager]. Cancellation cancels by handle — it never
 * shells out to `pkill -f` with a command prefix, which could match
 * unrelated processes.
 *
 * [workflowPath] defaults to blank (no file sync) until the Activity
 * wires it to a Void-owned path; the old default pointed into another
 * app's private directory, which this app cannot reliably read/write.
 * Polling still reads an external workflow.json when one is configured
 * (e.g. a user-chosen shared-storage path), but dispatch no longer
 * depends on it.
 */
class AgentOrchestrator(
    private val context: Context,
    private val shellPort: ShellPort,
    private val processManager: VoidProcessManager = VoidProcessManager()
) {

    companion object {
        private const val TAG = "AgentOrchestrator"
    }

    /** Blank = no workflow.json sync. Set by the Activity to a Void-owned path. */
    var workflowPath: String = ""

    var onTaskDispatched: ((AgentTask) -> Unit)? = null
    var onTaskCompleted: ((AgentTask) -> Unit)? = null
    var onTaskFailed: ((AgentTask) -> Unit)? = null

    /**
     * Registers a task in VoidProcessManager, syncs it to workflow.json
     * when configured, and dispatches execution through VoidShell.
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

        // 1. Update workflow.json, only when a Void-owned path is configured.
        if (workflowPath.isNotBlank()) {
            try {
                val file = File(workflowPath)
                val parent = file.parentFile
                if (parent != null && !parent.exists()) parent.mkdirs()

                val json = JSONObject().apply {
                    put("id", task.id)
                    put("active_task", title)
                    put("command", command)
                    put("status", "running")
                    put("started_at", task.startedAt)
                }
                file.writeText(json.toString(2))
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing to workflow.json: ${e.message}")
            }
        }

        // 2. Dispatch command through the Void-owned shell.
        shellPort.dispatchCommand(command, inBackground = true)
        Log.i(TAG, "Dispatched agent task [${task.id}]: $title")

        onTaskDispatched?.invoke(task)
        return task
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
        return processManager.getAll().map { voidTask ->
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
                completedAt = voidTask.completedAt
            )
        }
    }

    /** Cancels a task by handle in VoidProcessManager; never shells out to pkill. */
    fun cancelTask(taskId: String) {
        val cancelled = processManager.cancel(taskId)
        if (!cancelled) return

        if (workflowPath.isNotBlank()) {
            try {
                val file = File(workflowPath)
                if (file.exists()) {
                    val json = JSONObject(file.readText())
                    json.put("status", "cancelled")
                    file.writeText(json.toString(2))
                }
            } catch (_: Exception) {}
        }
    }
}
