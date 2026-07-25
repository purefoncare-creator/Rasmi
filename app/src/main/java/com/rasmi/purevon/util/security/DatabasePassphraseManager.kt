package com.rasmi.purevon.util.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the SQLCipher database passphrase.
 * The passphrase is randomly generated once and stored encrypted
 * in SharedPreferences using an AES key from AndroidKeyStore.
 */
object DatabasePassphraseManager {

    private const val TAG = "DBPassphraseManager"
    private const val KEYSTORE_ALIAS = "purevon_db_cipher_key"
    private const val PREFS_NAME = "purevon_db_cipher"
    private const val PREF_ENCRYPTED_PASSPHRASE = "encrypted_passphrase"
    private const val PREF_IV = "passphrase_iv"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val PASSPHRASE_LENGTH = 32 // 256-bit

    fun getPassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedEncrypted = prefs.getString(PREF_ENCRYPTED_PASSPHRASE, null)
        val storedIv = prefs.getString(PREF_IV, null)

        return if (storedEncrypted != null && storedIv != null) {
            try {
                decryptPassphrase(storedEncrypted, storedIv)
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL: Failed to decrypt database passphrase. Database is inaccessible.", e)
                throw SecurityException(
                    "Database passphrase decryption failed. " +
                    "The existing database cannot be opened. " +
                    "This may occur after a device KeyStore reset or after restoring the app " +
                    "to a different device. Data recovery is not possible without the original device.",
                    e
                )
            }
        } else {
            generateAndStore(prefs)
        }
    }

    private fun generateAndStore(prefs: android.content.SharedPreferences): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_LENGTH).also {
            java.security.SecureRandom().nextBytes(it)
        }

        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val encrypted = cipher.doFinal(passphrase)
        val iv = cipher.iv

        // ✅ FIX #8: Use commit() instead of apply() for security-critical write
        prefs.edit()
            .putString(PREF_ENCRYPTED_PASSPHRASE, android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
            .putString(PREF_IV, android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
            .commit()

        Log.d(TAG, "Generated and stored new database passphrase")
        return passphrase
    }

    private fun decryptPassphrase(encryptedB64: String, ivB64: String): ByteArray {
        val encrypted = android.util.Base64.decode(encryptedB64, android.util.Base64.NO_WRAP)
        val iv = android.util.Base64.decode(ivB64, android.util.Base64.NO_WRAP)
        val key = getOrCreateKey()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        keyStore.getEntry(KEYSTORE_ALIAS, null)?.let { entry ->
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }
}
