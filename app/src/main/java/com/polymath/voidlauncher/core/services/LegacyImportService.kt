package com.polymath.voidlauncher.core.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileOutputStream

/**
 * impl-06 step 9 — SAF legacy import (copy-only).
 *
 * The user picks a source TREE with the system folder picker
 * (ACTION_OPEN_DOCUMENT_TREE, no new permission — the picker grants
 * a read URI grant). This service then copies `workflow.json` and
 * `agy_nodeos.db` from the tree ROOT into Void-owned paths.
 *
 * Hard rules, all enforced here:
 * - COPY ONLY: sources are never moved, deleted, or written to.
 * - NEVER reads another app's private directory at runtime: the
 *   files arrive only through the user's explicit SAF pick.
 * - VoidLauncher does NOT install or run NodeOS; the copies are
 *   read by NodeOsBridge (workflow.json) and AstGraphRenderer
 *   (read-only SQLite) only when nodeOsLegacyImportEnabled is on.
 * - Size guard: a single file over 100 MiB is refused with a clear
 *   error (a NodeOS topology DB over that size is implausible here;
 *   the user can still point config paths at their own copy).
 * - Each copy is tmp + rename in the destination directory, so a
 *   failed copy never leaves a half-written destination behind.
 *
 * Framework DocumentsContract only — no DocumentFile dependency.
 */
class LegacyImportService(private val context: Context) {

    companion object {
        const val WORKFLOW_NAME = "workflow.json"
        const val DB_NAME = "agy_nodeos.db"
        const val MAX_FILE_BYTES = 100L * 1024L * 1024L
    }

    data class ImportResult(
        val copiedWorkflow: Boolean,
        val copiedDb: Boolean,
        val missing: List<String>,
        val error: String? = null
    )

    fun importFromTree(treeUri: Uri, destWorkflow: File, destDb: File): ImportResult {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // A non-persistable grant still allows this one import.
        }
        val found = findChildren(treeUri)
        val missing = mutableListOf<String>()
        var copiedWorkflow = false
        var copiedDb = false
        var error: String? = null
        val workflowUri = found[WORKFLOW_NAME]
        if (workflowUri != null) {
            val ok = copyDocument(workflowUri, destWorkflow)
            if (ok) copiedWorkflow = true else error = "Could not copy $WORKFLOW_NAME"
        } else {
            missing.add(WORKFLOW_NAME)
        }
        val dbUri = found[DB_NAME]
        if (dbUri != null) {
            val ok = copyDocument(dbUri, destDb)
            if (ok) copiedDb = true else error = (error?.let { "$it; " } ?: "") + "Could not copy $DB_NAME"
        } else {
            missing.add(DB_NAME)
        }
        return ImportResult(copiedWorkflow, copiedDb, missing, error)
    }

    /** Maps display names at the tree root to document URIs. */
    private fun findChildren(treeUri: Uri): Map<String, Uri> {
        val out = mutableMapOf<String, Uri>()
        try {
            val treeId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    if (name == WORKFLOW_NAME || name == DB_NAME) {
                        out[name] = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun copyDocument(docUri: Uri, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, "${dest.name}.tmp")
            context.contentResolver.openInputStream(docUri)?.use { input ->
                var total = 0L
                FileOutputStream(tmp).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FILE_BYTES) {
                            tmp.delete()
                            return false
                        }
                        output.write(buf, 0, read)
                    }
                }
            } ?: return false
            if (tmp.renameTo(dest)) true else {
                tmp.delete()
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
