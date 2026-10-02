package com.thirdeye.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.thirdeye.app.language.NexusEyeLocalization
import com.thirdeye.app.security.NexusEyeApiCredentialStore

@Composable
fun GeminiApiKeySetupScreen(
    context: Context,
    onBack: () -> Unit
) {

    val appContext =
        context.applicationContext

    val apiCredentialStore =
        remember {
            NexusEyeApiCredentialStore(
                appContext
            )
        }

    var apiKey by
    remember {
        mutableStateOf("")
    }

    var showApiKey by
    remember {
        mutableStateOf(false)
    }

    var isConfigured by
    remember {
        mutableStateOf(
            apiCredentialStore.hasGeminiApiKey()
        )
    }

    var statusText by
    remember {
        mutableStateOf("")
    }

    Scaffold { innerPadding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        innerPadding
                    )
                    .padding(
                        20.dp
                    )
                    .verticalScroll(
                        rememberScrollState()
                    ),

            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {

            Text(
                text =
                    geminiText(
                        "title"
                    ),

                style =
                    MaterialTheme
                        .typography
                        .headlineLarge
            )

            Text(
                text =
                    geminiText(
                        "description"
                    ),

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
                        Modifier.padding(
                            16.dp
                        ),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    Text(
                        text =
                            geminiText(
                                "status_title"
                            ),

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Text(
                        text =
                            if (
                                isConfigured
                            ) {
                                geminiText(
                                    "configured"
                                )
                            } else {
                                geminiText(
                                    "not_configured"
                                )
                            }
                    )
                }
            }

            OutlinedTextField(
                value =
                    apiKey,

                onValueChange = {
                    apiKey =
                        it
                },

                modifier =
                    Modifier.fillMaxWidth(),

                singleLine =
                    true,

                label = {
                    Text(
                        text =
                            geminiText(
                                "api_key_label"
                            )
                    )
                },

                placeholder = {
                    Text(
                        text =
                            geminiText(
                                "api_key_placeholder"
                            )
                    )
                },

                visualTransformation =
                    if (
                        showApiKey
                    ) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    }
            )

            TextButton(
                onClick = {
                    showApiKey =
                        !showApiKey
                },

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        if (
                            showApiKey
                        ) {
                            geminiText(
                                "hide_key"
                            )
                        } else {
                            geminiText(
                                "show_key"
                            )
                        }
                )
            }

            Button(
                onClick = {

                    val cleanKey =
                        apiKey.trim()

                    if (
                        cleanKey.isBlank()
                    ) {

                        statusText =
                            geminiText(
                                "empty_key"
                            )

                        return@Button
                    }

                    try {

                        apiCredentialStore
                            .saveGeminiApiKey(
                                cleanKey
                            )

                        isConfigured =
                            apiCredentialStore
                                .hasGeminiApiKey()

                        apiKey =
                            ""

                        showApiKey =
                            false

                        statusText =
                            geminiText(
                                "saved"
                            )

                    } catch (
                        exception: Exception
                    ) {

                        statusText =
                            exception.message
                                ?: geminiText(
                                    "save_failed"
                                )
                    }
                },

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        geminiText(
                            "save"
                        )
                )
            }

            OutlinedButton(
                onClick = {

                    try {

                        apiCredentialStore
                            .clearGeminiApiKey()

                        isConfigured =
                            false

                        apiKey =
                            ""

                        showApiKey =
                            false

                        statusText =
                            geminiText(
                                "removed"
                            )

                    } catch (
                        exception: Exception
                    ) {

                        statusText =
                            exception.message
                                ?: geminiText(
                                    "remove_failed"
                                )
                    }
                },

                enabled =
                    isConfigured,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        geminiText(
                            "remove"
                        )
                )
            }

            if (
                statusText.isNotBlank()
            ) {

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            statusText,

                        modifier =
                            Modifier.padding(
                                14.dp
                            )
                    )
                }
            }

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(
                            16.dp
                        ),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    Text(
                        text =
                            geminiText(
                                "privacy_title"
                            ),

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Text(
                        text =
                            geminiText(
                                "privacy_body"
                            )
                    )

                    Text(
                        text =
                            geminiText(
                                "local_storage"
                            )
                    )

                    Text(
                        text =
                            geminiText(
                                "online_usage"
                            )
                    )
                }
            }

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        geminiText(
                            "security_note"
                        ),

                    modifier =
                        Modifier.padding(
                            16.dp
                        )
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        4.dp
                    )
            )

            OutlinedButton(
                onClick = onBack,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        geminiText(
                            "back"
                        )
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )
        }
    }
}

