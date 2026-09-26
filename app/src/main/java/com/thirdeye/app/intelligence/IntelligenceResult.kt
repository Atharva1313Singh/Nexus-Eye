package com.thirdeye.app.intelligence

enum class ResponseSource {
    OFFLINE_DATABASE,
    CALCULATOR,
    DEVICE,
    DEVICE_ACTION,
    GEMINI,
    WIKIPEDIA,
    UNKNOWN
}

data class IntelligenceResult(
    val answer: String,
    val source: ResponseSource,
    val shouldSpeakThroughEsp32: Boolean = true,
    val requiredPermissions: List<String> = emptyList()
)