package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.process.VoidProcessManager
import com.polymath.voidlauncher.core.process.VoidTaskStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * impl-06 step 7 — GATED PARTIAL. Timeout enforcement is real (pure
 * registry tests below); durable scheduling is NOT built, because the
 * gate failed: no WorkManager/AlarmManager exists in this app, and a
 * Handler-delay "scheduler" that dies with the process is forbidden by
 * the plan. scheduled_tasks.json, a daemon, and autonomous JS trigger
 * firing are therefore absent — deliberately, not by omission.
 */
class AgentStep7Test {

    @Test
    fun timeoutKillsByHandleAndMarksTimedOut() {
        val pm = VoidProcessManager()
        val task = pm.submit("slow", "sleep 60")
        var handleCalled = false
        assertTrue(pm.attachHandle(task.id) { handleCalled = true })
        assertTrue(pm.timeout(task.id))
        assertTrue(handleCalled)
        assertEquals(VoidTaskStatus.TIMED_OUT, pm.get(task.id)?.status)
    }

    @Test
    fun timeoutAfterFinishIsNoop() {
        val pm = VoidProcessManager()
        val task = pm.submit("fast", "true")
        assertTrue(pm.markFinished(task.id, true))
        assertFalse(pm.timeout(task.id))
        assertEquals(VoidTaskStatus.COMPLETED, pm.get(task.id)?.status)
    }

    @Test
    fun cancelAfterTimeoutIsNoop() {
        val pm = VoidProcessManager()
        val task = pm.submit("slow", "sleep 60")
        assertTrue(pm.timeout(task.id))
        assertFalse(pm.cancel(task.id))
        assertEquals(VoidTaskStatus.TIMED_OUT, pm.get(task.id)?.status)
    }

    @Test
    fun orchestratorWiresTimeoutAndNoDaemonExists() {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val orch = File(root, "app/src/main/java/com/polymath/voidlauncher/core/AgentOrchestrator.kt").readText()
        assertTrue(orch.contains("DEFAULT_TASK_TIMEOUT_SECONDS = 60L"))
        assertTrue(orch.contains("fun finishTaskTimedOut"))
        assertTrue(orch.contains("postDelayed({ finishTaskTimedOut"))
        assertTrue(orch.contains("VoidTaskStatus.TIMED_OUT -> \"timed_out\""))
        assertTrue(orch.contains("timeout_seconds"))
        val activity = File(root, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").readText()
        assertTrue(activity.contains("dispatchTask(title, cmd, config.agentTaskTimeoutSeconds)"))
        // Gate evidence: no durable scheduler, no scheduled-task store.
        val mainTree = File(root, "app/src/main/java/com/polymath/voidlauncher")
        val allMain = mainTree.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        // API usage, not prose: step 7's own comments legitimately
        // NAME WorkManager/AlarmManager while recording their absence.
        assertFalse("no WorkManager API use", allMain.contains("WorkManager.") || allMain.contains("androidx.work"))
        assertFalse("no AlarmManager API use", allMain.contains("AlarmManager."))
        assertFalse("no scheduled_tasks store", allMain.contains("scheduled_tasks"))
        val gradle = File(root, "app/build.gradle").readText()
        assertFalse("no workmanager dependency", gradle.contains("workmanager"))
    }

    private fun findRepoRoot(): File? {
        var dir: File? = File("").absoluteFile
        var hops = 0
        while (dir != null && hops < 6) {
            if (File(dir, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").exists()) return dir
            dir = dir.parentFile
            hops++
        }
        return null
    }
}
