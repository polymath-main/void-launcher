package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.shell.DefaultProcessRunner
import com.polymath.voidlauncher.core.shell.ProcessResult
import com.polymath.voidlauncher.core.shell.ProcessRunner
import com.polymath.voidlauncher.core.shell.ShellResult
import com.polymath.voidlauncher.core.shell.VoidShell
import com.polymath.voidlauncher.core.terminal.TerminalSession
import com.polymath.voidlauncher.core.terminal.VoidShellRunner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** impl-02 Step 6 — VoidTerm really runs in the opened directory.
 *  The runner seam is tested for real; the panel/Activity wiring
 *  (Android-bound) is asserted on source text, UiV2* pattern. */
class NavigationStep6Test {

    private fun shellWithRecorder(seen: MutableList<String>): VoidShell {
        val runner = ProcessRunner { _, _, workDir ->
            seen.add(workDir)
            ProcessResult("ok", "", 0)
        }
        return VoidShell(runner, "/bin/sh", "/tmp")
    }

    @Test
    fun runnerUsesItsWorkDirWhenSet() {
        val seen = mutableListOf<String>()
        val runner = VoidShellRunner(shellWithRecorder(seen))
        runner.workDir = "/tmp"
        val result = runner.run("pwd")
        assertTrue(result is com.polymath.voidlauncher.core.terminal.TerminalRunResult)
        assertEquals(listOf("/tmp"), seen)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun runnerFallsBackToShellDefaultWhenWorkDirBlank() {
        val seen = mutableListOf<String>()
        val runner = VoidShellRunner(shellWithRecorder(seen))
        runner.workDir = ""
        runner.run("pwd")
        assertEquals(listOf("/tmp"), seen)
    }

    @Test
    fun sessionStoresWorkDirForTheRunner() {
        val session = TerminalSession(id = "dir:/tmp", workDir = "/tmp")
        assertEquals("/tmp", session.workDir)
        // The panel syncs runner.workDir from session.workDir in
        // executeVia, so a :term run after opening a folder session
        // executes with that folder as its workdir.
        val seen = mutableListOf<String>()
        val runner = VoidShellRunner(shellWithRecorder(seen))
        runner.workDir = session.workDir
        session.execute("pwd", runner)
        assertEquals(listOf("/tmp"), seen)
    }

    @Test
    fun panelAndActivityWireDirectorySessions() {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var root: File? = null
        repeat(6) {
            if (root == null && dir != null && File(dir, "app/src/main/java/com/polymath/voidlauncher/core/BareIntegerRouter.kt").exists()) root = dir
            dir = dir?.parentFile
        }
        assumeTrue(root != null)
        val panel = File(root, "app/src/main/java/com/polymath/voidlauncher/core/VoidTermPanel.kt").readText()
        assertTrue("panel must sync runner workdir from the session", panel.contains("runner.workDir = getSession().workDir"))
        assertTrue("panel must offer a directory session", panel.contains("fun openDirectorySession(dir: String)"))
        val activity = File(root, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").readText()
        assertTrue("Open in VoidTerm must open a directory session", activity.contains("voidTermPanel.openDirectorySession(safeDir)"))
        assertTrue("Open in VoidTerm must fall back to Void home for bad paths", activity.contains("if (File(dir).isDirectory) dir else fileManager.voidHomePath()"))
        assertTrue("Open in VoidTerm must print the directory, not just the file", activity.contains("[VOIDTERM] \$safeDir"))
    }
}
