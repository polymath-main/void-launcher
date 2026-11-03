package com.polymath.voidlauncher

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import com.polymath.voidlauncher.core.ConfigGeneration
import com.polymath.voidlauncher.core.ConfigMigration

/**
 * Area 07 step 5 — generation stamp + migration hook + reload
 * failure feedback. Pure seams run for real; the Activity/Settings
 * wiring is source-text (Android-bound, needs CI/device proof).
 */
class ConfigStep5Test {

    @Test
    fun generationDetectsChangeAndIgnoresUnchanged() {
        val dir = createTempDir("configgen")
        val file = File(dir, "config.json")
        file.writeText("""{"theme_id":"void"}""")
        val snap = ConfigGeneration.snapshot(file)
        assertTrue(snap != null)
        assertFalse("unchanged file must not count as changed", ConfigGeneration.changed(snap, file))
        Thread.sleep(10)
        file.writeText("""{"theme_id":"void","drawer_width_percent":90}""")
        assertTrue("changed content/length must count as changed", ConfigGeneration.changed(snap, file))
        assertTrue("missing file counts as changed", ConfigGeneration.changed(snap, File(dir, "gone.json")))
        assertTrue("null snapshot counts as changed", ConfigGeneration.changed(null, file))
    }

    @Test
    fun migrationHookIsAPassthroughToday() {
        val json = JSONObject("""{"theme_id":"void"}""")
        val out = ConfigMigration.migrate(json, 1)
        assertTrue("passthrough must return the same object content", out.toString() == json.toString())
    }

    private fun read(rel: String): String {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val file = File(root, rel)
        assumeTrue("$rel missing", file.exists())
        return file.readText()
    }

    @Test
    fun settingsWarnsOnExternalChangeInsteadOfOverwriting() {
        val text = read("app/src/main/java/com/polymath/voidlauncher/VoidSettingsActivity.kt")
        assertTrue(text.contains("storeChangedExternally()"))
        assertTrue(text.contains("Config changed externally"))
        assertTrue(text.contains("loadedStoreSnapshot = ConfigGeneration.snapshot(storeFile())"))
    }

    @Test
    fun reloadFailureIsSurfacedNotSilent() {
        val manager = read("app/src/main/java/com/polymath/voidlauncher/core/ConfigManager.kt")
        assertTrue(manager.contains("var lastLoadError"))
        assertTrue(manager.contains("Cannot parse"))
        val main = read("app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt")
        assertTrue(main.contains("configManager.lastLoadError"))
        assertTrue(main.contains("WARNING: file failed to parse"))
        val sync = read("app/src/main/java/com/polymath/voidlauncher/core/ConfigSyncService.kt")
        assertTrue("debounce already 300ms from area 01", sync.contains("DEBOUNCE_MS = 300L"))
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
