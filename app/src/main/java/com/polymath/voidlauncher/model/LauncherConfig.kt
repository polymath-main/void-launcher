package com.polymath.voidlauncher.model

import android.graphics.Color

/**
 * Complete visual & behavioral configuration for VoidLauncher.
 *
 * Users can edit ~/.voidlauncher/config.json from Termux and
 * the launcher hot-reloads on resume.
 */
data class LauncherConfig(
    // ── Color Palette ────────────────────────────────────────
    val colors: ColorPalette = ColorPalette(),

    // ── Typography ───────────────────────────────────────────
    val fontFamily: String = "monospace",       // monospace | sans-serif | serif | cursive
    val hudFontSizeSp: Float = 11f,
    val dockFontSizeSp: Float = 12.5f,
    val omniFontSizeSp: Float = 12.5f,

    // ── Dock Slots (1..N, max 7) ─────────────────────────────
    val dockSlots: List<DockSlot> = defaultDockSlots(),

    // ── HUD Visibility Toggles ───────────────────────────────
    val showTelemetryBar: Boolean = true,
    val showNodeOsCard: Boolean = true,
    val showIntentCard: Boolean = true,
    val showDigitKeypad: Boolean = true,

    // ── Thermal Envelope ─────────────────────────────────────
    val thermalLimitCelsius: Float = 39.0f,

    // ── Identity ─────────────────────────────────────────────
    val defaultIdentity: String = "VOID",       // "VOID" or "MAIN"
    val identityVoidLabel: String = "[VOID]",
    val identityMainLabel: String = "[MAIN]",

    // ── Omni-Bar ─────────────────────────────────────────────
    val omniBarHint: String = ">_ search apps or enter :cmd",
    val omniPromptSymbol: String = ">_ ",
    val omniFuzzyMaxResults: Int = 5,

    // ── Workspace ────────────────────────────────────────────
    val defaultWorkspacePath: String = "/data/data/com.termux/files/home/Projects",
    val workflowJsonPath: String = "/data/data/com.termux/files/home/Projects/workflow.json",
    val nodeosDbPath: String = "/data/data/com.termux/files/home/Projects/agy_nodeos.db",

    // ── Glyph ────────────────────────────────────────────────
    val enableGlyphPulse: Boolean = true,

    // ── Haptics ──────────────────────────────────────────────
    val enableHaptics: Boolean = true,

    // ── Clock ────────────────────────────────────────────────
    val clockFormat: String = "HH:mm:ss",       // Java SimpleDateFormat pattern
    val clockSuffix: String = " UTC+6",

    // ── Advanced ─────────────────────────────────────────────
    val telemetryPollMs: Long = 1500L
)

data class ColorPalette(
    val background: String = "#000000",
    val surface: String = "#080808",
    val surfacePressed: String = "#171717",
    val border: String = "#1A1A1A",
    val borderActive: String = "#333333",

    val textPrimary: String = "#F4F4F5",
    val textSecondary: String = "#A1A1AA",
    val textMuted: String = "#52525B",

    val accent: String = "#E11D48",
    val accentSurface: String = "#4C0519",
    val secondary: String = "#06B6D4",
    val secondarySurface: String = "#083344",
    val safe: String = "#10B981",
    val warning: String = "#F59E0B",
    val critical: String = "#E11D48"
) {
    fun parseColor(hex: String, fallback: Int = Color.WHITE): Int {
        return try { Color.parseColor(hex) } catch (_: Exception) { fallback }
    }

    val backgroundInt get() = parseColor(background, Color.BLACK)
    val surfaceInt get() = parseColor(surface)
    val surfacePressedInt get() = parseColor(surfacePressed)
    val borderInt get() = parseColor(border)
    val borderActiveInt get() = parseColor(borderActive)
    val textPrimaryInt get() = parseColor(textPrimary)
    val textSecondaryInt get() = parseColor(textSecondary)
    val textMutedInt get() = parseColor(textMuted)
    val accentInt get() = parseColor(accent)
    val accentSurfaceInt get() = parseColor(accentSurface)
    val secondaryInt get() = parseColor(secondary)
    val secondarySurfaceInt get() = parseColor(secondarySurface)
    val safeInt get() = parseColor(safe)
    val warningInt get() = parseColor(warning)
    val criticalInt get() = parseColor(critical)
}

data class DockSlot(
    val index: Int,
    val label: String,
    val subtitle: String,
    val packageName: String,             // target package or special action key
    val action: String = "launch_package" // launch_package | launch_termux | open_browser | open_dialer | open_settings | termux_command
)

fun defaultDockSlots(): List<DockSlot> = listOf(
    DockSlot(1, "TERMINAL",   "Termux · VoidTerm",  "com.termux",                        "launch_termux"),
    DockSlot(2, "FILESYSTEM", "Polymath FS",         "com.aistudio.filesystem.pmfsxz",    "launch_package"),
    DockSlot(3, "BROWSER",    "WebAuth & Links",     "",                                   "open_browser"),
    DockSlot(4, "COMMS",      "Dialer & Comms",      "",                                   "open_dialer"),
    DockSlot(5, "SETTINGS",   "System Controls",     "",                                   "open_settings")
)
