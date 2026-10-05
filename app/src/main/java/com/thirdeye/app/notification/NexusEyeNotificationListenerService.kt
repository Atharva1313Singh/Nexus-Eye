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
        Log.d(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        Log.d(
            TAG,
            "Notification received from: ${sbn.packageName}"
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        Log.d(
            TAG,
            "Notification removed from: ${sbn.packageName}"
        )
    }
}