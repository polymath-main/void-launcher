package com.polymath.voidlauncher.model

import android.app.PendingIntent
import android.graphics.drawable.Drawable

/**
 * Representation of an active system notification displayed in the
 * Void Launcher homescreen notification cockpit card.
 */
/** One tap-only action from the notification itself (impl-06 step 10). */
data class NotifAction(
    val title: String,
    val intent: PendingIntent?
)

data class NotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val appIcon: Drawable? = null,
    val title: String,
    val text: String,
    val postTimeMs: Long = System.currentTimeMillis(),
    val isOngoing: Boolean = false,
    val contentIntent: PendingIntent? = null,
    // impl-06 step 10: the notification's own actions, mapped by the
    // listener. Rendered as taps only — never fired automatically.
    val actions: List<NotifAction> = emptyList()
)
