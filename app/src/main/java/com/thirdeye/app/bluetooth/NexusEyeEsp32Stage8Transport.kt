package com.thirdeye.app.bluetooth

import android.content.Context
import com.thirdeye.app.audio.NexusEyeTtsManager
import com.thirdeye.app.language.NexusEyeLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Android-side Stage 8 wearable transport foundation.
 *
 * This file deliberately does not replace NexusEyeBleManager or invent a second
 * BLE connection implementation. It provides the transport-independent pieces
 * that the real BLE layer can consume once the physical ESP32-S3 is available:
 *
 * - wearable link state
 * - TTS audio artifact creation
 * - audio format transparency
 * - deterministic byte chunking
 * - transfer identifiers
 * - transfer progress/state
 *
 * Important:
 * NexusEyeTtsManager.synthesizeToFile() produces an engine-specific audio file.
 * The file extension alone does not prove MP3 encoding. Therefore the audio
 * format is represented explicitly as ENGINE_OUTPUT until the Stage 8 hardware
 * protocol defines and verifies the actual encoded format.
 */

enum class NexusEyeWearableLinkState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

enum class NexusEyeWearableAudioFormat {
    ENGINE_OUTPUT,
    MP3,
    WAV,
    PCM,
    UNKNOWN
}

data class NexusEyeWearableAudioArtifact(
    val transferId: Long,
    val file: File,
    val sizeBytes: Int,
    val format: NexusEyeWearableAudioFormat
)

data class NexusEyeWearableAudioChunk(
    val transferId: Long,
    val index: Int,
    val totalChunks: Int,
    val offsetBytes: Int,
    val lengthBytes: Int,
    val isFirst: Boolean,
    val isLast: Boolean,
    val payload: ByteArray
)

data class NexusEyeWearableTransferState(
    val transferId: Long?,
    val totalBytes: Int,
    val sentBytes: Int,
    val totalChunks: Int,
    val sentChunks: Int,
    val active: Boolean,
    val lastError: String? = null
) {
    val progress: Float
        get() {
            if (totalBytes <= 0) {
                return 0f
            }
            return (sentBytes.toFloat() / totalBytes.toFloat())
                .coerceIn(0f, 1f)
        }
}

class NexusEyeEsp32AudioChunker(
    private val chunkSizeBytes: Int = DEFAULT_CHUNK_SIZE_BYTES
) {

    init {
        require(chunkSizeBytes > 0) {
            "Chunk size must be greater than zero."
        }
    }

    fun chunk(
        artifact: NexusEyeWearableAudioArtifact
    ): List<NexusEyeWearableAudioChunk> {
        val bytes = artifact.file.readBytes()
        return chunk(
            transferId = artifact.transferId,
            bytes = bytes
        )
    }

    fun chunk(
        transferId: Long,
        bytes: ByteArray
    ): List<NexusEyeWearableAudioChunk> {
        if (bytes.isEmpty()) {
            return emptyList()
        }

        val totalChunks =
            (bytes.size + chunkSizeBytes - 1) /
                    chunkSizeBytes

        val chunks =
            ArrayList<NexusEyeWearableAudioChunk>(
                totalChunks
            )

        var offset = 0
        var index = 0

        while (offset < bytes.size) {
            val length =
                minOf(
                    chunkSizeBytes,
                    bytes.size - offset
                )

            val payload =
                bytes.copyOfRange(
                    offset,
                    offset + length
                )

            chunks +=
                NexusEyeWearableAudioChunk(
                    transferId = transferId,
                    index = index,
                    totalChunks = totalChunks,
                    offsetBytes = offset,
                    lengthBytes = length,
                    isFirst = index == 0,
                    isLast = index == totalChunks - 1,
                    payload = payload
                )

            offset += length
            index += 1
        }

        return chunks
    }

    companion object {
        /**
         * Conservative default payload size for the first BLE transport layer.
         * The final value must be aligned with the negotiated MTU and the
         * NEXUS EYE packet overhead when Stage 8 connects to real hardware.
         */
        const val DEFAULT_CHUNK_SIZE_BYTES =
            180
    }
}

