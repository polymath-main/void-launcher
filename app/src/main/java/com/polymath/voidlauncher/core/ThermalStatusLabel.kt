package com.polymath.voidlauncher.core

/**
 * Pure mapping for PowerManager thermal status codes (impl-05 step 6a).
 * Display-only: the OS thermal status is a coarse OS signal, NOT a sensor
 * reading, and it must NEVER become a guard input without a threshold
 * decision and a soak period (plan §5.8). Codes are the documented
 * PowerManager.THERMAL_STATUS_* values (API 29+); unknown codes map to
 * "UNKNOWN" rather than a guessed severity.
 */
object ThermalStatusLabel {
    fun fromCode(code: Int): String = when (code) {
        0 -> "NONE"
        1 -> "LIGHT"
        2 -> "MODERATE"
        3 -> "SEVERE"
        4 -> "CRITICAL"
        5 -> "EMERGENCY"
        6 -> "SHUTDOWN"
        else -> "UNKNOWN"
    }
}
