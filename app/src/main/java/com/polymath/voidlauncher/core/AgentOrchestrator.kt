package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.core.shell.ShellPort

import android.content.Context
import android.util.Log
import com.polymath.voidlauncher.model.AgentTask
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Agent Task Orchestrator (F-08).
 *
 * Dispatches, monitors, and synchronizes autonomous agent workflows
 * through ~/Projects/workflow.json and Termux IPC execution.
 */
class AgentOrchestrator(
    private val context: Context,
    private val shellPort: ShellPort
) {

    companion object {
        private const val TAG = "AgentOrchestrator"
    }

    var workflowPath: String = "/data/data/com.termux/files/home/Projects/workflow.json"
    private val memoryTasks = mutableMapOf<String, AgentTask>()

    var onTaskDispatched: ((AgentTask) -> Unit)? = null
    var onTaskCompleted: ((AgentTask) -> Unit)? = null
    var onTaskFailed: ((AgentTask) -> Unit)? = null

    /**
     * Injects a new task into workflow.json and immediately dispatches execution via Termux.
     */
    fun dispatchTask(title: String, command: String): AgentTask {
        val taskId = UUID.randomUUID().toString().take(8)
        val task = AgentTask(
            id = taskId,
            title = title,
            command = command,
            status = "running",
            startedAt = System.currentTimeMillis()
        )
        memoryTasks[taskId] = task

        // 1. Update workflow.json
        try {
            val file = File(workflowPath)
            val parent = file.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()

            val json = JSONObject().apply {
                put("id", taskId)
                put("active_task", title)
                put("command", command)
                put("status", "running")
                put("started_at", task.startedAt)
            }
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing to workflow.json: ${e.message}")
        }

        // 2. Dispatch command to Termux
        shellPort.dispatchCommand(command, inBackground = true)
        Log.i(TAG, "Dispatched agent task [$taskId]: $title")

        onTaskDispatched?.invoke(task)
        return task
    }

    /**
     * Polls current workflow.json to inspect task state.
     */
    fun pollWorkflow(): AgentTask? {
        val file = File(workflowPath)
        if (!file.exists() || !file.canRead()) return null

        return try {
            val json = JSONObject(file.readText())
            val id = json.optString("id", "current")
            val title = json.optString("active_task", "Idle")
            val cmd = json.optString("command", "")
            val status = json.optString("status", "idle")
            val startedAt = json.optLong("started_at", System.currentTimeMillis())

            val task = AgentTask(
                id = id,
                title = title,
                command = cmd,
                status = status,
                startedAt = startedAt
            )
            memoryTasks[id] = task
            task
        } catch (e: Exception) {
            Log.w(TAG, "Error reading workflow.json: ${e.message}")
            null
        }
    }

    fun getAllTasks(): List<AgentTask> {
        return memoryTasks.values.sortedByDescending { it.startedAt }
    }

    fun cancelTask(taskId: String) {
        val task = memoryTasks[taskId] ?: return
        val updated = task.copy(status = "cancelled", completedAt = System.currentTimeMillis())
        memoryTasks[taskId] = updated

        // Kill process in Termux if running
        shellPort.dispatchCommand("pkill -f '${task.command.take(20)}'", inBackground = true)

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
