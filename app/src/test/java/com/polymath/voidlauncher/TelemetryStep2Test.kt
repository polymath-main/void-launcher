package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.ThermalGuardian
import com.polymath.voidlauncher.core.process.VoidProcessManager
import com.polymath.voidlauncher.core.process.VoidTaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * impl-05 step 2: thermal cancellation invokes the real handle (not just a
 * status flag), records cancelled ids, and reset() has explicit callback
 * semantics. JVM tests use fake CancelHandles — descendant-killing of a
 * real OS process needs on-device verification and is NOT claimed here.
 */
class TelemetryStep2Test {

    @Test
    fun tripInvokesRealHandleExactlyOnceAndRecordsIds() {
        val manager = VoidProcessManager()
        val task = manager.submit("agent job", "sleep 60")
        var invokes = 0
        assertTrue(manager.attachHandle(task.id) { invokes++ })
        val guardian = ThermalGuardian(manager)
        guardian.evaluateThermalEnvelope(tempC = 42.0f, limitC = 39.0f)
        assertTrue(guardian.isThrottled)
        assertEquals(1, invokes)
        assertEquals(VoidTaskStatus.CANCELLED, manager.get(task.id)?.status)
        assertEquals(listOf(task.id), guardian.lastTripCancelledTaskIds)
        assertEquals(listOf(task.id), guardian.thermallyCancelledTaskIds)
        // A second trip while throttled must not re-invoke the handle.
        guardian.evaluateThermalEnvelope(tempC = 43.0f, limitC = 39.0f)
        assertEquals(1, invokes)
    }

    @Test
    fun tripWithZeroRunningTasksIsSafeAndEmpty() {
        val manager = VoidProcessManager()
        val guardian = ThermalGuardian(manager)
        guardian.evaluateThermalEnvelope(tempC = 42.0f, limitC = 39.0f)
        assertTrue(guardian.isThrottled)
        assertTrue(guardian.lastTripCancelledTaskIds.isEmpty())
        assertTrue(guardian.thermallyCancelledTaskIds.isEmpty())
    }

    @Test
    fun resetFiresClearedOnlyWhenThrottled() {
        val manager = VoidProcessManager()
        val guardian = ThermalGuardian(manager)
        var cleared = 0
        guardian.onThrottleCleared = { cleared++ }
        guardian.reset()
        assertEquals(0, cleared)
        guardian.evaluateThermalEnvelope(tempC = 42.0f, limitC = 39.0f)
        assertTrue(guardian.isThrottled)
        guardian.reset()
        assertFalse(guardian.isThrottled)
        assertEquals(1, cleared)
    }

    @Test
    fun recoveryFiresClearedExactlyOnce() {
        val manager = VoidProcessManager()
        val guardian = ThermalGuardian(manager)
        var cleared = 0
        guardian.onThrottleCleared = { cleared++ }
        guardian.evaluateThermalEnvelope(tempC = 42.0f, limitC = 39.0f)
        guardian.evaluateThermalEnvelope(tempC = 37.0f, limitC = 39.0f, hysteresisC = 2.0f)
        assertFalse(guardian.isThrottled)
        assertEquals(1, cleared)
        guardian.evaluateThermalEnvelope(tempC = 36.0f, limitC = 39.0f, hysteresisC = 2.0f)
        assertEquals(1, cleared)
    }
}