private fun geminiText(
    key: String
): String {

    val languageId =
        NexusEyeLocalization
            .currentAppLanguage
            .id

    return when (
        languageId
    ) {

        "hi" -> {

            when (
                key
            ) {

                "title" ->
                    "Gemini AI सेटिंग"

                "description" ->
                    "NEXUS EYE के AI विज़न फीचर के लिए Gemini API key सुरक्षित रूप से सेट करें।"

                "status_title" ->
                    "API key स्थिति"

                "configured" ->
                    "Gemini API key कॉन्फ़िगर है।"

                "not_configured" ->
                    "Gemini API key अभी कॉन्फ़िगर नहीं है।"

                "api_key_label" ->
                    "Gemini API key"

                "api_key_placeholder" ->
                    "अपनी Gemini API key दर्ज करें"

                "show_key" ->
                    "API key दिखाएँ"

                "hide_key" ->
                    "API key छिपाएँ"

                "empty_key" ->
                    "कृपया API key दर्ज करें।"

                "save" ->
                    "API key सुरक्षित करें"

                "saved" ->
                    "Gemini API key सुरक्षित रूप से सेव हो गई।"

                "save_failed" ->
                    "API key सेव नहीं हो सकी।"

                "remove" ->
                    "API key हटाएँ"

                "removed" ->
                    "Gemini API key हटा दी गई।"

                "remove_failed" ->
                    "API key हटाई नहीं जा सकी।"

                "privacy_title" ->
                    "गोपनीयता"

                "privacy_body" ->
                    "API key Android Keystore आधारित सुरक्षित स्टोरेज में एन्क्रिप्ट की जाती है।"

                "local_storage" ->
                    "स्थानीय: API key आपके डिवाइस पर सुरक्षित स्टोरेज में रखी जाती है।"

                "online_usage" ->
                    "ऑनलाइन: AI Vision विश्लेषण के लिए चुनी गई इमेज Gemini API को भेजी जा सकती है।"

                "security_note" ->
                    "API key को source code में hard-code न करें और उसे किसी अन्य व्यक्ति के साथ साझा न करें।"

                "back" ->
                    "वापस"

                else ->
                    ""
            }
        }

        else -> {

            when (
                key
            ) {

                "title" ->
                    "Gemini AI Settings"

                "description" ->
                    "Configure a Gemini API key securely for the NEXUS EYE AI Vision feature."

                "status_title" ->
                    "API Key Status"

                "configured" ->
                    "Gemini API key is configured."

                "not_configured" ->
                    "Gemini API key is not configured."

                "api_key_label" ->
                    "Gemini API key"

                "api_key_placeholder" ->
                    "Enter your Gemini API key"

                "show_key" ->
                    "Show API Key"

                "hide_key" ->
                    "Hide API Key"

                "empty_key" ->
                    "Please enter an API key."

                "save" ->
                    "Save API Key Securely"

                "saved" ->
                    "Gemini API key was saved securely."

                "save_failed" ->
                    "The API key could not be saved."

                "remove" ->
                    "Remove API Key"

                "removed" ->
                    "Gemini API key was removed."

                "remove_failed" ->
                    "The API key could not be removed."

                "privacy_title" ->
                    "Privacy"

                "privacy_body" ->
                    "The API key is encrypted using Android Keystore-backed secure storage."

                "local_storage" ->
                    "Local: the API key is kept in secure storage on this device."

                "online_usage" ->
                    "Online: when AI Vision analysis is requested, the selected image may be sent to the Gemini API."

                "security_note" ->
                    "Do not hard-code the API key into source code or share it with other people."

                "back" ->
                    "Back"

                else ->
                    ""
            }
        }
    }
}
