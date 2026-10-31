# VoidLauncher — Architecture & Technical Specifications

## 1. Component Hierarchy

```
com.polymath.voidlauncher/
├── VoidLauncherActivity.kt        # Root launcher lifecycle & window controller
├── core/
│   ├── AppIndexer.kt              # PackageManager query & in-memory fuzzy finder
│   ├── BareIntegerRouter.kt       # Bare integer 1..5 action dispatcher
│   ├── NodeOsBridge.kt            # NodeOS daemon state, workflow.json & identity mode
│   ├── NothingGlyphManager.kt     # Hardware Glyph LED controller
│   ├── TelemetryMonitor.kt        # Coroutine flow probing battery, temp & RAM
│   └── TermuxIpcDispatcher.kt     # com.termux.RUN_COMMAND intent bridge
├── model/
│   ├── AppEntry.kt                # Application descriptor
│   ├── IntentStatus.kt            # Workspace intent & NodeOS daemon metadata
│   └── TelemetrySnapshot.kt       # System vital snapshot
└── ui/
    └── AppFilterAdapter.kt        # RecyclerView adapter for fast search list
```

## 2. IPC Integration: Termux RUN_COMMAND

```
┌─────────────────────┐       Intent (com.termux.RUN_COMMAND)       ┌────────────────────────┐
│ VoidLauncherActivity│ ──────────────────────────────────────────> │ Termux RunCommandService│
│   (:sh / :work)     │                                             │   (Bionic / Bash)      │
└─────────────────────┘                                             └────────────────────────┘
```

The launcher dispatches commands with:
- `com.termux.RUN_COMMAND_PATH`: `/data/data/com.termux/files/usr/bin/bash`
- `com.termux.RUN_COMMAND_WORKDIR`: `/data/data/com.termux/files/home/Projects`
- `com.termux.RUN_COMMAND_BACKGROUND`: `true` for headless jobs, `false` for interactive terminal sessions.

## 3. Thermal Envelope Guard

Android 16 restricts access to `/sys/class/thermal/` without root. VoidLauncher uses `Intent.ACTION_BATTERY_CHANGED` to retrieve `BatteryManager.EXTRA_TEMPERATURE` in tenths of a degree Celsius:
- If `temp <= 39.0°C`: Status text is Phosphor Green (`#10B981`).
- If `temp > 39.0°C`: Status text turns Void Crimson (`#E11D48`), alerting the user to throttle heavy background workloads.
