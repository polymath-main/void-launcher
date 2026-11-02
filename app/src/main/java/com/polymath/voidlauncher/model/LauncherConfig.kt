package com.polymath.voidlauncher.model

import android.content.Context
import android.graphics.Color
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * App-owned storage paths.
 *
 * Everything lives under the app's own config folder so the launcher can always
 * read/write it without runtime permissions:
 *
 *   /sdcard/Android/data/<pkg>/files/config/config.json
 *
 * The legacy shared/Termux locations are kept read-only for one-time migration.
 */
object VoidPaths {
    private const val TAG = "VoidPaths"

    @Volatile
    private var baseDir: String? = null

    /** Used only if [init] has not run yet. */
    private val fallbackHome: String
        get() = File(Environment.getExternalStorageDirectory(), ".voidlauncher").absolutePath

    fun init(context: Context) {
        val dir = context.getExternalFilesDir("config") ?: File(context.filesDir, "config")
        try { dir.mkdirs() } catch (_: Exception) {}
        baseDir = dir.absolutePath
        Log.i(TAG, "VOID config home = ${dir.absolutePath}")
    }

    /** Primary app-owned config folder. */
    val HOME: String get() = baseDir ?: fallbackHome

    val CONFIG: String get() = "$HOME/config.json"
    val MACROS: String get() = "$HOME/macros"
    val WIDGETS: String get() = "$HOME/widgets"
    val PERSONAS: String get() = "$HOME/personas"
    val GLYPH_PATTERNS: String get() = "$HOME/glyph_patterns.json"
    val FOCUS_SCHEDULE: String get() = "$HOME/focus_schedule.json"

    /** Legacy config locations for read-only migration. */
    val LEGACY_CONFIG_FILES: List<String> = listOf(
        File(Environment.getExternalStorageDirectory(), ".voidlauncher/config.json").absolutePath,
        "/data/data/com.termux/files/home/.voidlauncher/config.json"
    )
}

/**
 * Complete visual & behavioral configuration for VoidLauncher (v2.0).
 *
 * Users can edit ~/.voidlauncher/config.json from Termux or
 * configure directly via the built-in Void Settings UI.
 */
data class LauncherConfig(
    // ── Color Palette ────────────────────────────────────────
    val colors: ColorPalette = ColorPalette(),

    // ── Theme Engine (Appearance Studio) ─────────────────────
    val themeId: String = "void",               // preset id | "custom"

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

    // Compound Gesture Extensions (F-07)
    val compoundGesturesEnabled: Boolean = true,
    val gestureTwoFingerUp: String = "OPEN_HUD",
    val gesturePinchIn: String = "TOGGLE_MINIMAL",
    val gesturePinchOut: String = "COCKPIT_MODE",
    val gestureLeftEdgeSwipe: String = "OPEN_FILES",
    val gestureRightEdgeSwipe: String = "OPEN_DRAWER",
    val gestureBottomEdgeSwipe: String = "FOCUS_OMNI",

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

    // ── Thermal Envelope & Guardian (F-06) ───────────────────
    val thermalLimitCelsius: Float = 39.0f,
    val thermalGuardianEnabled: Boolean = true,
    /** DEPRECATED (Termux-removal Cycle 2): never executed as shell; kept only so old configs still parse. */
    val thermalKillCommands: List<String> = emptyList(),
    val thermalHysteresisCelsius: Float = 2.0f,

    // ── Identity & Smart Personas (F-02) ─────────────────────
    val defaultIdentity: String = "VOID",       // "VOID" or "MAIN"
    val identityVoidLabel: String = "[VOID]",
    val identityMainLabel: String = "[MAIN]",
    val activePersona: String = "void",
    val personas: List<PersonaProfile> = emptyList(),

    // ── Omni-Bar & History (F-09) ────────────────────────────
    val omniBarHint: String = ">_ search apps or enter :cmd",
    val omniPromptSymbol: String = ">_ ",
    val omniFuzzyMaxResults: Int = 5,
    val commandHistoryEnabled: Boolean = true,
    val commandHistoryLimit: Int = 500,

    // ── Workspace ────────────────────────────────────────────
    val defaultWorkspacePath: String = "/data/data/com.termux/files/home/Projects",
    val workflowJsonPath: String = "/data/data/com.termux/files/home/Projects/workflow.json",
    val nodeosDbPath: String = "/data/data/com.termux/files/home/Projects/agy_nodeos.db",
    /** Termux-removal Cycle 2: NodeOS legacy import is opt-in; false = Void daemon status is shown instead. */
    val nodeOsLegacyImportEnabled: Boolean = false,

    // ── Glyph & Choreographer (F-03) ─────────────────────────
    val enableGlyphPulse: Boolean = true,
    val glyphPatternsPath: String = VoidPaths.GLYPH_PATTERNS,

    // ── Haptics ──────────────────────────────────────────────
    val enableHaptics: Boolean = true,

    // ── Liquid Touch Surface (OLED underlay) ─────────────────
    val liquidTouchEnabled: Boolean = true,

    // ── Clock ────────────────────────────────────────────────
    val clockFormat: String = "HH:mm:ss",       // Java SimpleDateFormat pattern
    val clockSuffix: String = " UTC+6",

    // ── Telemetry & Power Profile (F-13) ─────────────────────
    val telemetryPollMs: Long = 1500L,
    val powerProfileAdaptive: Boolean = true,

    // ── AST Graph View (F-05) ────────────────────────────────
    val astGraphEnabled: Boolean = false,
    val astGraphNodeCap: Int = 80,

    // ── Agent Orchestrator (F-08) ────────────────────────────
    val agentOrchestratorEnabled: Boolean = true,

    // ── Widgets (F-04) ───────────────────────────────────────
    val widgetsEnabled: Boolean = false,
    val widgetsDirectory: String = VoidPaths.WIDGETS,

    // ── Focus Scheduler (F-10) ───────────────────────────────
    val focusSchedulerEnabled: Boolean = true,
    val focusSchedulePath: String = VoidPaths.FOCUS_SCHEDULE,

    // ── Notification Intelligence (F-11) ─────────────────────
    val notifWhitelistPackages: List<String> = listOf("com.termux", "com.google.android.dialer"),
    val notifBlacklistPackages: List<String> = emptyList(),
    val notifPriorityKeywords: List<String> = listOf("error", "failed", "agent", "completed", "critical", "warning"),

    // ── VoidTerm Embedded Panel (F-14) ───────────────────────
    val voidTermEnabled: Boolean = false,
    val voidTermRows: Int = 8,
    val voidTermCols: Int = 48,

    // ── Macro Recorder (F-15) ────────────────────────────────
    val macrosEnabled: Boolean = true,
    val macrosDirectory: String = VoidPaths.MACROS
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
    val action: String = "launch_package" // launch_package | open_voidterm | open_browser | open_dialer | open_settings | open_files | open_drawer | run_void_command
)

fun defaultDockSlots(): List<DockSlot> = listOf(
    DockSlot(1, "TERMINAL",   "VoidTerm",           "",                                   "open_voidterm"),
    DockSlot(2, "FILES",      "Browse Projects",    "",                                   "open_files"),
    DockSlot(3, "DRAWER",     "All Installed Apps", "",                                   "open_drawer"),
    DockSlot(4, "BROWSER",    "WebAuth & Links",     "",                                   "open_browser"),
    DockSlot(5, "SETTINGS",   "Void Customizer",     "",                                   "open_settings")
)
