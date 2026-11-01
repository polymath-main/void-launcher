package com.polymath.voidlauncher.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.polymath.voidlauncher.model.PersonaProfile
import org.json.JSONObject
import java.io.File

/**
 * Smart Persona Engine (F-02).
 *
 * Manages multiple named identity contexts beyond the binary VOID / MAIN.
 */
class PersonaManager(private val context: Context) {

    companion object {
        private const val TAG = "PersonaManager"
        private const val PREFS_NAME = "void_persona_prefs"
        private const val KEY_ACTIVE_PERSONA = "active_persona_id"
        private const val EXTERNAL_PERSONAS_DIR = "/data/data/com.termux/files/home/.voidlauncher/personas"

        val DEFAULT_VOID = PersonaProfile(
            id = "void",
            label = "[VOID]",
            subtitle = "Runtimes & Compilers",
            accentColor = "#E11D48",
            secondaryColor = "#06B6D4",
            workspacePath = "/data/data/com.termux/files/home/Projects",
            glyphPatternName = "VOID_ACCENT"
        )

        val DEFAULT_MAIN = PersonaProfile(
            id = "main",
            label = "[MAIN]",
            subtitle = "SaaS & Production",
            accentColor = "#06B6D4",
            secondaryColor = "#10B981",
            workspacePath = "/data/data/com.termux/files/home/Projects",
            glyphPatternName = "MAIN_ACCENT"
        )

        val DEFAULT_STILL = PersonaProfile(
            id = "still",
            label = "[STILL]",
            subtitle = "Obsidian OLED Minimalist",
            accentColor = "#007AFF",
            secondaryColor = "#8E8E93",
            workspacePath = "/data/data/com.termux/files/home/Projects",
            glyphPatternName = "FOCUS_MODE_ON"
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val memoryProfiles = mutableMapOf<String, PersonaProfile>()

    init {
        registerProfile(DEFAULT_VOID)
        registerProfile(DEFAULT_MAIN)
        registerProfile(DEFAULT_STILL)
        loadExternalProfiles()
    }

    fun registerProfile(profile: PersonaProfile) {
        memoryProfiles[profile.id.lowercase()] = profile
    }

    fun registerConfigProfiles(profiles: List<PersonaProfile>) {
        for (p in profiles) {
            registerProfile(p)
        }
    }

    fun getAllPersonas(): List<PersonaProfile> {
        return memoryProfiles.values.toList()
    }

    fun getActivePersona(): PersonaProfile {
        val activeId = prefs.getString(KEY_ACTIVE_PERSONA, "void") ?: "void"
        return memoryProfiles[activeId.lowercase()] ?: DEFAULT_VOID
    }

    fun selectPersona(id: String): PersonaProfile? {
        val target = memoryProfiles[id.lowercase()] ?: return null
        prefs.edit().putString(KEY_ACTIVE_PERSONA, target.id).apply()
        Log.i(TAG, "Switched active persona to ${target.id}")
        return target
    }

    fun cyclePersona(): PersonaProfile {
        val all = memoryProfiles.values.toList()
        if (all.isEmpty()) return DEFAULT_VOID

        val current = getActivePersona()
        val currentIndex = all.indexOfFirst { it.id.equals(current.id, ignoreCase = true) }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % all.size else 0
        val nextPersona = all[nextIndex]

        prefs.edit().putString(KEY_ACTIVE_PERSONA, nextPersona.id).apply()
        Log.i(TAG, "Cycled active persona to ${nextPersona.id}")
        return nextPersona
    }

    private fun loadExternalProfiles() {
        try {
            val dir = File(EXTERNAL_PERSONAS_DIR)
            if (!dir.exists() || !dir.isDirectory) return

            dir.listFiles { f -> f.extension == "json" }?.forEach { file ->
                try {
                    val json = JSONObject(file.readText())
                    val id = json.optString("id", file.nameWithoutExtension)
                    val profile = PersonaProfile(
                        id = id,
                        label = json.optString("label", "[$id]"),
                        subtitle = json.optString("subtitle", ""),
                        accentColor = json.optString("accent", "#E11D48"),
                        secondaryColor = json.optString("secondary", "#06B6D4"),
                        surfaceColor = json.optString("surface", "#080808"),
                        workspacePath = json.optString("workspace", "/data/data/com.termux/files/home/Projects"),
                        glyphPatternName = json.optString("glyph_pattern", "DEFAULT")
                    )
                    registerProfile(profile)
                } catch (e: Exception) {
                    Log.w(TAG, "Error loading persona from ${file.name}: ${e.message}")
                }
            }
        } catch (_: Exception) {}
    }
}
