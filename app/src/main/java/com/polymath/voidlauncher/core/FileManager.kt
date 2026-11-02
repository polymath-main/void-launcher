package com.polymath.voidlauncher.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.polymath.voidlauncher.model.FileItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileManager(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    companion object {
        const val PATH_SDCARD = "/storage/emulated/0"
        const val PATH_DOWNLOADS = "/storage/emulated/0/Download"
        const val PATH_PROJECTS = "/storage/emulated/0/Projects"
    }

    /**
     * Migration Cycle 2: the Void-owned workspace root —
     * app files/void/. Created on first use. This is the default
     * workspace for files the launcher itself owns; a user-chosen
     * shared-storage workspace via SAF is the alternative, never
     * another app's private directory (unreadable under Android DAC).
     */
    fun voidHomePath(): String {
        val dir = File(context.filesDir, "void")
        try {
            if (!dir.exists()) dir.mkdirs()
        } catch (_: Exception) {}
        return dir.absolutePath
    }

    /**
     * Checks whether Void Launcher has full filesystem access to /sdcard.
     * On Android 11+ (API 30+), requires MANAGE_EXTERNAL_STORAGE.
     */
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Launches the system permission dialog or settings page for All Files Access.
     */
    fun requestStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivity(intent)
            }
        } else {
            activity.requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                1001
            )
        }
    }

    /**
     * Verifies if a given directory path is readable.
     * If unreadable or pointing to a restricted path, resolves to the best available fallback.
     * Void home is tried first: it is always app-owned and readable.
     */
    fun resolveAccessiblePath(initialPath: String): String {
        val target = File(initialPath)
        if (target.exists() && target.isDirectory && target.canRead() && target.listFiles() != null) {
            return target.absolutePath
        }

        // Test fallback locations in priority order
        val candidates = listOf(
            File(voidHomePath()),
            File(PATH_PROJECTS),
            File(PATH_DOWNLOADS),
            File(PATH_SDCARD),
            Environment.getExternalStorageDirectory(),
            context.getExternalFilesDir(null)
        )

        for (candidate in candidates) {
            if (candidate != null && candidate.exists() && candidate.canRead() && candidate.listFiles() != null) {
                return candidate.absolutePath
            }
        }

        return PATH_SDCARD
    }

    fun listDirectory(dirPath: String, showHidden: Boolean = true): List<FileItem> {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }

        val files = dir.listFiles() ?: return emptyList()

        return files.filter { file ->
            if (!showHidden && file.name.startsWith(".")) false else true
        }.map { file ->
            val isDir = file.isDirectory
            val childCount = if (isDir) (file.list()?.size ?: 0) else 0
            val ext = if (!isDir) file.extension.lowercase(Locale.ROOT) else ""

            FileItem(
                name = file.name,
                path = file.absolutePath,
                isDirectory = isDir,
                sizeBytes = if (isDir) 0L else file.length(),
                lastModifiedMs = file.lastModified(),
                childCount = childCount,
                extension = ext
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
    }

    fun deleteFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (_: Exception) {
            false
        }
    }

    fun renameFile(oldPath: String, newName: String): Boolean {
        return try {
            val oldFile = File(oldPath)
            val parent = oldFile.parentFile ?: return false
            val newFile = File(parent, newName)
            oldFile.renameTo(newFile)
        } catch (_: Exception) {
            false
        }
    }

    fun createFolder(parentPath: String, name: String): Boolean {
        return try {
            val newDir = File(parentPath, name)
            newDir.mkdirs()
        } catch (_: Exception) {
            false
        }
    }

    fun createFile(parentPath: String, name: String): Boolean {
        return try {
            val newFile = File(parentPath, name)
            newFile.createNewFile()
        } catch (_: Exception) {
            false
        }
    }

    fun readTextPreview(path: String, maxLines: Int = 40): String {
        return try {
            val file = File(path)
            if (!file.exists() || file.isDirectory) return "[Directory]"
            val lines = file.bufferedReader().useLines { linesSeq ->
                linesSeq.take(maxLines).toList()
            }
            lines.joinToString("\n")
        } catch (e: Exception) {
            "[Cannot preview: ${e.message}]"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
    }

    fun formatDate(timestampMs: Long): String {
        return dateFormat.format(Date(timestampMs))
    }

    fun getMimeType(path: String): String {
        val ext = File(path).extension.lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }
}
