package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.shell.DefaultProcessRunner
import com.polymath.voidlauncher.core.shell.ProcessResult
import com.polymath.voidlauncher.core.shell.ProcessRunner
import com.polymath.voidlauncher.core.shell.ShellResult
import com.polymath.voidlauncher.core.shell.VoidShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoidShellTest {

    @Test
    fun runEchoReturnsOutput() {
        val shell = VoidShell(DefaultProcessRunner(), "/bin/sh", System.getProperty("user.dir") ?: ".")
        val result = shell.run("echo hello-void")
        assertTrue(result is ShellResult.Success)
        assertEquals("hello-void\n", (result as ShellResult.Success).output)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun blankCommandFailsWithoutRunning() {
        var called = false
        val runner = ProcessRunner { _, _, _ ->
            called = true
            ProcessResult("", "", 0)
        }
        val result = VoidShell(runner, "/bin/sh", ".").run("   ")
        assertTrue(result is ShellResult.Failed)
        assertTrue(!called)
    }

    @Test
    fun nonZeroExitIsFailedWithReason() {
        val shell = VoidShell(DefaultProcessRunner(), "/bin/sh", ".")
        val result = shell.run("exit 3")
        assertTrue(result is ShellResult.Failed)
        assertTrue((result as ShellResult.Failed).reason.contains("exit 3"))
    }

    @Test
    fun outputIsCapped() {
        val runner = ProcessRunner { _, _, _ ->
            ProcessResult("x".repeat(1000), "", 0)
        }
        val result = VoidShell(runner, "/bin/sh", ".", maxOutputChars = 20).run("echo x")
        assertTrue(result is ShellResult.Success)
        assertEquals(20, (result as ShellResult.Success).output.length)
    }

    @Test
    fun fakeRunnerPassesWorkDirThrough() {
        var seenWorkDir = ""
        val runner = ProcessRunner { _, _, workDir ->
            seenWorkDir = workDir
            ProcessResult("ok", "", 0)
        }
        val shell = VoidShell(runner, "/bin/sh", "/tmp")
        shell.run("echo ok", "/tmp/void-test")
        assertEquals("/tmp/void-test", seenWorkDir)
    }

    @Test
    fun defaultWorkDirIsUsedWhenNoneGiven() {
        var seenWorkDir = ""
        val runner = ProcessRunner { _, _, workDir ->
            seenWorkDir = workDir
            ProcessResult("ok", "", 0)
        }
        VoidShell(runner, "/bin/sh", "/tmp/default-dir").run("echo ok")
        assertEquals("/tmp/default-dir", seenWorkDir)
    }
}
