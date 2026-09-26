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

    private val appContext = context.applicationContext

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
            geminiApiKeyConfigured = hasGeminiApiKey(),
            onlineNavigationApiKeyConfigured = hasOnlineNavigationApiKey()
        )
    }

    fun hasGeminiApiKey(): Boolean = hasCredential(
        KEY_GEMINI_CIPHERTEXT,
        KEY_GEMINI_IV
    )

    fun saveGeminiApiKey(apiKey: String) {
        saveCredential(
            ciphertextKey = KEY_GEMINI_CIPHERTEXT,
            ivKey = KEY_GEMINI_IV,
            apiKey = apiKey,
            emptyMessage = "Gemini API key cannot be empty."
        )
    }

    fun getGeminiApiKey(): String? = getCredential(
        KEY_GEMINI_CIPHERTEXT,
        KEY_GEMINI_IV
    )

    fun clearGeminiApiKey() {
        clearCredential(
            KEY_GEMINI_CIPHERTEXT,
            KEY_GEMINI_IV
        )
    }

    fun hasOnlineNavigationApiKey(): Boolean = hasCredential(
        KEY_ONLINE_NAVIGATION_CIPHERTEXT,
        KEY_ONLINE_NAVIGATION_IV
    )

    fun saveOnlineNavigationApiKey(apiKey: String) {
        saveCredential(
            ciphertextKey = KEY_ONLINE_NAVIGATION_CIPHERTEXT,
            ivKey = KEY_ONLINE_NAVIGATION_IV,
            apiKey = apiKey,
            emptyMessage = "Online navigation API key cannot be empty."
        )
    }

    fun getOnlineNavigationApiKey(): String? = getCredential(
        KEY_ONLINE_NAVIGATION_CIPHERTEXT,
        KEY_ONLINE_NAVIGATION_IV
    )

    fun clearOnlineNavigationApiKey() {
        clearCredential(
            KEY_ONLINE_NAVIGATION_CIPHERTEXT,
            KEY_ONLINE_NAVIGATION_IV
        )
    }

    private fun hasCredential(
        ciphertextKey: String,
        ivKey: String
    ): Boolean {
        val encrypted = preferences.getString(ciphertextKey, null)
        val iv = preferences.getString(ivKey, null)
        return !encrypted.isNullOrBlank() && !iv.isNullOrBlank()
    }

    private fun saveCredential(
        ciphertextKey: String,
        ivKey: String,
        apiKey: String,
        emptyMessage: String
    ) {
        val cleanKey = apiKey.trim()
        require(cleanKey.isNotEmpty()) { emptyMessage }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val encrypted = cipher.doFinal(
            cleanKey.toByteArray(StandardCharsets.UTF_8)
        )

        preferences.edit()
            .putString(
                ciphertextKey,
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            )
            .putString(
                ivKey,
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            )
            .apply()
    }

    private fun getCredential(
        ciphertextKey: String,
        ivKey: String
    ): String? {
        if (!hasCredential(ciphertextKey, ivKey)) {
            return null
        }

        return try {
            val encrypted = Base64.decode(
                preferences.getString(ciphertextKey, null) ?: return null,
                Base64.NO_WRAP
            )
            val iv = Base64.decode(
                preferences.getString(ivKey, null) ?: return null,
                Base64.NO_WRAP
            )

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            )

            String(
                cipher.doFinal(encrypted),
                StandardCharsets.UTF_8
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun clearCredential(
        ciphertextKey: String,
        ivKey: String
    ) {
        preferences.edit()
            .remove(ciphertextKey)
            .remove(ivKey)
            .apply()
    }

    private fun ensureKeyExists() {
        getOrCreateKey()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        val existingEntry = keyStore.getEntry(KEY_ALIAS, null)
        if (existingEntry is KeyStore.SecretKeyEntry) {
            return existingEntry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
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

        // Keep the existing alias so already-saved Gemini credentials remain usable.
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
