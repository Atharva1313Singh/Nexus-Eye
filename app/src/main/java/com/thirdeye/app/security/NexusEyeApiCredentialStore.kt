package com.thirdeye.app.security

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class NexusEyeApiCredentialState(
    val geminiApiKeyConfigured: Boolean,
    val onlineNavigationApiKeyConfigured: Boolean
)

class NexusEyeApiCredentialStore(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val preferences =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    init {
        ensureKeyExists()
    }

    fun getState(): NexusEyeApiCredentialState {
        return NexusEyeApiCredentialState(
            geminiApiKeyConfigured =
                hasGeminiApiKey(),
            onlineNavigationApiKeyConfigured =
                hasOnlineNavigationApiKey()
        )
    }

    fun hasGeminiApiKey(): Boolean {
        val encrypted =
            preferences.getString(
                KEY_GEMINI_CIPHERTEXT,
                null
            )

        val iv =
            preferences.getString(
                KEY_GEMINI_IV,
                null
            )

        return !encrypted.isNullOrBlank() &&
                !iv.isNullOrBlank()
    }

    fun hasOnlineNavigationApiKey(): Boolean {
        val encrypted =
            preferences.getString(
                KEY_ONLINE_NAVIGATION_CIPHERTEXT,
                null
            )

        val iv =
            preferences.getString(
                KEY_ONLINE_NAVIGATION_IV,
                null
            )

        return !encrypted.isNullOrBlank() &&
                !iv.isNullOrBlank()
    }

    fun saveGeminiApiKey(
        apiKey: String
    ) {
        val cleanKey =
            apiKey.trim()

        require(cleanKey.isNotEmpty()) {
            "Gemini API key cannot be empty."
        }

        val key =
            getOrCreateKey()

        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            key
        )

        val encrypted =
            cipher.doFinal(
                cleanKey.toByteArray(
                    StandardCharsets.UTF_8
                )
            )

        preferences.edit()
            .putString(
                KEY_GEMINI_CIPHERTEXT,
                Base64.encodeToString(
                    encrypted,
                    Base64.NO_WRAP
                )
            )
            .putString(
                KEY_GEMINI_IV,
                Base64.encodeToString(
                    cipher.iv,
                    Base64.NO_WRAP
                )
            )
            .apply()
    }

    fun saveOnlineNavigationApiKey(
        apiKey: String
    ) {
        val cleanKey =
            apiKey.trim()

        require(cleanKey.isNotEmpty()) {
            "Online navigation API key cannot be empty."
        }

        val key =
            getOrCreateKey()

        val cipher =
            Cipher.getInstance(TRANSFORMATION)

        cipher.init(
            Cipher.ENCRYPT_MODE,
            key
        )

        val encrypted =
            cipher.doFinal(
                cleanKey.toByteArray(
                    StandardCharsets.UTF_8
                )
            )

        preferences.edit()
            .putString(
                KEY_ONLINE_NAVIGATION_CIPHERTEXT,
                Base64.encodeToString(
                    encrypted,
                    Base64.NO_WRAP
                )
            )
            .putString(
                KEY_ONLINE_NAVIGATION_IV,
                Base64.encodeToString(
                    cipher.iv,
                    Base64.NO_WRAP
                )
            )
            .apply()
    }

    fun getOnlineNavigationApiKey(): String? {
        if (!hasOnlineNavigationApiKey()) {
            return null
        }

        return try {
            val encrypted =
                Base64.decode(
                    preferences.getString(
                        KEY_ONLINE_NAVIGATION_CIPHERTEXT,
                        null
                    ) ?: return null,
                    Base64.NO_WRAP
                )

            val iv =
                Base64.decode(
                    preferences.getString(
                        KEY_ONLINE_NAVIGATION_IV,
                        null
                    ) ?: return null,
                    Base64.NO_WRAP
                )

            val cipher =
                Cipher.getInstance(TRANSFORMATION)

            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(
                    GCM_TAG_LENGTH_BITS,
                    iv
                )
            )

            String(
                cipher.doFinal(encrypted),
                StandardCharsets.UTF_8
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearOnlineNavigationApiKey() {
        preferences.edit()
            .remove(KEY_ONLINE_NAVIGATION_CIPHERTEXT)
            .remove(KEY_ONLINE_NAVIGATION_IV)
            .apply()
    }

    fun getGeminiApiKey(): String? {
        if (!hasGeminiApiKey()) {
            return null
        }

        return try {
            val encrypted =
                Base64.decode(
                    preferences.getString(
                        KEY_GEMINI_CIPHERTEXT,
                        null
                    )
                        ?: return null,
                    Base64.NO_WRAP
                )

            val iv =
                Base64.decode(
                    preferences.getString(
                        KEY_GEMINI_IV,
                        null
                    )
                        ?: return null,
                    Base64.NO_WRAP
                )

            val cipher =
                Cipher.getInstance(
                    TRANSFORMATION
                )

            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(
                    GCM_TAG_LENGTH_BITS,
                    iv
                )
            )

            String(
                cipher.doFinal(
                    encrypted
                ),
                StandardCharsets.UTF_8
            )

        } catch (_: Exception) {
            null
        }
    }

    fun clearGeminiApiKey() {
        preferences.edit()
            .remove(
                KEY_GEMINI_CIPHERTEXT
            )
            .remove(
                KEY_GEMINI_IV
            )
            .apply()
    }

    private fun ensureKeyExists() {
        getOrCreateKey()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore =
            KeyStore.getInstance(
                ANDROID_KEYSTORE
            )

        keyStore.load(null)

        val existingEntry =
            keyStore.getEntry(
                KEY_ALIAS,
                null
            )

        if (
            existingEntry is KeyStore.SecretKeyEntry
        ) {
            return existingEntry.secretKey
        }

        val keyGenerator =
            KeyGenerator.getInstance(
                KEY_ALGORITHM,
                ANDROID_KEYSTORE
            )

        keyGenerator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(
                    android.security.keystore.KeyProperties.BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return keyGenerator.generateKey()
    }

    companion object {

        private const val PREFS_NAME =
            "nexus_eye_protected_api_credentials"

        private const val KEY_ALIAS =
            "nexus_eye_gemini_api_key_v1"

        private const val KEY_GEMINI_CIPHERTEXT =
            "gemini_api_ciphertext"

        private const val KEY_GEMINI_IV =
            "gemini_api_iv"

        private const val KEY_ONLINE_NAVIGATION_CIPHERTEXT =
            "online_navigation_api_ciphertext"

        private const val KEY_ONLINE_NAVIGATION_IV =
            "online_navigation_api_iv"

        private const val ANDROID_KEYSTORE =
            "AndroidKeyStore"

        private const val KEY_ALGORITHM =
            "AES"

        private const val TRANSFORMATION =
            "AES/GCM/NoPadding"

        private const val GCM_TAG_LENGTH_BITS =
            128
    }
}
