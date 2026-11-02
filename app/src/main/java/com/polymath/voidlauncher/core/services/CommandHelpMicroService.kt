package com.polymath.voidlauncher.core.services

import android.app.Activity
import android.widget.Toast
import com.polymath.voidlauncher.core.CommandPalette
import com.polymath.voidlauncher.model.LauncherConfig

/**
 * Micro-service handling Omni-Bar inline snippet extraction (`## <alias>`),
 * snippet lookup, `:help` reference formatting, and `:wallpaper` subcommands.
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

    fun formatQuickGuide(): String {
        return "=== VOID LAUNCHER QUICK GUIDE ===\n\n" +
            "• APPS       Type app name to search, or tap ‹ on right edge for App Drawer\n" +
            "• DOCK       Tap [1]..[5] to open slot; LONG-PRESS any slot to customize\n" +
            "• PERSONA    Tap [VOID] badge to cycle; LONG-PRESS to pick mode/persona\n" +
            "• WALLPAPER  :wallpaper (Studio) | :wallpaper on | :wallpaper off | :wallpaper pick\n" +
            "• MODES      :still | :cockpit | :minimal | :files\n" +
            "• THEMES     :themes | :theme <void|obsidian|midnight|graphite|matrix|amber>\n" +
            "• DECK & CC  :deck (Widgets) | :cmd (Command Center) | :settings\n" +
            "• TOOLS      :eval <math> | :tasks | :task <name> <cmd> | :power | :hud\n" +
            "• SNIPPETS   :snippet <alias> <cmd> | :snippets | <cmd> ## <alias>"
    }

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
                onUpdateConfig(config)
                onShowOutput("[WALLPAPER]: Custom image override cleared")
            }
            arg.startsWith("set ") -> {
                val path = arg.removePrefix("set ").trim()
                val ok = wallpaperService.setWallpaperFromFile(path, alsoSetSystemWallpaper = true)
                if (ok) {
                    onUpdateConfig(config.copy(useSystemWallpaper = true))
                    onShowOutput("[WALLPAPER APPLIED]: $path")
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
                    ":wallpaper set <p>  Set image file <p> as wallpaper\n" +
                    ":wallpaper clear    Clear custom image override"
                )
            }
        }
    }
}
