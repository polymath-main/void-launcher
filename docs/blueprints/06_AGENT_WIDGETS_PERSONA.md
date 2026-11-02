# Blueprint 06 — Agent Orchestrator, Widget Deck, Personas & Notification Intelligence

## 1. Domain Scope
Coordinates background agent tasks, pluggable home widgets (`SCRIPT`, `HTTP`, `STATIC`), workspace identity personas, time-boxed focus schedules, and priority-scored notifications.

## 2. Micro-Service & Module Inventory

| Module | File | Responsibility |
|---|---|---|
| `PersonaIdentityMicroService` | [`PersonaIdentityMicroService.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/services/PersonaIdentityMicroService.kt) | Renders the top-bar identity badge (`[VOID]`, `[STILL]`, `[MAIN]`, `[AGENT]`, `[CLIENT]`), cycles personas, and presents the Workspace Identity & Mode dialog. |
| `AgentOrchestrator` | [`AgentOrchestrator.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/AgentOrchestrator.kt) | Dispatches async tasks via `ShellPort` + `VoidProcessManager`, persists task state in `filesDir/void_tasks.json`, non-clobberingly updates `workflow.json`, and invokes `onTaskCompleted` / `onTaskFailed` on the main thread. |
| `WidgetRegistry` | [`WidgetRegistry.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/WidgetRegistry.kt) | Loads `*.widget.json` descriptors and executes `SCRIPT` widgets via `VoidShell`, `HTTP` widgets via `HttpURLConnection`, and `STATIC` widgets with live UI refresh callbacks. |
| `PersonaManager` & `FocusScheduler` | [`PersonaManager.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/PersonaManager.kt), [`FocusScheduler.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/FocusScheduler.kt) | Manages built-in + custom JSON personas and evaluates `focus_schedule.json` time windows + `days_of_week` arrays. |
| `NodeOsBridge` & `AstGraphRenderer` | [`NodeOsBridge.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/NodeOsBridge.kt), [`AstGraphRenderer.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/AstGraphRenderer.kt) | Opt-in legacy NodeOS `workflow.json` status reader and read-only SQLite `agy_nodeos.db` AST node/edge topology query engine. |
| `NotifIntelligence` & `VoidNotificationListenerService` | [`NotifIntelligence.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/NotifIntelligence.kt) | Streams live notifications via `StateFlow` and scores/filters them by blacklist (`×0`), whitelist (`+5`), priority keywords (`+3`), and recency (`+1`). |
