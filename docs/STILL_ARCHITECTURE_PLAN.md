# VOID Launcher — STILL Architecture Plan & Blueprint
> **Launcher Name**: **VOID**  
> **Design System**: **STILL** (Obsidian OLED Design Language)  
> **Document**: Architecture Plan · Design System · Interaction Model · Implementation Roadmap · Technical References  
> **Source**: `/storage/emulated/0/Download/Void_Android_Launcher_Architecture_Plan.pdf`  
> **Version**: 1.0 (Prepared 1 November 2026)  
> **Target OS & Device**: Android 16 (API 35/36) · Nothing Phone (2a) [A142]  
> **Primary Visual Theme**: STILL Obsidian OLED (`#000000`, white text `#FFFFFF`, soft blue accent `#007AFF`)

---

## Project Baseline

| Attribute | Decision |
|---|---|
| **Launcher Name** | **VOID** (`void-launcher`) |
| **Design System** | **STILL** (Minimalist, Intentional Text-First Design) |
| **Application ID** | `com.polymath.voidlauncher` |
| **Product Direction** | Text-first, monochrome, low-distraction Android Home replacement |
| **Primary Platform** | Android; initial test target: Nothing Phone (2a) / Android 16 |
| **Stack** | Kotlin, Jetpack Compose / ViewBinding, MVVM, StateFlow, DataStore / SQLite |
| **Primary Visual Theme** | STILL Obsidian OLED — pure black (`#000000`), white text (`#FFFFFF`), soft blue accent (`#007AFF`) |
| **Delivery Approach** | Phased, test-driven implementation; one complete file at a time |
| **Document Status** | Architecture baseline |

---

## 01. Executive Summary and Product Principles

STILL is a native Android launcher designed to reduce visual noise and make intentional actions fast. It replaces the conventional icon-grid home screen with a small number of deliberate spaces: a clock-led Home, a Widget Deck, an App Library, and a full-screen Command Center.

### Product Principles
- **Intentionality**: Expose a small set of high-value actions instead of maximizing visible content.
- **Low cognitive load**: Monochrome icons, restrained typography, and consistent spatial hierarchy.
- **Native Android behavior**: Use the platform launcher contract, package APIs, system insets, and lifecycle correctly.
- **Local-first**: The core launcher works without an account, network connection, or external AI service.
- **Progressive capability**: Keep foundation small and dependable; add advanced features only after the base is stable.
- **Recoverability**: Configuration must survive process death and upgrades; failures must not strand the user without a Home app.

### Non-Goals for the First Release
- No cloud account, analytics, advertising, or mandatory network access.
- No AI API dependency or remote command interpretation.
- No accessibility-service automation, notification-listener access, or usage-access permission in the basic launcher.
- No attempt to replace Android system navigation, status bar, or lock screen.
- No heavy multi-module architecture before code size and ownership boundaries justify it.

---

## 02. Product Experience & Information Architecture

### Workspace Model

| Workspace | Position / Entry | Purpose | Phase 01 Behavior |
|---|---|---|---|
| **Widget Deck** | Swipe horizontally left | At-a-glance information and personal utility cards | Functional shell; full card system later |
| **Home** | Center / default | Clock, date, favorites, and compact system status | Primary complete experience |
| **App Library** | Swipe horizontally right | Installed application discovery and launch | Functional shell; full library later |
| **Command Center** | Swipe up from Home | Full-screen command/search surface | Working shell; search engine later |

### Home Composition
- **Clock**: Large centered clock (~76sp visual starting point; no seconds).
- **Date**: Uppercase secondary text directly below the clock.
- **Time Format**: 12/24-hour follows the device system setting.
- **Favorites**: Five initial favorites (Phone, Messages, Camera, Notes, Maps), later configurable up to seven.
- **App Rows**: 54–58dp touch targets, monochrome icon plus label, no card background.
- **Status Line**: Bottom status line displays battery percentage and audio mode (Ring / Vibrate / Silent) when reliably available.

### App Library & Widget Deck
In Phase 01, these are navigation destinations with intentional empty-state or placeholder content to keep the workspace contract testable without premature indexing complexity.

### Command Center
Full-screen surface with back/close behavior. Subsequent phases add local search, app aliases, quick actions, recents, keyboard navigation, and deterministic command parsing (never presented as AI-powered).

---

## 03. Visual Design System

