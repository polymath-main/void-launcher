# Void Launcher UI audit — feature/ui-audit-fix-1

Base: `feature/impl-07-config` at `f89838c` (CI run 41608826922 passed on it).
Audit branch: `feature/ui-audit-fix-1`. Main untouched, no force-push, nothing
installed on any phone. A JVM test count is not proof of UI behaviour; the
proof for this branch is the CI build recorded at the bottom of this file.
Device verification is still outstanding for everything below.

Method: every `CommandGuide` entry was checked against `handleCommandMode`
in `VoidLauncherActivity.kt`, every layout id in `activity_void_launcher.xml`
and `activity_void_settings.xml` was checked against its binding use and
listener, and every Settings control was checked against its load/save path.
A gap is listed only where that check failed.

## Audit table

| Feature | UI entry | Backend status | Fix applied / gap left open | Proof |
|---|---|---|---|---|
| Home dock slots 1–7, tap to launch, long-press to edit | Dock buttons + edit dialog | Real (`DockSlotMicroService`, `BareIntegerRouter`) | None needed | Code path checked; CI build |
| App drawer, search, A–Z scrubber, long-press app menu | Drawer tab handle, drawer search | Real (`AppIndexer`, `DrawerAppAdapter`) | None needed | Code path checked; CI build |
| Omni search, app launch, bare-digit routing, `:cmd` typing | Omni bar + execute button | Real | None needed | Code path checked; CI build |
| Command center input, suggestions, app results | Command-center input + execute on editor action | Real | None needed | Code path checked; CI build |
| Command-center chips (was 8) | 8 chips | Real | **Fixed:** added `:deck`, `:term`, `:macros`, `:help` chips (same 48dp/12sp style, `handleCommandCenterChip`) | `UiAuditFixTest.commandCenterHasDeckTermMacrosHelpChips` + CI build |
| `:help`, `:eval`, `:run`, `:sh`, `:!` | Omni / command center typing | Real (`LiveScriptEngine`, `VoidShell`) | None needed | Dispatch checked |
| `:task`, `:tasks`, `:kill` | Typing; `:tasks` chip existed | Real, gated by Agent switch (existed) | None needed | Dispatch checked |
| `:graph` | Typing; `:graph` chip existed | Real, gated by AST switch (existed) | None needed | Dispatch checked |
| `:persona`, `:id`, `:persona edit` | Typing; badge tap cycles, long-press picks | Real, dialog editor exists | None needed | Dispatch checked |
| `:record`, `:stop`, `:play`, `:macros` | Typing; `:macros` chip added | Real, gated by `macrosEnabled` (default ON) | **Fixed:** added Settings switch for `macrosEnabled` (previously no control) | `UiAuditFixTest.gatedFeaturesHaveSettingsSwitches` + CI build |
| `:history`, `:history delete`, `:snippet`, `:snippets` | Typing | Real, gated by `commandHistoryEnabled` (default ON) | **Fixed:** added Settings switch for `commandHistoryEnabled` (previously no control) | Same test + CI build |
| `:term` (with command) | Typing | Real (`VoidTermPanel`, gated) | None needed for the with-arg form | Dispatch checked |
| Bare `:term` | Typing / new `:term` chip | Panel existed, dispatch missing | **Fixed:** bare `:term` now calls `openVoidTerm()` instead of falling through to UNKNOWN COMMAND | `UiAuditFixTest.bareTermOpensVoidTermPanel` + CI build |
| VoidTerm enable/disable | NONE before this branch | Gate `voidTermEnabled` default OFF | **Fixed:** added Settings switch for `voidTermEnabled`. Before this, `:term` was unreachable without hand-editing config JSON; the dock TERMINAL slot and OPEN_TERMINAL gesture bypass the gate, which is unchanged. | Same test + CI build |
| `:power`, `:focus`, `:focus edit`, `:mode`, `:still`, `:cockpit`, `:minimal` | Typing; `:power` chip existed; focus editor also via Settings | Real | None needed | Dispatch checked |
| `:wallpaper`, `:theme` | Typing; wallpaper studio via Settings and file operations | Real | None needed | Dispatch checked |
| `:work`, `:files`, `:apps`, `:settings`, `:clip`, `:nodeos`, `:hud`, `:deck`, `:cmd`, `:reload`, `:audit` | Typing; `:files`/`:settings` chips existed; `:deck` chip added | Real | None needed beyond the `:deck` chip | Dispatch checked |
| `:void-js` list/run/stop/triggers/fire/attach/detach | Typing | Runner, manifest, sandbox, trigger store real; native engine was ABSENT from the impl-07 APK, so runs reported Unavailable | **Fixed (build-level, not device-proven):** cherry-picked `feature/quickjs-engine-real` commit `4a2cd9a` (already based on `f89838c`): vendored QuickJS 2024-01-13, `voidquickjs.c`, JNI `nativeRun` with adapters, CMake in `app/build.gradle`, NDK/CMake install step in CI. The availability gate stays: a build without the `.so` still reports Unavailable. | CI build below (does the `.so` build?); on-device JS execution still UNPROVEN |
| JS triggers firing autonomously | Attach/detach/fire via `:void-js` only | Stored, never fired by any daemon | **Left open, deliberately:** no background daemon was built. Autonomous trigger firing needs Shuvo's explicit approval (it would run user scripts without him present). | Recorded here, not built |
| Widget deck, per-widget retry tap, action approval dialog | `:deck`, STILL swipe-right gesture | Real (`WidgetRegistry`), gated by Widgets switch (existed) | None needed | Code path checked; CI build |
| Files browser: chips, search/sort, create file/folder, Open-with, wallpaper/theme from file, Open in VoidTerm | Files browser UI | Real | None needed | Code path checked; CI build |
| Files SAF workspace picker (choose an arbitrary folder) | NONE | No picker code exists for the files browser (SAF exists only for legacy import and config import/export) | **Left open:** area 02 step 8 was left for Shuvo's UX approval; not decided unilaterally here | Recorded here, not built |
| Persistent interactive terminal (type into a live shell session) | NONE — VoidTerm shows a rendered buffer; `:term <cmd>` runs one command | Output-only panel | **Left open:** gated/undecided product decision; not decided unilaterally here | Recorded here, not built |
| Settings: save, export/import (SAF), restore backup, reset groups, wallpaper studio, dock manager, persona/focus editors, legacy-import route | Settings activity | Real | None needed beyond the new switches | Code path checked; CI build |
| Compound gestures (pinch/edge/two-finger) on/off | NONE before this branch (simple gestures had spinners; system reset already touched this flag) | Flag `compoundGesturesEnabled` default ON | **Fixed (partial):** added Settings master switch. Remapping individual compound gestures from Settings is still not possible — left open. | Test + CI build |
| Compound-gesture remapping (per-gesture spinners) | NONE | Config fields exist, no UI | **Left open** (see above) | Recorded here |
| Liquid-touch surface on/off | NONE | Flag `liquidTouchEnabled` default ON | **Left open:** no Settings control; cosmetic effect, not a core command path | Recorded here |
| Notifications: list, open, dismiss, clear, permission prompts | Notifications card, permission cards | Real (`VoidNotificationListenerService`) | None needed | Code path checked; CI build |
| Gestures (simple), default-launcher role, status-bar expansion, minimal toggle | Gesture engine, banner button, Settings | Real | None needed | Code path checked; CI build |

