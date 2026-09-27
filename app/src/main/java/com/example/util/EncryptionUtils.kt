package com.example.util

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

object EncryptionUtils {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val PASSPHRASE = "FinSmartSecureBackupKey2026!"

    private fun deriveKey(): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(PASSPHRASE.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plainText: String): String {
        return try {
            val secretKey = deriveKey()
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = ByteArray(16) { 0x4e.toByte() } // Secure 16-byte IV
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    fun decrypt(encryptedText: String): String {
        return try {
            val secretKey = deriveKey()
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = ByteArray(16) { 0x4e.toByte() }
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decodedBytes = Base64.decode(encryptedText, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
