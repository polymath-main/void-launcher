package com.polymath.voidlauncher.core

import android.content.Context
import android.util.Log
import com.polymath.voidlauncher.model.ColorPalette
import com.polymath.voidlauncher.model.DockSlot
import com.polymath.voidlauncher.model.LauncherConfig
import com.polymath.voidlauncher.model.PersonaProfile
import com.polymath.voidlauncher.model.VoidPaths
import com.polymath.voidlauncher.model.defaultDockSlots
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages VoidLauncher visual & behavioral configuration (v2.0).
 *
 * Config resolution order (first found wins):
 *   1. External user override: ~/.voidlauncher/config.json
 *   2. App-internal data:      /data/data/<pkg>/files/config.json
 *   3. Bundled default:        assets/default_config.json
 *   4. Hardcoded Kotlin defaults
 */
class ConfigManager(private val context: Context) {

    companion object {
        private const val TAG = "ConfigManager"

        /** Void-owned config dir. */
        val EXTERNAL_CONFIG_DIR: String get() = VoidPaths.HOME
        val EXTERNAL_CONFIG_FILE: String get() = VoidPaths.CONFIG
        const val INTERNAL_CONFIG_FILE = "config.json"
    }

    private var cachedConfig: LauncherConfig = LauncherConfig()

    /**
     * Loads config from the resolution chain. Call on every onResume for hot-reload.
     */
    fun loadConfig(): LauncherConfig {
        val json = readExternalConfig()
            ?: readInternalConfig()
            ?: readBundledConfig()

        cachedConfig = if (json != null) {
            try {
                parseConfig(json)
            } catch (e: Exception) {
                Log.e(TAG, "Config parse error, falling back to defaults: ${e.message}")
                LauncherConfig()
            }
        } else {
            LauncherConfig()
        }

        return cachedConfig
    }

    fun getConfig(): LauncherConfig = cachedConfig

    /**
     * Saves the current config to the external user-editable path.
     */
    fun saveConfig(config: LauncherConfig) {
        cachedConfig = config
        try {
            val dir = File(EXTERNAL_CONFIG_DIR)
            if (!dir.exists()) dir.mkdirs()

            val json = serializeConfig(config)
            File(EXTERNAL_CONFIG_FILE).writeText(json.toString(2))
            Log.i(TAG, "Config saved to $EXTERNAL_CONFIG_FILE")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save external config: ${e.message}")
            // Fallback: save internally
            try {
                val json = serializeConfig(config)
                File(context.filesDir, INTERNAL_CONFIG_FILE).writeText(json.toString(2))
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to save internal config: ${e2.message}")
            }
        }
    }

    /**
     * Exports the CURRENT config to the Void config store (migration
     * Cycle 3 fix). This deliberately never writes LauncherConfig()
     * defaults: the old export overwrote the user's
     * external config with defaults while the UI toasted "Exported
     * template". Callers pass the config in effect (e.g. Settings'
     * last-saved config); a SAF-chosen destination is handled by
     * ConfigSyncService.exportConfig, which copies this same store file.
     */
    fun exportCurrentConfig(config: LauncherConfig) {
        saveConfig(config)
    }

    // ── File readers ──────────────────────────────────────────

    private fun readExternalConfig(): JSONObject? {
        // Primary: app-owned config folder
        readJsonFile(File(EXTERNAL_CONFIG_FILE))?.let { return it }
        // Migration: legacy location (older shared dir)
        for (legacy in VoidPaths.LEGACY_CONFIG_FILES) {
            readJsonFile(File(legacy))?.let {
                Log.i(TAG, "Migrating legacy config from $legacy")
                return it
            }
        }
        return null
    }

    private fun readJsonFile(file: File): JSONObject? {
        return try {
            if (file.exists() && file.canRead()) JSONObject(file.readText()) else null
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read config ${file.absolutePath}: ${e.message}")
            null
        }
    }

