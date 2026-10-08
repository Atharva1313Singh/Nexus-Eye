package com.thirdeye.app.voice

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NexusEyeVoskRecognizer(
    context: Context
) {

    companion object {
        private const val TAG = "NexusEyeVosk"

        private const val MODEL_ASSET_DIR =
            "vosk-model-small-en-us"

        private const val SAMPLE_RATE =
            16000.0f

        private const val MAX_WAV_BYTES =
            10 * 1024 * 1024

        private const val WAV_HEADER_SIZE =
            44
    }

    private val appContext: Context =
        context.applicationContext

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    @Volatile
    private var model: Model? = null

    @Volatile
    private var initialized = false

    @Volatile
    private var initializing = false

    // ---------------------------------------------------------
    // INITIALIZATION
    // ---------------------------------------------------------

    fun initialize(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        if (initialized && model != null) {
            Log.i(
                TAG,
                "Vosk is already initialized."
            )

            onSuccess()
            return
        }

        synchronized(this) {

            if (initialized && model != null) {
                onSuccess()
                return
            }

            if (initializing) {
                Log.i(
                    TAG,
                    "Vosk initialization already running."
                )
                return
            }

            initializing = true
        }

        executor.execute {

            try {

                Log.i(
                    TAG,
                    "Initializing Vosk..."
                )

                val modelDirectory =
                    copyModelFromAssetsIfNeeded()

                Log.i(
                    TAG,
                    "Vosk model directory: " +
                            modelDirectory.absolutePath
                )

                val loadedModel =
                    Model(
                        modelDirectory.absolutePath
                    )

                model = loadedModel
                initialized = true
                initializing = false

                Log.i(
                    TAG,
                    "Vosk initialized successfully."
                )

                onSuccess()

            } catch (e: Exception) {

                initialized = false
                initializing = false
                model = null

                Log.e(
                    TAG,
                    "Vosk initialization failed.",
                    e
                )

                onError(
                    e.message
                        ?: "Unknown Vosk initialization error"
                )
            }
        }
    }

    fun isReady(): Boolean {
        return initialized && model != null
    }

    // ---------------------------------------------------------
    // WAV RECOGNITION
    // ---------------------------------------------------------

    fun recognizeWav(
        wavBytes: ByteArray,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {

        if (wavBytes.isEmpty()) {

            onError(
                "WAV data is empty."
            )

            return
        }

        if (wavBytes.size > MAX_WAV_BYTES) {

            onError(
                "WAV file is too large."
            )

            return
        }

        executor.execute {

            var recognizer: Recognizer? = null

            try {

                val currentModel =
                    model

                if (
                    !initialized ||
                    currentModel == null
                ) {

                    throw IllegalStateException(
                        "Vosk is not initialized."
                    )
                }

                Log.i(
                    TAG,
                    "Received WAV: " +
                            "${wavBytes.size} bytes"
                )

                val pcm =
                    extractPcmFromWav(
                        wavBytes
                    )

                if (pcm.isEmpty()) {

                    throw IllegalArgumentException(
                        "WAV contains no PCM audio."
                    )
                }

                Log.i(
                    TAG,
                    "PCM extracted: " +
                            "${pcm.size} bytes"
                )

                val durationMs =
                    (
                            pcm.size.toLong() * 1000L
                            ) / (
                            16000L * 2L
                            )

                Log.i(
                    TAG,
                    "PCM duration: " +
                            "$durationMs ms"
                )

                recognizer =
                    Recognizer(
                        currentModel,
                        SAMPLE_RATE
                    )

                Log.i(
                    TAG,
                    "Starting Vosk recognition..."
                )

                recognizer.acceptWaveForm(
                    pcm,
                    pcm.size
                )

                val resultJson =
                    recognizer.finalResult

                Log.i(
                    TAG,
                    "Vosk raw result: " +
                            resultJson
                )

                val text =
                    extractText(
                        resultJson
                    ).trim()

                Log.i(
                    TAG,
                    "Vosk result: \"$text\""
                )

                onResult(text)

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Vosk recognition failed.",
                    e
                )

                onError(
                    e.message
                        ?: "Unknown Vosk recognition error"
                )

            } finally {

                try {
                    recognizer?.close()
                } catch (
                    _: Exception
                ) {
                }
            }
        }
    }

    // ---------------------------------------------------------
    // WAV PARSER
    // ---------------------------------------------------------

    private fun extractPcmFromWav(
        wav: ByteArray
    ): ByteArray {

        if (wav.size < WAV_HEADER_SIZE) {

            throw IllegalArgumentException(
                "WAV file is too small."
            )
        }

        if (
            wav[0] != 'R'.code.toByte() ||
            wav[1] != 'I'.code.toByte() ||
            wav[2] != 'F'.code.toByte() ||
            wav[3] != 'F'.code.toByte()
        ) {

            throw IllegalArgumentException(
                "Invalid WAV RIFF header."
            )
        }

        if (
            wav[8] != 'W'.code.toByte() ||
            wav[9] != 'A'.code.toByte() ||
            wav[10] != 'V'.code.toByte() ||
            wav[11] != 'E'.code.toByte()
        ) {

            throw IllegalArgumentException(
                "Invalid WAV format."
            )
        }

        var offset = 12

        var audioFormat = -1
        var channels = -1
        var sampleRate = -1
        var bitsPerSample = -1

        var dataOffset = -1
        var dataSize = -1

        while (
            offset + 8 <= wav.size
        ) {

            val chunkId =
                String(
                    wav,
                    offset,
                    4,
                    Charsets.US_ASCII
                )

            val chunkSize =
                readLittleEndianInt(
                    wav,
                    offset + 4
                )

            if (chunkSize < 0) {
                break
            }

            val chunkDataStart =
                offset + 8

            if (
                chunkDataStart > wav.size
            ) {
                break
            }

            when (chunkId) {

                "fmt " -> {

                    if (chunkSize < 16) {

                        throw IllegalArgumentException(
                            "Invalid WAV fmt chunk."
                        )
                    }

                    if (
                        chunkDataStart + 16 >
                        wav.size
                    ) {

                        throw IllegalArgumentException(
                            "Truncated WAV fmt chunk."
                        )
                    }

                    audioFormat =
                        readLittleEndianShort(
                            wav,
                            chunkDataStart
                        )

                    channels =
                        readLittleEndianShort(
                            wav,
                            chunkDataStart + 2
                        )

                    sampleRate =
                        readLittleEndianInt(
                            wav,
                            chunkDataStart + 4
                        )

                    bitsPerSample =
                        readLittleEndianShort(
                            wav,
                            chunkDataStart + 14
                        )
                }

                "data" -> {

                    dataOffset =
                        chunkDataStart

                    dataSize =
                        chunkSize

                    break
                }
            }

            val nextOffset =
                chunkDataStart + chunkSize

            if (
                nextOffset <= offset ||
                nextOffset > wav.size
            ) {
                break
            }

            offset = nextOffset

            if ((offset and 1) != 0) {
                offset++
            }
        }

        if (audioFormat != 1) {

            throw IllegalArgumentException(
                "Unsupported WAV audio format: " +
                        "$audioFormat"
            )
        }

        if (channels != 1) {

            throw IllegalArgumentException(
                "WAV must be mono. Channels=$channels"
            )
        }

        if (sampleRate != 16000) {

            throw IllegalArgumentException(
                "WAV sample rate must be 16000 Hz. " +
                        "Actual=$sampleRate"
            )
        }

        if (bitsPerSample != 16) {

            throw IllegalArgumentException(
                "WAV must be 16-bit PCM. " +
                        "Actual=$bitsPerSample"
            )
        }

        if (
            dataOffset < 0 ||
            dataSize < 0
        ) {

            throw IllegalArgumentException(
                "WAV data chunk not found."
            )
        }

        if (
            dataOffset + dataSize >
            wav.size
        ) {

            throw IllegalArgumentException(
                "WAV data chunk exceeds file size."
            )
        }

        if (dataSize == 0) {
            return ByteArray(0)
        }

        return wav.copyOfRange(
            dataOffset,
            dataOffset + dataSize
        )
    }

    // ---------------------------------------------------------
    // VOSK RESULT PARSER
    // ---------------------------------------------------------

    private fun extractText(
        json: String
    ): String {

        if (json.isBlank()) {
            return ""
        }

        return try {

            val objectJson =
                JSONObject(json)

            objectJson.optString(
                "text",
                ""
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to parse Vosk JSON result.",
                e
            )

            ""
        }
    }

    // ---------------------------------------------------------
    // ASSET MODEL
    // ---------------------------------------------------------

    private fun copyModelFromAssetsIfNeeded(): File {

        val targetDirectory =
            File(
                appContext.filesDir,
                MODEL_ASSET_DIR
            )

        val markerFile =
            File(
                targetDirectory,
                ".model_ready"
            )

        if (
            targetDirectory.exists() &&
            markerFile.exists()
        ) {

            Log.i(
                TAG,
                "Vosk model already copied."
            )

            return targetDirectory
        }

        if (targetDirectory.exists()) {

            targetDirectory.deleteRecursively()
        }

        if (
            !targetDirectory.mkdirs() &&
            !targetDirectory.exists()
        ) {

            throw IOException(
                "Unable to create Vosk model directory."
            )
        }

        Log.i(
            TAG,
            "Copying Vosk model from assets..."
        )

        copyAssetDirectory(
            MODEL_ASSET_DIR,
            targetDirectory
        )

        if (
            !markerFile.createNewFile()
        ) {

            throw IOException(
                "Unable to create Vosk model marker."
            )
        }

        Log.i(
            TAG,
            "Vosk model copied successfully."
        )

        return targetDirectory
    }

    private fun copyAssetDirectory(
        assetPath: String,
        destination: File
    ) {

        val assetManager =
            appContext.assets

        val children =
            assetManager.list(
                assetPath
            )

        if (
            children == null ||
            children.isEmpty()
        ) {

            copyAssetFile(
                assetPath,
                destination
            )

            return
        }

        if (
            !destination.exists() &&
            !destination.mkdirs()
        ) {

            throw IOException(
                "Unable to create directory: " +
                        destination.absolutePath
            )
        }

        for (
        child in children
        ) {

            val childAssetPath =
                "$assetPath/$child"

            val childDestination =
                File(
                    destination,
                    child
                )

            copyAssetDirectory(
                childAssetPath,
                childDestination
            )
        }
    }

    private fun copyAssetFile(
        assetPath: String,
        destination: File
    ) {

        val parent =
            destination.parentFile

        if (
            parent != null &&
            !parent.exists()
        ) {

            if (!parent.mkdirs()) {

                throw IOException(
                    "Unable to create directory: " +
                            parent.absolutePath
                )
            }
        }

        appContext.assets
            .open(assetPath)
            .use { input ->

                FileOutputStream(
                    destination
                ).use { output ->

                    val buffer =
                        ByteArray(8192)

                    while (true) {

                        val count =
                            input.read(
                                buffer
                            )

                        if (count <= 0) {
                            break
                        }

                        output.write(
                            buffer,
                            0,
                            count
                        )
                    }

                    output.flush()
                }
            }
    }

    // ---------------------------------------------------------
    // LITTLE-ENDIAN HELPERS
    // ---------------------------------------------------------

    private fun readLittleEndianInt(
        data: ByteArray,
        offset: Int
    ): Int {

        if (
            offset < 0 ||
            offset + 4 > data.size
        ) {

            throw IllegalArgumentException(
                "Invalid WAV integer offset."
            )
        }

        return (
                (data[offset].toInt() and 0xFF) or
                        ((data[offset + 1].toInt() and 0xFF) shl 8) or
                        ((data[offset + 2].toInt() and 0xFF) shl 16) or
                        ((data[offset + 3].toInt() and 0xFF) shl 24)
                )
    }

    private fun readLittleEndianShort(
        data: ByteArray,
        offset: Int
    ): Int {

        if (
            offset < 0 ||
            offset + 2 > data.size
        ) {

            throw IllegalArgumentException(
                "Invalid WAV short offset."
            )
        }

        return (
                (data[offset].toInt() and 0xFF) or
                        ((data[offset + 1].toInt() and 0xFF) shl 8)
                )
    }

    // ---------------------------------------------------------
    // SHUTDOWN
    // ---------------------------------------------------------

    fun shutdown() {

        Log.i(
            TAG,
            "Shutting down Vosk recognizer."
        )

        initialized = false
        initializing = false

        try {
            model?.close()
        } catch (
            _: Exception
        ) {
        }

        model = null

        executor.shutdownNow()
    }
}