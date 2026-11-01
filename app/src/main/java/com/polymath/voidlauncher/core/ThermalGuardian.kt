package com.polymath.voidlauncher.core

import android.util.Log

/**
 * Thermal Throttle Guardian (F-06).
 *
 * Actively protects the Nothing Phone (2a) Dimensity 7200 Pro from thermal saturation
 * during intensive local agent or ML compilation loops.
 */
class ThermalGuardian(
    private val termuxIpc: TermuxIpcDispatcher
) {

    companion object {
        private const val TAG = "ThermalGuardian"
        private val DEFAULT_KILL_COMMANDS = listOf(
            "pkill -f 'nodeos.*daemon'",
            "pkill -f 'python.*agent'",
            "pkill -f 'pytest'"
        )
    }

    var isThrottled: Boolean = false
        private set

    var currentTempC: Float = 0.0f
        private set

    var onThrottleTriggered: ((tempC: Float, summary: String) -> Unit)? = null
    var onThrottleCleared: ((tempC: Float) -> Unit)? = null

    /**
     * Inspects incoming telemetry temperature and enforces the thermal hysteresis envelope.
     */
    fun evaluateThermalEnvelope(
        tempC: Float,
        limitC: Float = 39.0f,
        hysteresisC: Float = 2.0f,
        killCommands: List<String> = emptyList()
    ) {
        currentTempC = tempC

        if (!isThrottled && tempC >= limitC) {
            isThrottled = true
            val targets = if (killCommands.isNotEmpty()) killCommands else DEFAULT_KILL_COMMANDS
            val combinedCmd = targets.joinToString(" ; ")

            Log.w(TAG, "Thermal limit tripped (${tempC}°C >= ${limitC}°C). Dispatching throttle: $combinedCmd")
            termuxIpc.dispatchCommand(combinedCmd, inBackground = true)

            val summary = "Throttled background workloads at ${tempC}°C (limit: ${limitC}°C)"
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
