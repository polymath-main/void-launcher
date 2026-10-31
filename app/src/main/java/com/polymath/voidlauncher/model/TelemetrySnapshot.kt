package com.polymath.voidlauncher.model

data class TelemetrySnapshot(
    val timestampFormatted: String,
    val batteryPercent: Int,
    val batteryTemperatureC: Float,
    val isCharging: Boolean,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val isThermalSafe: Boolean // true if temp <= 39.0C
)