    private fun readInternalConfig(): JSONObject? {
        return try {
            val file = File(context.filesDir, INTERNAL_CONFIG_FILE)
            if (file.exists()) {
                JSONObject(file.readText())
            } else null
        } catch (e: Exception) { null }
    }

    private fun readBundledConfig(): JSONObject? {
        return try {
            val stream = context.assets.open("default_config.json")
            val text = stream.bufferedReader().readText()
            stream.close()
            JSONObject(text)
        } catch (e: Exception) { null }
    }

    // ── JSON Parsing ──────────────────────────────────────────

    private fun parseConfig(json: JSONObject): LauncherConfig {
        val colorsJson = json.optJSONObject("colors")
        val colors = if (colorsJson != null) parseColors(colorsJson) else ColorPalette()

        val dockJson = json.optJSONArray("dock_slots")
        val dockSlots = if (dockJson != null) parseDockSlots(dockJson) else defaultDockSlots()

        val personasJson = json.optJSONArray("personas")
        val personas = if (personasJson != null) parsePersonas(personasJson) else emptyList()

        return LauncherConfig(
            colors = colors,
            themeId = json.optString("theme_id", "void"),
            fontFamily = json.optString("font_family", "monospace"),
            hudFontSizeSp = json.optDouble("hud_font_size_sp", 11.0).toFloat(),
            dockFontSizeSp = json.optDouble("dock_font_size_sp", 12.5).toFloat(),
            omniFontSizeSp = json.optDouble("omni_font_size_sp", 12.5).toFloat(),
            dockSlots = dockSlots,
            defaultHomeMode = json.optString("default_home_mode", "COCKPIT"),
            showTelemetryBar = json.optBoolean("show_telemetry_bar", true),
            showNodeOsCard = json.optBoolean("show_nodeos_card", false),
            showIntentCard = json.optBoolean("show_intent_card", true),
            showNotificationsCard = json.optBoolean("show_notifications_card", true),
            notificationsMaxCount = json.optInt("notifications_max_count", 5),
            filterOngoingNotifications = json.optBoolean("filter_ongoing_notifications", true),
            showDigitKeypad = json.optBoolean("show_digit_keypad", true),
            showDock = json.optBoolean("show_dock", true),
            showAppIcons = json.optBoolean("show_app_icons", true),
            appIconStyle = json.optString("app_icon_style", "original"),
            drawerAlpha = json.optDouble("drawer_alpha", 0.90).toFloat(),
            drawerEdgeTransparent = json.optBoolean("drawer_edge_transparent", true),
            drawerWidthPercent = json.optInt("drawer_width_percent", 85),
            enableAtoZScrubber = json.optBoolean("enable_atoz_scrubber", true),
            filesDefaultPath = json.optString("files_default_path", "/storage/emulated/0"),
            filesShowHidden = json.optBoolean("files_show_hidden", true),
            thermalLimitCelsius = json.optDouble("thermal_limit_celsius", 39.0).toFloat(),
            thermalGuardianEnabled = json.optBoolean("thermal_guardian_enabled", true),
            thermalKillCommands = parseStringList(json.optJSONArray("thermal_kill_commands")),
            thermalHysteresisCelsius = json.optDouble("thermal_hysteresis_celsius", 2.0).toFloat(),
            defaultIdentity = json.optString("default_identity", "VOID"),
            identityVoidLabel = json.optString("identity_void_label", "[VOID]"),
            identityMainLabel = json.optString("identity_main_label", "[MAIN]"),
            activePersona = json.optString("active_persona", "void"),
            personas = personas,
            omniBarHint = json.optString("omni_bar_hint", ">_ search apps or enter :cmd"),
            omniPromptSymbol = json.optString("omni_prompt_symbol", ">_ "),
            omniFuzzyMaxResults = json.optInt("omni_fuzzy_max_results", 5),
            commandHistoryEnabled = json.optBoolean("command_history_enabled", true),
            commandHistoryLimit = json.optInt("command_history_limit", 500),
            defaultWorkspacePath = json.optString("default_workspace_path", VoidPaths.WORKSPACE),
            workflowJsonPath = json.optString("workflow_json_path", VoidPaths.WORKFLOW_JSON),
            nodeosDbPath = json.optString("nodeos_db_path", VoidPaths.NODEOS_DB),
            nodeOsLegacyImportEnabled = json.optBoolean("nodeos_legacy_import_enabled", false),
            useSystemWallpaper = json.optBoolean("use_system_wallpaper", false),
            cockpitCardRadiusDp = json.optDouble("cockpit_card_radius_dp", 14.0).toFloat(),
            showStatusBarTime = json.optBoolean("show_status_bar_time", true),
            showStatusBarBattery = json.optBoolean("show_status_bar_battery", true),
            showStatusBarRam = json.optBoolean("show_status_bar_ram", true),
            gestureSwipeUp = json.optString("gesture_swipe_up", "OPEN_DRAWER"),
            gestureSwipeDown = json.optString("gesture_swipe_down", "NOTIFICATIONS"),
            gestureSwipeLeft = json.optString("gesture_swipe_left", "OPEN_DRAWER"),
            gestureSwipeRight = json.optString("gesture_swipe_right", "OPEN_FILES"),
            gestureDoubleTap = json.optString("gesture_double_tap", "TOGGLE_MINIMAL"),
            gestureLongPress = json.optString("gesture_long_press", "OPEN_SETTINGS"),
            compoundGesturesEnabled = json.optBoolean("compound_gestures_enabled", true),
            gestureTwoFingerUp = json.optString("gesture_two_finger_up", "OPEN_HUD"),
            gesturePinchIn = json.optString("gesture_pinch_in", "TOGGLE_MINIMAL"),
            gesturePinchOut = json.optString("gesture_pinch_out", "COCKPIT_MODE"),
            gestureLeftEdgeSwipe = json.optString("gesture_left_edge_swipe", "OPEN_FILES"),
            gestureRightEdgeSwipe = json.optString("gesture_right_edge_swipe", "OPEN_DRAWER"),
            gestureBottomEdgeSwipe = json.optString("gesture_bottom_edge_swipe", "FOCUS_OMNI"),
            enableGlyphPulse = json.optBoolean("enable_glyph_pulse", true),
            glyphPatternsPath = json.optString("glyph_patterns_path", VoidPaths.GLYPH_PATTERNS),
            enableHaptics = json.optBoolean("enable_haptics", true),
            liquidTouchEnabled = json.optBoolean("liquid_touch_enabled", true),
            clockFormat = json.optString("clock_format", "HH:mm:ss"),
            clockSuffix = json.optString("clock_suffix", " UTC+6"),
            telemetryPollMs = json.optLong("telemetry_poll_ms", 1500L),
            powerProfileAdaptive = json.optBoolean("power_profile_adaptive", true),
            astGraphEnabled = json.optBoolean("ast_graph_enabled", false),
            astGraphNodeCap = json.optInt("ast_graph_node_cap", 80),
            agentOrchestratorEnabled = json.optBoolean("agent_orchestrator_enabled", true),
            widgetsEnabled = json.optBoolean("widgets_enabled", false),
            widgetsDirectory = json.optString("widgets_directory", VoidPaths.WIDGETS),
            focusSchedulerEnabled = json.optBoolean("focus_scheduler_enabled", true),
            focusSchedulePath = json.optString("focus_schedule_path", VoidPaths.FOCUS_SCHEDULE),
            notifWhitelistPackages = parseStringList(json.optJSONArray("notif_whitelist_packages"), listOf("com.google.android.dialer")),
            notifBlacklistPackages = parseStringList(json.optJSONArray("notif_blacklist_packages")),
            notifPriorityKeywords = parseStringList(json.optJSONArray("notif_priority_keywords"), listOf("error", "failed", "agent", "completed", "critical", "warning")),
            voidTermEnabled = json.optBoolean("voidterm_enabled", false),
            voidTermRows = json.optInt("voidterm_rows", 8),
            voidTermCols = json.optInt("voidterm_cols", 48),
            macrosEnabled = json.optBoolean("macros_enabled", true),
            macrosDirectory = json.optString("macros_directory", VoidPaths.MACROS),
            shellTimeoutSeconds = json.optLong("shell_timeout_seconds", 30L),
            shellMaxOutputChars = json.optInt("shell_max_output_chars", 16384)
        )
    }

