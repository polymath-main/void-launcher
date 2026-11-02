package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * UI v3 Cycle 3: grouped settings port assertions, as layout/activity
 * source-text checks (same JVM-harness pattern as UiV2DrawerTest —
 * no Android layout inflater here, so the XML/activity source is the
 * artefact under test; a device/CI build is still the visual proof).
 *
 * Spec source: UI_V2_SPEC §2(7) settings — grouped Appearance /
 * Home / System / Data, not a wall of switches. The regrouping is
 * block-level: existing sections are reordered under the four group
 * headers, no view id is added or removed, and no save path changes.
 * Documented divergences (see LOOP_LOG): identity has no settings
 * control (it changes via the persona badge tap / TOGGLE_IDENTITY
 * gesture), there is no dock-slot editor and no SAF-import or
 * legacy-import control in the settings UI, and the telemetry/NodeOS
 * rows stay inside the Home card they shipped in rather than being
 * split row-by-row into System.
 */
class UiV2SettingsTest {

    private fun read(rel: String): String {
        val root = findRepoRoot()
        assumeTrue("repo sources not found; see LOOP_LOG", root != null)
        val file = File(root, rel)
        assumeTrue("$rel missing", file.exists())
        return file.readText()
    }

    private fun settingsText() = read("app/src/main/res/layout/activity_void_settings.xml")
    private fun settingsActivityText() = read("app/src/main/java/com/polymath/voidlauncher/VoidSettingsActivity.kt")

    private fun groupPos(text: String, group: String): Int {
        val idx = text.indexOf("android:text=\"$group\"")
        assertTrue("group header $group missing", idx >= 0)
        return idx
    }

    @Test
    fun settingsAreGroupedAppearanceHomeSystemDataInOrder() {
        val text = settingsText()
        val appearance = groupPos(text, "APPEARANCE")
        val home = groupPos(text, "HOME")
        val system = groupPos(text, "SYSTEM")
        val data = groupPos(text, "DATA")
        assertTrue("groups must be Appearance, Home, System, Data in order", appearance < home && home < system && system < data)
        // Group headers use the spec 12sp letter-spaced style.
        for (group in listOf("APPEARANCE", "HOME", "SYSTEM", "DATA")) {
            val head = text.substring(0, groupPos(text, group))
            assertTrue("$group header must be 12sp", text.substring(groupPos(text, group) - 400, groupPos(text, group)).contains("android:textSize=\"12sp\""))
            assertTrue("$group header must be letter-spaced", text.substring(groupPos(text, group) - 400, groupPos(text, group)).contains("android:letterSpacing=\"0.08\""))
        }
        // Screen title is the 16sp spec title size.
        val titleIdx = text.indexOf("VOID CONFIG EDITOR")
        assertTrue("settings title must be 16sp", text.substring(titleIdx - 400, titleIdx).contains("android:textSize=\"16sp\""))
    }

    @Test
    fun settingsGroupsContainTheRightControls() {
        val text = settingsText()
        fun between(id: String, from: Int, to: Int): Boolean {
            val idx = text.indexOf("android:id=\"@+id/$id\"")
            return idx in from until to
        }
        val appearance = groupPos(text, "APPEARANCE")
        val home = groupPos(text, "HOME")
        val system = groupPos(text, "SYSTEM")
        val data = groupPos(text, "DATA")
        assertTrue("theme preset must be under Appearance", between("spnTheme", appearance, home))
        assertTrue("drawer controls must be under Appearance", between("switchAtoZScrubber", appearance, home))
        assertTrue("default mode must be under Home", between("spnHomeMode", home, system))
        assertTrue("digit keypad toggle must be under Home", between("switchShowDigitKeypad", home, system))
        assertTrue("intent toggle must be under Home", between("switchShowIntent", home, system))
        assertTrue("notifications toggle must be under Home", between("switchShowNotifications", home, system))
        assertTrue("dock toggle must be under Home", between("switchShowDock", home, system))
        assertTrue("system roles must be under System", between("btnSetDefaultLauncherSettings", system, data))
        assertTrue("gestures must be under System", between("spnGestureSwipeUp", system, data))
        assertTrue("thermal limit must be under System", between("etThermalLimit", system, data))
        assertTrue("status bar toggles must be under System or earlier Home homescreen block, not Data", text.indexOf("android:id=\"@+id/switchShowStatusTime\"") < data)
        assertTrue("files default path must be under Data", between("etFilesDefaultPath", data, text.length))
        assertTrue("export button must be under Data", between("btnExportJson", data, text.length))
        assertTrue("reset button must be under Data", between("btnResetDefaults", data, text.length))
    }

    @Test
    fun everyExistingSettingSurvivesWithItsSavePath() {
        val text = settingsText()
        // Complete id set of the settings layout before this cycle.
        val ids = listOf(
            "settingsRoot", "btnSettingsBack", "btnSettingsSave", "spnTheme",
            "presetCrimson", "presetCyan", "presetGreen", "presetAmber",
            "etAccentColor", "etSecondaryColor", "etSurfaceColor",
            "switchDrawerEdge", "tvDrawerOpacityLabel", "seekDrawerOpacity",
            "switchShowAppIcons", "spnIconStyle", "switchAtoZScrubber",
            "spnHomeMode", "switchShowTelemetry", "switchShowNodeOs",
            "switchShowIntent", "switchShowNotifications", "switchFilterOngoing",
            "switchShowDock", "switchShowDigitKeypad", "switchUseWallpaper",
            "tvCardRadiusLabel", "seekCardRadius", "switchShowStatusTime",
            "switchShowStatusBattery", "switchShowStatusRam",
            "tvDefaultLauncherStatus", "btnSetDefaultLauncherSettings",
            "tvStoragePermissionStatus", "btnGrantStorageSettings",
            "tvNotifListenerStatus", "btnConfigureNotifListener",
            "spnGestureSwipeUp", "spnGestureSwipeDown", "spnGestureSwipeLeft",
            "spnGestureSwipeRight", "spnGestureDoubleTap", "spnGestureLongPress",
            "etThermalLimit", "switchGlyphPulse", "switchHaptics",
            "etFilesDefaultPath", "switchFilesShowHidden",
            "btnResetDefaults", "btnExportJson"
        )
        for (id in ids) {
            assertTrue("settings id $id must survive the regrouping", text.contains("android:id=\"@+id/$id\""))
        }
        // Save paths untouched: the Activity still reads/writes the
        // same bindings and exports the CURRENT config (1.1.0 fix).
        val activity = settingsActivityText()
        assertTrue("export must still write the current config", activity.contains("configManager.exportCurrentConfig(currentConfig)"))
        assertTrue("export must not regress to template/default export", !activity.contains("exportCurrentConfig(LauncherConfig())"))
        assertTrue("save must still build from the same bindings", activity.contains("showDigitKeypad = binding.switchShowDigitKeypad.isChecked"))
        assertTrue("save must still persist thermal limit", activity.contains("thermalLimitCelsius = thermal"))
        assertTrue("save must still persist files path", activity.contains("filesDefaultPath = filesPath"))
    }

    private fun findRepoRoot(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (dir == null) return null
            if (File(dir, "app/src/main/java/com/polymath/voidlauncher/core/BareIntegerRouter.kt").exists()) {
                return dir
            }
            dir = dir?.parentFile
        }
        return null
    }
}
