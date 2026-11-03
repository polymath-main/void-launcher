package com.polymath.voidlauncher.core

import org.json.JSONObject

/**
 * Area 07 step 2 — non-destructive config validation.
 *
 * Pure Kotlin (org.json only, no Android types) so it runs in the
 * JVM harness. Parsing stays lenient by design: a bad key NEVER
 * rejects the whole file here — it produces a warning naming the
 * key, and ConfigManager still falls back per-field to defaults.
 * Warnings are for Settings/import UI to surface, not for
 * load-time rejection (plan §4 step 2, and the plan's NOT-do rule:
 * do not reject a whole file for one bad key without a migration +
 * user-warning story).
 *
 * Ranges below are WARNING bands, chosen in this step and recorded
 * in the impl log (plan U-04 left them unconfirmed): they warn,
 * they never clamp on load — clamping that already exists in code
 * (e.g. scrim coerceIn 0..220, notificationsMaxCount 1..20 in
 * Settings) is untouched.
 */
data class ConfigValidationWarning(val key: String, val message: String)

object ConfigValidator {

    val knownTopLevelKeys: Set<String> = setOf(
        "colors", "theme_id", "font_family", "hud_font_size_sp",
        "dock_font_size_sp", "omni_font_size_sp", "dock_slots",
        "default_home_mode", "show_telemetry_bar", "show_nodeos_card",
        "show_intent_card", "show_notifications_card",
        "notifications_count_line_only", "notifications_max_count",
        "filter_ongoing_notifications", "show_digit_keypad", "show_dock",
        "show_app_icons", "app_icon_style", "drawer_alpha",
        "drawer_edge_transparent", "drawer_width_percent",
        "enable_atoz_scrubber", "files_default_path", "files_show_hidden",
        "thermal_limit_celsius", "thermal_guardian_enabled",
        "thermal_kill_commands", "thermal_hysteresis_celsius",
        "default_identity", "identity_void_label", "identity_main_label",
        "identity_still_label", "active_persona", "personas",
        "omni_bar_hint", "omni_prompt_symbol", "omni_fuzzy_max_results",
        "command_history_enabled", "command_history_limit",
        "default_workspace_path", "workflow_json_path", "nodeos_db_path",
        "nodeos_legacy_import_enabled", "use_system_wallpaper",
        "wallpaper_dim_scrim_alpha", "custom_wallpaper_path",
        "wallpaper_fit", "cockpit_card_radius_dp", "show_status_bar_time",
        "show_status_bar_battery", "show_status_bar_ram",
        "gesture_swipe_up", "gesture_swipe_down", "gesture_swipe_left",
        "gesture_swipe_right", "gesture_double_tap", "gesture_long_press",
        "compound_gestures_enabled", "gesture_two_finger_up",
        "gesture_pinch_in", "gesture_pinch_out", "gesture_left_edge_swipe",
        "gesture_right_edge_swipe", "gesture_bottom_edge_swipe",
        "enable_glyph_pulse", "glyph_patterns_path", "enable_haptics",
        "liquid_touch_enabled", "clock_format", "clock_suffix",
        "telemetry_poll_ms", "power_profile_adaptive", "ast_graph_enabled",
        "ast_graph_node_cap", "agent_orchestrator_enabled",
        "agent_task_timeout_seconds", "widgets_enabled",
        "widgets_directory", "focus_scheduler_enabled",
        "focus_schedule_path", "notif_whitelist_packages",
        "notif_blacklist_packages", "notif_priority_keywords",
        "voidterm_enabled", "voidterm_rows", "voidterm_cols",
        "macros_enabled", "macros_directory", "js_macros_directory",
        "macro_shell_auto_allow", "shell_timeout_seconds",
        "shell_max_output_chars", "schema_version"
    )

    private val colorKeys = setOf(
        "background", "surface", "surface_pressed", "border",
        "border_active", "text_primary", "text_secondary", "text_muted",
        "accent", "accent_surface", "secondary", "secondary_surface",
        "safe", "warning", "critical"
    )

    private val homeModes = setOf("COCKPIT", "STILL", "MINIMAL", "FILES")
    private val gestureActions = setOf(
        "NONE", "OPEN_DRAWER", "OPEN_FILES", "OPEN_SEARCH",
        "OPEN_COMMAND_CENTER", "OPEN_WIDGET_DECK", "OPEN_SETTINGS",
        "OPEN_TERMINAL", "NOTIFICATIONS", "EXPAND_STATUS_BAR",
        "TOGGLE_MINIMAL", "TOGGLE_STILL", "COCKPIT_MODE", "FOCUS_OMNI",
        "OPEN_HUD", "TOGGLE_IDENTITY"
    )
    private val hexRegex = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")

