package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.shell.DefaultProcessRunner
import com.polymath.voidlauncher.core.shell.ShellResult
import com.polymath.voidlauncher.core.shell.VoidShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class VoidShellStreamingTest {

    private fun shell() = VoidShell(DefaultProcessRunner(), "/bin/sh", ".")

    @Test
    fun largeOutputCompletesBeforeTimeout() {
        // 200 KiB: the old waitFor-then-read code deadlocked on a full
        // pipe buffer until the 30 s timeout. Must finish in seconds.
        val started = System.currentTimeMillis()
        val result = shell().run("yes x | head -c 204800")
        val elapsed = System.currentTimeMillis() - started
        assertTrue("took ${elapsed}ms", elapsed < 15000)
        assertTrue(result is ShellResult.Success)
        // VoidShell caps at 16384 chars; the point is it completes, not
        // that the full 200 KiB is returned.
        assertEquals(16384, (result as ShellResult.Success).output.length)
    }

    @Test
    fun largeStderrDoesNotDeadlock() {
        val result = shell().run("yes errline | head -c 102400 >&2; exit 0")
        assertTrue(result is ShellResult.Success)
        assertTrue((result as ShellResult.Success).error.isNotEmpty())
    }

    @Test
    fun streamingDeliversLinesAsTheyArrive() {
        val lines = Collections.synchronizedList(mutableListOf<String>())
        val result = shell().runStreaming(
            "printf 'one\\ntwo\\n'; sleep 1; printf 'three\\n'",
            onStdoutLine = { lines.add(it) }
        )
        assertTrue(result is ShellResult.Success)
        assertEquals(listOf("one", "two", "three"), lines.toList())
    }

    @Test
    fun streamingSeparatesStderrLines() {
        val errLines = Collections.synchronizedList(mutableListOf<String>())
        shell().runStreaming("echo out; echo boom >&2", onStderrLine = { errLines.add(it) })
        assertEquals(listOf("boom"), errLines.toList())
    }

    @Test
    fun timeoutStillKillsAndReports() {
        val shell = VoidShell(DefaultProcessRunner(timeoutSeconds = 2), "/bin/sh", ".")
        val started = System.currentTimeMillis()
        val result = shell.run("sleep 30")
        val elapsed = System.currentTimeMillis() - started
        assertTrue(result is ShellResult.Failed)
        assertTrue((result as ShellResult.Failed).reason.contains("timed out"))
        assertTrue("took ${elapsed}ms", elapsed < 10000)
    }

    @Test
    fun outputWithoutTrailingNewlineIsPreserved() {
        val result = shell().run("printf hi")
        assertTrue(result is ShellResult.Success)
        assertEquals("hi", (result as ShellResult.Success).output)
    }
}
