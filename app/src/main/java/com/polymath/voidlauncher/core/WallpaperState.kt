package com.polymath.voidlauncher.core

import kotlin.math.max
import kotlin.math.min

/**
 * Pure (JVM-testable, no Android imports) wallpaper state logic for
 * impl-03. Android-bound code (WallpaperMicroService, ConfigManager)
 * consumes these functions; it does not re-implement their rules.
 *
 * Single default, decided in impl-03: scrim 115 (SCRIM_45, "Balanced").
 * The plan's pre-merge stored default of 110 is superseded by the merged
 * theme, which already defaults to SCRIM_45 and labels it Default.
 */
object WallpaperState {

    const val OWNED_COPY_NAME = "custom_wallpaper.jpg"
    const val DEFAULT_SCRIM_ALPHA = 115
    const val MAX_SCRIM_ALPHA = 220

    /** How a custom image is fitted into the 1080x2400 display layer. */
    enum class Fit { CENTER_CROP, FIT_CENTER;

        companion object {
            fun fromString(raw: String?): Fit = when (raw?.trim()?.uppercase()) {
                "FIT_CENTER", "FIT", "FITCENTRE", "FIT_CENTRE" -> Fit.FIT_CENTER
                else -> Fit.CENTER_CROP
            }
        }
    }

    /**
     * Resolution order for the active custom image.
     * 1. Config path, if the file exists and is readable.
     * 2. The owned copy in Void home, if it exists and is readable.
     * 3. The legacy prefs path (one migration cycle only), if it exists
     *    and is readable — callers should copy it to the owned copy
     *    and stop using it after this returns it.
     * Anything else -> null (system wallpaper / pure OLED state).
     */
    fun resolveCustomImageExists(
        configPath: String?,
        configExists: Boolean,
        ownedCopyExists: Boolean,
        ownedCopyPath: String,
        legacyPath: String?,
        legacyExists: Boolean
    ): String? {
        if (!configPath.isNullOrBlank() && configExists) return configPath
        if (ownedCopyExists) return ownedCopyPath
        if (!legacyPath.isNullOrBlank() && legacyExists) return legacyPath
        return null
    }

    /**
     * Prefs -> config migration. Returns (scrimAlpha, customPath, migrated).
     * Config wins whenever it is present (non-null); prefs are used only
     * when the config field is absent. Copy-first contract: the caller
     * writes the returned config, verifies, and only then clears prefs.
     */
    fun migrateFromPrefs(
        configScrim: Int?,
        configPath: String?,
        prefsScrim: Int?,
        prefsPath: String?
    ): Triple<Int, String, Boolean> {
        val scrim = configScrim ?: prefsScrim?.coerceIn(0, MAX_SCRIM_ALPHA) ?: DEFAULT_SCRIM_ALPHA
        val path = configPath ?: prefsPath.orEmpty()
        val migrated = configScrim == null || configPath == null
        return Triple(scrim, path, migrated)
    }

    /** Scale a source bitmap into a destination box, preserving aspect. */
    data class FitResult(val scaledWidth: Int, val scaledHeight: Int, val cropLeft: Int, val cropTop: Int)

    fun fitSize(srcW: Int, srcH: Int, dstW: Int, dstH: Int, fit: Fit): FitResult {
        require(srcW > 0 && srcH > 0 && dstW > 0 && dstH > 0) { "dimensions must be positive" }
        return when (fit) {
            Fit.FIT_CENTER -> {
                val scale = min(dstW.toDouble() / srcW, dstH.toDouble() / srcH)
                FitResult(
                    (srcW * scale).toInt().coerceAtLeast(1),
                    (srcH * scale).toInt().coerceAtLeast(1),
                    0, 0
                )
            }
            Fit.CENTER_CROP -> {
                val scale = max(dstW.toDouble() / srcW, dstH.toDouble() / srcH)
                val sw = (srcW * scale).toInt().coerceAtLeast(1)
                val sh = (srcH * scale).toInt().coerceAtLeast(1)
                FitResult(sw, sh, (sw - dstW).coerceAtLeast(0) / 2, (sh - dstH).coerceAtLeast(0) / 2)
            }
        }
    }

    /** One-line status report for `:wallpaper status` and Settings readout. */
    fun formatStatus(
        useSystemWallpaper: Boolean,
        customImageName: String?,
        scrimAlpha: Int,
        fit: Fit
    ): String {
        val mode = when {
            !useSystemWallpaper -> "Pure OLED"
            customImageName != null -> "Custom image"
            else -> "System wallpaper"
        }
        val image = customImageName ?: "none"
        val pct = (scrimAlpha.coerceIn(0, MAX_SCRIM_ALPHA) * 100 / 255)
        return "[WALLPAPER] mode=$mode image=$image scrim=${pct}% fit=${fit.name}"
    }
}

/** Ripple coalescing: ignore an ACTION_DOWN within [minIntervalMs] of the
 * previous ripple at a near-identical point (within [maxDistancePx]). */
object RippleCoalescer {
    fun shouldSpawn(
        nowMs: Long,
        x: Float,
        y: Float,
        lastTimeMs: Long?,
        lastX: Float?,
        lastY: Float?,
        minIntervalMs: Long = 80L,
        maxDistancePx: Float = 24f
    ): Boolean {
        if (lastTimeMs == null || lastX == null || lastY == null) return true
        if (nowMs - lastTimeMs < minIntervalMs) {
            val dx = x - lastX
            val dy = y - lastY
            if (dx * dx + dy * dy <= maxDistancePx * maxDistancePx) return false
        }
        return true
    }
}
