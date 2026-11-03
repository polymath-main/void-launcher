package com.polymath.voidlauncher.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.polymath.voidlauncher.model.TelemetrySnapshot
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelemetryMonitor(private val context: Context) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * Emits telemetry snapshots forever.
     *
     * @param pollIntervalMs fixed cadence used when [intervalProvider] is null.
     * @param intervalProvider optional dynamic cadence resolver (adaptive power profile).
     *        When supplied it is consulted before every delay, so the cadence can
     *        change at runtime without restarting the flow.
     */
    fun monitorTelemetry(
        pollIntervalMs: Long = 1000L,
        intervalProvider: (() -> Long)? = null
    ): Flow<TelemetrySnapshot> = flow {
        while (currentCoroutineContext().isActive) {
            val snapshot = captureSnapshot()
            emit(snapshot)
            val delayMs = (intervalProvider?.invoke() ?: pollIntervalMs).coerceAtLeast(200L)
            delay(delayMs)
        }
    }

    /**
     * One-shot snapshot. [thermalLimitC] is the configured thermal limit
     * (default 39.0 °C); safety is computed with [TelemetrySnapshot.isSafeAt],
     * so an unknown battery (percent -1) is reported unsafe, never safe.
     * The temperature is BATTERY temperature from the sticky battery intent —
     * a proxy for device heat, not a SoC / thermal-zone reading.
     */
    fun captureSnapshot(thermalLimitC: Float = 39.0f): TelemetrySnapshot {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        var batteryPct = -1
        var tempC = 0.0f
        var isCharging = false

        if (batteryIntent != null) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                batteryPct = ((level.toFloat() / scale.toFloat()) * 100).toInt()
            }
            
            // Battery temperature is delivered in tenths of a degree Celsius
            val tempTenths = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            tempC = tempTenths / 10.0f

            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }

        // RAM Metrics via ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val usedMb = totalMb - availMb

        val nowFormatted = timeFormat.format(Date())
        // Unknown battery (batteryPct stays -1 when the intent is null) is
        // unsafe by construction in isSafeAt — never "perfectly safe".
        val isThermalSafe = batteryPct >= 0 && tempC <= thermalLimitC

        return TelemetrySnapshot(
            timestampFormatted = nowFormatted,
            batteryPercent = batteryPct,
            batteryTemperatureC = tempC,
            isCharging = isCharging,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            isThermalSafe = isThermalSafe
        )
    }
}
