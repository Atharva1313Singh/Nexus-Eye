package com.thirdeye.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NavigationScreen(
    savedHomeAddress: String?,
    onStartNavigation: (String) -> Unit,
    onGoHome: () -> Boolean,
    onBack: () -> Unit
) {
    var destination by remember {
        mutableStateOf("")
    }

    var statusText by remember {
        mutableStateOf(
            "Enter a destination and start navigation."
        )
    }

    Scaffold { innerPadding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),

            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp
                )
        ) {

            Text(
                text =
                    "Navigation",

                style =
                    MaterialTheme
                        .typography
                        .headlineLarge
            )

            Text(
                text =
                    "NEXUS EYE uses offline navigation first.",

                style =
                    MaterialTheme
                        .typography
                        .bodyLarge
            )

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {

                    Text(
                        text =
                            "Destination",

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    OutlinedTextField(
                        value =
                            destination,

                        onValueChange = {
                            destination = it
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {
                            Text(
                                text =
                                    "Place or address"
                            )
                        },

                        placeholder = {
                            Text(
                                text =
                                    "Example: Railway Station"
                            )
                        },

                        singleLine = true
                    )
                }
            }

            Button(
                onClick = {

                    val safeDestination =
                        destination.trim()

                    if (
                        safeDestination.isBlank()
                    ) {

                        statusText =
                            "Please enter a destination."

                    } else {

                        statusText =
                            "Starting navigation to $safeDestination."

                        onStartNavigation(
                            safeDestination
                        )
                    }
                },

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Start Navigation"
                )
            }

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    Text(
                        text =
                            "Saved Home",

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Text(
                        text =
                            savedHomeAddress
                                ?: "Home location has not been saved.",

                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge
                    )

                    Button(
                        onClick = {

                            val started =
                                onGoHome()

                            statusText =
                                if (
                                    started
                                ) {
                                    "Starting navigation to your saved home."
                                } else {
                                    "No home location is saved. Set your home location in Settings."
                                }
                        },

                        enabled =
                            savedHomeAddress != null,

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            text =
                                "Go Home"
                        )
                    }
                }
            }

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        statusText,

                    modifier =
                        Modifier.padding(
                            16.dp
                        ),

                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge
                )
            }

            OutlinedButton(
                onClick =
                    onBack,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Back"
                )
            }
        }
    }
}