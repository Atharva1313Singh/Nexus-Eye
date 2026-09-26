package com.thirdeye.app.intelligence

object HindiFemaleResponseStyle {

    fun apply(
        answer: String
    ): String {

        var result =
            answer.trim()

        if (result.isBlank()) {
            return result
        }

        val replacements =
            listOf(

                "मैं बता सकता हूँ" to
                        "मैं बता सकती हूँ",

                "मैं आपको बता सकता हूँ" to
                        "मैं आपको बता सकती हूँ",

                "मैं समझा सकता हूँ" to
                        "मैं समझा सकती हूँ",

                "मैं समझ सकता हूँ" to
                        "मैं समझ सकती हूँ",

                "मैं मदद कर सकता हूँ" to
                        "मैं मदद कर सकती हूँ",

                "मैं आपकी मदद कर सकता हूँ" to
                        "मैं आपकी मदद कर सकती हूँ",

                "मैं कर सकता हूँ" to
                        "मैं कर सकती हूँ",

                "मैं करूँगा" to
                        "मैं करूँगी",

                "मैं बताता हूँ" to
                        "मैं बताती हूँ",

                "मैं समझाता हूँ" to
                        "मैं समझाती हूँ",

                "मैं करता हूँ" to
                        "मैं करती हूँ",

                "मैं करता" to
                        "मैं करती",

                "मैं बताऊंगा" to
                        "मैं बताऊँगी",

                "मैं कर सकता" to
                        "मैं कर सकती",

                "मैं नहीं कर सकता" to
                        "मैं नहीं कर सकती",

                "मैं उपलब्ध करा सकता हूँ" to
                        "मैं उपलब्ध करा सकती हूँ",

                "मैं खोज सकता हूँ" to
                        "मैं खोज सकती हूँ",

                "मैं देख सकता हूँ" to
                        "मैं देख सकती हूँ",

                "मैं बता सकता" to
                        "मैं बता सकती",

                "मैं समझा सकता" to
                        "मैं समझा सकती"
            )

        /*
         * Each item is a Pair<String, String>.
         *
         * Kotlin therefore destructures it as:
         * (masculine, feminine)
         */
        replacements.forEach { (masculine, feminine) ->

            result =
                result.replace(
                    masculine,
                    feminine,
                    ignoreCase = false
                )
        }

        /*
         * Add a natural Hindi conversational opening
         * to longer answers.
         */
        if (
            shouldAddConversationalOpening(
                result
            )
        ) {

            result =
                "जी, $result"
        }

        return result
    }

    private fun shouldAddConversationalOpening(
        answer: String
    ): Boolean {

        if (answer.length < 20) {
            return false
        }

        val openings =
            listOf(
                "जी,",
                "जी ",
                "हाँ,",
                "हाँ ",
                "माफ़ कीजिए",
                "क्षमा कीजिए"
            )

        return openings.none {
            answer.startsWith(it)
        }
    }
}