package com.polymath.voidlauncher.core.shim

import com.polymath.voidlauncher.core.process.VoidProcessManager
import com.polymath.voidlauncher.core.process.VoidTask
import com.polymath.voidlauncher.core.shell.VoidShell
import com.polymath.voidlauncher.core.terminal.TerminalCommandRunner
import com.polymath.voidlauncher.core.terminal.TerminalRunResult
import com.polymath.voidlauncher.core.terminal.TerminalSession
import com.polymath.voidlauncher.core.terminal.VoidShellRunner
import java.io.File

/**
 * VoidShim — the transitional v2 compatibility surface (VoidShim v2 plan §2).
 *
 * One named surface per backing store, so call sites stop reaching past
 * the shim into ad-hoc combinations of the backing classes:
 *
 * - [TerminalShim] -> [TerminalSession] (+ [VoidShellRunner] adapter)
 * - [DaemonShim] -> [VoidProcessManager] (task registry, cancel by handle)
 * - [FileShim] -> Void home (app files/void/, the FileManager rule in
 *   pure form so it is unit-testable without Android)
 * - [ConfigShim] -> the Void config store file (current-config export
 *   semantics at file level; JSON validation lives in ConfigSyncService)
 * - [NodeOSShim] -> import-only legacy status (opt-in, never required)
 *
 * Transitional, not a new permanent dependency: the migration rule is
 * one call site at a time, with a test, then the shim method is deleted
 * when no call site remains. Nothing here shells out to kill processes
 * by pattern, and nothing here references another app's private
 * directory: every path is Void-owned or caller-chosen.
 *
 * Pure Kotlin: no Android imports, so the whole surface is unit-testable
 * on the JVM. The Android twins (FileManager.voidHomePath(),
 * ConfigSyncService, NodeOsBridge) implement the same rules behind
 * Context; this file is the pure statement of those rules, and the
 * VoidShimTest equivalence tests prove the shim and the backing
 * classes agree on the same inputs.
 */
object TerminalShim {

    fun newSession(
        id: String,
        workDir: String = "",
        rows: Int = 8,
        cols: Int = 48
    ): TerminalSession = TerminalSession(id, workDir, rows, cols)

    /** Runs [command] in [session] through [runner]; result shape unchanged. */
    fun execute(
        session: TerminalSession,
        command: String,
        runner: TerminalCommandRunner
    ): TerminalRunResult = session.execute(command, runner)

    /** Runs [command] in [session] through [VoidShell] (the :term path). */
    fun executeViaVoidShell(
        session: TerminalSession,
        command: String,
        shell: VoidShell
    ): TerminalRunResult = session.execute(command, VoidShellRunner(shell))
}

object DaemonShim {

    fun create(): VoidProcessManager = VoidProcessManager()

    fun submit(manager: VoidProcessManager, title: String, command: String): VoidTask =
        manager.submit(title, command)

    fun get(manager: VoidProcessManager, taskId: String): VoidTask? =
        manager.get(taskId)

    fun getAll(manager: VoidProcessManager): List<VoidTask> = manager.getAll()

    fun getRunning(manager: VoidProcessManager): List<VoidTask> = manager.getRunning()

    fun markFinished(manager: VoidProcessManager, taskId: String, success: Boolean): Boolean =
        manager.markFinished(taskId, success)

    /** Cancels one task by handle; never by process-name pattern. */
    fun cancel(manager: VoidProcessManager, taskId: String): Boolean =
        manager.cancel(taskId)

    fun cancelRunning(manager: VoidProcessManager): List<VoidTask> =
        manager.cancelRunning()
}

object FileShim {

    /**
     * Void home: app files/void/, created on first use. This is the
     * same rule as FileManager.voidHomePath(), with the files dir
     * passed in so it stays Android-free.
     */
    fun voidHome(filesDir: File): File {
        val dir = File(filesDir, "void")
        try {
            if (!dir.exists()) dir.mkdirs()
        } catch (_: Exception) {
        }
        return dir
    }

    /** True when [path] is Void home itself or lives under it. */
    fun isVoidHomePath(path: String, filesDir: File): Boolean {
        return try {
            val home = voidHome(filesDir).canonicalPath
            val canonical = File(path).canonicalPath
            canonical == home || canonical.startsWith(home + File.separator)
        } catch (_: Exception) {
            false
        }
    }
}

object ConfigShim {

    /** The Void config store file inside Void home. */
    fun configFile(voidHomeDir: File): File = File(voidHomeDir, "config.json")

    /**
     * Exports the CURRENT store file to [dest] (copy, byte for byte).
     * Returns false — and writes nothing — when no store file exists,
     * so defaults are never written over a user's config. This mirrors
     * ConfigSyncService.exportConfig at file level; JSON validation of
     * imports lives in that service, not here.
     */
    fun exportCurrent(storeFile: File, dest: File): Boolean {
        return try {
            if (!storeFile.isFile) return false
            dest.parentFile?.mkdirs()
            storeFile.copyTo(dest, overwrite = true)
            true
        } catch (_: Exception) {
            false
        }
    }
}

object NodeOSShim {

    const val IMPORT_DISABLED_STATUS = "NodeOS import not enabled (opt-in)"

    /** Legacy import is opt-in; the flag is the whole gate. */
    fun importEnabled(flag: Boolean): Boolean = flag

    /**
     * Import-only status: the legacy status is surfaced ONLY when
     * import is enabled, otherwise the neutral not-imported status, so
     * a fresh install never shows a broken legacy card. Mirrors
     * NodeOsBridge.pollIntentStatus without Android.
     */
    fun pollStatus(importEnabled: Boolean, legacyStatus: String?): String =
        if (importEnabled) (legacyStatus ?: IMPORT_DISABLED_STATUS) else IMPORT_DISABLED_STATUS
}
