package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * UI v3 Cycle 2: files + command center port assertions, as
 * layout/activity source-text checks (same JVM-harness pattern as
 * UiV2DrawerTest — no Android layout inflater here, so the
 * XML/activity source is the artefact under test; a device/CI build
 * is still the visual proof).
 *
 * Spec source: UI_V2_SPEC §2(5) files and §2(6) command center.
 * Kept behaviour: real FileManager behaviour and SAF paths, all
 * :commands working, permission/empty states conditional-only.
 */
class UiV2FilesCommandTest {

    private fun read(rel: String): String {
        val root = findRepoRoot()
        assumeTrue("repo sources not found; see LOOP_LOG", root != null)
        val file = File(root, rel)
        assumeTrue("$rel missing", file.exists())
        return file.readText()
    }

    private fun layoutText() = read("app/src/main/res/layout/activity_void_launcher.xml")
    private fun activityText() = read("app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt")
    private fun colorsText() = read("app/src/main/res/values/colors.xml")

    private fun blockFor(text: String, id: String): String {
        val idx = text.indexOf("android:id=\"@+id/$id\"")
        assertTrue("missing id $id", idx >= 0)
        val start = text.lastIndexOf("<TextView", idx)
        return text.substring(start, minOf(text.indexOf("/>", idx) + 2, text.length))
    }

    @Test
    fun filesChipsAreInSpecOrderAt48dp() {
        val text = layoutText()
        val order = listOf("btnChipVoid", "btnChipProjects", "btnChipDownload", "btnChipSdcard")
        val positions = order.map { text.indexOf("android:id=\"@+id/$it\"") }
        assertTrue("all four files chips must exist", positions.all { it >= 0 })
        assertTrue("files chips must be Void, Projects, Downloads, sdcard", positions == positions.sorted())
        for (chip in order) {
            val block = blockFor(text, chip)
            assertTrue("$chip must be 48dp", block.contains("android:layout_height=\"48dp\""))
            assertTrue("$chip must be 12sp", block.contains("android:textSize=\"12sp\""))
        }
    }

    @Test
    fun filesHeaderPathAndControlsUseSpecSizes() {
        val text = layoutText()
        val pathIdx = text.indexOf("android:id=\"@+id/tvFilesCurrentPath\"")
        assertTrue("tvFilesCurrentPath missing", pathIdx >= 0)
        assertTrue(
            "files path line must be 16sp",
            text.substring(pathIdx, minOf(pathIdx + 600, text.length)).contains("android:textSize=\"16sp\"")
        )
        for (btn in listOf("btnFilesUp", "btnFilesNewFolder", "btnFilesClose")) {
            assertTrue("$btn must be a 48dp target", blockFor(text, btn).contains("android:minHeight=\"48dp\""))
        }
        val filesIdx = text.indexOf("android:id=\"@+id/filesBrowserLayout\"")
        val filesHead = text.substring(filesIdx, minOf(filesIdx + 700, text.length))
        assertFalse("files container must not use the cockpit card background", filesHead.contains("bg_cockpit_card"))
        assertTrue("files container must use 16dp screen padding", filesHead.contains("android:padding=\"16dp\""))
        assertFalse(
            "files must not be re-carded at runtime either",
            activityText().contains("applyCardBackground(binding.filesBrowserLayout")
        )
    }

    @Test
    fun filesRowsAre48dpAndStatesAreConditionalOnly() {
        val row = read("app/src/main/res/layout/item_file_entry.xml")
        assertTrue("file rows must be 48dp", row.contains("android:minHeight=\"48dp\""))
        assertTrue("file name must be 14sp", row.contains("android:textSize=\"14sp\""))
        assertTrue("file details must be 12sp", row.contains("android:textSize=\"12sp\""))
        val text = layoutText()
        for (id in listOf("storagePermissionCard", "tvFilesEmptyState")) {
            val idx = text.indexOf("android:id=\"@+id/$id\"")
            assertTrue("$id missing", idx >= 0)
            assertTrue(
                "$id must be gone by default, shown only conditionally",
                text.substring(idx, minOf(idx + 700, text.length)).contains("android:visibility=\"gone\"")
            )
        }
        val activity = activityText()
        assertTrue(
            "permission card must be conditional on storage permission",
            activity.contains("binding.storagePermissionCard.visibility = if (hasStorage) View.GONE else View.VISIBLE")
        )
        assertTrue("empty state must be set from the listing result", activity.contains("binding.tvFilesEmptyState.visibility = View.VISIBLE"))
    }

