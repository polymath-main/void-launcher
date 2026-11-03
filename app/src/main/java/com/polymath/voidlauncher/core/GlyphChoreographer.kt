package com.polymath.voidlauncher.core

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.polymath.voidlauncher.model.GlyphPattern
import org.json.JSONArray
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Glyph Choreographer (F-03).
 *
 * Directs Nothing Phone (2a) rear Glyph LED channels with timed animation sequences.
 *
 * The Nothing Glyph Developer Kit (`com.nothing.ketchum`) is distributed outside public
 * Maven repositories, so it is bridged reflectively: when the GDK classes are present on
 * device, real `turnOn`/`turnOff` LED calls are issued; otherwise playback degrades to a
 * synchronized haptic waveform so the signal is never lost.
 */
class GlyphChoreographer(private val context: Context) {

    companion object {
        private const val TAG = "GlyphChoreographer"

        private const val GDK_MANAGER_CLASS = "com.nothing.ketchum.GlyphManager"
        private const val GDK_CALLBACK_CLASS = "com.nothing.ketchum.GlyphManager\$Callback"
        private const val GDK_GLYPH_CLASS = "com.nothing.ketchum.Glyph"

        val PATTERN_THERMAL = GlyphPattern("THERMAL_WARNING", listOf(1, 2, 3), periodMs = 200, cycles = 4, curve = "STROBE")
        val PATTERN_SUCCESS = GlyphPattern("TASK_SUCCESS", listOf(1, 2), periodMs = 350, cycles = 2, curve = "SINE")
        val PATTERN_FAIL    = GlyphPattern("TASK_FAIL", listOf(1), periodMs = 150, cycles = 3, curve = "PULSE")
        val PATTERN_FOCUS   = GlyphPattern("FOCUS_MODE_ON", listOf(2, 3), periodMs = 600, cycles = 1, curve = "LINEAR")
        val PATTERN_RUNNING = GlyphPattern("AGENT_RUNNING", listOf(1, 2), periodMs = 500, cycles = 2, curve = "SINE")
    }

    private var isNothingPhone = false
    private val handler = Handler(Looper.getMainLooper())

    /** Master gate for all glyph/haptic feedback (config: `enable_glyph_pulse`). */
    var enabled: Boolean = true

    /** True once a real GDK LED sequence has been issued successfully. */
    var isGdkActive: Boolean = false
        private set

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val patternRegistry = mutableMapOf<String, GlyphPattern>()
    private val playbackPolicy = GlyphPlaybackPolicy()
    private val pendingCallbacks = mutableListOf<Runnable>()

    init {
        detectHardware()
        registerBuiltInPatterns()
    }

    private fun detectHardware() {
        // impl-05 step 5: detection lives in NothingHardware now (was
        // duplicated in NothingGlyphManager). This answers "Nothing phone",
        // not "GDK present" — see NothingHardware docs.
        isNothingPhone = NothingHardware.isNothingPhone()
        Log.d(TAG, "Hardware status: isNothingPhone=$isNothingPhone (model=${Build.MODEL})")
    }

    private fun registerBuiltInPatterns() {
        for (name in listOf("THERMAL_WARNING", "TASK_SUCCESS", "TASK_FAIL", "FOCUS_MODE_ON", "AGENT_RUNNING")) {
            playbackPolicy.register(name)
        }
        patternRegistry["THERMAL_WARNING"] = PATTERN_THERMAL
        patternRegistry["TASK_SUCCESS"] = PATTERN_SUCCESS
        patternRegistry["TASK_FAIL"] = PATTERN_FAIL
        patternRegistry["FOCUS_MODE_ON"] = PATTERN_FOCUS
        patternRegistry["AGENT_RUNNING"] = PATTERN_RUNNING
    }

