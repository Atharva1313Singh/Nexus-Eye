package com.thirdeye.app.vision

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class VisionPhotoLibrary(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val libraryDirectory =
        File(
            appContext.filesDir,
            LIBRARY_DIRECTORY_NAME
        )

    init {

        if (
            !libraryDirectory.exists()
        ) {

            libraryDirectory.mkdirs()
        }
    }

    fun getLibraryDirectory(): File {
        return libraryDirectory
    }

    fun saveFromUri(
        uri: Uri,
        source: VisionPhotoSource
    ): VisionPhoto {

        val mimeType =
            appContext.contentResolver
                .getType(uri)
                ?: "image/jpeg"

        val extension =
            extensionForMimeType(
                mimeType
            )

        val file =
            createLibraryFile(
                source = source,
                extension = extension
            )

        val input =
            appContext.contentResolver
                .openInputStream(uri)
                ?: throw IllegalStateException(
                    "Could not open the selected image."
                )

        try {

            FileOutputStream(
                file
            ).use { output ->

                input.use { stream ->

                    val buffer =
                        ByteArray(
                            COPY_BUFFER_SIZE
                        )

                    while (true) {

                        val read =
                            stream.read(
                                buffer
                            )

                        if (
                            read <= 0
                        ) {
                            break
                        }

                        output.write(
                            buffer,
                            0,
                            read
                        )
                    }
                }
            }

        } catch (exception: Exception) {

            file.delete()

            throw exception
        }

        if (
            file.length() <= 0L
        ) {

            file.delete()

            throw IllegalStateException(
                "The selected image is empty."
            )
        }

        return buildPhoto(
            file = file,
            source = source,
            mimeType = mimeType
        )
    }

    /*
     * Future OV7670 support.
     *
     * The ESP32 image receiver can reconstruct
     * the image bytes and pass them directly here.
     */
    fun saveFromBytes(
        imageBytes: ByteArray,
        source: VisionPhotoSource,
        mimeType: String = "image/jpeg"
    ): VisionPhoto {

        require(
            imageBytes.isNotEmpty()
        ) {
            "Image data must not be empty."
        }

        val extension =
            extensionForMimeType(
                mimeType
            )

        val file =
            createLibraryFile(
                source = source,
                extension = extension
            )

        try {

            FileOutputStream(
                file
            ).use { output ->

                output.write(
                    imageBytes
                )
            }

        } catch (exception: Exception) {

            file.delete()

            throw exception
        }

        return buildPhoto(
            file = file,
            source = source,
            mimeType = mimeType
        )
    }

    fun listPhotos(): List<VisionPhoto> {

        if (
            !libraryDirectory.exists()
        ) {

            libraryDirectory.mkdirs()

            return emptyList()
        }

        val files =
            libraryDirectory
                .listFiles()
                ?.filter { file ->
                    file.isFile &&
                            isSupportedImageFile(
                                file
                            )
                }
                ?.sortedByDescending {
                    it.lastModified()
                }
                ?: emptyList()

        return files.mapNotNull { file ->

            try {

                val source =
                    sourceFromFileName(
                        file.name
                    )

                val mimeType =
                    mimeTypeFromExtension(
                        file.extension
                    )

                buildPhoto(
                    file = file,
                    source = source,
                    mimeType = mimeType
                )

            } catch (_: Exception) {

                null
            }
        }
    }

    fun findPhoto(
        id: String
    ): VisionPhoto? {

        return listPhotos()
            .firstOrNull {
                it.id == id
            }
    }

    fun deletePhoto(
        photo: VisionPhoto
    ): Boolean {

        return try {

            photo.file.delete()

        } catch (_: Exception) {

            false
        }
    }

    fun clearLibrary(): Int {

        val files =
            libraryDirectory
                .listFiles()
                ?: return 0

        var deletedCount =
            0

        files.forEach { file ->

            if (
                file.isFile &&
                isSupportedImageFile(file)
            ) {

                try {

                    if (
                        file.delete()
                    ) {

                        deletedCount++
                    }

                } catch (_: Exception) {
                }
            }
        }

        return deletedCount
    }

    private fun createLibraryFile(
        source: VisionPhotoSource,
        extension: String
    ): File {

        if (
            !libraryDirectory.exists()
        ) {

            libraryDirectory.mkdirs()
        }

        val prefix =
            when (source) {

                VisionPhotoSource.PHONE_CAMERA ->
                    "camera"

                VisionPhotoSource.GALLERY ->
                    "gallery"

                VisionPhotoSource.OV7670 ->
                    "ov7670"
            }

        val id =
            System.currentTimeMillis()
                .toString() +
                    "_" +
                    UUID.randomUUID()
                        .toString()
                        .replace(
                            "-",
                            ""
                        )

        return File(
            libraryDirectory,
            "${prefix}_${id}.${extension}"
        )
    }

    private fun buildPhoto(
        file: File,
        source: VisionPhotoSource,
        mimeType: String
    ): VisionPhoto {

        val metadata =
            VisionPhotoMetadataReader.read(
                file = file,
                mimeType = mimeType
            )

        return VisionPhoto(
            id = file.nameWithoutExtension,
            file = file,
            source = source,
            fileSizeBytes = file.length(),
            metadata = metadata
        )
    }

    private fun sourceFromFileName(
        fileName: String
    ): VisionPhotoSource {

        val lower =
            fileName.lowercase()

        return when {

            lower.startsWith(
                "camera_"
            ) ->
                VisionPhotoSource.PHONE_CAMERA

            lower.startsWith(
                "gallery_"
            ) ->
                VisionPhotoSource.GALLERY

            lower.startsWith(
                "ov7670_"
            ) ->
                VisionPhotoSource.OV7670

            else ->
                VisionPhotoSource.GALLERY
        }
    }

    private fun extensionForMimeType(
        mimeType: String
    ): String {

        return when (
            mimeType.lowercase()
        ) {

            "image/jpeg",
            "image/jpg" ->
                "jpg"

            "image/png" ->
                "png"

            "image/webp" ->
                "webp"

            "image/heic" ->
                "heic"

            "image/heif" ->
                "heif"

            else ->
                "img"
        }
    }

    private fun mimeTypeFromExtension(
        extension: String
    ): String {

        return when (
            extension.lowercase()
        ) {

            "jpg",
            "jpeg" ->
                "image/jpeg"

            "png" ->
                "image/png"

            "webp" ->
                "image/webp"

            "heic" ->
                "image/heic"

            "heif" ->
                "image/heif"

            else ->
                "application/octet-stream"
        }
    }

    private fun isSupportedImageFile(
        file: File
    ): Boolean {

        return when (
            file.extension.lowercase()
        ) {

            "jpg",
            "jpeg",
            "png",
            "webp",
            "heic",
            "heif" ->
                true

            else ->
                false
        }
    }

    companion object {

        private const val LIBRARY_DIRECTORY_NAME =
            "vision_photo_library"

        private const val COPY_BUFFER_SIZE =
            16 * 1024
    }
}