package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.model.DockStyle
import com.polymath.voidlauncher.model.GlyphMode
import com.polymath.voidlauncher.model.OverlayShaderType
import com.polymath.voidlauncher.model.ScrimLevel
import com.polymath.voidlauncher.model.ThemePersona
import com.polymath.voidlauncher.model.VoidThemeManifest

/**
 * Built-in Core Theme Presets for STILL & COCKPIT v2 (Blueprint 07 §2).
 *
 * Implements the 5 complete reference themes with strict token fidelity,
 * >=48dp touch targets, overlay shaders, scrim dimming, and micro-service hooks.
 */
object BuiltInThemes {

    /** 1. OBSIDIAN_VOID (Nothing Dot-Matrix Native) */
    val OBSIDIAN_VOID = VoidThemeManifest(
        schemaVersion = VoidThemeManifest.SCHEMA_VERSION,
        id = "obsidian_void",
        name = "Obsidian Void",
        author = "VoidSystem",
        isBuiltIn = true,
        surfacePrimaryHex = "#000000",
        surfaceElevatedHex = "#0D0D0D",
        textPrimaryHex = "#FFFFFF",
        textSecondaryHex = "#A3A3A3",
        accentPrimaryHex = "#FF1E1E",
        accentWarnHex = "#FFB020",
        borderHairlineHex = "#262626",
        headerFont = "monospace",
        monoFont = "monospace",
        cornerRadiusDp = 14,
        borderWidthDp = 1,
        touchTargetMinDp = 48,
        overlayShader = OverlayShaderType.DOT_MATRIX,
        showSystemWallpaper = false,
        scrimLevel = ScrimLevel.SCRIM_70,
        customWallpaperPath = "",
        defaultPersona = ThemePersona.VOID,
        glyphMode = GlyphMode.PULSE_SYNC,
        dockStyle = DockStyle.NUMERIC_BRACKET
    )

    /** 2. PHOSPHOR_TERMINAL (Amber/Emerald VoidShell CRT) */
    val PHOSPHOR_TERMINAL = VoidThemeManifest(
        schemaVersion = VoidThemeManifest.SCHEMA_VERSION,
        id = "phosphor_terminal",
        name = "Phosphor Terminal",
        author = "VoidSystem",
        isBuiltIn = true,
        surfacePrimaryHex = "#050806",
        surfaceElevatedHex = "#0B140E",
        textPrimaryHex = "#00FF66",
        textSecondaryHex = "#00AA44",
        accentPrimaryHex = "#00FF66",
        accentWarnHex = "#FFB000",
        borderHairlineHex = "#0F2B18",
        headerFont = "monospace",
        monoFont = "monospace",
        cornerRadiusDp = 4,
        borderWidthDp = 1,
        touchTargetMinDp = 48,
        overlayShader = OverlayShaderType.CRT_SCANLINE,
        showSystemWallpaper = false,
        scrimLevel = ScrimLevel.SCRIM_70,
        customWallpaperPath = "",
        defaultPersona = ThemePersona.AGENT,
        glyphMode = GlyphMode.ACTIVE_HUD,
        dockStyle = DockStyle.ASCII_TTY
    )

    /** 3. STILLNESS_EINK (Daylight Paper / Slate Minimalist) */
    val STILLNESS_EINK = VoidThemeManifest(
        schemaVersion = VoidThemeManifest.SCHEMA_VERSION,
        id = "stillness_eink",
        name = "Stillness E-Ink",
        author = "VoidSystem",
        isBuiltIn = true,
        surfacePrimaryHex = "#F4F1EA",
        surfaceElevatedHex = "#EAE5D9",
        textPrimaryHex = "#1A1A1A",
        textSecondaryHex = "#525252",
        accentPrimaryHex = "#525252",
        accentWarnHex = "#B45309",
        borderHairlineHex = "#D6D1C4",
        headerFont = "serif",
        monoFont = "monospace",
        cornerRadiusDp = 2,
        borderWidthDp = 1,
        touchTargetMinDp = 48,
        overlayShader = OverlayShaderType.PAPER_GRAIN,
        showSystemWallpaper = false,
        scrimLevel = ScrimLevel.SCRIM_25,
        customWallpaperPath = "",
        defaultPersona = ThemePersona.STILL,
        glyphMode = GlyphMode.SILENT_FLIP,
        dockStyle = DockStyle.EDITORIAL_SERIF
    )

