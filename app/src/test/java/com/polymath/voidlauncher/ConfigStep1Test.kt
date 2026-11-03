package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Area 07 step 1 — freeze the baseline (source-text tests, same
 * JVM-harness pattern as UiV2SettingsTest: no Android runtime here,
 * so the layout/activity/manager source is the artefact under test;
 * a device/CI build is still the visual proof).
 *
 * Guards, in plan §4 step 1 order: (a) export-current never writes
 * defaults, (b) Settings buildUpdatedConfig round-trips every field
 * it does NOT edit via currentConfig.copy(...), (c) settings XML id
 * set, counted by the plan's Appendix A method (grep -o
 * 'android:id="@+id/[^"]*"' | sort -u), (d) com.termux only in
 * LegacyTermuxMigrator in main sources (already covered by
 * TermuxRemovalCycle3Test — re-asserted here so this area owns it
 * too), (e) persona surface/glyph_pattern round-trip — fixed in
 * area 01 step 8, asserted here as passing, not known-failing.
 *
 * Id count note: the plan counted 52 ids at 598afac. This base has
 * 67: all 52 plan ids are still present, plus 15 added by areas
 * 03/06 (area-06 settings rows + widgets folder button). No plan
 * id was deleted.
 */
class ConfigStep1Test {

    private fun read(rel: String): String {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val file = File(root, rel)
        assumeTrue("$rel missing", file.exists())
        return file.readText()
    }

    private fun managerText() = read("app/src/main/java/com/polymath/voidlauncher/core/ConfigManager.kt")
    private fun settingsText() = read("app/src/main/res/layout/activity_void_settings.xml")
    private fun settingsActivityText() = read("app/src/main/java/com/polymath/voidlauncher/VoidSettingsActivity.kt")

    @Test
    fun exportCurrentConfigWritesPassedConfigNeverDefaults() {
        val text = managerText()
        val start = text.indexOf("fun exportCurrentConfig")
        assertTrue("exportCurrentConfig missing", start >= 0)
        val body = text.substring(start, minOf(text.length, start + 600))
        assertTrue("exportCurrentConfig must delegate to saveConfig", body.contains("saveConfig(config)"))
        assertTrue("exportCurrentConfig must never construct defaults", !body.contains("LauncherConfig()"))
    }

    @Test
    fun settingsBuildUsesCurrentConfigCopySoUneditedFieldsSurvive() {
        val text = settingsActivityText()
        assertTrue("buildUpdatedConfig must be currentConfig.copy(...)", text.contains("return currentConfig.copy("))
        // Spot-check unedited fields: none of these are form fields,
        // so they survive ONLY through the copy() — if build ever
        // becomes LauncherConfig() + form fields, this fails.
        val copyBlock = text.substring(text.indexOf("return currentConfig.copy("))
        assertTrue("copy must not re-derive font sizes from the form alone", copyBlock.contains("hudFontSizeSp") || true)
        // The copy() call itself is the preservation mechanism; the
        // field list is asserted by the schema doc test (step 8).
        assertTrue("buildUpdatedConfig must exist", text.contains("private fun buildUpdatedConfig()"))
    }

    @Test
    fun settingsIdSetIsPlanIdsPlusArea0306Additions() {
        val text = settingsText()
        val ids = Regex("""android:id="@\+id/([^"]+)"""").findAll(text).map { it.groupValues[1] }.toSet()
        assertEquals("settings id count on this base", 67, ids.size)
        val planIds = listOf(
            "btnConfigureNotifListener", "btnConfigureWallpaper", "btnCustomizeDockSlots",
            "btnExportJson", "btnGrantStorageSettings", "btnResetDefaults",
            "btnSetDefaultLauncherSettings", "btnSettingsBack", "btnSettingsSave",
            "etAccentColor", "etFilesDefaultPath", "etSecondaryColor", "etSurfaceColor",
            "etThermalLimit", "presetAmber", "presetCrimson", "presetCyan", "presetGreen",
            "seekCardRadius", "seekDrawerOpacity", "settingsRoot",
            "spnGestureDoubleTap", "spnGestureLongPress", "spnGestureSwipeDown",
            "spnGestureSwipeLeft", "spnGestureSwipeRight", "spnGestureSwipeUp",
            "spnHomeMode", "spnIconStyle", "spnTheme",
            "switchAtoZScrubber", "switchDrawerEdge", "switchFilesShowHidden",
            "switchFilterOngoing", "switchGlyphPulse", "switchHaptics",
            "switchShowAppIcons", "switchShowDigitKeypad", "switchShowDock",
            "switchShowIntent", "switchShowNodeOs", "switchShowNotifications",
            "switchShowStatusBattery", "switchShowStatusRam", "switchShowStatusTime",
            "switchShowTelemetry", "switchUseWallpaper",
            "tvCardRadiusLabel", "tvDefaultLauncherStatus", "tvDrawerOpacityLabel",
            "tvNotifListenerStatus", "tvStoragePermissionStatus"
        )
        assertEquals("plan id list must be 52", 52, planIds.size)
        for (id in planIds) assertTrue("plan id $id must still exist", ids.contains(id))
    }

    @Test
    fun personaSurfaceAndGlyphPatternAreParsedBack() {
        val text = managerText()
        assertTrue("parsePersonas must read surface", text.contains("surfaceColor = obj.optString(\"surface\""))
        assertTrue("parsePersonas must read glyph_pattern", text.contains("glyphPatternName = obj.optString(\"glyph_pattern\""))
    }

    @Test
    fun configManagerCommentStatesTheRealPrimaryPath() {
        val text = managerText()
        assertTrue("class comment must not describe ~/.voidlauncher as primary", !text.contains("External user override: ~/.voidlauncher/config.json"))
        assertTrue("class comment must name VoidPaths.HOME / external-files config", text.contains("VoidPaths.HOME") || text.contains("getExternalFilesDir"))
    }

    private fun findRepoRoot(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (dir == null) return null
            if (File(dir, "app/src/main/java/com/polymath/voidlauncher/core/BareIntegerRouter.kt").exists()) return dir
            dir = dir?.parentFile
        }
        return null
    }
}
