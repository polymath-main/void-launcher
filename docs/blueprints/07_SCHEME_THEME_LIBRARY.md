# 07 — Scheme-Based Theme Library, Customizer & Share Engine

> **Target Package**: `com.polymath.voidlauncher`
> **Blueprints**: Extends `02_THEME_WALLPAPER_SURFACE.md` & integrates with `core/services/`

## 1. Universal Theme Schema (`VoidThemeSchema` v2.0 — `*.voidtheme` & `void://` URI)
Every built-in preset, user-customized theme, and imported file uses a single JSON contract (`VoidThemeManifest.kt`):
- **Metadata**: `schema_version` ("2.0"), `id`, `name`, `author`, `isBuiltIn`
- **Color Tokens**: `surfacePrimaryHex`, `surfaceElevatedHex`, `textPrimaryHex`, `textSecondaryHex`, `accentPrimaryHex`, `accentWarnHex`, `borderHairlineHex`
- **Typography & Geometry**: `headerFont`, `monoFont`, `cornerRadiusDp` (0..28), `borderWidthDp` (0..4), `touchTargetMinDp` (strictly >= 48dp), `overlayShader` (`NONE`, `DOT_MATRIX`, `CRT_SCANLINE`, `PAPER_GRAIN`, `NEON_HUD`, `CAD_BLUEPRINT_GRID`)
- **Surface & Wallpaper Hooks**: `showSystemWallpaper` (`FLAG_SHOW_WALLPAPER` + `DecorView` transparency), `scrimLevel` (`SCRIM_25` = 0.25f, `SCRIM_45` = 0.45f, `SCRIM_70` = 0.70f), `customWallpaperPath`
- **Micro-Service Hooks**: `defaultPersona` (`[VOID]`, `[STILL]`, `[MAIN]`, `[AGENT]`, `[CLIENT]`), `glyphMode` (`PULSE_SYNC`, `SILENT_FLIP`, `ACTIVE_HUD`, `OFF`), `dockStyle` (`NUMERIC_BRACKET`, `ASCII_TTY`, `EDITORIAL_SERIF`, `CHAMFERED_HUD`, `CAD_WIREFRAME`)

## 2. The 5 Core Theme Presets (`BuiltInThemes.kt`)
1. **`OBSIDIAN_VOID` (Nothing Dot-Matrix Native)**:
   - Tokens: `#000000` surface, `#0D0D0D` elevated, `#FFFFFF` text, `#FF1E1E` crimson accent, `#262626` 1dp border.
   - Surface/Shader: `showSystemWallpaper = false`, `SCRIM_70`, `DOT_MATRIX` (16dp grid, 96dp crimson touch ripple).
   - Micro-services: Default `[VOID]`/`[STILL]`, `PULSE_SYNC` Glyph on `ThermalGuardian` >= 39.0°C, `NUMERIC_BRACKET` dock (`[1] App ....`).
2. **`PHOSPHOR_TERMINAL` (Amber/Emerald VoidShell)**:
   - Tokens: `#050806` CRT surface, `#00FF66` phosphor emerald text, `#FFB000` amber thermal warning.
   - Surface/Shader: `SCRIM_70`, `CRT_SCANLINE` (1px scanline every 3px + edge vignette + ASCII box borders `+---+`).
   - Micro-services: Default `[AGENT]`, inline 6-line `TerminalSession` (`tty0`) on Cockpit Home, ASCII tree long-press menu (`├─`).
3. **`STILLNESS_EINK` (Daylight Paper / Slate Minimalist)**:
   - Tokens: `#F4F1EA` warm paper surface (or `#121212` night slate), `#1A1A1A` charcoal ink, `#525252` stone accent, `#D6D1C4` rule.
   - Surface/Shader: `SCRIM_25`, `PAPER_GRAIN` (static procedural texture, 0% GPU draw at idle, instant color-invert touch feedback, zero animations).
   - Micro-services: Default `[STILL]`/`[CLIENT]`, `SILENT_FLIP` Glyph, FocusScheduler & NotifIntelligence digest card on Scene 2.
4. **`CYBER_TELEMETRY` (Translucent Scrim & Neon HUD)**:
   - Tokens: `#0A0F1A` translucent base, `#E2F1FF` ice white text, `#00F0FF` cyber cyan accent, `#FF0055` laser magenta thermal alert.
   - Surface/Shader: `showSystemWallpaper = true`, `SCRIM_45`, `NEON_HUD` hexagonal touch mesh + 45° chamfered glass cards.
   - Micro-services: Default `[VOID]`, 270° radial arc gauges for `TelemetryMonitor` & `ThermalGuardian` (39.0°C hysteresis), split-screen Scrim preview in `WallpaperMicroService`.
5. **`TACTICAL_BLUEPRINT` (Industrial CAD Grid)**:
   - Tokens: `#0A0E17` CAD slate, `#E5E9F0` technical white, `#FF5500` international orange accent, `#1E2D47` grid lines.
   - Surface/Shader: `SCRIM_45`, `CAD_BLUEPRINT_GRID` (8dp minor / 64dp major grid with bezel rulers and orange touch crosshairs).
   - Micro-services: Default `[MAIN]`, explicit 48dp wireframe bounding boxes with `[01..07]` / `[01..99]` `BareIntegerRouter` indices, inline `MacroRecorder` timeline bar.

