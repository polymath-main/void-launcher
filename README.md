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
- **Bare-Integer Fast Dock (`1`–`5`)**:
  - `[1]` Terminal (`com.termux` / VoidTerm)
  - `[2]` Filesystem (`com.polymath.fs`)
  - `[3]` Browser (WebAuth & Termux URL bridge)
  - `[4]` Comms (Dialer)
  - `[5]` System Settings
- **Omni-Command & Shell Dispatcher (`>_`)**:
  - Type 2 letters for instant fuzzy app filtering.
  - Prefix `:` to dispatch commands directly to Termux via `com.termux.RUN_COMMAND` intent.
  - Direct shortcuts: `:sh <cmd>`, `:work <project>`, `:clip`, `:status`.
- **Identity Switcher**: One-tap toggle between `[VOID]` (low-level runtimes, compilers) and `[MAIN]` (production SaaS, platforms).
- **Nothing Phone (2a) Glyph Ready**: Extensible hooks to pulse rear Glyph LEDs on agent build completion or thermal threshold warnings.

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
