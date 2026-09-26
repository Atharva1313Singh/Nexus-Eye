package com.thirdeye.app.vision

import android.content.Context

class VisionImageNameStore(
    context: Context
) {

    companion object {

        private const val PREF_NAME =
            "nexus_eye_vision_image_names"

        private const val KEY_PREFIX =
            "image_name_"
    }

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

    @Synchronized
    fun setImageName(
        photoId: String,
        personName: String
    ) {

        val cleanName =
            personName
                .trim()

        require(
            photoId.isNotBlank()
        ) {
            "Photo ID cannot be empty."
        }

        require(
            cleanName.isNotBlank()
        ) {
            "Person name cannot be empty."
        }

        preferences
            .edit()
            .putString(
                KEY_PREFIX + photoId,
                cleanName
            )
            .apply()
    }

    @Synchronized
    fun getImageName(
        photoId: String
    ): String? {

        if (
            photoId.isBlank()
        ) {
            return null
        }

        return preferences
            .getString(
                KEY_PREFIX + photoId,
                null
            )
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
    }

    @Synchronized
    fun removeImageName(
        photoId: String
    ) {

        if (
            photoId.isBlank()
        ) {
            return
        }

        preferences
            .edit()
            .remove(
                KEY_PREFIX + photoId
            )
            .apply()
    }

    @Synchronized
    fun clearAll() {

        preferences
            .edit()
            .clear()
            .apply()
    }
}