    @Test
    fun filesChipsKeepRealPathsAndSafBehaviour() {
        val activity = activityText()
        assertTrue("Void chip must open the Void home", activity.contains("loadFilesDirectory(fileManager.voidHomePath())"))
        assertTrue("Projects chip must use the projects path", activity.contains("loadFilesDirectory(FileManager.PATH_PROJECTS)"))
        assertTrue("Downloads chip must use the downloads path", activity.contains("loadFilesDirectory(FileManager.PATH_DOWNLOADS)"))
        assertTrue("sdcard chip must use the sdcard path", activity.contains("loadFilesDirectory(FileManager.PATH_SDCARD)"))
        assertTrue("files listing must go through FileManager", activity.contains("fileManager.listDirectory(targetPath"))
        assertTrue("files paths must be resolved accessibly", activity.contains("fileManager.resolveAccessiblePath(path)"))
    }

    @Test
    fun commandCenterHasInputEightChipsAndOutputOnlyWhenPresent() {
        val text = layoutText()
        val headerIdx = text.indexOf("STILL // COMMAND CENTER")
        assertTrue("command center header missing", headerIdx >= 0)
        assertTrue(
            "command center header must be 16sp",
            text.substring(headerIdx - 600, headerIdx).contains("android:textSize=\"16sp\"")
        )
        assertTrue("command center close must be 48dp", blockFor(text, "btnCommandCenterClose").contains("android:minHeight=\"48dp\""))
        val inputIdx = text.indexOf("android:id=\"@+id/etCommandCenterInput\"")
        assertTrue("command center input missing", inputIdx >= 0)
        assertTrue(
            "command center input must be 14sp",
            text.substring(inputIdx, minOf(inputIdx + 800, text.length)).contains("android:textSize=\"14sp\"")
        )
        val chips = listOf("chipCmdStill", "chipCmdCockpit", "chipCmdFiles", "chipCmdTasks", "chipCmdGraph", "chipCmdPower", "chipCmdNotif", "chipCmdSettings")
        for (chip in chips) {
            val block = blockFor(text, chip)
            assertTrue("$chip must be 48dp", block.contains("android:layout_height=\"48dp\""))
            assertTrue("$chip must be 12sp", block.contains("android:textSize=\"12sp\""))
        }
        val activity = activityText()
        for ((chip, cmd) in chips.zip(listOf(":still", ":cockpit", ":files", ":tasks", ":graph", ":power", ":notif", ":settings"))) {
            assertTrue("$chip must run $cmd", activity.contains("binding.$chip.setOnClickListener { handleCommandCenterChip(\"$cmd\") }"))
        }
        // Output only when present: no default hint text, fresh open
        // clears it, and blank/":" input hides an empty output pane.
        val outputIdx = text.indexOf("android:id=\"@+id/tvCommandCenterOutput\"")
        assertTrue("command center output missing", outputIdx >= 0)
        assertTrue(
            "command center output must have no permanent default text",
            text.substring(outputIdx, minOf(outputIdx + 700, text.length)).contains("android:text=\"\"")
        )
        assertTrue("fresh command center open must clear stale output", activity.contains("binding.tvCommandCenterOutput.text = \"\""))
        assertTrue(
            "empty output pane must stay hidden on blank input",
            activity.contains("if (binding.tvCommandCenterOutput.text.isNullOrEmpty()) View.GONE else View.VISIBLE")
        )
        // Results rows are the same 48dp drawer-style rows.
        val results = read("app/src/main/res/layout/item_app_entry.xml")
        assertTrue("command center result rows must be 48dp", results.contains("android:minHeight=\"48dp\""))
        assertTrue("command center result name must be 14sp", results.contains("android:textSize=\"14sp\""))
    }

    @Test
    fun commandCenterKeepsAllColonCommandsWorking() {
        val activity = activityText()
        assertTrue("command submit must route :commands", activity.contains("handleCommandMode(q.substring(1).trim())"))
        assertTrue("command submit must route bare integers", activity.contains("bareIntegerRouter.routeInteger(integerCommand)"))
        assertTrue("command submit must launch best app matches", activity.contains("launchApp(bestMatch)"))
        assertTrue("command center results must filter apps", activity.contains("appIndexer.filterApps(text, maxResults = 10)"))
        // Colours used by the files/command blocks must exist in colors.xml.
        val colors = colorsText()
        val filesIdx = layoutText().indexOf("android:id=\"@+id/filesBrowserLayout\"")
        val commandIdx = layoutText().indexOf("android:id=\"@+id/stillCommandCenterLayout\"")
        val combined = layoutText().substring(filesIdx, commandIdx) + layoutText().substring(commandIdx)
        for (color in Regex("@color/([A-Za-z0-9_]+)").findAll(combined).map { it.groupValues[1] }.toSet()) {
            assertTrue("files/command colour $color must be defined in colors.xml", colors.contains("name=\"$color\""))
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
