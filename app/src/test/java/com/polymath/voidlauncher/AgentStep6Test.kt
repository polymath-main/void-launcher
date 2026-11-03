package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * impl-06 step 6: Settings UI for this area only. Settings is
 * Android-bound (generated view binding), so these are source-text
 * assertions — rendered UI still needs CI/device verification.
 * Editors for personas, per-persona dock slots, focus schedules,
 * widgets, and the SAF picker are steps 8–9, NOT built here.
 */
class AgentStep6Test {

    @Test
    fun settingsCoversArea06SubsetOnly() {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val layout = File(root, "app/src/main/res/layout/activity_void_settings.xml").readText()
        for (id in listOf(
            "switchNodeOsLegacyImport", "etWorkflowJsonPath", "etNodeosDbPath",
            "etNotifWhitelist", "etNotifBlacklist", "etNotifKeywords", "etNotifMaxCount",
            "switchWidgetsEnabled", "tvWidgetsDirectory", "btnOpenWidgetsFolder",
            "switchAgentOrchestrator", "switchAstGraph", "etAstNodeCap",
            "switchFocusScheduler", "tvFocusSchedulePath"
        )) {
            assertTrue("layout missing $id", layout.contains(id))
        }
        // Guarded legacy import explanation, verbatim in the layout.
        assertTrue(layout.contains("does not install NodeOS and never runs a NodeOS daemon"))
        assertTrue(layout.contains("Turning this on writes nothing to those files") ||
            layout.contains("writes nothing to those files"))

        val activity = File(root, "app/src/main/java/com/polymath/voidlauncher/VoidSettingsActivity.kt").readText()
        assertTrue(activity.contains("notifWhitelistPackages = parseCsvList"))
        assertTrue(activity.contains("coerceIn(1, 20)"))
        assertTrue(activity.contains("nodeOsLegacyImportEnabled = binding.switchNodeOsLegacyImport.isChecked"))
        assertTrue(activity.contains("btnOpenWidgetsFolder"))
        // Explicitly NOT built in step 6:
        assertFalse("persona editor belongs to step 8", activity.contains("PersonaEditor") || activity.contains("showPersonaEditor"))
        assertFalse("schedule editor belongs to step 8", activity.contains("FocusScheduleEditor") || activity.contains("showScheduleEditor"))
        assertFalse("widget editor is not step 6", activity.contains("WidgetEditor"))
        assertFalse("SAF picker belongs to step 9", activity.contains("ACTION_OPEN_DOCUMENT_TREE"))

        val main = File(root, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").readText()
        assertTrue(main.contains("void_open_files_path"))
        assertTrue(main.contains("fun handleOpenFilesIntent"))
    }

    private fun findRepoRoot(): File? {
        var dir: File? = File("").absoluteFile
        var hops = 0
        while (dir != null && hops < 6) {
            if (File(dir, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").exists()) return dir
            dir = dir.parentFile
            hops++
        }
        return null
    }
}
