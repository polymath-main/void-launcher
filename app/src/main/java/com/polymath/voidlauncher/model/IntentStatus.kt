package com.polymath.voidlauncher.model

data class IntentStatus(
    val activeTaskTitle: String,
    val taskStatus: String, // "pending", "running", "idle"
    val workspacePath: String,
    val gitBranch: String,
    val astNodeCount: Int,
    val isDaemonAlive: Boolean,
    val identityMode: String // "VOID" or "MAIN"
)
