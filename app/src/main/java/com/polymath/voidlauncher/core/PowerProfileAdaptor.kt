package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.model.TelemetrySnapshot

/**
 * Adaptive Power Profile (F-13).
 *
 * Dynamically scales UI density and telemetry polling intervals
 * based on battery charge and thermal pressure.
 */
class PowerProfileAdaptor {

    enum class PowerLevel(val label: String) {
        PERFORMANCE("[PERF]"),
        BALANCED("[BAL]"),
        ECO("[ECO]"),
        CRITICAL("[CRIT]")
    }

    var currentLevel: PowerLevel = PowerLevel.BALANCED
        private set

    fun evaluate(snapshot: TelemetrySnapshot, thermalLimitC: Float = 39.0f): PowerLevel {
        val level = when {
            snapshot.batteryTemperatureC >= thermalLimitC || (snapshot.batteryPercent in 1..14) -> {
                PowerLevel.CRITICAL
            }
            snapshot.batteryPercent in 15..30 -> {
                PowerLevel.ECO
            }
            snapshot.isCharging || snapshot.batteryPercent > 70 -> {
                PowerLevel.PERFORMANCE
            }
            else -> {
                PowerLevel.BALANCED
            }
        }
        currentLevel = level
        return level
    }

    fun getRecommendedPollIntervalMs(level: PowerLevel = currentLevel): Long {
        return when (level) {
            PowerLevel.PERFORMANCE -> 1000L
            PowerLevel.BALANCED    -> 1500L
            PowerLevel.ECO         -> 4000L
            PowerLevel.CRITICAL    -> 8000L
        }
    }

    fun shouldRenderNonEssentialWidgets(level: PowerLevel = currentLevel): Boolean {
        return level != PowerLevel.CRITICAL
    }
}
