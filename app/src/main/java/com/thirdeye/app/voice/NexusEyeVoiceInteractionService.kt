package com.thirdeye.app.voice

import android.Manifest
import android.os.Bundle
import android.content.pm.PackageManager
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.util.Log
import com.openwakeword.OpenWakeWord
import java.lang.ref.WeakReference
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * System-managed Android Digital Assistant entry point.
 *
 * When Nexus-Eye owns ROLE_ASSISTANT, Android keeps this service alive so it
 * can provide hotwording. The heavier command/TTS work remains in the session.
 */
class NexusEyeVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "NexusEyeAssistant"
        private const val MODEL_ASSET = "hey_nexus.onnx"
        private const val THRESHOLD = 0.06f
        private const val DEBOUNCE_MS = 1200L

        @Volatile
        private var activeInstance: WeakReference<NexusEyeVoiceInteractionService>? = null

        fun resumeHotwording() {
            activeInstance?.get()?.startHotwording()
        }
    }

    private var wakeWordDetector: OpenWakeWord? = null
    private var listening = false
    private var sessionShowing = false

    override fun onCreate() {
        super.onCreate()
        activeInstance = WeakReference(this)
    }

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "Nexus-Eye is now the active Android voice assistant")
        startHotwording()
    }

    private fun startHotwording() {
        if (sessionShowing || listening ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            wakeWordDetector?.stop()
            wakeWordDetector?.release()
            wakeWordDetector = OpenWakeWord.Builder(applicationContext)
                .setModelAsset(MODEL_ASSET)
                .setThreshold(THRESHOLD)
                .setDebounceMs(DEBOUNCE_MS)
                .build()

            listening = true
            wakeWordDetector?.start { score ->
                if (!listening || sessionShowing || score < THRESHOLD) return@start
                listening = false
                try { wakeWordDetector?.stop() } catch (_: Exception) {}
                sessionShowing = true
                Log.i(TAG, "Hey Nexus detected by system assistant: score=$score")
                try {
                    showSession(Bundle(), VoiceInteractionSession.SHOW_WITH_ASSIST)
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to show assistant session", error)
                    sessionShowing = false
                    startHotwording()
                }
            }
        } catch (error: Exception) {
            listening = false
            Log.e(TAG, "Assistant hotword initialization failed", error)
        }
    }

    fun onSessionHidden() {
        sessionShowing = false
        startHotwording()
    }

    override fun onShutdown() {
        listening = false
        sessionShowing = false
        try { wakeWordDetector?.stop() } catch (_: Exception) {}
        try { wakeWordDetector?.release() } catch (_: Exception) {}
        wakeWordDetector = null
        activeInstance = null
        super.onShutdown()
    }
}

/** Active voice session used by the Android assistant entry point. */
class NexusEyeVoiceInteractionSession(
    context: android.content.Context
) : VoiceInteractionSession(context) {

    companion object {
        private const val SESSION_TAG = "NexusEyeVoiceSession"
    }

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() +
                kotlinx.coroutines.Dispatchers.Main.immediate
    )
    private val speechRecognizer = NexusEyeSpeechRecognizer(context)
    private val taskRouter = com.thirdeye.app.intelligence.TaskRouter(context.applicationContext)
    private val ttsManager = com.thirdeye.app.audio.NexusEyeTtsManager(context.applicationContext)
    private val languageManager = com.thirdeye.app.language.LanguageManager(context.applicationContext)
    private var sessionActive = false

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        sessionActive = true
        Log.i(SESSION_TAG, "Nexus-Eye Digital Assistant session shown")

        val language = languageManager.getCurrentState().speechLanguage
        speechRecognizer.startListening(
            language = language,
            onFinalResult = { command ->
                if (sessionActive) processCommand(command)
            },
            onError = { error ->
                if (sessionActive) speak(error) { hide() }
            }
        )
    }

    private fun processCommand(command: String) {
        scope.launch {
            try {
                val language = languageManager.getCurrentState().speechLanguage
                val result = taskRouter.process(command.trim(), language.id)
                speak(result.answer) { hide() }
            } catch (error: Exception) {
                Log.e(SESSION_TAG, "Assistant command failed", error)
                speak("I could not process that command.") { hide() }
            }
        }
    }

    private fun speak(text: String, onComplete: () -> Unit) {
        val language = languageManager.getCurrentState().speechLanguage
        try {
            if (!ttsManager.setLanguage(language)) {
                onComplete()
                return
            }
            ttsManager.speakOnPhoneFallback(
                text = text,
                language = language,
                onComplete = onComplete,
                onError = { onComplete() }
            )
        } catch (_: Exception) {
            onComplete()
        }
    }

    override fun onHide() {
        sessionActive = false
        speechRecognizer.stopListening()
        NexusEyeVoiceInteractionService.resumeHotwording()
        Log.i(SESSION_TAG, "Nexus-Eye Digital Assistant session hidden")
        super.onHide()
    }

    override fun onDestroy() {
        sessionActive = false
        speechRecognizer.shutdown()
        ttsManager.shutdown()
        scope.cancel()
        super.onDestroy()
    }
}
