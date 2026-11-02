package com.polymath.voidlauncher.core.terminal

import com.polymath.voidlauncher.core.shell.ShellResult
import com.polymath.voidlauncher.core.shell.VoidShell

/**
 * Adapts [VoidShell] to [TerminalCommandRunner] (Termux-removal Cycle 1).
 *
 * This is what VoidTermPanel.executeVia() runs on Android: :term output
 * is now real VoidShell output, not a write-only dispatch to another app.
 * A failed VoidShell run surfaces as exit code -1 with the failure
 * reason as stderr, because VoidShell's Failed type carries no exit code.
 */
class VoidShellRunner(
    private val voidShell: VoidShell
) : TerminalCommandRunner {
    override fun run(command: String): TerminalRunResult {
        return when (val result = voidShell.run(command)) {
            is ShellResult.Success ->
                TerminalRunResult(result.output, result.error, result.exitCode)
            is ShellResult.Failed ->
                TerminalRunResult("", result.reason, -1)
        }
    }
}
