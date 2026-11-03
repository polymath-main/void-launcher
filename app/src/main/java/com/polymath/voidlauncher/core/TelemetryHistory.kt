package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.model.TelemetrySnapshot

/**
 * Pure ring buffer of recent telemetry snapshots (impl-05 step 6c).
 * Bounded (default last 60) so a :graph-style temperature trend can be
 * built later without unbounded memory growth. Display/history only —
 * nothing in this buffer feeds the thermal guard.
 */
class TelemetryHistory(private val capacity: Int = 60) {
    private val buffer = ArrayDeque<TelemetrySnapshot>()

    fun add(snapshot: TelemetrySnapshot) {
        buffer.addLast(snapshot)
        while (buffer.size > capacity) buffer.removeFirst()
    }

    fun snapshots(): List<TelemetrySnapshot> = buffer.toList()

    val size: Int
        get() = buffer.size

    /** Latest battery temperature, or null when no snapshot exists yet. */
    fun latestTempC(): Float? = buffer.lastOrNull()?.batteryTemperatureC
}
