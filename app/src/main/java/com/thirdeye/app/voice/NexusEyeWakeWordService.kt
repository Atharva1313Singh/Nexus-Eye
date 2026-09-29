package com.thirdeye.app.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.thirdeye.app.NexusEyeRuntime
import com.thirdeye.app.R
import com.thirdeye.app.audio.NexusEyeAssistantAudioRouter
import com.thirdeye.app.audio.NexusEyeTtsManager
import com.thirdeye.app.intelligence.TaskRouter
import com.thirdeye.app.language.LanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Background "Hey Nexus" listener.
 *
 * This service is started while the app is visible and microphone permission
 * has been granted. It keeps a microphone foreground-service notification
 * visible and cycles SpeechRecognizer sessions to look for the wake phrase.
 *
 * SpeechRecognizer is not a dedicated low-power hotword engine, so this is a
 * practical wake-phrase implementation rather than a hardware/always-on DSP
 * hotword detector.
 */
class NexusEyeWakeWordService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate
        )

    private lateinit var taskRouter: TaskRouter
    private lateinit var ttsManager: NexusEyeTtsManager
    private lateinit var languageManager: LanguageManager

    private var speechRecognizer: SpeechRecognizer? = null
    private var listeningForWakeWord = false
    private var listeningForCommand = false
    private var commandProcessing = false
    private var destroyed = false

    private val restartRunnable = Runnable {
        if (!destroyed && !commandProcessing) {
            startWakeListening()
        }
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val notification =
            buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }

        taskRouter =
            TaskRouter(
                applicationContext
            )

        ttsManager =
            NexusEyeTtsManager(
                applicationContext
            )

        languageManager =
            LanguageManager(
                applicationContext
            )

        if (!hasMicrophonePermission()) {
            stopSelf()
            return
        }

        startWakeListening()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (!hasMicrophonePermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!listeningForWakeWord &&
            !listeningForCommand &&
            !commandProcessing
        ) {
            startWakeListening()
        }

        return START_STICKY
    }

    private fun hasMicrophonePermission(): Boolean {
        return checkSelfPermission(
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startWakeListening() {
        if (destroyed ||
            commandProcessing ||
            !hasMicrophonePermission()
        ) {
            return
        }

        listeningForWakeWord = true
        listeningForCommand = false

        startRecognizer(
            commandMode = false
        )
    }

    private fun startCommandListening() {
        if (destroyed ||
            commandProcessing ||
            !hasMicrophonePermission()
        ) {
            return
        }

        listeningForWakeWord = false
        listeningForCommand = true

        startRecognizer(
            commandMode = true
        )
    }

    private fun startRecognizer(
        commandMode: Boolean
    ) {
        mainHandler.removeCallbacks(
            restartRunnable
        )

        destroyRecognizerOnly()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            scheduleWakeRestart()
            return
        }

        val recognizer =
            try {
                SpeechRecognizer.createSpeechRecognizer(
                    this
                )
            } catch (_: Exception) {
                scheduleWakeRestart()
                return
            }

        speechRecognizer = recognizer

        recognizer.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                }

                override fun onBeginningOfSpeech() {
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
                }

                override fun onError(
                    error: Int
                ) {
                    listeningForWakeWord = false
                    listeningForCommand = false

                    if (!commandProcessing) {
                        scheduleWakeRestart()
                    }
                }

                override fun onResults(
                    results: Bundle?
                ) {
                    val text =
                        results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                    listeningForWakeWord = false
                    listeningForCommand = false

                    if (commandMode) {
                        processCommand(
                            text
                        )
                    } else {
                        handleWakeRecognition(
                            text
                        )
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                    if (!commandMode) {
                        val text =
                            partialResults
                                ?.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                                )
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()

                        val command =
                            extractCommandAfterWakeWord(
                                text
                            )

                        if (command != null && command.isNotBlank()) {
                            cancelRecognizer()
                            processCommand(
                                command
                            )
                        }
                    }
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                }
            }
        )

        val languageTag =
            Locale.getDefault().toLanguageTag()

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
                    languageTag
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    languageTag
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

        try {
            recognizer.startListening(
                intent
            )
        } catch (_: Exception) {
            listeningForWakeWord = false
            listeningForCommand = false
            scheduleWakeRestart()
        }
    }

    private fun handleWakeRecognition(
        recognizedText: String
    ) {
        val command =
            extractCommandAfterWakeWord(
                recognizedText
            )

        if (command != null) {
            if (command.isBlank()) {
                // Support the natural two-step flow:
                // "Hey Nexus" -> wait for the command.
                startCommandListening()
            } else {
                // Also support the one-step flow:
                // "Hey Nexus, navigate home".
                processCommand(
                    command
                )
            }
            return
        }

        scheduleWakeRestart(
            delayMillis = 350L
        )
    }

    private fun extractCommandAfterWakeWord(
        text: String
    ): String? {
        val normalized =
            text
                .trim()
                .lowercase(Locale.ROOT)

        val wakePhrases =
            listOf(
                "hey nexus",
                "hey nexis",
                "hey nex us",
                "hey next us",
                "hey nexus"
            )

        for (wakePhrase in wakePhrases) {
            val index =
                normalized.indexOf(
                    wakePhrase
                )

            if (index >= 0) {
                val originalAfter =
                    text
                        .substring(
                            (index + wakePhrase.length)
                                .coerceAtMost(text.length)
                        )
                        .trim()

                return originalAfter.ifBlank {
                    ""
                }
            }
        }

        return null
    }

    private fun processCommand(
        command: String
    ) {
        val cleanCommand =
            command.trim()

        if (cleanCommand.isBlank()) {
            scheduleWakeRestart(
                delayMillis = 450L
            )
            return
        }

        commandProcessing = true
        cancelRecognizer()

        serviceScope.launch {
            try {
                val speechLanguage =
                    languageManager
                        .getCurrentState()
                        .speechLanguage

                val result =
                    taskRouter.process(
                        query = cleanCommand,
                        speechLanguageId = speechLanguage.id
                    )

                val bleManager =
                    NexusEyeRuntime.getBleManager()

                if (bleManager == null) {
                    // MainActivity has not registered an active BLE manager,
                    // so use the existing phone fallback.
                    try {
                        if (!ttsManager.setLanguage(speechLanguage)) {
                            commandProcessing = false
                            scheduleWakeRestart(250L)
                            return@launch
                        }

                        ttsManager.speakOnPhoneFallback(
                            text = result.answer,
                            language = speechLanguage,
                            onComplete = {
                                commandProcessing = false
                                scheduleWakeRestart(250L)
                            },
                            onError = {
                                commandProcessing = false
                                scheduleWakeRestart(250L)
                            }
                        )
                    } catch (_: Exception) {
                        commandProcessing = false
                        scheduleWakeRestart(250L)
                    }
                    return@launch
                }

                val audioRouter =
                    NexusEyeAssistantAudioRouter(
                        context = applicationContext,
                        bleManager = bleManager,
                        speechLanguage = speechLanguage,
                        providedTtsManager = ttsManager
                    )

                audioRouter.routeText(
                    text = result.answer,
                    language = speechLanguage,
                    onSuccess = {
                        audioRouter.shutdown()
                        commandProcessing = false
                        scheduleWakeRestart(250L)
                    },
                    onError = {
                        audioRouter.shutdown()
                        commandProcessing = false
                        scheduleWakeRestart(250L)
                    }
                )
            } catch (_: Exception) {
                commandProcessing = false
                scheduleWakeRestart(
                    delayMillis = 250L
                )
            }
        }
    }

    private fun scheduleWakeRestart(
        delayMillis: Long = 800L
    ) {
        if (destroyed ||
            commandProcessing ||
            !hasMicrophonePermission()
        ) {
            return
        }

        mainHandler.removeCallbacks(
            restartRunnable
        )

        mainHandler.postDelayed(
            restartRunnable,
            delayMillis
        )
    }

    private fun cancelRecognizer() {
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        destroyRecognizerOnly()

        listeningForWakeWord = false
        listeningForCommand = false
    }

    private fun destroyRecognizerOnly() {
        val recognizer =
            speechRecognizer

        speechRecognizer =
            null

        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "NEXUS EYE voice activation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Keeps Hey Nexus voice activation available while NEXUS EYE is in the background."
            }

        getSystemService(
            NotificationManager::class.java
        )?.createNotificationChannel(
            channel
        )
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.ic_btn_speak_now
            )
            .setContentTitle(
                "NEXUS EYE voice activation"
            )
            .setContentText(
                "Say Hey Nexus to activate NEXUS EYE."
            )
            .setOngoing(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .build()
    }

    override fun onDestroy() {
        destroyed = true

        mainHandler.removeCallbacksAndMessages(
            null
        )

        cancelRecognizer()

        try {
            ttsManager.shutdown()
        } catch (_: Exception) {
        }

        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    companion object {
        private const val CHANNEL_ID =
            "nexus_eye_wake_word"

        private const val NOTIFICATION_ID =
            4701
    }
}
