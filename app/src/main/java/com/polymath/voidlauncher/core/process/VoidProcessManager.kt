package com.polymath.voidlauncher.core.process

import java.util.UUID

/**
 * VoidProcessManager — the Void-owned task registry (migration Cycle 2).
 *
 * Replaces external dispatch + `pkill -f` for agent tasks and thermal
 * throttling. Tasks are tracked by handle (an 8-character id), and
 * cancellation is by handle — never by shelling out to kill processes
 * by pattern, which could match unrelated processes.
 *
 * Pure Kotlin: no Android imports, so it is unit-testable on the JVM.
 * Note the honest limit: this registry tracks tasks the launcher
 * itself dispatched through VoidShell. It cannot kill a process it
 * never registered; a future VoidDaemon step owns that.
 */
enum class VoidTaskStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class VoidTask(
    val id: String,
    val title: String,
    val command: String,
    val status: VoidTaskStatus = VoidTaskStatus.RUNNING,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L
)

/**
 * A stop handle for a live task (impl-01 step 3). The registry holds
 * these so [VoidProcessManager.cancel] can stop a real OS process,
 * not just flip a status flag. Implementations must stop only the
 * process they started — never a name/pattern match.
 */
fun interface CancelHandle {
    fun cancel()
}

class VoidProcessManager {

    private val tasks = mutableMapOf<String, VoidTask>()
    private val handles = mutableMapOf<String, CancelHandle>()

    /** Registers a new running task and returns its handle. */
    fun submit(title: String, command: String): VoidTask {
        val task = VoidTask(
            id = UUID.randomUUID().toString().take(8),
            title = title,
            command = command
        )
        tasks[task.id] = task
        return task
    }

    fun get(taskId: String): VoidTask? = tasks[taskId]

    fun getAll(): List<VoidTask> =
        tasks.values.sortedByDescending { it.startedAt }

    fun getRunning(): List<VoidTask> =
        getAll().filter { it.status == VoidTaskStatus.RUNNING }

    /**
     * Attaches a live stop handle to a running task. Returns false if
     * the task is unknown or no longer running (handle not kept).
     * The handle is invoked exactly once, on cancel, and dropped on
     * finish — a finished task's process is never stopped twice.
     */
    fun attachHandle(taskId: String, handle: CancelHandle): Boolean {
        val task = tasks[taskId] ?: return false
        if (task.status != VoidTaskStatus.RUNNING) return false
        handles[taskId] = handle
        return true
    }

    /** Marks a task finished. Returns false if the id is unknown or already finished. */
    fun markFinished(taskId: String, success: Boolean): Boolean {
        val task = tasks[taskId] ?: return false
        if (task.status != VoidTaskStatus.RUNNING) return false
        handles.remove(taskId)
        tasks[taskId] = task.copy(
            status = if (success) VoidTaskStatus.COMPLETED else VoidTaskStatus.FAILED,
            completedAt = System.currentTimeMillis()
        )
        return true
    }

    /**
     * Cancels one task by handle. Returns false if unknown or already
     * finished. When a live handle is attached, it is invoked first so
     * the real process stops — this is what thermal throttling and
     * agent cancel now actually do (previously flag-only).
     */
    fun cancel(taskId: String): Boolean {
        val task = tasks[taskId] ?: return false
        if (task.status != VoidTaskStatus.RUNNING) return false
        val handle = handles.remove(taskId)
        try { handle?.cancel() } catch (_: Exception) { }
        tasks[taskId] = task.copy(
            status = VoidTaskStatus.CANCELLED,
            completedAt = System.currentTimeMillis()
        )
        return true
    }

    /** Cancels every running task by handle; returns the cancelled tasks. */
    fun cancelRunning(): List<VoidTask> {
        val running = getRunning()
        for (task in running) {
            val handle = handles.remove(task.id)
            try { handle?.cancel() } catch (_: Exception) { }
            tasks[task.id] = task.copy(
                status = VoidTaskStatus.CANCELLED,
                completedAt = System.currentTimeMillis()
            )
        }
        return running
    }
}
