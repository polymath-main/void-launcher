# Blueprint 01 — Core Runtime, VoidShell & Process Engine

## 1. Domain Scope
Provides a self-contained, zero-external-dependency shell and background task runtime inside VoidLauncher (`com.polymath.voidlauncher.core.shell`, `core.shim`, `core.process`, `core.terminal`).

## 2. Micro-Service & Module Inventory

| Module | File | Responsibility |
|---|---|---|
| `VoidShell` | [`VoidShell.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/shell/VoidShell.kt) | Executes commands via `/system/bin/sh` (`ProcessRunner`) or intercepts built-in `VoidShim` commands (`pmc`, `nodeos`, `void`, `help`). |
| `VoidShim` | [`VoidShim.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/shim/VoidShim.kt) | In-process command dispatcher handling `pmc status|hud|doctor`, `nodeos -c ping|status`, `void status|tasks|cancel`. |
| `VoidShellPort` | [`VoidShellPort.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/shell/VoidShellPort.kt) | Implements `ShellPort` over `VoidShell`; routes interactive requests to the in-app `VoidTermPanel`. |
| `VoidProcessManager` | [`VoidProcessManager.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/process/VoidProcessManager.kt) | Tracks active background tasks by `TaskHandle`, supports cooperative cancellation, and persists task state in `VoidTaskStore`. |
| `TerminalSession` / `TerminalBuffer` | [`TerminalSession.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/terminal/TerminalSession.kt) | Ring-buffered viewport (`rows × 4` scrollback cap) with soft column wrapping (`cols >= 8`) and command history. |
| `ConfigManager` / `ConfigSyncService` | [`ConfigManager.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/ConfigManager.kt) | 4-tier JSON config resolution (`VoidPaths.CONFIG` → `filesDir/config.json` → `assets/default_config.json` → Kotlin defaults) + `FileObserver` live hot-reload. |

## 3. Functional Data Flow

```
Omni-Bar (:run / :sh / :term)
   │
   ▼
VoidShell.run(command, workDir)
   ├──► VoidShim.tryHandle(command) ──► (Matches pmc / nodeos / void / help) ──► ShellResult.Success
   └──► DefaultProcessRunner (/system/bin/sh -c command) ──────────────────────► ShellResult.Success / Failed
```

## 4. Verification Contract
- Tested by `VoidShellTest`, `VoidShimTest`, `VoidProcessManagerTest`, `TerminalTest`, `TermuxRemovalCycle1Test`, and `TermuxRemovalCycle3Test`.
