package com.polymath.voidlauncher.core.action

/**
 * VoidAction — typed dock actions (refined plan, Phase 1/2).
 *
 * Termux-removal Cycle 1: the legacy dock strings "launch_termux" and
 * "termux_command" now map to OPEN_VOIDTERM and RUN_VOID_COMMAND, so
 * old saved configs keep working but route to the in-app VoidTerm /
 * VoidShell instead of launching another app. The new canonical
 * strings are "open_voidterm" and "run_void_command".
 *
 * Pure Kotlin: no Android imports, so this mapping is unit-testable
 * on the JVM. The router switches on this enum only — it never
 * references any external terminal dispatcher.
 */
enum class VoidAction {
    OPEN_VOIDTERM,
    OPEN_FILES,
    OPEN_DRAWER,
    OPEN_SETTINGS,
    LAUNCH_PACKAGE,
    OPEN_BROWSER,
    OPEN_DIALER,
    RUN_VOID_COMMAND,
    UNKNOWN;

    companion object {
        fun fromAction(action: String, packageName: String): VoidAction {
            return when (action) {
                "launch_termux", "open_voidterm" -> OPEN_VOIDTERM
                "open_files" -> OPEN_FILES
                "open_drawer" -> OPEN_DRAWER
                "open_settings" -> OPEN_SETTINGS
                "launch_package" -> LAUNCH_PACKAGE
                "open_browser" -> OPEN_BROWSER
                "open_dialer" -> OPEN_DIALER
                "termux_command", "run_void_command" -> RUN_VOID_COMMAND
                // Legacy fallback, preserved exactly: an unknown action
                // with a package still launches that package; without
                // a package it is a no-op (UNKNOWN).
                else -> if (packageName.isNotBlank()) LAUNCH_PACKAGE else UNKNOWN
            }
        }
    }
}
