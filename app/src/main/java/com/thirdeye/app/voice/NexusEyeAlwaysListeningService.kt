package com.thirdeye.app.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.thirdeye.app.R
import com.thirdeye.app.audio.NexusEyeTtsManager
import com.thirdeye.app.intelligence.TaskRouter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NexusEyeAlwaysListeningService : Service() {

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate
        )

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isProcessing = false
    private var restartScheduled = false

    private lateinit var taskRouter: TaskRouter
    private lateinit var ttsManager: NexusEyeTtsManager

    override fun onCreate() {
        super.onCreate()

        taskRouter =
            TaskRouter(
                applicationContext
            )

        ttsManager =
            NexusEyeTtsManager(
                applicationContext
            )

        createNotificationChannel()
        startAsForegroundService()
        createSpeechRecognizer()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (
            !NexusEyeAlwaysListeningController.isEnabled(
                applicationContext
            )
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        scheduleListening(
            delayMillis = 250L
        )

        return START_STICKY
    }

    private fun startAsForegroundService() {
        val notification =
            buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
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
                "NEXUS EYE is listening"
            )
            .setContentText(
                "Say “Hey Nexus” to give a voice command."
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

    private fun createNotificationChannel() {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "NEXUS EYE voice control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Continuous microphone listening for the Hey Nexus wake phrase."
            }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(
            channel
        )
    }

    private fun createSpeechRecognizer() {
        if (
            !SpeechRecognizer.isRecognitionAvailable(
                applicationContext
            )
        ) {
            return
        }

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(
                applicationContext
            ).also { recognizer ->
                recognizer.setRecognitionListener(
                    recognitionListener
                )
            }
    }

    private val recognitionListener =
        object : RecognitionListener {

            override fun onReadyForSpeech(
                params: android.os.Bundle?
            ) {
                isListening = true
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
                isListening = false
            }

            override fun onError(
                error: Int
            ) {
                isListening = false
                scheduleListening(
                    delayMillis = 500L
                )
            }

            override fun onResults(
                results: android.os.Bundle?
            ) {
                isListening = false

                val phrases =
                    results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                        ?: return scheduleListening(
                            delayMillis = 250L
                        )

                val heardText =
                    phrases.firstOrNull()
                        ?.trim()
                        .orEmpty()

                handleRecognizedSpeech(
                    heardText
                )
            }

            override fun onPartialResults(
                partialResults: android.os.Bundle?
            ) {
            }

            override fun onEvent(
                eventType: Int,
                params: android.os.Bundle?
            ) {
            }
        }

    private fun handleRecognizedSpeech(
        heardText: String
    ) {
        val normalized =
            normalize(
                heardText
            )

        val wakeIndex =
            normalized.indexOf(
                WAKE_PHRASE
            )

        if (wakeIndex < 0) {
            scheduleListening(
                delayMillis = 200L
            )
            return
        }

        val command =
            normalized
                .substring(
                    wakeIndex + WAKE_PHRASE.length
                )
                .trim()

        if (command.isBlank()) {
            speakAndListen(
                "Yes?"
            )
            return
        }

        processCommand(
            command
        )
    }

    private fun processCommand(
        command: String
    ) {
        if (isProcessing) {
            return
        }

        isProcessing = true

        serviceScope.launch {
            try {
                val result =
                    taskRouter.process(
                        query = command,
                        speechLanguageId =
                            currentSpeechLanguageId()
                    )

                speakAndListen(
                    result.answer
                )
            } catch (exception: Exception) {
                speakAndListen(
                    exception.message
                        ?: "I could not process that command."
                )
            }
        }
    }

    private fun speakAndListen(
        text: String
    ) {
        mainHandler.post {
            try {
                ttsManager.speakOnPhoneFallback(
                    text = text,
                    onComplete = {
                        isProcessing = false
                        scheduleListening(
                            delayMillis = 350L
                        )
                    },
                    onError = {
                        isProcessing = false
                        scheduleListening(
                            delayMillis = 350L
                        )
                    }
                )
            } catch (_: Exception) {
                isProcessing = false
                scheduleListening(
                    delayMillis = 350L
                )
            }
        }
    }

    private fun currentSpeechLanguageId(): String {
        val language =
            Locale.getDefault().language

        return if (
            language.equals(
                "hi",
                ignoreCase = true
            )
        ) {
            "hi"
        } else {
            "en"
        }
    }

    private fun normalize(
        text: String
    ): String {
        return text
            .lowercase(Locale.ROOT)
            .replace(
                Regex("[^\\p{L}\\p{N}]+"),
                " "
            )
            .trim()
            .replace(
                Regex("\\s+"),
                " "
            )
    }

    private fun scheduleListening(
        delayMillis: Long
    ) {
        if (
            restartScheduled ||
            isProcessing ||
            !NexusEyeAlwaysListeningController.isEnabled(
                applicationContext
            )
        ) {
            return
        }

        restartScheduled = true

        mainHandler.postDelayed(
            {
                restartScheduled = false
                startListening()
            },
            delayMillis
        )
    }

    private fun startListening() {
        if (
            isListening ||
            isProcessing ||
            !NexusEyeAlwaysListeningController.isEnabled(
                applicationContext
            )
        ) {
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val recognizer =
            speechRecognizer
                ?: return

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
                    Locale.getDefault().toLanguageTag()
                )
                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
                )
                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )
            }

        try {
            recognizer.startListening(
                intent
            )
            isListening = true
        } catch (_: Exception) {
            isListening = false
            scheduleListening(
                delayMillis = 1000L
            )
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(
            null
        )

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null

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
            "nexus_eye_always_listening"

        private const val NOTIFICATION_ID =
            4701

        private const val WAKE_PHRASE =
            "hey nexus"
    }
}
