package com.example.util

import com.example.data.local.UserDao
import com.example.data.model.User
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 12000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTE_LENGTH = 16
    const val PREFIX = "pbkdf2_sha256"

    fun hashPassword(password: String, salt: ByteArray = generateSalt()): String {
        require(password.isNotEmpty()) { "Password cannot be empty" }
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        return "$PREFIX$$ITERATIONS$${bytesToHex(salt)}$${bytesToHex(hash)}"
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        if (password.isEmpty() || storedHash.isEmpty()) return false
        if (!isHashed(storedHash)) {
            // Legacy / plaintext comparison during transition
            return MessageDigest.isEqual(
                password.toByteArray(Charsets.UTF_8),
                storedHash.toByteArray(Charsets.UTF_8)
            )
        }
        val parts = storedHash.split("$")
        if (parts.size != 4) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = hexToBytes(parts[2]) ?: return false
        val expectedHash = hexToBytes(parts[3]) ?: return false

        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, expectedHash.size * 8)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val computedHash = factory.generateSecret(spec).encoded

        return MessageDigest.isEqual(computedHash, expectedHash)
    }

    fun isHashed(storedHash: String): Boolean {
        if (!storedHash.startsWith("$PREFIX$")) return false
        val parts = storedHash.split("$")
        if (parts.size != 4) return false
        if (parts[1].toIntOrNull() == null) return false
        if (parts[2].isEmpty() || parts[3].isEmpty()) return false
        return true
    }

    fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTE_LENGTH)
        random.nextBytes(salt)
        return salt
    }

    suspend fun migrateLegacyPasswords(userDao: UserDao) {
        val users = userDao.getAllUsers().first()
        for (user in users) {
            if (user.passwordHash.isNotEmpty() && !isHashed(user.passwordHash)) {
                val secureHash = hashPassword(user.passwordHash)
                userDao.updateUser(user.copy(passwordHash = secureHash))
            }
        }
    }

    suspend fun migrateLegacyPasswords(userRepository: UserRepository) {
        val users = userRepository.getAllUsers().first()
        for (user in users) {
            if (user.passwordHash.isNotEmpty() && !isHashed(user.passwordHash)) {
                val secureHash = hashPassword(user.passwordHash)
                userRepository.updateUser(user.copy(passwordHash = secureHash))
            }
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            result.append(hexChars[i shr 4])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val result = ByteArray(hex.length / 2)
        try {
            for (i in result.indices) {
                val byteIndex = i * 2
                val byteVal = hex.substring(byteIndex, byteIndex + 2).toInt(16)
                result[i] = byteVal.toByte()
            }
            return result
        } catch (e: Exception) {
            return null
        }
    }
}
