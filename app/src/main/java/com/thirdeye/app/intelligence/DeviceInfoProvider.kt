package com.thirdeye.app.intelligence

import android.content.Context
import android.os.BatteryManager
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object DeviceInfoProvider {

    fun currentTime(
        languageId: String
    ): String {

        val time =
            LocalTime.now()

        val formatter =
            if (languageId == "hi") {
                DateTimeFormatter.ofPattern(
                    "HH:mm"
                )
            } else {
                DateTimeFormatter.ofPattern(
                    "h:mm a"
                )
            }

        val formatted =
            time.format(formatter)

        return if (languageId == "hi") {
            "अभी समय $formatted है।"
        } else {
            "The current time is $formatted."
        }
    }

    fun currentDate(
        languageId: String
    ): String {

        val date =
            LocalDate.now()

        val formatter =
            DateTimeFormatter.ofPattern(
                "dd MMMM yyyy"
            )

        val formatted =
            date.format(formatter)

        return if (languageId == "hi") {
            "आज की तारीख $formatted है।"
        } else {
            "Today's date is $formatted."
        }
    }

    fun battery(
        context: Context,
        languageId: String
    ): String {

        val manager =
            context.getSystemService(
                Context.BATTERY_SERVICE
            ) as BatteryManager

        val percentage =
            manager.getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CAPACITY
            )

        return if (percentage in 0..100) {

            if (languageId == "hi") {
                "डिवाइस की बैटरी $percentage प्रतिशत है।"
            } else {
                "The device battery is at $percentage percent."
            }

        } else {

            if (languageId == "hi") {
                "बैटरी की जानकारी उपलब्ध नहीं है।"
            } else {
                "Battery information is not available."
            }
        }
    }
}