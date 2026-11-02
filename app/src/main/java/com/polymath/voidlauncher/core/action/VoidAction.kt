package com.polymath.voidlauncher.core.action

/**
 * VoidAction — typed dock actions (refined plan, Phase 1/2).
 *
 * Migration Cycle 3: dock actions are Void-owned. The canonical
 * strings are "open_voidterm" and "run_void_command". Configs saved
 * before Cycle 1 that still use the old external-terminal strings no
 * longer map here (they fall through to the router's legacy fallback);
 * every config saved by the app since Cycle 1 already uses the new strings.
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
                "open_voidterm" -> OPEN_VOIDTERM
                "open_files" -> OPEN_FILES
                "open_drawer" -> OPEN_DRAWER
                "open_settings" -> OPEN_SETTINGS
                "launch_package" -> LAUNCH_PACKAGE
                "open_browser" -> OPEN_BROWSER
                "open_dialer" -> OPEN_DIALER
                "run_void_command" -> RUN_VOID_COMMAND
                // Legacy fallback, preserved exactly: an unknown action
                // with a package still launches that package; without
                // a package it is a no-op (UNKNOWN).
                else -> if (packageName.isNotBlank()) LAUNCH_PACKAGE else UNKNOWN
            }
        }
    }
}
