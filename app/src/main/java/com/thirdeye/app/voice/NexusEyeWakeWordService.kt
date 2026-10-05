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
import android.util.Log
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
    private var lastPartialCommand: String = ""

    private var listeningForWakeWord = false
    private var listeningForCommand = false
    private var wakeDetectionHandled = false
    private var commandProcessing = false
    private var destroyed = false
    private var speechErrorStreak = 0

    private val restartRunnable = Runnable {
        if (!destroyed && !commandProcessing) {
            Log.i(TAG, "Restart runnable -> starting wake listening")
            startWakeListening()
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "========================================")
        Log.i(TAG, "NexusEyeWakeWordService.onCreate()")
        Log.i(TAG, "Process/service initialization started")
        Log.i(TAG, "========================================")

        createNotificationChannel()

        val notification = buildNotification()

        try {
            Log.i(TAG, "Starting foreground service...")

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

            Log.i(TAG, "Foreground service started successfully")
        } catch (e: Exception) {
            Log.e(
                TAG,
                "FAILED to start foreground service",
                e
            )
        }

        try {
            Log.i(TAG, "Initializing TaskRouter")
            taskRouter = TaskRouter(applicationContext)

            Log.i(TAG, "Initializing TTS manager")
            ttsManager = NexusEyeTtsManager(applicationContext)

            Log.i(TAG, "Initializing LanguageManager")
            languageManager = LanguageManager(applicationContext)

            Log.i(TAG, "Core managers initialized successfully")
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Core manager initialization FAILED",
                e
            )

            stopSelf()
            return
        }

        if (!hasMicrophonePermission()) {
            Log.e(
                TAG,
                "RECORD_AUDIO permission is NOT granted"
            )

            stopSelf()
            return
        }

        Log.i(
            TAG,
            "RECORD_AUDIO permission granted"
        )

        Log.i(
            TAG,
            "Starting wake-word initialization..."
        )

        initializeWakeWordDetector()

        Log.i(
            TAG,
            "Starting wake-word listening..."
        )

        startWakeListening()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.i(
            TAG,
            "onStartCommand(startId=$startId)"
        )

        if (!hasMicrophonePermission()) {
            Log.e(
                TAG,
                "onStartCommand: RECORD_AUDIO permission missing"
            )

            stopSelf()
            return START_NOT_STICKY
        }

        if (!listeningForWakeWord &&
            !listeningForCommand &&
            !commandProcessing
        ) {
            Log.i(
                TAG,
                "Service is idle -> starting wake listening"
            )

            startWakeListening()
        } else {
            Log.i(
                TAG,
                "Service already active: " +
                        "wake=$listeningForWakeWord, " +
                        "command=$listeningForCommand, " +
                        "processing=$commandProcessing"
            )
        }

        return START_STICKY
    }

    private fun hasMicrophonePermission(): Boolean {
        return checkSelfPermission(
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun initializeWakeWordDetector() {

        Log.i(
            TAG,
            "----------------------------------------"
        )

        Log.i(
            TAG,
            "Initializing OpenWakeWord"
        )

        Log.i(
            TAG,
            "Model asset: $WAKE_WORD_MODEL_ASSET"
        )

        Log.i(
            TAG,
            "Threshold: $WAKE_WORD_THRESHOLD"
        )

        Log.i(
            TAG,
            "Debounce: ${WAKE_WORD_DEBOUNCE_MS}ms"
        )

        try {
            /*
             * IMPORTANT:
             * OpenWakeWord in this project does NOT expose release().
             * Only stop() is used here.
             */
            wakeWordDetector?.stop()

            Log.i(
                TAG,
                "Previous wake-word detector stopped"
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Error stopping previous detector",
                e
            )
        }

        wakeWordDetector = null

        try {
            Log.i(
                TAG,
                "Creating OpenWakeWord.Builder..."
            )

            val startTime = System.currentTimeMillis()

            wakeWordDetector =
                OpenWakeWord.Builder(applicationContext)
                    .setModelAsset(WAKE_WORD_MODEL_ASSET)
                    .setThreshold(WAKE_WORD_THRESHOLD)
                    .setDebounceMs(WAKE_WORD_DEBOUNCE_MS)
                    .build()

            val elapsed =
                System.currentTimeMillis() - startTime

            Log.i(
                TAG,
                "OpenWakeWord initialized SUCCESSFULLY"
            )

            Log.i(
                TAG,
                "OpenWakeWord build time: ${elapsed}ms"
            )

            Log.i(
                TAG,
                "Detector object is ${
                    if (wakeWordDetector != null) "NOT NULL" else "NULL"
                }"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "========================================"
            )

            Log.e(
                TAG,
                "OpenWakeWord initialization FAILED",
                e
            )

            Log.e(
                TAG,
                "Model asset: $WAKE_WORD_MODEL_ASSET"
            )

            Log.e(
                TAG,
                "========================================"
            )

            wakeWordDetector = null
        }

        Log.i(
            TAG,
            "----------------------------------------"
        )
    }

    private fun startWakeListening() {

        Log.i(
            TAG,
            "startWakeListening()"
        )

        if (destroyed) {
            Log.w(
                TAG,
                "startWakeListening aborted: service destroyed"
            )
            return
        }

        if (commandProcessing) {
            Log.w(
                TAG,
                "startWakeListening aborted: commandProcessing=true"
            )
            return
        }

        if (listeningForCommand) {
            Log.w(
                TAG,
                "startWakeListening aborted: listeningForCommand=true"
            )
            return
        }

        if (!hasMicrophonePermission()) {
            Log.e(
                TAG,
                "startWakeListening aborted: RECORD_AUDIO permission missing"
            )
            return
        }

        mainHandler.removeCallbacks(restartRunnable)

        destroyRecognizerOnly()

        val detector = wakeWordDetector
            ?: run {
                Log.w(
                    TAG,
                    "Wake detector is NULL -> reinitializing"
                )

                initializeWakeWordDetector()

                wakeWordDetector
            }
            ?: run {
                Log.e(
                    TAG,
                    "Wake detector still NULL after initialization"
                )

                scheduleWakeRestart(
                    WAKE_DETECTOR_RETRY_MS
                )

                return
            }

        if (listeningForWakeWord) {
            Log.w(
                TAG,
                "Wake listener already active"
            )
            return
        }

        wakeDetectionHandled = false
        listeningForWakeWord = true

        Log.i(
            TAG,
            "Wake listener state = ACTIVE"
        )

        try {

            Log.i(
                TAG,
                "Starting OpenWakeWord microphone..."
            )

            val startTime =
                System.currentTimeMillis()

            detector.start { score ->

                if (destroyed ||
                    commandProcessing ||
                    !listeningForWakeWord ||
                    wakeDetectionHandled
                ) {
                    return@start
                }

                if (score >= SCORE_LOG_THRESHOLD) {
                    Log.i(
                        TAG,
                        "Wake-word score=$score"
                    )
                }

                /*
                 * IMPORTANT:
                 *
                 * Only scores >= 0.50 are accepted as "Hey Nexus".
                 *
                 * Your previous threshold was 0.06, which meant scores such
                 * as 0.1079, 0.1853 and 0.2539 could activate the assistant.
                 */
                if (score >= WAKE_WORD_THRESHOLD) {

                    wakeDetectionHandled = true

                    Log.i(
                        TAG,
                        "========================================"
                    )

                    Log.i(
                        TAG,
                        "HEY NEXUS DETECTED"
                    )

                    Log.i(
                        TAG,
                        "Wake score=$score"
                    )

                    Log.i(
                        TAG,
                        "Stopping wake detector for microphone handoff"
                    )

                    Log.i(
                        TAG,
                        "========================================"
                    )

                    handleWakeWordDetected(score)
                }
            }

            val elapsed =
                System.currentTimeMillis() - startTime

            Log.i(
                TAG,
                "OpenWakeWord microphone started"
            )

            Log.i(
                TAG,
                "detector.start() returned in ${elapsed}ms"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "OpenWakeWord microphone START FAILED",
                e
            )

            listeningForWakeWord = false
            wakeDetectionHandled = false

            scheduleWakeRestart(
                WAKE_DETECTOR_RETRY_MS
            )
        }
    }

    private fun handleWakeWordDetected(
        score: Float
    ) {

        Log.i(
            TAG,
            "handleWakeWordDetected(score=$score)"
        )

        /*
         * Stop openWakeWord BEFORE creating SpeechRecognizer.
         */
        listeningForWakeWord = false

        try {
            wakeWordDetector?.stop()

            Log.i(
                TAG,
                "OpenWakeWord stopped successfully"
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Error stopping OpenWakeWord",
                e
            )
        }

        Log.i(
            TAG,
            "Wake word accepted; acknowledging before command capture"
        )

        acknowledgeWakeWordAndStartCommandListening()
    }

    /**
     * This is called ONLY after a score passes WAKE_WORD_THRESHOLD.
     *
     * Therefore "I am listening" is NOT spoken while the service is
     * simply waiting for Hey Nexus.
     */
    private fun acknowledgeWakeWordAndStartCommandListening() {

        if (destroyed || !hasMicrophonePermission()) {
            Log.w(
                TAG,
                "Wake acknowledgement skipped: service unavailable"
            )
            return
        }

        val speechLanguage =
            languageManager
                .getCurrentState()
                .speechLanguage

        if (!ttsManager.setLanguage(speechLanguage)) {

            Log.w(
                TAG,
                "Wake acknowledgement TTS language unavailable; " +
                        "starting command listening"
            )

            mainHandler.postDelayed(
                {
                    if (!destroyed) {
                        startCommandListening()
                    }
                },
                MICROPHONE_HANDOFF_DELAY_MS
            )

            return
        }

        try {

            ttsManager.speakOnPhoneFallback(
                text = "I am listening.",
                language = speechLanguage,

                onComplete = {

                    if (!destroyed &&
                        !commandProcessing &&
                        hasMicrophonePermission()
                    ) {

                        mainHandler.postDelayed(
                            {
                                if (!destroyed) {
                                    startCommandListening()
                                }
                            },
                            MICROPHONE_HANDOFF_DELAY_MS
                        )
                    }
                },

                onError = { error ->

                    Log.w(
                        TAG,
                        "Wake acknowledgement failed: $error"
                    )

                    if (!destroyed &&
                        !commandProcessing &&
                        hasMicrophonePermission()
                    ) {

                        mainHandler.postDelayed(
                            {
                                if (!destroyed) {
                                    startCommandListening()
                                }
                            },
                            MICROPHONE_HANDOFF_DELAY_MS
                        )
                    }
                }
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Wake acknowledgement exception; " +
                        "starting command listening",
                e
            )

            mainHandler.postDelayed(
                {
                    if (!destroyed) {
                        startCommandListening()
                    }
                },
                MICROPHONE_HANDOFF_DELAY_MS
            )
        }
    }

    private fun startCommandListening() {

        Log.i(
            TAG,
            "startCommandListening()"
        )

        if (destroyed) {
            Log.w(
                TAG,
                "Command listening aborted: service destroyed"
            )
            return
        }

        if (commandProcessing) {
            Log.w(
                TAG,
                "Command listening aborted: commandProcessing=true"
            )
            return
        }

        if (!hasMicrophonePermission()) {
            Log.e(
                TAG,
                "Command listening aborted: RECORD_AUDIO missing"
            )
            return
        }

        listeningForWakeWord = false
        listeningForCommand = true
        lastPartialCommand = ""

        destroyRecognizerOnly()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {

            Log.e(
                TAG,
                "SpeechRecognizer is NOT available"
            )

            listeningForCommand = false

            scheduleWakeRestart(
                COMMAND_ERROR_RESTART_MS
            )

            return
        }

        val recognizer =
            try {

                Log.i(
                    TAG,
                    "Creating SpeechRecognizer"
                )

                createSpeechRecognizer()

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "SpeechRecognizer creation FAILED",
                    e
                )

                listeningForCommand = false

                scheduleWakeRestart(
                    COMMAND_ERROR_RESTART_MS
                )

                return
            }

        speechRecognizer = recognizer

        recognizer.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {

                    Log.i(
                        TAG,
                        "SpeechRecognizer: onReadyForSpeech"
                    )
                }

                override fun onBeginningOfSpeech() {

                    Log.i(
                        TAG,
                        "SpeechRecognizer: onBeginningOfSpeech"
                    )
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) = Unit

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) = Unit

                override fun onEndOfSpeech() {

                    Log.i(
                        TAG,
                        "SpeechRecognizer: onEndOfSpeech"
                    )
                }

                override fun onError(
                    error: Int
                ) {

                    Log.e(
                        TAG,
                        "SpeechRecognizer: onError=$error, " +
                                "lastPartial='$lastPartialCommand'"
                    )

                    listeningForCommand = false

                    speechErrorStreak =
                        (speechErrorStreak + 1)
                            .coerceAtMost(6)

                    /*
                     * Never execute a partial transcript after an error.
                     */
                    destroyRecognizerOnly()

                    /*
                     * Silently return to wake-word listening.
                     *
                     * No "I am listening" is spoken here.
                     */
                    if (!commandProcessing) {

                        val delay =
                            commandRetryDelayMs(error)

                        scheduleWakeRestart(delay)
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

                    Log.i(
                        TAG,
                        "SpeechRecognizer result='$text'"
                    )

                    listeningForCommand = false
                    speechErrorStreak = 0

                    destroyRecognizerOnly()

                    if (text.isBlank()) {

                        Log.w(
                            TAG,
                            "SpeechRecognizer returned blank result"
                        )

                        scheduleWakeRestart(
                            COMMAND_ERROR_RESTART_MS
                        )

                    } else {

                        processCommand(text)
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {

                    val partialText =
                        partialResults
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                    if (partialText.isNotBlank()) {

                        lastPartialCommand =
                            partialText

                        Log.d(
                            TAG,
                            "SpeechRecognizer partial='$partialText'"
                        )
                    }
                }

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

                Locale.forLanguageTag(
                    speechLanguage.id
                )
                    .toLanguageTag()
                    .ifBlank {
                        Locale.getDefault()
                            .toLanguageTag()
                    }
            }

        Log.i(
            TAG,
            "Speech language=$languageTag"
        )

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
                    RecognizerIntent
                        .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    1200L
                )

                putExtra(
                    RecognizerIntent
                        .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    1800L
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    5
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                    putExtra(
                        RecognizerIntent.EXTRA_ENABLE_BIASING_DEVICE_CONTEXT,
                        true
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {

                    putExtra(
                        RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION,
                        true
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                        RecognizerIntent.LANGUAGE_SWITCH_BALANCED
                    )
                }
            }

        try {

            Log.i(
                TAG,
                "Starting SpeechRecognizer microphone..."
            )

            recognizer.startListening(intent)

            Log.i(
                TAG,
                "SpeechRecognizer.startListening() returned"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "SpeechRecognizer.startListening FAILED",
                e
            )

            listeningForCommand = false

            destroyRecognizerOnly()

            scheduleWakeRestart(
                COMMAND_ERROR_RESTART_MS
            )
        }
    }

    private fun processCommand(
        command: String
    ) {

        val cleanCommand =
            command.trim()

        Log.i(
            TAG,
            "processCommand('$cleanCommand')"
        )

        if (cleanCommand.isBlank()) {

            Log.w(
                TAG,
                "Command is blank"
            )

            scheduleWakeRestart(
                COMMAND_ERROR_RESTART_MS
            )

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

                Log.i(
                    TAG,
                    "Sending command to TaskRouter"
                )

                val result =
                    taskRouter.process(
                        query = cleanCommand,
                        speechLanguageId = speechLanguage.id
                    )

                Log.i(
                    TAG,
                    "TaskRouter completed"
                )

                val bleManager =
                    NexusEyeRuntime.getBleManager()

                if (bleManager == null) {

                    Log.i(
                        TAG,
                        "BLE manager unavailable -> phone TTS fallback"
                    )

                    try {

                        if (!ttsManager.setLanguage(speechLanguage)) {

                            Log.e(
                                TAG,
                                "TTS language setup failed"
                            )

                            commandProcessing = false

                            scheduleWakeRestart(
                                TTS_RESTART_DELAY_MS
                            )

                            return@launch
                        }

                        ttsManager.speakOnPhoneFallback(
                            text = result.answer,
                            language = speechLanguage,

                            onComplete = {

                                Log.i(
                                    TAG,
                                    "Phone TTS completed"
                                )

                                commandProcessing = false

                                scheduleWakeRestart(
                                    TTS_RESTART_DELAY_MS
                                )
                            },

                            onError = {

                                Log.e(
                                    TAG,
                                    "Phone TTS error"
                                )

                                commandProcessing = false

                                scheduleWakeRestart(
                                    TTS_RESTART_DELAY_MS
                                )
                            }
                        )

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "Phone TTS FAILED",
                            e
                        )

                        commandProcessing = false

                        scheduleWakeRestart(
                            TTS_RESTART_DELAY_MS
                        )
                    }

                    return@launch
                }

                Log.i(
                    TAG,
                    "BLE manager available -> routing audio through BLE"
                )

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

                        Log.i(
                            TAG,
                            "BLE/audio routing completed"
                        )

                        audioRouter.shutdown()

                        commandProcessing = false

                        scheduleWakeRestart(
                            TTS_RESTART_DELAY_MS
                        )
                    },

                    onError = {

                        Log.e(
                            TAG,
                            "BLE/audio routing failed"
                        )

                        audioRouter.shutdown()

                        commandProcessing = false

                        scheduleWakeRestart(
                            TTS_RESTART_DELAY_MS
                        )
                    }
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Command processing FAILED",
                    e
                )

                commandProcessing = false

                scheduleWakeRestart(
                    TTS_RESTART_DELAY_MS
                )
            }
        }
    }

    private fun createSpeechRecognizer(): SpeechRecognizer {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {

            throw IllegalStateException(
                "No Android speech recognition service is available"
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {

            try {

                Log.i(
                    TAG,
                    "Using on-device SpeechRecognizer"
                )

                return SpeechRecognizer
                    .createOnDeviceSpeechRecognizer(this)

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "On-device SpeechRecognizer unavailable; " +
                            "using system recognizer",
                    e
                )
            }
        }

        Log.i(
            TAG,
            "Using system SpeechRecognizer"
        )

        return SpeechRecognizer.createSpeechRecognizer(this)
    }

    private fun commandRetryDelayMs(
        error: Int
    ): Long {

        val base =
            when (error) {

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_SERVER ->
                    500L

                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    1000L

                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    300L

                else ->
                    750L
            }

        return (
                base *
                        (
                                1L shl
                                        (speechErrorStreak - 1)
                                            .coerceIn(0, 4)
                                )
                )
            .coerceAtMost(5000L)
    }

    private fun scheduleWakeRestart(
        delayMillis: Long = DEFAULT_WAKE_RESTART_MS
    ) {

        if (destroyed) {

            Log.w(
                TAG,
                "Wake restart skipped: service destroyed"
            )

            return
        }

        if (commandProcessing) {

            Log.w(
                TAG,
                "Wake restart skipped: commandProcessing=true"
            )

            return
        }

        if (!hasMicrophonePermission()) {

            Log.e(
                TAG,
                "Wake restart skipped: RECORD_AUDIO missing"
            )

            return
        }

        Log.i(
            TAG,
            "Scheduling wake restart in ${delayMillis}ms"
        )

        mainHandler.removeCallbacks(
            restartRunnable
        )

        mainHandler.postDelayed(
            restartRunnable,
            delayMillis
        )
    }

    private fun destroyRecognizerOnly() {

        val recognizer =
            speechRecognizer

        speechRecognizer = null

        if (recognizer != null) {

            Log.i(
                TAG,
                "Destroying SpeechRecognizer"
            )

            try {

                recognizer.cancel()

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "SpeechRecognizer.cancel() failed",
                    e
                )
            }

            try {

                recognizer.destroy()

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "SpeechRecognizer.destroy() failed",
                    e
                )
            }
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
                    "Keeps Hey Nexus voice activation available " +
                            "while NEXUS EYE is in the background."
            }

        getSystemService(
            NotificationManager::class.java
        )?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {

        return NotificationCompat
            .Builder(
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

        Log.i(
            TAG,
            "========================================"
        )

        Log.i(
            TAG,
            "NexusEyeWakeWordService.onDestroy()"
        )

        Log.i(
            TAG,
            "Stopping wake-word service"
        )

        destroyed = true

        mainHandler.removeCallbacksAndMessages(
            null
        )

        try {

            wakeWordDetector?.stop()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Wake detector stop failed during destroy",
                e
            )
        }

        /*
         * IMPORTANT:
         * There is NO wakeWordDetector?.release() here.
         *
         * Your OpenWakeWord implementation exposes stop(), not release().
         */

        wakeWordDetector = null

        destroyRecognizerOnly()

        try {

            ttsManager.shutdown()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "TTS shutdown failed",
                e
            )
        }

        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    companion object {

        private const val TAG =
            "NexusEyeWakeWord"

        private const val CHANNEL_ID =
            "nexus_eye_wake_word"

        private const val NOTIFICATION_ID =
            4701

        private const val WAKE_WORD_MODEL_ASSET =
            "hey_nexus.onnx"

        /*
         * IMPORTANT FIX:
         *
         * OLD:
         *     0.06f
         *
         * That was far too sensitive. Your logs showed false wake
         * scores around 0.10 - 0.25.
         *
         * NEW:
         *     0.50f
         *
         * Your trained model's validation positives were above ~0.95,
         * so 0.50 gives us a much safer starting point.
         */
        private const val WAKE_WORD_THRESHOLD =
            0.50f

        /*
         * IMPORTANT FIX:
         *
         * Prevent repeated wake events during microphone/TTS handoff.
         */
        private const val WAKE_WORD_DEBOUNCE_MS =
            3000L

        /*
         * Short microphone handoff:
         *
         * OpenWakeWord stops first.
         * SpeechRecognizer starts after this delay.
         */
        private const val MICROPHONE_HANDOFF_DELAY_MS =
            50L

        private const val COMMAND_ERROR_RESTART_MS =
            100L

        private const val TTS_RESTART_DELAY_MS =
            100L

        private const val DEFAULT_WAKE_RESTART_MS =
            100L

        private const val WAKE_DETECTOR_RETRY_MS =
            500L

        /*
         * Avoid flooding Logcat with tiny wake scores.
         */
        private const val SCORE_LOG_THRESHOLD =
            0.01f
    }
}