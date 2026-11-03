# Blueprint 02 — Theme Engine, Wallpaper Studio & Surface Rendering

## 1. Domain Scope
Controls the visual presentation layer: 6 built-in OLED/dark color palettes + custom hex overrides, Window `DecorView` wallpaper transparency, custom image wallpaper rendering with adjustable OLED readability scrim, and interactive liquid touch feedback.

## 2. Micro-Service & Module Inventory

| Module | File | Responsibility |
|---|---|---|
| `WallpaperMicroService` | [`WallpaperMicroService.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/services/WallpaperMicroService.kt) | Manages `FLAG_SHOW_WALLPAPER`, transparent `window.setBackgroundDrawable` (`DecorView`), custom image wallpaper decoding (`BitmapFactory` sampled), OLED readability scrim (`dimScrimAlpha`), `WallpaperManager` system assignment, and the Wallpaper Studio dialog. |
| `ThemeEngine` | [`ThemeEngine.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/ThemeEngine.kt) | Provides 6 presets (`void`, `obsidian`, `midnight`, `graphite`, `matrix`, `amber`) and `custom` palette resolution. |
| `LiquidTouchSurfaceView` | [`LiquidTouchSurfaceView.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/ui/LiquidTouchSurfaceView.kt) | Renders expanding radial touch ripples on the root canvas using the active theme's accent color. |

## 3. Functional Wallpaper Pipeline

1. **System Wallpaper Mode (`use_system_wallpaper = true`, no custom image override)**:
   - Sets `WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER`.
   - Sets `window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))` so Android's `DecorView` does not paint opaque `@color/pitch_black` over the system wallpaper surface.
   - Applies a translucent dark scrim (`Color.argb(dimScrimAlpha, 0, 0, 0)`) to `rootLayout` so monospace HUD and dock text remain high-contrast over bright wallpapers.
2. **Custom Image Wallpaper Mode (`setWallpaperFromFile(path)`)**:
   - Downsamples the target image via `BitmapFactory.Options.inSampleSize` (`1080×2400` bound) to prevent OOM.
   - Wraps the `BitmapDrawable` and `ColorDrawable` scrim inside a `LayerDrawable` on `rootLayout`, and syncs with Android's `WallpaperManager`.
3. **Pure OLED Mode (`use_system_wallpaper = false`)**:
   - Clears `FLAG_SHOW_WALLPAPER` and paints `config.colors.backgroundInt` (`#000000` on `void` / `obsidian`).

## 4. User Entry Points
- **Settings**: `Show System Wallpaper` switch + `WALLPAPER STUDIO (PICK / SCRIM) ➔` button.
- **Files Browser**: Tap any `.jpg`, `.jpeg`, `.png`, or `.webp` file → `🖼 Set as Wallpaper`.
- **Omni-Bar**: `:wallpaper` (or `:wp`), `:wallpaper on`, `:wallpaper off`, `:wallpaper pick`, `:wallpaper set <path>`, `:wallpaper clear`.
- **Identity Badge Long-Press**: `🖼 Wallpaper Studio`.

## Impl-03 update (scheme theme, merged)
- Built-in themes are now the 5 scheme presets in `core/BuiltInThemes.kt`
  (OBSIDIAN_VOID, PHOSPHOR_TERMINAL, STILLNESS_EINK, CYBER_TELEMETRY,
  TACTICAL_BLUEPRINT); the old 6 inline presets are superseded.
- Scrim (default 115 / SCRIM_45), custom wallpaper path, and fit
  (CENTER_CROP / FIT_CENTER) live in `LauncherConfig` and round-trip
  through export/import/reset. A one-time migration copies them from
  `SharedPreferences "void_wallpaper_prefs"` on first load, then clears
  those prefs keys (copy-first: config is written before prefs are cleared).
- Custom wallpapers are owned copies: `setWallpaperFromFile` copies the
  source into Void home as `custom_wallpaper.jpg`; the stored path is the
  copy, so deleting the source no longer drops the wallpaper. Relative
  `:wallpaper set` paths resolve against the Void files home, not the
  process working directory.
- STILL follows the preset at runtime: `applyConfig` sets clock/date/status
  colours from `config.colors` and favorites are built from `config.colors`.
  XML `still_*` colours are only pre-applyConfig defaults.
- Type scale decision (recorded, not silently changed): COCKPIT sizes stay
  at the config floats 11 / 12.5 / 12.5, applied in the single
  `applyTypography()` (+ dock size in `rebuildDockSlots()`). The spec
  12/14/16/20 scale is NOT adopted unilaterally; it needs a product
  decision, and a Settings UI for font sizes is area 07 follow-up.
- Card radius: each scheme preset carries its own `cornerRadiusDp`
  (14/4/2/10/0); `ThemeEngine.applyTheme` sets `cockpitCardRadiusDp` from
  the theme. Spec radius 12 is superseded by scheme presets.
- Elevations removed from the drawer and command centre per the spec's
  "no shadows" rule; both surfaces are opaque, so separation is by colour.
- `:wallpaper status` reports mode, custom image, scrim %, and fit. The
  Studio dialog gained a preview dialog and a fit picker; the token
  customizer now covers all 7 schema colour tokens, and any token edit
  marks the theme id `custom`.
- VoidSim parsers (`voidsim.py`, `build_ui_v2.py`) read
  `BuiltInThemes.ALL` (5 presets); the old `ThemeEngine` inline-preset
  parser is retired (it parsed only 1 preset after the merge).
