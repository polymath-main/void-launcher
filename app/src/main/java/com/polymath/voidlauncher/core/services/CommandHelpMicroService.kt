package com.polymath.voidlauncher.core.services

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.polymath.voidlauncher.core.CommandPalette
import com.polymath.voidlauncher.core.ConfigManager
import com.polymath.voidlauncher.core.ThemeEngine
import com.polymath.voidlauncher.model.LauncherConfig
import com.polymath.voidlauncher.model.VoidThemeManifest

/**
 * Micro-service handling Omni-Bar inline snippet extraction (`## <alias>`),
 * snippet lookup, `:help` reference formatting, `:wallpaper` subcommands,
 * and the `:theme` management command suite (`list`, `apply`, `save`, `export`, `import`).
 */
class CommandHelpMicroService(
    private val activity: Activity,
    private val commandPalette: CommandPalette,
    private val wallpaperService: WallpaperMicroService
) {

    /**
     * Parses `command ## alias`, saves the snippet if present, and returns
     * the stripped command ready for execution.
     */
    fun extractInlineSnippet(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (!trimmed.contains("##")) return trimmed
        val parts = trimmed.split("##", limit = 2)
        val cmdPart = parts[0].trim()
        val aliasPart = parts.getOrNull(1)?.trim().orEmpty()
        if (cmdPart.isNotEmpty() && aliasPart.isNotEmpty()) {
            commandPalette.saveSnippet(aliasPart, cmdPart)
            Toast.makeText(activity, "Saved snippet :$aliasPart", Toast.LENGTH_SHORT).show()
        }
        return cmdPart
    }

    /**
     * Resolves whether `command` matches a saved snippet alias.
     */
    fun findSnippetTarget(command: String): String? {
        return commandPalette.getSnippets().firstOrNull {
            it.snippetAlias.equals(command, ignoreCase = true)
        }?.commandText?.trim()
    }

    /**
     * impl-04 step 0: generated from the CommandGuide table, not a
     * hard-coded string — the old text said dock 1–5 (dock is 1–7)
     * and omitted commands. Adding a command means adding a row to
     * CommandGuide, not hand-editing this string again.
     */
    fun formatQuickGuide(): String = com.polymath.voidlauncher.core.CommandGuide.formatQuickGuide()

    /**
     * Handles `:wallpaper` and `:wp` commands.
     */
    fun handleWallpaperCommand(
        subCommand: String,
        config: LauncherConfig,
        onUpdateConfig: (LauncherConfig) -> Unit,
        onShowOutput: (String) -> Unit
    ) {
        val arg = subCommand.trim()
        when {
            arg.isEmpty() || arg.equals("studio", ignoreCase = true) -> {
                wallpaperService.showWallpaperStudioDialog(activity, config, onUpdateConfig)
            }
            arg.equals("on", ignoreCase = true) || arg.equals("enable", ignoreCase = true) -> {
                onUpdateConfig(config.copy(useSystemWallpaper = true))
                onShowOutput("[WALLPAPER]: Enabled (System / Custom Image active)")
            }
            arg.equals("off", ignoreCase = true) || arg.equals("disable", ignoreCase = true) -> {
                onUpdateConfig(config.copy(useSystemWallpaper = false))
                onShowOutput("[WALLPAPER]: Disabled (Pure OLED Pitch Black)")
            }
            arg.equals("pick", ignoreCase = true) -> {
                onUpdateConfig(config.copy(useSystemWallpaper = true))
                wallpaperService.openSystemWallpaperPicker(activity)
            }
            arg.equals("clear", ignoreCase = true) -> {
                wallpaperService.clearCustomWallpaper()
                onUpdateConfig(config.copy(customWallpaperPath = ""))
                onShowOutput("[WALLPAPER]: Custom image override cleared")
            }
            arg.equals("status", ignoreCase = true) -> {
                onShowOutput(wallpaperService.wallpaperStatus(config))
            }
            arg.startsWith("set ") -> {
                val path = arg.removePrefix("set ").trim()
                val ok = wallpaperService.setWallpaperFromFile(path, alsoSetSystemWallpaper = true)
                if (ok) {
                    val owned = wallpaperService.lastOwnedCopyPath ?: path
                    onUpdateConfig(config.copy(useSystemWallpaper = true, customWallpaperPath = owned))
                    onShowOutput("[WALLPAPER APPLIED]: $owned (source: $path)")
                } else {
                    onShowOutput("[WALLPAPER ERROR]: Cannot load image at $path")
                }
            }
            else -> {
                onShowOutput(
                    "=== Wallpaper Commands ===\n" +
                    ":wallpaper          Open interactive Wallpaper Studio\n" +
                    ":wallpaper on       Show system / custom wallpaper\n" +
                    ":wallpaper off      Pure OLED pitch-black mode\n" +
                    ":wallpaper pick     Open system wallpaper picker\n" +
                    ":wallpaper set <p>  Set image file <p> as wallpaper (relative paths resolve against the Void files home; the image is copied into Void home)\n" +
                    ":wallpaper status   Show mode, custom image, scrim %, and fit\n" +
                    ":wallpaper clear    Clear custom image override"
                )
            }
        }
    }

    /**
     * Handles `:theme` and `:themes` subcommands:
     * - `:theme list`
     * - `:theme apply <id>`
     * - `:theme save <name>`
     * - `:theme export`
     * - `:theme import <path|uri>`
     */
    fun handleThemeCommand(
        subCommand: String,
        config: LauncherConfig,
        configManager: ConfigManager,
        onUpdateConfig: (LauncherConfig) -> Unit,
        onShowOutput: (String) -> Unit
    ) {
        val arg = subCommand.trim()
        when {
            arg.isEmpty() || arg.equals("list", ignoreCase = true) -> {
                val allThemes = ThemeEngine.getAllThemes()
                val current = ThemeEngine.currentTheme
                val sb = StringBuilder("=== Void Themes (${allThemes.size}) ===\n\n")
                for (t in allThemes) {
                    val mark = if (t.id.equals(current.id, ignoreCase = true) || t.id.equals(config.themeId, ignoreCase = true)) "●" else "○"
                    val tag = if (t.isBuiltIn) "[PRESET]" else "[USER]"
                    sb.appendLine("$mark ${t.id.padEnd(20)} ${t.name} $tag")
                }
                sb.appendLine("\nCommands:")
                sb.appendLine(":theme apply <id>       Apply theme by ID")
                sb.appendLine(":theme save <name>      Save current tokens as theme")
                sb.appendLine(":theme export           Export and share current theme")
                sb.appendLine(":theme import <uri|p>   Import void:// URI or .voidtheme")
                onShowOutput(sb.toString().trimEnd())
            }

            arg.startsWith("apply ") -> {
                val id = arg.removePrefix("apply ").trim()
                applyThemeById(id, config, configManager, onUpdateConfig, onShowOutput)
            }

            arg.startsWith("save ") -> {
                val name = arg.removePrefix("save ").trim()
                if (name.isEmpty()) {
                    onShowOutput("Usage: :theme save <name>")
                    return
                }
                val current = ThemeEngine.currentTheme
                val id = VoidThemeManifest.sanitizeId(name)
                val custom = current.copy(
                    id = id,
                    name = name,
                    author = "User",
                    isBuiltIn = false,
                    showSystemWallpaper = config.useSystemWallpaper,
                    customWallpaperPath = config.customWallpaperPath
                ).normalized()

                val savedFile = ThemeEngine.saveCustomTheme(custom)
                val updated = ThemeEngine.applyTheme(custom, config, configManager, wallpaperService, activity)
                onUpdateConfig(updated)
                onShowOutput("[THEME SAVED & APPLIED]: ${custom.name}\nFile: ${savedFile.absolutePath}")
            }

            arg.equals("export", ignoreCase = true) || arg.equals("share", ignoreCase = true) -> {
                val current = ThemeEngine.currentTheme
                val shareUri = current.encodeShareUri()
                val shareIntent = ThemeEngine.exportShareIntent(activity, current)
                try {
                    activity.startActivity(Intent.createChooser(shareIntent, "Share Theme: ${current.name}"))
                } catch (_: Exception) {}
                onShowOutput(
                    "[THEME EXPORT]: ${current.name}\n" +
                    "Share URI: $shareUri\n\n" +
                    current.toJsonString(2)
                )
            }

            arg.startsWith("import ") -> {
                val payloadOrPath = arg.removePrefix("import ").trim()
                val result = ThemeEngine.importFromUriOrString(payloadOrPath)
                result.onSuccess { manifest ->
                    val updated = ThemeEngine.applyTheme(manifest, config, configManager, wallpaperService, activity)
                    onUpdateConfig(updated)
                    onShowOutput("[THEME IMPORTED & APPLIED]: ${manifest.name} (${manifest.id})\nShader: ${manifest.overlayShader.name} · Dock: ${manifest.dockStyle.name}")
                }.onFailure { err ->
                    onShowOutput("[THEME IMPORT FAILED]: ${err.message}")
                }
            }

            // Direct theme id shortcut: `:theme obsidian_void` or `:theme matrix`
            else -> {
                applyThemeById(arg, config, configManager, onUpdateConfig, onShowOutput)
            }
        }
    }

    private fun applyThemeById(
        id: String,
        config: LauncherConfig,
        configManager: ConfigManager,
        onUpdateConfig: (LauncherConfig) -> Unit,
        onShowOutput: (String) -> Unit
    ) {
        val manifest = ThemeEngine.resolveManifest(id, null)
        val isKnown = ThemeEngine.getAllThemes().any { it.id.equals(id, ignoreCase = true) } ||
            ThemeEngine.byId(id) != null

        if (!isKnown && !id.equals(ThemeEngine.CUSTOM_ID, ignoreCase = true)) {
            onShowOutput("[ERROR]: Unknown theme ID '$id'. Type :theme list to view all themes.")
            return
        }

        val updated = ThemeEngine.applyTheme(manifest, config, configManager, wallpaperService, activity)
        onUpdateConfig(updated)
        onShowOutput("[THEME APPLIED]: ${manifest.name}\nShader: ${manifest.overlayShader.name} · Scrim: ${manifest.scrimLevel.label} · Dock: ${manifest.dockStyle.name}")
    }
}
