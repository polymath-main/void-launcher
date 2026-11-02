package com.polymath.voidlauncher.core.shell

/**
 * ShellPort — the seam between the launcher and any command executor.
 *
 * VoidShellPort is the Void-owned implementation: headless commands run
 * through VoidShell, and launchInteractive/openProject open the in-app
 * VoidTerm panel. Consumers (BareIntegerRouter, AgentOrchestrator,
 * VoidTermPanel) depend on this interface, never on an external
 * terminal app, so the executor can change without touching them.
 */
interface ShellPort {
    fun dispatchCommand(
        command: String,
        workDir: String = "",
        inBackground: Boolean = true
    ): Boolean

    fun launchInteractive(workDir: String = "")

    fun openProject(projectName: String)
}
