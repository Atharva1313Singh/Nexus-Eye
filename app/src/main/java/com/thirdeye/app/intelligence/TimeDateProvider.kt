package com.thirdeye.app.intelligence

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeDateProvider {

    fun currentTime(
        locale: Locale = Locale.getDefault()
    ): String {
        return SimpleDateFormat(
            "h:mm a",
            locale
        ).format(Date())
    }

    fun currentDate(
        locale: Locale = Locale.getDefault()
    ): String {
        return SimpleDateFormat(
            "d MMMM yyyy",
            locale
        ).format(Date())
    }

    fun currentDay(
        locale: Locale = Locale.getDefault()
    ): String {
        return SimpleDateFormat(
            "EEEE",
            locale
        ).format(Date())
    }

    fun currentDateTime(
        locale: Locale = Locale.getDefault()
    ): String {
        return SimpleDateFormat(
            "EEEE, d MMMM yyyy, h:mm a",
            locale
        ).format(Date())
    }

    fun answerFor(
        query: String,
        locale: Locale = Locale.getDefault()
    ): String? {

        val normalized =
            query
                .trim()
                .lowercase(locale)

        return when {

            isTimeQuestion(normalized) ->
                currentTime(locale)

            isDateQuestion(normalized) ->
                currentDate(locale)

            isDayQuestion(normalized) ->
                currentDay(locale)

            isDateTimeQuestion(normalized) ->
                currentDateTime(locale)

            else ->
                null
        }
    }

    private fun isTimeQuestion(
        query: String
    ): Boolean {

        val keywords =
            listOf(
                "time",
                "clock",
                "current time",
                "what time",
                "tell me the time",
                "time now",
                "time right now"
            )

        return keywords.any {
            query.contains(it)
        }
    }

    private fun isDateQuestion(
        query: String
    ): Boolean {

        val keywords =
            listOf(
                "date",
                "today's date",
                "todays date",
                "what date",
                "which date",
                "today date",
                "current date"
            )

        return keywords.any {
            query.contains(it)
        }
    }

    private fun isDayQuestion(
        query: String
    ): Boolean {

        val keywords =
            listOf(
                "what day",
                "which day",
                "day today",
                "today day",
                "what is today"
            )

        return keywords.any {
            query.contains(it)
        }
    }

    private fun isDateTimeQuestion(
        query: String
    ): Boolean {

        val keywords =
            listOf(
                "date and time",
                "time and date",
                "current date and time",
                "today date and time"
            )

        return keywords.any {
            query.contains(it)
        }
    }
}