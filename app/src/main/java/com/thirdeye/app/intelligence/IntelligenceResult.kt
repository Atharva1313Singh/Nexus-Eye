package com.thirdeye.app.intelligence

enum class ResponseSource {
    OFFLINE_DATABASE,
    CALCULATOR,
    DEVICE,
    WIKIPEDIA,
    UNKNOWN
}

data class IntelligenceResult(
    val answer: String,
    val source: ResponseSource,
    val shouldSpeakThroughEsp32: Boolean = true
)