## What was NOT fixed, and why

- JS triggers do not fire on their own, the files SAF workspace picker does not
  exist, and there is no persistent interactive terminal. All three were
  already gated (approval / UX approval / undecided decision) before this
  branch, and this branch does not decide them.
- No background daemon, auto-send, auto-post, or auto-run behaviour was added.

## Proof (to be filled by the CI run on this branch)

- Branch: `feature/ui-audit-fix-1`
- Commits: (filled after push, before reporting)
- Build: GitHub Actions workflow `build.yml` via workflow_dispatch on this
  branch, because the workflow only auto-runs on `main` and `feature/v3-ui`.
- Result, run URL, APK package/version/checksums, manifest permission check:
  (filled after the CI run — do not read this file as proof until filled.)

## Still unproven (plainly)

- Device testing on Shuvo's Nothing Phone (2a): no phone run was done.
- Visual verification of COCKPIT / STILL / MINIMAL / FILES screens.
- QuickJS on-device execution: even if CI builds `libvoidquickjs.so`, no
  device has run `:void-js run` from this branch yet.
- Trigger firing, SAF workspace picking, and interactive terminal: not built,
  so nothing to verify there.
- The new Settings switches and command-center chips have only been proven
  to compile and to be wired in source; their on-device tap behaviour is
  unverified until the APK is installed and tapped.
