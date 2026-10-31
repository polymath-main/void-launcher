package com.polymath.voidlauncher.model

import android.graphics.Color

/**
 * Complete visual & behavioral configuration for VoidLauncher.
 *
 * Users can edit ~/.voidlauncher/config.json from Termux or
 * configure directly via the built-in Void Settings UI.
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

    // ── Default Home Screen Customizations ───────────────────
    val defaultHomeMode: String = "COCKPIT",     // COCKPIT | MINIMAL | FILES
    val showTelemetryBar: Boolean = true,
    val showNodeOsCard: Boolean = true,
    val showIntentCard: Boolean = true,
    val showNotificationsCard: Boolean = true,
    val notificationsMaxCount: Int = 5,
    val filterOngoingNotifications: Boolean = true,
    val showDigitKeypad: Boolean = true,
    val showDock: Boolean = true,
    val useSystemWallpaper: Boolean = false,     // Render over system Android wallpaper
    val cockpitCardRadiusDp: Float = 14f,        // Corner radius of HUD cards
    val showStatusBarTime: Boolean = true,
    val showStatusBarBattery: Boolean = true,
    val showStatusBarRam: Boolean = true,

    // ── Gesture Controls ─────────────────────────────────────
    val gestureSwipeUp: String = "OPEN_DRAWER",      // OPEN_DRAWER | OPEN_SEARCH | OPEN_FILES | NONE
    val gestureSwipeDown: String = "NOTIFICATIONS",  // NOTIFICATIONS | EXPAND_STATUS_BAR | OPEN_SEARCH | OPEN_SETTINGS | NONE
    val gestureSwipeLeft: String = "OPEN_DRAWER",    // OPEN_DRAWER | OPEN_FILES | NONE
    val gestureSwipeRight: String = "OPEN_FILES",    // OPEN_FILES | OPEN_DRAWER | TOGGLE_IDENTITY | NONE
    val gestureDoubleTap: String = "TOGGLE_MINIMAL", // TOGGLE_MINIMAL | OPEN_SETTINGS | OPEN_TERMINAL | NONE
    val gestureLongPress: String = "OPEN_SETTINGS",  // OPEN_SETTINGS | OPEN_DRAWER | TOGGLE_FILES | NONE

    // ── App Icons & Drawer Customizations ────────────────────
    val showAppIcons: Boolean = true,
    val appIconStyle: String = "original",      // original | monochrome | tinted
    val drawerAlpha: Float = 0.90f,             // 0.0f (transparent) to 1.0f (opaque)
    val drawerEdgeTransparent: Boolean = true,  // Gradient fade on drawer leading edge
    val drawerWidthPercent: Int = 85,           // Width % of right edge drawer (50..100)
    val enableAtoZScrubber: Boolean = true,     // Fast alphabetical vertical scrub bar

    // ── Built-in Files Browser Customizations ────────────────
    val filesDefaultPath: String = "/storage/emulated/0",
    val filesShowHidden: Boolean = true,

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
    val action: String = "launch_package" // launch_package | launch_termux | open_browser | open_dialer | open_settings | open_files | open_drawer | termux_command
)

fun defaultDockSlots(): List<DockSlot> = listOf(
    DockSlot(1, "TERMINAL",   "Termux · VoidTerm",  "com.termux",                        "launch_termux"),
    DockSlot(2, "FILES",      "Browse Projects",    "",                                   "open_files"),
    DockSlot(3, "DRAWER",     "All Installed Apps", "",                                   "open_drawer"),
    DockSlot(4, "BROWSER",    "WebAuth & Links",     "",                                   "open_browser"),
    DockSlot(5, "SETTINGS",   "Void Customizer",     "",                                   "open_settings")
)
