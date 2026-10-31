package com.polymath.voidlauncher.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.polymath.voidlauncher.model.FileItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileManager(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

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
