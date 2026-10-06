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

    // Wake confirmation state. A single model spike must never wake the app.
    private var wakeHighScoreCount = 0
    private var wakeLastHighScoreAt = 0L
    private var wakePeakScore = 0f

    // Some Android devices expose an on-device recognizer but do not have the
    // requested language pack installed. We fall back to the system recognizer
    // once before abandoning the command capture.
    private var languageUnavailableFallbackAttempted = false

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

    /**
     * Initializes the OpenWakeWord detector.
     *
     * This method intentionally logs the complete initialization path.
     * Previously all exceptions were silently swallowed, making it
     * impossible to know whether hey_nexus.onnx was actually loading.
     */
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
            wakeWordDetector?.stop()

            Log.i(
                TAG,
                "Previous wake-word detector stopped"
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Error releasing previous detector",
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
                "Detector object is ${if (wakeWordDetector != null) "NOT NULL" else "NULL"}"
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
        wakeHighScoreCount = 0
        wakeLastHighScoreAt = 0L
        wakePeakScore = 0f
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

                /*
                 * Log the score only when it is useful for diagnostics.
                 * A low threshold can generate many callbacks, so we don't
                 * want to flood Logcat with every low-level score.
                 */
                if (score >= SCORE_LOG_THRESHOLD) {
                    Log.i(
                        TAG,
                        "Wake-word score=$score"
                    )
                }

                // Require several consecutive high-confidence model frames.
                // This rejects isolated score spikes, which were responsible
                // for the previous fake "Hey Nexus" activations.
                val now = System.currentTimeMillis()

                if (score >= WAKE_WORD_THRESHOLD) {
                    if (wakeLastHighScoreAt == 0L ||
                        now - wakeLastHighScoreAt > WAKE_CONFIRMATION_MAX_GAP_MS
                    ) {
                        wakeHighScoreCount = 0
                        wakePeakScore = 0f
                    }

                    wakeHighScoreCount++
                    wakeLastHighScoreAt = now
                    wakePeakScore = maxOf(wakePeakScore, score)

                    Log.i(
                        TAG,
                        "Wake candidate: score=$score, " +
                                "count=$wakeHighScoreCount/$WAKE_CONFIRMATION_FRAMES, " +
                                "peak=$wakePeakScore"
                    )

                    if (wakeHighScoreCount >= WAKE_CONFIRMATION_FRAMES &&
                        wakePeakScore >= WAKE_CONFIRMATION_PEAK_THRESHOLD
                    ) {
                        wakeDetectionHandled = true

                        Log.i(
                            TAG,
                            "========================================"
                        )

                        Log.i(
                            TAG,
                            "🔥 HEY NEXUS DETECTED"
                        )

                        Log.i(
                            TAG,
                            "Wake score=$score, peak=$wakePeakScore"
                        )

                        Log.i(
                            TAG,
                            "Stopping wake detector for microphone handoff"
                        )

                        Log.i(
                            TAG,
                            "========================================"
                        )

                        handleWakeWordDetected(wakePeakScore)
                    }
                } else {
                    wakeHighScoreCount = 0
                    wakeLastHighScoreAt = 0L
                    wakePeakScore = 0f
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
            wakeHighScoreCount = 0
            wakeLastHighScoreAt = 0L
            wakePeakScore = 0f

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
         * This is the critical microphone handoff that prevents both
         * engines from racing over AudioRecord.
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
     * Give the user an immediate audible confirmation that Hey Nexus was
     * actually detected. The command recognizer starts only after the short
     * acknowledgement finishes, so it cannot accidentally transcribe the
     * acknowledgement itself as the user's command.
     */
    private fun acknowledgeWakeWordAndStartCommandListening() {
        if (destroyed || !hasMicrophonePermission()) {
            Log.w(TAG, "Wake acknowledgement skipped: service unavailable")
            return
        }

        val speechLanguage =
            languageManager
                .getCurrentState()
                .speechLanguage

        try {
            // IMPORTANT: do not gate the wake acknowledgement on setLanguage().
            // The TTS manager already receives the speechLanguage below. On some
            // devices setLanguage() can report false even though phone TTS is
            // perfectly able to speak, which previously caused a real wake to
            // produce no "I am listening" acknowledgement at all.
            Log.i(TAG, "Starting wake acknowledgement TTS")

            ttsManager.speakOnPhoneFallback(
                text = "I am listening.",
                language = speechLanguage,
                onComplete = {
                    if (!destroyed && !commandProcessing && hasMicrophonePermission()) {
                        mainHandler.postDelayed(
                            { if (!destroyed) startCommandListening() },
                            MICROPHONE_HANDOFF_DELAY_MS
                        )
                    }
                },
                onError = { error ->
                    Log.w(TAG, "Wake acknowledgement failed: $error")
                    if (!destroyed && !commandProcessing && hasMicrophonePermission()) {
                        mainHandler.postDelayed(
                            { if (!destroyed) startCommandListening() },
                            MICROPHONE_HANDOFF_DELAY_MS
                        )
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Wake acknowledgement exception; starting command listening", e)
            mainHandler.postDelayed(
                { if (!destroyed) startCommandListening() },
                MICROPHONE_HANDOFF_DELAY_MS
            )
        }
    }

    private fun startCommandListening(forceSystemRecognizer: Boolean = false) {

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

        if (!forceSystemRecognizer) {
            languageUnavailableFallbackAttempted = false
        }

        listeningForWakeWord = false
        lastPartialCommand = ""

        // Cancel any stale recognizer BEFORE marking the new command session
        // active. destroyRecognizerOnly() intentionally sets the command state
        // to false, so the order here is important.
        destroyRecognizerOnly()
        listeningForCommand = true

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

                createSpeechRecognizer(
                    forceSystemRecognizer = forceSystemRecognizer
                )

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
                ) {
                    if (rmsdB > -45f) {
                        Log.d(
                            TAG,
                            "SpeechRecognizer: RMS=$rmsdB dB"
                        )
                    }
                }

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
                        "SpeechRecognizer: onError=$error, lastPartial='$lastPartialCommand'"
                    )

                    listeningForCommand = false
                    speechErrorStreak = (speechErrorStreak + 1).coerceAtMost(6)

                    // Never execute an incomplete partial transcript after a
                    // failed recognition session. A partial such as "call" or
                    // "send" can be dangerous if turned into an action.
                    destroyRecognizerOnly()

                    // ERROR_LANGUAGE_UNAVAILABLE is common when the on-device
                    // recognizer exists but its requested language pack is not
                    // installed. Retry once using Android's normal recognizer
                    // instead of immediately returning to wake mode.
                    if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE &&
                        !forceSystemRecognizer &&
                        !languageUnavailableFallbackAttempted &&
                        !destroyed &&
                        !commandProcessing
                    ) {
                        languageUnavailableFallbackAttempted = true

                        Log.w(
                            TAG,
                            "On-device language unavailable -> retrying with system SpeechRecognizer"
                        )

                        mainHandler.postDelayed(
                            {
                                if (!destroyed && !commandProcessing) {
                                    startCommandListening(
                                        forceSystemRecognizer = true
                                    )
                                }
                            },
                            SPEECH_RECOGNIZER_FALLBACK_DELAY_MS
                        )

                        return
                    }

                    if (!commandProcessing) {
                        val delay = commandRetryDelayMs(error)
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
                    languageUnavailableFallbackAttempted = false

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
                        lastPartialCommand = partialText
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

        // Android recognizers are more reliable with a concrete locale than
        // a bare language code such as "en".
        val languageTag =
            when (speechLanguage.id.lowercase(Locale.ROOT)) {
                "hi", "hi-in" -> "hi-IN"
                "en", "en-in" -> "en-IN"
                "en-us" -> "en-US"
                else -> Locale.forLanguageTag(
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

            // Explicitly release phone TTS before Android acquires the
            // microphone. Some OEM audio stacks report onReadyForSpeech
            // while the previous TTS route is still settling, producing
            // ERROR_NO_MATCH without ever delivering onBeginningOfSpeech.
            try {
                ttsManager.stop()
            } catch (ttsError: Exception) {
                Log.w(
                    TAG,
                    "Could not stop TTS before microphone acquisition",
                    ttsError
                )
            }

            Log.i(
                TAG,
                "Starting SpeechRecognizer microphone after TTS/audio handoff..."
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

    private fun createSpeechRecognizer(
        forceSystemRecognizer: Boolean = false
    ): SpeechRecognizer {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            throw IllegalStateException("No Android speech recognition service is available")
        }

        if (!forceSystemRecognizer &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        ) {
            try {
                Log.i(TAG, "Using on-device SpeechRecognizer")
                return SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } catch (e: Exception) {
                Log.w(TAG, "On-device SpeechRecognizer unavailable; using system recognizer", e)
            }
        }

        if (forceSystemRecognizer) {
            Log.i(TAG, "Using system SpeechRecognizer after on-device language fallback")
        } else {
            Log.i(TAG, "Using system SpeechRecognizer")
        }

        return SpeechRecognizer.createSpeechRecognizer(this)
    }

    private fun commandRetryDelayMs(error: Int): Long {
        val base = when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SERVER -> 500L
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 1000L
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 300L
            else -> 750L
        }
        return (base * (1L shl (speechErrorStreak - 1).coerceIn(0, 4)))
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
                    "Keeps Hey Nexus voice activation available while NEXUS EYE is in the background."
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
         * A single score spike must not activate the assistant. The value is
         * deliberately much higher than the old 0.06 threshold.
         */
        private const val WAKE_WORD_THRESHOLD =
            0.50f

        private const val WAKE_CONFIRMATION_FRAMES =
            3

        private const val WAKE_CONFIRMATION_PEAK_THRESHOLD =
            0.60f

        private const val WAKE_CONFIRMATION_MAX_GAP_MS =
            700L

        private const val WAKE_WORD_DEBOUNCE_MS =
            3000L

        /*
         * OpenWakeWord must fully relinquish the microphone before Android's
         * SpeechRecognizer is allowed to acquire it.
         */
        private const val MICROPHONE_HANDOFF_DELAY_MS =
            1200L

        private const val COMMAND_ERROR_RESTART_MS =
            1500L

        private const val TTS_RESTART_DELAY_MS =
            500L

        private const val DEFAULT_WAKE_RESTART_MS =
            1500L

        private const val WAKE_DETECTOR_RETRY_MS =
            1500L

        private const val SPEECH_RECOGNIZER_FALLBACK_DELAY_MS =
            750L

        /*
         * Avoid flooding Logcat with tiny wake scores.
         * Scores >= 0.01 are shown.
         */
        private const val SCORE_LOG_THRESHOLD =
            0.10f
    }
}
