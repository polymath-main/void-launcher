package com.polymath.voidlauncher.model

/**
 * Dispatched agent task model tracked in the cockpit (F-08).
 */
data class AgentTask(
    val id: String,
    val title: String,
    val command: String,
    val status: String = "pending",            // pending | running | completed | failed | cancelled
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val outputPreview: String = "",
    val exitCode: Int = 0
)
