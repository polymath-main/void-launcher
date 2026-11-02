package com.polymath.voidlauncher.core.shell

import java.io.File
import java.io.InputStream
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
 * stdout and stderr are captured separately.
 *
 * Streaming fix (impl-01 step 1): stdout and stderr are drained on
 * reader threads WHILE the process runs, never after waitFor().
 * The old code called waitFor() first and then read both streams, so
 * a command filling a pipe buffer (>~64 KiB) deadlocked until the
 * 30 s timeout killed it. Reader threads also deliver complete lines
 * to [runStreaming] callbacks as they arrive; callbacks run on the
 * reader threads, never on the UI thread — callers marshal to Main,
 * as the Activity already does for final results.
 */
class DefaultProcessRunner(
    private val timeoutSeconds: Long = 30
) : ProcessRunner {

    /** Hard cap on what a single stream keeps in memory while draining. */
    companion object {
        const val STREAM_KEEP_CAP_CHARS = 1_048_576
    }

    override fun run(shellPath: String, command: String, workDir: String): ProcessResult {
        return runInternal(shellPath, command, workDir, onStdoutLine = null, onStderrLine = null)
    }

    /**
     * Same as [run], but complete stdout/stderr lines are delivered to
     * the callbacks as they arrive. The final [ProcessResult] is still
     * returned, so streaming callers get both incremental and final
     * output. Callbacks are invoked on reader threads.
     */
    fun runStreaming(
        shellPath: String,
        command: String,
        workDir: String,
        onStdoutLine: ((String) -> Unit)? = null,
        onStderrLine: ((String) -> Unit)? = null
    ): ProcessResult {
        return runInternal(shellPath, command, workDir, onStdoutLine, onStderrLine)
    }

    private fun runInternal(
        shellPath: String,
        command: String,
        workDir: String,
        onStdoutLine: ((String) -> Unit)?,
        onStderrLine: ((String) -> Unit)?
    ): ProcessResult {
        return try {
            val process = ProcessBuilder(shellPath, "-c", command)
                .directory(File(workDir).takeIf { it.exists() })
                .redirectErrorStream(false)
                .start()
            val outDrain = StreamDrainer(process.inputStream, onStdoutLine)
            val errDrain = StreamDrainer(process.errorStream, onStderrLine)
            val outThread = Thread(outDrain, "voidshell-stdout").apply { isDaemon = true; start() }
            val errThread = Thread(errDrain, "voidshell-stderr").apply { isDaemon = true; start() }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                outThread.join(2000)
                errThread.join(2000)
                return ProcessResult("", "VoidShell: command timed out after ${timeoutSeconds}s", -1)
            }
            // Streams close when the process exits; join so no output is lost.
            outThread.join()
            errThread.join()
            ProcessResult(outDrain.text(), errDrain.text(), process.exitValue())
        } catch (e: Exception) {
            ProcessResult("", "VoidShell: ${e.message}", -1)
        }
    }

    /**
     * Drains one stream in chunks (verbatim, so a final line without a
     * trailing newline keeps its exact bytes), splits complete lines
     * for the callback, and keeps at most [STREAM_KEEP_CAP_CHARS] chars
     * in memory — it keeps draining past the cap so the process can
     * never block on a full pipe, it just stops accumulating.
     */
    private class StreamDrainer(
        private val stream: InputStream,
        private val onLine: ((String) -> Unit)?
    ) : Runnable {
        private val kept = StringBuilder()
        private val pending = StringBuilder()

        fun text(): String = synchronized(kept) { kept.toString() }

        override fun run() {
            try {
                val buf = CharArray(8192)
                val reader = stream.bufferedReader()
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    val chunk = String(buf, 0, n)
                    synchronized(kept) {
                        if (kept.length < STREAM_KEEP_CAP_CHARS) {
                            kept.append(chunk, 0, minOf(chunk.length, STREAM_KEEP_CAP_CHARS - kept.length))
                        }
                    }
                    if (onLine != null) {
                        pending.append(chunk)
                        var nl: Int
                        while (pending.indexOf("\n").also { nl = it } >= 0) {
                            val line = pending.substring(0, nl)
                            pending.delete(0, nl + 1)
                            try { onLine.invoke(line) } catch (_: Exception) { }
                        }
                    }
                }
                if (onLine != null && pending.isNotEmpty()) {
                    try { onLine.invoke(pending.toString()) } catch (_: Exception) { }
                }
            } catch (_: Exception) {
                // Stream closed (e.g. process killed on timeout): draining ends.
            }
        }
    }
}

/**
 * VoidShell — the Void-owned headless shell (refined plan, Phase 1).
 *
 * Pure Kotlin: no Android imports, so it is unit-testable on the JVM.
 * It deliberately does NOT try to be a full terminal: one-shot
 * commands with streamed output, a default workdir, and an output cap.
 * Interactive sessions belong to VoidTermPanel's successor, not to
 * this class.
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
        return toShellResult(result)
    }

    /**
     * Streaming variant of [run]: complete output lines are delivered
     * to [onStdoutLine]/[onStderrLine] as they arrive (on reader
     * threads when the runner is [DefaultProcessRunner]; for any other
     * runner the final output is split into lines after completion).
     * The returned [ShellResult] is identical in shape to [run]'s.
     */
    fun runStreaming(
        command: String,
        workDir: String = defaultWorkDir,
        onStdoutLine: ((String) -> Unit)? = null,
        onStderrLine: ((String) -> Unit)? = null
    ): ShellResult {
        if (command.isBlank()) return ShellResult.Failed("empty command")
        val defaultRunner = runner as? DefaultProcessRunner
        val result = if (defaultRunner != null) {
            defaultRunner.runStreaming(shellPath, command, workDir, onStdoutLine, onStderrLine)
        } else {
            val r = runner.run(shellPath, command, workDir)
            if (onStdoutLine != null) r.output.split("\n").filter { it.isNotEmpty() }.forEach { onStdoutLine(it) }
            if (onStderrLine != null) r.error.split("\n").filter { it.isNotEmpty() }.forEach { onStderrLine(it) }
            r
        }
        return toShellResult(result)
    }

    private fun toShellResult(result: ProcessResult): ShellResult {
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
