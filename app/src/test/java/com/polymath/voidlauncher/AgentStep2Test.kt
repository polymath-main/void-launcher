package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.TaskOutcome
import com.polymath.voidlauncher.core.shell.ShellResult
import com.polymath.voidlauncher.core.taskOutcomeFromShellResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** impl-06 step 2: real task output/exit codes, dead reader deleted. */
class AgentStep2Test {

    @Test
    fun successCarriesRealOutputAndExitCode() {
        val outcome = taskOutcomeFromShellResult(ShellResult.Success("hello\n", "warn\n", 0))
        assertTrue(outcome != null)
        assertTrue(outcome!!.success)
        assertEquals(0, outcome.exitCode)
        assertTrue(outcome.outputPreview.contains("hello"))
        assertTrue(outcome.outputPreview.contains("warn"))
    }

    @Test
    fun failedExitCodeIsParsedNotSynthesised() {
        val outcome = taskOutcomeFromShellResult(
            ShellResult.Failed("exit 3: boom | output: partial")
        )
        assertTrue(outcome != null)
        assertFalse(outcome!!.success)
        assertEquals(3, outcome.exitCode)
        assertEquals("partial", outcome.outputPreview)
    }

    @Test
    fun failedWithoutExitPrefixDefaultsToOne() {
        val outcome = taskOutcomeFromShellResult(ShellResult.Failed("something broke"))
        assertEquals(1, outcome!!.exitCode)
        assertEquals("something broke", outcome.outputPreview)
    }

    @Test
    fun nullResultMeansFallback() {
        assertNull(taskOutcomeFromShellResult(null))
    }

    @Test
    fun outputPreviewIsTruncated() {
        val outcome = taskOutcomeFromShellResult(ShellResult.Success("x".repeat(5000), "", 0))
        assertEquals(2000, outcome!!.outputPreview.length)
    }

    @Test
    fun pollWorkflowIsDeletedAndThermalSyncExists() {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val orch = File(root, "app/src/main/java/com/polymath/voidlauncher/core/AgentOrchestrator.kt")
        assumeTrue("orchestrator missing", orch.exists())
        val text = orch.readText()
        assertFalse("pollWorkflow must be deleted, not kept", text.contains("fun pollWorkflow"))
        assertTrue(text.contains("fun markExternalCancellation"))
        assertTrue(text.contains("awaitResult()"))
        assertTrue(text.contains("output_preview"))
        val activity = File(root, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt")
        val activityText = activity.readText()
        assertTrue(activityText.contains("markExternalCancellation"))
        assertTrue(activityText.contains("t.outputPreview"))
        assertTrue(activityText.contains("updateNodeOsHudView()"))
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
