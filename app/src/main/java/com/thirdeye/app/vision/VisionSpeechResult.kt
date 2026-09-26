package com.thirdeye.app.vision

data class VisionSpeechResult(
    val text: String,
    val languageId: String,
    val source: VisionSpeechSource
)

enum class VisionSpeechSource {
    KNOWN_PERSON,
    STRANGER_PERSON
}

object VisionSpeechResultBuilder {

    /*
     * =========================================================
     * KNOWN PERSON
     * =========================================================
     */

    fun knownPerson(
        name: String,
        languageId: String
    ): VisionSpeechResult {

        val cleanName =
            name.trim()

        val cleanLanguageId =
            languageId
                .trim()
                .ifBlank {
                    "en"
                }

        val text =
            if (
                cleanLanguageId == "hi"
            ) {

                "$cleanName सामने हैं।"

            } else {

                "$cleanName is ahead."
            }

        return VisionSpeechResult(
            text = text,
            languageId = cleanLanguageId,
            source = VisionSpeechSource.KNOWN_PERSON
        )
    }

    /*
     * =========================================================
     * STRANGER PERSON
     * =========================================================
     *
     * IMPORTANT:
     *
     * This text is intentionally NOT translated.
     *
     * The exact required fallback is:
     *
     * Stranger person ahead.
     */

    fun strangerPerson(
        languageId: String
    ): VisionSpeechResult {

        return VisionSpeechResult(
            text = "Stranger person ahead.",
            languageId =
                languageId
                    .trim()
                    .ifBlank {
                        "en"
                    },
            source =
                VisionSpeechSource.STRANGER_PERSON
        )
    }

    /*
     * =========================================================
     * MULTIPLE KNOWN PEOPLE
     * =========================================================
     */

    fun multipleKnownPeople(
        names: List<String>,
        languageId: String
    ): VisionSpeechResult {

        val cleanNames =
            names
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        val cleanLanguageId =
            languageId
                .trim()
                .ifBlank {
                    "en"
                }

        if (
            cleanNames.isEmpty()
        ) {
            return strangerPerson(
                cleanLanguageId
            )
        }

        val joinedNames =
            cleanNames.joinToString(
                separator = ", "
            )

        val text =
            if (
                cleanLanguageId == "hi"
            ) {

                "$joinedNames सामने हैं।"

            } else {

                "$joinedNames are ahead."
            }

        return VisionSpeechResult(
            text = text,
            languageId = cleanLanguageId,
            source = VisionSpeechSource.KNOWN_PERSON
        )
    }

    /*
     * =========================================================
     * FINAL PERSON DECISION
     * =========================================================
     *
     * knownNames:
     *     Names confidently matched to saved profiles.
     *
     * unknownCount:
     *     Faces detected but not reliably matched.
     *
     * Rules:
     *
     * 1. No reliable known person:
     *       exact stranger fallback.
     *
     * 2. One reliable known person:
     *       speak that person's name.
     *
     * 3. Multiple reliable known people:
     *       speak all reliable names.
     *
     * 4. Reliable known + unknown:
     *       speak the known names and then the exact
     *       stranger phrase.
     */

    fun personDecision(
        knownNames: List<String>,
        unknownCount: Int,
        languageId: String
    ): VisionSpeechResult {

        val cleanNames =
            knownNames
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        val cleanUnknownCount =
            unknownCount.coerceAtLeast(
                0
            )

        val cleanLanguageId =
            languageId
                .trim()
                .ifBlank {
                    "en"
                }

        /*
         * ---------------------------------------------------------
         * NO RELIABLE RESULT
         * ---------------------------------------------------------
         */

        if (
            cleanNames.isEmpty()
        ) {

            return strangerPerson(
                languageId =
                    cleanLanguageId
            )
        }

        /*
         * ---------------------------------------------------------
         * ONE KNOWN PERSON
         * ---------------------------------------------------------
         */

        if (
            cleanNames.size == 1 &&
            cleanUnknownCount == 0
        ) {

            return knownPerson(
                name =
                    cleanNames.first(),
                languageId =
                    cleanLanguageId
            )
        }

        /*
         * ---------------------------------------------------------
         * MULTIPLE KNOWN PEOPLE
         * ---------------------------------------------------------
         */

        val knownText =
            if (
                cleanLanguageId == "hi"
            ) {

                "${cleanNames.joinToString(", ")} सामने हैं।"

            } else {

                "${cleanNames.joinToString(", ")} are ahead."
            }

        /*
         * ---------------------------------------------------------
         * KNOWN + UNKNOWN
         * ---------------------------------------------------------
         */

        val finalText =
            if (
                cleanUnknownCount > 0
            ) {

                "$knownText Stranger person ahead."

            } else {

                knownText
            }

        return VisionSpeechResult(
            text = finalText,
            languageId = cleanLanguageId,
            source = VisionSpeechSource.KNOWN_PERSON
        )
    }
}