    /** 4. CYBER_TELEMETRY (Translucent Scrim & Neon HUD) */
    val CYBER_TELEMETRY = VoidThemeManifest(
        schemaVersion = VoidThemeManifest.SCHEMA_VERSION,
        id = "cyber_telemetry",
        name = "Cyber Telemetry",
        author = "VoidSystem",
        isBuiltIn = true,
        surfacePrimaryHex = "#0A0F1A",
        surfaceElevatedHex = "#111C30",
        textPrimaryHex = "#E2F1FF",
        textSecondaryHex = "#7096BA",
        accentPrimaryHex = "#00F0FF",
        accentWarnHex = "#FF0055",
        borderHairlineHex = "#1A3050",
        headerFont = "sans-serif",
        monoFont = "monospace",
        cornerRadiusDp = 10,
        borderWidthDp = 1,
        touchTargetMinDp = 48,
        overlayShader = OverlayShaderType.NEON_HUD,
        showSystemWallpaper = true,
        scrimLevel = ScrimLevel.SCRIM_45,
        customWallpaperPath = "",
        defaultPersona = ThemePersona.VOID,
        glyphMode = GlyphMode.ACTIVE_HUD,
        dockStyle = DockStyle.CHAMFERED_HUD
    )

    /** 5. TACTICAL_BLUEPRINT (Industrial CAD Grid) */
    val TACTICAL_BLUEPRINT = VoidThemeManifest(
        schemaVersion = VoidThemeManifest.SCHEMA_VERSION,
        id = "tactical_blueprint",
        name = "Tactical Blueprint",
        author = "VoidSystem",
        isBuiltIn = true,
        surfacePrimaryHex = "#0A0E17",
        surfaceElevatedHex = "#111827",
        textPrimaryHex = "#E5E9F0",
        textSecondaryHex = "#8899AC",
        accentPrimaryHex = "#FF5500",
        accentWarnHex = "#FF2200",
        borderHairlineHex = "#1E2D47",
        headerFont = "monospace",
        monoFont = "monospace",
        cornerRadiusDp = 0,
        borderWidthDp = 2,
        touchTargetMinDp = 48,
        overlayShader = OverlayShaderType.CAD_BLUEPRINT_GRID,
        showSystemWallpaper = false,
        scrimLevel = ScrimLevel.SCRIM_45,
        customWallpaperPath = "",
        defaultPersona = ThemePersona.MAIN,
        glyphMode = GlyphMode.PULSE_SYNC,
        dockStyle = DockStyle.CAD_WIREFRAME
    )

    val ALL: List<VoidThemeManifest> = listOf(
        OBSIDIAN_VOID,
        PHOSPHOR_TERMINAL,
        STILLNESS_EINK,
        CYBER_TELEMETRY,
        TACTICAL_BLUEPRINT
    )

    /** Resolves a theme ID or legacy alias to a built-in manifest. */
    fun byId(id: String): VoidThemeManifest? {
        val clean = id.trim().lowercase()
        return when (clean) {
            "obsidian_void", "void", "obsidian" -> OBSIDIAN_VOID
            "phosphor_terminal", "phosphor", "matrix", "amber" -> PHOSPHOR_TERMINAL
            "stillness_eink", "still", "eink", "stillness", "daylight" -> STILLNESS_EINK
            "cyber_telemetry", "cyber", "telemetry", "midnight" -> CYBER_TELEMETRY
            "tactical_blueprint", "blueprint", "tactical", "graphite", "cad" -> TACTICAL_BLUEPRINT
            else -> ALL.firstOrNull { it.id.equals(clean, ignoreCase = true) }
        }
    }
}
