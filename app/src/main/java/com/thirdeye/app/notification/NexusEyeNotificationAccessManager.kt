package com.thirdeye.app.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

class NexusEyeNotificationAccessManager(
    context: Context
) {

    private val appContext =
        context.applicationContext

    fun isAccessGranted(): Boolean {

        return try {

            NotificationManagerCompat
                .getEnabledListenerPackages(
                    appContext
                )
                .contains(
                    appContext.packageName
                )

        } catch (_: Exception) {

            false
        }
    }

    fun openNotificationAccessSettings() {

        val intent =
            Intent(
                Settings
                    .ACTION_NOTIFICATION_LISTENER_SETTINGS
            ).apply {

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        appContext.startActivity(
            intent
        )
    }
}