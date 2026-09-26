package com.thirdeye.app.vision

import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.File

object VisionPhotoMetadataReader {

    fun read(
        file: File,
        mimeType: String
    ): VisionPhotoMetadata {

        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        try {
            BitmapFactory.decodeFile(
                file.absolutePath,
                options
            )
        } catch (_: Exception) {
        }

        val width =
            if (options.outWidth > 0) {
                options.outWidth
            } else {
                0
            }

        val height =
            if (options.outHeight > 0) {
                options.outHeight
            } else {
                0
            }

        var orientation =
            "Unknown"

        var dateTaken:
                String? = null

        var cameraMake:
                String? = null

        var cameraModel:
                String? = null

        var latitude:
                Double? = null

        var longitude:
                Double? = null

        try {

            val exif =
                ExifInterface(
                    file.absolutePath
                )

            orientation =
                readOrientation(
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                )

            dateTaken =
                exif.getAttribute(
                    ExifInterface.TAG_DATETIME_ORIGINAL
                )
                    ?: exif.getAttribute(
                        ExifInterface.TAG_DATETIME
                    )

            cameraMake =
                exif.getAttribute(
                    ExifInterface.TAG_MAKE
                )
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }

            cameraModel =
                exif.getAttribute(
                    ExifInterface.TAG_MODEL
                )
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }

            /*
             * android.media.ExifInterface uses:
             * getLatLong(float[])
             *
             * The first value is latitude.
             * The second value is longitude.
             */
            val coordinates =
                FloatArray(2)

            val hasCoordinates =
                exif.getLatLong(
                    coordinates
                )

            if (hasCoordinates) {

                latitude =
                    coordinates[0]
                        .toDouble()

                longitude =
                    coordinates[1]
                        .toDouble()
            }

        } catch (_: Exception) {
        }

        return VisionPhotoMetadata(
            mimeType = mimeType,
            width = width,
            height = height,
            orientation = orientation,
            dateTaken = dateTaken,
            cameraMake = cameraMake,
            cameraModel = cameraModel,
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun readOrientation(
        orientation: Int
    ): String {

        return when (orientation) {

            ExifInterface.ORIENTATION_NORMAL ->
                "Normal"

            ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                "Flipped horizontally"

            ExifInterface.ORIENTATION_ROTATE_180 ->
                "Rotated 180 degrees"

            ExifInterface.ORIENTATION_FLIP_VERTICAL ->
                "Flipped vertically"

            ExifInterface.ORIENTATION_TRANSPOSE ->
                "Transposed"

            ExifInterface.ORIENTATION_ROTATE_90 ->
                "Rotated 90 degrees"

            ExifInterface.ORIENTATION_TRANSVERSE ->
                "Transversed"

            ExifInterface.ORIENTATION_ROTATE_270 ->
                "Rotated 270 degrees"

            else ->
                "Unknown"
        }
    }
}