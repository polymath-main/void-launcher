# VoidLauncher — Architecture & Technical Specifications

> **Version**: 3.0.0 (Void-owned core runtime: streaming VoidShell, real task cancellation by handle, VoidRoots path vocabulary, single-buffer VoidTerm with named sessions, schema-versioned atomic config)  
> **Target OS**: Android 16 (API 35/36) / Linux 5.15 aarch64 (Bionic libc)  
> **Hardware Target**: Nothing Phone (2a) [A142] · Dimensity 7200 Pro · 12 GB RAM · AMOLED 120Hz  
> **Repository**: [`https://github.com/polymath-main/void-launcher`](https://github.com/polymath-main/void-launcher)

---

## 1. Component Hierarchy & Module Map

```
com.polymath.voidlauncher/
├── VoidLauncherActivity.kt            # Root launcher lifecycle, layout & IME window orchestration
├── VoidSettingsActivity.kt            # Built-in visual & behavioral configuration editor
├── core/
│   ├── AppIndexer.kt                  # PackageManager query, icon loader & fuzzy finder
│   ├── BareIntegerRouter.kt           # Bare integer 1..N action dispatcher & intent router
│   ├── ConfigManager.kt               # 4-tier configuration loader & dynamic hot-reload engine
│   ├── FileManager.kt                 # Filesystem browser, operations (mkdir, rename, delete) & text preview
│   ├── NodeOsBridge.kt                # NodeOS daemon state, workflow.json & identity mode switcher
│   ├── NothingGlyphManager.kt         # Hardware Glyph LED controller
│   ├── TelemetryMonitor.kt            # Coroutine flow probing battery, temp (<39°C) & RAM
│   ├── VoidShellPort.kt             # VoidShell-backed ShellPort (no external terminal)
│   ├── VoidRoots.kt                 # Three-root path vocabulary + project resolution (config home / files home / shell cwd)
│   ├── VoidProcessManager.kt        # Task registry with real CancelHandles (thermal/agent cancel stops the process)
│   └── LegacyTermuxMigrator.kt      # Opt-in COPY-only legacy import (not a runtime path)
├── model/
│   ├── AppEntry.kt                    # Application descriptor, icon holder & section header
│   ├── FileItem.kt                    # File/folder descriptor with metadata & formatting
│   ├── IntentStatus.kt                # Workspace intent & NodeOS daemon metadata
│   ├── LauncherConfig.kt              # Full visual, layout, and behavioral config schema
│   └── TelemetrySnapshot.kt           # System vital snapshot
└── ui/
    ├── AlphabetScrubberView.kt        # Right-edge vertical A-Z touch scrubber with haptics
    ├── AppFilterAdapter.kt            # Search overlay adapter with configurable icon styles
    ├── DrawerAppAdapter.kt            # Right-edge vertical apps drawer adapter
    └── FileBrowserAdapter.kt          # Filesystem listing adapter with context operations
```

---

## 2. Shell Integration: VoidShell (no external terminal)

```
┌─────────────────────────┐       VoidShell (/system/bin/sh)            ┌────────────────────────┐
│  VoidLauncherActivity   │ ──────────────────────────────────────────> │  VoidShellPort /       │
│  (:sh / :run / :term)   │              in-process                     │  VoidTerm panel        │
└─────────────────────────┘                                             └────────────────────────┘
```

Commands run through the Void-owned VoidShell with real captured output;
`:term` runs in the in-app VoidTerm panel. No external terminal app is
required, and no other app's private directory is used at runtime.

---

## 3. Thermal Envelope Guard

Android 16 restricts access to `/sys/class/thermal/` without root. VoidLauncher uses `Intent.ACTION_BATTERY_CHANGED` to retrieve `BatteryManager.EXTRA_TEMPERATURE` in tenths of a degree Celsius:
- If `temp <= 39.0°C`: Status text is Phosphor Green (`#10B981`).
- If `temp > 39.0°C`: Status text turns Void Crimson (`#E11D48`), alerting the user to throttle heavy background workloads and triggering Nothing Glyph LED alert pulses.

---

## 4. Configuration Resolution Chain

VoidLauncher implements a 4-tier configuration resolution engine:
1. **User Override**: Void-owned config dir (`VoidPaths.HOME/config.json`)
2. **App Sandbox**: `/data/data/com.polymath.voidlauncher/files/config.json`
3. **Bundled Asset**: `assets/default_config.json`
4. **Hardcoded Fallbacks**: Default Kotlin models in `LauncherConfig.kt`

Every time `VoidLauncherActivity.onResume()` triggers, `ConfigManager.loadConfig()` checks the file timestamp/content. If changes are detected, it updates colors, redraws card surfaces, re-binds dock slots, adjusts drawer transparency, and refreshes the HUD in sub-millisecond time.

Area 07 additions (config surface, final): full field schema in `docs/SCHEMA.md` (generated from `LauncherConfig.kt`, guarded by `SchemaDocTest`); Settings edits font sizes, shell timeout/output cap, agent task timeout, wallpaper fit + status, and the notifications count-line toggle (default OFF), plus per-group reset previews, a dirty-check so leaving Settings without SAVE discards Reset previews and never rewrites a clean form, and a generation stamp (store mtime + length) that warns instead of silently overwriting an externally changed store. Export writes the CURRENT config, never defaults; the Settings button is now honestly labelled SAVE TO STORE, and EXPORT FILE… / IMPORT FILE… are SAF picker operations driven by `ConfigSyncService` streams, with a share-carefully warning (a config file can reveal paths and package names) and restore from `config.json.bak`. Legacy-config auto-read is kept as a read-only migration path for the legacy config file only; new legacy reads (NodeOS card, bridge, SAF file import) stay gated behind `nodeOsLegacyImportEnabled` (area 07 step 4, plan G-06 decision). The CI "release" APK is debug-signed (`signingConfigs.debug`) and is labelled as such in the workflow; a real signed release needs Shuvo's keystore decision.

---

## 5. Right-Edge App Drawer & A-Z Scrubber

- **Drawer Layout**: Collapsible vertical overlay anchored to the right edge with user-defined width (`drawer_width_percent`, default 85%).
- **Edge Transparency**: Supports user-configurable `drawer_alpha` (0.0 to 1.0). The drawer background is a flat argb colour; `drawer_edge_transparent` is stored in config but is not currently read when drawing the drawer, so no gradient fade is rendered (impl-02 step 2 correction — the old "subtle gradient fade" claim did not match code).
- **Alphabetical Scrubbing**: `AlphabetScrubberView` maps vertical touch coordinates directly to letter indices in `AppIndexer.getSectionPositions()`, smoothly scrolling to target packages with haptic feedback.

---

## 6. Window Insets & Soft Keyboard Elevation

Using Android `WindowInsetsCompat.Type.ime()`, `VoidLauncherActivity` dynamically calculates the soft keyboard's bounding height:
- When the keyboard rises, `bottomAnchorLayout` (containing the omni-command bar) lifts smoothly to sit flush on top of the keyboard space.
- When the keyboard closes, it settles back above the system navigation bar without layout jitter or clipping.
