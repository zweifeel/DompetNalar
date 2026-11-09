package com.example.config

/**
 * Reviewer Account Provisioning Specification (Google Play Store Access)
 *
 * Configures the dedicated test account for Google Play App Review team.
 * Role: SMART_MASTER (full access to premium features for review)
 * Entitlement: Permanent / Non-expiring (subscriptionExpiresAt = null)
 * Data scope: Strictly isolated synthetic financial data
 * Developer Tools: Strictly disabled (role != MASTER_DEV)
 *
 * Plaintext passwords are NEVER stored in source code or application binaries.
 * Only the cryptographically salted PBKDF2-HMAC-SHA256 hash is embedded.
 */
object ReviewerAccountConfig {
    const val DEFAULT_REVIEWER_USER_ID = 2L
    const val REVIEWER_NAME = "DompetNalar Reviewer Test Account"
    const val REVIEWER_USERNAME = "GoogleReviewer"
    const val REVIEWER_EMAIL = "reviewer@finsmart.app"

    /**
     * Pre-computed PBKDF2-HMAC-SHA256 salted hash (12,000 iterations).
     * No plaintext password is ever stored or hardcoded in the application binary.
     */
    const val REVIEWER_PASSWORD_HASH = "pbkdf2_sha256\$12000\$cde41741dbd7b5a09381d3ab6fc45ab0\$a126367aa27eb628390bba0047433c3291727d951eddcc6f7a77a041e4b8d131"

    fun isReviewerIdentifier(identifier: String): Boolean {
        val trimmed = identifier.trim()
        return trimmed.equals(REVIEWER_USERNAME, ignoreCase = true) ||
                trimmed.equals(REVIEWER_EMAIL, ignoreCase = true)
    }
}
