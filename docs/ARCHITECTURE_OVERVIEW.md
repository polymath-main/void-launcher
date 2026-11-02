# VoidLauncher — Complete Architecture Overview

> **Project**: `void-launcher` · `com.polymath.voidlauncher` · v2.0 / v3 UI
> **Positioning**: Sovereign system cockpit + calm regular-user STILL home + VoidShell Android HOME replacement
> **Target**: Nothing Phone (2a) [A142] · Android 16 (API 35/36) · Dimensity 7200 Pro · Self-contained VoidShell
> **Repo**: [`polymath-main/void-launcher`](https://github.com/polymath-main/void-launcher)
> **Design system**: STILL & COCKPIT v2 (Obsidian OLED, 6 Theme Presets, borderless 48dp touch targets)

---

## 1. Stack & Build Profile

| Layer | Choice |
|---|---|
| Language / VM | Kotlin · JVM target 17 · minSdk 26 · target/compileSdk 35 |
| UI | ViewBinding + XML · RecyclerView adapters · custom Views (`AlphabetScrubberView`, `LiquidTouchSurfaceView`) |
| Async | Coroutines · `Flow` / `StateFlow` · lifecycle-ktx |
| Persistence | Plain SQLite (`SQLiteOpenHelper`), SharedPreferences (isolated files), JSON in Void-owned storage (`VoidPaths.HOME`) |
| Shell & Process Engine | In-app `VoidShell` (`/system/bin/sh`), `VoidShim` (built-in commands: `pmc`, `nodeos`, `void`, `help`), `VoidProcessManager`, `VoidTaskStore` |
| CI | `.github/workflows/build.yml`: JDK 17 → `testDebugUnitTest` → `assembleDebug` → APK artifact |

---

## 2. Spatial Model — the STILL & COCKPIT Topology

```
        [ Command Center  >_ ]  ← swipe up / bottom edge / :cmd
                 ▲
 [Widget Deck] ← STILL / COCKPIT / MINIMAL → [App Library drawer + A-Z scrubber]
  (swipe right)                               (swipe left / right edge handle ‹)
```

- **Home modes**:
  - `STILL` (Calm regular-user mode): 20sp letter-spaced clock, 14sp date, 12sp vitals, borderless 48dp favorite rows for Slots 1, 3, 5, and bottom Omni-Bar.
  - `COCKPIT` (Power-user mode): Telemetry HUD line, borderless intent & notification lines, borderless 48dp `[1..7]` dock, collapsible `[123]` digit keypad, and Omni-Bar.
  - `MINIMAL`: Distraction-free Omni-Bar + Dock only.
  - `FILES`: Built-in VFS Explorer with quick chips (`Void`, `Projects`, `Downloads`, `sdcard`).
- **Interactive Customization for Regular Users**:
  - **Long-press any App** (in Drawer, Omni search, or Command Center): Open, App Info, Uninstall, Add to Dock `[1..7]`, or Pin Home Shortcut.
  - **Long-press any Dock Slot `[1..7]` or STILL Favorite**: Assign Installed App, Assign Quick Action (Drawer, Files, Terminal, Browser, Phone, Settings), Rename Slot, or Reset.
  - **Tap / Long-press Identity Badge (`[VOID]`)**: Tap cycles personas; long-press opens the interactive Workspace Identity & Mode switcher.
  - **Settings (`VoidSettingsActivity`)**: Grouped into `APPEARANCE`, `HOME` (with interactive Dock Slot Customizer `[1..7]`), `SYSTEM` (Default Launcher role, Storage access, Notification listener, 16 Gesture actions, Thermal & Glyph controls), and `DATA`.

---

## 3. Subsystem Catalog (`core/`)

| Module | Responsibility | Status |
|---|---|---|
| `VoidShell` / `VoidShim` | Self-contained shell execution (`/system/bin/sh`) + built-in `pmc`, `nodeos`, `void`, `help` shims | ✅ Real |
| `VoidProcessManager` / `VoidTaskStore` | Background process tracking, cancellation, and persistent JSON task state | ✅ Real |
| `ThemeEngine` | 6 built-in themes (`void`, `obsidian`, `midnight`, `graphite`, `matrix`, `amber`) + `custom` | ✅ Real |
| `ConfigManager` / `ConfigSyncService` | 4-tier config (`VoidPaths.CONFIG` → app `filesDir` → `assets/default_config.json` → Kotlin defaults) + live `FileObserver` hot-reload | ✅ Real |
| `TelemetryMonitor` / `PowerProfileAdaptor` | Battery %, tenths-°C temperature, RAM usage, adaptive polling (`PERFORMANCE`/`BALANCED`/`ECO`/`CRITICAL`) | ✅ Real |
| `ThermalGuardian` | 39.0 °C ceiling + 2.0 °C hysteresis; executes kill-list via `ShellPort`; triggers `THERMAL_WARNING` glyph | ✅ Real |
| `AppIndexer` / `BareIntegerRouter` | Installed launcher-intent indexing, A–Z section map, fuzzy search, `[1..7]` action routing | ✅ Real |
| `AgentOrchestrator` | Async task execution via `ShellPort` + `VoidProcessManager`, persistent `void_tasks.json`, non-clobbering `workflow.json` merge, completion callbacks | ✅ Real |
| `WidgetRegistry` | Pluggable `*.widget.json` cards: `SCRIPT` (live `VoidShell`), `HTTP` (`HttpURLConnection`), and `STATIC` | ✅ Real |
| `GlyphChoreographer` / `NothingGlyphManager` | Reflective Nothing GDK (`com.nothing.ketchum.GlyphManager`) channel sequence engine with haptic waveform fallback | ✅ Real |
| `FocusScheduler` | Time-window + `days_of_week` schedule evaluation with automatic persona & glyph transitions | ✅ Real |
| `NodeOsBridge` / `AstGraphRenderer` | Opt-in NodeOS `workflow.json` + background-cached SQLite `agy_nodeos.db` AST node/edge topology | ✅ Real |
| `CommandPalette` / `MacroRecorder` / `LiveScriptEngine` | SQLite history, `:snippet` + inline `## alias` snippets, macro recording/replay, RPN `:eval` engine | ✅ Real |
