package com.polymath.voidlauncher.core

import android.content.Context
import java.io.File

/**
 * LegacyTermuxMigrator — the ONLY place a legacy external-terminal path may appear
 * (migration Cycle 2, kept through Cycle 3's proof as the named
 * opt-in exception).
 *
 * This is an opt-in, user-initiated legacy import helper, NOT a
 * runtime path: nothing in launch, shell, thermal, agent, or files
 * browsing calls it. It COPIES files from the legacy Termux home into
 * a caller-chosen destination (on Android, a SAF-granted directory
 * chosen by the user in Settings) and NEVER moves or deletes the
 * originals — after a copy, the source files are left untouched, so a
 * failed or partial import loses nothing.
 *
 * Honest limit: another app's private directory
 * (/data/data/com.termux/...) is normally unreadable to this app
 * under Android DAC, so on most devices listLegacyFiles() simply
 * returns an empty list and copyFile() returns false. The migrator
 * never escalates privileges to work around that; the SAF flow (user
 * picks the source tree) is the supported import route, and this
 * helper only copies what the OS actually lets it read.
 */
class LegacyTermuxMigrator(private val context: Context) {

    companion object {
        const val LEGACY_TERMUX_HOME = "/data/data/com.termux/files/home"
    }

    /** Lists readable files directly under the legacy home, or empty if unreadable. */
    fun listLegacyFiles(): List<File> {
        return try {
            val dir = File(LEGACY_TERMUX_HOME)
            if (!dir.isDirectory) return emptyList()
            dir.listFiles()?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Copies [source] into [destDir] (created if needed), preserving the
     * file name. Returns false unless the source is a readable file
     * under the legacy home and the destination is outside it.
     * The source is never moved or deleted.
     */
    fun copyFile(source: File, destDir: File): Boolean {
        return try {
            if (!source.isFile || !source.canRead()) return false
            val canonicalSource = source.canonicalPath
            if (!canonicalSource.startsWith(File(LEGACY_TERMUX_HOME).canonicalPath + File.separator)) {
                return false
            }
            val canonicalDestDir = destDir.canonicalPath
            if (canonicalDestDir.startsWith(File(LEGACY_TERMUX_HOME).canonicalPath)) {
                return false
            }
            if (!destDir.exists()) destDir.mkdirs()
            val dest = File(destDir, source.name)
            source.copyTo(dest, overwrite = true)
            true
        } catch (_: Exception) {
            false
        }
    }
}
