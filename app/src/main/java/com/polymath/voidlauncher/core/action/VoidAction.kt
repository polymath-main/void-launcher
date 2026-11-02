package com.polymath.voidlauncher.core.action

/**
 * VoidAction — typed dock actions (refined plan, Phase 1/2).
 *
 * DockSlot.action is a raw string today, and slot.packageName is abused
 * as a command string for "termux_command". This enum is the first step
 * away from that: the router maps the string once, then switches on
 * the enum. Behaviour in this cycle is intentionally identical to the
 * old string switch, including its fallback.
 *
 * Pure Kotlin: no Android imports, so this mapping is unit-testable
 * on the JVM. Future cycles rename the legacy actions
 * (launch_termux -> open_voidterm, termux_command -> run_void_command)
 * here, in one place, instead of in the router and the activity.
 */
enum class VoidAction {
    LAUNCH_TERMUX,
    OPEN_FILES,
    OPEN_DRAWER,
    OPEN_SETTINGS,
    LAUNCH_PACKAGE,
    OPEN_BROWSER,
    OPEN_DIALER,
    TERMUX_COMMAND,
    UNKNOWN;

    companion object {
        fun fromAction(action: String, packageName: String): VoidAction {
            return when (action) {
                "launch_termux" -> LAUNCH_TERMUX
                "open_files" -> OPEN_FILES
                "open_drawer" -> OPEN_DRAWER
                "open_settings" -> OPEN_SETTINGS
                "launch_package" -> LAUNCH_PACKAGE
                "open_browser" -> OPEN_BROWSER
                "open_dialer" -> OPEN_DIALER
                "termux_command" -> TERMUX_COMMAND
                // Legacy fallback, preserved exactly: an unknown action
                // with a package still launches that package; without
                // a package it is a no-op (UNKNOWN).
                else -> if (packageName.isNotBlank()) LAUNCH_PACKAGE else UNKNOWN
            }
        }
    }
}
