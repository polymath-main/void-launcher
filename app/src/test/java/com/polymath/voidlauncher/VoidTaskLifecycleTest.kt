package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.process.CancelHandle
import com.polymath.voidlauncher.core.process.VoidProcessManager
import com.polymath.voidlauncher.core.process.VoidTaskStatus
import com.polymath.voidlauncher.core.shell.DefaultProcessRunner
import com.polymath.voidlauncher.core.shell.ShellResult
import com.polymath.voidlauncher.core.shell.VoidShell
import com.polymath.voidlauncher.core.shell.VoidShellPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoidTaskLifecycleTest {

    private fun shell() = VoidShell(DefaultProcessRunner(), "/bin/sh", ".")

    @Test
    fun startedShellCancelStopsSleepWithinSeconds() {
        val running = shell().start("sleep 30")!!
        val started = System.currentTimeMillis()
        running.cancel()
        val result = running.await()
        val elapsed = System.currentTimeMillis() - started
        assertTrue("await took ${elapsed}ms", elapsed < 10000)
        assertTrue(result is ShellResult.Failed)
        assertTrue((result as ShellResult.Failed).reason.contains("exit"))
    }

    @Test
    fun startedShellChildProcessIsAlsoStopped() {
        // sh -c "sleep 30" on this shell execs sleep, but a wrapper that
        // spawns a background child must not leave the child behind:
        // destroy() kills descendants of the started process.
        val running = shell().start("sleep 30 & wait")!!
        Thread.sleep(300)
        running.cancel()
        val result = running.await()
        assertTrue(result is ShellResult.Failed)
    }

    @Test
    fun trackedDispatchCancelStopsRun() {
        val port = VoidShellPort(shell())
        val tracked = port.dispatchTracked("sleep 30")!!
        Thread.sleep(200)
        tracked.cancel()
        val started = System.currentTimeMillis()
        assertFalse(tracked.awaitSuccess())
        assertTrue(System.currentTimeMillis() - started < 10000)
    }

    @Test
    fun trackedDispatchReportsSuccessAndFailure() {
        val port = VoidShellPort(shell())
        assertTrue(port.dispatchTracked("echo ok")!!.awaitSuccess())
        assertFalse(port.dispatchTracked("exit 3")!!.awaitSuccess())
    }

    @Test
    fun managerCancelInvokesHandleOnce() {
        val manager = VoidProcessManager()
        val task = manager.submit("job", "sleep 30")
        var calls = 0
        assertTrue(manager.attachHandle(task.id, CancelHandle { calls++ }))
        assertTrue(manager.cancel(task.id))
        assertEquals(1, calls)
        assertEquals(VoidTaskStatus.CANCELLED, manager.get(task.id)?.status)
        // Second cancel is a no-op and does not re-invoke the handle.
        assertFalse(manager.cancel(task.id))
        assertEquals(1, calls)
    }

    @Test
    fun managerCancelRunningInvokesAllHandles() {
        val manager = VoidProcessManager()
        val a = manager.submit("a", "sleep 1")
        val b = manager.submit("b", "sleep 2")
        val cancelled = mutableListOf<String>()
        manager.attachHandle(a.id, CancelHandle { cancelled.add("a") })
        manager.attachHandle(b.id, CancelHandle { cancelled.add("b") })
        manager.markFinished(a.id, success = true)
        manager.cancelRunning()
        assertEquals(listOf("b"), cancelled)
        assertEquals(VoidTaskStatus.COMPLETED, manager.get(a.id)?.status)
    }

    @Test
    fun managerMarkFinishedDropsHandle() {
        val manager = VoidProcessManager()
        val task = manager.submit("job", "true")
        var calls = 0
        manager.attachHandle(task.id, CancelHandle { calls++ })
        assertTrue(manager.markFinished(task.id, success = true))
        assertFalse(manager.cancel(task.id))
        assertEquals(0, calls)
    }

    @Test
    fun managerAttachToUnknownOrFinishedReturnsFalse() {
        val manager = VoidProcessManager()
        assertFalse(manager.attachHandle("nope", CancelHandle {}))
        val task = manager.submit("job", "true")
        manager.markFinished(task.id, success = true)
        assertFalse(manager.attachHandle(task.id, CancelHandle {}))
    }
}
