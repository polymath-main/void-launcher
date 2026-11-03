package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.core.shell.ShellResult

/**
 * Pure task-outcome mapping (impl-06 step 2).
 *
 * Before this step, [AgentOrchestrator] recorded only a boolean and
 * synthesised exit codes 0/1, with `outputPreview` always "". A tracked
 * run now yields a real [ShellResult] via `RunningCommand.awaitResult()`:
 *  - Success -> success=true, preview = stdout+stderr (truncated), real exit code.
 *  - Failed  -> VoidShell encodes non-zero exits as
 *    "exit <code>[: <error>] [| output: <output>]". The code is parsed
 *    back out here; if parsing fails, exit is 1 and the whole reason is
 *    the preview. This parsing is a documented consequence of VoidShell's
 *    Failed encoding, not a new contract — a future shell-area change
 *    that returns exit codes directly should replace this function.
 *  - Null result (executor handed out no result) -> null, and the caller
 *    falls back to awaitSuccess() with synthesised 0/1, as before.
 */
data class TaskOutcome(
    val success: Boolean,
    val outputPreview: String,
    val exitCode: Int
)

const val TASK_OUTPUT_PREVIEW_CHARS = 2000

fun taskOutcomeFromShellResult(result: ShellResult?): TaskOutcome? {
    return when (result) {
        null -> null
        is ShellResult.Success -> TaskOutcome(
            success = true,
            outputPreview = listOf(result.output, result.error)
                .filter { it.isNotBlank() }.joinToString("\n").take(TASK_OUTPUT_PREVIEW_CHARS),
            exitCode = result.exitCode
        )
        is ShellResult.Failed -> {
            val reason = result.reason
            val code = Regex("^exit (\\d+)").find(reason)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val outputPart = reason.substringAfter(" | output: ", "")
            val preview = if (outputPart.isNotEmpty()) outputPart else reason
            TaskOutcome(success = false, outputPreview = preview.take(TASK_OUTPUT_PREVIEW_CHARS), exitCode = code)
        }
    }
}
