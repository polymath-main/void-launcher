package com.polymath.voidlauncher.core

import android.util.Log
import com.polymath.voidlauncher.core.process.VoidProcessManager

/**
 * Thermal Throttle Guardian (F-06).
 *
 * Actively protects the Nothing Phone (2a) Dimensity 7200 Pro from thermal saturation
 * during intensive local agent or ML compilation loops.
 *
 * Migration Cycle 2: throttling cancels Void-owned tasks by handle
 * through [VoidProcessManager]. It NEVER shells out to `pkill -f`
 * patterns any more. The [killCommands] parameter is kept only for
 * call-site compatibility (the Activity still passes
 * config.thermalKillCommands) and is deprecated: any supplied commands
 * are logged and ignored, never executed. The config field itself is
 * deprecated in LauncherConfig and is removed from the shipped default
 * config in Cycle 3.
 */
class ThermalGuardian(
    private val processManager: VoidProcessManager
) {

    companion object {
        private const val TAG = "ThermalGuardian"
    }

    var isThrottled: Boolean = false
        private set

    var currentTempC: Float = 0.0f
        private set

    var onThrottleTriggered: ((tempC: Float, summary: String) -> Unit)? = null
    var onThrottleCleared: ((tempC: Float) -> Unit)? = null

    /**
     * Ids cancelled by the most recent trip, and every id cancelled by any
     * trip since this guardian was created. Recorded so :tasks / :status can
     * surface thermally-cancelled tasks after recovery. Never auto-retried:
     * re-queue is manual only. Tasks dispatched outside VoidProcessManager
     * can never appear here — the guard can only stop what it registered.
     */
    var lastTripCancelledTaskIds: List<String> = emptyList()
        private set
    var thermallyCancelledTaskIds: List<String> = emptyList()
        private set

    /**
     * Inspects incoming telemetry temperature and enforces the thermal hysteresis envelope.
     *
     * @param killCommands DEPRECATED and ignored: shell kill commands are
     * never executed. Kept so existing call sites keep compiling.
     */
    @Deprecated("Shell kill commands are never executed; throttling cancels Void tasks by handle.")
    fun evaluateThermalEnvelope(
        tempC: Float,
        limitC: Float = 39.0f,
        hysteresisC: Float = 2.0f,
        killCommands: List<String> = emptyList()
    ) {
        currentTempC = tempC

        if (!isThrottled && tempC >= limitC) {
            isThrottled = true
            if (killCommands.isNotEmpty()) {
                Log.w(TAG, "Ignoring deprecated thermal_kill_commands (never executed as shell): $killCommands")
            }
            // cancelRunning() invokes the real CancelHandles attached by
            // impl-01/impl-04 dispatch (dispatchTracked), so a throttled
            // task's actual process stops — this is no longer registry-only
            // for registry-dispatched tasks. Tasks with no attached handle
            // are still marked CANCELLED only; nothing outside the registry
            // is ever killed (no pkill, no pattern match).
            val cancelled = processManager.cancelRunning()
            lastTripCancelledTaskIds = cancelled.map { it.id }
            thermallyCancelledTaskIds = thermallyCancelledTaskIds + lastTripCancelledTaskIds
            Log.w(TAG, "Thermal limit tripped (${tempC}°C >= ${limitC}°C). Cancelled ${cancelled.size} Void task(s) by handle.")

            val summary = "Throttled background workloads at ${tempC}°C (limit: ${limitC}°C, ${cancelled.size} task(s) cancelled)"
            onThrottleTriggered?.invoke(tempC, summary)
        } else if (isThrottled && tempC <= (limitC - hysteresisC)) {
            isThrottled = false
            Log.i(TAG, "Thermal recovered (${tempC}°C <= ${limitC - hysteresisC}°C). Throttle lifted.")
            onThrottleCleared?.invoke(tempC)
        }
    }

    /**
     * Clears the throttle. Fires [onThrottleCleared] ONLY if the guard was
     * actually throttled, so a reset and a recovery are both observable and
     * a reset of an unthrottled guard is a documented silent no-op (not a
     * phantom recovery). A task that finishes between "cancel requested"
     * and "destroy issued" lands in exactly one terminal state: cancel wins
     * if it arrives first (markFinished refuses a CANCELLED task), otherwise
     * the task completes and is never stopped twice (handles are dropped
     * on finish).
     */
    fun reset() {
        if (!isThrottled) return
        isThrottled = false
        onThrottleCleared?.invoke(currentTempC)
    }
}
