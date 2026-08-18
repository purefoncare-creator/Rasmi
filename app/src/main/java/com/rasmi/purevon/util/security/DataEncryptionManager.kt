package com.rasmi.purevon.util.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore

/**
 * Manager for encrypting and decrypting sensitive data
 * Uses AndroidX Security Crypto library for secure encryption
 */
class DataEncryptionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DataEncryptionManager"
        private const val KEY_ALIAS = "purevon_data_key"
        private const val FALLBACK_KEY_ALIAS = "purevon_fallback_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH = 128
        private const val IV_LENGTH = 12
        
        // File names for encrypted data
        const val ENCRYPTED_CONTACTS_FILE = "encrypted_contacts.dat"
        const val ENCRYPTED_MESSAGES_FILE = "encrypted_messages.dat"
        const val ENCRYPTED_CALL_LOGS_FILE = "encrypted_call_logs.dat"
    }
    
    private val masterKey by lazy {
        try {
            MasterKey.Builder(context, KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "MasterKey creation failed, trying fallback", e)
            try {
                createFallbackKey()
            } catch (fallbackException: Exception) {
                Log.e(TAG, "Fallback key creation also failed", fallbackException)
                throw fallbackException
            }
        }
    }
    
    /**
     * Encrypt a string using AndroidX Security Crypto.
     * Data is written to the encrypted file; returns the fileName on success.
     */
    suspend fun encryptString(plainText: String, fileName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Delete file first — EncryptedFile won't overwrite
                val file = File(context.filesDir, fileName)
                file.delete()

                val encryptedFile = EncryptedFile.Builder(
                    context,
                    file,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                
                encryptedFile.openFileOutput().use { outputStream ->
                    outputStream.write(plainText.toByteArray(StandardCharsets.UTF_8))
                }
                
                // Return file name as success indicator (data is in the file)
                fileName
            } catch (e: Exception) {
                Log.e(TAG, "EncryptedFile write failed, using fallback AES", e)
                encryptWithFallback(plainText)
            }
        }
    }
    
    /**
     * Decrypt a string using AndroidX Security Crypto
     */
    suspend fun decryptString(fileName: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val encryptedFile = EncryptedFile.Builder(
                    context,
                    File(context.filesDir, fileName),
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                
                val byteArrayOutputStream = ByteArrayOutputStream()
                encryptedFile.openFileInput().use { inputStream ->
                    val buffer = ByteArray(1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        byteArrayOutputStream.write(buffer, 0, bytesRead)
                    }
                }
                
                Result.success(String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8))
            } catch (e: java.io.FileNotFoundException) {
                // File doesn't exist — genuinely empty
                Result.success("")
            } catch (e: Exception) {
                // Decryption failure — don't silently lose data
                Log.e("DataEncryptionManager", "Failed to decrypt $fileName", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Check if encryption is available on this device
     */
    fun isEncryptionAvailable(): Boolean {
        return try {
            MasterKey.Builder(context, KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Encrypt sensitive data for Room Database.
     * Returns a Base64-encoded IV+ciphertext string.
     * ✅ FIX #2: Throws on failure instead of returning empty string
     */
    fun encryptForDatabase(plainText: String): String {
        return try {
            encryptWithFallback(plainText)
        } catch (e: Exception) {
            Log.e(TAG, "encryptForDatabase failed", e)
            throw e
        }
    }
    
    /**
     * Decrypt data from Room Database.
     * Expects the Base64-encoded IV+ciphertext string from encryptForDatabase.
     * ✅ FIX #2: Throws on failure instead of returning empty string
     */
    fun decryptFromDatabase(encryptedText: String): String {
        return try {
            decryptWithFallback(encryptedText)
        } catch (e: Exception) {
            Log.e(TAG, "decryptFromDatabase failed", e)
            throw e
        }
    }
    
    /**
     * Create a fallback key for devices without proper KeyStore support
     */
    private fun createFallbackKey(): MasterKey {
        return MasterKey.Builder(context, KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(false)
            .build()
    }
    
    /**
     * Get or create the fallback AES key from AndroidKeyStore.
     * Unlike generateKey() which always creates a new key, this loads
     * the existing key if present.
     */
    private fun getOrCreateFallbackKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        
        // Return existing key if available
        val existingEntry = keyStore.getEntry(FALLBACK_KEY_ALIAS, null)
        if (existingEntry is KeyStore.SecretKeyEntry) {
            return existingEntry.secretKey
        }
        
        // Generate new key only if it doesn't exist yet
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val keySpec = KeyGenParameterSpec.Builder(
            FALLBACK_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    /**
     * Fallback encryption using AES/GCM with a persistent AndroidKeyStore key.
     * Returns Base64( IV || ciphertext ).
     */
    private fun encryptWithFallback(plainText: String): String {
        return try {
            val secretKey = getOrCreateFallbackKey()
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            
            // Combine IV and encrypted data
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback AES encryption failed", e)
            throw e
        }
    }
    
    /**
     * Fallback decryption using AES/GCM with persistent AndroidKeyStore key.
     * Expects Base64( IV || ciphertext ).
     */
    private fun decryptWithFallback(encryptedText: String): String {
        return try {
            val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
            
            if (combined.size <= IV_LENGTH) {
                throw IllegalArgumentException("Encrypted data too short")
            }
            
            val iv = ByteArray(IV_LENGTH)
            val encrypted = ByteArray(combined.size - IV_LENGTH)
            
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.size)
            
            val secretKey = getOrCreateFallbackKey()
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback AES decryption failed", e)
            throw e
        }
    }
    
    /**
     * Encrypt plain text directly to an arbitrary output file using EncryptedFile.
     * Returns success if the file was written, or failure with the cause.
     */
    suspend fun encryptToFile(plainText: String, outputFile: File): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                outputFile.delete()
                val encryptedFile = EncryptedFile.Builder(
                    context,
                    outputFile,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()

                encryptedFile.openFileOutput().use { outputStream ->
                    outputStream.write(plainText.toByteArray(StandardCharsets.UTF_8))
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "encryptToFile failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Decrypt the content of an arbitrary encrypted file.
     * Returns failure on any decryption error — never falls back to plaintext.
     */
    suspend fun decryptFromFile(inputFile: File): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val encryptedFile = EncryptedFile.Builder(
                    context,
                    inputFile,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()

                val byteArrayOutputStream = ByteArrayOutputStream()
                encryptedFile.openFileInput().use { inputStream ->
                    val buffer = ByteArray(1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        byteArrayOutputStream.write(buffer, 0, bytesRead)
                    }
                }
                Result.success(String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt file ${inputFile.name}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Clear all encrypted data (for logout or app reset)
     */
    suspend fun clearAllEncryptedData() {
        withContext(Dispatchers.IO) {
            listOf(
                ENCRYPTED_CONTACTS_FILE,
                ENCRYPTED_MESSAGES_FILE,
                ENCRYPTED_CALL_LOGS_FILE
            ).forEach { fileName ->
                try {
                    File(context.filesDir, fileName).delete()
                } catch (e: Exception) {
                    // Ignore deletion errors
                }
            }
        }
    }
}
