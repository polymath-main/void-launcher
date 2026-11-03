# Blueprint 01 — Core Runtime, VoidShell & Process Engine

## 1. Domain Scope
Provides a self-contained, zero-external-dependency shell and background task runtime inside VoidLauncher (`com.polymath.voidlauncher.core.shell`, `core.process`, `core.terminal`).

## 2. Micro-Service & Module Inventory

| Module | File | Responsibility |
|---|---|---|
| `VoidShell` | [`VoidShell.kt`](app/src/main/java/com/polymath/voidlauncher/core/shell/VoidShell.kt) | Executes commands via `/system/bin/sh` (`ProcessRunner`) with concurrent stream draining (no pipe deadlock), optional line streaming, run records (lengths only, never output text), configurable timeout/output cap, and `start()` returning a cancellable `RunningShell`. No command interception: there is no `VoidShim.tryHandle` — the shim was deleted in impl-01 step 6. |
| `VoidShellPort` | [`VoidShellPort.kt`](app/src/main/java/com/polymath/voidlauncher/core/shell/VoidShellPort.kt) | Implements `ShellPort` over `VoidShell`; routes interactive requests to the in-app `VoidTermPanel`. |
| `VoidProcessManager` | [`VoidProcessManager.kt`](app/src/main/java/com/polymath/voidlauncher/core/process/VoidProcessManager.kt) | Tracks active background tasks by handle, with attached `CancelHandle`s: `cancel`/`cancelRunning` (agent cancel, thermal throttle) destroy the real process and its descendants, never by name/pattern. Persistence of task *history* is `AgentOrchestrator`'s `void_tasks.json`; there is no `VoidTaskStore` class. |
| `VoidRoots` | [`VoidRoots.kt`](app/src/main/java/com/polymath/voidlauncher/core/shell/VoidRoots.kt) | One path vocabulary: config home (`VoidPaths.HOME`), files home (`filesDir/void`), shell default cwd (`filesDir`, unchanged by design). Resolves project names under the files home with canonical containment; `VoidShell` fails loudly on a non-directory workdir. |
| `TerminalSession` / `TerminalBuffer` | [`TerminalSession.kt`](app/src/main/java/com/polymath/voidlauncher/core/terminal/TerminalSession.kt) | Ring-buffered viewport (`rows × 4` scrollback cap) with soft column wrapping (`cols >= 8`), command history, and minimal ANSI handling (SGR/CSI dropped, CR overwrites, backspace deletes, clear-screen clears) — escape text never leaks literally. `VoidTermPanel` holds named sessions and renders each session's own buffer (no shadow buffer). |
| `ConfigManager` / `ConfigSyncService` | [`ConfigManager.kt`](app/src/main/java/com/polymath/voidlauncher/core/ConfigManager.kt) | 4-tier JSON config resolution (`VoidPaths.CONFIG` → `filesDir/config.json` → `assets/default_config.json` → Kotlin defaults) + `FileObserver` live hot-reload (300 ms debounce, unchanged-content guard, own-save guard in the Activity) + `schema_version` (current 1, future versions load with a warning) + atomic tmp+rename saves + `config.json.bak` backup on import and `restoreBackup()`. |

## 3. Functional Data Flow

```
Omni-Bar (:run / :sh / :term)
   │
   ▼
VoidShell.run(command, workDir)
   └──► DefaultProcessRunner (/system/bin/sh -c command) ──────────────────────► ShellResult.Success / Failed
```

## 4. Verification Contract