    fun validate(json: JSONObject): List<ConfigValidationWarning> {
        val out = mutableListOf<ConfigValidationWarning>()
        // Unknown top-level keys.
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "_schema_version") continue // bundled asset legacy key, advisory only
            if (key !in knownTopLevelKeys) {
                out.add(ConfigValidationWarning(key, "Unknown key — ignored on load; check spelling."))
            }
        }
        // Schema version: advisory, never a gate (area 01 step 7).
        val schemaVersion = json.optInt("schema_version", -1)
        if (schemaVersion > 0 && schemaVersion > 1) {
            out.add(ConfigValidationWarning("schema_version", "Schema version $schemaVersion is newer than supported 1; unknown fields load as defaults."))
        }
        // Colours: bad hex falls back silently in the parser — warn.
        val colors = json.optJSONObject("colors")
        if (colors != null) {
            val ckeys = colors.keys()
            while (ckeys.hasNext()) {
                val key = ckeys.next()
                if (key !in colorKeys) {
                    out.add(ConfigValidationWarning("colors.$key", "Unknown colour key — ignored on load."))
                } else if (!hexRegex.matches(colors.optString(key, ""))) {
                    out.add(ConfigValidationWarning("colors.$key", "Not a #RRGGBB / #AARRGGBB colour — falls back to the preset default."))
                }
            }
        }
        // Enums.
        val mode = json.optString("default_home_mode", "")
        if (mode.isNotEmpty() && mode.uppercase() !in homeModes) {
            out.add(ConfigValidationWarning("default_home_mode", "Unknown home mode '$mode' — handled downstream as COCKPIT, not validated on load."))
        }
        for (g in listOf(
            "gesture_swipe_up", "gesture_swipe_down", "gesture_swipe_left",
            "gesture_swipe_right", "gesture_double_tap", "gesture_long_press",
            "gesture_two_finger_up", "gesture_pinch_in", "gesture_pinch_out",
            "gesture_left_edge_swipe", "gesture_right_edge_swipe", "gesture_bottom_edge_swipe"
        )) {
            val v = json.optString(g, "")
            if (v.isNotEmpty() && v.uppercase() !in gestureActions) {
                out.add(ConfigValidationWarning(g, "Unknown gesture action '$v' — falls back downstream, not validated on load."))
            }
        }
        val iconStyle = json.optString("app_icon_style", "")
        if (iconStyle.isNotEmpty() && iconStyle.lowercase() !in setOf("original", "monochrome", "tinted")) {
            out.add(ConfigValidationWarning("app_icon_style", "Unknown icon style '$iconStyle'."))
        }
        val fit = json.optString("wallpaper_fit", "")
        if (fit.isNotEmpty() && fit !in setOf("CENTER_CROP", "FIT_CENTER")) {
            out.add(ConfigValidationWarning("wallpaper_fit", "Unknown fit '$fit' — expected CENTER_CROP or FIT_CENTER."))
        }
        // Ranges (warning bands only).
        fun range(key: String, value: Double, lo: Double, hi: Double, unit: String) {
            if (value < lo || value > hi) {
                out.add(ConfigValidationWarning(key, "Value $value $unit is outside the expected $lo–$hi band — check for a typo."))
            }
        }
        if (json.has("drawer_width_percent")) range("drawer_width_percent", json.optDouble("drawer_width_percent", 85.0), 50.0, 100.0, "%")
        if (json.has("thermal_limit_celsius")) range("thermal_limit_celsius", json.optDouble("thermal_limit_celsius", 39.0), 20.0, 50.0, "°C")
        if (json.has("thermal_hysteresis_celsius")) range("thermal_hysteresis_celsius", json.optDouble("thermal_hysteresis_celsius", 2.0), 0.0, 5.0, "°C")
        if (json.has("telemetry_poll_ms")) range("telemetry_poll_ms", json.optDouble("telemetry_poll_ms", 1500.0), 200.0, 10000.0, "ms")
        if (json.has("hud_font_size_sp")) range("hud_font_size_sp", json.optDouble("hud_font_size_sp", 11.0), 8.0, 20.0, "sp")
        if (json.has("dock_font_size_sp")) range("dock_font_size_sp", json.optDouble("dock_font_size_sp", 12.5), 8.0, 20.0, "sp")
        if (json.has("omni_font_size_sp")) range("omni_font_size_sp", json.optDouble("omni_font_size_sp", 12.5), 8.0, 20.0, "sp")
        if (json.has("notifications_max_count")) range("notifications_max_count", json.optDouble("notifications_max_count", 5.0), 1.0, 20.0, "")
        if (json.has("cockpit_card_radius_dp")) range("cockpit_card_radius_dp", json.optDouble("cockpit_card_radius_dp", 14.0), 0.0, 40.0, "dp")
        if (json.has("drawer_alpha")) range("drawer_alpha", json.optDouble("drawer_alpha", 0.9), 0.0, 1.0, "")
        if (json.has("shell_timeout_seconds")) range("shell_timeout_seconds", json.optDouble("shell_timeout_seconds", 30.0), 5.0, 300.0, "s")
        if (json.has("agent_task_timeout_seconds")) range("agent_task_timeout_seconds", json.optDouble("agent_task_timeout_seconds", 60.0), 0.0, 3600.0, "s")
        if (json.has("voidterm_rows")) range("voidterm_rows", json.optDouble("voidterm_rows", 8.0), 1.0, 50.0, "")
        if (json.has("voidterm_cols")) range("voidterm_cols", json.optDouble("voidterm_cols", 48.0), 20.0, 200.0, "")
        return out
    }
}
