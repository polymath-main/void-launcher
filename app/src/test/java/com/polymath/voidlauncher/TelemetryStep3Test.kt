package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.PowerProfileAdaptor
import com.polymath.voidlauncher.model.TelemetrySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** impl-05 step 3: power-profile boundary tests, including battery-unknown. */
class TelemetryStep3Test {

    private val adaptor = PowerProfileAdaptor()

    private fun snap(pct: Int, temp: Float, charging: Boolean = false) = TelemetrySnapshot(
        timestampFormatted = "12:00:00",
        batteryPercent = pct,
        batteryTemperatureC = temp,
        isCharging = charging,
        ramUsedMb = 3000,
        ramTotalMb = 12000,
        isThermalSafe = temp <= 39.0f
    )

    @Test
    fun unknownBatteryIsEcoEvenWhenCharging() {
        assertEquals(PowerProfileAdaptor.PowerLevel.ECO, adaptor.evaluate(snap(-1, 30.0f, charging = true)))
        assertEquals(PowerProfileAdaptor.PowerLevel.ECO, adaptor.evaluate(snap(-1, 30.0f)))
    }

    @Test
    fun zeroBatteryIsCriticalEvenWhenCharging() {
        assertEquals(PowerProfileAdaptor.PowerLevel.CRITICAL, adaptor.evaluate(snap(0, 30.0f, charging = true)))
        assertEquals(PowerProfileAdaptor.PowerLevel.CRITICAL, adaptor.evaluate(snap(0, 30.0f)))
    }

    @Test
    fun boundaries() {
        assertEquals(PowerProfileAdaptor.PowerLevel.CRITICAL, adaptor.evaluate(snap(14, 30.0f)))
        assertEquals(PowerProfileAdaptor.PowerLevel.ECO, adaptor.evaluate(snap(15, 30.0f)))
        assertEquals(PowerProfileAdaptor.PowerLevel.ECO, adaptor.evaluate(snap(30, 30.0f)))
        assertEquals(PowerProfileAdaptor.PowerLevel.BALANCED, adaptor.evaluate(snap(31, 30.0f)))
        assertEquals(PowerProfileAdaptor.PowerLevel.BALANCED, adaptor.evaluate(snap(70, 30.0f)))
        assertEquals(PowerProfileAdaptor.PowerLevel.PERFORMANCE, adaptor.evaluate(snap(71, 30.0f)))
        assertEquals(PowerProfileAdaptor.PowerLevel.CRITICAL, adaptor.evaluate(snap(10, 30.0f, charging = true)))
        assertEquals(PowerProfileAdaptor.PowerLevel.CRITICAL, adaptor.evaluate(snap(80, 39.0f)))
        assertEquals(1000L, adaptor.getRecommendedPollIntervalMs(PowerProfileAdaptor.PowerLevel.PERFORMANCE))
        assertEquals(1500L, adaptor.getRecommendedPollIntervalMs(PowerProfileAdaptor.PowerLevel.BALANCED))
        assertEquals(4000L, adaptor.getRecommendedPollIntervalMs(PowerProfileAdaptor.PowerLevel.ECO))
        assertEquals(8000L, adaptor.getRecommendedPollIntervalMs(PowerProfileAdaptor.PowerLevel.CRITICAL))
    }

    @Test
    fun widgetGateIsFalseOnlyAtCritical() {
        assertFalse(adaptor.shouldRenderNonEssentialWidgets(PowerProfileAdaptor.PowerLevel.CRITICAL))
        assertTrue(adaptor.shouldRenderNonEssentialWidgets(PowerProfileAdaptor.PowerLevel.PERFORMANCE))
        assertTrue(adaptor.shouldRenderNonEssentialWidgets(PowerProfileAdaptor.PowerLevel.BALANCED))
        assertTrue(adaptor.shouldRenderNonEssentialWidgets(PowerProfileAdaptor.PowerLevel.ECO))
    }
}
