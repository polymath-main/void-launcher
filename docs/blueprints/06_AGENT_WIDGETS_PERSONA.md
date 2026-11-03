# Blueprint 06 — Agent Orchestrator, Widget Deck, Personas & Notification Intelligence

> Doc version stamp: matches code at version 3.0.0 (code 5) + impl-06
> (feature/impl-06-agent, steps 1–9). Corrected 2026-11-03 during
> impl-06 step 9: the previous revision linked sources under a Termux
> private path, claimed a `VoidShim` NodeOS path, and claimed widget
> "live refresh callbacks" without qualification. All three were wrong;
> see §4.

## 1. Domain Scope
Coordinates background agent tasks, pluggable home widgets (`SCRIPT`, `HTTP`, `STATIC`), workspace identity personas, time-boxed focus schedules, and priority-scored notifications.

## 2. Micro-Service & Module Inventory

| Module | File | Responsibility |
|---|---|---|
| `PersonaIdentityMicroService` | [`PersonaIdentityMicroService.kt`](app/src/main/java/com/polymath/voidlauncher/core/services/PersonaIdentityMicroService.kt) | Renders the top-bar identity badge, cycles personas, and presents the Workspace Identity & Mode dialog. |
| `AgentOrchestrator` | [`AgentOrchestrator.kt`](app/src/main/java/com/polymath/voidlauncher/core/AgentOrchestrator.kt) | Dispatches async tasks via `ShellPort` + `VoidProcessManager`, persists task state in `filesDir/void_tasks.json`, non-clobberingly updates `workflow.json`, and invokes `onTaskCompleted` / `onTaskFailed` on the main thread. Real output + exit codes (impl-06 step 2); per-task timeout → `timed_out` (step 7). |
| `WidgetRegistry` | [`WidgetRegistry.kt`](app/src/main/java/com/polymath/voidlauncher/core/WidgetRegistry.kt) | Loads `*.widget.json` descriptors and executes `SCRIPT` widgets via `VoidShell`, `HTTP` widgets via `HttpURLConnection`, and `STATIC` widgets. Refresh is SCHEDULED while the deck is open (clamped 5 s–10 min, step 4) — not background live callbacks, not a full power gate (CRITICAL suppression lives in the deck-refresh path only). |
| `PersonaManager` & `FocusScheduler` | [`PersonaManager.kt`](app/src/main/java/com/polymath/voidlauncher/core/PersonaManager.kt), [`FocusScheduler.kt`](app/src/main/java/com/polymath/voidlauncher/core/FocusScheduler.kt) | Manages built-in + custom personas (config + external JSON) and evaluates `focus_schedule.json` time windows + `days_of_week` arrays. Cycle order is pinned: built-ins (void, main, still, agent, client), then config, then external (step 3). Focus blocks support midnight crossing, overlap priority, and restore-on-exit (step 5). |
| `NodeOsBridge` & `AstGraphRenderer` | [`NodeOsBridge.kt`](app/src/main/java/com/polymath/voidlauncher/core/NodeOsBridge.kt), [`AstGraphRenderer.kt`](app/src/main/java/com/polymath/voidlauncher/core/AstGraphRenderer.kt) | Opt-in legacy NodeOS `workflow.json` status reader and read-only SQLite `agy_nodeos.db` AST node/edge topology query engine. |
| `NotifIntelligence` & `VoidNotificationListenerService` | [`NotifIntelligence.kt`](app/src/main/java/com/polymath/voidlauncher/core/NotifIntelligence.kt) | Streams live notifications via `StateFlow` and scores/filters them by blacklist (drop), whitelist (`+5`), priority keywords (`+3`), and recency (`+1`, under 5 min), base 1.0, max 5. Scores are surfaced as a "why" line (step 10). |

## 3. Identity badges — what is actually true

Built-in personas are void, main, still, agent, and client, so `[AGENT]`
and `[CLIENT]` badges are real. Precision: in `NUMERIC_BRACKET` badge
style the badge renders from the persona **id**; badge-label overrides
(`identityVoidLabel`, `identityMainLabel`, `identityStillLabel`) exist
only for void, main, and still. Other dock styles render the badge
differently (see `PersonaIdentityMicroService.applyIdentityBadge`).

## 4. Removed / corrected claims

- **VoidShim does not exist and never handled NodeOS.** It was deleted
  in impl-01 (by deletion, not adoption). There is no `nodeos -c ping`
  path anywhere in the launcher. NodeOS status comes from reading a
  user-provided `workflow.json` (opt-in) plus `agy_nodeos.db` existence.
- **VoidLauncher does not install, download, bundle, or run NodeOS.**
  There is no installer, setup script, or `nodeos` binary in this repo.
  Legacy import is opt-in (`nodeOsLegacyImportEnabled=false` by
  default), copy-only, and read-only.
- **No Termux paths.** Previous `file:///data/data/com.termux/...`
  links are replaced above with repo-relative paths. The launcher
  never reads another app's private directory at runtime; the
  supported route is the step 9 SAF user-picked import.
- **Widget "live refresh" qualified** (see the `WidgetRegistry` row):
  periodic, deck-open-only, clamped, CRITICAL-suppressed in the deck
  path. `WidgetRegistry` itself has no power gate.

## 5. Legacy import (step 9)

`:legacy import` opens the system folder picker; the picked tree's
`workflow.json` + `agy_nodeos.db` are COPIED (never moved or deleted)
into the Void-owned config paths, with a 100 MiB per-file guard and
tmp + rename writes. After import, a one-tap Verify runs
`queryTopology()` on the copied DB and reports node/edge counts +
top types, or a clear "not a NodeOS DB" error. Importing alone does
not enable reading — the Settings legacy-import switch must be on,
and with it off no code path reads those paths for display.

## 6. Approval gates (§5, applied throughout impl-06)

Nothing in this area auto-sends, auto-posts, or auto-dismisses.
Widget command actions confirm on first tap per (widget, action);
`:task` runs with the same trust as typing the command; HTTP widgets
are HTTPS-only unless a widget opts into LAN HTTP; notification
dismiss is per-tap only. Any future outward action needs, in order:
explicit enablement, per-action approval, preview-before-send, caps,
revoke + local audit log, and silent-safe failure.

## 7. Legacy-import sunset — UNDECIDED (product owner, not implementer)

Options: (a) keep legacy import indefinitely as an import-only bridge;
(b) sunset it once Void-native tasks + `workflow.json` cover the same
HUD needs. No decision has been taken. The bridge must NOT be deleted
without that decision. Recorded here, not decided here.

## 8. Known partials carried forward

- Step 7 durable scheduling is BLOCKED: no WorkManager/AlarmManager
  exists in this app, so there are no scheduled tasks, no daemon, and
  no autonomous JS trigger firing. Task timeouts (60 s default) are
  enforced; scheduling awaits a product-owner decision on adding a
  durable scheduler dependency.
- Android-bound wiring (dialogs, deck rendering, SAF picker, badge
  rendering) is source-asserted in JVM tests and still needs CI +
  device verification; JVM tests never claim it.
