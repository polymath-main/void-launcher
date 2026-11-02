package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.process.VoidProcessManager
import com.polymath.voidlauncher.core.process.VoidTaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoidProcessManagerTest {

    @Test
    fun submitRegistersRunningTaskWithHandle() {
        val manager = VoidProcessManager()
        val task = manager.submit("demo", "echo hi")
        assertEquals(8, task.id.length)
        assertEquals(VoidTaskStatus.RUNNING, task.status)
        assertEquals(task, manager.get(task.id))
    }

    @Test
    fun cancelByHandleCancelsOnlyThatTask() {
        val manager = VoidProcessManager()
        val first = manager.submit("one", "sleep 1")
        val second = manager.submit("two", "sleep 2")
        assertTrue(manager.cancel(first.id))
        assertEquals(VoidTaskStatus.CANCELLED, manager.get(first.id)?.status)
        assertEquals(VoidTaskStatus.RUNNING, manager.get(second.id)?.status)
        // Second cancel is a no-op: already finished.
        assertFalse(manager.cancel(first.id))
    }

    @Test
    fun cancelUnknownIdReturnsFalse() {
        assertFalse(VoidProcessManager().cancel("no-such-id"))
    }

    @Test
    fun cancelRunningCancelsAllRunningAndOnlyThose() {
        val manager = VoidProcessManager()
        val a = manager.submit("a", "sleep 1")
        val b = manager.submit("b", "sleep 2")
        manager.markFinished(a.id, success = true)
        val cancelled = manager.cancelRunning()
        assertEquals(listOf(b.id), cancelled.map { it.id })
        assertEquals(VoidTaskStatus.COMPLETED, manager.get(a.id)?.status)
        assertEquals(VoidTaskStatus.CANCELLED, manager.get(b.id)?.status)
        assertTrue(manager.getRunning().isEmpty())
    }

    @Test
    fun markFinishedTransitionsOnce() {
        val manager = VoidProcessManager()
        val task = manager.submit("job", "true")
        assertTrue(manager.markFinished(task.id, success = false))
        assertEquals(VoidTaskStatus.FAILED, manager.get(task.id)?.status)
        assertFalse(manager.markFinished(task.id, success = true))
    }

    @Test
    fun getAllIsNewestFirst() {
        val manager = VoidProcessManager()
        val first = manager.submit("first", "true")
        Thread.sleep(2)
        val second = manager.submit("second", "true")
        assertEquals(listOf(second.id, first.id), manager.getAll().map { it.id })
    }
}