    private fun parseColors(json: JSONObject): ColorPalette {
        return ColorPalette(
            background = json.optString("background", "#000000"),
            surface = json.optString("surface", "#080808"),
            surfacePressed = json.optString("surface_pressed", "#171717"),
            border = json.optString("border", "#1A1A1A"),
            borderActive = json.optString("border_active", "#333333"),
            textPrimary = json.optString("text_primary", "#F4F4F5"),
            textSecondary = json.optString("text_secondary", "#A1A1AA"),
            textMuted = json.optString("text_muted", "#52525B"),
            accent = json.optString("accent", "#E11D48"),
            accentSurface = json.optString("accent_surface", "#4C0519"),
            secondary = json.optString("secondary", "#06B6D4"),
            secondarySurface = json.optString("secondary_surface", "#083344"),
            safe = json.optString("safe", "#10B981"),
            warning = json.optString("warning", "#F59E0B"),
            critical = json.optString("critical", "#E11D48")
        )
    }

    private fun parseDockSlots(json: JSONArray): List<DockSlot> {
        val slots = mutableListOf<DockSlot>()
        for (i in 0 until minOf(json.length(), 7)) {
            val obj = json.optJSONObject(i) ?: continue
            slots.add(DockSlot(
                index = obj.optInt("index", i + 1),
                label = obj.optString("label", "SLOT ${i + 1}"),
                subtitle = obj.optString("subtitle", ""),
                packageName = obj.optString("package_name", ""),
                action = obj.optString("action", "launch_package")
            ))
        }
        return if (slots.isEmpty()) defaultDockSlots() else slots
    }

