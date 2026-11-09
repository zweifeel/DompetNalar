package com.example.config

/**
 * Developer Account Identities & Access Configuration
 *
 * Centralizes the definition of trusted developer identities:
 * 1. [CONTACT_DEV_EMAIL]: lunachroc01@gmail.com - Primary Google Play Console contact email and Firestore Rules administrator.
 * 2. [OWNER_PROJECT_EMAIL]: zweifeel@gmail.com - Firebase Project & Google Play Console project owner.
 *
 * Security Principle:
 * - Entitlement to Master Developer role is granted ONLY upon cryptographically verified
 *   Google Sign-In authentication via Android Credential Manager, or via verified local
 *   Master Dev password authentication.
 * - Local manual registration with typed email NEVER grants Master Developer privileges.
 * - Lukas / lunachroc07@gmail.com account has been permanently removed in Phase 1G.
 */
object DeveloperAccountConfig {
    const val CONTACT_DEV_EMAIL = "lunachroc01@gmail.com"
    const val OWNER_PROJECT_EMAIL = "zweifeel@gmail.com"
    const val LEGACY_DEV_EMAIL = "lunachroc07@gmail.com"

    // Legacy typo constants retained for security regression tests to assert rejection
    const val CONTACT_DEV_EMAIL_ALIAS_TYPO = "lunachro01@gmail.com"
    const val LEGACY_DEV_EMAIL_ALIAS_TYPO = "lunachrooc07@gmail.com"

    val TRUSTED_DEVELOPER_EMAILS: Set<String> = setOf(
        CONTACT_DEV_EMAIL.lowercase(),
        OWNER_PROJECT_EMAIL.lowercase()
    )

    fun isTrustedDeveloperEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return TRUSTED_DEVELOPER_EMAILS.contains(email.trim().lowercase())
    }

    fun isMasterDevIdentifier(identifier: String?): Boolean {
        if (identifier.isNullOrBlank()) return false
        return isTrustedDeveloperEmail(identifier.trim())
    }
}
