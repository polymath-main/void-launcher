# Blueprint 05 — Telemetry, Power Profile, Thermal Guardian & Nothing Glyph Engine

## 1. Domain Scope
Monitors real-time device vitals on Android 16 (without root), adapts polling frequency to battery/thermal conditions, enforces the thermal envelope, and drives Nothing Phone (2a) Glyph LEDs with haptic fallback.

**Temperature honesty (impl-05 step 1):** the only temperature in the system is
**battery temperature** from the sticky `ACTION_BATTERY_CHANGED` intent
(`BatteryManager.EXTRA_TEMPERATURE`, tenths of a degree). It is a **proxy for
device heat, not a SoC / CPU / thermal-zone reading**. The launcher never reads
`/sys/class/thermal`, never uses `PowerManager` thermal APIs as a guard input,
and has no root path. Do not present battery temperature as chip temperature in
UI copy, docs, or `:power` output — `:power` labels it "battery temp" explicitly.
An unknown battery (percent -1, no battery intent) is reported **unsafe**, never
safe: a missing sensor must not read as "perfectly safe".

## 2. Micro-Service & Module Inventory

| Module | File | Responsibility |
|---|---|---|
| `TelemetryMonitor` | `app/src/main/java/com/polymath/voidlauncher/core/TelemetryMonitor.kt` | Emits `Flow<TelemetrySnapshot>` from `Intent.ACTION_BATTERY_CHANGED` (level %, battery temperature, charging state) and `ActivityManager.MemoryInfo` (RAM used/total MB). `captureSnapshot(thermalLimitC)` computes safety with `TelemetrySnapshot.isSafeAt(limitC)`. |
| `PowerProfileAdaptor` | `app/src/main/java/com/polymath/voidlauncher/core/PowerProfileAdaptor.kt` | Classifies system state into `PERFORMANCE` (**1000 ms**), `BALANCED` (**1500 ms**), `ECO` (**4000 ms**), or `CRITICAL` (**8000 ms**) polling intervals. These are the code values — any doc claiming 1.5/2.5/5/8 s is wrong. `shouldRenderNonEssentialWidgets()` returns false only at CRITICAL and gates widget-deck SCRIPT refresh (impl-05 step 3). |
| `ThermalGuardian` | `app/src/main/java/com/polymath/voidlauncher/core/ThermalGuardian.kt` | Enforces `thermal_limit_celsius` (default `39.0 °C`) with `2.0 °C` hysteresis; cancels running background tasks in `VoidProcessManager` **by handle** — since impl-01 the registry holds real `CancelHandle`s, so cancellation stops the actual process (for tasks dispatched through the registry), not just a status flag. Configured `thermal_kill_commands` are **logged and ignored, never executed** — there is no shell kill path and no `pkill` anywhere in the app. |
| `GlyphChoreographer` | `app/src/main/java/com/polymath/voidlauncher/core/GlyphChoreographer.kt` | Reflective bridge to Nothing GDK (`com.nothing.ketchum.GlyphManager` / `GlyphFrame.Builder`) with timed multi-channel LED sequences and `VibrationEffect` haptic fallback. Playback is governed by the pure `GlyphPlaybackPolicy` seam: an unknown pattern name is a logged no-op (never a success fallback), overlapping plays coalesce, and `stop()` cancels pending callbacks. Real LED behaviour on a Nothing device is **UNKNOWN until a device verification pass exists** — no GDK artifact is in the repo. |
| `NothingGlyphManager` | `app/src/main/java/com/polymath/voidlauncher/core/NothingGlyphManager.kt` | High-level hardware facade delegating thermal/task pulses to the choreographer. Hardware detection is owned by the shared `NothingHardware` helper (single place, not duplicated per class). |

## 3. Thermal Hysteresis State Machine
- **Normal (`isThrottled = false`)**: When `batteryTempC >= limitC` (`39.0 °C`), transitions to **Throttling**, cancels active `VoidProcessManager` tasks by handle, and fires `onThrottleTriggered(tempC, summary)` — the **Activity** wires that callback to the `THERMAL_WARNING` glyph pulse, a Toast, and the persistent HUD `⚠ THROTTLED` token. The guardian itself plays no glyph and shows no Toast.
- **Throttling (`isThrottled = true`)**: Remains active until `batteryTempC <= limitC - hysteresisC` (`37.0 °C`), preventing rapid oscillation, then fires `onThrottleCleared(tempC)`. `reset()` fires `onThrottleCleared` too, but only if the guard was actually throttled; resetting an unthrottled guard is a silent no-op.
- Thermally cancelled task ids are recorded on the guardian (`lastTripCancelledTaskIds`, cumulative `thermallyCancelledTaskIds`) and surface in `:tasks` / `:status` as `cancelled` — **never auto-retried**. Re-queue is manual only.
- The guard never depends on HUD visibility: thermal evaluation runs on every telemetry tick whether or not the HUD/card is shown.
