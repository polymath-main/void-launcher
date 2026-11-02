package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * UI v3 Cycle 1: drawer port assertions, as layout/activity source-text
 * checks (same JVM-harness pattern as UiV2CockpitTest — no Android
 * layout inflater here, so the XML/activity source is the artefact
 * under test; a device/CI build is still the visual proof).
 *
 * Spec source: UI_V2_SPEC §2(4) drawer — 85% slide-over, search input
 * on top, 48px rows (name + package), A-Z scrubber, count header,
 * tokens from colors.xml / ThemeEngine.kt only. The 85% width is set
 * at runtime from drawer_width_percent (default 85, default_config
 * + LauncherConfig), not by the 300dp XML literal, which the runtime
 * width assignment supersedes — asserted on the Activity source.
 */
class UiV2DrawerTest {

    private fun read(rel: String): String {
        val root = findRepoRoot()
        assumeTrue("repo sources not found; see LOOP_LOG", root != null)
        val file = File(root, rel)
        assumeTrue("$rel missing", file.exists())
        return file.readText()
    }

    private fun layoutText() = read("app/src/main/res/layout/activity_void_launcher.xml")
    private fun drawerRowText() = read("app/src/main/res/layout/item_drawer_app.xml")
    private fun activityText() = read("app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt")
    private fun colorsText() = read("app/src/main/res/values/colors.xml")

    private fun functionBody(text: String, name: String): String {
        val start = text.indexOf("private fun $name(")
        assertTrue("missing function $name", start >= 0)
        val next = text.indexOf("\n    private fun ", start + 10)
        return if (next >= 0) text.substring(start, next) else text.substring(start)
    }

    @Test
    fun drawerSearchInputIsOnTopAt48dp14sp() {
        val text = layoutText()
        val searchIdx = text.indexOf("android:id=\"@+id/etDrawerSearch\"")
        assertTrue("etDrawerSearch missing", searchIdx >= 0)
        val head = text.substring(searchIdx, minOf(searchIdx + 1200, text.length))
        assertTrue("drawer search must be 48dp", head.contains("android:layout_height=\"48dp\""))
        assertTrue("drawer search must be 14sp", head.contains("android:textSize=\"14sp\""))
        assertTrue("drawer search must use the primary text token", head.contains("@color/text_primary"))
        assertTrue("drawer search must use the muted hint token", head.contains("@color/text_muted"))
        // Search sits above the apps list, not below it.
        val listIdx = text.indexOf("android:id=\"@+id/rvDrawerApps\"")
        assertTrue("drawer search must precede the apps list", searchIdx < listIdx)
        assertTrue("drawer search must reuse the omni bar drawable", head.contains("@drawable/bg_omni_bar"))
    }

    @Test
    fun drawerRowsAre48dpWithNameAndPackage() {
        val row = drawerRowText()
        assertTrue("drawer rows must be 48dp", row.contains("android:minHeight=\"48dp\""))
        assertTrue("drawer app name must be 14sp", row.contains("android:textSize=\"14sp\""))
        assertTrue("drawer app package must be 12sp", row.contains("android:textSize=\"12sp\""))
        assertTrue("drawer row must carry the name id", row.contains("android:id=\"@+id/tvDrawerAppName\""))
        assertTrue("drawer row must carry the package id", row.contains("android:id=\"@+id/tvDrawerAppPkg\""))
        assertTrue("drawer name must use the primary token", row.contains("@color/text_primary"))
        assertTrue("drawer package must use the muted token", row.contains("@color/text_muted"))
    }

    @Test
    fun drawerHeaderIs16spWith48dpClose() {
        val text = layoutText()
        val countIdx = text.indexOf("android:id=\"@+id/tvDrawerAppCount\"")
        assertTrue("tvDrawerAppCount missing", countIdx >= 0)
        assertTrue(
            "drawer count header must be 16sp",
            text.substring(countIdx, minOf(countIdx + 600, text.length)).contains("android:textSize=\"16sp\"")
        )
        val closeIdx = text.indexOf("android:id=\"@+id/btnDrawerClose\"")
        assertTrue("btnDrawerClose missing", closeIdx >= 0)
        val closeHead = text.substring(closeIdx, minOf(closeIdx + 600, text.length))
        assertTrue("drawer close must be 48dp wide", closeHead.contains("android:layout_width=\"48dp\""))
        assertTrue("drawer close must be 48dp high", closeHead.contains("android:layout_height=\"48dp\""))
        // A-Z scrubber and edge handle survive the port.
        assertTrue("alphabetScrubber missing", text.contains("android:id=\"@+id/alphabetScrubber\""))
        assertTrue("drawer tab handle missing", text.contains("android:id=\"@+id/btnDrawerTabHandle\""))
    }

    @Test
    fun drawerWidthIs85PercentAtRuntime() {
        val activity = activityText()
        assertTrue(
            "drawer width must come from drawerWidthPercent at runtime",
            activity.contains("config.drawerWidthPercent")
        )
        val config = read("app/src/main/assets/default_config.json")
        assertTrue("drawer_width_percent default must be 85", config.contains("\"drawer_width_percent\": 85"))
    }

    @Test
    fun drawerSearchFiltersWithoutAddingALaunchTap() {
        val activity = activityText()
        val filter = functionBody(activity, "applyDrawerFilter")
        assertTrue("filter must match on label", filter.contains("it.label.contains(query, ignoreCase = true)"))
        assertTrue("filter must match on package", filter.contains("it.packageName.contains(query, ignoreCase = true)"))
        assertTrue("filter must update the count header", filter.contains("binding.tvDrawerAppCount.text"))
        assertFalse("filter must not launch anything", filter.contains("launch"))
        // The watcher only filters; the adapter still owns tap/long-press.
        assertTrue("drawer search watcher must be registered", activity.contains("binding.etDrawerSearch.addTextChangedListener"))
        val adapter = read("app/src/main/java/com/polymath/voidlauncher/ui/DrawerAppAdapter.kt")
        assertTrue("tap on a drawer row must still launch", adapter.contains("binding.root.setOnClickListener"))
        assertTrue("long-press on a drawer row must still show the app menu", adapter.contains("binding.root.setOnLongClickListener"))
        // Drawer refresh keeps an active search filter instead of clearing it.
        val refresh = functionBody(activity, "refreshIndexAsync")
        assertTrue("drawer refresh must re-apply the filter", refresh.contains("applyDrawerFilter(all)"))
    }

    @Test
    fun drawerOmniCapAndColoursAreUnchanged() {
        val activity = activityText()
        assertTrue(
            "omni filtering cap must still come from config (5)",
            activity.contains("maxResults = config.omniFuzzyMaxResults")
        )
        val config = read("app/src/main/assets/default_config.json")
        assertTrue("omni_fuzzy_max_results default must be 5", config.contains("\"omni_fuzzy_max_results\": 5"))
        // Every colour the drawer block references must exist in colors.xml.
        val colors = colorsText()
        val drawerIdx = layoutText().indexOf("android:id=\"@+id/rightDrawerLayout\"")
        val drawerBlock = layoutText().substring(drawerIdx, minOf(drawerIdx + 4500, layoutText().length))
        for (color in Regex("@color/([A-Za-z0-9_]+)").findAll(drawerBlock).map { it.groupValues[1] }.toSet()) {
            assertTrue("drawer colour $color must be defined in colors.xml", colors.contains("name=\"$color\""))
        }
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
