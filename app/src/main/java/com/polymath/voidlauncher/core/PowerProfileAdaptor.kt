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

    /**
     * impl-05 step 3: battery-unknown fall-through fixed. A missing battery
     * (percent < 0) is UNKNOWN, not BALANCED/PERFORMANCE — it classifies as
     * ECO (conservative: slower polling) even when charging. A 0% battery is
     * CRITICAL, not BALANCED. Order matters: unknown is checked first so
     * charging cannot promote an unknown battery to PERFORMANCE.
     */
    fun evaluate(snapshot: TelemetrySnapshot, thermalLimitC: Float = 39.0f): PowerLevel {
        val level = when {
            snapshot.batteryPercent < 0 -> {
                PowerLevel.ECO
            }
            snapshot.batteryTemperatureC >= thermalLimitC || (snapshot.batteryPercent in 0..14) -> {
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

    /**
     * impl-05 step 3: this gate is now actually called — see
     * VoidLauncherActivity.refreshWidgetDeckContent(), which skips SCRIPT
     * widget live refresh at CRITICAL (static descriptor content is still
     * shown; vitals and task lists are never gated). It must never gate
     * thermal evaluation or the telemetry flow itself.
     */
    fun shouldRenderNonEssentialWidgets(level: PowerLevel = currentLevel): Boolean {
        return level != PowerLevel.CRITICAL
    }
}
