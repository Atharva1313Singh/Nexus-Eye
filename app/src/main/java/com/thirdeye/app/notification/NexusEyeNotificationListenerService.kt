package com.thirdeye.app.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NexusEyeNotificationListenerService :
    NotificationListenerService() {

    companion object {

        private val _isConnected =
            MutableStateFlow(false)

        val isConnected =
            _isConnected.asStateFlow()
    }

    override fun onListenerConnected() {

        super.onListenerConnected()

        _isConnected.value =
            true
    }

    override fun onListenerDisconnected() {

        _isConnected.value =
            false

        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {
        /*
         * Notification reception is intentionally kept separate
         * from the UI. Future notification-reading features can
         * consume the notification data here without changing
         * the permission/setup flow.
         */
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification
    ) {
        /*
         * Notification removal is received here by Android.
         */
    }

    override fun onDestroy() {

        _isConnected.value =
            false

        super.onDestroy()
    }
}