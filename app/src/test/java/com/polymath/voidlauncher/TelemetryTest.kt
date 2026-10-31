package com.polymath.voidlauncher

import com.polymath.voidlauncher.model.TelemetrySnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryTest {

    @Test
    fun testThermalSafetyThreshold() {
        val safeSnapshot = TelemetrySnapshot(
            timestampFormatted = "17:30:00",
            batteryPercent = 50,
            batteryTemperatureC = 34.5f,
            isCharging = false,
            ramUsedMb = 4096,
            ramTotalMb = 12288,
            isThermalSafe = 34.5f <= 39.0f
        )
        assertTrue(safeSnapshot.isThermalSafe)

        val hotSnapshot = TelemetrySnapshot(
            timestampFormatted = "17:30:00",
            batteryPercent = 50,
            batteryTemperatureC = 41.2f,
            isCharging = true,
            ramUsedMb = 6000,
            ramTotalMb = 12288,
            isThermalSafe = 41.2f <= 39.0f
        )
        assertFalse(hotSnapshot.isThermalSafe)
    }
}
