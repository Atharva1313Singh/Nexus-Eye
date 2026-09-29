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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thirdeye.app.language.AppTextKey
import com.thirdeye.app.language.nexusText

@Composable
fun HomeScreen(
    onCommunicationClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onIntelligenceClick: () -> Unit,
    onVisionClick: () -> Unit,
    onNavigationClick: () -> Unit,
    onWeatherClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Scaffold { innerPadding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),

            verticalArrangement =
                Arrangement.spacedBy(
                    18.dp
                )
        ) {

            Text(
                text =
                    nexusText(
                        AppTextKey.APP_NAME
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineLarge
            )

            Text(
                text =
                    nexusText(
                        AppTextKey.HOME
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleLarge
            )

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(
                            20.dp
                        ),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    Text(
                        text =
                            nexusText(
                                AppTextKey.ESP32_STATUS
                            ),
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Text(
                        text =
                            nexusText(
                                AppTextKey.NOT_CONNECTED
                            ),
                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge
                    )
                }
            }

            Button(
                onClick =
                    onIntelligenceClick,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        nexusText(
                            AppTextKey.INTELLIGENCE
                        )
                )
            }

            Button(
                onClick =
                    onVoiceClick,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        nexusText(
                            AppTextKey.VOICE_SYSTEM
                        )
                )
            }

            Button(
                onClick =
                    onNavigationClick,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Navigation"
                )
            }

            Button(
                onClick =
                    onWeatherClick,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Weather"
                )
            }

            Button(
                onClick =
                    onCommunicationClick,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        nexusText(
                            AppTextKey.COMMUNICATION
                        )
                )
            }

            Button(
                onClick =
                    onVisionClick,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Vision"
                )
            }

            OutlinedButton(
                onClick =
                    onSettingsClick,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        nexusText(
                            AppTextKey.SETTINGS
                        )
                )
            }
        }
    }
}