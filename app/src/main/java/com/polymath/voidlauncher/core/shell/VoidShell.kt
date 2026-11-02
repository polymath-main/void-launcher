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
    val exitCode: Int,
    /** True only when the runner killed the process on timeout. */
    val timedOut: Boolean = false
)

/**
 * Observability record for one shell run (impl-01 step 2). It carries
 * lengths, never output text: output can contain secrets, and the
 * runtime core must never write it to logcat. Listeners are in-app
 * only (e.g. a diagnostics view) and run on the caller's thread.
 */
data class ShellRunRecord(
    val id: String,
    val command: String,
    val workDir: String,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val exitCode: Int,
    val outputLength: Int,
    val errorLength: Int,
    val timedOut: Boolean
) {
    val durationMs: Long get() = finishedAtMs - startedAtMs
}

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
 * A started OS process with its stream drainers, owned by
 * [DefaultProcessRunner.start]. [destroy] kills the process AND its
 * descendants (children of `sh -c` would otherwise survive a plain
 * destroy() and keep running) — descendants of this process only,
 * never a name/pattern match, so unrelated processes are untouched.
 * [await] waits up to the runner timeout and returns the final result.
 */
class StartedProcess internal constructor(
    private val process: Process,
    private val outDrain: DefaultProcessRunner.StreamDrainer,
    private val errDrain: DefaultProcessRunner.StreamDrainer,
    private val timeoutSeconds: Long
) {
    fun destroy() {
        try {
            val descendants = process.descendants()
            try {
                descendants.forEach { handle ->
                    try { handle.destroy() } catch (_: Exception) { }
                }
            } finally {
                descendants.close()
            }
        } catch (_: Exception) { }
        try { process.destroy() } catch (_: Exception) { }
        try {
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                try {
                    val descendants = process.descendants()
                    try {
                        descendants.forEach { handle ->
                            try { handle.destroyForcibly() } catch (_: Exception) { }
                        }
                    } finally {
                        descendants.close()
                    }
                } catch (_: Exception) { }
                process.destroyForcibly()
            }
        } catch (_: Exception) { }
    }

    fun await(): ProcessResult {
        return try {
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                destroy()
                ProcessResult("", "VoidShell: command timed out after ${timeoutSeconds}s", -1, timedOut = true)
            } else {
                // Join the drainers BEFORE reading: the process has
                // exited, so its streams are at EOF, but the reader
                // threads may still be flushing buffered data. Reading
                // first (the pre-step-5 code's shape, via await) races
                // and can return empty output. The join is capped so
                // a grandchild holding a pipe open cannot hang await.
                outDrain.joinThread(2000)
                errDrain.joinThread(2000)
                ProcessResult(outDrain.text(), errDrain.text(), process.exitValue())
            }
        } catch (e: Exception) {
            ProcessResult("", "VoidShell: ${e.message}", -1)
        }
    }

    internal fun startDrainers() {
        outDrain.startThread("voidshell-stdout")
        errDrain.startThread("voidshell-stderr")
    }
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

    /**
     * Starts a process without waiting for it. The caller owns the
     * returned [StartedProcess]: [StartedProcess.destroy] actually
     * stops it, [StartedProcess.await] waits for its result. This is
     * what makes task cancellation real (impl-01 step 3) — the old
     * run-only API gave the registry no process to stop.
     */
    fun start(
        shellPath: String,
        command: String,
        workDir: String,
        onStdoutLine: ((String) -> Unit)? = null,
        onStderrLine: ((String) -> Unit)? = null
    ): StartedProcess {
        val process = ProcessBuilder(shellPath, "-c", command)
            .directory(File(workDir).takeIf { it.exists() })
            .redirectErrorStream(false)
            .start()
        val started = StartedProcess(
            process,
            StreamDrainer(process.inputStream, onStdoutLine),
            StreamDrainer(process.errorStream, onStderrLine),
            timeoutSeconds
        )
        started.startDrainers()
        return started
    }

    private fun runInternal(
        shellPath: String,
        command: String,
        workDir: String,
        onStdoutLine: ((String) -> Unit)?,
        onStderrLine: ((String) -> Unit)?
    ): ProcessResult {
        return try {
            start(shellPath, command, workDir, onStdoutLine, onStderrLine).await()
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
    internal class StreamDrainer(
        private val stream: InputStream,
        private val onLine: ((String) -> Unit)?
    ) : Runnable {
        private val kept = StringBuilder()
        private val pending = StringBuilder()
        @Volatile private var thread: Thread? = null

        fun text(): String = synchronized(kept) { kept.toString() }

        fun startThread(name: String) {
            thread = Thread(this, name).apply { isDaemon = true; start() }
        }

        fun joinThread(timeoutMs: Long) {
            try { thread?.join(timeoutMs) } catch (_: Exception) { }
        }

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
    private val maxOutputChars: Int = 16384,
    /** In-app only; receives a [ShellRunRecord] per run, on the caller's thread. Never log output here. */
    private val onRunRecord: ((ShellRunRecord) -> Unit)? = null
) {
    companion object {
        /** Config clamps for shell_timeout_seconds / shell_max_output_chars (step 2). */
        fun clampTimeoutSeconds(value: Long): Long = value.coerceIn(5L, 300L)
        fun clampMaxOutputChars(value: Int): Int = value.coerceIn(1024, 262144)
    }

    /**
     * impl-01 step 5: a non-blank workdir must exist and be a
     * directory, otherwise the run fails loudly. Previously the
     * runner silently ignored a nonexistent workdir and ran in the
     * process's inherited cwd — surprising and untestable. An empty
     * workdir still means "inherited cwd", as before.
     */
    private fun workDirError(workDir: String): String? {
        if (workDir.isBlank()) return null
        val dir = File(workDir)
        return if (!dir.isDirectory) "workdir is not a directory: $workDir" else null
    }

    fun run(command: String, workDir: String = defaultWorkDir): ShellResult {
        if (command.isBlank()) return ShellResult.Failed("empty command")
        workDirError(workDir)?.let { return ShellResult.Failed(it) }
        val started = System.currentTimeMillis()
        val result = runner.run(shellPath, command, workDir)
        emitRecord(command, workDir, started, result)
        return toShellResult(result)
    }

    /**
     * A started-but-not-awaited shell run (impl-01 step 3). The caller
     * owns it: [cancel] actually stops the OS process (via
     * [StartedProcess.destroy], descendants included), [await] waits
     * for the result and emits the run record. Returns null for a
     * blank command or for a non-default runner, which has no process
     * to hand out — callers fall back to [run] in that case.
     */
    inner class RunningShell internal constructor(
        private val started: StartedProcess,
        private val commandText: String,
        private val workDirText: String,
        private val startedAtMs: Long
    ) {
        fun cancel() = started.destroy()

        fun await(): ShellResult {
            val result = started.await()
            emitRecord(commandText, workDirText, startedAtMs, result)
            return toShellResult(result)
        }

        fun awaitSuccess(): Boolean = await() is ShellResult.Success
    }

    fun start(command: String, workDir: String = defaultWorkDir): RunningShell? {
        if (command.isBlank()) return null
        if (workDirError(workDir) != null) return null
        val defaultRunner = runner as? DefaultProcessRunner ?: return null
        return try {
            val started = defaultRunner.start(shellPath, command, workDir)
            RunningShell(started, command, workDir, System.currentTimeMillis())
        } catch (_: Exception) {
            null
        }
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
        workDirError(workDir)?.let { return ShellResult.Failed(it) }
        val started = System.currentTimeMillis()
        val defaultRunner = runner as? DefaultProcessRunner
        val result = if (defaultRunner != null) {
            defaultRunner.runStreaming(shellPath, command, workDir, onStdoutLine, onStderrLine)
        } else {
            val r = runner.run(shellPath, command, workDir)
            if (onStdoutLine != null) r.output.split("\n").filter { it.isNotEmpty() }.forEach { onStdoutLine(it) }
            if (onStderrLine != null) r.error.split("\n").filter { it.isNotEmpty() }.forEach { onStderrLine(it) }
            r
        }
        emitRecord(command, workDir, started, result)
        return toShellResult(result)
    }

    private fun emitRecord(command: String, workDir: String, started: Long, result: ProcessResult) {
        val listener = onRunRecord ?: return
        try {
            listener(
                ShellRunRecord(
                    id = java.util.UUID.randomUUID().toString().take(8),
                    command = command,
                    workDir = workDir,
                    startedAtMs = started,
                    finishedAtMs = System.currentTimeMillis(),
                    exitCode = result.exitCode,
                    outputLength = result.output.length,
                    errorLength = result.error.length,
                    timedOut = result.timedOut
                )
            )
        } catch (_: Exception) {
            // A diagnostics listener must never break a shell run.
        }
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
