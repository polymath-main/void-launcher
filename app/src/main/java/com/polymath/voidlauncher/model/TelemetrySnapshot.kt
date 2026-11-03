package com.polymath.voidlauncher.model

data class TelemetrySnapshot(
    val timestampFormatted: String,
    val batteryPercent: Int,
    val batteryTemperatureC: Float,
    val isCharging: Boolean,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val isThermalSafe: Boolean // true if battery known AND temp <= limit (see isSafeAt)
) {
    /**
     * Safety rule, shared by TelemetryMonitor.captureSnapshot(): a snapshot is
     * safe only when the battery is known (percent >= 0) and the battery
     * temperature is at or below the configured limit. An unknown battery
     * (percent -1, e.g. no battery intent) is NEVER reported safe — a missing
     * sensor must not read as "perfectly safe". The temperature here is the
     * BATTERY temperature from the sticky battery intent, a proxy for device
     * heat, not a SoC / thermal-zone reading (the launcher never reads
     * /sys/class/thermal and has no root path).
     */
    fun isSafeAt(limitC: Float): Boolean =
        batteryPercent >= 0 && batteryTemperatureC <= limitC
}
