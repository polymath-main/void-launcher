package com.polymath.voidlauncher.core.terminal

/**
 * TerminalSession — one line-mode terminal session (refined plan, Phase 2).
 *
 * Unlike the old VoidTermPanel.execute(), which dispatched out-of-process and
 * never saw output, a session runs a command through a [TerminalCommandRunner]
 * and appends the real output (plus an exit marker on failure) to its buffer.
 * The runner is injected so tests use a fake and Android wires VoidShell
 * in a later, emulator-verified step — the Activity does not switch to
 * this path in Cycle 3.
 */
data class TerminalRunResult(
    val output: String,
    val error: String,
    val exitCode: Int
)

fun interface TerminalCommandRunner {
    fun run(command: String): TerminalRunResult
}

sealed interface TerminalSessionState {
    data object Idle : TerminalSessionState
    data object Running : TerminalSessionState
    data class Exited(val exitCode: Int) : TerminalSessionState
    data class Failed(val reason: String) : TerminalSessionState
}

class TerminalSession(
    val id: String,
    val workDir: String = "",
    rows: Int = 8,
    cols: Int = 48
) {
    val buffer = TerminalBuffer(rows, cols)
    var state: TerminalSessionState = TerminalSessionState.Idle
        private set

    private val _history = mutableListOf<String>()
    val history: List<String> get() = _history.toList()

    fun execute(command: String, runner: TerminalCommandRunner): TerminalRunResult {
        if (command.isBlank()) {
            state = TerminalSessionState.Failed("empty command")
            return TerminalRunResult("", "empty command", -1)
        }
        _history.add(command)
        buffer.append(">_ $command")
        state = TerminalSessionState.Running
        val result = try {
            runner.run(command)
        } catch (e: Exception) {
            TerminalRunResult("", e.message ?: "runner failed", -1)
        }
        if (result.output.isNotBlank()) buffer.append(result.output)
        if (result.error.isNotBlank()) buffer.append("stderr: " + result.error)
        state = if (result.exitCode == 0) {
            TerminalSessionState.Exited(0)
        } else {
            buffer.append("[exit ${result.exitCode}]")
            TerminalSessionState.Exited(result.exitCode)
        }
        return result
    }

    fun clear() {
        buffer.clear()
        state = TerminalSessionState.Idle
    }
}