## 3. Customizer, Persistence, Import & Share Requirements
- **Hot-Reload Apply**: `ThemeEngine.applyTheme()` updates `StateFlow<VoidThemeManifest>`, persists to 4-tier `ConfigManager`, triggers `WallpaperMicroService` (`DecorView` transparency + `FLAG_SHOW_WALLPAPER` + `25%/45%/70%` scrim), and updates `LiquidTouchSurfaceView`.
- **Save Custom Theme**: Users can clone/modify any preset token in `WallpaperMicroService` / `VoidSettingsActivity` and save it as `<id>.voidtheme` under `getExternalFilesDir("themes")`.
- **Share & Import**:
  - Export via `FileProvider` (`*.voidtheme`) or compact `void://theme/import?payload=<base64url>` URI.
  - Import via `FileOperationsMicroService` VFS context action (`🎨 Apply & Import Theme` on `.voidtheme` files) or `CommandHelpMicroService` (`:theme list|apply|save|export|import`).

## 4. Current Repository Tree Snapshot

```
app/src/main/AndroidManifest.xml
app/src/main/assets/default_config.json
app/src/main/java/com/polymath/voidlauncher/core/AgentOrchestrator.kt
app/src/main/java/com/polymath/voidlauncher/core/AndroidClipboard.kt
app/src/main/java/com/polymath/voidlauncher/core/AppIndexer.kt
app/src/main/java/com/polymath/voidlauncher/core/AstGraphRenderer.kt
app/src/main/java/com/polymath/voidlauncher/core/BareIntegerRouter.kt
app/src/main/java/com/polymath/voidlauncher/core/CommandPalette.kt
app/src/main/java/com/polymath/voidlauncher/core/CompoundGestureEngine.kt
app/src/main/java/com/polymath/voidlauncher/core/ConfigManager.kt
app/src/main/java/com/polymath/voidlauncher/core/ConfigSyncService.kt
app/src/main/java/com/polymath/voidlauncher/core/FileManager.kt
app/src/main/java/com/polymath/voidlauncher/core/FocusScheduler.kt
app/src/main/java/com/polymath/voidlauncher/core/GlyphChoreographer.kt
app/src/main/java/com/polymath/voidlauncher/core/LegacyTermuxMigrator.kt
app/src/main/java/com/polymath/voidlauncher/core/LiveScriptEngine.kt
app/src/main/java/com/polymath/voidlauncher/core/MacroRecorder.kt
app/src/main/java/com/polymath/voidlauncher/core/NodeOsBridge.kt
app/src/main/java/com/polymath/voidlauncher/core/NothingGlyphManager.kt
app/src/main/java/com/polymath/voidlauncher/core/NotifIntelligence.kt
app/src/main/java/com/polymath/voidlauncher/core/PersonaManager.kt
app/src/main/java/com/polymath/voidlauncher/core/PowerProfileAdaptor.kt
app/src/main/java/com/polymath/voidlauncher/core/TelemetryMonitor.kt
app/src/main/java/com/polymath/voidlauncher/core/ThemeEngine.kt
app/src/main/java/com/polymath/voidlauncher/core/ThermalGuardian.kt
app/src/main/java/com/polymath/voidlauncher/core/VoidGestureDetector.kt
app/src/main/java/com/polymath/voidlauncher/core/VoidNotificationListenerService.kt
app/src/main/java/com/polymath/voidlauncher/core/VoidTermPanel.kt
app/src/main/java/com/polymath/voidlauncher/core/WidgetRegistry.kt
app/src/main/java/com/polymath/voidlauncher/model/AgentTask.kt
app/src/main/java/com/polymath/voidlauncher/model/AppEntry.kt
app/src/main/java/com/polymath/voidlauncher/model/CommandEntry.kt
app/src/main/java/com/polymath/voidlauncher/model/FileItem.kt
app/src/main/java/com/polymath/voidlauncher/model/FocusBlock.kt
app/src/main/java/com/polymath/voidlauncher/model/GlyphPattern.kt
app/src/main/java/com/polymath/voidlauncher/model/IntentStatus.kt
app/src/main/java/com/polymath/voidlauncher/model/LauncherConfig.kt
app/src/main/java/com/polymath/voidlauncher/model/NotificationItem.kt
app/src/main/java/com/polymath/voidlauncher/model/PersonaProfile.kt
app/src/main/java/com/polymath/voidlauncher/model/TelemetrySnapshot.kt
app/src/main/java/com/polymath/voidlauncher/model/WidgetDescriptor.kt
app/src/main/java/com/polymath/voidlauncher/ui/AlphabetScrubberView.kt
app/src/main/java/com/polymath/voidlauncher/ui/AppFilterAdapter.kt
app/src/main/java/com/polymath/voidlauncher/ui/DrawerAppAdapter.kt
app/src/main/java/com/polymath/voidlauncher/ui/FileBrowserAdapter.kt
app/src/main/java/com/polymath/voidlauncher/ui/LiquidTouchSurfaceView.kt
app/src/main/java/com/polymath/voidlauncher/ui/NotificationAdapter.kt
app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt
app/src/main/java/com/polymath/voidlauncher/VoidLauncherApp.kt
app/src/main/java/com/polymath/voidlauncher/VoidSettingsActivity.kt
app/src/main/res/drawable/bg_bare_slot.xml
app/src/main/res/drawable/bg_chip_filter.xml
app/src/main/res/drawable/bg_cockpit_card.xml
app/src/main/res/drawable/bg_digit_btn.xml
app/src/main/res/drawable/bg_identity_badge.xml
app/src/main/res/drawable/bg_omni_bar.xml
app/src/main/res/drawable/ic_launcher_background.xml
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/layout/activity_void_launcher.xml
app/src/main/res/layout/activity_void_settings.xml
app/src/main/res/layout/item_app_entry.xml
app/src/main/res/layout/item_drawer_app.xml
app/src/main/res/layout/item_file_entry.xml
app/src/main/res/layout/item_notification_entry.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
app/src/main/res/values/colors.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/themes.xml
```
