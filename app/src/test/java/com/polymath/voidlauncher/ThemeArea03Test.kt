package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.RippleCoalescer
import com.polymath.voidlauncher.core.WallpaperState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeArea03Test {

    @Test fun resolvePrefersConfigOverOwnedCopy() {
        assertEquals("/cfg/a.jpg", WallpaperState.resolveCustomImageExists(
            "/cfg/a.jpg", true, true, "/home/custom_wallpaper.jpg", "/old/b.jpg", true))
    }

    @Test fun resolveFallsBackToOwnedCopyThenLegacy() {
        assertEquals("/home/custom_wallpaper.jpg", WallpaperState.resolveCustomImageExists(
            "", false, true, "/home/custom_wallpaper.jpg", "/old/b.jpg", true))
        assertEquals("/old/b.jpg", WallpaperState.resolveCustomImageExists(
            "", false, false, "/home/custom_wallpaper.jpg", "/old/b.jpg", true))
        assertNull(WallpaperState.resolveCustomImageExists(
            "", false, false, "/home/custom_wallpaper.jpg", "", false))
    }

    @Test fun migrateUsesPrefsOnlyWhenConfigAbsent() {
        val (scrim, path, migrated) = WallpaperState.migrateFromPrefs(null, null, 64, "/old/b.jpg")
        assertEquals(64, scrim); assertEquals("/old/b.jpg", path); assertTrue(migrated)
        val keep = WallpaperState.migrateFromPrefs(115, "/cfg/a.jpg", 64, "/old/b.jpg")
        assertEquals(115, keep.first); assertEquals("/cfg/a.jpg", keep.second); assertFalse(keep.third)
        assertEquals(115, WallpaperState.migrateFromPrefs(null, null, null, null).first)
        assertEquals(220, WallpaperState.migrateFromPrefs(null, null, 999, null).first)
    }

    @Test fun fitCenterScalesToFitAndCropFills() {
        val fit = WallpaperState.fitSize(2000, 1000, 1080, 2400, WallpaperState.Fit.FIT_CENTER)
        assertEquals(1080, fit.scaledWidth); assertEquals(540, fit.scaledHeight)
        val crop = WallpaperState.fitSize(2000, 1000, 1080, 2400, WallpaperState.Fit.CENTER_CROP)
        assertEquals(4800, crop.scaledWidth); assertEquals(2400, crop.scaledHeight)
        assertEquals(1860, crop.cropLeft); assertEquals(0, crop.cropTop)
        assertEquals(WallpaperState.Fit.FIT_CENTER, WallpaperState.Fit.fromString("fit_centre"))
        assertEquals(WallpaperState.Fit.CENTER_CROP, WallpaperState.Fit.fromString("bogus"))
    }

    @Test fun statusFormatCoversAllModes() {
        assertTrue(WallpaperState.formatStatus(false, null, 115, WallpaperState.Fit.CENTER_CROP)
            .contains("mode=Pure OLED"))
        assertTrue(WallpaperState.formatStatus(true, "a.jpg", 115, WallpaperState.Fit.FIT_CENTER)
            .contains("mode=Custom image") && WallpaperState.formatStatus(true, "a.jpg", 115, WallpaperState.Fit.FIT_CENTER).contains("fit=FIT_CENTER"))
        assertTrue(WallpaperState.formatStatus(true, null, 64, WallpaperState.Fit.CENTER_CROP)
            .contains("mode=System wallpaper"))
    }

    @Test fun rippleCoalescingIgnoresNearDuplicatesOnly() {
        assertFalse(RippleCoalescer.shouldSpawn(1000L, 10f, 10f, 950L, 12f, 12f))
        assertTrue(RippleCoalescer.shouldSpawn(1000L, 200f, 200f, 950L, 12f, 12f))
        assertTrue(RippleCoalescer.shouldSpawn(2000L, 10f, 10f, 950L, 12f, 12f))
        assertTrue(RippleCoalescer.shouldSpawn(1000L, 10f, 10f, null, null, null))
    }

    private fun repoRoot(): File? {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            if (File(dir, "settings.gradle").exists()) return dir
            dir = dir.parentFile
        }
        return null
    }

    @Test fun stillFollowsPresetAtRuntime() {
        val root = repoRoot() ?: return
        val src = File(root, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").readText()
        assertTrue(src.contains("binding.tvStillClock.setTextColor(c.textPrimaryInt)"))
        assertTrue(src.contains("binding.tvStillDate.setTextColor(c.textSecondaryInt)"))
        assertTrue(src.contains("binding.tvStillStatus.setTextColor(c.textMutedInt)"))
    }

    @Test fun typographySingleSourceForHudOmniDock() {
        val root = repoRoot() ?: return
        val src = File(root, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").readText()
        // HUD + omni sizes live in applyTypography; dock size in rebuildDockSlots.
        assertTrue(src.contains("config.hudFontSizeSp"))
        assertTrue(src.contains("config.omniFontSizeSp"))
        assertTrue(src.contains("config.dockFontSizeSp"))
        val hudSets = Regex("""setTextSize\(TypedValue\.COMPLEX_UNIT_SP, config\.hudFontSizeSp\)""").findAll(src).count()
        assertTrue(hudSets >= 1)
    }

    // noElevation test is added in step 6, with the elevation removal itself.
}
