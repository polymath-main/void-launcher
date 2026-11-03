package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * impl-05 step 4: throttle visibility, asserted as Activity-source checks
 * (the JVM harness has no Android layout inflater, same pattern as the
 * UiV2* tests). Runtime behaviour still needs CI/device verification.
 */
class TelemetryStep4Test {

    private fun activityText(): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (dir == null) return ""
            val f = File(dir, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt")
            if (f.exists()) return f.readText()
            dir = dir?.parentFile
        }
        return ""
    }

    @Test
    fun hudCarriesPersistentThrottleToken() {
        val text = activityText()
        assumeTrue("Activity source not found", text.isNotEmpty())
        assertTrue(text.contains("⚠ THROTTLED"))
        assertTrue(text.contains("thermalGuardian.isThrottled"))
    }

    @Test
    fun statusReportsThrottleAndCancelledIds() {
        val text = activityText()
        assumeTrue("Activity source not found", text.isNotEmpty())
        assertTrue(text.contains("[THERMAL]: THROTTLED"))
        assertTrue(text.contains("thermallyCancelledTaskIds"))
    }
}
