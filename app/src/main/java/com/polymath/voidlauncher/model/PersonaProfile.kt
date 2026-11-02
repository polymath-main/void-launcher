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
    val workspacePath: String = VoidPaths.WORKSPACE,
    val nodeosDbPath: String = VoidPaths.NODEOS_DB,
    val workflowJsonPath: String = VoidPaths.WORKFLOW_JSON,
    val customDockSlots: List<DockSlot>? = null,
    val glyphPatternName: String = "DEFAULT"
)
