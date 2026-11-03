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
        onApplyThemeFromFile: ((String) -> Unit)? = null
    ) {
        val isImage = !item.isDirectory && wallpaperService.isImageFile(item.path)
        val isTheme = !item.isDirectory && item.name.lowercase().endsWith(".voidtheme")

        val options = when {
            isTheme -> arrayOf(
                "🎨 Apply & Import Theme",
                "🔗 Share Theme",
                "⚡ Open in VoidTerm (inspect)",
                "📄 Read Text Preview",
                "📋 Copy Full Path",
                "✏️ Rename",
                "🗑 Delete"
            )
            isImage -> arrayOf(
                "🖼 Set as Wallpaper",
                "⚡ Open in VoidTerm (run here)",
                "📄 Read Text Preview",
                "📋 Copy Full Path",
                "✏️ Rename",
                "🗑 Delete"
            )
            else -> arrayOf(
                "⚡ Open in VoidTerm (${if (item.isDirectory) "cd here" else "run here"})",
                "📄 Read Text Preview",
                "📋 Copy Full Path",
                "✏️ Rename",
                "🗑 Delete"
            )
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(item.name)
            .setItems(options) { _, which ->
                when {
                    isTheme -> when (which) {
                        0 -> {
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
                        1 -> shareThemeFile(item.path)
                        2 -> onOpenInVoidTerm(item)
                        3 -> {
                            val preview = fileManager.readTextPreview(item.path, maxLines = 60)
                            onShowPreview("[THEME PREVIEW: ${item.name}]\n\n$preview")
                        }
                        4 -> onCopyPath(item.path)
                        5 -> showRenameDialog(item, onRefreshDirectory)
                        6 -> showDeleteConfirmDialog(item, onRefreshDirectory)
                    }
                    isImage -> when (which) {
                        0 -> onApplyWallpaperFromFile(item.path)
                        1 -> onOpenInVoidTerm(item)
                        2 -> {
                            val preview = fileManager.readTextPreview(item.path, maxLines = 60)
                            onShowPreview("[PREVIEW: ${item.name}]\n\n$preview")
                        }
                        3 -> onCopyPath(item.path)
                        4 -> showRenameDialog(item, onRefreshDirectory)
                        5 -> showDeleteConfirmDialog(item, onRefreshDirectory)
                    }
                    else -> when (which) {
                        0 -> onOpenInVoidTerm(item)
                        1 -> {
                            val preview = fileManager.readTextPreview(item.path, maxLines = 60)
                            onShowPreview("[PREVIEW: ${item.name}]\n\n$preview")
                        }
                        2 -> onCopyPath(item.path)
                        3 -> showRenameDialog(item, onRefreshDirectory)
                        4 -> showDeleteConfirmDialog(item, onRefreshDirectory)
                    }
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
