package com.thirdeye.app.intelligence

object AlternativePhraseMatcher {

    private val phraseGroups = listOf(

        listOf(
            "what is bluetooth",
            "what's bluetooth",
            "tell me about bluetooth",
            "what do you know about bluetooth",
            "bluetooth meaning"
        ),

        listOf(
            "what is wifi",
            "what's wifi",
            "tell me about wifi",
            "what does wifi mean",
            "wifi meaning"
        ),

        listOf(
            "what is gps",
            "what's gps",
            "tell me about gps",
            "what does gps mean",
            "gps meaning"
        ),

        listOf(
            "what is artificial intelligence",
            "what's artificial intelligence",
            "what is ai",
            "what's ai",
            "tell me about artificial intelligence",
            "tell me about ai"
        ),

        listOf(
            "what is machine learning",
            "what's machine learning",
            "tell me about machine learning",
            "what does machine learning mean"
        ),

        listOf(
            "how does bluetooth work",
            "how bluetooth works",
            "how does bluetooth work"
        ),

        listOf(
            "how does gps work",
            "how gps works",
            "how does gps work"
        ),

        listOf(
            "how does wifi work",
            "how wifi works",
            "how does wifi work"
        ),

        listOf(
            "what is the internet",
            "what's the internet",
            "what does internet mean",
            "tell me about the internet"
        ),

        listOf(
            "what is a computer",
            "what's a computer",
            "what does computer mean",
            "tell me about computers"
        ),

        listOf(
            "what is a smartphone",
            "what's a smartphone",
            "what does smartphone mean",
            "tell me about smartphones"
        ),

        listOf(
            "what is android",
            "what's android",
            "what is android operating system",
            "tell me about android"
        ),

        listOf(
            "what is an app",
            "what is a mobile app",
            "what does app mean",
            "what is an application"
        ),

        listOf(
            "who made you",
            "who created you",
            "who developed you",
            "who built you"
        ),

        listOf(
            "what can you do",
            "what are your capabilities",
            "what can nexus eye do",
            "what can you help me with"
        ),

        listOf(
            "help",
            "help me",
            "i need help",
            "how can you help me"
        )
    )

    fun equivalentsFor(
        query: String
    ): List<String> {

        val normalized =
            QuestionMatcher.normalize(query)

        if (normalized.isBlank()) {
            return emptyList()
        }

        val group =
            phraseGroups.firstOrNull { phrases ->
                phrases.any { phrase ->
                    QuestionMatcher.normalize(
                        phrase
                    ) == normalized
                }
            }

        return group
            ?.map {
                QuestionMatcher.normalize(it)
            }
            ?.distinct()
            ?: emptyList()
    }

    fun areEquivalent(
        first: String,
        second: String
    ): Boolean {

        val firstNormalized =
            QuestionMatcher.normalize(first)

        val secondNormalized =
            QuestionMatcher.normalize(second)

        if (
            firstNormalized ==
            secondNormalized
        ) {
            return true
        }

        val firstGroup =
            equivalentsFor(first)

        if (firstGroup.isEmpty()) {
            return false
        }

        return secondNormalized in firstGroup
    }
}