    fun loadCustomPatterns(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "")
                if (name.isEmpty()) continue
                val channels = mutableListOf<Int>()
                val chArr = obj.optJSONArray("channels")
                if (chArr != null) {
                    for (j in 0 until chArr.length()) channels.add(chArr.optInt(j, 1))
                } else {
                    channels.add(1)
                }
                playbackPolicy.register(name)
                patternRegistry[name.uppercase()] = GlyphPattern(
                    name = name,
                    channels = channels,
                    periodMs = obj.optLong("period_ms", 400L),
                    cycles = obj.optInt("cycles", 1),
                    brightnessPercent = obj.optInt("brightness_percent", 100),
                    curve = obj.optString("curve", "SINE")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load custom glyph patterns: ${e.message}")
        }
    }

    /**
     * Plays the named pattern on Nothing Glyph LEDs (or falls back to haptics).
     */
    /**
     * impl-05 step 5: unknown pattern names are a logged no-op — the old
     * silent fallback to TASK_SUCCESS meant a caller typo produced a
     * success signal. Duplicate plays while a pattern is already playing
     * are dropped (no interleaved on/off schedules). Use stop() to cancel
     * a playing pattern; note the policy tracks the decision, and the
     * Handler callbacks are tracked in pendingCallbacks for stop().
     */
    fun play(patternName: String) {
        when (playbackPolicy.request(patternName)) {
            PlayDecision.IGNORE_UNKNOWN -> {
                Log.w(TAG, "Unknown glyph pattern '$patternName' — ignoring (never falls back to TASK_SUCCESS)")
                return
            }
            PlayDecision.DROP_DUPLICATE -> {
                Log.d(TAG, "Glyph pattern '$patternName' already playing — dropping duplicate")
                return
            }
            PlayDecision.PLAY -> { /* fall through */ }
        }
        val pattern = patternRegistry[patternName.uppercase()] ?: return
        playPattern(pattern)
    }

    /** Cancels any pending GDK on/off callbacks and clears playback state. */
    fun stop() {
        for (cb in pendingCallbacks) handler.removeCallbacks(cb)
        pendingCallbacks.clear()
        playbackPolicy.stop()
    }

    fun playPattern(pattern: GlyphPattern) {
        if (!enabled) {
            Log.d(TAG, "Glyph playback suppressed (enable_glyph_pulse=false)")
            return
        }
        Log.i(TAG, "Playing Glyph pattern '${pattern.name}' (channels=${pattern.channels}, cycles=${pattern.cycles})")
        if (isNothingPhone) {
            executeNothingGlyphSequence(pattern)
        } else {
            fallbackHapticPulse(pattern)
        }
    }

    // ---- Nothing GDK reflective bridge --------------------------------------

    private fun executeNothingGlyphSequence(pattern: GlyphPattern) {
        val managerCls = classForName(GDK_MANAGER_CLASS)
        if (managerCls == null) {
            fallbackHapticPulse(pattern)
            return
        }
        val instance = resolveGlyphInstance(managerCls)
        if (instance == null) {
            fallbackHapticPulse(pattern)
            return
        }

        // Bind the GDK service connection (async; we proceed optimistically).
        initGdkService(managerCls, instance)

        val turnOn = methodOrNull(managerCls, "turnOn", Integer.TYPE)
        val turnOff = methodOrNull(managerCls, "turnOff", Integer.TYPE)
        if (turnOn == null || turnOff == null) {
            Log.d(TAG, "GDK present but turnOn/turnOff unavailable — using haptics")
            fallbackHapticPulse(pattern)
            return
        }

        try {
            methodOrNull(managerCls, "openSession")?.invoke(instance)
            val cycles = if (pattern.cycles <= 0) 6 else pattern.cycles
            val period = pattern.periodMs.coerceIn(50L, 5_000L)
            val codes = pattern.channels.map { resolveChannelCode(it) }
            val closeSession = methodOrNull(managerCls, "closeSession")

            for (c in 0 until cycles) {
                val startAt = c * period * 2
                val onCb = Runnable { invokeQuietly(turnOn, instance, codes) }
                val offCb = Runnable {
                    invokeQuietly(turnOff, instance, codes)
                    if (c == cycles - 1) {
                        if (closeSession != null) invokeQuietly(closeSession, instance, emptyList())
                    }
                    playbackPolicy.finish(pattern.name)
                }
                pendingCallbacks.add(onCb)
                pendingCallbacks.add(offCb)
                handler.postDelayed(onCb, startAt)
                handler.postDelayed(offCb, startAt + period)
            }

            isGdkActive = true
            Log.i(TAG, "GDK LED sequence dispatched for '${pattern.name}' (cycles=$cycles, period=${period}ms)")
        } catch (e: Exception) {
            isGdkActive = false
            Log.w(TAG, "GDK LED sequence failed (${e.message}) — using haptics")
            fallbackHapticPulse(pattern)
        }
    }

    private fun resolveGlyphInstance(managerCls: Class<*>): Any? {
        return try {
            val withContext = methodOrNull(managerCls, "getInstance", Context::class.java)
            if (withContext != null) withContext.invoke(null, context)
            else methodOrNull(managerCls, "getInstance")?.invoke(null)
        } catch (e: Exception) {
            Log.d(TAG, "GDK getInstance failed: ${e.message}")
            null
        }
    }

    private fun initGdkService(managerCls: Class<*>, instance: Any) {
        try {
            val callbackCls = classForName(GDK_CALLBACK_CLASS) ?: return
            val proxy = Proxy.newProxyInstance(callbackCls.classLoader, arrayOf(callbackCls)) { _, _, _ -> null }
            methodOrNull(managerCls, "init", callbackCls)?.invoke(instance, proxy)
        } catch (e: Exception) {
            Log.d(TAG, "GDK init handshake skipped: ${e.message}")
        }
    }

    private fun resolveChannelCode(channel: Int): Int {
        val glyphCls = classForName(GDK_GLYPH_CLASS) ?: return channel
        return try {
            glyphCls.getField("Code_$channel").getInt(null)
        } catch (_: Exception) {
            channel
        }
    }

    private fun invokeQuietly(method: Method, target: Any, codes: List<Int>) {
        if (codes.isEmpty()) {
            try {
                method.invoke(target)
            } catch (_: Exception) {
            }
            return
        }
        for (code in codes) {
            try {
                method.invoke(target, code)
            } catch (_: Exception) {
            }
        }
    }

    private fun classForName(name: String): Class<*>? = try {
        Class.forName(name)
    } catch (_: Exception) {
        null
    }

    private fun methodOrNull(cls: Class<*>, name: String, vararg params: Class<*>): Method? = try {
        cls.getMethod(name, *params)
    } catch (_: Exception) {
        null
    }

    // ---- Haptic fallback ----------------------------------------------------

    private fun fallbackHapticPulse(pattern: GlyphPattern) {
        if (vibrator == null || !vibrator!!.hasVibrator()) return
        // impl-05 step 5: cycles + brightness now honoured on the haptic
        // path (previously one fixed waveform per curve). Period remains a
        // GDK-path parameter — see GlyphHapticWaveform docs.
        val (timings, amplitudes) = GlyphHapticWaveform.build(pattern)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(timings, -1)
            }
        } catch (_: Exception) {
        }
    }
}
