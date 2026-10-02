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
import com.openwakeword.OpenWakeWord
import com.thirdeye.app.NexusEyeRuntime
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
 * Always-on local wake-word service.
 *
 * Microphone ownership is deliberately exclusive:
 *
 *   Hey Nexus -> openWakeWord owns the microphone
 *   detection -> openWakeWord stops
 *   command   -> Android SpeechRecognizer owns the microphone
 *   result/TTS complete -> SpeechRecognizer stops -> openWakeWord resumes
 *
 * This prevents the wake-word detector and SpeechRecognizer from fighting
 * over AudioRecord at the same time.
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

    private var wakeWordDetector: OpenWakeWord? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private var listeningForWakeWord = false
    private var listeningForCommand = false
    private var wakeDetectionHandled = false
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

        val notification = buildNotification()

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

        taskRouter = TaskRouter(applicationContext)
        ttsManager = NexusEyeTtsManager(applicationContext)
        languageManager = LanguageManager(applicationContext)

        if (!hasMicrophonePermission()) {
            stopSelf()
            return
        }

        initializeWakeWordDetector()
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

    private fun initializeWakeWordDetector() {
        try {
            wakeWordDetector?.stop()
            wakeWordDetector?.release()
        } catch (_: Exception) {
        }

        wakeWordDetector = null

        try {
            wakeWordDetector =
                OpenWakeWord.Builder(applicationContext)
                    .setModelAsset(WAKE_WORD_MODEL_ASSET)
                    .setThreshold(WAKE_WORD_THRESHOLD)
                    .setDebounceMs(WAKE_WORD_DEBOUNCE_MS)
                    .build()
        } catch (_: Exception) {
            // If the ONNX asset is missing or the detector cannot initialize,
            // do not start SpeechRecognizer as a fake wake-word detector.
            wakeWordDetector = null
        }
    }

    private fun startWakeListening() {
        if (destroyed ||
            commandProcessing ||
            listeningForCommand ||
            !hasMicrophonePermission()
        ) {
            return
        }

        mainHandler.removeCallbacks(restartRunnable)
        destroyRecognizerOnly()

        val detector = wakeWordDetector ?: run {
            initializeWakeWordDetector()
            wakeWordDetector
        } ?: run {
            scheduleWakeRestart(WAKE_DETECTOR_RETRY_MS)
            return
        }

        if (listeningForWakeWord) {
            return
        }

        wakeDetectionHandled = false
        listeningForWakeWord = true

        try {
            detector.start { score ->
                if (destroyed ||
                    commandProcessing ||
                    !listeningForWakeWord ||
                    wakeDetectionHandled
                ) {
                    return@start
                }

                wakeDetectionHandled = true
                handleWakeWordDetected(score)
            }
        } catch (_: Exception) {
            listeningForWakeWord = false
            wakeDetectionHandled = false
            scheduleWakeRestart(WAKE_DETECTOR_RETRY_MS)
        }
    }

    private fun handleWakeWordDetected(score: Float) {
        // Stop openWakeWord BEFORE creating SpeechRecognizer. This is the
        // critical microphone handoff that prevents both engines from racing.
        listeningForWakeWord = false

        try {
            wakeWordDetector?.stop()
        } catch (_: Exception) {
        }

        mainHandler.postDelayed(
            {
                if (!destroyed &&
                    !commandProcessing &&
                    hasMicrophonePermission()
                ) {
                    startCommandListening()
                }
            },
            MICROPHONE_HANDOFF_DELAY_MS
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

        destroyRecognizerOnly()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            listeningForCommand = false
            scheduleWakeRestart(COMMAND_ERROR_RESTART_MS)
            return
        }

        val recognizer =
            try {
                SpeechRecognizer.createSpeechRecognizer(this)
            } catch (_: Exception) {
                listeningForCommand = false
                scheduleWakeRestart(COMMAND_ERROR_RESTART_MS)
                return
            }

        speechRecognizer = recognizer

        recognizer.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    listeningForCommand = false
                    destroyRecognizerOnly()

                    if (!commandProcessing) {
                        scheduleWakeRestart(COMMAND_ERROR_RESTART_MS)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val text =
                        results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                    listeningForCommand = false
                    destroyRecognizerOnly()

                    if (text.isBlank()) {
                        scheduleWakeRestart(COMMAND_ERROR_RESTART_MS)
                    } else {
                        processCommand(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) = Unit
            }
        )

        val speechLanguage =
            languageManager
                .getCurrentState()
                .speechLanguage

        val languageTag =
            if (speechLanguage.id == "hi") {
                "hi-IN"
            } else {
                Locale.forLanguageTag(speechLanguage.id)
                    .toLanguageTag()
                    .ifBlank { Locale.getDefault().toLanguageTag() }
            }

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
                    false
                )
                // Keep the command session short after the user finishes
                // speaking. Some recognition providers honor these values.
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    350L
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    600L
                )
                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    5
                )
            }

        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            listeningForCommand = false
            destroyRecognizerOnly()
            scheduleWakeRestart(COMMAND_ERROR_RESTART_MS)
        }
    }

    private fun processCommand(command: String) {
        val cleanCommand = command.trim()

        if (cleanCommand.isBlank()) {
            scheduleWakeRestart(COMMAND_ERROR_RESTART_MS)
            return
        }

        commandProcessing = true
        destroyRecognizerOnly()

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
                    try {
                        if (!ttsManager.setLanguage(speechLanguage)) {
                            commandProcessing = false
                            scheduleWakeRestart(TTS_RESTART_DELAY_MS)
                            return@launch
                        }

                        ttsManager.speakOnPhoneFallback(
                            text = result.answer,
                            language = speechLanguage,
                            onComplete = {
                                commandProcessing = false
                                scheduleWakeRestart(TTS_RESTART_DELAY_MS)
                            },
                            onError = {
                                commandProcessing = false
                                scheduleWakeRestart(TTS_RESTART_DELAY_MS)
                            }
                        )
                    } catch (_: Exception) {
                        commandProcessing = false
                        scheduleWakeRestart(TTS_RESTART_DELAY_MS)
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
                        scheduleWakeRestart(TTS_RESTART_DELAY_MS)
                    },
                    onError = {
                        audioRouter.shutdown()
                        commandProcessing = false
                        scheduleWakeRestart(TTS_RESTART_DELAY_MS)
                    }
                )
            } catch (_: Exception) {
                commandProcessing = false
                scheduleWakeRestart(TTS_RESTART_DELAY_MS)
            }
        }
    }

    private fun scheduleWakeRestart(delayMillis: Long = DEFAULT_WAKE_RESTART_MS) {
        if (destroyed ||
            commandProcessing ||
            !hasMicrophonePermission()
        ) {
            return
        }

        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.postDelayed(restartRunnable, delayMillis)
    }

    private fun destroyRecognizerOnly() {
        val recognizer = speechRecognizer
        speechRecognizer = null

        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }

        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        listeningForCommand = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
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

        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("NEXUS EYE voice activation")
            .setContentText("Say Hey Nexus to activate NEXUS EYE.")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        destroyed = true
        mainHandler.removeCallbacksAndMessages(null)

        try {
            wakeWordDetector?.stop()
        } catch (_: Exception) {
        }

        try {
            wakeWordDetector?.release()
        } catch (_: Exception) {
        }

        wakeWordDetector = null
        destroyRecognizerOnly()

        try {
            ttsManager.shutdown()
        } catch (_: Exception) {
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "nexus_eye_wake_word"
        private const val NOTIFICATION_ID = 4701

        private const val WAKE_WORD_MODEL_ASSET = "hey_nexus.onnx"

        // The trained model had a best validation point around this range;
        // keep the diagnostic threshold configurable in one place.
        private const val WAKE_WORD_THRESHOLD = 0.10f
        private const val WAKE_WORD_DEBOUNCE_MS = 2000L

        // Short handoff: openWakeWord must release AudioRecord before the
        // Android speech recognizer attempts to acquire the microphone.
        private const val MICROPHONE_HANDOFF_DELAY_MS = 50L
        private const val COMMAND_ERROR_RESTART_MS = 100L
        private const val TTS_RESTART_DELAY_MS = 100L
        private const val DEFAULT_WAKE_RESTART_MS = 100L
        private const val WAKE_DETECTOR_RETRY_MS = 500L
    }
}
