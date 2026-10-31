package com.polymath.voidlauncher.model

import android.app.PendingIntent
import android.graphics.drawable.Drawable

/**
 * Representation of an active system notification displayed in the
 * Void Launcher homescreen notification cockpit card.
 */
data class NotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val appIcon: Drawable? = null,
    val title: String,
    val text: String,
    val postTimeMs: Long = System.currentTimeMillis(),
    val isOngoing: Boolean = false,
    val contentIntent: PendingIntent? = null
)
