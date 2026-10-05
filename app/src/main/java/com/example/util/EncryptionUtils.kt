package com.example.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.config.AppConfig
import com.example.data.model.BackupEnvelopeV2
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Multi-Format Cryptographic Engine for FinSmart Backups (Dual Format v1/v2).
 *
 * Provides:
 * 1. Format v2 (Hardened / Default):
 *    - Authenticated Encryption: AES/GCM/NoPadding (AEAD)
 *    - Random 12-byte IV/nonce per backup via SecureRandom
 *    - 128-bit authentication tag with tamper detection
 *    - Hardware-backed Android Keystore key with deterministic local fallback
 *    - Optional PBKDF2 passphrase-based key derivation for portable backups
 * 2. Format v1 (Legacy Backward Compatibility):
 *    - AES/CBC/PKCS5Padding with legacy key and static IV
 *    - Fully supported for decrypting historical user backups without data loss
 */
object EncryptionUtils {

    // --- Format v2 Constants (AEAD AES-GCM) ---
    private const val V2_ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val PBKDF2_ITERATIONS = 100000
    private const val KEYSTORE_ALIAS = "FinSmartBackupKeyV2"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val LOCAL_FALLBACK_SALT = "FinSmartV2LocalFallbackSalt2026_KeyGen"

    // --- Format v1 Constants (Legacy AES-CBC) ---
    private const val V1_ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val V1_PASSPHRASE = "FinSmartSecureBackupKey2026!"

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * Cross-platform Base64 helper ensuring graceful operation across
     * Android runtime (API 24+) and JVM test runners.
     */
    @android.annotation.SuppressLint("NewApi")
    internal object Base64Compat {
        fun encodeToString(bytes: ByteArray): String {
            return try {
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } catch (e: Throwable) {
                java.util.Base64.getEncoder().encodeToString(bytes)
            }
        }

        fun decode(str: String): ByteArray {
            return try {
                android.util.Base64.decode(str.trim(), android.util.Base64.DEFAULT)
            } catch (e: Throwable) {
                java.util.Base64.getDecoder().decode(str.trim())
            }
        }
    }

    // ==========================================
    // FORMAT DETECTION DISPATCHER
    // ==========================================

    /**
     * Inspects content to determine if it is a Format v2 JSON envelope.
     */
    fun isFormatV2(content: String): Boolean {
        val trimmed = content.trim()
        if (!trimmed.startsWith("{")) return false
        return trimmed.contains("\"formatVersion\":2") || trimmed.contains("\"formatVersion\": 2")
    }

    // ==========================================
    // UNIFIED PUBLIC API
    // ==========================================

    /**
     * Encrypts plaintext into Format v2 (AES-GCM-256 authenticated envelope).
     */
    fun encrypt(plainText: String): String {
        return encryptV2(plainText)
    }

    /**
     * Decrypts encrypted content with automatic format dispatch:
     * - If Format v2: decodes envelope, validates AEAD tag, and decrypts via AES-GCM.
     * - If Format v1: routes to legacy AES-CBC engine for seamless backward compatibility.
     */
    fun decrypt(encryptedContent: String, customPassphrase: String? = null): String {
        val trimmed = encryptedContent.trim()
        return if (isFormatV2(trimmed)) {
            decryptV2(trimmed, customPassphrase)
        } else {
            decryptLegacyV1(trimmed)
        }
    }

    // ==========================================
    // FORMAT V2 IMPLEMENTATION (AES-GCM-256)
    // ==========================================

    /**
     * Encrypts plaintext into Format v2 JSON envelope with random IV and AEAD tag.
     */
    fun encryptV2(plainText: String, customPassphrase: String? = null): String {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }

        val (secretKey, keyProtection, saltBase64) = if (!customPassphrase.isNullOrBlank()) {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            Triple(deriveKeyFromPassphrase(customPassphrase, salt), "PBKDF2", Base64Compat.encodeToString(salt))
        } else {
            Triple(getSecretKeyForLocal(), "LOCAL_KEY", null)
        }

        val cipher = Cipher.getInstance(V2_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val envelope = BackupEnvelopeV2(
            formatVersion = 2,
            algorithm = "AES-GCM-256",
            keyProtection = keyProtection,
            iv = Base64Compat.encodeToString(iv),
            ciphertext = Base64Compat.encodeToString(cipherBytes),
            salt = saltBase64,
            createdAt = System.currentTimeMillis(),
            schemaVersion = 5,
            appVersion = AppConfig.APP_VERSION_NAME
        )
        return moshi.adapter(BackupEnvelopeV2::class.java).toJson(envelope)
    }

    /**
     * Decrypts Format v2 JSON envelope, enforcing authentication tag verification.
     */
    fun decryptV2(envelopeJson: String, customPassphrase: String? = null): String {
        val adapter = moshi.adapter(BackupEnvelopeV2::class.java)
        val envelope = adapter.fromJson(envelopeJson)
            ?: throw IllegalArgumentException("Format data cadangan v2 tidak valid")

        val iv = Base64Compat.decode(envelope.iv)
        val cipherBytes = Base64Compat.decode(envelope.ciphertext)

        val secretKey = if (envelope.keyProtection == "PBKDF2") {
            val salt = envelope.salt?.let { Base64Compat.decode(it) }
                ?: throw IllegalArgumentException("Garam (salt) kriptografi tidak ditemukan pada cadangan terproteksi sandi")
            val pass = customPassphrase
                ?: throw IllegalArgumentException("Kata sandi cadangan diperlukan untuk memulihkan file ini")
            deriveKeyFromPassphrase(pass, salt)
        } else {
            getSecretKeyForLocal()
        }

        val cipher = Cipher.getInstance(V2_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun getSecretKeyForLocal(): SecretKey {
        return try {
            getOrCreateKeystoreKey()
        } catch (e: Throwable) {
            getLocalFallbackSecretKey()
        }
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun getLocalFallbackSecretKey(): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(
            "FinSmartV2LocalSecureMasterKey2026!".toCharArray(),
            LOCAL_FALLBACK_SALT.toByteArray(Charsets.UTF_8),
            10000,
            256
        )
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    private fun deriveKeyFromPassphrase(passphrase: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    // ==========================================
    // FORMAT V1 LEGACY IMPLEMENTATION (AES-CBC)
    // ==========================================

    private fun deriveLegacyV1Key(): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(V1_PASSPHRASE.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(bytes, "AES")
    }

    /**
     * Legacy v1 encryption retained strictly for backward compatibility test verification.
     */
    fun encryptLegacyV1(plainText: String): String {
        val secretKey = deriveLegacyV1Key()
        val cipher = Cipher.getInstance(V1_ALGORITHM)
        val iv = ByteArray(16) { 0x4e.toByte() }
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64Compat.encodeToString(encryptedBytes)
    }

    /**
     * Legacy v1 decryption ensuring existing historical user backups remain restorable.
     */
    fun decryptLegacyV1(encryptedText: String): String {
        val secretKey = deriveLegacyV1Key()
        val cipher = Cipher.getInstance(V1_ALGORITHM)
        val iv = ByteArray(16) { 0x4e.toByte() }
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        val decodedBytes = Base64Compat.decode(encryptedText)
        val decryptedBytes = cipher.doFinal(decodedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
