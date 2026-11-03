package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.TelemetryHistory
import com.polymath.voidlauncher.core.ThermalStatusLabel
import com.polymath.voidlauncher.model.TelemetrySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** impl-05 step 6: thermal-status labels + snapshot history ring buffer. */
class TelemetryStep6Test {

    @Test
    fun thermalStatusLabels() {
        assertEquals("NONE", ThermalStatusLabel.fromCode(0))
        assertEquals("LIGHT", ThermalStatusLabel.fromCode(1))
        assertEquals("MODERATE", ThermalStatusLabel.fromCode(2))
        assertEquals("SEVERE", ThermalStatusLabel.fromCode(3))
        assertEquals("CRITICAL", ThermalStatusLabel.fromCode(4))
        assertEquals("EMERGENCY", ThermalStatusLabel.fromCode(5))
        assertEquals("SHUTDOWN", ThermalStatusLabel.fromCode(6))
        assertEquals("UNKNOWN", ThermalStatusLabel.fromCode(99))
        assertEquals("UNKNOWN", ThermalStatusLabel.fromCode(-1))
    }

    @Test
    fun historyIsBoundedAndOrdered() {
        val history = TelemetryHistory(capacity = 3)
        assertNull(history.latestTempC())
        for (i in 1..5) {
            history.add(
                TelemetrySnapshot("12:00:0$i", 50, 30.0f + i, false, 3000, 12000, true)
            )
        }
        assertEquals(3, history.size)
        assertEquals(listOf(33.0f, 34.0f, 35.0f), history.snapshots().map { it.batteryTemperatureC })
        assertEquals(35.0f, history.latestTempC())
    }
}
