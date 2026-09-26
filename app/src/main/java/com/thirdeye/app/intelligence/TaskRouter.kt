package com.thirdeye.app.intelligence

import android.content.Context
import java.util.Locale

class TaskRouter(
    private val context: Context
) {

    private val wikipediaFallback =
        WikipediaFallback()

    suspend fun process(
        query: String,
        speechLanguageId: String
    ): IntelligenceResult {

        val cleanQuery =
            query.trim()

        if (cleanQuery.isBlank()) {

            return IntelligenceResult(
                answer =
                    emptyQuestionMessage(
                        speechLanguageId
                    ),
                source =
                    ResponseSource.UNKNOWN
            )
        }

        val lower =
            cleanQuery.lowercase(
                Locale.ROOT
            )

        /*
         * ==================================================
         * 1. FAST DEVICE TASKS
         * ==================================================
         */

        if (
            containsAny(
                lower,
                "what time",
                "current time",
                "time now",
                "what is the time",
                "tell me the time",
                "time right now",
                "अभी समय",
                "समय क्या",
                "अभी कितने बजे",
                "कितने बजे"
            )
        ) {

            return IntelligenceResult(
                answer =
                    DeviceInfoProvider.currentTime(
                        speechLanguageId
                    ),
                source =
                    ResponseSource.DEVICE
            )
        }

        if (
            containsAny(
                lower,
                "today's date",
                "todays date",
                "current date",
                "date today",
                "what is today's date",
                "what is the date today",
                "आज की तारीख",
                "आज तारीख",
                "आज की डेट"
            )
        ) {

            return IntelligenceResult(
                answer =
                    DeviceInfoProvider.currentDate(
                        speechLanguageId
                    ),
                source =
                    ResponseSource.DEVICE
            )
        }

        if (
            containsAny(
                lower,
                "date and time",
                "time and date",
                "current date and time",
                "current time and date",
                "today date and time",
                "आज की तारीख और समय",
                "आज तारीख और समय"
            )
        ) {

            val locale =
                if (
                    speechLanguageId == "hi"
                ) {

                    Locale(
                        "hi",
                        "IN"
                    )

                } else {

                    Locale(
                        "en",
                        "IN"
                    )
                }

            val dateTime =
                TimeDateProvider.answerFor(
                    query = cleanQuery,
                    locale = locale
                )

            if (dateTime != null) {

                return IntelligenceResult(
                    answer = dateTime,
                    source =
                        ResponseSource.DEVICE
                )
            }
        }

        if (
            containsAny(
                lower,
                "battery",
                "battery percentage",
                "battery level",
                "how much battery",
                "how much battery is left",
                "बैटरी",
                "बैटरी कितनी",
                "बैटरी कितनी बची है"
            )
        ) {

            return IntelligenceResult(
                answer =
                    DeviceInfoProvider.battery(
                        context,
                        speechLanguageId
                    ),
                source =
                    ResponseSource.DEVICE
            )
        }

        /*
         * ==================================================
         * 2. CALCULATOR
         * ==================================================
         */

        val calculation =
            CalculatorEngine.tryCalculate(
                cleanQuery
            )

        if (calculation != null) {

            val formatted =
                CalculatorEngine.formatResult(
                    calculation
                )

            return IntelligenceResult(
                answer =
                    if (
                        speechLanguageId == "hi"
                    ) {

                        "उत्तर $formatted है।"

                    } else {

                        "The answer is $formatted."
                    },
                source =
                    ResponseSource.CALCULATOR
            )
        }

        /*
         * ==================================================
         * 3. CURRENT / LIVE INFORMATION
         * ==================================================
         */

        if (
            isCurrentInformationQuestion(
                lower
            )
        ) {

            return IntelligenceResult(
                answer =
                    unavailableQuestionMessage(
                        speechLanguageId
                    ),
                source =
                    ResponseSource.UNKNOWN
            )
        }

        /*
         * ==================================================
         * 4. EXISTING OFFLINE KNOWLEDGE BASE
         *
         * The database itself is NOT replaced.
         * Only the Hindi response style is changed.
         * ==================================================
         */

        val offline =
            findOfflineAnswer(
                cleanQuery
            )

        if (offline != null) {

            val answer =
                if (
                    speechLanguageId == "hi"
                ) {

                    HindiFemaleResponseStyle.apply(
                        offline.hindiAnswer
                    )

                } else {

                    offline.englishAnswer
                }

            return IntelligenceResult(
                answer = answer,
                source =
                    ResponseSource.OFFLINE_DATABASE
            )
        }

        /*
         * ==================================================
         * 5. INTERNET CHECK
         * ==================================================
         */

        if (
            !NetworkAvailability
                .isInternetAvailable(
                    context
                )
        ) {

            return IntelligenceResult(
                answer =
                    noInternetMessage(
                        speechLanguageId
                    ),
                source =
                    ResponseSource.UNKNOWN
            )
        }

        /*
         * ==================================================
         * 6. ONLINE FALLBACK
         * ==================================================
         */

        val onlineResult =
            try {

                wikipediaFallback.search(
                    query = cleanQuery,
                    languageId = speechLanguageId
                )

            } catch (_: Exception) {

                null
            }

        if (
            onlineResult == null
        ) {

            return IntelligenceResult(
                answer =
                    unavailableQuestionMessage(
                        speechLanguageId
                    ),
                source =
                    ResponseSource.UNKNOWN
            )
        }

        /*
         * Never expose technical failure text.
         */
        if (
            onlineResult.source ==
            ResponseSource.UNKNOWN
        ) {

            return IntelligenceResult(
                answer =
                    unavailableQuestionMessage(
                        speechLanguageId
                    ),
                source =
                    ResponseSource.UNKNOWN
            )
        }

        if (
            onlineResult.answer
                .trim()
                .isBlank()
        ) {

            return IntelligenceResult(
                answer =
                    unavailableQuestionMessage(
                        speechLanguageId
                    ),
                source =
                    ResponseSource.UNKNOWN
            )
        }

        /*
         * Keep real online answers unchanged.
         */
        return onlineResult
    }

    /*
     * ======================================================
     * OFFLINE MATCHING
     * ======================================================
     */

    private fun findOfflineAnswer(
        query: String
    ): OfflineQuestion? {

        val directMatch =
            OfflineKnowledgeBase.find(
                query
            )

        if (directMatch != null) {
            return directMatch
        }

        val alternatives =
            AlternativePhraseMatcher
                .equivalentsFor(
                    query
                )

        for (
        alternative
        in alternatives
        ) {

            if (
                QuestionMatcher
                    .isExactNormalizedMatch(
                        query,
                        alternative
                    )
            ) {
                continue
            }

            val alternativeMatch =
                OfflineKnowledgeBase.find(
                    alternative
                )

            if (
                alternativeMatch != null
            ) {

                return alternativeMatch
            }
        }

        return null
    }

    /*
     * ======================================================
     * CURRENT / LIVE INFORMATION
     * ======================================================
     */

    private fun isCurrentInformationQuestion(
        query: String
    ): Boolean {

        val currentTerms =
            listOf(
                "current",
                "currently",
                "right now",
                "at present",
                "latest",
                "today",
                "now",
                "this year",
                "this month",
                "this week",
                "recent",
                "recently",

                "वर्तमान",
                "अभी",
                "इस समय",
                "आज",
                "नवीनतम",
                "हाल का",
                "हाल ही में"
            )

        if (
            !currentTerms.any { term ->
                query.contains(term)
            }
        ) {

            return false
        }

        val questionPatterns =
            listOf(
                "who is",
                "what is",
                "what are",
                "where is",
                "who are",
                "which is",
                "which are",
                "how is",
                "how much",
                "where are",

                "कौन है",
                "क्या है",
                "कौन हैं",
                "कहाँ है",
                "कितना है",
                "कितनी है",
                "कहाँ हैं"
            )

        return questionPatterns.any { pattern ->
            query.contains(pattern)
        }
    }

    /*
     * ======================================================
     * MESSAGES
     * ======================================================
     */

    private fun emptyQuestionMessage(
        speechLanguageId: String
    ): String {

        return if (
            speechLanguageId == "hi"
        ) {

            "कृपया अपना प्रश्न बोलें।"

        } else {

            "Please say your question."
        }
    }

    private fun noInternetMessage(
        speechLanguageId: String
    ): String {

        return if (
            speechLanguageId == "hi"
        ) {

            "इंटरनेट उपलब्ध नहीं है। कृपया कोई ऑफलाइन प्रश्न पूछें।"

        } else {

            "Internet is not available. Please ask an offline question."
        }
    }

    private fun unavailableQuestionMessage(
        speechLanguageId: String
    ): String {

        return if (
            speechLanguageId == "hi"
        ) {

            "माफ़ कीजिए, मेरे पास अभी इस प्रश्न का उत्तर उपलब्ध नहीं है।"

        } else {

            "Sorry, I don't have an answer to that question right now."
        }
    }

    private fun containsAny(
        query: String,
        vararg phrases: String
    ): Boolean {

        return phrases.any {
            query.contains(it)
        }
    }
}