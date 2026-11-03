package com.polymath.voidlauncher.core

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.polymath.voidlauncher.model.NotificationItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoidNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "VoidNotificationListener"

        private val _notificationsFlow = MutableStateFlow<List<NotificationItem>>(emptyList())
        val notificationsFlow: StateFlow<List<NotificationItem>> = _notificationsFlow.asStateFlow()

        private var instance: VoidNotificationListenerService? = null

        fun isAccessGranted(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val myComponent = ComponentName(context, VoidNotificationListenerService::class.java).flattenToString()
            val shortComponent = ComponentName(context, VoidNotificationListenerService::class.java).flattenToShortString()
            return enabledListeners.contains(myComponent) || enabledListeners.contains(shortComponent)
        }

        fun openAccessSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open notification listener settings: ${e.message}")
            }
        }

        fun dismiss(key: String) {
            try {
                instance?.cancelNotification(key)
                _notificationsFlow.value = _notificationsFlow.value.filter { it.key != key }
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing notification $key: ${e.message}")
            }
        }

        fun dismissAll() {
            try {
                instance?.cancelAllNotifications()
                _notificationsFlow.value = emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing all notifications: ${e.message}")
            }
        }

        fun triggerRefresh() {
            instance?.refreshNotifications()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "Notification listener connected successfully")
        refreshNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refreshNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refreshNotifications()
    }

    fun refreshNotifications() {
        try {
            val sbns = activeNotifications ?: emptyArray()
            val pm = packageManager

            val list = sbns.mapNotNull { sbn ->
                val notif = sbn.notification ?: return@mapNotNull null
                val extras = notif.extras

                // Exclude system UI or own package notifications if empty
                val pkg = sbn.packageName ?: return@mapNotNull null
                if (pkg == packageName) return@mapNotNull null

                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
                    ?: ""

                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                    ?: ""

                // Filter out completely blank notifications
                if (title.isBlank() && text.isBlank()) return@mapNotNull null

                var appName = pkg
                var appIcon: android.graphics.drawable.Drawable? = null
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    appName = pm.getApplicationLabel(appInfo).toString()
                    appIcon = pm.getApplicationIcon(appInfo)
                } catch (_: PackageManager.NameNotFoundException) {
                    // Ignore, fallback to pkg
                }

                val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0

                // impl-06 step 10: map the notification's own actions
                // (verified available on android.app.Notification).
                // They are rendered as taps only, never auto-fired.
                val actions = try {
                    notif.actions?.mapNotNull { action ->
                        val actionTitle = action.title?.toString()?.trim().orEmpty()
                        if (actionTitle.isEmpty()) null
                        else com.polymath.voidlauncher.model.NotifAction(actionTitle, action.actionIntent)
                    } ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }

                NotificationItem(
                    key = sbn.key,
                    packageName = pkg,
                    appName = appName,
                    appIcon = appIcon,
                    title = title,
                    text = text,
                    postTimeMs = sbn.postTime,
                    isOngoing = isOngoing,
                    contentIntent = notif.contentIntent,
                    actions = actions
                )
            }.sortedByDescending { it.postTimeMs }

            _notificationsFlow.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing notifications: ${e.message}")
        }
    }
}
