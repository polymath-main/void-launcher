package com.polymath.voidlauncher.core.services

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.voidlauncher.core.BuiltInThemes
import com.polymath.voidlauncher.core.ConfigManager
import com.polymath.voidlauncher.core.ThemeEngine
import com.polymath.voidlauncher.core.WallpaperState
import com.polymath.voidlauncher.core.shell.VoidRoots
import com.polymath.voidlauncher.model.DockStyle
import com.polymath.voidlauncher.model.LauncherConfig
import com.polymath.voidlauncher.model.OverlayShaderType
import com.polymath.voidlauncher.model.ScrimLevel
import com.polymath.voidlauncher.model.VoidPaths
import com.polymath.voidlauncher.model.VoidThemeManifest
import java.io.File

/**
 * Micro-service responsible for Android System Wallpaper integration,
 * Window DecorView transparency, custom image wallpaper rendering with
 * OLED readability scrim (25% / 45% / 70%), system wallpaper assignment,
 * and the expanded Appearance & Wallpaper Studio.
 */
class WallpaperMicroService(private val context: Context) {

    private val customWallpaperFile: File
        get() = File(VoidPaths.HOME, "custom_wallpaper.jpg")

    private val legacyPrefs by lazy {
        context.getSharedPreferences("void_wallpaper_prefs", Context.MODE_PRIVATE)
    }

    var customImagePath: String
        get() = legacyPrefs.getString("custom_image_path", "") ?: ""
        set(value) {
            legacyPrefs.edit().putString("custom_image_path", value).apply()
        }

    var dimScrimAlpha: Int
        get() = legacyPrefs.getInt("dim_scrim_alpha", ScrimLevel.SCRIM_45.alpha255).coerceIn(0, 220)
        set(value) {
            legacyPrefs.edit().putInt("dim_scrim_alpha", value.coerceIn(0, 220)).apply()
        }

    /** Absolute path of the owned copy written by the last successful set,
     * or null if no set has happened in this process. Config wiring (step 4)
     * copies this into LauncherConfig.customWallpaperPath. */
    var lastOwnedCopyPath: String? = null
        private set

    /** Resolves a wallpaper path argument: absolute paths as given,
     * relative paths against the Void files home (filesDir/void) —
     * never against the process working directory. */
    fun resolveWallpaperArg(path: String): File {
        val f = File(path)
        return if (f.isAbsolute) f else File(VoidRoots.filesHome(context.filesDir), path)
    }

