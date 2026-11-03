package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** impl-02 Step 3 — dead file APIs wired, honestly. Source-text tests
 *  (FileManager/Activity are Android-bound; same UiV2* pattern). */
class NavigationStep3Test {

    private fun read(rel: String): String {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val file = File(root, rel)
        assumeTrue("$rel missing", file.exists())
        return file.readText()
    }

    private fun activityText() =
        read("app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt")

    private fun serviceText() =
        read("app/src/main/java/com/polymath/voidlauncher/core/services/FileOperationsMicroService.kt")

    @Test
    fun createFileIsWiredBehind48dpFileButton() {
        val layout = read("app/src/main/res/layout/activity_void_launcher.xml")
        val idx = layout.indexOf("android:id=\"@+id/btnFilesNewFile\"")
        assertTrue("+FILE button must exist in the files header", idx >= 0)
        val head = layout.substring(idx, minOf(idx + 600, layout.length))
        assertTrue("+FILE button must be 48dp", head.contains("android:minHeight=\"48dp\""))
        assertTrue("+FILE button must be labelled +FILE", head.contains("android:text=\"+FILE\""))
        val activity = activityText()
        assertTrue("Activity must wire the +FILE button", activity.contains("binding.btnFilesNewFile.setOnClickListener"))
        assertTrue("Activity must refresh the directory after creating a file", activity.contains("showCreateFileDialog(currentFilesPath)"))
        assertTrue("create-file dialog must call FileManager.createFile", serviceText().contains("fileManager.createFile(currentPath, name)"))
    }

    @Test
    fun openWithIsOfferedOnlyForNonImageNonDirectoryFiles() {
        val service = serviceText()
        assertTrue(
            "Open with must be gated on non-image, non-directory files",
            // Merge update (not a deletion): theme files gained their own menu
            // (apply/share) in the origin theme work, so Open-with is now
            // additionally excluded for them; non-image, non-directory,
            // non-theme gating is otherwise unchanged.
            service.contains("if (!isImage && !isTheme && !item.isDirectory) options.add(\"🔗 Open with…\" to 6)")
        )
        val activity = activityText()
        assertTrue("Open with must use FileManager.getMimeType", activity.contains("fileManager.getMimeType(fileItem.path)"))
        assertTrue("Open with must use a FileProvider URI, not file://", activity.contains("FileProvider.getUriForFile"))
        assertTrue("Open with must grant read permission", activity.contains("FLAG_GRANT_READ_URI_PERMISSION"))
        assertTrue("Open with must offer a chooser", activity.contains("Intent.createChooser(viewIntent"))
        assertTrue("Open with must toast cleanly when no app can open the file", activity.contains("No app can open this file"))
    }

    @Test
    fun fileProviderIsDeclaredWithVoidAndSharedPaths() {
        val manifest = read("app/src/main/AndroidManifest.xml")
        assertTrue("FileProvider must be declared", manifest.contains("androidx.core.content.FileProvider"))
        assertTrue("FileProvider must follow the runtime package name", manifest.contains("\${applicationId}.fileprovider"))
        assertFalse("FileProvider must not be exported", manifest.contains("android:name=\"androidx.core.content.FileProvider\"\n            android:authorities=\"\${applicationId}.fileprovider\"\n            android:exported=\"true\""))
        val paths = read("app/src/main/res/xml/file_paths.xml")
        assertTrue("FileProvider must expose Void home", paths.contains("<files-path name=\"void\" path=\"void/\" />"))
        assertTrue("FileProvider must expose shared storage", paths.contains("<external-path name=\"shared\" path=\".\" />"))
    }

    @Test
    fun tapBehaviourIsUnchangedByOpenWith() {
        val activity = activityText()
        val start = activity.indexOf("private fun handleFileItemClick(")
        assertTrue("handleFileItemClick must exist", start >= 0)
        val body = activity.substring(start, minOf(start + 800, activity.length))
        assertTrue("tap on a non-image file must still show the text preview", body.contains("readTextPreview(item.path, maxLines = 40)"))
        assertFalse("tap must not open the viewer directly", body.contains("ACTION_VIEW"))
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