### Design Tokens

| Token | Hex / Value | Usage |
|---|---|---|
| **Background** | `#000000` | Pure OLED black; main surfaces |
| **Primary Text** | `#FFFFFF` | Clock, app labels, and essential content |
| **Accent** | `#007AFF` | Focused/selected state and restrained action emphasis |
| **Secondary Text** | `#8E8E93` | Date, metadata, and low-priority labels |
| **Typeface** | Roboto Flex / Monospace | Primary typographic family |
| **Icon Treatment** | Monochrome | Adaptive monochrome layer when available; fallback otherwise |

### Layout & Rendering Rules
1. **No Gradients, Blur, or Ornamental Shadows**: Pure OLED black backgrounds with minimal border lines.
2. **System Insets & Cutouts**: Respect display cutouts, edge-to-edge behavior, and native system insets.
3. **Native Status Bar**: Keep the system status bar native; never draw a fake status bar.
4. **Touch Target Size**: Consistent spacing scale and minimum touch targets (≥48–58dp).
5. **Monochrome Icon Strategy**:
   - Prefer an app's adaptive-icon monochrome layer when the platform exposes it.
   - If unavailable, render a consistent monochrome fallback rather than mixing full-color and monochrome icons.
   - Cache derived icon assets outside DataStore/database.
   - Invalidate affected entries on package add, replace, change, or removal.
   - Icon conversion is a presentation concern, not a domain-model responsibility.

---

## 04. Interaction & Gesture Model

| Input | Action | Behavior / Constraint |
|---|---|---|
| **Horizontal swipe left** | Move toward Widget Deck | Use a stable threshold and settle animation |
| **Horizontal swipe right** | Move toward App Library | Do not intercept vertical gestures |
| **Swipe up from Home** | Open Command Center | Full-screen overlay/surface, not a fourth page |
| **System Back** | Close Command Center or return to Home | Delegate to Android back handling; avoid trapping user |
| **Tap favorite** | Launch application | Resolve current launch intent; handle missing/unlaunchable app |
| **Settings entry** | Open launcher settings | Always reachable from a predictable location |

### Gesture Arbitration
- Classify movement only after a touch slop threshold.
- Compare horizontal and vertical displacement before claiming a gesture.
- A horizontal workspace swipe must not consume an Android edge-back gesture.
- An upward gesture must not conflict with the system Home affordance.
- Support both gesture navigation and three-button navigation seamlessly.

### Navigation State
- Represent the active workspace as a small sealed or enum-like state.
- Keep the Command Center as a separate modal/full-screen state rather than adding it to the horizontal workspace index.
- App relaunch should default to Home unless explicitly configured.

---

## 05. Technical Architecture

### Layer Responsibilities
- **UI**: Declarative screens, components, gestures, system insets.
- **Presentation**: ViewModel + StateFlow, UI state, event handling, lifecycle-aware state.
- **Domain**: Kotlin models and use cases (AppInfo, HomeConfig); platform-independent rules.
- **Data**: Repositories + DataStore/Room; persistent Home configuration and preferences.
- **Platform Adapters**: PackageManager / LauncherApps / System Managers; installed apps, launch intents, battery and audio state.

### Architecture Rules
1. Screens render immutable UI state and emit user events; never query `PackageManager` directly from UI.
2. ViewModels coordinate repositories and expose `StateFlow`.
3. Platform APIs stay behind adapters so Android-specific behavior is isolated and testable.
4. Repositories own data access and persistence boundaries.
5. Avoid introducing heavy database layers until relational queries or large records justify them.
6. Avoid a use-case class for every trivial function; introduce domain use cases when business rules justify them.

---

## 06. Android Platform Integration

### Launcher Registration
Must declare an Activity that handles `ACTION_MAIN` with `CATEGORY_HOME` and `CATEGORY_DEFAULT`. The user selects the launcher through Android's system UI (do not silently attempt to force default launcher).

### Installed-App Discovery & Launch
- Use `PackageManager` or `LauncherApps` APIs to enumerate launchable activities.
- Account for Android package-visibility rules (`QUERY_ALL_PACKAGES` or specific `<queries>`).
- Handle null launch intents, disabled packages, removed packages, and work-profile visibility.
- Do not assume package labels or icons are immutable; updates can change both.

