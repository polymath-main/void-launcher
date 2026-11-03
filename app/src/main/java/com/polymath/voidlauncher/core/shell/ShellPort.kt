package com.polymath.voidlauncher.core.shell

/**
 * ShellPort — the seam between the launcher and any command executor.
 *
 * VoidShellPort is the Void-owned implementation: headless commands run
 * through VoidShell, and launchInteractive/openProject open the in-app
 * VoidTerm panel. Consumers (BareIntegerRouter, AgentOrchestrator,
 * VoidTermPanel) depend on this interface, never on an external
 * terminal app, so the executor can change without touching them.
 *
 * impl-01 step 3: [dispatchCommand] keeps its Boolean return (router,
 * panel and existing tests depend on it); callers that need to stop
 * a run use [dispatchTracked], which hands back a [RunningCommand]
 * whose [RunningCommand.cancel] stops the real OS process. A tracked
 * dispatch returning null means the executor has no process to hand
 * out — callers fall back to [dispatchCommand].
 */
interface ShellPort {
    fun dispatchCommand(
        command: String,
        workDir: String = "",
        inBackground: Boolean = true
    ): Boolean

    fun dispatchTracked(
        command: String,
        workDir: String = "",
        inBackground: Boolean = true
    ): RunningCommand? = null

    fun launchInteractive(workDir: String = "")

    fun openProject(projectName: String)
}

/**
 * A started command: [awaitSuccess] blocks until it finishes and
 * reports success, [cancel] stops the underlying process (and its
 * descendants) — never by name or pattern.
 *
 * impl-06 step 2: [awaitResult] is the observable variant — it returns
 * the real [ShellResult] (output, error, exit code) instead of a bare
 * boolean, so agent tasks can record real output/exit codes. Default
 * null = executor has no result to hand out; callers fall back to
 * [awaitSuccess] and keep the old synthesised 0/1 exit codes, and say
 * so where they do.
 */
interface RunningCommand {
    fun cancel()
    fun awaitSuccess(): Boolean
    fun awaitResult(): ShellResult? = null
}
