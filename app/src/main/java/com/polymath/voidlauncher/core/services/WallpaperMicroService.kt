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
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.voidlauncher.model.LauncherConfig
import com.polymath.voidlauncher.model.VoidPaths
import java.io.File

/**
 * Micro-service responsible for Android System Wallpaper integration,
 * Window DecorView transparency, custom image wallpaper rendering with
 * OLED readability scrim, and system wallpaper assignment.
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
        get() = legacyPrefs.getInt("dim_scrim_alpha", 110).coerceIn(0, 220)
        set(value) {
            legacyPrefs.edit().putInt("dim_scrim_alpha", value.coerceIn(0, 220)).apply()
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
        val customFile = resolveActiveCustomImage()

        if (config.useSystemWallpaper) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            // Critical fix: DecorView must be transparent for Android's WindowManager
            // wallpaper layer to show through behind the Activity surface.
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            if (customFile != null) {
                val layered = loadScrimmedWallpaperDrawable(customFile)
                if (layered != null) {
                    rootView.background = layered
                } else {
                    rootView.setBackgroundColor(Color.argb(dimScrimAlpha, 0, 0, 0))
                }
            } else {
                // Translucent OLED scrim keeps monospace HUD & dock legible over bright wallpapers
                rootView.setBackgroundColor(Color.argb(dimScrimAlpha, 0, 0, 0))
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
        val file = File(imagePath)
        if (!file.exists() || !file.canRead()) return false
        return try {
            val bitmap = decodeSampledBitmap(file, 1440, 3200) ?: return false
            customImagePath = file.absolutePath
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

    fun showWallpaperStudioDialog(
        activity: Activity,
        currentConfig: LauncherConfig,
        onUpdateConfig: (LauncherConfig) -> Unit
    ) {
        val status = if (currentConfig.useSystemWallpaper) "ENABLED" else "OFF (Pure OLED)"
        val scrimPct = (dimScrimAlpha * 100 / 255)
        val options = arrayOf(
            if (currentConfig.useSystemWallpaper) "🌑 Disable Wallpaper (Pure OLED Black)" else "🖼 Enable Wallpaper Mode",
            "🎨 Pick System / Live Wallpaper…",
            "🌓 Scrim Dimming: Light (25%)",
            "🌘 Scrim Dimming: Balanced (45% — Default)",
            "🌑 Scrim Dimming: Deep OLED (70%)",
            "🗑 Clear Custom Image Override"
        )

        MaterialAlertDialogBuilder(activity)
            .setTitle("Wallpaper Studio [$status · Scrim ${scrimPct}%]")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val next = !currentConfig.useSystemWallpaper
                        onUpdateConfig(currentConfig.copy(useSystemWallpaper = next))
                        Toast.makeText(
                            activity,
                            if (next) "Wallpaper enabled" else "Pure OLED mode enabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    1 -> {
                        if (!currentConfig.useSystemWallpaper) {
                            onUpdateConfig(currentConfig.copy(useSystemWallpaper = true))
                        }
                        openSystemWallpaperPicker(activity)
                    }
                    2 -> {
                        dimScrimAlpha = 64
                        onUpdateConfig(currentConfig.copy(useSystemWallpaper = true))
                    }
                    3 -> {
                        dimScrimAlpha = 115
                        onUpdateConfig(currentConfig.copy(useSystemWallpaper = true))
                    }
                    4 -> {
                        dimScrimAlpha = 178
                        onUpdateConfig(currentConfig.copy(useSystemWallpaper = true))
                    }
                    5 -> {
                        clearCustomWallpaper()
                        onUpdateConfig(currentConfig)
                        Toast.makeText(activity, "Custom image override cleared", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    fun isImageFile(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".webp")
    }

    private fun resolveActiveCustomImage(): File? {
        if (customImagePath.isNotBlank()) {
            val f = File(customImagePath)
            if (f.exists() && f.canRead()) return f
        }
        if (customWallpaperFile.exists() && customWallpaperFile.canRead()) {
            return customWallpaperFile
        }
        return null
    }

    private fun loadScrimmedWallpaperDrawable(file: File): LayerDrawable? {
        val bmp = decodeSampledBitmap(file, 1080, 2400) ?: return null
        val bmpDrawable = BitmapDrawable(context.resources, bmp).apply {
            gravity = android.view.Gravity.CENTER
        }
        val scrimDrawable = ColorDrawable(Color.argb(dimScrimAlpha, 0, 0, 0))
        return LayerDrawable(arrayOf(bmpDrawable, scrimDrawable))
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
