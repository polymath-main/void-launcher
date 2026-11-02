package com.polymath.voidlauncher.core.shell

/**
 * VoidShellPort — the Void-owned [ShellPort] (migration Cycle 1).
 *
 * Pure Kotlin: no Android imports, so it is unit-testable on the JVM.
 * dispatchCommand runs through [VoidShell] (/system/bin/sh on device)
 * and reports real success/failure, instead of fire-and-forget IPC to
 * another app. launchInteractive cannot launch an external terminal
 * any more: it invokes [onLaunchInteractive], which the Activity wires
 * to the in-app VoidTerm panel. openProject likewise opens VoidTerm;
 * the panel session workdir is the caller's concern, not a path into
 * another app's private directory.
 */
class VoidShellPort(
    private val voidShell: VoidShell,
    /** Files home (filesDir/void) used to resolve project names; "" = no resolution. */
    private val filesHome: String = "",
    private val onLaunchInteractive: (workDir: String) -> Unit = {}
) : ShellPort {

    override fun dispatchCommand(
        command: String,
        workDir: String,
        inBackground: Boolean
    ): Boolean {
        // inBackground is a legacy IPC flag: VoidShell runs are
        // already one-shot and caller-threaded (Activity uses IO
        // dispatchers for :run/:sh); the flag is accepted for
        // interface compatibility and does not change execution.
        return when (voidShell.run(command, workDir)) {
            is ShellResult.Success -> true
            is ShellResult.Failed -> false
        }
    }

    override fun dispatchTracked(
        command: String,
        workDir: String,
        inBackground: Boolean
    ): RunningCommand? {
        val running = voidShell.start(command, workDir) ?: return null
        return object : RunningCommand {
            override fun cancel() = running.cancel()
            override fun awaitSuccess(): Boolean = running.awaitSuccess()
        }
    }

    override fun launchInteractive(workDir: String) {
        onLaunchInteractive(workDir)
    }

    override fun openProject(projectName: String) {
        // impl-01 step 5: resolve the name to a directory under the
        // files home before invoking the hook, and refuse names that
        // escape it. Previously the bare name was passed as the
        // workDir argument, so the hook received a name, not a dir.
        // When no files home is configured, the hook keeps the old
        // behaviour (bare name) so unconfigured callers do not break.
        if (filesHome.isBlank()) {
            onLaunchInteractive(projectName)
            return
        }
        val resolved = VoidRoots.resolveProject(
            java.io.File(filesHome).parentFile ?: java.io.File(filesHome),
            projectName
        )
        // resolveProject expects filesDir (parent of files home); the
        // files home itself is filesDir/void, so pass its parent.
        if (resolved != null) {
            onLaunchInteractive(resolved.absolutePath)
        }
        // Invalid/escaping names are silently ignored: openProject
        // has no result channel, and inventing a directory for a bad
        // name would be worse than opening nothing.
    }
}
