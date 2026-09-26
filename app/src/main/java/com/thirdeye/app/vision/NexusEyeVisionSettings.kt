package com.thirdeye.app.vision

import android.content.Context

/**
 * Persistent Android-side preferences for NEXUS EYE photo and vision behavior.
 *
 * These preferences control how the Android application should treat vision
 * data and vision features. The Vision screen can consume them without making
 * the settings screen responsible for image processing itself.
 */
data class NexusEyeVisionSettings(
    val savePhotosToLibrary: Boolean,
    val saveCameraCaptures: Boolean,
    val saveGalleryImports: Boolean,
    val personRecognitionEnabled: Boolean,
    val showPhotoMetadata: Boolean,
    val autoIdentifyAfterCapture: Boolean
)

class NexusEyeVisionSettingsManager(
    context: Context
) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun getSettings(): NexusEyeVisionSettings {
        return NexusEyeVisionSettings(
            savePhotosToLibrary = preferences.getBoolean(
                KEY_SAVE_PHOTOS_TO_LIBRARY,
                DEFAULT_SAVE_PHOTOS_TO_LIBRARY
            ),
            saveCameraCaptures = preferences.getBoolean(
                KEY_SAVE_CAMERA_CAPTURES,
                DEFAULT_SAVE_CAMERA_CAPTURES
            ),
            saveGalleryImports = preferences.getBoolean(
                KEY_SAVE_GALLERY_IMPORTS,
                DEFAULT_SAVE_GALLERY_IMPORTS
            ),
            personRecognitionEnabled = preferences.getBoolean(
                KEY_PERSON_RECOGNITION_ENABLED,
                DEFAULT_PERSON_RECOGNITION_ENABLED
            ),
            showPhotoMetadata = preferences.getBoolean(
                KEY_SHOW_PHOTO_METADATA,
                DEFAULT_SHOW_PHOTO_METADATA
            ),
            autoIdentifyAfterCapture = preferences.getBoolean(
                KEY_AUTO_IDENTIFY_AFTER_CAPTURE,
                DEFAULT_AUTO_IDENTIFY_AFTER_CAPTURE
            )
        )
    }

    fun saveSettings(
        settings: NexusEyeVisionSettings
    ) {
        preferences.edit()
            .putBoolean(
                KEY_SAVE_PHOTOS_TO_LIBRARY,
                settings.savePhotosToLibrary
            )
            .putBoolean(
                KEY_SAVE_CAMERA_CAPTURES,
                settings.saveCameraCaptures
            )
            .putBoolean(
                KEY_SAVE_GALLERY_IMPORTS,
                settings.saveGalleryImports
            )
            .putBoolean(
                KEY_PERSON_RECOGNITION_ENABLED,
                settings.personRecognitionEnabled
            )
            .putBoolean(
                KEY_SHOW_PHOTO_METADATA,
                settings.showPhotoMetadata
            )
            .putBoolean(
                KEY_AUTO_IDENTIFY_AFTER_CAPTURE,
                settings.autoIdentifyAfterCapture
            )
            .apply()
    }

    fun resetToDefaults() {
        saveSettings(
            NexusEyeVisionSettings(
                savePhotosToLibrary = DEFAULT_SAVE_PHOTOS_TO_LIBRARY,
                saveCameraCaptures = DEFAULT_SAVE_CAMERA_CAPTURES,
                saveGalleryImports = DEFAULT_SAVE_GALLERY_IMPORTS,
                personRecognitionEnabled = DEFAULT_PERSON_RECOGNITION_ENABLED,
                showPhotoMetadata = DEFAULT_SHOW_PHOTO_METADATA,
                autoIdentifyAfterCapture = DEFAULT_AUTO_IDENTIFY_AFTER_CAPTURE
            )
        )
    }

    companion object {
        private const val PREFS_NAME =
            "nexus_eye_vision_settings"

        private const val KEY_SAVE_PHOTOS_TO_LIBRARY =
            "save_photos_to_library"

        private const val KEY_SAVE_CAMERA_CAPTURES =
            "save_camera_captures"

        private const val KEY_SAVE_GALLERY_IMPORTS =
            "save_gallery_imports"

        private const val KEY_PERSON_RECOGNITION_ENABLED =
            "person_recognition_enabled"

        private const val KEY_SHOW_PHOTO_METADATA =
            "show_photo_metadata"

        private const val KEY_AUTO_IDENTIFY_AFTER_CAPTURE =
            "auto_identify_after_capture"

        const val DEFAULT_SAVE_PHOTOS_TO_LIBRARY =
            true

        const val DEFAULT_SAVE_CAMERA_CAPTURES =
            true

        const val DEFAULT_SAVE_GALLERY_IMPORTS =
            true

        const val DEFAULT_PERSON_RECOGNITION_ENABLED =
            true

        const val DEFAULT_SHOW_PHOTO_METADATA =
            true

        const val DEFAULT_AUTO_IDENTIFY_AFTER_CAPTURE =
            true
    }
}
