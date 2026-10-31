# VoidLauncher (`void-launcher`)

> **Sovereign System Cockpit & Terminal-First Android Launcher**  
> Custom engineered for Nothing Phone (2a) [A142] · Android 16 (Bionic libc) · Dimensity 7200 Pro · Termux Native

---

## Overview

VoidLauncher replaces conventional app grids, folder carousels, and dopamine-inducing badges with a **high-density, zero-latency system cockpit**. 

Built for edge software engineers and autonomous agent builders who treat their mobile device as a personal sovereign workstation.

## Core Features

- **100% Pitch Black AMOLED (`#000000`)**: Turns off inactive screen pixels to minimize thermals and maximize battery life during local ML/LLM runs.
- **Hardware & Telemetry HUD**: Real-time monitoring of battery %, temperature (<39°C preflight guard), RAM/swap pressure, and active NodeOS daemon ping status.
- **Hot-Reload User Customization (`~/.voidlauncher/config.json`)**:
  - Full control over color palette (accent, secondary, surface, border, warning, safe).
  - Configurable dock slots (up to 7 custom slots with custom labels, packages, actions, or direct Termux commands).
  - Configurable font family and sizes.
  - Toggles for HUD cards, telemetry bar, and on-screen digit keypad.
  - Hot-reloads dynamically every time you return to the home screen.
- **Bare-Integer Fast Dock (`1`–`5`)**:
  - `[1]` Terminal (`com.termux` / VoidTerm)
  - `[2]` Filesystem (`com.polymath.fs`)
  - `[3]` Browser (WebAuth & Termux URL bridge)
  - `[4]` Comms (Dialer)
  - `[5]` System Settings
- **Omni-Command & Shell Dispatcher (`>_`)**:
  - Type 2 letters for instant fuzzy app filtering.
  - Prefix `:` to dispatch commands directly to Termux via `com.termux.RUN_COMMAND` intent.
  - Direct shortcuts:
    - `:sh <cmd>`: Headless execution in Termux.
    - `:work <project>`: Navigates Termux directly to `~/Projects/<project>`.
    - `:clip`: Reads Android clipboard.
    - `:config`: Exports default JSON config template to `~/.voidlauncher/config.json`.
    - `:reload`: Immediately reloads configuration from disk.
    - `:status` / `:hud`: Launches Polymath Core status HUD.
- **Identity Switcher**: One-tap toggle between `[VOID]` (low-level runtimes, compilers) and `[MAIN]` (production SaaS, platforms).
- **Nothing Phone (2a) Glyph Ready**: Extensible hooks to pulse rear Glyph LEDs on agent build completion or thermal threshold warnings.

## Configuration & Customization

Create or edit your config file from Termux:

```bash
mkdir -p ~/.voidlauncher
nano ~/.voidlauncher/config.json
```

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
    { "index": 1, "label": "TERMINAL", "subtitle": "Termux · VoidTerm", "action": "launch_termux" },
    { "index": 2, "label": "FILESYSTEM", "subtitle": "Polymath FS", "package_name": "com.aistudio.filesystem.pmfsxz", "action": "launch_package" },
    { "index": 3, "label": "BROWSER", "subtitle": "WebAuth", "action": "open_browser" },
    { "index": 4, "label": "COMMS", "subtitle": "Dialer", "action": "open_dialer" },
    { "index": 5, "label": "SETTINGS", "subtitle": "Controls", "action": "open_settings" }
  ]
}
```

## Tech Stack

- **Platform**: Android 16 (API 35/36), Kotlin 1.9, Java 17/21
- **Architecture**: Low-overhead ViewBinding, Coroutines Flow, Zero Animation Lag
- **IPC**: Android Intents (`com.termux.RUN_COMMAND`), SharedPreferences, ContentProvider
- **CI/CD**: GitHub Actions automated APK compilation (`.github/workflows/build.yml`)

## Build & Test

```bash
# Run local preflight and QA analyzer
./scripts/build_apk.sh

# Build debug APK (via Gradle wrapper or GitHub Actions CI)
./gradlew assembleDebug
```
