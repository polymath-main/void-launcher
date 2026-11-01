package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.PowerProfileAdaptor
import com.polymath.voidlauncher.model.TelemetrySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class PowerProfileTest {

    private val adaptor = PowerProfileAdaptor()

    @Test
    fun testCriticalThermalLevel() {
        val snap = TelemetrySnapshot(
            timestampFormatted = "12:00:00",
            batteryPercent = 80,
            batteryTemperatureC = 40.5f,
            isCharging = false,
            ramUsedMb = 3000,
            ramTotalMb = 12000,
            isThermalSafe = false
        )
        val level = adaptor.evaluate(snap, 39.0f)
        assertEquals(PowerProfileAdaptor.PowerLevel.CRITICAL, level)
        assertEquals(8000L, adaptor.getRecommendedPollIntervalMs(level))
    }

    @Test
    fun testEcoBatteryLevel() {
        val snap = TelemetrySnapshot(
            timestampFormatted = "12:00:00",
            batteryPercent = 20,
            batteryTemperatureC = 35.0f,
            isCharging = false,
            ramUsedMb = 3000,
            ramTotalMb = 12000,
            isThermalSafe = true
        )
        val level = adaptor.evaluate(snap, 39.0f)
        assertEquals(PowerProfileAdaptor.PowerLevel.ECO, level)
        assertEquals(4000L, adaptor.getRecommendedPollIntervalMs(level))
    }

    @Test
    fun testPerformanceWhenCharging() {
        val snap = TelemetrySnapshot(
            timestampFormatted = "12:00:00",
            batteryPercent = 50,
            batteryTemperatureC = 36.0f,
            isCharging = true,
            ramUsedMb = 3000,
            ramTotalMb = 12000,
            isThermalSafe = true
        )
        val level = adaptor.evaluate(snap, 39.0f)
        assertEquals(PowerProfileAdaptor.PowerLevel.PERFORMANCE, level)
        assertEquals(1000L, adaptor.getRecommendedPollIntervalMs(level))
    }
}
