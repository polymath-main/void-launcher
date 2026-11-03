package com.polymath.voidlauncher.core.terminal

import com.polymath.voidlauncher.core.shell.ShellResult
import com.polymath.voidlauncher.core.shell.VoidShell

/**
 * Adapts [VoidShell] to [TerminalCommandRunner] (migration Cycle 1).
 *
 * This is what VoidTermPanel.executeVia() runs on Android: :term output
 * is now real VoidShell output, not a write-only dispatch to another app.
 * A failed VoidShell run surfaces as exit code -1 with the failure
 * reason as stderr, because VoidShell's Failed type carries no exit code.
 */
class VoidShellRunner(
    private val voidShell: VoidShell
) : TerminalCommandRunner {
    /**
     * impl-02 step 6: the working directory for runs. Blank means
     * VoidShell's default. The panel syncs this from the current
     * session's stored workDir before every run, so a session opened
     * from a folder actually runs there (previously the stored
     * workDir was never read, and every :term ran in filesDir).
     */
    var workDir: String = ""

    override fun run(command: String): TerminalRunResult {
        val result = if (workDir.isBlank()) voidShell.run(command) else voidShell.run(command, workDir)
        return when (result) {
            is ShellResult.Success ->
                TerminalRunResult(result.output, result.error, result.exitCode)
            is ShellResult.Failed ->
                TerminalRunResult("", result.reason, -1)
        }
    }
}
