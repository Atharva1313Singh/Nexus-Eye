package com.thirdeye.app.vision

data class FaceProfile(
    val name: String,
    val embedding: FloatArray,
    val createdAt: Long
)

data class FaceMatch(
    val name: String,
    val similarity: Float
)

data class FaceEmbedding(
    val embedding: FloatArray,
    val boundingBoxLeft: Int,
    val boundingBoxTop: Int,
    val boundingBoxRight: Int,
    val boundingBoxBottom: Int
)