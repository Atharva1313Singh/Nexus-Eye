package com.thirdeye.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt


class FaceRecognitionEngine(
    context: Context
) : AutoCloseable {

    companion object {

        private const val MODEL_FILE =
            "MobileFaceNet.tflite"

        private const val INPUT_SIZE =
            112

        private const val INPUT_CHANNELS =
            3

        private const val BYTES_PER_FLOAT =
            4

        private const val FACE_MARGIN =
            0.20f

        /*
         * ML Kit can detect smaller faces when this is lower.
         * The previous value was 0.08f.
         */
        private const val MIN_FACE_SIZE =
            0.03f

        /*
         * Very large phone-camera images are reduced for the
         * detector. This helps keep detection reliable and
         * memory use reasonable.
         */
        private const val MAX_DETECTION_DIMENSION =
            1600
    }


    private val appContext =
        context.applicationContext


    /*
     * ============================================================
     * ML KIT FACE DETECTOR
     * ============================================================
     */

    private val detector =
        FaceDetection.getClient(

            FaceDetectorOptions.Builder()

                .setPerformanceMode(
                    FaceDetectorOptions
                        .PERFORMANCE_MODE_ACCURATE
                )

                .setLandmarkMode(
                    FaceDetectorOptions
                        .LANDMARK_MODE_ALL
                )

                .setClassificationMode(
                    FaceDetectorOptions
                        .CLASSIFICATION_MODE_ALL
                )

                .setMinFaceSize(
                    MIN_FACE_SIZE
                )

                .build()
        )


    /*
     * ============================================================
     * LITERT INTERPRETER
     * ============================================================
     */

    private val interpreter:
            Interpreter


    init {

        interpreter =
            Interpreter(
                loadModel()
            )
    }


    /*
     * ============================================================
     * ANALYZE FILE
     * ============================================================
     */

    fun analyzeFile(
        file: File
    ): List<FaceEmbedding> {

        require(
            file.exists()
        ) {
            "Image file does not exist."
        }


        /*
         * First decode and correct EXIF orientation.
         */

        val orientedBitmap =
            decodeOrientedBitmap(
                file
            )
                ?: throw IllegalArgumentException(
                    "Could not decode image."
                )


        try {

            /*
             * First attempt:
             * normal oriented image.
             */

            val firstResult =
                analyzeBitmap(
                    orientedBitmap
                )


            if (
                firstResult.isNotEmpty()
            ) {

                return firstResult
            }


            /*
             * Second attempt:
             * use the raw camera image.
             *
             * Some camera applications store unusual EXIF
             * orientation information. This gives ML Kit another
             * chance to detect the face.
             */

            val rawBitmap =
                try {

                    BitmapFactory.decodeFile(
                        file.absolutePath
                    )

                } catch (_: Exception) {

                    null
                }


            if (
                rawBitmap != null
            ) {

                try {

                    val secondResult =
                        analyzeBitmap(
                            rawBitmap
                        )

                    if (
                        secondResult.isNotEmpty()
                    ) {

                        return secondResult
                    }

                } finally {

                    if (
                        !rawBitmap.isRecycled
                    ) {

                        rawBitmap.recycle()
                    }
                }
            }


            /*
             * No face was found in either pass.
             */

            return emptyList()

        } finally {

            if (
                !orientedBitmap.isRecycled
            ) {

                orientedBitmap.recycle()
            }
        }
    }


    /*
     * ============================================================
     * ANALYZE BITMAP
     * ============================================================
     */

    fun analyzeBitmap(
        bitmap: Bitmap
    ): List<FaceEmbedding> {

        if (
            bitmap.width <= 0 ||
            bitmap.height <= 0
        ) {

            return emptyList()
        }


        /*
         * Downscale very large phone images before detection.
         */

        val detectionBitmap =
            scaleForDetection(
                bitmap
            )


        try {

            val inputImage =
                InputImage.fromBitmap(
                    detectionBitmap,
                    0
                )


            val faces =
                Tasks.await(

                    detector.process(
                        inputImage
                    )
                )


            if (
                faces.isEmpty()
            ) {

                return emptyList()
            }


            val results =
                ArrayList<FaceEmbedding>(
                    faces.size
                )


            for (
            face in faces
            ) {

                val crop =
                    cropFace(
                        detectionBitmap,
                        face.boundingBox
                    )
                        ?: continue


                try {

                    val resized =
                        Bitmap.createScaledBitmap(

                            crop,

                            INPUT_SIZE,

                            INPUT_SIZE,

                            true
                        )


                    try {

                        val embedding =
                            createEmbedding(
                                resized
                            )


                        val normalized =
                            normalize(
                                embedding
                            )


                        val box =
                            face.boundingBox


                        results +=
                            FaceEmbedding(

                                embedding =
                                    normalized,

                                boundingBoxLeft =
                                    box.left,

                                boundingBoxTop =
                                    box.top,

                                boundingBoxRight =
                                    box.right,

                                boundingBoxBottom =
                                    box.bottom
                            )

                    } finally {

                        if (
                            !resized.isRecycled
                        ) {

                            resized.recycle()
                        }
                    }

                } finally {

                    if (
                        !crop.isRecycled
                    ) {

                        crop.recycle()
                    }
                }
            }


            return results

        } finally {

            /*
             * scaleForDetection returns the original bitmap
             * when no scaling is necessary.
             *
             * Only recycle a separately-created bitmap.
             */

            if (
                detectionBitmap !== bitmap &&
                !detectionBitmap.isRecycled
            ) {

                detectionBitmap.recycle()
            }
        }
    }


    /*
     * ============================================================
     * SCALE FOR DETECTION
     * ============================================================
     */

    private fun scaleForDetection(
        bitmap: Bitmap
    ): Bitmap {

        val width =
            bitmap.width

        val height =
            bitmap.height


        if (
            width <=
            MAX_DETECTION_DIMENSION &&
            height <=
            MAX_DETECTION_DIMENSION
        ) {

            return bitmap
        }


        val largestSide =
            max(
                width,
                height
            )
                .toFloat()


        val scale =
            MAX_DETECTION_DIMENSION /
                    largestSide


        val newWidth =
            max(
                1,
                (width * scale)
                    .toInt()
            )


        val newHeight =
            max(
                1,
                (height * scale)
                    .toInt()
            )


        return try {

            Bitmap.createScaledBitmap(

                bitmap,

                newWidth,

                newHeight,

                true
            )

        } catch (_: Exception) {

            bitmap
        }
    }


    /*
     * ============================================================
     * FACE CROP
     * ============================================================
     */

    private fun cropFace(
        bitmap: Bitmap,
        box: Rect
    ): Bitmap? {

        val marginX =
            (
                    box.width()
                        .toFloat() *
                            FACE_MARGIN
                    )
                .toInt()


        val marginY =
            (
                    box.height()
                        .toFloat() *
                            FACE_MARGIN
                    )
                .toInt()


        val left =
            max(
                0,
                box.left -
                        marginX
            )


        val top =
            max(
                0,
                box.top -
                        marginY
            )


        val right =
            min(
                bitmap.width,
                box.right +
                        marginX
            )


        val bottom =
            min(
                bitmap.height,
                box.bottom +
                        marginY
            )


        val width =
            right -
                    left


        val height =
            bottom -
                    top


        if (
            width <= 0 ||
            height <= 0
        ) {

            return null
        }


        return try {

            Bitmap.createBitmap(

                bitmap,

                left,

                top,

                width,

                height
            )

        } catch (_: Exception) {

            null
        }
    }


    /*
     * ============================================================
     * CREATE FACE EMBEDDING
     * ============================================================
     */

    private fun createEmbedding(
        bitmap: Bitmap
    ): FloatArray {

        val pixelCount =
            INPUT_SIZE *
                    INPUT_SIZE


        val inputBuffer =
            ByteBuffer.allocateDirect(

                pixelCount *
                        INPUT_CHANNELS *
                        BYTES_PER_FLOAT

            )
                .order(
                    ByteOrder.nativeOrder()
                )


        inputBuffer.rewind()


        val pixels =
            IntArray(
                pixelCount
            )


        bitmap.getPixels(

            pixels,

            0,

            INPUT_SIZE,

            0,

            0,

            INPUT_SIZE,

            INPUT_SIZE
        )


        for (
        pixel in pixels
        ) {

            val red =
                (
                        pixel shr 16
                        ) and 0xFF


            val green =
                (
                        pixel shr 8
                        ) and 0xFF


            val blue =
                pixel and 0xFF


            /*
             * RGB [0,255] -> approximately [-1,1]
             *
             * This must match the actual TFLite model.
             */

            inputBuffer.putFloat(

                red /
                        127.5f -
                        1.0f
            )


            inputBuffer.putFloat(

                green /
                        127.5f -
                        1.0f
            )


            inputBuffer.putFloat(

                blue /
                        127.5f -
                        1.0f
            )
        }


        inputBuffer.rewind()


        val outputSize =
            outputEmbeddingSize()


        val output =
            Array(
                1
            ) {

                FloatArray(
                    outputSize
                )
            }


        interpreter.run(

            inputBuffer,

            output
        )


        return output[0]
    }


    /*
     * ============================================================
     * OUTPUT EMBEDDING SIZE
     * ============================================================
     */

    private fun outputEmbeddingSize():
            Int {

        val shape =
            interpreter
                .getOutputTensor(
                    0
                )
                .shape()


        if (
            shape.isEmpty()
        ) {

            throw IllegalStateException(
                "Face model has no output tensor."
            )
        }


        var size =
            1


        for (
        dimension in shape
        ) {

            if (
                dimension <= 0
            ) {

                throw IllegalStateException(
                    "Face model output shape is invalid."
                )
            }


            size *=
                dimension
        }


        if (
            size <= 0
        ) {

            throw IllegalStateException(
                "Face model output size is invalid."
            )
        }


        return size
    }


    /*
     * ============================================================
     * NORMALIZE EMBEDDING
     * ============================================================
     */

    private fun normalize(
        vector: FloatArray
    ): FloatArray {

        var sum =
            0.0


        for (
        value in vector
        ) {

            val v =
                value.toDouble()


            sum +=
                v * v
        }


        val magnitude =
            sqrt(
                sum
            )


        if (
            magnitude <=
            0.0000001
        ) {

            return vector
        }


        val result =
            FloatArray(
                vector.size
            )


        for (
        index in
        vector.indices
        ) {

            result[index] =
                (
                        vector[index]
                            .toDouble() /
                                magnitude
                        )
                    .toFloat()
        }


        return result
    }


    /*
     * ============================================================
     * DECODE + EXIF ORIENTATION
     * ============================================================
     */

    private fun decodeOrientedBitmap(
        file: File
    ): Bitmap? {

        val bitmap =
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

                        ExifInterface
                            .TAG_ORIENTATION,

                        ExifInterface
                            .ORIENTATION_NORMAL
                    )

            } catch (_: Exception) {

                ExifInterface
                    .ORIENTATION_NORMAL
            }


        val matrix =
            Matrix()


        when (
            orientation
        ) {

            ExifInterface
                .ORIENTATION_FLIP_HORIZONTAL -> {

                matrix.setScale(
                    -1f,
                    1f
                )
            }


            ExifInterface
                .ORIENTATION_ROTATE_180 -> {

                matrix.setRotate(
                    180f
                )
            }


            ExifInterface
                .ORIENTATION_FLIP_VERTICAL -> {

                matrix.setScale(
                    1f,
                    -1f
                )
            }


            ExifInterface
                .ORIENTATION_TRANSPOSE -> {

                matrix.setRotate(
                    90f
                )

                matrix.postScale(
                    -1f,
                    1f
                )
            }


            ExifInterface
                .ORIENTATION_ROTATE_90 -> {

                matrix.setRotate(
                    90f
                )
            }


            ExifInterface
                .ORIENTATION_TRANSVERSE -> {

                matrix.setRotate(
                    -90f
                )

                matrix.postScale(
                    -1f,
                    1f
                )
            }


            ExifInterface
                .ORIENTATION_ROTATE_270 -> {

                matrix.setRotate(
                    -90f
                )
            }


            ExifInterface
                .ORIENTATION_NORMAL -> {

                return bitmap
            }
        }


        return try {

            val rotated =
                Bitmap.createBitmap(

                    bitmap,

                    0,

                    0,

                    bitmap.width,

                    bitmap.height,

                    matrix,

                    true
                )


            if (
                rotated !== bitmap
            ) {

                bitmap.recycle()
            }


            rotated

        } catch (_: Exception) {

            bitmap
        }
    }


    /*
     * ============================================================
     * LOAD LITERT MODEL
     * ============================================================
     */

    private fun loadModel():
            ByteBuffer {

        val fileDescriptor =
            appContext.assets
                .openFd(
                    MODEL_FILE
                )


        val inputStream =
            FileInputStream(
                fileDescriptor.fileDescriptor
            )


        return try {

            val channel:
                    FileChannel =
                inputStream.channel


            channel.map(

                FileChannel.MapMode
                    .READ_ONLY,

                fileDescriptor
                    .startOffset,

                fileDescriptor
                    .declaredLength
            )

        } finally {

            inputStream.close()

            fileDescriptor.close()
        }
    }


    /*
     * ============================================================
     * CLOSE
     * ============================================================
     */

    override fun close() {

        try {

            detector.close()

        } catch (_: Exception) {
        }


        try {

            interpreter.close()

        } catch (_: Exception) {
        }
    }
}