package com.polymath.voidlauncher.model

data class IntentStatus(
    val activeTaskTitle: String,
    val taskStatus: String, // "pending", "running", "idle"
    val workspacePath: String,
    /**
     * impl-06 step 1: retained for model compatibility, but NEVER a real
     * branch — legacy code hardcoded "main" here and the HUD displayed it
     * as if it were. The HUD no longer displays this field; it shows
     * "git: —". Do not fill it from a repo without a real git reader.
     */
    val gitBranch: String = "",
    val astNodeCount: Int,
    val isDaemonAlive: Boolean,
    val identityMode: String, // "VOID" or "MAIN"
    /** "exact" (queryTopology), "estimate" (file-size/3), or "none". */
    val astProvenance: String = "none",
    /** False when legacy is on but no workflow.json exists at all. */
    val workflowPresent: Boolean = true
)
