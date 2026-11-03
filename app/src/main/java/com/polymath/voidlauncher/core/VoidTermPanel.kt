package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.core.shell.ShellPort
import com.polymath.voidlauncher.core.terminal.TerminalCommandRunner
import com.polymath.voidlauncher.core.terminal.VoidShellRunner
import com.polymath.voidlauncher.core.terminal.TerminalRunResult
import com.polymath.voidlauncher.core.terminal.TerminalSession

/**
 * VoidTerm Embedded Panel (F-14).
 *
 * Coordinates an in-cockpit terminal line buffer, enabling direct
 * command dispatch and live output streaming without switching apps.
 *
 * impl-01 step 4: the panel no longer keeps a shadow buffer. There is
 * exactly one buffer per session, owned by that [TerminalSession];
 * the panel renders the current session's buffer, so the two buffers
 * can no longer diverge and rows/cols need no manual sync. The panel
 * also holds named sessions (default "voidterm-main") instead of a
 * single hardcoded one: create/switch/list/close. The legacy
 * ShellPort dispatch path remains only as a fallback when no runner
 * is set; it is not used by the Activity any more.
 */
class VoidTermPanel(
    private val shellPort: ShellPort
) {
    companion object {
        const val DEFAULT_SESSION_ID = "voidterm-main"
    }

    private val sessions = LinkedHashMap<String, TerminalSession>()
    private var currentSessionId: String = DEFAULT_SESSION_ID

    init {
        sessions[DEFAULT_SESSION_ID] = TerminalSession(id = DEFAULT_SESSION_ID)
    }

    /** The session currently shown. */
    fun getSession(): TerminalSession = sessions.getValue(currentSessionId)

    /** Creates (or returns, if it already exists) a named session. */
    fun createSession(id: String, workDir: String = ""): TerminalSession {
        return sessions.getOrPut(id) {
            TerminalSession(id = id, workDir = workDir, rows = rows, cols = cols)
        }
    }

    /**
     * impl-02 step 6: creates (or reuses) and switches to the session
     * for a directory, so "Open in VoidTerm" from the files browser
     * lands the user in that directory. The session id embeds the
     * directory, so reopening the same folder reuses its buffer and
     * history instead of spawning a duplicate session.
     */
    fun openDirectorySession(dir: String): TerminalSession {
        val session = createSession("dir:$dir", workDir = dir)
        switchSession(session.id)
        return session
    }

    /** Switches the shown session. Returns false for an unknown id. */
    fun switchSession(id: String): Boolean {
        if (!sessions.containsKey(id)) return false
        currentSessionId = id
        return true
    }

    fun listSessions(): List<String> = sessions.keys.toList()

    /**
     * Closes a session. Returns false for an unknown id or for the
     * last remaining session (the panel always keeps at least one).
     * Closing the current session switches to the oldest remaining one.
     */
    fun closeSession(id: String): Boolean {
        if (!sessions.containsKey(id) || sessions.size <= 1) return false
        sessions.remove(id)
        if (currentSessionId == id) {
            currentSessionId = sessions.keys.first()
        }
        return true
    }

    var isVisible: Boolean = false

    /**
     * Runner for the new path. Set by the Activity to a VoidShell
     * adapter; when set, execute() uses it instead of ShellPort dispatch.
     */
    var terminalRunner: TerminalCommandRunner? = null

    /** Visible viewport height in rows (config: `voidterm_rows`). */
    var rows: Int
        get() = getSession().buffer.rows
        set(value) {
            for (session in sessions.values) session.buffer.rows = value
        }

    /** Terminal column width used for soft-wrapping output (config: `voidterm_cols`). */
    var cols: Int
        get() = getSession().buffer.cols
        set(value) {
            for (session in sessions.values) session.buffer.cols = value
        }

    /** Kept for callers that read the old maxLines field. */
    val maxLines: Int get() = getSession().buffer.maxLines

    fun appendOutput(text: String) {
        getSession().buffer.append(text)
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
     * Android). Output lives only in the session buffer; the panel
     * renders that same buffer, so no mirroring is needed or done.
     * Returns the run result for the caller.
     */
    fun executeVia(command: String, runner: TerminalCommandRunner): TerminalRunResult {
        // impl-02 step 6: sync the runner's workdir from the current
        // session before every run — otherwise a directory session
        // would still execute in VoidShell's default directory.
        if (runner is VoidShellRunner) runner.workDir = getSession().workDir
        return getSession().execute(command, runner)
    }

    fun getRenderedBuffer(): String {
        return getSession().buffer.rendered()
    }

    fun clear() {
        getSession().clear()
    }
}
