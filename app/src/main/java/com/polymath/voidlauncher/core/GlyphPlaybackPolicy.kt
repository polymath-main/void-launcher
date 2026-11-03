package com.polymath.voidlauncher.core

/**
 * Pure, JVM-testable glyph playback policy (impl-05 step 5).
 *
 * The old GlyphChoreographer.play() fell back to TASK_SUCCESS for an
 * unknown pattern name — a typo in a caller produced a *success* signal,
 * the worst possible default. The policy fixes the contract:
 * unknown name -> IGNORE_UNKNOWN (logged no-op, never a success signal);
 * same pattern requested while already playing -> DROP_DUPLICATE
 * (overlapping plays no longer interleave their on/off schedules);
 * anything else -> PLAY. stop() clears the playing state so a later
 * request can play again. The Android choreographer owns the actual
 * Handler callbacks; this class owns only the decision, so it is
 * testable without Handler/Looper/GDK stubs.
 */
enum class PlayDecision { PLAY, DROP_DUPLICATE, IGNORE_UNKNOWN }

class GlyphPlaybackPolicy {
    private val known = mutableSetOf<String>()
    private var playing: String? = null

    fun register(name: String) {
        known.add(name.uppercase())
    }

    fun request(name: String): PlayDecision {
        val key = name.uppercase()
        if (key !in known) return PlayDecision.IGNORE_UNKNOWN
        if (playing == key) return PlayDecision.DROP_DUPLICATE
        playing = key
        return PlayDecision.PLAY
    }

    fun stop() {
        playing = null
    }

    fun finish(name: String) {
        if (playing == name.uppercase()) playing = null
    }

    val currentPlaying: String?
        get() = playing
}
