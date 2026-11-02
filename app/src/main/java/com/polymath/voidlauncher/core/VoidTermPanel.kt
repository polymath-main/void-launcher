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
 * Termux-removal Cycle 1: when [terminalRunner] is set (the Activity
 * wires it to VoidShell), execute() runs through it via executeVia()
 * and shows real output. The legacy ShellPort dispatch path remains
 * only as a fallback while other callers migrate; it is not used by
 * the Activity any more.
 */
class VoidTermPanel(
    private val context: Context,
    private val shellPort: ShellPort
) {
    private val buffer = TerminalBuffer()
    private val session = TerminalSession(id = "voidterm-main")

    var isVisible: Boolean = false

    /**
     * Runner for the new path. Set by the Activity to a VoidShell
     * adapter; when set, execute() uses it instead of ShellPort dispatch.
     */
    var terminalRunner: TerminalCommandRunner? = null

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
        val runner = terminalRunner
        if (runner != null) {
            executeVia(command, runner)
            return
        }
        appendOutput(">_ $command")
        shellPort.dispatchCommand(command, inBackground = inBackground)
        if (inBackground) {
            appendOutput("[DISPATCHED HEADLESS]")
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
