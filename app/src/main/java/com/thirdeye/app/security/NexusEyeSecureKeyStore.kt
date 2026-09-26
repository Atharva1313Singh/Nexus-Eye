package com.thirdeye.app.security

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

class NexusEyeSecureKeyStore(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val preferences =
        appContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    fun saveGeminiApiKey(
        apiKey: String
    ) {

        val cleanKey =
            apiKey.trim()

        if (
            cleanKey.isBlank()
        ) {
            clearGeminiApiKey()
            return
        }

        val encrypted =
            encrypt(
                cleanKey
            )

        preferences
            .edit()
            .putString(
                KEY_GEMINI_API,
                encrypted
            )
            .apply()
    }

    fun getGeminiApiKey():
            String? {

        val encrypted =
            preferences
                .getString(
                    KEY_GEMINI_API,
                    null
                )
                ?.trim()
                .orEmpty()

        if (
            encrypted.isBlank()
        ) {
            return null
        }

        return try {

            decrypt(
                encrypted
            )

        } catch (_: Exception) {

            null
        }
    }

    fun hasGeminiApiKey():
            Boolean {

        return !getGeminiApiKey()
            .isNullOrBlank()
    }

    fun clearGeminiApiKey() {

        preferences
            .edit()
            .remove(
                KEY_GEMINI_API
            )
            .apply()
    }

    private fun encrypt(
        plainText: String
    ): String {

        val secretKey =
            getOrCreateSecretKey()

        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            secretKey
        )

        val iv =
            cipher.iv

        val plainBytes =
            plainText.toByteArray(
                StandardCharsets.UTF_8
            )

        val encryptedBytes =
            cipher.doFinal(
                plainBytes
            )

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

        return Base64.encodeToString(
            combined,
            Base64.NO_WRAP
        )
    }

    private fun decrypt(
        encryptedText: String
    ): String {

        val combined =
            Base64.decode(
                encryptedText,
                Base64.NO_WRAP
            )

        if (
            combined.size <=
            GCM_IV_LENGTH_BYTES
        ) {
            throw IllegalArgumentException(
                "Encrypted API key is invalid."
            )
        }

        val iv =
            combined.copyOfRange(
                0,
                GCM_IV_LENGTH_BYTES
            )

        val ciphertext =
            combined.copyOfRange(
                GCM_IV_LENGTH_BYTES,
                combined.size
            )

        val secretKey =
            getOrCreateSecretKey()

        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        val gcmSpec =
            GCMParameterSpec(
                GCM_TAG_LENGTH_BITS,
                iv
            )

        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            gcmSpec
        )

        val decryptedBytes =
            cipher.doFinal(
                ciphertext
            )

        return String(
            decryptedBytes,
            StandardCharsets.UTF_8
        )
    }

    private fun getOrCreateSecretKey():
            SecretKey {

        val keyStore =
            KeyStore.getInstance(
                ANDROID_KEYSTORE
            )

        keyStore.load(
            null
        )

        val existingEntry =
            keyStore.getEntry(
                KEY_ALIAS,
                null
            )

        if (
            existingEntry
                    is KeyStore.SecretKeyEntry
        ) {
            return existingEntry.secretKey
        }

        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
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

        keyGenerator.init(
            keySpec
        )

        return keyGenerator.generateKey()
    }

    companion object {

        private const val PREFERENCES_NAME =
            "nexus_eye_secure_preferences"

        private const val KEY_GEMINI_API =
            "gemini_api_key"

        private const val KEY_ALIAS =
            "nexus_eye_api_key_aes"

        private const val ANDROID_KEYSTORE =
            "AndroidKeyStore"

        private const val TRANSFORMATION =
            "AES/GCM/NoPadding"

        private const val GCM_TAG_LENGTH_BITS =
            128

        private const val GCM_IV_LENGTH_BYTES =
            12
    }
}