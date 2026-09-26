package com.thirdeye.app.intelligence

import kotlin.math.round

object CalculatorEngine {

    fun tryCalculate(
        input: String
    ): Double? {

        val expression =
            extractExpression(input)
                ?: return null

        return try {

            val parser =
                Parser(expression)

            val result =
                parser.parse()

            if (parser.hasRemaining()) {
                null
            } else {
                result
            }

        } catch (_: Exception) {
            null
        }
    }

    fun formatResult(
        value: Double
    ): String {

        if (!value.isFinite()) {
            return "The result is not a finite number."
        }

        val rounded =
            round(value * 1_000_000.0) /
                    1_000_000.0

        return if (
            rounded == rounded.toLong().toDouble()
        ) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }

    private fun extractExpression(
        input: String
    ): String? {

        val lower =
            input.lowercase().trim()

        val directPrefixes =
            listOf(
                "calculate",
                "what is",
                "compute",
                "solve"
            )

        var expression =
            lower

        for (prefix in directPrefixes) {

            if (expression.startsWith(prefix)) {

                expression =
                    expression
                        .removePrefix(prefix)
                        .trim()

                break
            }
        }

        expression =
            expression
                .replace("×", "*")
                .replace("÷", "/")
                .replace("plus", "+")
                .replace("minus", "-")
                .replace("multiplied by", "*")
                .replace("times", "*")
                .replace("divided by", "/")
                .replace("into", "*")
                .replace("mod", "%")

        val allowed =
            Regex("^[0-9+\\-*/%.()\\s]+$")

        return if (
            expression.isNotBlank() &&
            allowed.matches(expression)
        ) {
            expression
        } else {
            null
        }
    }

    private class Parser(
        private val input: String
    ) {

        private var position = 0

        fun parse(): Double {
            val result =
                parseExpression()

            skipSpaces()

            return result
        }

        fun hasRemaining(): Boolean {
            skipSpaces()
            return position < input.length
        }

        private fun parseExpression(): Double {

            var value =
                parseTerm()

            while (true) {

                skipSpaces()

                if (position >= input.length) {
                    return value
                }

                when (input[position]) {

                    '+' -> {
                        position++
                        value += parseTerm()
                    }

                    '-' -> {
                        position++
                        value -= parseTerm()
                    }

                    else -> {
                        return value
                    }
                }
            }
        }

        private fun parseTerm(): Double {

            var value =
                parseFactor()

            while (true) {

                skipSpaces()

                if (position >= input.length) {
                    return value
                }

                when (input[position]) {

                    '*' -> {
                        position++
                        value *= parseFactor()
                    }

                    '/' -> {

                        position++

                        val divisor =
                            parseFactor()

                        if (divisor == 0.0) {
                            throw ArithmeticException(
                                "Division by zero."
                            )
                        }

                        value /= divisor
                    }

                    '%' -> {

                        position++

                        val divisor =
                            parseFactor()

                        if (divisor == 0.0) {
                            throw ArithmeticException(
                                "Modulo by zero."
                            )
                        }

                        value %= divisor
                    }

                    else -> {
                        return value
                    }
                }
            }
        }

        private fun parseFactor(): Double {

            skipSpaces()

            if (position >= input.length) {
                throw IllegalArgumentException()
            }

            if (input[position] == '+') {
                position++
                return parseFactor()
            }

            if (input[position] == '-') {
                position++
                return -parseFactor()
            }

            if (input[position] == '(') {

                position++

                val value =
                    parseExpression()

                skipSpaces()

                if (
                    position >= input.length ||
                    input[position] != ')'
                ) {
                    throw IllegalArgumentException()
                }

                position++

                return value
            }

            val start =
                position

            while (
                position < input.length &&
                (
                        input[position].isDigit() ||
                                input[position] == '.'
                        )
            ) {
                position++
            }

            if (start == position) {
                throw IllegalArgumentException()
            }

            return input
                .substring(
                    start,
                    position
                )
                .toDouble()
        }

        private fun skipSpaces() {

            while (
                position < input.length &&
                input[position].isWhitespace()
            ) {
                position++
            }
        }
    }
}