### Battery and Audio Status
- Use `BatteryManager` sticky intent/broadcast for battery percentage.
- Use `AudioManager` ringer mode for Ring / Vibrate / Silent.
- Register observers only while the relevant UI is active; do not run permanent polling services.

### Permissions Policy
The core launcher does not request Internet, location, contacts, calendar, microphone, camera, or accessibility permissions. Optional features request permissions only at the point the user enables them.

---

## 07. Data Model & Persistence

### Core Domain Models
```kotlin
data class AppInfo(
    val packageName: String,
    val activityName: String?,
    val label: String,
    val isSystemApp: Boolean,
    val launchable: Boolean
)

data class HomeConfig(
    val schemaVersion: Int,
    val favoritePackageNames: List<String>,
    val clockFormat: ClockFormat, // SystemDefault | TwelveHour | TwentyFourHour
    val selectedTheme: String,
    val showBattery: Boolean,
    val showAudioMode: Boolean
)
```

### Persistence Principles
- Persist favorites, theme selection, clock preferences, and visibility toggles.
- Store a schema/config version and define migration behavior before changing persisted representation.
- Validate favorite package names at load and launch time because applications can be uninstalled.
- Never store bitmap icons or large metadata in the primary preferences store.

---

## 08. Performance, Privacy & Reliability

### Performance Principles
- Keep disk I/O, package enumeration, and expensive icon processing off the main thread.
- Load the Home shell first; defer App Library indexing and secondary workspace work.
- Use stable keys for app rows and avoid unnecessary layout recompositions.
- Update the clock at minute granularity when seconds are not displayed.
- Avoid permanent background services and frequent polling.
- Measure cold start, warm start, frame timing, allocations, and PSS on physical hardware (Nothing Phone 2a).

### Privacy & Data Minimization
- No analytics SDK, advertising SDK, cloud sync, or remote AI.
- Keep preferences and app metadata local.
- Explain optional permissions in context; provide an off-switch.

### Reliability & Recovery
- The launcher must render a usable Home even if preference loading fails; fallback to safe defaults.
- An invalid favorite must not crash rendering or block other favorites.
- Package changes and process recreation must not corrupt configuration.
- Keep Android system Back behavior functional from every screen.
- Provide a clear settings route; never make the launcher difficult to exit.

---

## 09. Implementation Roadmap & Phases

- **Phase 01 — Foundation**: Native HOME registration, Obsidian OLED theme, large clock/date, favorite app rows, 3 horizontal workspace shells, Command Center shell, battery/audio indicators, persistent configuration.
- **Phase 02 — App Management**: Full app discovery, alphabetical library, search, aliases, favorites, hidden apps, context menu, package-change updates.
- **Phase 03 — Command Center**: Full-screen local search, keyboard auto-open, app/alias search, recents, quick actions, deterministic parser, ranking and history controls.
- **Phase 04 — Widget Deck**: Calendar, optional weather, notes, battery, reorderable utility cards.
- **Phase 05 — Focus Engine**: Work / Study / Sleep / Deep Focus profiles, allowed apps, profile favorites, timer and schedules.
- **Phase 06 — Appearance Studio**: Obsidian, Warm Paper, Midnight Blue, Graphite presets; typography, clock, icon style, spacing, wallpaper controls.
- **Phase 07 — Privacy & Performance**: Permission dashboard, local data controls, export/import, cache management, and profiling.
- **Phase 08 — Release**: Signed APK, debug APK, versioned config, documentation, permission audit, and compatibility report.

---

## 10. Test Matrix & Acceptance Criteria

| Area | Test Cases |
|---|---|
| **Launcher Lifecycle** | Set default Home; cold launch; force-stop/relaunch; process death; switch default away and back |
| **Display** | Small/large font scale; display size; cutout; portrait; dark theme; gesture and 3-button navigation |
| **Gestures** | Left/right workspace swipe; upward Command Center; diagonal movement; edge-back; rapid repeated swipes |
| **Apps** | Launch favorite; uninstall favorite; disable app; package update; no launch activity |
| **Persistence** | Edit favorites/settings; force-stop; reboot; invalid or older config schema |
| **System State** | Battery changes; ringer mode changes; unavailable status; lifecycle pause/resume |
| **Performance** | Cold/warm start; frame timing; package enumeration; icon cache; PSS and allocation profiling |
| **Permissions** | Fresh install; core use with no optional permissions; optional permission denial and revocation |
