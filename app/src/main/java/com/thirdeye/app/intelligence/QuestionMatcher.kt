package com.thirdeye.app.intelligence

import java.util.Locale

object QuestionMatcher {

    private val stopWords = setOf(
        "a",
        "an",
        "the",
        "is",
        "are",
        "was",
        "were",
        "be",
        "am",
        "of",
        "to",
        "in",
        "on",
        "for",
        "with",
        "and",
        "or",
        "what",
        "who",
        "where",
        "when",
        "why",
        "how",
        "can",
        "could",
        "would",
        "should",
        "please",
        "tell",
        "me",
        "give",
        "my",
        "do",
        "does",
        "did",
        "i",
        "you",
        "your",
        "it"
    )

    data class MatchResult(
        val matched: Boolean,
        val score: Double,
        val matchedKeywords: List<String>
    )

    fun normalize(text: String): String {
        return text
            .lowercase(Locale.US)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun tokenize(text: String): List<String> {
        val normalized = normalize(text)

        if (normalized.isBlank()) {
            return emptyList()
        }

        return normalized
            .split(" ")
            .asSequence()
            .filter { it.isNotBlank() }
            .filter { it !in stopWords }
            .distinct()
            .toList()
    }

    fun keywordScore(
        query: String,
        keywords: List<String>
    ): MatchResult {

        val queryTokens =
            tokenize(query)

        if (queryTokens.isEmpty() || keywords.isEmpty()) {
            return MatchResult(
                matched = false,
                score = 0.0,
                matchedKeywords = emptyList()
            )
        }

        val normalizedKeywords =
            keywords
                .asSequence()
                .flatMap { keyword ->
                    tokenize(keyword).asSequence()
                }
                .distinct()
                .toList()

        if (normalizedKeywords.isEmpty()) {
            return MatchResult(
                matched = false,
                score = 0.0,
                matchedKeywords = emptyList()
            )
        }

        val matched =
            queryTokens.filter { queryToken ->
                normalizedKeywords.any { keyword ->
                    tokensRelated(
                        queryToken,
                        keyword
                    )
                }
            }.distinct()

        val score =
            matched.size.toDouble() /
                    queryTokens.size.toDouble()

        return MatchResult(
            matched = score > 0.0,
            score = score,
            matchedKeywords = matched
        )
    }

    fun bestScore(
        query: String,
        keywords: List<String>
    ): Double {
        return keywordScore(
            query = query,
            keywords = keywords
        ).score
    }

    fun isStrongMatch(
        query: String,
        keywords: List<String>,
        minimumScore: Double = 0.50
    ): Boolean {
        return bestScore(
            query = query,
            keywords = keywords
        ) >= minimumScore
    }

    fun isExactNormalizedMatch(
        query: String,
        storedQuestion: String
    ): Boolean {
        return normalize(query) ==
                normalize(storedQuestion)
    }

    private fun tokensRelated(
        first: String,
        second: String
    ): Boolean {

        if (first == second) {
            return true
        }

        if (first.length >= 4 &&
            second.length >= 4
        ) {

            if (
                first.startsWith(second) ||
                second.startsWith(first)
            ) {
                return true
            }
        }

        return singularRelated(
            first,
            second
        )
    }

    private fun singularRelated(
        first: String,
        second: String
    ): Boolean {

        val firstBase =
            singularize(first)

        val secondBase =
            singularize(second)

        return firstBase == secondBase
    }

    private fun singularize(
        word: String
    ): String {

        if (word.length <= 3) {
            return word
        }

        return when {

            word.endsWith("ies") &&
                    word.length > 4 ->
                word.dropLast(3) + "y"

            word.endsWith("sses") &&
                    word.length > 5 ->
                word.dropLast(2)

            word.endsWith("xes") &&
                    word.length > 4 ->
                word.dropLast(2)

            word.endsWith("ches") &&
                    word.length > 5 ->
                word.dropLast(2)

            word.endsWith("shes") &&
                    word.length > 5 ->
                word.dropLast(2)

            word.endsWith("s") &&
                    !word.endsWith("ss") &&
                    word.length > 3 ->
                word.dropLast(1)

            else ->
                word
        }
    }
}