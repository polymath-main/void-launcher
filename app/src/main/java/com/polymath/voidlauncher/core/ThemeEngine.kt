package com.polymath.voidlauncher.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.polymath.voidlauncher.core.services.WallpaperMicroService
import com.polymath.voidlauncher.model.ColorPalette
import com.polymath.voidlauncher.model.LauncherConfig
import com.polymath.voidlauncher.model.VoidPaths
import com.polymath.voidlauncher.model.VoidThemeManifest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Theme Engine (Appearance Studio v2).
 *
 * Exposes a reactive [StateFlow] of the active [VoidThemeManifest] backed by
 * [ConfigManager]. Coordinates token resolution, custom theme storage in
 * [VoidPaths.THEMES], FileProvider export, and `void://` URI import.
 */
object ThemeEngine {

    private const val TAG = "ThemeEngine"
    const val CUSTOM_ID = "custom"

    /** Backward-compatible Preset wrapper for existing UI spinners and callers. */
    data class Preset(
        val id: String,
        val name: String,
        val palette: ColorPalette,
        val manifest: VoidThemeManifest
    )

    /** 5 Core presets exposed for spinners / legacy references. */
    val presets: List<Preset> = BuiltInThemes.ALL.map { manifest ->
        Preset(
            id = manifest.id,
            name = manifest.name,
            palette = manifest.toColorPalette(),
            manifest = manifest
        )
    }

    private val _themeState = MutableStateFlow(BuiltInThemes.OBSIDIAN_VOID)
    val themeState: StateFlow<VoidThemeManifest> = _themeState.asStateFlow()

    /** Currently active theme manifest. */
    val currentTheme: VoidThemeManifest get() = _themeState.value

    /**
     * Initializes or synchronizes the active theme from the current launcher config.
     */
    fun syncFromConfig(config: LauncherConfig) {
        val resolved = resolveManifest(config.themeId, config.colors)
        _themeState.value = resolved
    }

    /** Resolves a Preset by ID (built-in or alias). */
    fun byId(id: String): Preset? {
        val m = BuiltInThemes.byId(id) ?: return null
        return presets.firstOrNull { it.id.equals(m.id, ignoreCase = true) } ?: Preset(
            id = m.id,
            name = m.name,
            palette = m.toColorPalette(),
            manifest = m
        )
    }

    /** Resolves effective ColorPalette for a theme ID or config fallback. */
    fun paletteFor(themeId: String, fallback: ColorPalette): ColorPalette {
        val manifest = resolveManifest(themeId, fallback)
        return manifest.toColorPalette()
    }

    fun ids(): List<String> = presets.map { it.id }

    /**
     * Resolves manifest from built-ins, custom storage (`*.voidtheme`), or legacy palette.
     */
    fun resolveManifest(themeId: String, fallbackColors: ColorPalette? = null): VoidThemeManifest {
        // 1. Check built-ins
        val builtIn = BuiltInThemes.byId(themeId)
        if (builtIn != null) return builtIn

        // 2. Check custom themes on disk
        val custom = loadCustomTheme(themeId)
        if (custom != null) return custom

        // 3. Fallback to custom palette or Obsidian Void
        return if (fallbackColors != null) {
            VoidThemeManifest.fromLegacyPalette(
                id = if (themeId.isBlank()) CUSTOM_ID else themeId,
                name = if (themeId.isBlank() || themeId == CUSTOM_ID) "Custom" else themeId,
                palette = fallbackColors,
                template = BuiltInThemes.OBSIDIAN_VOID
            )
        } else {
            BuiltInThemes.OBSIDIAN_VOID
        }
    }

