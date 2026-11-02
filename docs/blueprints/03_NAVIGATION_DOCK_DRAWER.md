# Blueprint 03 — Navigation, Dock Slots, App Drawer & VFS Explorer

## 1. Domain Scope
Covers application discovery, Bare-Integer `[1..7]` fast routing, interactive Dock Slot customization, the 85%-width Right App Drawer with A–Z scrubbing, and the built-in VFS File Explorer.

## 2. Micro-Service & Module Inventory

| Module | File | Responsibility |
|---|---|---|
| `DockSlotMicroService` | [`DockSlotMicroService.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/services/DockSlotMicroService.kt) | Interactive dialogs to bind any installed app or built-in action to Dock Slots `[1..7]`, rename slot labels, or reset to defaults. |
| `AppActionsMicroService` | [`AppActionsMicroService.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/services/AppActionsMicroService.kt) | Handles app launch, system App Info, Uninstall intent, Pin Home Shortcut (`ShortcutManager`), and the 5-item long-press menu. |
| `FileOperationsMicroService` | [`FileOperationsMicroService.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/services/FileOperationsMicroService.kt) | File context menu: Set Image as Wallpaper, Open in VoidTerm, Text Preview, Copy Path, Rename, Delete, and Create Folder. |
| `AppIndexer` | [`AppIndexer.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/AppIndexer.kt) | Queries `CATEGORY_LAUNCHER` activities, caches icons, builds A–Z section offsets, and performs prefix/substring fuzzy matching. |
| `BareIntegerRouter` / `VoidAction` | [`BareIntegerRouter.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/BareIntegerRouter.kt) | Maps digits `1..7` and `DockSlot` descriptors to `VoidAction` (`OPEN_VOIDTERM`, `OPEN_FILES`, `OPEN_DRAWER`, `OPEN_BROWSER`, `OPEN_DIALER`, `OPEN_SETTINGS`, `LAUNCH_PACKAGE`, `RUN_VOID_COMMAND`). |
| `CompoundGestureEngine` | [`CompoundGestureEngine.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/CompoundGestureEngine.kt) | Recognizes single-finger swipes, double-tap, long-press, two-finger swipes, pinch-in/out, and 28dp left/right/bottom edge swipes. |

## 3. Home Modes & Spatial Layout
- **COCKPIT**: Telemetry HUD bar + Intent line + Notification line + `[1..7]` Dock + collapsible `[123]` keypad + Omni-Bar.
- **STILL**: 20sp clock + 14sp date + 12sp vitals + Slots `1, 3, 5` as 48dp borderless favorite rows + Omni-Bar.
- **MINIMAL**: Distraction-free Omni-Bar + Dock only.
- **FILES**: Built-in VFS Explorer with `~Void`, `Projects`, `Downloads`, and `/sdcard` root jump chips.
