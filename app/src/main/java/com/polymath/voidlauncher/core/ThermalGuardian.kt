package com.polymath.voidlauncher.core

import android.util.Log
import com.polymath.voidlauncher.core.process.VoidProcessManager

/**
 * Thermal Throttle Guardian (F-06).
 *
 * Actively protects the Nothing Phone (2a) Dimensity 7200 Pro from thermal saturation
 * during intensive local agent or ML compilation loops.
 *
 * Termux-removal Cycle 2: throttling cancels Void-owned tasks by handle
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
            val cancelled = processManager.cancelRunning()
            Log.w(TAG, "Thermal limit tripped (${tempC}°C >= ${limitC}°C). Cancelled ${cancelled.size} Void task(s) by handle.")

            val summary = "Throttled background workloads at ${tempC}°C (limit: ${limitC}°C, ${cancelled.size} task(s) cancelled)"
            onThrottleTriggered?.invoke(tempC, summary)
        } else if (isThrottled && tempC <= (limitC - hysteresisC)) {
            isThrottled = false
            Log.i(TAG, "Thermal recovered (${tempC}°C <= ${limitC - hysteresisC}°C). Throttle lifted.")
            onThrottleCleared?.invoke(tempC)
        }
    }

    fun reset() {
        isThrottled = false
    }
}
