package com.thirdeye.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thirdeye.app.environment.NexusEyeHomeLocationManager
import com.thirdeye.app.notifications.NexusEyeNotificationAccessManager

@Composable
fun NotificationAccessSetupScreen(
    onAccessGranted: () -> Unit
) {

    val context =
        LocalContext.current

    val lifecycleOwner =
        LocalLifecycleOwner.current

    val notificationManager =
        remember {
            NexusEyeNotificationAccessManager(
                context
            )
        }

    val homeLocationManager =
        remember {
            NexusEyeHomeLocationManager(
                context
            )
        }

    var notificationAccessGranted by
    remember {
        mutableStateOf(
            notificationManager.isAccessGranted()
        )
    }

    var showHomeSetup by
    remember {
        mutableStateOf(false)
    }

    fun refreshAccess() {

        val granted =
            notificationManager.isAccessGranted()

        notificationAccessGranted =
            granted

        if (granted) {

            if (
                homeLocationManager
                    .hasSavedHomeLocation()
            ) {

                onAccessGranted()

            } else {

                showHomeSetup =
                    true
            }
        }
    }

    DisposableEffect(
        lifecycleOwner
    ) {

        val observer =
            LifecycleEventObserver {
                    _, event ->

                if (
                    event ==
                    Lifecycle.Event.ON_RESUME
                ) {

                    refreshAccess()
                }
            }

        lifecycleOwner
            .lifecycle
            .addObserver(
                observer
            )

        onDispose {

            lifecycleOwner
                .lifecycle
                .removeObserver(
                    observer
                )
        }
    }

    LaunchedEffect(Unit) {

        refreshAccess()
    }

    if (
        showHomeSetup &&
        notificationAccessGranted
    ) {

        HomeLocationSetupScreen(
            onComplete = {

                onAccessGranted()
            }
        )

        return
    }

    Surface(
        modifier =
            Modifier.fillMaxSize(),

        color =
            MaterialTheme
                .colorScheme
                .background
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.Center
        ) {

            Text(
                text =
                    "NEXUS EYE",

                style =
                    MaterialTheme
                        .typography
                        .headlineLarge,

                textAlign =
                    TextAlign.Center
            )

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            Text(
                text =
                    "Notification Access",

                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,

                textAlign =
                    TextAlign.Center
            )

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            Text(
                text =
                    "Notification Access allows NEXUS EYE to receive supported notification events for the assistive features of the project.",

                textAlign =
                    TextAlign.Center
            )

            Spacer(
                modifier =
                    Modifier.height(24.dp)
            )

            Button(
                modifier =
                    Modifier.fillMaxWidth(),

                onClick = {

                    notificationManager
                        .openNotificationAccessSettings()
                }
            ) {

                Text(
                    text =
                        "Open Notification Access"
                )
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),

                onClick = {

                    refreshAccess()
                }
            ) {

                Text(
                    text =
                        "Check Access"
                )
            }
        }
    }
}