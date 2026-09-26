package com.thirdeye.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.thirdeye.app.audio.AudioDestination
import com.thirdeye.app.audio.AudioOutputPolicy
import com.thirdeye.app.audio.NexusEyeTtsManager
import com.thirdeye.app.bluetooth.NexusEyeBleManager
import com.thirdeye.app.intelligence.ResponseSource
import com.thirdeye.app.intelligence.TaskRouter
import com.thirdeye.app.language.NexusEyeLanguage
import com.thirdeye.app.navigation.NexusEyeHomeCommandHandler
import com.thirdeye.app.navigation.NexusEyeHomeCommandResult
import com.thirdeye.app.navigation.NexusEyeHomeNavigationTarget
import kotlinx.coroutines.launch

@Composable
fun VoiceScreen(
    context: Context,
    bleManager: NexusEyeBleManager,
    taskRouter: TaskRouter,
    speechLanguage: NexusEyeLanguage,
    onNavigateHome: (NexusEyeHomeNavigationTarget) -> Unit = {},
    onBack: () -> Unit
) {
    val appContext =
        context.applicationContext

    val connectionState by
    bleManager.connectionState.collectAsState()

    val scope =
        rememberCoroutineScope()

    val ttsManager =
        remember {
            NexusEyeTtsManager(
                appContext
            )
        }

    val homeCommandHandler =
        remember {
            NexusEyeHomeCommandHandler(
                appContext
            )
        }

    val speechRecognizer =
        remember {
            if (
                SpeechRecognizer.isRecognitionAvailable(
                    appContext
                )
            ) {
                SpeechRecognizer.createSpeechRecognizer(
                    appContext
                )
            } else {
                null
            }
        }

    var isListening by
    remember {
        mutableStateOf(false)
    }

    var isProcessing by
    remember {
        mutableStateOf(false)
    }

    var partialText by
    remember {
        mutableStateOf("")
    }

    var recognizedText by
    remember {
        mutableStateOf("")
    }

    var answerText by
    remember {
        mutableStateOf("")
    }

    var sourceText by
    remember {
        mutableStateOf("")
    }

    var statusText by
    remember {
        mutableStateOf("Ready")
    }

    var errorText by
    remember {
        mutableStateOf("")
    }

    var pendingDeviceActionQuery by
    remember {
        mutableStateOf<String?>(null)
    }

    var permissionRetryQuery by
    remember {
        mutableStateOf<String?>(null)
    }

    fun speakPhone(
        text: String
    ) {
        try {
            if (
                !ttsManager.setLanguage(
                    speechLanguage
                )
            ) {
                errorText =
                    "The selected speech language is not available."

                statusText =
                    "Speech language error"

                return
            }

            ttsManager.speakOnPhoneFallback(
                text = text,
                onComplete = {
                    statusText =
                        "Answer spoken"
                },
                onError = {
                    errorText =
                        it

                    statusText =
                        "Speech error"
                }
            )
        } catch (
            exception: Exception
        ) {
            errorText =
                exception.message
                    ?: "Could not speak the answer through the phone."

            statusText =
                "Speech error"
        }
    }

    fun handleHomeCommand(
        text: String
    ): Boolean {
        return when (
            val result =
                homeCommandHandler.handle(
                    text
                )
        ) {
            is NexusEyeHomeCommandResult.Ready -> {
                recognizedText =
                    text

                answerText =
                    "Starting navigation home."

                sourceText =
                    "Saved Home Location"

                errorText =
                    ""

                statusText =
                    "Starting home navigation"

                onNavigateHome(
                    result.target
                )

                true
            }

            NexusEyeHomeCommandResult.HomeNotSaved -> {
                recognizedText =
                    text

                answerText =
                    "Home location has not been configured."

                sourceText =
                    "Home Location"

                errorText =
                    ""

                statusText =
                    "Home location not configured"

                when (
                    AudioOutputPolicy.destinationFor(
                        connectionState
                    )
                ) {
                    AudioDestination.PHONE_FALLBACK -> {
                        speakPhone(
                            "Home location has not been configured."
                        )
                    }

                    AudioDestination.ESP32_WEARABLE -> {
                        statusText =
                            "ESP32 connected. Home location has not been configured."
                    }
                }

                true
            }

            is NexusEyeHomeCommandResult.NotAHomeCommand -> {
                false
            }
        }
    }

    val deviceActionPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val pendingQuery =
                pendingDeviceActionQuery

            pendingDeviceActionQuery =
                null

            if (pendingQuery.isNullOrBlank()) {
                return@rememberLauncherForActivityResult
            }

            val allGranted =
                permissions.values.all { it }

            if (allGranted) {
                permissionRetryQuery =
                    pendingQuery
            } else {
                isListening =
                    false

                isProcessing =
                    false

                answerText =
                    if (speechLanguage.id == "hi") {
                        "आवश्यक अनुमति नहीं मिली।"
                    } else {
                        "The required permission was not granted."
                    }

                sourceText =
                    "Device action"

                statusText =
                    "Permission required"
            }
        }

    fun processVoiceCommand(
        text: String
    ) {
        val query =
            text.trim()

        if (
            query.isBlank()
        ) {
            isListening =
                false

            isProcessing =
                false

            statusText =
                "No speech detected."

            return
        }

        recognizedText =
            query

        partialText =
            ""

        errorText =
            ""

        if (
            handleHomeCommand(
                query
            )
        ) {
            isListening =
                false

            isProcessing =
                false

            return
        }

        isListening =
            false

        isProcessing =
            true

        statusText =
            "Processing"

        scope.launch {
            try {
                val result =
                    taskRouter.process(
                        query =
                            query,

                        speechLanguageId =
                            speechLanguage.id
                    )

                if (
                    result.requiredPermissions.isNotEmpty()
                ) {
                    pendingDeviceActionQuery =
                        query

                    answerText =
                        result.answer

                    sourceText =
                        "Device action"

                    statusText =
                        "Permission required"

                    deviceActionPermissionLauncher.launch(
                        result.requiredPermissions.toTypedArray()
                    )

                    return@launch
                }

                answerText =
                    result.answer

                sourceText =
                    when (
                        result.source
                    ) {
                        ResponseSource.OFFLINE_DATABASE ->
                            "Offline database"

                        ResponseSource.CALCULATOR ->
                            "Calculator"

                        ResponseSource.DEVICE ->
                            "Device"

                        ResponseSource.DEVICE_ACTION ->
                            "Device action"

                        ResponseSource.GEMINI ->
                            "Gemini AI"

                        ResponseSource.WIKIPEDIA ->
                            "Online"

                        ResponseSource.UNKNOWN ->
                            "Unknown"
                    }

                when (
                    AudioOutputPolicy.destinationFor(
                        connectionState
                    )
                ) {
                    AudioDestination.PHONE_FALLBACK -> {
                        statusText =
                            "Speaking through phone"

                        speakPhone(
                            result.answer
                        )
                    }

                    AudioDestination.ESP32_WEARABLE -> {
                        statusText =
                            "ESP32 connected"
                    }
                }
            } catch (
                exception: Exception
            ) {
                errorText =
                    exception.message
                        ?: "Unable to process the question."

                statusText =
                    "Error"
            } finally {
                isProcessing =
                    false
            }
        }
    }

    LaunchedEffect(
        permissionRetryQuery
    ) {
        val retryQuery =
            permissionRetryQuery

        if (!retryQuery.isNullOrBlank()) {
            permissionRetryQuery =
                null

            processVoiceCommand(
                retryQuery
            )
        }
    }

    fun startListening() {
        val recognizer =
            speechRecognizer

        if (
            recognizer == null
        ) {
            errorText =
                "Speech recognition is not available on this device."

            statusText =
                "Speech recognition unavailable"

            return
        }

        if (
            isListening
        ) {
            return
        }

        try {
            val intent =
                Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                ).apply {

                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        speechLanguage.id
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                        speechLanguage.id
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                        true
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_MAX_RESULTS,
                        5
                    )
                }

            recognizer.setRecognitionListener(
                object :
                    RecognitionListener {

                    override fun onReadyForSpeech(
                        params: Bundle?
                    ) {
                        isListening =
                            true

                        isProcessing =
                            false

                        statusText =
                            "Listening"
                    }

                    override fun onBeginningOfSpeech() {
                        isListening =
                            true

                        statusText =
                            "Listening"
                    }

                    override fun onRmsChanged(
                        rmsdB: Float
                    ) {
                    }

                    override fun onBufferReceived(
                        buffer: ByteArray?
                    ) {
                    }

                    override fun onEndOfSpeech() {
                        isListening =
                            false

                        statusText =
                            "Processing"
                    }

                    override fun onError(
                        error: Int
                    ) {
                        isListening =
                            false

                        isProcessing =
                            false

                        partialText =
                            ""

                        errorText =
                            speechErrorMessage(
                                error
                            )

                        statusText =
                            "Voice error"
                    }

                    override fun onResults(
                        results: Bundle?
                    ) {
                        isListening =
                            false

                        partialText =
                            ""

                        val text =
                            results
                                ?.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                                )
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()

                        processVoiceCommand(
                            text
                        )
                    }

                    override fun onPartialResults(
                        partialResults: Bundle?
                    ) {
                        val text =
                            partialResults
                                ?.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                                )
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()

                        if (
                            text.isNotBlank()
                        ) {
                            partialText =
                                text

                            statusText =
                                "Listening"
                        }
                    }

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {
                    }
                }
            )

            recognizedText =
                ""

            partialText =
                ""

            errorText =
                ""

            isProcessing =
                false

            isListening =
                true

            statusText =
                "Starting voice recognition"

            recognizer.startListening(
                intent
            )
        } catch (
            exception: Exception
        ) {
            isListening =
                false

            isProcessing =
                false

            errorText =
                exception.message
                    ?: "Could not start speech recognition."

            statusText =
                "Voice error"
        }
    }

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (
                granted
            ) {
                startListening()
            } else {
                isListening =
                    false

                isProcessing =
                    false

                errorText =
                    "Microphone permission is required."

                statusText =
                    "Microphone permission required"
            }
        }

    fun launchVoice() {
        val granted =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (
            granted
        ) {
            startListening()
        } else {
            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    DisposableEffect(
        speechRecognizer
    ) {
        onDispose {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }

            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }

            try {
                ttsManager.stop()
            } catch (_: Exception) {
            }

            try {
                ttsManager.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    DisposableEffect(
        speechLanguage
    ) {
        try {
            ttsManager.setLanguage(
                speechLanguage
            )
        } catch (_: Exception) {
        }

        onDispose {
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(20.dp),

        verticalArrangement =
            Arrangement.Top
    ) {
        Text(
            text =
                "VOICE",

            style =
                MaterialTheme
                    .typography
                    .headlineMedium
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        Text(
            text =
                "Speech language: ${speechLanguage.displayName}",

            style =
                MaterialTheme
                    .typography
                    .bodyLarge
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        Text(
            text =
                "ESP32: ${connectionState.name}",

            style =
                MaterialTheme
                    .typography
                    .bodyMedium
        )

        Spacer(
            modifier =
                Modifier.height(
                    20.dp
                )
        )

        Button(
            onClick = {
                if (
                    isListening
                ) {
                    try {
                        speechRecognizer
                            ?.stopListening()
                    } catch (_: Exception) {
                    }

                    isListening =
                        false

                    isProcessing =
                        false

                    statusText =
                        "Stopped"
                } else {
                    launchVoice()
                }
            },

            enabled =
                !isProcessing,

            modifier =
                Modifier.fillMaxWidth()
        ) {
            Text(
                text =
                    when {
                        isListening ->
                            "STOP"

                        isProcessing ->
                            "WAIT"

                        else ->
                            "TALK"
                    }
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
                )
        )

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        16.dp
                    )
            ) {
                Text(
                    text =
                        "Status",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            6.dp
                        )
                )

                Text(
                    text =
                        statusText
                )
            }
        }

        if (
            partialText.isNotBlank()
        ) {
            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            16.dp
                        )
                ) {
                    Text(
                        text =
                            "Listening",

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                6.dp
                            )
                    )

                    Text(
                        text =
                            partialText,

                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge
                    )
                }
            }
        }

        if (
            recognizedText.isNotBlank()
        ) {
            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            16.dp
                        )
                ) {
                    Text(
                        text =
                            "You said",

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                6.dp
                            )
                    )

                    Text(
                        text =
                            recognizedText
                    )
                }
            }
        }

        if (
            answerText.isNotBlank()
        ) {
            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            16.dp
                        )
                ) {
                    Text(
                        text =
                            "Answer",

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                6.dp
                            )
                    )

                    Text(
                        text =
                            answerText,

                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge
                    )

                    if (
                        sourceText.isNotBlank()
                    ) {
                        Spacer(
                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )

                        Text(
                            text =
                                "Source: $sourceText",

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                }
            }
        }

        if (
            errorText.isNotBlank()
        ) {
            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            16.dp
                        )
                ) {
                    Text(
                        text =
                            "Error",

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                6.dp
                            )
                    )

                    Text(
                        text =
                            errorText
                    )
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        16.dp
                    )
            ) {
                Text(
                    text =
                        "Home command",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            6.dp
                        )
                )

                Text(
                    text =
                        if (
                            homeCommandHandler
                                .hasSavedHome()
                        ) {
                            "Saved home location is available."
                        } else {
                            "No home location is configured."
                        }
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    20.dp
                )
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {
            OutlinedButton(
                onClick = {
                    try {
                        speechRecognizer
                            ?.stopListening()
                    } catch (_: Exception) {
                    }

                    try {
                        ttsManager.stop()
                    } catch (_: Exception) {
                    }

                    isListening =
                        false

                    isProcessing =
                        false

                    partialText =
                        ""

                    recognizedText =
                        ""

                    answerText =
                        ""

                    sourceText =
                        ""

                    errorText =
                        ""

                    statusText =
                        "Ready"
                },

                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {
                Text(
                    text =
                        "RESET"
                )
            }

            OutlinedButton(
                onClick = {
                    try {
                        speechRecognizer
                            ?.stopListening()
                    } catch (_: Exception) {
                    }

                    try {
                        ttsManager.stop()
                    } catch (_: Exception) {
                    }

                    onBack()
                },

                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {
                Text(
                    text =
                        "BACK"
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )
    }
}

private fun speechErrorMessage(
    errorCode: Int
): String {
    return when (
        errorCode
    ) {
        SpeechRecognizer.ERROR_AUDIO ->
            "Audio recording error."

        SpeechRecognizer.ERROR_CLIENT ->
            "Speech recognition client error."

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission is required."

        SpeechRecognizer.ERROR_NETWORK ->
            "Speech recognition network error."

        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition network timeout."

        SpeechRecognizer.ERROR_NO_MATCH ->
            "I could not understand the speech."

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "Speech recognizer is busy."

        SpeechRecognizer.ERROR_SERVER ->
            "Speech recognition server error."

        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "No speech was detected."

        else ->
            "Speech recognition failed."
    }
}