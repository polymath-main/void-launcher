package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Area 07 step 4 — SAF import/export UI (source-text tests; the
 * pickers themselves are Android-bound and need CI/device
 * verification for runtime proof, per the plan's acceptance note).
 */
class ConfigStep4Test {

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
    private fun manager() = read("app/src/main/java/com/polymath/voidlauncher/core/ConfigManager.kt")

    @Test
    fun safButtonsAndAdvancedRowsExist() {
        val text = xml()
        for (id in listOf(
            "etShellTimeoutSeconds", "etShellMaxOutputChars",
            "etAgentTaskTimeoutSeconds", "switchNotificationsCountLineOnly",
            "spnWallpaperFit", "tvWallpaperStatus",
            "btnExportFile", "btnImportFile", "btnRestoreBackup", "btnLegacyImportFiles"
        )) assertTrue("id $id missing", text.contains("android:id=\"@+id/$id\""))
        // EXPORT label must no longer over-promise: the old button
        // is now SAVE TO STORE, and EXPORT FILE… is the picker.
        assertTrue("old EXPORT CONFIG label must be gone", !text.contains("EXPORT CONFIG"))
        assertTrue("SAVE TO STORE label must exist", text.contains("SAVE TO STORE"))
        assertTrue("EXPORT FILE… label must exist", text.contains("EXPORT FILE"))
    }

    @Test
    fun safLaunchersDriveStreamPrimitivesWithWarnings() {
        val text = activity()
        assertTrue(text.contains("ActivityResultContracts.CreateDocument"))
        assertTrue(text.contains("ActivityResultContracts.OpenDocument"))
        assertTrue(text.contains("configSyncService.exportConfig(it)"))
        assertTrue(text.contains("configSyncService.importConfig(it)"))
        assertTrue(text.contains("configSyncService.restoreBackup()"))
        assertTrue(text.contains("store untouched"))
        assertTrue(text.contains("share carefully"))
        assertTrue(text.contains("lastValidationWarnings"))
        // Advanced rows round-trip through buildUpdatedConfig.
        assertTrue(text.contains("shellTimeoutSeconds = (binding.etShellTimeoutSeconds"))
        assertTrue(text.contains("shellMaxOutputChars = (binding.etShellMaxOutputChars"))
        assertTrue(text.contains("agentTaskTimeoutSeconds = (binding.etAgentTaskTimeoutSeconds"))
        assertTrue(text.contains("notificationsCountLineOnly = binding.switchNotificationsCountLineOnly.isChecked"))
        assertTrue(text.contains("wallpaperFit = binding.spnWallpaperFit.selectedItem"))
        // Legacy entry routes to the area-06 picker, not a duplicate.
        assertTrue(text.contains("putExtra(\"void_legacy_import\", true)"))
        assertTrue(mainActivity().contains("getBooleanExtra(\"void_legacy_import\", false)"))
        assertTrue(mainActivity().contains("legacyImportLauncher.launch(null)"))
    }

    @Test
    fun legacyAutoReadDecisionIsRecordedNotAmbiguous() {
        val text = manager()
        assertTrue(text.contains("Area 07 step 4, plan G-06 DECISION"))
        assertTrue(text.contains("KEPT as the intended"))
        assertTrue(text.contains("never written back to the legacy path"))
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
