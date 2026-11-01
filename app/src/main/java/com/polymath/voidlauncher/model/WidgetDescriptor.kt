package com.polymath.voidlauncher.model

/**
 * Descriptor for dynamic plugin widget cards on the cockpit homescreen (F-04).
 */
data class WidgetDescriptor(
    val id: String,
    val title: String,
    val type: String = "SCRIPT",               // SCRIPT | HTTP | STATIC
    val source: String,                        // Shell command or HTTP URL or static text
    val refreshIntervalMs: Long = 5000L,       // Refresh cadence
    val heightDp: Int = 48,                    // Height on homescreen
    val actionOnClick: String = "",            // Optional command or package to launch
    val isEnabled: Boolean = true
)
