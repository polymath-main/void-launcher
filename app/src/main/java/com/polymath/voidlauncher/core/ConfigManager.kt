package com.polymath.voidlauncher.core

import android.content.Context
import android.util.Log
import com.polymath.voidlauncher.model.ColorPalette
import com.polymath.voidlauncher.model.DockSlot
import com.polymath.voidlauncher.model.LauncherConfig
import com.polymath.voidlauncher.model.defaultDockSlots
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages VoidLauncher visual & behavioral configuration.
 *
 * Config resolution order (first found wins):
 *   1. External user override: ~/.voidlauncher/config.json
 *   2. App-internal data:      /data/data/<pkg>/files/config.json
 *   3. Bundled default:        assets/default_config.json
 *   4. Hardcoded Kotlin defaults
 *
 * Users can edit the JSON from Termux:
 *   nano ~/.voidlauncher/config.json
 *
 * The launcher hot-reloads on every onResume().
 */
class ConfigManager(private val context: Context) {

    companion object {
        private const val TAG = "ConfigManager"
        const val EXTERNAL_CONFIG_DIR = "/data/data/com.termux/files/home/.voidlauncher"
        const val EXTERNAL_CONFIG_FILE = "$EXTERNAL_CONFIG_DIR/config.json"
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
     * Writes the bundled defaults to the external path so the user has a template to edit.
     */
    fun exportDefaultConfig() {
        saveConfig(LauncherConfig())
    }

    // ── File readers ──────────────────────────────────────────

    private fun readExternalConfig(): JSONObject? {
        return try {
            val file = File(EXTERNAL_CONFIG_FILE)
            if (file.exists() && file.canRead()) {
                JSONObject(file.readText())
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read external config: ${e.message}")
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

        return LauncherConfig(
            colors = colors,
            fontFamily = json.optString("font_family", "monospace"),
            hudFontSizeSp = json.optDouble("hud_font_size_sp", 11.0).toFloat(),
            dockFontSizeSp = json.optDouble("dock_font_size_sp", 12.5).toFloat(),
            omniFontSizeSp = json.optDouble("omni_font_size_sp", 12.5).toFloat(),
            dockSlots = dockSlots,
            defaultHomeMode = json.optString("default_home_mode", "COCKPIT"),
            showTelemetryBar = json.optBoolean("show_telemetry_bar", true),
            showNodeOsCard = json.optBoolean("show_nodeos_card", true),
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
            defaultIdentity = json.optString("default_identity", "VOID"),
            identityVoidLabel = json.optString("identity_void_label", "[VOID]"),
            identityMainLabel = json.optString("identity_main_label", "[MAIN]"),
            omniBarHint = json.optString("omni_bar_hint", ">_ search apps or enter :cmd"),
            omniPromptSymbol = json.optString("omni_prompt_symbol", ">_ "),
            omniFuzzyMaxResults = json.optInt("omni_fuzzy_max_results", 5),
            defaultWorkspacePath = json.optString("default_workspace_path", "/data/data/com.termux/files/home/Projects"),
            workflowJsonPath = json.optString("workflow_json_path", "/data/data/com.termux/files/home/Projects/workflow.json"),
            nodeosDbPath = json.optString("nodeos_db_path", "/data/data/com.termux/files/home/Projects/agy_nodeos.db"),
            enableGlyphPulse = json.optBoolean("enable_glyph_pulse", true),
            enableHaptics = json.optBoolean("enable_haptics", true),
            clockFormat = json.optString("clock_format", "HH:mm:ss"),
            clockSuffix = json.optString("clock_suffix", " UTC+6"),
            telemetryPollMs = json.optLong("telemetry_poll_ms", 1500L)
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

        // Thermal
        json.put("thermal_limit_celsius", config.thermalLimitCelsius.toDouble())

        // Identity
        json.put("default_identity", config.defaultIdentity)
        json.put("identity_void_label", config.identityVoidLabel)
        json.put("identity_main_label", config.identityMainLabel)

        // Omni-Bar
        json.put("omni_bar_hint", config.omniBarHint)
        json.put("omni_prompt_symbol", config.omniPromptSymbol)
        json.put("omni_fuzzy_max_results", config.omniFuzzyMaxResults)

        // Workspace
        json.put("default_workspace_path", config.defaultWorkspacePath)
        json.put("workflow_json_path", config.workflowJsonPath)
        json.put("nodeos_db_path", config.nodeosDbPath)

        // Glyph & Haptics
        json.put("enable_glyph_pulse", config.enableGlyphPulse)
        json.put("enable_haptics", config.enableHaptics)

        // Clock
        json.put("clock_format", config.clockFormat)
        json.put("clock_suffix", config.clockSuffix)

        // Advanced
        json.put("telemetry_poll_ms", config.telemetryPollMs)

        return json
    }
}
