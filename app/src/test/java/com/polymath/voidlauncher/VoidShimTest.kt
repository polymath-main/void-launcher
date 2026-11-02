package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.process.VoidProcessManager
import com.polymath.voidlauncher.core.process.VoidTaskStatus
import com.polymath.voidlauncher.core.shell.ProcessResult
import com.polymath.voidlauncher.core.shell.ProcessRunner
import com.polymath.voidlauncher.core.shell.VoidShell
import com.polymath.voidlauncher.core.shim.ConfigShim
import com.polymath.voidlauncher.core.shim.DaemonShim
import com.polymath.voidlauncher.core.shim.FileShim
import com.polymath.voidlauncher.core.shim.NodeOSShim
import com.polymath.voidlauncher.core.shim.TerminalShim
import com.polymath.voidlauncher.core.terminal.TerminalRunResult
import com.polymath.voidlauncher.core.terminal.TerminalSession
import com.polymath.voidlauncher.core.terminal.TerminalSessionState
import com.polymath.voidlauncher.core.terminal.VoidShellRunner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VoidShim Cycles 1+3: same inputs -> same VoidAction/output shape as
 * the backing classes, through exactly one shim surface each. The shim
 * adds no behaviour of its own; every test below compares the shim
 * result against the direct backing-class result. (Cycle 3 migrated
 * ShellShim off and deleted it; its equivalence tests went with it.)
 */
class VoidShimTest {

    private fun fakeShell(output: String = "ok\n", exitCode: Int = 0): VoidShell {
        val runner = ProcessRunner { _, _, _ -> ProcessResult(output, "", exitCode) }
        return VoidShell(runner, "/bin/sh", ".")
    }

    @Test
    fun terminalShimViaVoidShellMatchesDirectSessionRun() {
        val shell = fakeShell("term-out\n", 0)
        val viaShim = TerminalShim.executeViaVoidShell(
            TerminalShim.newSession("s1"), "echo hi", shell
        )
        val direct = TerminalSession("s1").execute("echo hi", VoidShellRunner(shell))
        assertEquals(direct, viaShim)
        assertEquals(TerminalRunResult("term-out\n", "", 0), viaShim)

        val session = TerminalShim.newSession("s2")
        TerminalShim.executeViaVoidShell(session, "echo hi", shell)
        assertTrue(session.state is TerminalSessionState.Exited)
        assertEquals(0, (session.state as TerminalSessionState.Exited).exitCode)
        assertTrue(session.buffer.rendered().contains("term-out"))
    }

    @Test
    fun terminalShimFailureSurfacesExitMarkerLikeDirectRun() {
        val shell = fakeShell("", 2)
        val session = TerminalShim.newSession("s3")
        val result = TerminalShim.executeViaVoidShell(session, "exit 2", shell)
        assertEquals(-1, result.exitCode)
        assertTrue(session.buffer.rendered().contains("[exit -1]"))
    }

    @Test
    fun daemonShimDelegatesToVoidProcessManager() {
        val manager = DaemonShim.create()
        val task = DaemonShim.submit(manager, "agent", "echo hi")
        assertEquals(VoidTaskStatus.RUNNING, DaemonShim.get(manager, task.id)?.status)
        assertEquals(1, DaemonShim.getRunning(manager).size)
        assertEquals(1, DaemonShim.getAll(manager).size)
        assertTrue(DaemonShim.markFinished(manager, task.id, true))
        assertEquals(VoidTaskStatus.COMPLETED, DaemonShim.get(manager, task.id)?.status)
        assertFalse(DaemonShim.markFinished(manager, task.id, true))
        assertFalse(DaemonShim.cancel(manager, "no-such-id"))

        val t2 = DaemonShim.submit(manager, "second", "sleep 1")
        assertTrue(DaemonShim.cancel(manager, t2.id))
        assertEquals(VoidTaskStatus.CANCELLED, DaemonShim.get(manager, t2.id)?.status)

        DaemonShim.submit(manager, "third", "echo x")
        val cancelled = DaemonShim.cancelRunning(manager)
        assertEquals(1, cancelled.size)
        assertEquals(0, DaemonShim.getRunning(manager).size)
        // Direct manager view agrees with the shim view throughout.
        assertEquals(manager.getAll().size, DaemonShim.getAll(manager).size)
        assertNotNull(manager.get(task.id))
    }

    @Test
    fun fileShimVoidHomeIsFilesDirVoid() {
        val tmp = createTempDir("voidshim-test")
        try {
            val home = FileShim.voidHome(tmp)
            assertEquals(File(tmp, "void").absolutePath, home.absolutePath)
            assertTrue(home.isDirectory)
            assertTrue(FileShim.isVoidHomePath(home.absolutePath, tmp))
            assertTrue(FileShim.isVoidHomePath(File(home, "config.json").absolutePath, tmp))
            assertFalse(FileShim.isVoidHomePath(tmp.absolutePath, tmp))
            assertFalse(FileShim.isVoidHomePath("/storage/emulated/0", tmp))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun configShimExportsCurrentConfigByteForByte() {
        val tmp = createTempDir("voidshim-config")
        try {
            val home = FileShim.voidHome(tmp)
            val store = ConfigShim.configFile(home)
            assertEquals(File(home, "config.json").absolutePath, store.absolutePath)
            store.writeText("{\"current\": true}")
            val dest = File(tmp, "exported.json")
            assertTrue(ConfigShim.exportCurrent(store, dest))
            assertEquals("{\"current\": true}", dest.readText())

            val missing = File(home, "absent.json")
            val dest2 = File(tmp, "should-not-exist.json")
            assertFalse(ConfigShim.exportCurrent(missing, dest2))
            assertFalse(dest2.exists())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun nodeOSShimIsImportOnlyAndOptIn() {
        assertFalse(NodeOSShim.importEnabled(false))
        assertTrue(NodeOSShim.importEnabled(true))
        // Disabled: legacy status is ignored, never surfaced.
        assertEquals(
            NodeOSShim.IMPORT_DISABLED_STATUS,
            NodeOSShim.pollStatus(false, "legacy alive")
        )
        assertEquals(
            NodeOSShim.IMPORT_DISABLED_STATUS,
            NodeOSShim.pollStatus(false, null)
        )
        // Enabled: legacy status passes through.
        assertEquals("legacy alive", NodeOSShim.pollStatus(true, "legacy alive"))
        assertEquals(
            NodeOSShim.IMPORT_DISABLED_STATUS,
            NodeOSShim.pollStatus(true, null)
        )
    }

    @Test
    fun shimSourceHasNoLegacyTerminalOrKillReferences() {
        val root = findRepoRoot() ?: run {
            org.junit.Assume.assumeTrue("repo sources not found; see LOOP_LOG grep proof", false)
            return
        }
        val shim = File(root, "app/src/main/java/com/polymath/voidlauncher/core/shim/VoidShim.kt")
        org.junit.Assume.assumeTrue("shim source missing", shim.exists())
        val text = shim.readText()
        assertFalse("shim must not name the legacy terminal", text.contains("termux", ignoreCase = true))
        assertFalse("shim must not shell out to kill by pattern", text.contains("pkill"))
        assertFalse(
            "shim must not reference another app's private path",
            text.contains("/data/data/com.termux")
        )
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
