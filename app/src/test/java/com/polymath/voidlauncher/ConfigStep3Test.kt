package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Area 07 step 3 — Settings behaviour fixes (source-text tests;
 * the Activity is Android-bound, so behaviour here is asserted on
 * source and needs CI/device verification for runtime proof).
 */
class ConfigStep3Test {

    private fun read(rel: String): String {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val file = File(root, rel)
        assumeTrue("$rel missing", file.exists())
        return file.readText()
    }

    private fun xml() = read("app/src/main/res/layout/activity_void_settings.xml")
    private fun activity() = read("app/src/main/java/com/polymath/voidlauncher/VoidSettingsActivity.kt")
    private fun mainActivity() = read("app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt")

    @Test
    fun sectionNumberingIsSequentialAndOldNumbersGone() {
        val text = xml()
        for (n in listOf("01.", "02.", "03.", "04.", "05.", "06.", "07.", "08.", "09.")) {
            assertTrue("section $n missing", text.contains("\"$n "))
        }
        assertTrue("old 00. numbering must be gone", !text.contains("\"00. "))
        assertTrue("old 03b. numbering must be gone", !text.contains("\"03b. "))
        assertTrue("old 03c. numbering must be gone", !text.contains("\"03c. "))
    }

    @Test
    fun newStep3IdsExist() {
        val text = xml()
        for (id in listOf(
            "seekHudFont", "seekDockFont", "seekOmniFont",
            "tvHudFontLabel", "tvDockFontLabel", "tvOmniFontLabel",
            "btnOpenPersonaEditor", "btnOpenFocusEditor",
            "tvConfigSaveStatus", "tvJsonOnlyNote",
            "btnResetAppearance", "btnResetHome", "btnResetSystem", "btnResetData"
        )) assertTrue("id $id missing", text.contains("android:id=\"@+id/$id\""))
    }

    @Test
    fun onPauseNeverSavesResetPreviewAndSkipsCleanForms() {
        val text = activity()
        assertTrue(text.contains("private var resetPreviewPending: Boolean = false"))
        assertTrue(text.contains("if (resetPreviewPending) return"))
        // Step 5 nested the persist inside a generation check; the
        // dirty-check itself (built != saved) must still gate it.
        assertTrue(text.contains("if (built != savedConfigAtLoad)"))
        assertTrue(text.contains("persist(false)"))
        assertTrue(text.contains("resetPreviewPending = true"))
    }

    @Test
    fun fontSizesAreConfigEditsInPopulateAndBuild() {
        val text = activity()
        assertTrue(text.contains("seekHudFont.progress = (cfg.hudFontSizeSp * 2)"))
        assertTrue(text.contains("hudFontSizeSp = (binding.seekHudFont.progress / 2f).coerceIn(8f, 20f)"))
        assertTrue(text.contains("dockFontSizeSp = (binding.seekDockFont.progress / 2f).coerceIn(8f, 20f)"))
        assertTrue(text.contains("omniFontSizeSp = (binding.seekOmniFont.progress / 2f).coerceIn(8f, 20f)"))
    }

    @Test
    fun groupResetAndEditorEntryPointsAreWired() {
        val text = activity()
        assertTrue(text.contains("private fun resetGroup(group: String)"))
        assertTrue(text.contains("btnResetAppearance.setOnClickListener { resetGroup(\"appearance\") }"))
        assertTrue(text.contains("putExtra(\"void_open_persona_editor\", true)"))
        assertTrue(text.contains("putExtra(\"void_open_focus_editor\", true)"))
        val main = mainActivity()
        assertTrue(main.contains("private fun handleSettingsEditorIntent"))
        assertTrue(main.contains("getBooleanExtra(\"void_open_persona_editor\", false)"))
        assertTrue(main.contains("getBooleanExtra(\"void_open_focus_editor\", false)"))
        assertTrue(main.contains("showPersonaManagerDialog"))
        assertTrue(main.contains("showScheduleDialog"))
    }

    @Test
    fun saveStatusSurfacesFallbackAndWarnings() {
        val text = activity()
        assertTrue(text.contains("tvConfigSaveStatus.text"))
        assertTrue(text.contains("lastSaveWasFallback"))
        assertTrue(text.contains("lastValidationWarnings"))
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
