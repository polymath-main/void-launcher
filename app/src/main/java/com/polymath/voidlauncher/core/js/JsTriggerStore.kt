package com.polymath.voidlauncher.core.js

import java.io.File

/**
 * JS macro trigger attachments (impl-04 step 7c, PARTIAL — read the caveat).
 *
 * A macro runs on a trigger ONLY if the user explicitly attached
 * that trigger to that macro (plan gate: no auto-run on an
 * unattached trigger). Attachments are stored as one JSON-ish
 * line per macro under `<jsMacrosDir>/.void-triggers/<macro>.txt`:
 * trigger names, one per line, e.g. `manual`, `scheduled`,
 * `notification:<filter>`, `app:<package>`.
 *
 * CAVEAT: this store records attachments and [JsMacroRunner] can
 * fire an attached macro when explicitly asked (`:void-js fire`
 * or a manual run with `--trigger`). There is NO daemon in this
 * codebase (no WorkManager surface, no notification/app listener
 * hook for JS macros), so scheduled/notification/app triggers do
 * NOT fire autonomously yet. Claiming they fire would be false.
 * Autonomous firing is an area-6 daemon follow-up: the daemon
 * must call `fireAttached(event)` here, and only for attachments
 * in this store. Until then, attached-but-unfired triggers are
 * listed, never silently run.
 */
object JsTriggerStore {

    fun attach(jsMacrosDir: String, macroName: String, trigger: String): Boolean {
        val clean = trigger.trim()
        if (clean.isEmpty()) return false
        if (com.polymath.voidlauncher.core.MacroName.validate(macroName) != null) return false
        val triggers = list(jsMacrosDir, macroName).toMutableSet()
        if (!triggers.add(clean)) return false
        write(jsMacrosDir, macroName, triggers)
        return true
    }

    fun detach(jsMacrosDir: String, macroName: String, trigger: String): Boolean {
        val triggers = list(jsMacrosDir, macroName).toMutableSet()
        if (!triggers.remove(trigger.trim())) return false
        write(jsMacrosDir, macroName, triggers)
        return true
    }

    fun list(jsMacrosDir: String, macroName: String): List<String> {
        val file = triggerFile(jsMacrosDir, macroName) ?: return emptyList()
        if (!file.exists()) return emptyList()
        return file.readLines().map { it.trim() }.filter { it.isNotEmpty() }.sorted()
    }

    fun isAttached(jsMacrosDir: String, macroName: String, trigger: String): Boolean =
        list(jsMacrosDir, macroName).contains(trigger.trim())

    private fun triggerFile(jsMacrosDir: String, macroName: String): File? {
        if (com.polymath.voidlauncher.core.MacroName.validate(macroName) != null) return null
        val dir = File(jsMacrosDir, ".void-triggers")
        dir.mkdirs()
        return File(dir, "$macroName.txt")
    }

    private fun write(jsMacrosDir: String, macroName: String, triggers: Set<String>) {
        val file = triggerFile(jsMacrosDir, macroName) ?: return
        file.writeText(triggers.sorted().joinToString("\n"))
    }
}
