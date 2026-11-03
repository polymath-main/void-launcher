# VoidLauncher — Master Architecture & Micro-Services Overview

> **Project**: `void-launcher` · `com.polymath.voidlauncher` · v3.0.0
> **Positioning**: Sovereign system cockpit + calm regular-user STILL home + VoidShell Android HOME replacement
> **Target**: Nothing Phone (2a) [A142] · Android 16 (API 35/36) · Dimensity 7200 Pro · Self-contained VoidShell
> **Design System**: STILL & COCKPIT v2 (Obsidian OLED, 6 Theme Presets, Functional Wallpaper Studio, 48dp targets)

---

## 1. The 6-Document Functional Blueprint Suite

Each core domain and feature cluster is documented in its own modular blueprint under [`docs/blueprints/`](docs/blueprints):

1. **[`01_CORE_RUNTIME_SHELL.md`](docs/blueprints/01_CORE_RUNTIME_SHELL.md)** — `VoidShell` (concurrent stream draining, run records, started-process handles), `VoidProcessManager` (real cancellation by handle), `VoidRoots` (three-root path vocabulary), `TerminalSession`/`VoidTermPanel` (single buffer, minimal ANSI, named sessions), and 4-tier `ConfigManager` (schema_version, atomic saves, debounced hot-reload).
2. **[`02_THEME_WALLPAPER_SURFACE.md`](docs/blueprints/02_THEME_WALLPAPER_SURFACE.md)** — `WallpaperMicroService` (Window `DecorView` transparency, `FLAG_SHOW_WALLPAPER`, custom image wallpaper with OLED readability scrim, `WallpaperManager` integration), `ThemeEngine`, and `LiquidTouchSurfaceView`.
3. **[`03_NAVIGATION_DOCK_DRAWER.md`](docs/blueprints/03_NAVIGATION_DOCK_DRAWER.md)** — `DockSlotMicroService`, `AppActionsMicroService`, `FileOperationsMicroService`, `AppIndexer`, `BareIntegerRouter`, and `CompoundGestureEngine`.
4. **[`04_OMNI_COMMAND_SCRIPTING.md`](docs/blueprints/04_OMNI_COMMAND_SCRIPTING.md)** — `CommandHelpMicroService`, `CommandPalette` (SQLite + `## <alias>` inline snippets), `LiveScriptEngine` (`:eval`), and `MacroRecorder`.
5. **[`05_TELEMETRY_THERMAL_GLYPH.md`](docs/blueprints/05_TELEMETRY_THERMAL_GLYPH.md)** — `TelemetryMonitor`, `PowerProfileAdaptor`, `ThermalGuardian` (39.0 °C hysteresis), `GlyphChoreographer` (Nothing GDK reflection), and `NothingGlyphManager`.
6. **[`06_AGENT_WIDGETS_PERSONA.md`](docs/blueprints/06_AGENT_WIDGETS_PERSONA.md)** — `PersonaIdentityMicroService`, `AgentOrchestrator`, `WidgetRegistry` (`SCRIPT`, `HTTP`, `STATIC`), `PersonaManager`, `FocusScheduler`, `NodeOsBridge`, and `NotifIntelligence`.

---

## 2. Modular Micro-Services Layer (`core/services/`)

To keep [`VoidLauncherActivity.kt`](app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt) and [`VoidSettingsActivity.kt`](app/src/main/java/com/polymath/voidlauncher/VoidSettingsActivity.kt) clean and maintainable, feature workflows are decomposed into 6 focused micro-services under [`app/src/main/java/com/polymath/voidlauncher/core/services/`](app/src/main/java/com/polymath/voidlauncher/core/services):

| Micro-Service | File | Lines | Responsibility |
|---|---|---|---|
| `WallpaperMicroService` | [`WallpaperMicroService.kt`](app/src/main/java/com/polymath/voidlauncher/core/services/WallpaperMicroService.kt) | ~185 | System wallpaper flag + `DecorView` transparency fix, custom image wallpaper decoding, adjustable OLED scrim dimming (`25%` / `45%` / `70%`), `WallpaperManager` assignment, and Wallpaper Studio dialog. |
| `DockSlotMicroService` | [`DockSlotMicroService.kt`](app/src/main/java/com/polymath/voidlauncher/core/services/DockSlotMicroService.kt) | ~180 | Shared Home + Settings interactive Dock Slot `[1..7]` editor (assign installed app, assign built-in quick action, rename label, reset slot). |
| `AppActionsMicroService` | [`AppActionsMicroService.kt`](app/src/main/java/com/polymath/voidlauncher/core/services/AppActionsMicroService.kt) | ~115 | App launch, App Info settings intent, Uninstall intent, Pin Home Shortcut (`ShortcutManager`), and 5-action long-press menu. |
| `PersonaIdentityMicroService` | [`PersonaIdentityMicroService.kt`](app/src/main/java/com/polymath/voidlauncher/core/services/PersonaIdentityMicroService.kt) | ~95 | Identity badge rendering (`[VOID]`, `[STILL]`, `[MAIN]`, `[AGENT]`, `[CLIENT]`), persona cycling, and interactive Workspace Identity & Mode dialog. |
| `FileOperationsMicroService` | [`FileOperationsMicroService.kt`](app/src/main/java/com/polymath/voidlauncher/core/services/FileOperationsMicroService.kt) | ~115 | VFS context actions: `🖼 Set as Wallpaper` for image files, Open in VoidTerm, Text Preview, Copy Path, Rename, Delete, and Create Folder. |
| `CommandHelpMicroService` | [`CommandHelpMicroService.kt`](app/src/main/java/com/polymath/voidlauncher/core/services/CommandHelpMicroService.kt) | ~100 | Inline `## <alias>` snippet extraction, saved snippet alias resolution, `:help` guide formatting, and `:wallpaper` / `:wp` command handling. |
