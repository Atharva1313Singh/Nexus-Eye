package com.thirdeye.app.ui

import android.content.Context

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.thirdeye.app.audio.NexusEyeAssistantAudioRouter
import com.thirdeye.app.bluetooth.NexusEyeBleManager
import com.thirdeye.app.bluetooth.NexusEyeConnectionState
import com.thirdeye.app.intelligence.IntelligenceResult
import com.thirdeye.app.intelligence.ResponseSource
import com.thirdeye.app.intelligence.TaskRouter
import com.thirdeye.app.language.AppTextKey
import com.thirdeye.app.language.NexusEyeLanguage
import com.thirdeye.app.language.nexusText

import kotlinx.coroutines.launch

@Composable
fun IntelligenceScreen(
    context: Context,
    taskRouter: TaskRouter,
    bleManager: NexusEyeBleManager,
    speechLanguage: NexusEyeLanguage,
    onBack: () -> Unit
) {
    var input by remember {
        mutableStateOf("")
    }

    var result by remember {
        mutableStateOf<IntelligenceResult?>(null)
    }

    var isProcessing by remember {
        mutableStateOf(false)
    }

    var speechStatus by remember {
        mutableStateOf("")
    }

    val scope =
        rememberCoroutineScope()

    val connectionState by
    bleManager.connectionState
        .collectAsState()

    val audioRouter =
        remember(speechLanguage) {
            NexusEyeAssistantAudioRouter(
                context = context,
                bleManager = bleManager,
                speechLanguage = speechLanguage
            )
        }

    DisposableEffect(audioRouter) {

        onDispose {

            try {
                audioRouter.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    fun speakAnswer(
        answer: String
    ) {
        val cleanAnswer =
            answer.trim()

        if (cleanAnswer.isBlank()) {
            speechStatus =
                "There is no answer to speak."
            return
        }

        speechStatus =
            if (
                audioRouter
                    .isWearableAudioAvailable
            ) {
                "Sending answer to ESP32 wearable."
            } else {
                "ESP32 not connected. Phone audio fallback active."
            }

        audioRouter.routeText(
            text = cleanAnswer,
            language = speechLanguage,
            onSuccess = { message ->
                speechStatus = message
            },
            onError = { message ->
                speechStatus = message
            }
        )
    }

    fun askQuestion() {

        val question =
            input.trim()

        if (question.isBlank()) {
            return
        }

        scope.launch {

            isProcessing = true
            speechStatus = ""
            result = null

            try {

                val response =
                    taskRouter.process(
                        query = question,
                        speechLanguageId =
                            speechLanguage.id
                    )

                result = response

                speakAnswer(
                    response.answer
                )

            } catch (
                exception: Exception
            ) {

                speechStatus =
                    exception.message
                        ?: "Unable to process the question."

            } finally {

                isProcessing = false
            }
        }
    }

    Scaffold { innerPadding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
            verticalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {

            Text(
                text =
                    nexusText(
                        AppTextKey.INTELLIGENCE
                    ),
                style =
                    MaterialTheme.typography.headlineLarge
            )

            Text(
                text =
                    if (
                        connectionState ==
                        NexusEyeConnectionState.READY ||
                        connectionState ==
                        NexusEyeConnectionState.CONNECTED
                    ) {
                        "ESP32 connected"
                    } else {
                        "ESP32 not connected"
                    },
                style =
                    MaterialTheme.typography.titleMedium
            )

            Text(
                text =
                    if (
                        audioRouter
                            .isWearableAudioAvailable
                    ) {
                        "Audio path: ESP32 wearable"
                    } else {
                        "Audio path: phone fallback"
                    },
                style =
                    MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                },
                modifier =
                    Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text =
                            nexusText(
                                AppTextKey.TYPE_QUESTION
                            )
                    )
                },
                minLines = 3
            )

            Button(
                onClick = {
                    askQuestion()
                },
                enabled =
                    input.isNotBlank() &&
                            !isProcessing,
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        if (isProcessing) {
                            nexusText(
                                AppTextKey.PROCESSING
                            )
                        } else {
                            nexusText(
                                AppTextKey.ASK
                            )
                        }
                )
            }

            result?.let { response ->

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier =
                            Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        Text(
                            text =
                                nexusText(
                                    AppTextKey.ANSWER
                                ),
                            style =
                                MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text =
                                response.answer,
                            style =
                                MaterialTheme.typography.bodyLarge
                        )

                        Text(
                            text =
                                "${
                                    nexusText(
                                        AppTextKey.RESPONSE_SOURCE
                                    )
                                }: ${
                                    sourceText(
                                        response.source
                                    )
                                }",
                            style =
                                MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (
                speechStatus
                    .isNotBlank()
            ) {

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text = speechStatus,
                        modifier =
                            Modifier.padding(16.dp),
                        style =
                            MaterialTheme.typography.bodyMedium
                    )
                }
            }

            OutlinedButton(
                onClick = {

                    try {
                        audioRouter.stop()
                    } catch (_: Exception) {
                    }

                    onBack()
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        nexusText(
                            AppTextKey.BACK
                        )
                )
            }
        }
    }
}

@Composable
private fun sourceText(
    source: ResponseSource
): String {

    return when (source) {

        ResponseSource.OFFLINE_DATABASE ->
            nexusText(
                AppTextKey.OFFLINE_DATABASE
            )

        ResponseSource.CALCULATOR ->
            nexusText(
                AppTextKey.CALCULATOR
            )

        ResponseSource.DEVICE ->
            nexusText(
                AppTextKey.DEVICE
            )

        ResponseSource.WIKIPEDIA ->
            nexusText(
                AppTextKey.ONLINE
            )

        ResponseSource.UNKNOWN ->
            nexusText(
                AppTextKey.UNKNOWN
            )
    }
}