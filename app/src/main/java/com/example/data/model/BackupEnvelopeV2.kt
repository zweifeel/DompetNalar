package com.example.data.model

import com.squareup.moshi.JsonClass

/**
 * Format v2 Envelope for Authenticated Backup Encryption (AES-GCM-256).
 *
 * This envelope wraps the ciphertext with outer unencrypted metadata,
 * a 12-byte random IV/nonce, and cryptographic parameters ensuring
 * authenticated encryption with associated data (AEAD) and tamper detection.
 */
@JsonClass(generateAdapter = true)
data class BackupEnvelopeV2(
    val formatVersion: Int = 2,
    val algorithm: String = "AES-GCM-256",
    val keyProtection: String = "LOCAL_KEY", // "LOCAL_KEY" (Android Keystore / local fallback) or "PBKDF2"
    val iv: String, // Base64 encoded 12-byte random nonce
    val ciphertext: String, // Base64 encoded AES-GCM ciphertext + 128-bit authentication tag
    val salt: String? = null, // Base64 encoded 16-byte salt (used when keyProtection == "PBKDF2")
    val createdAt: Long = System.currentTimeMillis(),
    val schemaVersion: Int = 5,
    val appVersion: String = "1.0.3"
)
