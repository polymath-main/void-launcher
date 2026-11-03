package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * impl-05 step 7: notifications count-line mode, asserted as source checks
 * (the JVM harness has no Android layout inflater, same pattern as the
 * UiV2* / TelemetryStep4 tests). Runtime behaviour needs CI/device
 * verification. Default is false, so shipped inline behaviour is preserved
 * until the key is enabled (Settings UI is area 07 follow-up).
 */
class TelemetryStep7Test {

    private fun readRepoFile(rel: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (dir == null) return ""
            val f = File(dir, rel)
            if (f.exists()) return f.readText()
            dir = dir?.parentFile
        }
        return ""
    }

    @Test
    fun countLineKeyExistsWithSafeDefault() {
        val config = readRepoFile("app/src/main/java/com/polymath/voidlauncher/model/LauncherConfig.kt")
        assumeTrue("LauncherConfig not found", config.isNotEmpty())
        assertTrue(config.contains("notificationsCountLineOnly"))
        assertTrue(config.contains("notificationsCountLineOnly: Boolean = false"))
    }

    @Test
    fun headerTapTogglesListInCountLineMode() {
        val activity = readRepoFile("app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt")
        assumeTrue("Activity source not found", activity.isNotEmpty())
        assertTrue(activity.contains("notificationsListExpanded"))
        assertTrue(activity.contains("tvNotifHeader.setOnClickListener"))
        // The toggle must not touch scoring/filtering or thermal evaluation.
        assertTrue(activity.contains("config.notificationsCountLineOnly"))
    }
}
