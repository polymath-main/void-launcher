package com.polymath.voidlauncher.core

import android.content.Context
import com.polymath.voidlauncher.core.shell.ShellPort
import com.polymath.voidlauncher.core.terminal.TerminalBuffer
import com.polymath.voidlauncher.core.terminal.TerminalCommandRunner
import com.polymath.voidlauncher.core.terminal.TerminalRunResult
import com.polymath.voidlauncher.core.terminal.TerminalSession

/**
 * VoidTerm Embedded Panel (F-14).
 *
 * Coordinates an in-cockpit terminal line buffer, enabling direct
 * command dispatch and live output streaming without switching apps.
 *
 * Cycle 3: the buffer is now TerminalBuffer (pure, tested), and a
 * TerminalSession is available for real output. The legacy execute()
 * path (Termux dispatch, write-only) is kept unchanged for the
 * Activity in this cycle; executeVia() is the new path, to be wired
 * to VoidShell in an emulator-verified step, not blindly here.
 */
class VoidTermPanel(
    private val context: Context,
    private val shellPort: ShellPort
) {
    private val buffer = TerminalBuffer()
    private val session = TerminalSession(id = "voidterm-main")

    var isVisible: Boolean = false

    /** Visible viewport height in rows (config: `voidterm_rows`). */
    var rows: Int
        get() = buffer.rows
        set(value) {
            buffer.rows = value
            session.buffer.rows = value
        }

    /** Terminal column width used for soft-wrapping output (config: `voidterm_cols`). */
    var cols: Int
        get() = buffer.cols
        set(value) {
            buffer.cols = value
            session.buffer.cols = value
        }

    /** Kept for callers that read the old maxLines field. */
    val maxLines: Int get() = buffer.maxLines

    fun appendOutput(text: String) {
        buffer.append(text)
    }

    fun execute(command: String, inBackground: Boolean = true) {
        appendOutput(">_ $command")
        shellPort.dispatchCommand(command, inBackground = inBackground)
        if (inBackground) {
            appendOutput("[DISPATCHED HEADLESS TO TERMUX]")
        }
    }

    /**
     * New path: run through a TerminalCommandRunner (VoidShell on
     * Android) and append the real output to both the panel buffer
     * and the session buffer. Returns the run result for the caller.
     */
    fun executeVia(command: String, runner: TerminalCommandRunner): TerminalRunResult {
        val result = session.execute(command, runner)
        // Mirror session output into the panel buffer so existing
        // showTerminalOutput(getRenderedBuffer()) callers see it.
        buffer.append(">_ $command")
        if (result.output.isNotBlank()) buffer.append(result.output)
        if (result.error.isNotBlank()) buffer.append("stderr: " + result.error)
        if (result.exitCode != 0) buffer.append("[exit ${result.exitCode}]")
        return result
    }

    fun getRenderedBuffer(): String {
        return buffer.rendered()
    }

    fun getSession(): TerminalSession = session

    fun clear() {
        buffer.clear()
        session.clear()
    }
}
