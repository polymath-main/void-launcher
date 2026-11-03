package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.AstProvenance
import com.polymath.voidlauncher.core.LegacyStatusInput
import com.polymath.voidlauncher.core.formatAstLine
import com.polymath.voidlauncher.core.resolveLegacyStatus
import com.polymath.voidlauncher.core.voidTaskLabel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** impl-06 step 1: HUD honesty — git lines, AST provenance, missing workflow. */
class AgentStep1Test {

    @Test
    fun missingWorkflowIsNotSovereignFallback() {
        val r = resolveLegacyStatus(
            LegacyStatusInput(false, "Some Task", "running", false, 0, 0)
        )
        assertEquals("No workflow file", r.title)
        assertEquals("idle", r.taskStatus)
        assertFalse(r.workflowPresent)
    }

    @Test
    fun existingWorkflowWithoutTaskKeepsFallback() {
        val r = resolveLegacyStatus(
            LegacyStatusInput(true, null, "idle", false, 0, 0)
        )
        assertEquals("Sovereign Cockpit Ready", r.title)
        assertTrue(r.workflowPresent)
    }

    @Test
    fun cachedNodesAreExactEstimateIsLabelled() {
        val exact = resolveLegacyStatus(
            LegacyStatusInput(true, "T", "running", true, 42, 900)
        )
        assertEquals(42, exact.astCount)
        assertEquals(AstProvenance.EXACT, exact.provenance)
        assertEquals("AST: 42 (exact)", formatAstLine(42, AstProvenance.EXACT))

        val est = resolveLegacyStatus(
            LegacyStatusInput(true, "T", "running", true, 0, 300)
        )
        assertEquals(100, est.astCount)
        assertEquals(AstProvenance.ESTIMATE, est.provenance)
        assertEquals("AST: ~100 (est.)", formatAstLine(100, AstProvenance.ESTIMATE))
        assertEquals("AST: —", formatAstLine(0, AstProvenance.NONE))
    }

    @Test
    fun voidModeLineIsTaskNotGit() {
        assertEquals("task: running", voidTaskLabel(true))
        assertEquals("task: idle", voidTaskLabel(false))
    }

    @Test
    fun activityHudShowsTaskAndProvenanceNotHardcodedBranch() {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val activity = File(root, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt")
        assumeTrue("activity missing", activity.exists())
        val text = activity.readText()
        // Void mode: task label, not "git: running|idle".
        assertTrue(text.contains("voidTaskLabel(latest != null)"))
        assertFalse(text.contains("\"git: \${if (latest != null)"))
        // Legacy mode: AST provenance formatting + git dash, no displayed gitBranch.
        assertTrue(text.contains("formatAstLine(status.astNodeCount"))
        assertTrue(text.contains("\"git: —\""))
        assertFalse(text.contains("binding.tvGitBranch.text = \"git: \${status.gitBranch}\""))
        // :kill gate (impl-04) still present alongside :task/:tasks gates.
        assertTrue(text.contains("[AGENT GATE]"))
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
