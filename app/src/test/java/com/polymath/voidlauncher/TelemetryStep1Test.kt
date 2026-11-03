package com.polymath.voidlauncher

import com.polymath.voidlauncher.model.TelemetrySnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * impl-05 step 1: snapshot safety follows the configured limit, and an
 * unknown battery is never reported safe.
 */
class TelemetryStep1Test {

    private fun snap(pct: Int, temp: Float) = TelemetrySnapshot(
        timestampFormatted = "12:00:00",
        batteryPercent = pct,
        batteryTemperatureC = temp,
        isCharging = false,
        ramUsedMb = 3000,
        ramTotalMb = 12000,
        isThermalSafe = false
    )

    @Test
    fun safetyFollowsNonDefaultLimit() {
        // Limit 35: 36C is unsafe even though it is below the old 39 default.
        assertFalse(snap(50, 36.0f).isSafeAt(35.0f))
        assertTrue(snap(50, 34.0f).isSafeAt(35.0f))
    }

    @Test
    fun atLimitIsSafe() {
        assertTrue(snap(50, 39.0f).isSafeAt(39.0f))
        assertFalse(snap(50, 39.1f).isSafeAt(39.0f))
    }

    @Test
    fun unknownBatteryIsNeverSafe() {
        // Percent -1 (no battery intent) at 0.0C must NOT read as safe.
        assertFalse(snap(-1, 0.0f).isSafeAt(39.0f))
        assertFalse(snap(-1, 0.0f).isSafeAt(100.0f))
    }
}
