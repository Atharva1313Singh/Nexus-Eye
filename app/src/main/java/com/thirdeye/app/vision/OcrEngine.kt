package com.thirdeye.app.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.math.max

class OcrEngine : AutoCloseable {

    companion object {
        private const val MAX_LONG_SIDE = 2000
    }

    fun recognizeFile(
        file: File,
        languageId: String
    ): String {

        require(file.exists()) {
            "The image file does not exist."
        }

        val bitmap =
            decodeOrientedBitmap(file)
                ?: throw IllegalArgumentException(
                    "Could not decode the image."
                )

        return try {

            recognizeBitmap(
                bitmap = bitmap,
                languageId = languageId
            )

        } finally {

            bitmap.recycle()
        }
    }

    private fun recognizeBitmap(
        bitmap: Bitmap,
        languageId: String
    ): String {

        val preparedBitmap =
            prepareBitmap(bitmap)

        return try {

            val inputImage =
                InputImage.fromBitmap(
                    preparedBitmap,
                    0
                )

            val recognizer =
                createRecognizer(languageId)

            try {

                val result =
                    Tasks.await(
                        recognizer.process(inputImage)
                    )

                result.text.trim()

            } finally {

                recognizer.close()
            }

        } finally {

            if (preparedBitmap !== bitmap) {
                preparedBitmap.recycle()
            }
        }
    }

    private fun createRecognizer(
        languageId: String
    ): TextRecognizer {

        val normalized =
            languageId
                .trim()
                .lowercase()

        return when {

            normalized == "hi" ||
                    normalized == "mr" ||
                    normalized == "ne" ||
                    normalized == "sa" -> {

                TextRecognition.getClient(
                    DevanagariTextRecognizerOptions
                        .Builder()
                        .build()
                )
            }

            normalized == "zh" ||
                    normalized == "zh-cn" ||
                    normalized == "zh-tw" -> {

                TextRecognition.getClient(
                    ChineseTextRecognizerOptions
                        .Builder()
                        .build()
                )
            }

            normalized == "ja" -> {

                TextRecognition.getClient(
                    JapaneseTextRecognizerOptions
                        .Builder()
                        .build()
                )
            }

            normalized == "ko" -> {

                TextRecognition.getClient(
                    KoreanTextRecognizerOptions
                        .Builder()
                        .build()
                )
            }

            else -> {

                TextRecognition.getClient(
                    TextRecognizerOptions.DEFAULT_OPTIONS
                )
            }
        }
    }

    private fun prepareBitmap(
        source: Bitmap
    ): Bitmap {

        val largestSide =
            max(
                source.width,
                source.height
            )

        if (
            largestSide <= MAX_LONG_SIDE
        ) {
            return source
        }

        val scale =
            MAX_LONG_SIDE.toFloat() /
                    largestSide.toFloat()

        val width =
            (source.width * scale)
                .toInt()
                .coerceAtLeast(1)

        val height =
            (source.height * scale)
                .toInt()
                .coerceAtLeast(1)

        return Bitmap.createScaledBitmap(
            source,
            width,
            height,
            true
        )
    }

    private fun decodeOrientedBitmap(
        file: File
    ): Bitmap? {

        val original =
            BitmapFactory.decodeFile(
                file.absolutePath
            )
                ?: return null

        val rotation =
            try {

                val exif =
                    ExifInterface(
                        file.absolutePath
                    )

                when (
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                ) {

                    ExifInterface.ORIENTATION_ROTATE_90 ->
                        90

                    ExifInterface.ORIENTATION_ROTATE_180 ->
                        180

                    ExifInterface.ORIENTATION_ROTATE_270 ->
                        270

                    else ->
                        0
                }

            } catch (_: Exception) {

                0
            }

        if (rotation == 0) {
            return original
        }

        val matrix =
            Matrix().apply {
                postRotate(
                    rotation.toFloat()
                )
            }

        val rotated =
            try {

                Bitmap.createBitmap(
                    original,
                    0,
                    0,
                    original.width,
                    original.height,
                    matrix,
                    true
                )

            } catch (_: Exception) {

                null
            }

        if (
            rotated != null &&
            rotated !== original
        ) {
            original.recycle()
        }

        return rotated ?: original
    }

    override fun close() {
        // ML Kit recognizers are closed after each operation.
    }
}