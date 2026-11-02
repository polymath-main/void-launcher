# Blueprint 05 — Telemetry, Power Profile, Thermal Guardian & Nothing Glyph Engine

## 1. Domain Scope
Monitors real-time device vitals on Android 16 (without root), adapts polling frequency to battery/thermal conditions, enforces the 39.0 °C thermal envelope, and drives Nothing Phone (2a) Glyph LEDs with haptic fallback.

## 2. Micro-Service & Module Inventory

| Module | File | Responsibility |
|---|---|---|
| `TelemetryMonitor` | [`TelemetryMonitor.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/TelemetryMonitor.kt) | Emits `Flow<TelemetrySnapshot>` from `Intent.ACTION_BATTERY_CHANGED` (level %, tenths-°C temperature, charging state) and `ActivityManager.MemoryInfo` (RAM used/total MB). |
| `PowerProfileAdaptor` | [`PowerProfileAdaptor.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/PowerProfileAdaptor.kt) | Classifies system state into `PERFORMANCE` (1.5s), `BALANCED` (2.5s), `ECO` (5s), or `CRITICAL` (8s) polling intervals. |
| `ThermalGuardian` | [`ThermalGuardian.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/ThermalGuardian.kt) | Enforces `thermal_limit_celsius` (default `39.0 °C`) with `2.0 °C` hysteresis; cancels running background tasks in `VoidProcessManager` and dispatches configured kill commands via `ShellPort`. |
| `GlyphChoreographer` | [`GlyphChoreographer.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/GlyphChoreographer.kt) | Reflective bridge to Nothing GDK (`com.nothing.ketchum.GlyphManager` / `GlyphFrame.Builder`) with timed multi-channel LED sequences and `VibrationEffect` haptic fallback. |
| `NothingGlyphManager` | [`NothingGlyphManager.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/NothingGlyphManager.kt) | High-level hardware facade detecting Nothing devices (`Build.MANUFACTURER`) and delegating thermal/intent pulses to `GlyphChoreographer`. |

## 3. Thermal Hysteresis State Machine
- **Normal (`isThrottlingActive = false`)**: When `batteryTempC > limitC` (`39.0 °C`), transitions to **Throttling**, cancels active `VoidProcessManager` tasks, plays `THERMAL_WARNING` Glyph pattern, and alerts user.
- **Throttling (`isThrottlingActive = true`)**: Remains active until `batteryTempC <= limitC - hysteresisC` (`37.0 °C`), preventing rapid oscillation.