    /**
     * Applies wallpaper state to both the Activity Window (DecorView) and root view.
     * Fixes the opaque DecorView bug where FLAG_SHOW_WALLPAPER was masked by
     * themes.xml's pitch_black windowBackground.
     */
    fun applyWallpaperState(
        activity: Activity,
        rootView: View,
        stillContainer: View?,
        config: LauncherConfig
    ) {
        val window = activity.window
        val customFile = resolveActiveCustomImage(config.customWallpaperPath)
        val scrimAlpha = config.wallpaperDimScrimAlpha.coerceIn(0, WallpaperState.MAX_SCRIM_ALPHA)

        if (config.useSystemWallpaper) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            // Critical fix: DecorView must be transparent for Android's WindowManager
            // wallpaper layer to show through behind the Activity surface.
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            if (customFile != null) {
                val layered = loadScrimmedWallpaperDrawable(customFile, scrimAlpha, WallpaperState.Fit.fromString(config.wallpaperFit))
                if (layered != null) {
                    rootView.background = layered
                } else {
                    rootView.setBackgroundColor(Color.argb(scrimAlpha, 0, 0, 0))
                }
            } else {
                // Translucent OLED scrim keeps monospace HUD & dock legible over bright wallpapers
                rootView.setBackgroundColor(Color.argb(scrimAlpha, 0, 0, 0))
            }
            stillContainer?.setBackgroundColor(Color.TRANSPARENT)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(config.colors.backgroundInt))
            rootView.setBackgroundColor(config.colors.backgroundInt)
            stillContainer?.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    /**
     * Sets an image file on disk as both the custom launcher wallpaper and
     * (optionally) the Android system wallpaper via WallpaperManager.
     */
    fun setWallpaperFromFile(
        imagePath: String,
        alsoSetSystemWallpaper: Boolean = true
    ): Boolean {
        // impl-03 step 3: own the image. The source is copied into Void
        // home (custom_wallpaper.jpg) at set time; the stored path is the
        // COPY, so deleting/moving the source can no longer silently drop
        // the wallpaper. Bounds decode validates the file first.
        val file = resolveWallpaperArg(imagePath)
        if (!file.exists() || !file.canRead()) return false
        return try {
            val bitmap = decodeSampledBitmap(file, 1440, 3200) ?: return false
            val tmp = File(VoidPaths.HOME, "${WallpaperState.OWNED_COPY_NAME}.tmp")
            file.copyTo(tmp, overwrite = true)
            val owned = customWallpaperFile
            if (!tmp.renameTo(owned)) {
                file.copyTo(owned, overwrite = true)
                try { tmp.delete() } catch (_: Exception) {}
            }
            lastOwnedCopyPath = owned.absolutePath
            customImagePath = owned.absolutePath
            if (alsoSetSystemWallpaper) {
                try {
                    WallpaperManager.getInstance(context).setBitmap(bitmap)
                } catch (_: Exception) {
                    // System WallpaperManager may be restricted on some profiles;
                    // customImagePath still renders inside VoidLauncher.
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearCustomWallpaper() {
        lastOwnedCopyPath = null
        customImagePath = ""
        if (customWallpaperFile.exists()) {
            try { customWallpaperFile.delete() } catch (_: Exception) {}
        }
    }

    fun openSystemWallpaperPicker(activity: Activity) {
        try {
            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
            activity.startActivity(Intent.createChooser(intent, "Select Wallpaper"))
        } catch (_: Exception) {
            Toast.makeText(activity, "No system wallpaper picker available", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Main Wallpaper Studio dialog: Theme Preset Switcher, Scrim Levels,
     * System Wallpaper picker, Token Customizer, Save & Share actions.
     */
    fun showWallpaperStudioDialog(
        activity: Activity,
        currentConfig: LauncherConfig,
        onUpdateConfig: (LauncherConfig) -> Unit
    ) {
        val status = if (currentConfig.useSystemWallpaper) "ENABLED" else "OFF (Pure OLED)"
        val scrimPct = (currentConfig.wallpaperDimScrimAlpha.coerceIn(0, WallpaperState.MAX_SCRIM_ALPHA) * 100 / 255)
        val activeThemeName = ThemeEngine.currentTheme.name

        val options = arrayOf(
            if (currentConfig.useSystemWallpaper) "🌑 Disable Wallpaper (Pure OLED Black)" else "🖼 Enable Wallpaper Mode",
            "🎨 Theme Preset Switcher [Active: $activeThemeName] ➔",
            "🛠️ Token Customizer (Colors, Scrim, Shader, Dock) ➔",
            "🖼 Pick System / Live Wallpaper…",
            "🌓 Scrim Dimming: Light (25%)",
            "🌘 Scrim Dimming: Balanced (45% — Default)",
            "🌑 Scrim Dimming: Deep OLED (70%)",
            "💾 Save Custom Theme…",
            "🔗 Share Current Theme…",
            "🗑 Clear Custom Image Override",
            "👁 Preview Current Wallpaper",
            "↔ Wallpaper Fit"
        )

        MaterialAlertDialogBuilder(activity)
            .setTitle("Wallpaper & Appearance Studio [$status · Scrim ${scrimPct}%]")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val next = !currentConfig.useSystemWallpaper
                        val updated = currentConfig.copy(useSystemWallpaper = next)
                        onUpdateConfig(updated)
                        Toast.makeText(
                            activity,
                            if (next) "Wallpaper enabled" else "Pure OLED mode enabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    1 -> showThemePresetSwitcher(activity, currentConfig, onUpdateConfig)
                    2 -> showTokenCustomizer(activity, currentConfig, onUpdateConfig)
                    3 -> {
                        if (!currentConfig.useSystemWallpaper) {
                            onUpdateConfig(currentConfig.copy(useSystemWallpaper = true))
                        }
                        openSystemWallpaperPicker(activity)
                    }
                    4 -> {
                        onUpdateConfig(currentConfig.copy(useSystemWallpaper = true, wallpaperDimScrimAlpha = ScrimLevel.SCRIM_25.alpha255))
                        Toast.makeText(activity, "Scrim set to 25% (Light)", Toast.LENGTH_SHORT).show()
                    }
                    5 -> {
                        onUpdateConfig(currentConfig.copy(useSystemWallpaper = true, wallpaperDimScrimAlpha = ScrimLevel.SCRIM_45.alpha255))
                        Toast.makeText(activity, "Scrim set to 45% (Balanced)", Toast.LENGTH_SHORT).show()
                    }
                    6 -> {
                        onUpdateConfig(currentConfig.copy(useSystemWallpaper = true, wallpaperDimScrimAlpha = ScrimLevel.SCRIM_70.alpha255))
                        Toast.makeText(activity, "Scrim set to 70% (Deep OLED)", Toast.LENGTH_SHORT).show()
                    }
                    7 -> showSaveCustomThemeDialog(activity, currentConfig, onUpdateConfig)
                    8 -> showShareThemeDialog(activity, currentConfig)
                    9 -> {
                        clearCustomWallpaper()
                        onUpdateConfig(currentConfig)
                        Toast.makeText(activity, "Custom image override cleared", Toast.LENGTH_SHORT).show()
                    }
                    10 -> showWallpaperPreviewDialog(activity, currentConfig)
                    11 -> showWallpaperFitPicker(activity, currentConfig, onUpdateConfig)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /** Preview dialog: current custom image (if any) + the `:wallpaper status` line.
     * Device check required: preview rendering cannot be proven by JVM tests. */
    fun showWallpaperPreviewDialog(activity: Activity, currentConfig: LauncherConfig) {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        val statusView = TextView(activity).apply {
            text = wallpaperStatus(currentConfig)
        }
        container.addView(statusView)
        val active = resolveActiveCustomImage(currentConfig.customWallpaperPath)
        if (active != null) {
            val bmp = decodeSampledBitmap(active, 1080, 2400)
            if (bmp != null) {
                container.addView(ImageView(activity).apply { setImageBitmap(bmp) })
            } else {
                container.addView(TextView(activity).apply { text = "Custom image could not be decoded: ${active.name}" })
            }
        } else {
            container.addView(TextView(activity).apply { text = "No custom image — system wallpaper or pure OLED is active." })
        }
        AlertDialog.Builder(activity)
            .setTitle("Wallpaper Preview")
            .setView(container)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showWallpaperFitPicker(
        activity: Activity,
        currentConfig: LauncherConfig,
        onUpdateConfig: (LauncherConfig) -> Unit
    ) {
        val fits = WallpaperState.Fit.values()
        val names = fits.map { if (it == WallpaperState.Fit.CENTER_CROP) "Centre-crop (fill screen)" else "Fit-centre (whole image, letterboxed)" }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle("Wallpaper Fit (current: ${currentConfig.wallpaperFit})")
            .setItems(names) { _, which ->
                onUpdateConfig(currentConfig.copy(wallpaperFit = fits[which].name))
                Toast.makeText(activity, "Wallpaper fit: ${fits[which].name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Back") { _, _ ->
                showWallpaperStudioDialog(activity, currentConfig, onUpdateConfig)
            }
            .show()
    }

    /**
     * Theme Preset Switcher dialog: displays the 5 reference themes and custom themes.
     */
    fun showThemePresetSwitcher(
        activity: Activity,
        currentConfig: LauncherConfig,
        onUpdateConfig: (LauncherConfig) -> Unit
    ) {
        val themes = ThemeEngine.getAllThemes()
        val currentId = ThemeEngine.currentTheme.id
        val labels = themes.map { t ->
            val mark = if (t.id.equals(currentId, ignoreCase = true)) "● " else "○ "
            val tag = if (t.isBuiltIn) "[PRESET]" else "[USER]"
            "$mark${t.name} $tag"
        }.toTypedArray()

        MaterialAlertDialogBuilder(activity)
            .setTitle("Select Theme Preset")
            .setItems(labels) { _, which ->
                val chosen = themes[which]
                val configManager = ConfigManager(context)
                val updated = ThemeEngine.applyTheme(chosen, currentConfig, configManager, this, activity)
                onUpdateConfig(updated)
                Toast.makeText(activity, "Applied Theme: ${chosen.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Back") { _, _ ->
                showWallpaperStudioDialog(activity, currentConfig, onUpdateConfig)
            }
            .show()
    }

    /**
     * Token Customizer: lets user tune scrim, overlay shader, dock style, and accent colors.
     */
    fun showTokenCustomizer(
        activity: Activity,
        currentConfig: LauncherConfig,
        onUpdateConfig: (LauncherConfig) -> Unit
    ) {
        val current = ThemeEngine.currentTheme
        val subOptions = arrayOf(
            "🌓 OLED Scrim Level (${current.scrimLevel.label})",
            "✨ Surface Overlay Shader (${current.overlayShader.name})",
            "🎛 Dock Style (${current.dockStyle.name})",
            "🎨 Primary Accent Color (${current.accentPrimaryHex})",
            "🌑 Surface Base Color (${current.surfacePrimaryHex})",
            "🌑 Surface Elevated Color (${current.surfaceElevatedHex})",
            "🔤 Text Primary Color (${current.textPrimaryHex})",
            "🔤 Text Secondary Color (${current.textSecondaryHex})",
            "⬛ Border Hairline Color (${current.borderHairlineHex})",
            "⚠ Accent Warning Color (${current.accentWarnHex})"
        )

        MaterialAlertDialogBuilder(activity)
            .setTitle("Theme Token Customizer")
            .setItems(subOptions) { _, which ->
                when (which) {
                    0 -> showScrimLevelPicker(activity, currentConfig, onUpdateConfig)
                    1 -> showShaderPicker(activity, currentConfig, onUpdateConfig)
                    2 -> showDockStylePicker(activity, currentConfig, onUpdateConfig)
                    3 -> showColorEditDialog(activity, "Primary Accent", current.accentPrimaryHex) { newHex ->
                        val updatedTheme = current.copy(accentPrimaryHex = newHex, isBuiltIn = false, id = ThemeEngine.CUSTOM_ID)
                        val configManager = ConfigManager(context)
                        val updated = ThemeEngine.applyTheme(updatedTheme, currentConfig, configManager, this, activity)
                        onUpdateConfig(updated)
                    }
                    4 -> showColorEditDialog(activity, "Surface Base", current.surfacePrimaryHex) { newHex ->
                        val updatedTheme = current.copy(surfacePrimaryHex = newHex, isBuiltIn = false, id = ThemeEngine.CUSTOM_ID)
                        val configManager = ConfigManager(context)
                        val updated = ThemeEngine.applyTheme(updatedTheme, currentConfig, configManager, this, activity)
                        onUpdateConfig(updated)
                    }
                    5 -> showColorEditDialog(activity, "Surface Elevated", current.surfaceElevatedHex) { newHex ->
                        val updatedTheme = current.copy(surfaceElevatedHex = newHex, isBuiltIn = false, id = ThemeEngine.CUSTOM_ID)
                        val configManager = ConfigManager(context)
                        val updated = ThemeEngine.applyTheme(updatedTheme, currentConfig, configManager, this, activity)
                        onUpdateConfig(updated)
                    }
                    6 -> showColorEditDialog(activity, "Text Primary", current.textPrimaryHex) { newHex ->
                        val updatedTheme = current.copy(textPrimaryHex = newHex, isBuiltIn = false, id = ThemeEngine.CUSTOM_ID)
                        val configManager = ConfigManager(context)
                        val updated = ThemeEngine.applyTheme(updatedTheme, currentConfig, configManager, this, activity)
                        onUpdateConfig(updated)
                    }
                    7 -> showColorEditDialog(activity, "Text Secondary", current.textSecondaryHex) { newHex ->
                        val updatedTheme = current.copy(textSecondaryHex = newHex, isBuiltIn = false, id = ThemeEngine.CUSTOM_ID)
                        val configManager = ConfigManager(context)
                        val updated = ThemeEngine.applyTheme(updatedTheme, currentConfig, configManager, this, activity)
                        onUpdateConfig(updated)
                    }
                    8 -> showColorEditDialog(activity, "Border Hairline", current.borderHairlineHex) { newHex ->
                        val updatedTheme = current.copy(borderHairlineHex = newHex, isBuiltIn = false, id = ThemeEngine.CUSTOM_ID)
                        val configManager = ConfigManager(context)
                        val updated = ThemeEngine.applyTheme(updatedTheme, currentConfig, configManager, this, activity)
                        onUpdateConfig(updated)
                    }
                    9 -> showColorEditDialog(activity, "Accent Warning", current.accentWarnHex) { newHex ->
                        val updatedTheme = current.copy(accentWarnHex = newHex, isBuiltIn = false, id = ThemeEngine.CUSTOM_ID)
                        val configManager = ConfigManager(context)
                        val updated = ThemeEngine.applyTheme(updatedTheme, currentConfig, configManager, this, activity)
                        onUpdateConfig(updated)
                    }
                }
            }
            .setNegativeButton("Back") { _, _ ->
                showWallpaperStudioDialog(activity, currentConfig, onUpdateConfig)
            }
            .show()
    }

    private fun showScrimLevelPicker(
        activity: Activity,
        currentConfig: LauncherConfig,
        onUpdateConfig: (LauncherConfig) -> Unit
    ) {
        val levels = ScrimLevel.values()
        val names = levels.map { it.label }.toTypedArray()

        MaterialAlertDialogBuilder(activity)
            .setTitle("OLED Scrim Dimming")
            .setItems(names) { _, which ->
                val chosen = levels[which]
                val current = ThemeEngine.currentTheme
                val updatedTheme = current.copy(scrimLevel = chosen, isBuiltIn = false, id = ThemeEngine.CUSTOM_ID)
                val configManager = ConfigManager(context)
                val updated = ThemeEngine.applyTheme(updatedTheme, currentConfig, configManager, this, activity)
                onUpdateConfig(updated)
            }
            .show()
    }

    private fun showShaderPicker(
        activity: Activity,
        currentConfig: LauncherConfig,
        onUpdateConfig: (LauncherConfig) -> Unit
    ) {
        val shaders = OverlayShaderType.values()
        val names = shaders.map { it.name }.toTypedArray()

        MaterialAlertDialogBuilder(activity)
            .setTitle("Select Overlay Shader")
            .setItems(names) { _, which ->
                val chosen = shaders[which]
                val current = ThemeEngine.currentTheme
                val updatedTheme = current.copy(overlayShader = chosen, isBuiltIn = false, id = ThemeEngine.CUSTOM_ID)
                val configManager = ConfigManager(context)
                val updated = ThemeEngine.applyTheme(updatedTheme, currentConfig, configManager, this, activity)
                onUpdateConfig(updated)
            }
            .show()
    }

    private fun showDockStylePicker(
        activity: Activity,
        currentConfig: LauncherConfig,
        onUpdateConfig: (LauncherConfig) -> Unit
    ) {
        val styles = DockStyle.values()
        val names = styles.map { it.name }.toTypedArray()

        MaterialAlertDialogBuilder(activity)
            .setTitle("Select Dock Style")
            .setItems(names) { _, which ->
                val chosen = styles[which]
                val current = ThemeEngine.currentTheme
                val updatedTheme = current.copy(dockStyle = chosen, isBuiltIn = false, id = ThemeEngine.CUSTOM_ID)
                val configManager = ConfigManager(context)
                val updated = ThemeEngine.applyTheme(updatedTheme, currentConfig, configManager, this, activity)
                onUpdateConfig(updated)
            }
            .show()
    }

    private fun showColorEditDialog(
        activity: Activity,
        title: String,
        currentHex: String,
        onSave: (String) -> Unit
    ) {
        val input = EditText(activity).apply {
            setText(currentHex)
            setSelection(currentHex.length)
            hint = "#RRGGBB"
        }

        AlertDialog.Builder(activity)
            .setTitle("Edit $title Color")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val hex = input.text.toString().trim()
                if (VoidThemeManifest.isValidHex(hex)) {
                    onSave(VoidThemeManifest.normalizeHex(hex, currentHex))
                } else {
                    Toast.makeText(activity, "Invalid hex color format", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Save Custom Theme Dialog.
     */
    private fun showSaveCustomThemeDialog(
        activity: Activity,
        currentConfig: LauncherConfig,
        onUpdateConfig: (LauncherConfig) -> Unit
    ) {
        val input = EditText(activity).apply {
            hint = "Theme Name (e.g. My Void Slate)"
        }

        AlertDialog.Builder(activity)
            .setTitle("Save Custom Theme")
            .setMessage("Save current tokens and shaders as a reusable .voidtheme file.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val current = ThemeEngine.currentTheme
                    val id = VoidThemeManifest.sanitizeId(name)
                    val customManifest = current.copy(
                        id = id,
                        name = name,
                        author = "User",
                        isBuiltIn = false,
                        showSystemWallpaper = currentConfig.useSystemWallpaper,
                        customWallpaperPath = currentConfig.customWallpaperPath
                    ).normalized()

                    val savedFile = ThemeEngine.saveCustomTheme(customManifest)
                    val configManager = ConfigManager(context)
                    val updated = ThemeEngine.applyTheme(customManifest, currentConfig, configManager, this, activity)
                    onUpdateConfig(updated)
                    Toast.makeText(activity, "Saved theme to ${savedFile.name}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Share Current Theme Dialog.
     */
    private fun showShareThemeDialog(activity: Activity, currentConfig: LauncherConfig) {
        val current = ThemeEngine.currentTheme
        val shareIntent = ThemeEngine.exportShareIntent(activity, current)
        try {
            activity.startActivity(Intent.createChooser(shareIntent, "Share Theme: ${current.name}"))
        } catch (_: Exception) {
            Toast.makeText(activity, "No app available to share theme", Toast.LENGTH_SHORT).show()
        }
    }

    fun isImageFile(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".webp")
    }

    /** Resolution order (WallpaperState): config path, owned copy,
     * legacy prefs path (one migration cycle). Config path is passed in
     * by step 4; until then callers pass null and the owned copy wins. */
    fun resolveActiveCustomImage(configPath: String? = null): File? {
        val configFile = if (!configPath.isNullOrBlank()) File(configPath) else null
        val resolved = WallpaperState.resolveCustomImageExists(
            configPath = configPath,
            configExists = configFile?.exists() == true && configFile.canRead(),
            ownedCopyExists = customWallpaperFile.exists() && customWallpaperFile.canRead(),
            ownedCopyPath = customWallpaperFile.absolutePath,
            legacyPath = customImagePath,
            legacyExists = customImagePath.isNotBlank() &&
                File(customImagePath).exists() && File(customImagePath).canRead()
        )
        return resolved?.let { File(it) }
    }

    private fun loadScrimmedWallpaperDrawable(file: File, scrimAlpha: Int, fit: WallpaperState.Fit): LayerDrawable? {
        val decoded = decodeSampledBitmap(file, 1080, 2400) ?: return null
        // Fit control (impl-03 step 5): FIT_CENTER letterboxes the whole
        // image; CENTER_CROP fills the layer and crops the centre.
        val fitResult = WallpaperState.fitSize(decoded.width, decoded.height, 1080, 2400, fit)
        val fitted = if (fitResult.scaledWidth != decoded.width || fitResult.scaledHeight != decoded.height) {
            Bitmap.createScaledBitmap(decoded, fitResult.scaledWidth, fitResult.scaledHeight, true)
        } else decoded
        val bmp = if (fit == WallpaperState.Fit.CENTER_CROP &&
            (fitResult.scaledWidth > 1080 || fitResult.scaledHeight > 2400)) {
            val cropped = Bitmap.createBitmap(
                fitted,
                fitResult.cropLeft.coerceIn(0, (fitResult.scaledWidth - 1080).coerceAtLeast(0)),
                fitResult.cropTop.coerceIn(0, (fitResult.scaledHeight - 2400).coerceAtLeast(0)),
                minOf(1080, fitResult.scaledWidth), minOf(2400, fitResult.scaledHeight)
            )
            if (cropped !== fitted) fitted.recycle()
            cropped
        } else fitted
        if (bmp !== decoded) decoded.recycle()
        val bmpDrawable = BitmapDrawable(context.resources, bmp).apply {
            gravity = android.view.Gravity.CENTER
        }
        val scrimDrawable = ColorDrawable(Color.argb(scrimAlpha, 0, 0, 0))
        return LayerDrawable(arrayOf(bmpDrawable, scrimDrawable))
    }

    /** Status report for `:wallpaper status` and Settings readout. */
    fun wallpaperStatus(config: LauncherConfig): String {
        val active = resolveActiveCustomImage(config.customWallpaperPath)
        return WallpaperState.formatStatus(
            useSystemWallpaper = config.useSystemWallpaper,
            customImageName = active?.name,
            scrimAlpha = config.wallpaperDimScrimAlpha,
            fit = WallpaperState.Fit.fromString(config.wallpaperFit)
        )
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sampleSize = 1
            if (bounds.outHeight > reqHeight || bounds.outWidth > reqWidth) {
                val halfH = bounds.outHeight / 2
                val halfW = bounds.outWidth / 2
                while ((halfH / sampleSize) >= reqHeight && (halfW / sampleSize) >= reqWidth) {
                    sampleSize *= 2
                }
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: Exception) {
            null
        }
    }
}
