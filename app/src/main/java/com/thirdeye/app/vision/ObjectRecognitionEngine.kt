package com.thirdeye.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.concurrent.TimeUnit

data class ObjectRecognitionResult(
    val label: String,
    val confidence: Float
)

class ObjectRecognitionEngine(
    context: Context
) : AutoCloseable {

    private val appContext =
        context.applicationContext

    private val labeler: ImageLabeler =
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(
                    0.50f
                )
                .build()
        )

    /**
     * Recognize objects/entities from an image file.
     *
     * This function performs real on-device ML Kit
     * image labeling.
     *
     * Run it from a background coroutine/thread.
     */
    fun recognizeFile(
        file: File
    ): List<ObjectRecognitionResult> {

        require(
            file.exists()
        ) {
            "Image file does not exist."
        }

        if (
            file.length() <= 0L
        ) {
            throw IllegalArgumentException(
                "Image file is empty."
            )
        }

        val bitmap =
            decodeOrientedBitmap(
                file
            )
                ?: throw IllegalArgumentException(
                    "Could not decode image."
                )

        return try {

            recognizeBitmap(
                bitmap
            )

        } finally {

            bitmap.recycle()
        }
    }

    /**
     * Recognize objects/entities from a Bitmap.
     *
     * The returned results are sorted by confidence.
     */
    fun recognizeBitmap(
        bitmap: Bitmap
    ): List<ObjectRecognitionResult> {

        if (
            bitmap.width <= 0 ||
            bitmap.height <= 0
        ) {
            return emptyList()
        }

        val inputImage =
            InputImage.fromBitmap(
                bitmap,
                0
            )

        val labels =
            Tasks
                .await(
                    labeler.process(
                        inputImage
                    ),
                    30L,
                    TimeUnit.SECONDS
                )

        return convertLabels(
            labels
        )
    }

    private fun convertLabels(
        labels: List<ImageLabel>
    ): List<ObjectRecognitionResult> {

        return labels
            .asSequence()
            .filter {
                it.text.isNotBlank()
            }
            .map {
                ObjectRecognitionResult(
                    label = it.text.trim(),
                    confidence = it.confidence
                )
            }
            .sortedByDescending {
                it.confidence
            }
            .distinctBy {
                it.label.lowercase()
            }
            .take(
                MAX_RESULTS
            )
            .toList()
    }

    /**
     * Creates an EXIF-corrected Bitmap.
     */
    private fun decodeOrientedBitmap(
        file: File
    ): Bitmap? {

        val original =
            BitmapFactory.decodeFile(
                file.absolutePath
            )
                ?: return null

        val orientation =
            try {

                ExifInterface(
                    file.absolutePath
                )
                    .getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )

            } catch (_: Exception) {

                ExifInterface.ORIENTATION_NORMAL
            }

        val matrix =
            Matrix()

        when (orientation) {

            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {

                matrix.setScale(
                    -1f,
                    1f
                )
            }

            ExifInterface.ORIENTATION_ROTATE_180 -> {

                matrix.setRotate(
                    180f
                )
            }

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {

                matrix.setScale(
                    1f,
                    -1f
                )
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {

                matrix.setRotate(
                    90f
                )

                matrix.postScale(
                    -1f,
                    1f
                )
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> {

                matrix.setRotate(
                    90f
                )
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {

                matrix.setRotate(
                    -90f
                )

                matrix.postScale(
                    -1f,
                    1f
                )
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> {

                matrix.setRotate(
                    -90f
                )
            }

            ExifInterface.ORIENTATION_NORMAL -> {
                // Nothing to do.
            }
        }

        if (
            matrix.isIdentity
        ) {
            return original
        }

        return try {

            val oriented =
                Bitmap.createBitmap(
                    original,
                    0,
                    0,
                    original.width,
                    original.height,
                    matrix,
                    true
                )

            if (
                oriented !== original
            ) {
                original.recycle()
            }

            oriented

        } catch (_: Exception) {

            original
        }
    }

    override fun close() {

        try {
            labeler.close()
        } catch (_: Exception) {
        }
    }

    companion object {

        private const val MAX_RESULTS =
            10
    }
}