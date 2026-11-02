package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.shell.DefaultProcessRunner
import com.polymath.voidlauncher.core.shell.ProcessResult
import com.polymath.voidlauncher.core.shell.ProcessRunner
import com.polymath.voidlauncher.core.shell.ShellRunRecord
import com.polymath.voidlauncher.core.shell.VoidShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellObservabilityTest {

    @Test
    fun runEmitsRecordWithTimingAndExitButNoOutputText() {
        val records = mutableListOf<ShellRunRecord>()
        val runner = ProcessRunner { _, _, _ -> ProcessResult("hello", "warn", 0) }
        val shell = VoidShell(runner, "/bin/sh", ".", onRunRecord = { records.add(it) })
        shell.run("echo hello")
        assertEquals(1, records.size)
        val r = records[0]
        assertEquals("echo hello", r.command)
        assertEquals(0, r.exitCode)
        assertEquals(5, r.outputLength)
        assertEquals(4, r.errorLength)
        assertFalse(r.timedOut)
        assertTrue(r.durationMs >= 0)
        assertTrue(r.finishedAtMs >= r.startedAtMs)
    }

    @Test
    fun timeoutRecordIsFlagged() {
        val records = mutableListOf<ShellRunRecord>()
        val shell = VoidShell(
            DefaultProcessRunner(timeoutSeconds = 1), "/bin/sh", ".",
            onRunRecord = { records.add(it) }
        )
        shell.run("sleep 30")
        assertEquals(1, records.size)
        assertTrue(records[0].timedOut)
        assertEquals(-1, records[0].exitCode)
    }

    @Test
    fun blankCommandEmitsNoRecord() {
        val records = mutableListOf<ShellRunRecord>()
        val shell = VoidShell(ProcessRunner { _, _, _ -> ProcessResult("", "", 0) }, "/bin/sh", ".",
            onRunRecord = { records.add(it) })
        shell.run("   ")
        assertTrue(records.isEmpty())
    }

    @Test
    fun failingListenerDoesNotBreakRun() {
        val shell = VoidShell(ProcessRunner { _, _, _ -> ProcessResult("ok", "", 0) }, "/bin/sh", ".",
            onRunRecord = { throw RuntimeException("listener boom") })
        val result = shell.run("echo ok")
        assertTrue(result is com.polymath.voidlauncher.core.shell.ShellResult.Success)
    }

    @Test
    fun configClamps() {
        assertEquals(5L, VoidShell.clampTimeoutSeconds(0L))
        assertEquals(300L, VoidShell.clampTimeoutSeconds(9999L))
        assertEquals(30L, VoidShell.clampTimeoutSeconds(30L))
        assertEquals(1024, VoidShell.clampMaxOutputChars(10))
        assertEquals(262144, VoidShell.clampMaxOutputChars(1_000_000))
        assertEquals(16384, VoidShell.clampMaxOutputChars(16384))
    }
}
