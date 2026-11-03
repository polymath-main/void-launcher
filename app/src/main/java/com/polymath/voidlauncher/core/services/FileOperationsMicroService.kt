package com.polymath.voidlauncher.core.services

import android.app.Activity
import android.content.Intent
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.voidlauncher.core.FileManager
import com.polymath.voidlauncher.core.ThemeEngine
import com.polymath.voidlauncher.model.FileItem
import com.polymath.voidlauncher.model.VoidThemeManifest
import java.io.File

/**
 * Micro-service encapsulating VFS File Browser operations:
 * VoidTerm context launch, text preview, path copy, image wallpaper assignment,
 * file rename, file delete, directory creation, and `.voidtheme` theme import & share actions.
 */
class FileOperationsMicroService(
    private val activity: Activity,
    private val fileManager: FileManager,
    private val wallpaperService: WallpaperMicroService
) {

    fun showFileOperationsDialog(
        item: FileItem,
        onOpenInVoidTerm: (FileItem) -> Unit,
        onShowPreview: (String) -> Unit,
        onCopyPath: (String) -> Unit,
        onApplyWallpaperFromFile: (String) -> Unit,
        onRefreshDirectory: () -> Unit,
        // Merge: BOTH callbacks — impl-02 step 3 onOpenWith (default no-op
        // keeps older call sites compiling; the Activity wires it to an
        // ACTION_VIEW chooser) AND origin's onApplyThemeFromFile (nullable;
        // null falls back to in-service import).
        onOpenWith: (FileItem) -> Unit = {},
        onApplyThemeFromFile: ((String) -> Unit)? = null
    ) {
        val isImage = !item.isDirectory && wallpaperService.isImageFile(item.path)
        val isTheme = !item.isDirectory && item.name.lowercase().endsWith(".voidtheme")
        // Merge: impl-02's (label, code) pairs — codes stay stable when an
        // entry is added for one file type only — plus origin's theme
        // entries (codes 10/11). Common entries keep impl-02 codes 0-6.
        val options = mutableListOf<Pair<String, Int>>()
        if (isTheme) {
            options.add("🎨 Apply & Import Theme" to 10)
            options.add("🔗 Share Theme" to 11)
        } else if (isImage) {
            options.add("🖼 Set as Wallpaper" to 0)
        }
        options.add("⚡ Open in VoidTerm (${if (item.isDirectory) "cd here" else "run here"})" to 1)
        options.add("📄 Read Text Preview" to 2)
        options.add("📋 Copy Full Path" to 3)
        options.add("✏️ Rename" to 4)
        options.add("🗑 Delete" to 5)
        // impl-02 step 3: "Open with…" only for non-image, non-directory,
        // non-theme files (images/theme already open this dialog).
        if (!isImage && !isTheme && !item.isDirectory) options.add("🔗 Open with…" to 6)

        MaterialAlertDialogBuilder(activity)
            .setTitle(item.name)
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                when (options[which].second) {
                    0 -> onApplyWallpaperFromFile(item.path)
                    1 -> onOpenInVoidTerm(item)
                    2 -> {
                        val preview = fileManager.readTextPreview(item.path, maxLines = 60)
                        onShowPreview(if (isTheme) "[THEME PREVIEW: ${item.name}]\n\n$preview" else "[PREVIEW: ${item.name}]\n\n$preview")
                    }
                    3 -> onCopyPath(item.path)
                    4 -> showRenameDialog(item, onRefreshDirectory)
                    5 -> showDeleteConfirmDialog(item, onRefreshDirectory)
                    6 -> onOpenWith(item)
                    10 -> {
                        if (onApplyThemeFromFile != null) {
                            onApplyThemeFromFile(item.path)
                        } else {
                            val result = ThemeEngine.importFromUriOrString(item.path)
                            result.onSuccess { manifest ->
                                Toast.makeText(activity, "Imported theme: ${manifest.name}", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(activity, "Failed to import theme: ${err.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    11 -> shareThemeFile(item.path)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareThemeFile(filePath: String) {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            Toast.makeText(activity, "Cannot read theme file", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val manifest = VoidThemeManifest.fromJsonString(file.readText(), trusted = false)
            val intent = ThemeEngine.exportShareIntent(activity, manifest)
            activity.startActivity(Intent.createChooser(intent, "Share Theme: ${manifest.name}"))
        } catch (e: Exception) {
            Toast.makeText(activity, "Error sharing theme: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun showRenameDialog(item: FileItem, onRefreshDirectory: () -> Unit) {
        val input = EditText(activity).apply {
            setText(item.name)
            setSelection(item.name.length)
        }
        AlertDialog.Builder(activity)
            .setTitle("Rename ${item.name}")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != item.name) {
                    val ok = fileManager.renameFile(item.path, newName)
                    if (ok) {
                        Toast.makeText(activity, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                        onRefreshDirectory()
                    } else {
                        Toast.makeText(activity, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showDeleteConfirmDialog(item: FileItem, onRefreshDirectory: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Delete ${item.name}?")
            .setMessage("This operation is permanent.")
            .setPositiveButton("Delete") { _, _ ->
                val ok = fileManager.deleteFile(item.path)
                if (ok) {
                    Toast.makeText(activity, "Deleted: ${item.name}", Toast.LENGTH_SHORT).show()
                    onRefreshDirectory()
                } else {
                    Toast.makeText(activity, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // impl-02 step 3: wires the previously dead FileManager.createFile()
    // behind a +FILE affordance, mirroring the +DIR dialog exactly.
    fun showCreateFileDialog(currentPath: String, onRefreshDirectory: () -> Unit) {
        val input = EditText(activity).apply {
            hint = "File name"
        }
        AlertDialog.Builder(activity)
            .setTitle("Create New File")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val ok = fileManager.createFile(currentPath, name)
                    if (ok) {
                        Toast.makeText(activity, "Created $name", Toast.LENGTH_SHORT).show()
                        onRefreshDirectory()
                    } else {
                        Toast.makeText(activity, "Failed to create file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showCreateDirectoryDialog(currentPath: String, onRefreshDirectory: () -> Unit) {
        val input = EditText(activity).apply {
            hint = "Folder name"
        }
        AlertDialog.Builder(activity)
            .setTitle("Create New Directory")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val ok = fileManager.createFolder(currentPath, name)
                    if (ok) {
                        Toast.makeText(activity, "Created $name", Toast.LENGTH_SHORT).show()
                        onRefreshDirectory()
                    } else {
                        Toast.makeText(activity, "Failed to create directory", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
