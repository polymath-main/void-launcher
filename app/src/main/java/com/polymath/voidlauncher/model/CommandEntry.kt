package com.polymath.voidlauncher.model

/**
 * Historical command or saved snippet record for OmniScript history (F-09).
 */
data class CommandEntry(
    val id: Long = 0,
    val commandText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val useCount: Int = 1,
    val isSnippet: Boolean = false,
    val snippetAlias: String = ""
)
