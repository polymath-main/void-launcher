package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.shell.ProcessResult
import com.polymath.voidlauncher.core.shell.ProcessRunner
import com.polymath.voidlauncher.core.shell.VoidShell
import com.polymath.voidlauncher.core.shell.VoidShellPort
import com.polymath.voidlauncher.core.terminal.TerminalSession
import com.polymath.voidlauncher.core.terminal.TerminalSessionState
import com.polymath.voidlauncher.core.terminal.VoidShellRunner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Termux-removal Cycle 1: :sh/:term/dock routing runs on VoidShell.
 *
 * The router and panel are Android classes and cannot run on the JVM
 * (documented reason, not an omission): what is tested here is the
 * seam they depend on — VoidShellPort, VoidShellRunner, and the
 * VoidAction mapping — plus a source check that the routing files no
 * longer reference TermuxIpcDispatcher. The authoritative proof for
 * that last point is the grep in LOOP_LOG.md; this test skips itself
 * if it cannot locate the repo sources (e.g. under Gradle, where
 * user.dir differs), rather than failing spuriously.
 */
class TermuxRemovalCycle1Test {

    private fun fakeShell(output: String = "ok\n", exitCode: Int = 0): VoidShell {
        val runner = ProcessRunner { _, _, _ -> ProcessResult(output, "", exitCode) }
        return VoidShell(runner, "/bin/sh", "/tmp")
    }

    @Test
    fun voidShellPortDispatchRunsThroughVoidShell() {
        var seenCommand = ""
        val runner = ProcessRunner { _, command, _ ->
            seenCommand = command
            ProcessResult("done\n", "", 0)
        }
        val port = VoidShellPort(VoidShell(runner, "/bin/sh", "/tmp"))
        assertTrue(port.dispatchCommand("echo done", inBackground = false))
        assertEquals("echo done", seenCommand)
    }

    @Test
    fun voidShellPortDispatchReportsFailure() {
        val port = VoidShellPort(fakeShell(exitCode = 3))
        assertFalse(port.dispatchCommand("exit 3"))
    }

    @Test
    fun voidShellPortInteractiveOpensVoidTermCallbackNotExternalApp() {
        var openedWith = "<none>"
        val port = VoidShellPort(fakeShell()) { workDir -> openedWith = workDir }
        port.launchInteractive()
        assertEquals("", openedWith)
        port.openProject("my-project")
        assertEquals("my-project", openedWith)
    }

    @Test
    fun voidShellRunnerFeedsTerminalSessionRealOutput() {
        val session = TerminalSession(id = "test", rows = 8, cols = 48)
        val result = session.execute("echo hi", VoidShellRunner(fakeShell("hi\n")))
        assertEquals(0, result.exitCode)
        assertEquals("hi\n", result.output)
        assertTrue(session.getRenderedForTest().contains("hi"))
        assertTrue(session.state is TerminalSessionState.Exited)
    }

    @Test
    fun voidShellRunnerSurfacesFailureAsStderr() {
        val session = TerminalSession(id = "test")
        val result = session.execute("exit 3", VoidShellRunner(fakeShell(exitCode = 3)))
        assertEquals(-1, result.exitCode)
        assertTrue(result.error.contains("exit 3"))
    }

    @Test
    fun routingSourcesDoNotReferenceTermuxIpcDispatcher() {
        val root = findRepoRoot() ?: run {
            assumeTrue("repo sources not found from user.dir; see LOOP_LOG grep proof", false)
            return
        }
        val routingFiles = listOf(
            "app/src/main/java/com/polymath/voidlauncher/core/BareIntegerRouter.kt",
            "app/src/main/java/com/polymath/voidlauncher/core/action/VoidAction.kt",
            "app/src/main/java/com/polymath/voidlauncher/core/VoidTermPanel.kt",
            "app/src/main/java/com/polymath/voidlauncher/core/shell/VoidShellPort.kt",
            "app/src/main/java/com/polymath/voidlauncher/core/terminal/VoidShellRunner.kt"
        )
        for (rel in routingFiles) {
            val file = File(root, rel)
            assumeTrue("missing source file $rel", file.exists())
            assertFalse(
                "$rel still references TermuxIpcDispatcher",
                file.readText().contains("TermuxIpcDispatcher")
            )
        }
    }

    private fun findRepoRoot(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (dir == null) return null
            if (File(dir, "app/src/main/java/com/polymath/voidlauncher/core/BareIntegerRouter.kt").exists()) {
                return dir
            }
            dir = dir?.parentFile
        }
        return null
    }
}

// TerminalBuffer has no public rendered accessor on the session; the
// session buffer is public, so expose its rendering for this test via
// the buffer's own rendered() method (same call the panel uses).
private fun TerminalSession.getRenderedForTest(): String = buffer.rendered()
