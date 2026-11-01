package com.polymath.voidlauncher.model

/**
 * Named identity persona profile (F-02).
 * Enables multi-context switching beyond VOID / MAIN.
 */
data class PersonaProfile(
    val id: String,
    val label: String,
    val subtitle: String = "",
    val accentColor: String = "#E11D48",
    val secondaryColor: String = "#06B6D4",
    val surfaceColor: String = "#080808",
    val workspacePath: String = "/data/data/com.termux/files/home/Projects",
    val nodeosDbPath: String = "/data/data/com.termux/files/home/Projects/agy_nodeos.db",
    val workflowJsonPath: String = "/data/data/com.termux/files/home/Projects/workflow.json",
    val customDockSlots: List<DockSlot>? = null,
    val glyphPatternName: String = "DEFAULT"
)
