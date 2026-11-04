package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * UI audit fix 1 (feature/ui-audit-fix-1): gates and core features
 * that had no reachable UI entry. Same JVM source-text harness
 * pattern as UiV2FilesCommandTest — these assertions guard the
 * wiring, they are NOT device proof (see UI_AUDIT.md).
 */
class UiAuditFixTest {

    private fun read(rel: String): String {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val file = File(root, rel)
        assumeTrue("$rel missing", file.exists())
        return file.readText()
    }

    private fun activityText() = read("app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt")
    private fun settingsActivityText() = read("app/src/main/java/com/polymath/voidlauncher/VoidSettingsActivity.kt")
    private fun settingsLayoutText() = read("app/src/main/res/layout/activity_void_settings.xml")
    private fun launcherLayoutText() = read("app/src/main/res/layout/activity_void_launcher.xml")

    @Test
    fun gatedFeaturesHaveSettingsSwitches() {
        val layout = settingsLayoutText()
        val activity = settingsActivityText()
        for ((id, field) in listOf(
            "switchVoidTerm" to "voidTermEnabled",
            "switchMacrosEnabled" to "macrosEnabled",
            "switchCommandHistoryEnabled" to "commandHistoryEnabled"
        )) {
            assertTrue("$id missing from settings layout", layout.contains("android:id=\"@+id/$id\""))
            assertTrue("$id must load from config", activity.contains("binding.$id.isChecked = cfg.$field"))
            assertTrue("$id must save to config", activity.contains("$field = binding.$id.isChecked"))
        }
    }

    @Test
    fun commandCenterHasDeckTermMacrosHelpChips() {
        val layout = launcherLayoutText()
        val activity = activityText()
        for ((chip, cmd) in listOf(
            "chipCmdDeck" to ":deck",
            "chipCmdTerm" to ":term",
            "chipCmdMacros" to ":macros",
            "chipCmdHelp" to ":help"
        )) {
            assertTrue("$chip missing from command center layout", layout.contains("android:id=\"@+id/$chip\""))
            assertTrue("$chip must run $cmd", activity.contains("binding.$chip.setOnClickListener { handleCommandCenterChip(\"$cmd\") }"))
        }
    }

    @Test
    fun bareTermOpensVoidTermPanel() {
        val activity = activityText()
        assertTrue("bare :term must dispatch", activity.contains("command == \"term\" -> {"))
        assertTrue("bare :term must open the panel", activity.contains("command == \"term\" -> {\n                openVoidTerm()"))
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
