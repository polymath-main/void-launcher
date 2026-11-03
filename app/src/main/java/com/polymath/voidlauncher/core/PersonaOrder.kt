package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.model.DockSlot
import com.polymath.voidlauncher.model.PersonaProfile

/**
 * impl-06 step 3 — pinned persona cycle order + stored-field application.
 *
 * The cycle order is pinned, not registration-accidental:
 *   1. built-ins in the fixed order void, main, still, agent, client
 *      (any other built-in id sorts after these, in input order)
 *   2. config profiles, in config order
 *   3. external JSON profiles, in filename order (the caller passes
 *      them already filename-sorted; this function preserves order)
 * Duplicated ids (case-insensitive) keep their FIRST occurrence.
 *
 * Before this step the live order was built-ins, external, config —
 * because PersonaManager registered external profiles before the
 * Activity registered config profiles — so a config profile could
 * never be relied on to cycle after a built-in.
 */
object PersonaOrder {

    private val BUILTIN_ORDER = listOf("void", "main", "still", "agent", "client")

    fun order(
        builtins: List<PersonaProfile>,
        configProfiles: List<PersonaProfile>,
        externalProfiles: List<PersonaProfile>
    ): List<PersonaProfile> {
        val sortedBuiltins = builtins.sortedWith(
            compareBy<PersonaProfile> { p ->
                val idx = BUILTIN_ORDER.indexOf(p.id.lowercase())
                if (idx >= 0) idx else BUILTIN_ORDER.size
            }.thenBy { builtins.indexOf(it) }
        )
        val seen = mutableSetOf<String>()
        val out = mutableListOf<PersonaProfile>()
        for (p in sortedBuiltins + configProfiles + externalProfiles) {
            if (seen.add(p.id.lowercase())) out.add(p)
        }
        return out
    }

    /** A persona's own dock slots win over the config dock; null = config dock. */
    fun effectiveDockSlots(persona: PersonaProfile, configDockSlots: List<DockSlot>): List<DockSlot> =
        persona.customDockSlots ?: configDockSlots

    /** A persona's workspacePath wins as the files-browser default; blank = keep current. */
    fun effectiveFilesPath(persona: PersonaProfile, currentPath: String): String =
        if (persona.workspacePath.isNotBlank()) persona.workspacePath else currentPath
}
