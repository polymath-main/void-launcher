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
| `:void-js` list/run/stop/triggers/fire/attach/detach | Typing | Runner, manifest, sandbox, trigger store real; native engine was ABSENT from the impl-07 APK, so runs reported Unavailable | **Fixed (build-level, not device-proven):** cherry-picked `feature/quickjs-engine-real` commit `4a2cd9a` (already based on `f89838c`): vendored QuickJS 2024-01-13, `voidquickjs.c`, JNI `nativeRun` with adapters, CMake in `app/build.gradle`, NDK/CMake install step in CI. The availability gate stays: a build without the `.so` still reports Unavailable. | CI run 41751562650 green; `.so` verified present in the debug APK for all 3 ABIs with the JNI symbol; on-device JS execution still UNPROVEN |
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

## Proof

- Branch: `feature/ui-audit-fix-1` (main untouched, no force-push).
- CI build commit: `98bbf13` (fixes + QuickJS merge + id-count fix +
  CONFIG_BIGNUM fix). The final commit on this branch after CI only
  fills in this proof section (a docs-only change; it cannot change
  the APK built from `98bbf13`).
- Build: GitHub Actions workflow `build.yml` via workflow_dispatch on
  this branch, because the workflow only auto-runs on `main` and
  `feature/v3-ui`. Local Gradle on this VM is not viable (Gradle
  daemon protocol failure, plus no NDK/CMake locally), so CI is the
  real build proof here, per the code-change proof skill.
- CI history on this branch (re-dispatches were build fixes only):
  - Run 41750193173: FAILED at unit tests — 281 tests, 1 failed
    (`ConfigStep1Test` exact settings-id count 91 vs 95 after the
    4 new switches). Main code compiled.
  - Run 41750644796: unit tests PASSED, assemble FAILED at
    `buildCMakeDebug[arm64-v8a]` — undefined `bfdec_normalize_and_
    round` / `mp_pow_dec`. Root cause: the Android CMakeLists
    omitted `CONFIG_BIGNUM`, which upstream QuickJS builds with by
    default. Fixed by defining it (no host-API change).
  - Run 41751562650: **SUCCESS** — conclusion `success`, 7m20s, all
    steps green (unit tests, lint non-blocking, version check,
    assemble release + debug, checksums, artifact uploads).
    URL: https://github.com/polymath-main/void-launcher/actions/runs/41751562650
- APKs from run 41751562650 (CI-published checksums, debug checksum
  re-verified on the downloaded artifact):
  - Release (debug-signed): SHA-256
    `07f4e77c9007ccc108f48773cfd9dcd23ee667fdc943ca2fdaac3b9f0b1f0d99`
  - Debug: SHA-256
    `82ee6c9cbc16b3623017e70eb7e9ac48cbf3e7917e39abf564bcc544ba2d4c06`
    (9.2 MB, verified locally after download)
  - Debug APK verified with aapt2: package
    `com.polymath.voidlauncher.debug`, versionCode 5,
    versionName `3.0.0-debug`, SDK 35/min 26.
  - Manifest permission check: QUERY_ALL_PACKAGES, INTERNET,
    ACCESS_NETWORK_STATE, VIBRATE, SET_WALLPAPER,
    READ_EXTERNAL_STORAGE (maxSdk 32), MANAGE_EXTERNAL_STORAGE —
    **no Termux permission, no RUN_COMMAND**.
  - QuickJS native library present in the debug APK for all three
    ABIs: `lib/arm64-v8a/libvoidquickjs.so`,
    `lib/armeabi-v7a/libvoidquickjs.so`,
    `lib/x86_64/libvoidquickjs.so`; the arm64 `.so` contains the
    JNI symbol
    `Java_com_polymath_voidlauncher_core_js_QuickJsEngine_nativeRun`.
    This proves the library ships; it does NOT prove a macro runs
    on a device (see still-unproven list).

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

## Dispatch fix (feature/ui-dispatch-fix-1, from feature/ui-audit-fix-1)

All five late-audit dispatch gaps were verified in code first and fixed
here; none was assumed. No default config was changed, no daemon or
auto-run behaviour was added, and bare `:term` (opens the VoidTerm
panel) is untouched.

- `:themes <sub>` now dispatches as an alias for `:theme <sub>`
  (previously only bare `:themes` was aliased; `:themes list` hit
  UNKNOWN). Prefix stripping is `themes` first, then `theme`.
- `:macros list` now lists macros, same as bare `:macros`.
- `:help` now documents commands that were already dispatched but
  undocumented: `:legacy import`, `:legacy verify`, and the
  `:void-js triggers | fire | trigger attach | detach` forms. No
  row was added for anything not dispatched.
- Bare-arg `:eval`, `:task`, `:kill`, `:record`, `:play`, `:run`,
  `:sh`, `:mode`, `:id`, `:work`, `:snippet` now print their
  CommandGuide usage line instead of UNKNOWN. Usage strings are
  looked up from the guide table, not duplicated, so table and
  dispatch stay in sync.
- Digits 6–7: default dock is slots 1–5 (unchanged). A typed bare
  integer, a command-center integer, or a digit key routing to an
  empty slot now says the slot is empty and points to Settings →
  Customize dock slots, instead of doing nothing silently.
  `BareIntegerRouter.routeInteger` already returned false for a
  missing slot; its call sites previously ignored the result.

Left out: nothing from the five gaps was left out. The new dispatch
tests (`DispatchFixTest`) assert on the executed CommandGuide table
only; Activity dispatch is Android-bound and cannot run in the JVM
harness, so its proof is the CI build on this branch (proof section
below to be filled from that run), and on-device command behaviour
remains unproven until the APK is installed and the commands typed.

## Dispatch-fix proof (to be filled from the CI run on this branch)
