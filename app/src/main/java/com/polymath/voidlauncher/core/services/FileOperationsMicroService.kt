package com.polymath.voidlauncher.core.services

import android.app.Activity
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.voidlauncher.core.FileManager
import com.polymath.voidlauncher.model.FileItem

/**
 * Micro-service encapsulating VFS File Browser operations:
 * VoidTerm context launch, text preview, path copy, image wallpaper assignment,
 * file rename, file delete, and directory creation.
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
        onRefreshDirectory: () -> Unit
    ) {
        val isImage = !item.isDirectory && wallpaperService.isImageFile(item.path)
        val options = if (isImage) {
            arrayOf(
                "🖼 Set as Wallpaper",
                "⚡ Open in VoidTerm (run here)",
                "📄 Read Text Preview",
                "📋 Copy Full Path",
                "✏️ Rename",
                "🗑 Delete"
            )
        } else {
            arrayOf(
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
                val adjusted = if (isImage) which else which + 1
                when (adjusted) {
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
            }
            .show()
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
