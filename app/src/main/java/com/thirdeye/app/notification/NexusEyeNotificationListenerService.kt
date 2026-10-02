package com.thirdeye.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NexusEyeNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NexusEyeNotification"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        Log.d(TAG, "NEXUS EYE Notification Access connected")
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {
        Log.d(
            TAG,
            "Notification posted: ${sbn.packageName}"
        )
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification
    ) {
        Log.d(
            TAG,
            "Notification removed: ${sbn.packageName}"
        )
    }

    override fun onListenerDisconnected() {
        Log.d(
            TAG,
            "NEXUS EYE Notification Access disconnected"
        )

        super.onListenerDisconnected()
    }
}