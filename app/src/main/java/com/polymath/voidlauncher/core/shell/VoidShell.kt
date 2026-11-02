package com.polymath.voidlauncher.core.shell

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Result of a VoidShell run. Output is capped by the runner so a
 * runaway command cannot fill memory with stdout.
 */
data class ProcessResult(
    val output: String,
    val error: String,
    val exitCode: Int
)

sealed interface ShellResult {
    data class Success(val output: String, val error: String, val exitCode: Int) : ShellResult
    data class Failed(val reason: String) : ShellResult
}

/**
 * Injectable process boundary, so VoidShell can be unit-tested with a
 * fake runner and so Android (/system/bin/sh) and JVM (/bin/sh)
 * callers differ only in the shell path they pass in.
 */
fun interface ProcessRunner {
    fun run(shellPath: String, command: String, workDir: String): ProcessResult
}

/**
 * Default runner: ProcessBuilder + a hard timeout. Merges nothing —
 * stdout and stderr are captured separately. Kills the process on
 * timeout and reports it as exit code -1 with an error message.
 */
class DefaultProcessRunner(
    private val timeoutSeconds: Long = 30
) : ProcessRunner {
    override fun run(shellPath: String, command: String, workDir: String): ProcessResult {
        return try {
            val process = ProcessBuilder(shellPath, "-c", command)
                .directory(File(workDir).takeIf { it.exists() })
                .redirectErrorStream(false)
                .start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ProcessResult("", "VoidShell: command timed out after ${timeoutSeconds}s", -1)
            }
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            ProcessResult(output, error, process.exitValue())
        } catch (e: Exception) {
            ProcessResult("", "VoidShell: ${e.message}", -1)
        }
    }
}

/**
 * VoidShell — the Void-owned headless shell (refined plan, Phase 1).
 *
 * Pure Kotlin: no Android imports, so it is unit-testable on the JVM.
 * It deliberately does NOT try to be a full terminal: one-shot
 * commands with streamed (here: captured) output, a default workdir,
 * and an output cap. Interactive sessions belong to VoidTermPanel's
 * successor, not to this class.
 *
 * Sandbox note for this cycle: VoidShell does not itself enforce a
 * workspace sandbox — callers pass the workdir, and the refined plan's
 * sandbox + approval gate is a later cycle. Do not pass another app's
 * private path as workdir; it is unreadable from the app sandbox.
 */
class VoidShell(
    private val runner: ProcessRunner = DefaultProcessRunner(),
    private val shellPath: String = "/system/bin/sh",
    private val defaultWorkDir: String = "",
    private val maxOutputChars: Int = 16384
) {
    fun run(command: String, workDir: String = defaultWorkDir): ShellResult {
        if (command.isBlank()) return ShellResult.Failed("empty command")
        val result = runner.run(shellPath, command, workDir)
        val output = result.output.take(maxOutputChars)
        val error = result.error.take(maxOutputChars)
        return if (result.exitCode == 0) {
            ShellResult.Success(output, error, result.exitCode)
        } else {
            // Still return output on non-zero exit so callers can show it,
            // but signal failure through the type.
            ShellResult.Failed(
                "exit ${result.exitCode}" +
                    (if (error.isNotBlank()) ": $error" else "") +
                    (if (output.isNotBlank()) " | output: $output" else "")
            )
        }
    }
}
