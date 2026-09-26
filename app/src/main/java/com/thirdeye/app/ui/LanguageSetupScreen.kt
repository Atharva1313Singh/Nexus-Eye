package com.thirdeye.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thirdeye.app.language.AppTextKey
import com.thirdeye.app.language.NexusEyeLanguage
import com.thirdeye.app.language.NexusEyeLanguages
import com.thirdeye.app.language.nexusText

@Composable
fun LanguageSetupScreen(
    initialAppLanguage: NexusEyeLanguage,
    initialSpeechLanguage: NexusEyeLanguage,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onSave: (
        NexusEyeLanguage,
        NexusEyeLanguage
    ) -> Unit
) {
    var selectedAppLanguage by remember {
        mutableStateOf(initialAppLanguage)
    }

    var selectedSpeechLanguage by remember {
        mutableStateOf(initialSpeechLanguage)
    }

    Scaffold { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text(
                text = nexusText(AppTextKey.LANGUAGE_SETUP),
                style = MaterialTheme.typography.headlineLarge
            )

            LanguageSelector(
                label = nexusText(AppTextKey.APP_LANGUAGE),
                selectedLanguage = selectedAppLanguage,
                onSelected = {
                    selectedAppLanguage = it
                }
            )

            Text(
                text = nexusText(
                    AppTextKey.APP_LANGUAGE_DESCRIPTION
                ),
                style = MaterialTheme.typography.bodyMedium
            )

            LanguageSelector(
                label = nexusText(AppTextKey.SPEECH_LANGUAGE),
                selectedLanguage = selectedSpeechLanguage,
                onSelected = {
                    selectedSpeechLanguage = it
                }
            )

            Text(
                text = nexusText(
                    AppTextKey.SPEECH_LANGUAGE_DESCRIPTION
                ),
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = {
                    onSave(
                        selectedAppLanguage,
                        selectedSpeechLanguage
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = nexusText(
                        AppTextKey.SAVE_AND_CONTINUE
                    )
                )
            }

            if (showBackButton) {

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = nexusText(
                            AppTextKey.BACK
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSelector(
    label: String,
    selectedLanguage: NexusEyeLanguage,
    onSelected: (NexusEyeLanguage) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedButton(
            onClick = {
                expanded = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selectedLanguage.displayName
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {

            NexusEyeLanguages.supportedLanguages.forEach { language ->

                DropdownMenuItem(
                    text = {
                        Text(language.displayName)
                    },
                    onClick = {

                        onSelected(language)

                        expanded = false
                    }
                )
            }
        }
    }
}