    private fun parsePersonas(json: JSONArray): List<PersonaProfile> {
        val list = mutableListOf<PersonaProfile>()
        for (i in 0 until json.length()) {
            val obj = json.optJSONObject(i) ?: continue
            val id = obj.optString("id", "")
            if (id.isEmpty()) continue
            list.add(PersonaProfile(
                id = id,
                label = obj.optString("label", "[$id]"),
                subtitle = obj.optString("subtitle", ""),
                accentColor = obj.optString("accent", "#E11D48"),
                secondaryColor = obj.optString("secondary", "#06B6D4"),
                workspacePath = obj.optString("workspace", VoidPaths.WORKSPACE)
            ))
        }
        return list
    }

    private fun parseStringList(json: JSONArray?, fallback: List<String> = emptyList()): List<String> {
        if (json == null) return fallback
        val list = mutableListOf<String>()
        for (i in 0 until json.length()) {
            val str = json.optString(i, "")
            if (str.isNotEmpty()) list.add(str)
        }
        return if (list.isEmpty()) fallback else list
    }

    // ── JSON Serialization ────────────────────────────────────

    private fun serializeConfig(config: LauncherConfig): JSONObject {
        val json = JSONObject()

        // Colors
        val colorsJson = JSONObject().apply {
            put("background", config.colors.background)
            put("surface", config.colors.surface)
            put("surface_pressed", config.colors.surfacePressed)
            put("border", config.colors.border)
            put("border_active", config.colors.borderActive)
            put("text_primary", config.colors.textPrimary)
            put("text_secondary", config.colors.textSecondary)
            put("text_muted", config.colors.textMuted)
            put("accent", config.colors.accent)
            put("accent_surface", config.colors.accentSurface)
            put("secondary", config.colors.secondary)
            put("secondary_surface", config.colors.secondarySurface)
            put("safe", config.colors.safe)
            put("warning", config.colors.warning)
            put("critical", config.colors.critical)
        }
        json.put("colors", colorsJson)
        json.put("theme_id", config.themeId)

        // Typography
        json.put("font_family", config.fontFamily)
        json.put("hud_font_size_sp", config.hudFontSizeSp.toDouble())
        json.put("dock_font_size_sp", config.dockFontSizeSp.toDouble())
        json.put("omni_font_size_sp", config.omniFontSizeSp.toDouble())

        // Dock Slots
        val dockArr = JSONArray()
        for (slot in config.dockSlots) {
            dockArr.put(JSONObject().apply {
                put("index", slot.index)
                put("label", slot.label)
                put("subtitle", slot.subtitle)
                put("package_name", slot.packageName)
                put("action", slot.action)
            })
        }
        json.put("dock_slots", dockArr)

        // HUD & Home Customizations
        json.put("default_home_mode", config.defaultHomeMode)
        json.put("show_telemetry_bar", config.showTelemetryBar)
        json.put("show_nodeos_card", config.showNodeOsCard)
        json.put("show_intent_card", config.showIntentCard)
        json.put("show_notifications_card", config.showNotificationsCard)
        json.put("notifications_max_count", config.notificationsMaxCount)
        json.put("filter_ongoing_notifications", config.filterOngoingNotifications)
        json.put("show_digit_keypad", config.showDigitKeypad)
        json.put("show_dock", config.showDock)
        json.put("use_system_wallpaper", config.useSystemWallpaper)
        json.put("cockpit_card_radius_dp", config.cockpitCardRadiusDp.toDouble())
        json.put("show_status_bar_time", config.showStatusBarTime)
        json.put("show_status_bar_battery", config.showStatusBarBattery)
        json.put("show_status_bar_ram", config.showStatusBarRam)

        // Gesture Controls
        json.put("gesture_swipe_up", config.gestureSwipeUp)
        json.put("gesture_swipe_down", config.gestureSwipeDown)
        json.put("gesture_swipe_left", config.gestureSwipeLeft)
        json.put("gesture_swipe_right", config.gestureSwipeRight)
        json.put("gesture_double_tap", config.gestureDoubleTap)
        json.put("gesture_long_press", config.gestureLongPress)

        // Compound Gestures
        json.put("compound_gestures_enabled", config.compoundGesturesEnabled)
        json.put("gesture_two_finger_up", config.gestureTwoFingerUp)
        json.put("gesture_pinch_in", config.gesturePinchIn)
        json.put("gesture_pinch_out", config.gesturePinchOut)
        json.put("gesture_left_edge_swipe", config.gestureLeftEdgeSwipe)
        json.put("gesture_right_edge_swipe", config.gestureRightEdgeSwipe)
        json.put("gesture_bottom_edge_swipe", config.gestureBottomEdgeSwipe)

        // Drawer & App Icon Customizations
        json.put("show_app_icons", config.showAppIcons)
        json.put("app_icon_style", config.appIconStyle)
        json.put("drawer_alpha", config.drawerAlpha.toDouble())
        json.put("drawer_edge_transparent", config.drawerEdgeTransparent)
        json.put("drawer_width_percent", config.drawerWidthPercent)
        json.put("enable_atoz_scrubber", config.enableAtoZScrubber)

        // File Browser Customizations
        json.put("files_default_path", config.filesDefaultPath)
        json.put("files_show_hidden", config.filesShowHidden)

        // Thermal & Guardian
        json.put("thermal_limit_celsius", config.thermalLimitCelsius.toDouble())
        json.put("thermal_guardian_enabled", config.thermalGuardianEnabled)
        val thermalKills = JSONArray()
        for (cmd in config.thermalKillCommands) thermalKills.put(cmd)
        json.put("thermal_kill_commands", thermalKills)
        json.put("thermal_hysteresis_celsius", config.thermalHysteresisCelsius.toDouble())

        // Identity & Personas
        json.put("default_identity", config.defaultIdentity)
        json.put("identity_void_label", config.identityVoidLabel)
        json.put("identity_main_label", config.identityMainLabel)
        json.put("active_persona", config.activePersona)

        val personasArr = JSONArray()
        for (p in config.personas) {
            personasArr.put(JSONObject().apply {
                put("id", p.id)
                put("label", p.label)
                put("subtitle", p.subtitle)
                put("accent", p.accentColor)
                put("secondary", p.secondaryColor)
                put("surface", p.surfaceColor)
                put("workspace", p.workspacePath)
                put("glyph_pattern", p.glyphPatternName)
            })
        }
        json.put("personas", personasArr)

        // Omni-Bar & History
        json.put("omni_bar_hint", config.omniBarHint)
        json.put("omni_prompt_symbol", config.omniPromptSymbol)
        json.put("omni_fuzzy_max_results", config.omniFuzzyMaxResults)
        json.put("command_history_enabled", config.commandHistoryEnabled)
        json.put("command_history_limit", config.commandHistoryLimit)

        // Workspace
        json.put("default_workspace_path", config.defaultWorkspacePath)
        json.put("workflow_json_path", config.workflowJsonPath)
        json.put("nodeos_db_path", config.nodeosDbPath)
        json.put("nodeos_legacy_import_enabled", config.nodeOsLegacyImportEnabled)

        // Glyph & Haptics
        json.put("enable_glyph_pulse", config.enableGlyphPulse)
        json.put("glyph_patterns_path", config.glyphPatternsPath)
        // Haptics
        json.put("enable_haptics", config.enableHaptics)

        // Liquid touch surface
        json.put("liquid_touch_enabled", config.liquidTouchEnabled)

        // Clock
        json.put("clock_format", config.clockFormat)
        json.put("clock_suffix", config.clockSuffix)

        // Telemetry & Power Profile
        json.put("telemetry_poll_ms", config.telemetryPollMs)
        json.put("power_profile_adaptive", config.powerProfileAdaptive)

        // AST Graph & Widgets
        json.put("ast_graph_enabled", config.astGraphEnabled)
        json.put("ast_graph_node_cap", config.astGraphNodeCap)
        json.put("agent_orchestrator_enabled", config.agentOrchestratorEnabled)
        json.put("widgets_enabled", config.widgetsEnabled)
        json.put("widgets_directory", config.widgetsDirectory)
        json.put("focus_scheduler_enabled", config.focusSchedulerEnabled)
        json.put("focus_schedule_path", config.focusSchedulePath)

        // VoidTerm & Macros
        json.put("voidterm_enabled", config.voidTermEnabled)
        json.put("voidterm_rows", config.voidTermRows)
        json.put("voidterm_cols", config.voidTermCols)
        json.put("macros_enabled", config.macrosEnabled)
        json.put("macros_directory", config.macrosDirectory)
        json.put("shell_timeout_seconds", config.shellTimeoutSeconds)
        json.put("shell_max_output_chars", config.shellMaxOutputChars)

        // Notification Intelligence filters
        val notifWhitelist = JSONArray()
        for (p in config.notifWhitelistPackages) notifWhitelist.put(p)
        json.put("notif_whitelist_packages", notifWhitelist)

        val notifBlacklist = JSONArray()
        for (p in config.notifBlacklistPackages) notifBlacklist.put(p)
        json.put("notif_blacklist_packages", notifBlacklist)

        val notifKeywords = JSONArray()
        for (k in config.notifPriorityKeywords) notifKeywords.put(k)
        json.put("notif_priority_keywords", notifKeywords)

        return json
    }
}
