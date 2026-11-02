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
