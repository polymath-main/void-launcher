# VoidLauncher (`void-launcher`)

> **Sovereign System Cockpit & Terminal-First Android Launcher**  
> Custom engineered for Nothing Phone (2a) [A142] · Android 16 (Bionic libc) · Dimensity 7200 Pro · Void-owned shell, no external terminal required

---

## Overview

VoidLauncher replaces conventional app grids, folder carousels, and dopamine-inducing badges with a **high-density, zero-latency system cockpit**. 

Built for edge software engineers and autonomous agent builders who treat their mobile device as a personal sovereign workstation.

## Core Features

- **100% Pitch Black AMOLED (`#000000`)**: Turns off inactive screen pixels to minimize thermals and maximize battery life during local ML/LLM runs.
- **Hardware & Telemetry HUD**: Real-time monitoring of battery %, temperature (<39°C preflight guard), RAM/swap pressure, and active NodeOS daemon ping status.
- **Hot-Reload User Customization (app config folder)**:
  - Config lives at `/sdcard/Android/data/com.polymath.voidlauncher.debug/files/config/config.json`.
  - Editable in-app via **Settings** (applies instantly on leave) or from any editor.
  - A FileObserver live-reloads the launcher whenever the file is saved.
- **Theme Engine (Appearance Studio)**:
  - Presets: `void`, `obsidian`, `midnight`, `graphite`, `matrix`, `amber` (+ `custom`).
  - Switch from **Settings → Theme Preset** or `:theme <id>`; list with `:themes`.
  - Custom schemes: override accent / secondary / surface hex values.
- **View Modes**: `COCKPIT`, `STILL`, `MINIMAL`, `FILES` — switch via Settings, `:still`/`:cockpit`/`:minimal`, or `:mode <mode>`.
  - Full control over color palette (accent, secondary, surface, border, warning, safe).
  - Configurable dock slots (up to 7 custom slots with custom labels, packages, actions, or direct Void shell commands).
  - Configurable font family and sizes.
  - Toggles for HUD cards, telemetry bar, and on-screen digit keypad.
  - Hot-reloads dynamically every time you return to the home screen.
- **Bare-Integer Fast Dock (`1`–`7`)**:
  - `[1]` Terminal (in-app VoidTerm)
  - `[2]` Filesystem browser
  - `[3]` App Drawer (all installed apps)
  - `[4]` Browser (WebAuth & links)
  - `[5]` System Settings
  - `[6]` / `[7]` User-assignable (long-press any app → **Add to dock…**)
- **Omni-Command & Shell Dispatcher (`>_`)**:
  - Type 2 letters for instant fuzzy app filtering.
  - Prefix `:` to run commands in the built-in VoidShell (`/system/bin/sh`) with real output. No external terminal app is required.
  - Direct shortcuts:
    - `:sh <cmd>`: Headless execution in VoidShell (alias for `:run`).
    - `:work <project>`: Opens the Void home project folder in the files browser.
    - `:clip`: Reads Android clipboard.
    - `:config`: Exports the current JSON config template to `/sdcard/.voidlauncher/config.json`.
    - `:reload`: Immediately reloads configuration from disk.
    - `:status` / `:hud`: Launches Polymath Core status HUD.
- **Identity Switcher**: One-tap toggle between `[VOID]` (low-level runtimes, compilers) and `[MAIN]` (production SaaS, platforms).
- **Nothing Phone (2a) Glyph Ready**: Extensible hooks to pulse rear Glyph LEDs on agent build completion or thermal threshold warnings.

## Configuration & Customization

Create or edit your config file from any editor:

```bash
# The launcher's app-owned config folder (no storage permission needed to read it
# from the app).
CFG=/sdcard/Android/data/com.polymath.voidlauncher.debug/files/config
ls "$CFG"
nano "$CFG/config.json"     # save → app hot-reloads immediately
```

> **Note:** the launcher never reads another app's private sandbox.
> Config, macros, widgets, personas and glyph patterns all live under
> the app's `files/config/` folder.

Or type `:config` in the launcher's omni-bar to export the full configuration template.

Example `config.json`:

```json
{
  "colors": {
    "background": "#000000",
    "surface": "#080808",
    "border": "#1A1A1A",
    "accent": "#E11D48",
    "secondary": "#06B6D4",
    "safe": "#10B981",
    "warning": "#F59E0B"
  },
  "font_family": "monospace",
  "hud_font_size_sp": 11.0,
  "dock_font_size_sp": 12.5,
  "show_telemetry_bar": true,
  "show_nodeos_card": true,
  "show_intent_card": true,
  "show_digit_keypad": true,
  "thermal_limit_celsius": 39.0,
  "dock_slots": [
    { "index": 1, "label": "TERMINAL", "subtitle": "VoidTerm", "package_name": "", "action": "open_voidterm" },
    { "index": 2, "label": "FILES", "subtitle": "Browse Projects", "action": "open_files" },
    { "index": 3, "label": "DRAWER", "subtitle": "All Installed Apps", "action": "open_drawer" },
    { "index": 4, "label": "BROWSER", "subtitle": "WebAuth & Links", "action": "open_browser" },
    { "index": 5, "label": "SETTINGS", "subtitle": "Void Customizer", "action": "open_settings" },
    { "index": 6, "label": "COMMS", "subtitle": "Dialer", "action": "open_dialer" },
    { "index": 7, "label": "HUD", "subtitle": "Void status", "action": "run_void_command", "package_name": "echo void-status" }
  ]
}
```

## Tech Stack

- **Platform**: Android 16 (API 35/36), Kotlin 1.9, Java 17/21
- **Architecture**: Low-overhead ViewBinding, Coroutines Flow, Zero Animation Lag
- **Shell**: Void-owned VoidShell (`/system/bin/sh`) + in-app VoidTerm; SharedPreferences for identity state
- **CI/CD**: GitHub Actions automated APK compilation (`.github/workflows/build.yml`)

## Build & Test

```bash
# Run local preflight and QA analyzer
./scripts/build_apk.sh

# Build debug APK (via Gradle wrapper or GitHub Actions CI)
./gradlew assembleDebug
```