    /**
     * Returns all available themes: 5 built-in presets + any user-created
     * `*.voidtheme` files under [VoidPaths.THEMES].
     */
    fun getAllThemes(): List<VoidThemeManifest> {
        val list = mutableListOf<VoidThemeManifest>()
        list.addAll(BuiltInThemes.ALL)

        val customDir = File(VoidPaths.THEMES)
        if (customDir.exists() && customDir.isDirectory) {
            val files = customDir.listFiles { f -> f.extension == "voidtheme" }
            files?.forEach { file ->
                try {
                    val m = VoidThemeManifest.fromJsonString(file.readText(), trusted = true)
                    if (list.none { it.id.equals(m.id, ignoreCase = true) }) {
                        list.add(m)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load custom theme from ${file.name}: ${e.message}")
                }
            }
        }
        return list
    }

    /** Loads a specific custom theme from disk. */
    fun loadCustomTheme(id: String): VoidThemeManifest? {
        val dir = File(VoidPaths.THEMES)
        if (!dir.exists()) return null
        val target = File(dir, "${VoidThemeManifest.sanitizeId(id)}.voidtheme")
        if (!target.exists() || !target.canRead()) return null
        return try {
            VoidThemeManifest.fromJsonString(target.readText(), trusted = true)
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing custom theme ${target.name}: ${e.message}")
            null
        }
    }

    /**
     * Applies a theme manifest: updates [themeState], constructs updated [LauncherConfig],
     * saves through [ConfigManager], and optionally notifies [WallpaperMicroService].
     */
    fun applyTheme(
        manifest: VoidThemeManifest,
        currentConfig: LauncherConfig,
        configManager: ConfigManager,
        wallpaperService: WallpaperMicroService? = null,
        activity: Activity? = null
    ): LauncherConfig {
        val norm = manifest.normalized()
        _themeState.value = norm

        // impl-03: scrim + custom path live in config (not service prefs).
        // A theme with no custom path must NOT clear the user's wallpaper.
        // Build new config
        val updatedConfig = currentConfig.copy(
            themeId = norm.id,
            colors = norm.toColorPalette(),
            useSystemWallpaper = norm.showSystemWallpaper,
            wallpaperDimScrimAlpha = norm.scrimLevel.alpha255,
            customWallpaperPath = if (norm.customWallpaperPath.isNotBlank()) norm.customWallpaperPath else currentConfig.customWallpaperPath,
            fontFamily = norm.monoFont.ifBlank { currentConfig.fontFamily },
            cockpitCardRadiusDp = norm.cornerRadiusDp.toFloat(),
            activePersona = norm.defaultPersona.personaId
        )

        configManager.saveConfig(updatedConfig)

        // Live update Window decor and wallpaper if activity is provided
        if (activity != null && wallpaperService != null) {
            wallpaperService.applyWallpaperState(
                activity,
                activity.findViewById(android.R.id.content),
                null,
                updatedConfig
            )
        }

        return updatedConfig
    }

    /**
     * Saves a custom theme manifest as `<id>.voidtheme` in [VoidPaths.THEMES].
     */
    fun saveCustomTheme(manifest: VoidThemeManifest): File {
        val dir = File(VoidPaths.THEMES)
        if (!dir.exists()) dir.mkdirs()

        val norm = manifest.copy(isBuiltIn = false).normalized()
        val file = File(dir, "${norm.id}.voidtheme")
        file.writeText(norm.toJsonString(2))
        Log.i(TAG, "Saved custom theme to ${file.absolutePath}")
        return file
    }

    /** Deletes a custom theme file from disk. */
    fun deleteCustomTheme(id: String): Boolean {
        if (BuiltInThemes.byId(id) != null) return false // Built-ins cannot be deleted
        val file = File(VoidPaths.THEMES, "${VoidThemeManifest.sanitizeId(id)}.voidtheme")
        return file.exists() && file.delete()
    }

    /**
     * Generates an Android Share Intent for a theme.
     * Includes both the `void://theme/import?payload=<base64url>` compact link and
     * a temporary attached `*.voidtheme` file through FileProvider.
     */
    fun exportShareIntent(context: Context, manifest: VoidThemeManifest): Intent {
        val norm = manifest.normalized()
        val shareUriString = norm.encodeShareUri()

        // Write to cache for FileProvider attachment
        val cacheFile = File(context.cacheDir, "${norm.id}.voidtheme")
        cacheFile.writeText(norm.toJsonString(2))

        val contentUri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )
        } catch (e: Exception) {
            Log.w(TAG, "FileProvider error: ${e.message}")
            Uri.EMPTY
        }

        val shareText = "VoidLauncher Theme: ${norm.name}\n" +
            "Install: $shareUriString\n\n" +
            norm.toJsonString(2)

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Void Theme: ${norm.name}")
            putExtra(Intent.EXTRA_TEXT, shareText)
            if (contentUri != Uri.EMPTY) {
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    /**
     * Imports a theme from a `void://` URI string, raw JSON string, or file path.
     */
    fun importFromUriOrString(raw: String): Result<VoidThemeManifest> {
        return try {
            val trimmed = raw.trim()
            val manifest = when {
                VoidThemeManifest.isShareUri(trimmed) -> {
                    VoidThemeManifest.decodeShareUri(trimmed)
                }
                trimmed.startsWith("{") -> {
                    VoidThemeManifest.fromJsonString(trimmed, trusted = false)
                }
                trimmed.endsWith(".voidtheme") -> {
                    val file = File(trimmed)
                    require(file.exists() && file.canRead()) { "File not accessible: $trimmed" }
                    VoidThemeManifest.fromJsonString(file.readText(), trusted = false)
                }
                else -> {
                    // Try decoding as bare payload
                    val json = VoidThemeManifest.decodePayload(trimmed)
                    VoidThemeManifest.fromJsonString(json, trusted = false)
                }
            }
            // Save imported theme to disk
            saveCustomTheme(manifest)
            Result.success(manifest)
        } catch (e: Exception) {
            Log.e(TAG, "Theme import failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
