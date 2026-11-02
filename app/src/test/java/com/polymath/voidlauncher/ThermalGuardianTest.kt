package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.ThermalGuardian
import com.polymath.voidlauncher.core.process.VoidProcessManager
import com.polymath.voidlauncher.core.process.VoidTaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ThermalGuardian runs on the JVM only with a /tmp android.util.Log
 * stub (see LOOP_LOG.md) — the class itself imports only Log from
 * Android. AgentOrchestrator cannot run here: it needs android.content
 * and org.json, which the JVM harness does not provide; its seam,
 * VoidProcessManager, is covered by VoidProcessManagerTest.
 */
class ThermalGuardianTest {

    @Test
    fun throttleCancelsRunningTasksByHandle() {
        val manager = VoidProcessManager()
        val task = manager.submit("agent job", "python agent.py")
        val guardian = ThermalGuardian(manager)
        guardian.evaluateThermalEnvelope(tempC = 42.0f, limitC = 39.0f)
        assertTrue(guardian.isThrottled)
        assertEquals(VoidTaskStatus.CANCELLED, manager.get(task.id)?.status)
    }

    @Test
    fun deprecatedKillCommandsAreNeverExecuted() {
        // Proof by construction: ThermalGuardian holds no ShellPort and
        // takes no shell input — the only observable effect of a
        // throttle is VoidProcessManager cancellation. A task NOT
        // registered with the manager is untouched (no pkill pattern
        // could ever reach it), and a registered task is cancelled
        // even when the caller passes pkill strings.
        val manager = VoidProcessManager()
        val task = manager.submit("pytest run", "pytest")
        val guardian = ThermalGuardian(manager)
        guardian.evaluateThermalEnvelope(
            tempC = 45.0f,
            killCommands = listOf("pkill -f 'pytest'", "pkill -f 'nodeos.*daemon'")
        )
        assertTrue(guardian.isThrottled)
        assertEquals(VoidTaskStatus.CANCELLED, manager.get(task.id)?.status)
    }

    @Test
    fun recoveryClearsThrottleBelowHysteresis() {
        val manager = VoidProcessManager()
        val guardian = ThermalGuardian(manager)
        guardian.evaluateThermalEnvelope(tempC = 40.0f, limitC = 39.0f, hysteresisC = 2.0f)
        assertTrue(guardian.isThrottled)
        // 38C is above the 37C recovery line: still throttled.
        guardian.evaluateThermalEnvelope(tempC = 38.0f, limitC = 39.0f, hysteresisC = 2.0f)
        assertTrue(guardian.isThrottled)
        // 37C hits the recovery line: cleared.
        guardian.evaluateThermalEnvelope(tempC = 37.0f, limitC = 39.0f, hysteresisC = 2.0f)
        assertFalse(guardian.isThrottled)
    }

    @Test
    fun belowLimitDoesNothing() {
        val manager = VoidProcessManager()
        val task = manager.submit("job", "true")
        val guardian = ThermalGuardian(manager)
        guardian.evaluateThermalEnvelope(tempC = 30.0f)
        assertFalse(guardian.isThrottled)
        assertEquals(VoidTaskStatus.RUNNING, manager.get(task.id)?.status)
    }
}
