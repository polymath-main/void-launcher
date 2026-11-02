package com.polymath.voidlauncher.core.shell

/**
 * ShellPort — the seam between the launcher and any command executor.
 *
 * TermuxIpcDispatcher is the first (legacy) implementation. VoidShell is
 * the Void-owned implementation for headless commands; it does not
 * implement launchInteractive/openProject, which are Android-app
 * concerns and stay with the Termux implementation for now.
 *
 * Consumers (BareIntegerRouter, ThermalGuardian, AgentOrchestrator,
 * VoidTermPanel) depend on this interface, never on Termux directly,
 * so a future implementation can replace Termux without touching them.
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
