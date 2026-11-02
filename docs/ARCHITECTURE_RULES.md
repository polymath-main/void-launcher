# VOID Launcher — Architecture Rules & STILL Design System
> **System Designation**: **VOID** (Sovereign System Cockpit & Terminal-First Android Launcher)  
> **Design Language**: **STILL** (Minimalist, Intentional, Obsidian OLED Design System)  
> **Target OS & Device**: Android 16 (API 35/36) · Linux 5.15 aarch64 · Nothing Phone (2a) [A142] · Dimensity 7200 Pro  
> **Package Namespace**: `com.polymath.voidlauncher`  
> **Repository**: [`https://github.com/polymath-main/void-launcher`](https://github.com/polymath-main/void-launcher)

---

## 1. Naming & Identity Invariant

- **Launcher Name**: **VOID** (`void-launcher` / `VoidLauncherActivity`).
- **Design System Name**: **STILL** (Text-first, low-distraction, monochrome Obsidian OLED design system).
- **Dual Persona Engine**:
  - `[VOID]`: Low-level runtimes, compilers, AST engines, and agent orchestration.
  - `[MAIN]`: Production SaaS platforms, multi-tenant workflows, and client applications.

---

## 2. The STILL Spatial & Interaction Model

VOID implements STILL's deliberate 4-space spatial topology:

```
                  ┌────────────────────────────────┐
                  │    COMMAND CENTER / OMNI-BAR   │
                  │   (Swipe Up / '>_' prompt)     │
                  └───────────────▲────────────────┘
                                  │ Swipe Up
┌──────────────────┐     ┌────────┴─────────┐     ┌──────────────────┐
│   WIDGET DECK    │ ◄── │   STILL HOME     │ ──► │   APP LIBRARY    │
│  (Swipe Left)    │     │    (Default)     │     │  (Swipe Right)   │
└──────────────────┘     └──────────────────┘     └──────────────────┘
```

1. **STILL Home (Center / Default)**:
   - Centered large clock (~76sp, no seconds) and uppercase date.
   - 5–7 high-value favorite rows (Phone, Messages, Camera, Notes, Maps, Terminal).
   - Touch targets strictly 54–58dp with high contrast.
   - Compact status line: battery percentage, thermal state, and audio ringer mode (Ring/Vibrate/Silent).
2. **Widget Deck (Horizontal Swipe Left)**:
   - At-a-glance utility cards (NodeOS status, battery vitals, weather/notes plug-ins).
3. **App Library (Horizontal Swipe Right)**:
   - Right-edge vertical drawer with fast A-Z touch scrubber (`AlphabetScrubberView`) and haptics.
4. **Command Center (Swipe Up from Home)**:
   - Full-screen omni-command surface (`>_`) with fuzzy app filter, `:cmd` execution, and command history.

---

## 3. STILL Visual Design System Tokens

| Token | Hex Value | Semantic Role |
|---|---|---|
| **Background** | `#000000` | Pure OLED pitch black; zero pixel power consumption |
| **Surface** | `#080808` | Card and drawer resting surface |
| **Surface Pressed** | `#171717` | Subtle, high-contrast touch feedback |
| **Border** | `#1A1A1A` | Thin 1dp structural divider |
| **Border Active** | `#333333` | Focused card / active input border |
| **Text Primary** | `#FFFFFF` | Clock, primary headers, app labels |
| **Text Secondary** | `#A1A1AA` | Metadata, branches, subtitles |
| **Text Muted** | `#52525B` | Timestamps, inactive hints, subtle icons |
| **Accent (STILL)** | `#007AFF` | Soft blue intentional focus accent |
| **Accent (VOID)** | `#E11D48` | Crimson sovereign execution accent |
| **Safe / Telemetry** | `#10B981` | Thermal safe status (<39°C), active daemon |
| **Warning** | `#F59E0B` | Battery pressure, high load |
| **Critical** | `#E11D48` | Thermal limit tripped (>39°C), job throttle |

### STILL Layout & Icon Rules
- **Zero Ornamental Glitz**: No gradients, no wallpaper blur overlays, no decorative dropshadows, no fake status bars.
- **Monochrome Adaptive Icon Pipeline**:
  - Prefer adaptive icon monochrome layer (`AdaptiveIconDrawable.monochrome`).
  - Fall back to clean single-color tinting or high-contrast initial glyphs.
  - Never mix random saturated icons into the primary Home favorites.
  - Icon caching is isolated outside persistent preference stores.

---

## 4. Architectural Rules & Engineering Invariants

### Rule 1: Zero UI Queries to System Managers
Compose and View screens render immutable UI state (`StateFlow` / models) and emit user events. They **never** invoke `PackageManager`, `LauncherApps`, or file I/O directly on the main UI thread.

### Rule 2: Platform API Isolation Behind Adapters
All Android-specific framework interactions (`BatteryManager`, `AudioManager`, `PackageManager`, shell execution) reside in dedicated core adapters (`TelemetryMonitor`, `AppIndexer`, `VoidShellPort`).

### Rule 3: Void-owned Shell Execution Contract
Commands run through VoidShell (`/system/bin/sh` on device) with a
Void-owned workdir (app files dir by default). Never target another
app's private binary or workdir, and never assume `/bin/bash` or
standard Linux FHS paths on device.

### Rule 4: Dynamic Hot-Reload Configuration Chain
Configuration resolves in 4 deterministic tiers:
1. `~/.voidlauncher/config.json` (external user override)
2. Sandbox `files/config.json`
3. Bundled asset `default_config.json`
4. Kotlin default models in `LauncherConfig.kt`
Hot reload occurs dynamically on every `onResume()` and via a FileObserver on the Void config store (ConfigSyncService).

### Rule 5: Thermal Envelope & Battery Guardian
- Thermal ceiling: **39.0°C**.
- `ThermalGuardian` enforces a 2.0°C hysteresis band:
  - If temp ≥ 39.0°C: Suspend heavy background ML/agent jobs and trigger Nothing Glyph LED warning pulse.
  - If temp ≤ 37.0°C: Clear throttle.
- `PowerProfileAdaptor` dynamically scales telemetry polling (1s → 1.5s → 4s → 8s) to eliminate unnecessary CPU wakeups.

### Rule 6: Gesture Arbitration & IME Insets
- Movements classify direction only after exceeding standard Android touch slop thresholds.
- Horizontal workspace swipes must not consume Android OS edge-back navigation gestures.
- Soft keyboard (`WindowInsetsCompat.Type.ime()`) lifts the omni-bar flush above the keyboard without layout jitter or clipping.

### Rule 7: Privacy & Permission Minimalism
The core launcher does not require or request Internet, camera, contacts, or accessibility permissions. All operations are strictly local-first and sovereign.