class NexusEyeEsp32WearableTransport(
    context: Context,
    private val ttsManager: NexusEyeTtsManager =
        NexusEyeTtsManager(
            context.applicationContext
        ),
    private val chunker: NexusEyeEsp32AudioChunker =
        NexusEyeEsp32AudioChunker()
) {

    private val appContext =
        context.applicationContext

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        )

    private val transferCounter =
        AtomicLong(1L)

    private val _linkState =
        MutableStateFlow(
            NexusEyeWearableLinkState.DISCONNECTED
        )

    val linkState: StateFlow<NexusEyeWearableLinkState> =
        _linkState.asStateFlow()

    private val _transferState =
        MutableStateFlow(
            NexusEyeWearableTransferState(
                transferId = null,
                totalBytes = 0,
                sentBytes = 0,
                totalChunks = 0,
                sentChunks = 0,
                active = false,
                lastError = null
            )
        )

    val transferState: StateFlow<NexusEyeWearableTransferState> =
        _transferState.asStateFlow()

    private var synthesisJob: Job? = null

    fun setScanning() {
        _linkState.value =
            NexusEyeWearableLinkState.SCANNING
    }

    fun setConnecting() {
        _linkState.value =
            NexusEyeWearableLinkState.CONNECTING
    }

    fun setConnected() {
        _linkState.value =
            NexusEyeWearableLinkState.CONNECTED
    }

    fun setReconnecting() {
        _linkState.value =
            NexusEyeWearableLinkState.RECONNECTING
    }

    fun setDisconnected() {
        _linkState.value =
            NexusEyeWearableLinkState.DISCONNECTED

        resetTransferState()
    }

    fun setError(
        message: String
    ) {
        _linkState.value =
            NexusEyeWearableLinkState.ERROR

        _transferState.value =
            _transferState.value.copy(
                active = false,
                lastError = message
            )
    }

    /**
     * Generates a real TTS artifact on Android storage.
     *
     * The artifact is intentionally marked ENGINE_OUTPUT. Stage 8 must verify
     * the actual codec produced by the installed TTS engine before advertising
     * the payload as MP3/WAV/PCM to the ESP32-side player.
     */
    fun synthesizeWearableAudio(
        text: String,
        language: NexusEyeLanguage,
        onSuccess: (NexusEyeWearableAudioArtifact) -> Unit,
        onError: (String) -> Unit
    ) {
        synthesisJob?.cancel()

        val cleanText =
            text.trim()

        if (cleanText.isBlank()) {
            onError(
                "There is no text to synthesize."
            )
            return
        }

        val transferId =
            transferCounter.getAndIncrement()

        val outputDirectory =
            File(
                appContext.cacheDir,
                "nexus_eye_wearable_audio"
            )

        val outputFile =
            File(
                outputDirectory,
                "transfer_${transferId}.audio"
            )

        synthesisJob =
            scope.launch {
                ttsManager.synthesizeToFile(
                    text = cleanText,
                    language = language,
                    outputFilePath = outputFile.absolutePath,
                    onSuccess = {
                        val bytes =
                            if (outputFile.exists()) {
                                outputFile.length()
                            } else {
                                0L
                            }

                        if (bytes <= 0L) {
                            _transferState.value =
                                _transferState.value.copy(
                                    active = false,
                                    lastError =
                                        "The TTS engine created an empty audio file."
                                )

                            onError(
                                "The TTS engine created an empty audio file."
                            )
                            return@synthesizeToFile
                        }

                        if (bytes > Int.MAX_VALUE) {
                            _transferState.value =
                                _transferState.value.copy(
                                    active = false,
                                    lastError =
                                        "The generated audio file is too large for the wearable transfer model."
                                )

                            onError(
                                "The generated audio file is too large for the wearable transfer model."
                            )
                            return@synthesizeToFile
                        }

                        val artifact =
                            NexusEyeWearableAudioArtifact(
                                transferId = transferId,
                                file = outputFile,
                                sizeBytes = bytes.toInt(),
                                format =
                                    NexusEyeWearableAudioFormat.ENGINE_OUTPUT
                            )

                        val chunks =
                            try {
                                chunker.chunk(artifact)
                            } catch (exception: Exception) {
                                _transferState.value =
                                    _transferState.value.copy(
                                        active = false,
                                        lastError =
                                            exception.message
                                                ?: "Could not prepare wearable audio chunks."
                                    )

                                onError(
                                    exception.message
                                        ?: "Could not prepare wearable audio chunks."
                                )
                                return@synthesizeToFile
                            }

                        _transferState.value =
                            NexusEyeWearableTransferState(
                                transferId = transferId,
                                totalBytes = artifact.sizeBytes,
                                sentBytes = 0,
                                totalChunks = chunks.size,
                                sentChunks = 0,
                                active = false,
                                lastError = null
                            )

                        onSuccess(
                            artifact
                        )
                    },
                    onError = {
                            message ->

                        _transferState.value =
                            _transferState.value.copy(
                                active = false,
                                lastError = message
                            )

                        onError(message)
                    }
                )
            }
    }

    /**
     * Prepares a previously generated artifact for a transport implementation.
     * No BLE write occurs here. The existing NexusEyeBleManager remains the
     * owner of the real Bluetooth connection.
     */
    fun prepareTransfer(
        artifact: NexusEyeWearableAudioArtifact
    ): List<NexusEyeWearableAudioChunk> {
        val chunks =
            chunker.chunk(
                artifact
            )

        _transferState.value =
            NexusEyeWearableTransferState(
                transferId = artifact.transferId,
                totalBytes = artifact.sizeBytes,
                sentBytes = 0,
                totalChunks = chunks.size,
                sentChunks = 0,
                active = chunks.isNotEmpty(),
                lastError = null
            )

        return chunks
    }

    /**
     * Allows the eventual BLE writer to report confirmed packet progress after
     * the ESP32 acknowledges a chunk.
     */
    fun acknowledgeChunk(
        chunk: NexusEyeWearableAudioChunk
    ) {
        val current =
            _transferState.value

        if (current.transferId != chunk.transferId) {
            return
        }

        val nextSentChunks =
            (current.sentChunks + 1)
                .coerceAtMost(current.totalChunks)

        val nextSentBytes =
            (current.sentBytes + chunk.lengthBytes)
                .coerceAtMost(current.totalBytes)

        val finished =
            nextSentChunks >= current.totalChunks ||
                    nextSentBytes >= current.totalBytes

        _transferState.value =
            current.copy(
                sentBytes = nextSentBytes,
                sentChunks = nextSentChunks,
                active = !finished,
                lastError = null
            )
    }

    fun failTransfer(
        message: String
    ) {
        _transferState.value =
            _transferState.value.copy(
                active = false,
                lastError = message
            )
    }

    fun cancelTransfer() {
        synthesisJob?.cancel()
        synthesisJob = null

        _transferState.value =
            _transferState.value.copy(
                active = false
            )
    }

    fun resetTransferState() {
        synthesisJob?.cancel()
        synthesisJob = null

        _transferState.value =
            NexusEyeWearableTransferState(
                transferId = null,
                totalBytes = 0,
                sentBytes = 0,
                totalChunks = 0,
                sentChunks = 0,
                active = false,
                lastError = null
            )
    }

    fun shutdown() {
        synthesisJob?.cancel()
        synthesisJob = null

        try {
            ttsManager.stop()
        } catch (_: Exception) {
        }

        try {
            ttsManager.shutdown()
        } catch (_: Exception) {
        }

        scope.cancel()
    }
}
