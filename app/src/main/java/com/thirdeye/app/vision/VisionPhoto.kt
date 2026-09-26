package com.thirdeye.app.vision

import java.io.File

enum class VisionPhotoSource {
    PHONE_CAMERA,
    GALLERY,
    OV7670
}

data class VisionPhotoMetadata(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val orientation: String,
    val dateTaken: String?,
    val cameraMake: String?,
    val cameraModel: String?,
    val latitude: Double?,
    val longitude: Double?
)

data class VisionPhoto(
    val id: String,
    val file: File,
    val source: VisionPhotoSource,
    val fileSizeBytes: Long,
    val metadata: VisionPhotoMetadata
)