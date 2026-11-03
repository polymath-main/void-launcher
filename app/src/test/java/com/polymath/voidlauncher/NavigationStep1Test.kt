package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * impl-02 Step 1 — regression harness for navigation/dock/drawer/files.
 * Activity/FileManager/CompoundGestureEngine are Android-bound and cannot
 * be compiled by the JVM harness, so (following the UiV2* pattern) these
 * are source-text assertions on the production code, plus the pure
 * VoidAction seam already covered by VoidActionTest (legacy fallback,
 * blank-package no-op — not duplicated here).
 *
 * Device-only items (drawer width %, scrubber scroll, 48dp rendering,
 * gesture matrix, install/uninstall refresh, keystroke lag) are NOT
 * pretended JVM tests; they live in the impl log's device checklist.
 */
class NavigationStep1Test {

    private fun read(rel: String): String {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val file = File(root, rel)
        assumeTrue("$rel missing", file.exists())
        return file.readText()
    }

    private fun activityText() =
        read("app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt")

    private fun dockServiceText() =
        read("app/src/main/java/com/polymath/voidlauncher/core/services/DockSlotMicroService.kt")

    private fun functionBody(text: String, signature: String): String {
        val start = text.indexOf(signature)
        assertTrue("missing function $signature", start >= 0)
        val next = text.indexOf("\n    private fun ", start + 10)
        return if (next >= 0) text.substring(start, next) else text.substring(start)
    }

    @Test
    fun dockBindsBySlotIndexAndHidesUnconfiguredSlots() {
        val body = functionBody(activityText(), "private fun rebuildDockSlots()")
        assertTrue("dock must bind by slot.index, not list position", body.contains("it.index == (i + 1)"))
        assertTrue("unconfigured slot buttons must be GONE (sparse binding)", body.contains("View.GONE"))
        assertTrue("configured slot buttons must be VISIBLE", body.contains("View.VISIBLE"))
        // Merge update (not a deletion): the literal "[i] label" format moved
        // out of rebuildDockSlots into the theme-aware DockSlotMicroService.
        // formatSlotDisplay (NUMERIC_BRACKET), which the Activity now calls.
        // Behaviour is unchanged for the default style; the assertion follows it.
        assertTrue("dock label must come from the theme-aware formatter", body.contains("formatSlotDisplay"))
        assertTrue(
            "dock label must carry the slot number",
            dockServiceText().contains("\"[\${slot.index}] \${slot.label}\"")
        )
    }

    @Test
    fun drawerFilterIsUncappedLabelOrPackageAndUpdatesCount() {
        val body = functionBody(activityText(), "private fun applyDrawerFilter(")
        assertTrue("drawer filter must match on label", body.contains("it.label.contains(query, ignoreCase = true)"))
        assertTrue("drawer filter must match on package", body.contains("it.packageName.contains(query, ignoreCase = true)"))
        assertTrue("drawer filter must update the count header", body.contains("binding.tvDrawerAppCount.text"))
        assertFalse("drawer filter must be uncapped (no take/limit)", body.contains(".take(") || body.contains("maxResults"))
        assertFalse("drawer filter must not launch anything", body.contains("launch"))
    }

    @Test
    fun fileListingIsDirsFirstNameAscending() {
        val fm = read("app/src/main/java/com/polymath/voidlauncher/core/FileManager.kt")
        assertTrue(
            "file listing must sort dirs-first then name-ascending",
            fm.contains("sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))")
        )
        assertTrue("hidden files must be gated on showHidden", fm.contains("if (!showHidden && file.name.startsWith(\".\")) false else true"))
    }

    @Test
    fun fileFallbackPathOrderIsVoidHomeFirst() {
        val fm = read("app/src/main/java/com/polymath/voidlauncher/core/FileManager.kt")
        val body = fm.substring(fm.indexOf("fun resolveAccessiblePath"))
        val order = listOf("voidHomePath()", "PATH_PROJECTS", "PATH_DOWNLOADS", "PATH_SDCARD")
        var prev = -1
        for (token in order) {
            val idx = body.indexOf(token, prev + 1)
            assertTrue("fallback order must contain $token after the previous fallback", idx > prev)
            prev = idx
        }
        assertTrue("fallback must never crash on a bad path (sdcard last resort)", body.contains("return PATH_SDCARD"))
    }

    @Test
    fun gestureThresholdConstantsMatchSpec() {
        val engine = read("app/src/main/java/com/polymath/voidlauncher/core/CompoundGestureEngine.kt")
        assertTrue("fling min distance must be 45dp", engine.contains("45f"))
        assertTrue("fling min velocity must be 120dp/s", engine.contains("120f"))
        assertTrue("edge zone must be 24dp (blueprint 28dp was wrong)", engine.contains("24f"))
        assertTrue("pinch-in threshold must be 0.85", engine.contains("0.85f"))
        assertTrue("pinch-out threshold must be 1.15", engine.contains("1.15f"))
        assertTrue("bottom edge zone must be 2x the edge zone", engine.contains("edgeZonePx * 2"))
    }

    @Test
    fun settingsGesturePickersCoverEveryHandledAction() {
        val settings = read("app/src/main/java/com/polymath/voidlauncher/VoidSettingsActivity.kt")
        val activity = activityText()
        val handler = functionBody(activity, "private fun handleGestureAction(")
        val handled = Regex("\"([A-Z_]+)\"\\s*->").findAll(handler).map { it.groupValues[1] }.toSet()
        assertTrue("handler must cover the known gesture actions", handled.size >= 16)
        val pickerBlock = settings.substring(settings.indexOf("val gestureActions = arrayOf("))
        for (action in handled) {
            assertTrue("Settings picker must offer handled action $action", pickerBlock.contains("\"$action\""))
        }
    }

    @Test
    fun dockSubtitleIsNotRenderedOnHome() {
        // impl-02 step 2: the dead getChildAt(1) subtitle assignment is
        // gone; DockSlot.subtitle survives only in the model/editor.
        val body = functionBody(activityText(), "private fun rebuildDockSlots()")
        assertFalse("rebuildDockSlots must not reference a subtitle view", body.contains("subtitleView"))
        assertFalse("rebuildDockSlots must not read getChildAt(1)", body.contains("getChildAt(1)"))
    }

    @Test
    fun keypadIsGeneratedFromConfiguredSlotsNotADialer() {
        val body = functionBody(activityText(), "private fun rebuildKeypad()")
        assertTrue("keypad must iterate configured slots sorted by index", body.contains("config.dockSlots.sortedBy { it.index }"))
        assertFalse("keypad must not be a 1-9 phone grid", body.contains("1..9") || body.contains("\"*\""))
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
