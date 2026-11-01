package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.model.ColorPalette

/**
 * Theme Engine (Appearance Studio).
 *
 * Provides named theme presets that fully define the [ColorPalette]. Selecting a
 * preset applies every token at once; manual color edits produce a `custom` theme.
 */
object ThemeEngine {

    const val CUSTOM_ID = "custom"

    data class Preset(
        val id: String,
        val name: String,
        val palette: ColorPalette
    )

    val presets: List<Preset> = listOf(
        Preset("void", "Void Crimson", ColorPalette()),

        Preset("obsidian", "Obsidian STILL", ColorPalette(
            background = "#000000", surface = "#0A0A0C", surfacePressed = "#161618",
            border = "#1C1C1E", borderActive = "#3A3A3C",
            textPrimary = "#FFFFFF", textSecondary = "#A1A1AA", textMuted = "#52525B",
            accent = "#007AFF", accentSurface = "#0A2540",
            secondary = "#5AC8FA", secondarySurface = "#0A2540",
            safe = "#30D158", warning = "#FF9F0A", critical = "#FF453A"
        )),

        Preset("midnight", "Midnight Blue", ColorPalette(
            background = "#050B18", surface = "#0A1526", surfacePressed = "#132238",
            border = "#16294A", borderActive = "#2A4A7A",
            textPrimary = "#E6EEF8", textSecondary = "#93A7C0", textMuted = "#4C5F7A",
            accent = "#4C8DFF", accentSurface = "#0B1F3A",
            secondary = "#22D3EE", secondarySurface = "#07283A",
            safe = "#34D399", warning = "#FBBF24", critical = "#F87171"
        )),

        Preset("graphite", "Graphite", ColorPalette(
            background = "#0A0A0A", surface = "#141414", surfacePressed = "#1F1F1F",
            border = "#262626", borderActive = "#404040",
            textPrimary = "#F5F5F5", textSecondary = "#A3A3A3", textMuted = "#525252",
            accent = "#D4D4D4", accentSurface = "#262626",
            secondary = "#8A8A8A", secondarySurface = "#1F1F1F",
            safe = "#A3A3A3", warning = "#C7A76B", critical = "#C77B7B"
        )),

        Preset("matrix", "Phosphor Matrix", ColorPalette(
            background = "#000800", surface = "#04140A", surfacePressed = "#08210F",
            border = "#0B3B1E", borderActive = "#12603A",
            textPrimary = "#D1FAE5", textSecondary = "#6EE7B7", textMuted = "#3F6F55",
            accent = "#22C55E", accentSurface = "#052E16",
            secondary = "#10B981", secondarySurface = "#04231A",
            safe = "#34D399", warning = "#A3E635", critical = "#F87171"
        )),

        Preset("amber", "Amber Terminal", ColorPalette(
            background = "#0A0700", surface = "#17110A", surfacePressed = "#241A0E",
            border = "#3B2A10", borderActive = "#5C421A",
            textPrimary = "#FDE68A", textSecondary = "#D6A94C", textMuted = "#7A5E2A",
            accent = "#F59E0B", accentSurface = "#3A2405",
            secondary = "#FBBF24", secondarySurface = "#3A2E05",
            safe = "#A3E635", warning = "#FB923C", critical = "#F87171"
        ))
    )

    fun byId(id: String): Preset? =
        presets.firstOrNull { it.id.equals(id, ignoreCase = true) }

    /** Resolves the effective palette for a config: preset palette or the config's own colors. */
    fun paletteFor(themeId: String, fallback: ColorPalette): ColorPalette =
        byId(themeId)?.palette ?: fallback

    fun ids(): List<String> = presets.map { it.id }
}
