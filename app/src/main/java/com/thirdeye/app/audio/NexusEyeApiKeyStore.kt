package com.thirdeye.app.audio

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure local storage for the Google Cloud TTS API key.
 *
 * The key-encryption key is generated and stored in Android Keystore.
 * The API key itself is stored only as encrypted data in SharedPreferences.
 *
 * No API key is hard-coded in the application source.
 */
class NexusEyeApiKeyStore(
    context: Context
) {

    companion object {

        private const val KEYSTORE_PROVIDER =
            "AndroidKeyStore"

        private const val KEY_ALIAS =
            "nexus_eye_tts_key"

        private const val PREFS_NAME =
            "nexus_eye_secure_settings"

        private const val PREF_API_KEY =
            "google_tts_api_key"

        private const val AES_TRANSFORMATION =
            "AES/GCM/NoPadding"

        private const val GCM_TAG_LENGTH_BITS =
            128
    }

    private val appContext =
        context.applicationContext

    private val preferences =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private fun getOrCreateSecretKey(): SecretKey {

        val keyStore =
            KeyStore.getInstance(
                KEYSTORE_PROVIDER
            ).apply {
                load(null)
            }

        val existingKey =
            keyStore.getKey(
                KEY_ALIAS,
                null
            ) as? SecretKey

        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )

        val keySpec =
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                        KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(
                    true
                )
                .build()

        keyGenerator.init(keySpec)

        return keyGenerator.generateKey()
    }

    fun saveApiKey(
        apiKey: String
    ): Boolean {

        val cleanKey =
            apiKey.trim()

        if (cleanKey.isEmpty()) {
            return false
        }

        return try {

            val secretKey =
                getOrCreateSecretKey()

            val cipher =
                Cipher.getInstance(
                    AES_TRANSFORMATION
                )

            cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey
            )

            val encryptedBytes =
                cipher.doFinal(
                    cleanKey.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

            val iv =
                cipher.iv

            val combined =
                ByteArray(
                    iv.size +
                            encryptedBytes.size
                )

            System.arraycopy(
                iv,
                0,
                combined,
                0,
                iv.size
            )

            System.arraycopy(
                encryptedBytes,
                0,
                combined,
                iv.size,
                encryptedBytes.size
            )

            preferences
                .edit()
                .putString(
                    PREF_API_KEY,
                    Base64.encodeToString(
                        combined,
                        Base64.NO_WRAP
                    )
                )
                .apply()

            true

        } catch (_: Exception) {

            false
        }
    }

    fun getApiKey(): String? {

        val stored =
            preferences.getString(
                PREF_API_KEY,
                null
            ) ?: return null

        return try {

            val combined =
                Base64.decode(
                    stored,
                    Base64.DEFAULT
                )

            if (combined.size <= 12) {
                return null
            }

            val ivSize = 12

            val iv =
                combined.copyOfRange(
                    0,
                    ivSize
                )

            val encryptedBytes =
                combined.copyOfRange(
                    ivSize,
                    combined.size
                )

            val secretKey =
                getOrCreateSecretKey()

            val cipher =
                Cipher.getInstance(
                    AES_TRANSFORMATION
                )

            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(
                    GCM_TAG_LENGTH_BITS,
                    iv
                )
            )

            val decryptedBytes =
                cipher.doFinal(
                    encryptedBytes
                )

            String(
                decryptedBytes,
                StandardCharsets.UTF_8
            )

        } catch (_: Exception) {

            null
        }
    }

    fun hasApiKey(): Boolean {
        return !getApiKey().isNullOrBlank()
    }

    fun clearApiKey() {

        preferences
            .edit()
            .remove(PREF_API_KEY)
            .apply()
    }
}