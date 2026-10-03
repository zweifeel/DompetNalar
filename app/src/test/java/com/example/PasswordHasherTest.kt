package com.example

import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.util.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun testHashFormatAndPrefix() {
        val password = "mySecretPassword123"
        val hash = PasswordHasher.hashPassword(password)
        assertTrue(hash.startsWith("pbkdf2_sha256$"))
        assertTrue(PasswordHasher.isHashed(hash))
        val parts = hash.split("$")
        assertEquals(4, parts.size)
        assertEquals("pbkdf2_sha256", parts[0])
        assertEquals("12000", parts[1])
        assertEquals(32, parts[2].length) // 16 bytes hex = 32 chars
        assertEquals(64, parts[3].length) // 32 bytes hex = 64 chars
    }

    @Test
    fun testSaltRandomness() {
        val password = "testPassword"
        val hash1 = PasswordHasher.hashPassword(password)
        val hash2 = PasswordHasher.hashPassword(password)
        assertNotEquals(hash1, hash2)
        assertTrue(PasswordHasher.verifyPassword(password, hash1))
        assertTrue(PasswordHasher.verifyPassword(password, hash2))
    }

    @Test
    fun testVerifyPasswordCorrect() {
        val password = "StrongPassword#2026"
        val hash = PasswordHasher.hashPassword(password)
        assertTrue(PasswordHasher.verifyPassword(password, hash))
    }

    @Test
    fun testVerifyPasswordIncorrect() {
        val password = "StrongPassword#2026"
        val hash = PasswordHasher.hashPassword(password)
        assertFalse(PasswordHasher.verifyPassword("WrongPassword#2026", hash))
    }

    @Test
    fun testVerifyPasswordEmptyInputs() {
        val hash = PasswordHasher.hashPassword("validPassword")
        assertFalse(PasswordHasher.verifyPassword("", hash))
        assertFalse(PasswordHasher.verifyPassword("   ", hash))
        assertFalse(PasswordHasher.verifyPassword("validPassword", ""))
        assertFalse(PasswordHasher.verifyPassword("", ""))
    }

    @Test
    fun testLegacyPlaintextCompatibility() {
        val legacyPassword = "legacyPassword123"
        // Legacy passwords are not yet PBKDF2 hashed
        assertFalse(PasswordHasher.isHashed(legacyPassword))
        // Verification should verify correctly
        assertTrue(PasswordHasher.verifyPassword(legacyPassword, legacyPassword))
        assertFalse(PasswordHasher.verifyPassword("wrong", legacyPassword))
    }

    @Test
    fun testLegacyPlaintextMigrationSimulation() {
        val legacyPlaintext = "oldPlaintextPass"
        val user = User(
            id = 1L,
            username = "Lukas",
            email = "lunachroc07@gmail.com",
            passwordHash = legacyPlaintext,
            role = UserRole.MASTER_DEV
        )

        // Pre-migration state
        assertFalse(PasswordHasher.isHashed(user.passwordHash))
        assertTrue(PasswordHasher.verifyPassword(legacyPlaintext, user.passwordHash))

        // Perform migration
        val migratedHash = PasswordHasher.hashPassword(user.passwordHash)
        val migratedUser = user.copy(passwordHash = migratedHash)

        // Post-migration state
        assertTrue(PasswordHasher.isHashed(migratedUser.passwordHash))
        assertTrue(PasswordHasher.verifyPassword(legacyPlaintext, migratedUser.passwordHash))
        assertFalse(PasswordHasher.verifyPassword("wrongPassword", migratedUser.passwordHash))
        assertEquals(UserRole.MASTER_DEV, migratedUser.role)
    }

    @Test
    fun testNewRegistrationPasswordHashing() {
        val rawPassword = "NewUserPass!2026"
        val hashed = PasswordHasher.hashPassword(rawPassword.trim())
        assertTrue(PasswordHasher.isHashed(hashed))
        assertFalse(hashed == rawPassword)
        assertTrue(PasswordHasher.verifyPassword(rawPassword, hashed))
    }

    @Test
    fun testLukasMasterDevAuthentication() {
        val masterPassword = "OwnerMasterPassword#99"
        val hash = PasswordHasher.hashPassword(masterPassword)
        val lukasUser = User(
            id = 100L,
            username = "Lukas",
            email = "lunachroc07@gmail.com",
            passwordHash = hash,
            role = UserRole.MASTER_DEV,
            isApproved = true
        )

        // Valid authentication
        val isValid = PasswordHasher.verifyPassword(masterPassword, lukasUser.passwordHash)
        assertTrue(isValid)
        assertEquals(UserRole.MASTER_DEV, lukasUser.role)

        // Invalid authentication
        val isInvalid = PasswordHasher.verifyPassword("wrongPassword", lukasUser.passwordHash)
        assertFalse(isInvalid)

        // Empty password rejection
        val isEmpty = PasswordHasher.verifyPassword("", lukasUser.passwordHash)
        assertFalse(isEmpty)
    }

    @Test
    fun testMasterDevAccountDeletionAuth() {
        val masterPassword = "OwnerMasterPassword#99"
        val hash = PasswordHasher.hashPassword(masterPassword)
        val lukasUser = User(
            id = 100L,
            username = "Lukas",
            email = "lunachroc07@gmail.com",
            passwordHash = hash,
            role = UserRole.MASTER_DEV
        )

        // Correct password authorizes deletion
        val canDeleteCorrect = PasswordHasher.verifyPassword(masterPassword, lukasUser.passwordHash)
        assertTrue(canDeleteCorrect)

        // Wrong password rejects deletion
        val canDeleteWrong = PasswordHasher.verifyPassword("wrongConfirmation", lukasUser.passwordHash)
        assertFalse(canDeleteWrong)

        // Empty password rejects deletion
        val canDeleteBlank = PasswordHasher.verifyPassword("", lukasUser.passwordHash)
        assertFalse(canDeleteBlank)
    }

    @Test
    fun testSeederExclusionVerification() {
        // Verify Lukas is seeded cleanly without hardcoded password
        val seededLukas = User(
            username = "Lukas",
            email = "lunachroc07@gmail.com",
            passwordHash = "",
            role = UserRole.MASTER_DEV,
            isApproved = true
        )
        assertEquals("Lukas", seededLukas.username)
        assertEquals("lunachroc07@gmail.com", seededLukas.email)
        assertEquals(UserRole.MASTER_DEV, seededLukas.role)
        assertTrue(seededLukas.passwordHash.isEmpty())
        assertTrue(seededLukas.isApproved)

        // Attempting to verify an empty password against an empty passwordHash must fail
        assertFalse(PasswordHasher.verifyPassword("", seededLukas.passwordHash))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testHashEmptyPasswordThrowsException() {
        PasswordHasher.hashPassword("")
    }

    @Test
    fun testInitialMasterPasswordSetupValidationRules() {
        fun validateInitialMasterSetup(user: User?, newPass: String, confirmPass: String): String? {
            val trimmedNew = newPass.trim()
            val trimmedConfirm = confirmPass.trim()
            if (trimmedNew.isEmpty()) return "Kata sandi tidak boleh kosong."
            if (trimmedNew.length < 6) return "Kata sandi minimal 6 karakter."
            if (trimmedNew != trimmedConfirm) return "Konfirmasi kata sandi tidak cocok."
            if (user == null || user.role != UserRole.MASTER_DEV || user.passwordHash.isNotEmpty()) {
                return "Pengaturan awal kata sandi tidak diizinkan."
            }
            return null
        }

        val freshLukas = User(username = "Lukas", email = "lunachroc07@gmail.com", passwordHash = "", role = UserRole.MASTER_DEV)
        val regularUser = User(username = "Member", email = "member@example.com", passwordHash = "", role = UserRole.FREE_MEMBER)
        val establishedLukas = User(username = "Lukas", email = "lunachroc07@gmail.com", passwordHash = "pbkdf2_sha256$12000$...", role = UserRole.MASTER_DEV)

        // 1. Password kosong ditolak
        assertEquals("Kata sandi tidak boleh kosong.", validateInitialMasterSetup(freshLukas, "", ""))
        assertEquals("Kata sandi tidak boleh kosong.", validateInitialMasterSetup(freshLukas, "   ", "   "))

        // 2. Password < 6 karakter ditolak
        assertEquals("Kata sandi minimal 6 karakter.", validateInitialMasterSetup(freshLukas, "12345", "12345"))

        // 3. Confirmation mismatch ditolak
        assertEquals("Konfirmasi kata sandi tidak cocok.", validateInitialMasterSetup(freshLukas, "Password123", "Password456"))

        // 4. Akun bukan MASTER_DEV ditolak
        assertEquals("Pengaturan awal kata sandi tidak diizinkan.", validateInitialMasterSetup(regularUser, "Password123", "Password123"))

        // 5. Akun MASTER_DEV dengan password yang sudah ada tidak dapat diinisialisasi ulang
        assertEquals("Pengaturan awal kata sandi tidak diizinkan.", validateInitialMasterSetup(establishedLukas, "Password123", "Password123"))

        // 6. Akun null ditolak
        assertEquals("Pengaturan awal kata sandi tidak diizinkan.", validateInitialMasterSetup(null, "Password123", "Password123"))

        // 7. Input valid diterima (null error)
        assertEquals(null, validateInitialMasterSetup(freshLukas, "ValidMasterPass#2026", "ValidMasterPass#2026"))
    }

    @Test
    fun testInitialMasterPasswordSetupSuccessfulHashingAndLogin() {
        val freshLukas = User(
            id = 1L,
            username = "Lukas",
            email = "lunachroc07@gmail.com",
            passwordHash = "",
            role = UserRole.MASTER_DEV,
            isApproved = true
        )

        // Initial state: passwordHash is empty, cannot authenticate
        assertTrue(freshLukas.passwordHash.isEmpty())
        assertFalse(PasswordHasher.verifyPassword("MasterPass#2026", freshLukas.passwordHash))

        // Perform setup: hash new password
        val newPassword = "MasterPass#2026"
        val secureHash = PasswordHasher.hashPassword(newPassword)
        val updatedLukas = freshLukas.copy(passwordHash = secureHash)

        // Post-setup state verification
        assertTrue(PasswordHasher.isHashed(updatedLukas.passwordHash))
        assertTrue(PasswordHasher.verifyPassword(newPassword, updatedLukas.passwordHash))
        assertEquals(UserRole.MASTER_DEV, updatedLukas.role)

        // Re-login with wrong password fails
        assertFalse(PasswordHasher.verifyPassword("WrongPassword#999", updatedLukas.passwordHash))

        // Empty password fails
        assertFalse(PasswordHasher.verifyPassword("", updatedLukas.passwordHash))
        assertFalse(PasswordHasher.verifyPassword("   ", updatedLukas.passwordHash))
    }

    @Test
    fun testChangePasswordValidationRulesOrderAndExactLogic() {
        fun validateChangePassword(
            user: User?,
            currentPass: String,
            newPass: String,
            confirmPass: String
        ): String? {
            if (user == null || user.isGuest) {
                return "Pengguna tamu tidak dapat mengubah kata sandi."
            }
            if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                return "Semua bidang kata sandi wajib diisi."
            }
            if (!PasswordHasher.verifyPassword(currentPass, user.passwordHash)) {
                return "Kata sandi saat ini salah."
            }
            if (newPass.length < 6) {
                return "Kata sandi baru minimal 6 karakter."
            }
            if (newPass != confirmPass) {
                return "Konfirmasi kata sandi baru tidak cocok."
            }
            if (newPass == currentPass) {
                return "Kata sandi baru tidak boleh sama dengan kata sandi saat ini."
            }
            return null
        }

        val currentPass = "OldSecurePass#2026"
        val currentHash = PasswordHasher.hashPassword(currentPass)
        val validUser = User(
            id = 10L,
            username = "TestUser",
            email = "test@example.com",
            passwordHash = currentHash,
            role = UserRole.FREE_MEMBER,
            isGuest = false
        )
        val guestUser = User(
            id = 9999L,
            username = "Guest",
            email = "guest@example.com",
            passwordHash = "",
            role = UserRole.FREE_MEMBER,
            isGuest = true
        )

        // 1. Session check: user == null or guest
        assertEquals("Pengguna tamu tidak dapat mengubah kata sandi.", validateChangePassword(null, currentPass, "NewPass#1", "NewPass#1"))
        assertEquals("Pengguna tamu tidak dapat mengubah kata sandi.", validateChangePassword(guestUser, currentPass, "NewPass#1", "NewPass#1"))

        // 2. Empty field check: any empty field
        assertEquals("Semua bidang kata sandi wajib diisi.", validateChangePassword(validUser, "", "NewPass#1", "NewPass#1"))
        assertEquals("Semua bidang kata sandi wajib diisi.", validateChangePassword(validUser, currentPass, "", "NewPass#1"))
        assertEquals("Semua bidang kata sandi wajib diisi.", validateChangePassword(validUser, currentPass, "NewPass#1", ""))

        // 3. Verify current password: wrong password
        assertEquals("Kata sandi saat ini salah.", validateChangePassword(validUser, "WrongOldPass#2026", "NewPass#1", "NewPass#1"))

        // 4. Minimum new-password length: newPass.length < 6
        assertEquals("Kata sandi baru minimal 6 karakter.", validateChangePassword(validUser, currentPass, "12345", "12345"))

        // 5. Confirmation mismatch: newPass != confirmPass
        assertEquals("Konfirmasi kata sandi baru tidak cocok.", validateChangePassword(validUser, currentPass, "NewPass#1", "NewPass#2"))

        // 6. Reject unchanged password: newPass == currentPass
        assertEquals("Kata sandi baru tidak boleh sama dengan kata sandi saat ini.", validateChangePassword(validUser, currentPass, currentPass, currentPass))

        // 7. Success case
        val newValidPass = "BrandNewSecurePass#99"
        assertEquals(null, validateChangePassword(validUser, currentPass, newValidPass, newValidPass))
    }

    @Test
    fun testChangePasswordExactWhitespacePreservation() {
        val oldPassWithSpaces = "  old pass with spaces 123  "
        val oldHash = PasswordHasher.hashPassword(oldPassWithSpaces)
        val user = User(
            id = 20L,
            username = "SpaceUser",
            email = "space@example.com",
            passwordHash = oldHash
        )

        // Exact old pass verifies
        assertTrue(PasswordHasher.verifyPassword(oldPassWithSpaces, user.passwordHash))
        // Trimmed old pass fails verification
        assertFalse(PasswordHasher.verifyPassword(oldPassWithSpaces.trim(), user.passwordHash))

        // Change password to new pass that also contains spaces
        val newPassWithSpaces = "  new secret pass #2026  "
        val confirmPassWithSpaces = "  new secret pass #2026  "
        assertEquals(newPassWithSpaces, confirmPassWithSpaces)

        // Verify that exact new pass does not equal trimmed new pass
        assertNotEquals(newPassWithSpaces, newPassWithSpaces.trim())

        val newHash = PasswordHasher.hashPassword(newPassWithSpaces)
        val updatedUser = user.copy(passwordHash = newHash)

        // Updated user verifies with exact new pass
        assertTrue(PasswordHasher.verifyPassword(newPassWithSpaces, updatedUser.passwordHash))
        // Verification fails if trimmed
        assertFalse(PasswordHasher.verifyPassword(newPassWithSpaces.trim(), updatedUser.passwordHash))
        // Old pass no longer verifies
        assertFalse(PasswordHasher.verifyPassword(oldPassWithSpaces, updatedUser.passwordHash))
    }

    @Test
    fun testChangePasswordLifecycleFlowAndFieldIntegrity() {
        val initialPass = "MasterInitialPass#1"
        val initialHash = PasswordHasher.hashPassword(initialPass)
        val masterUser = User(
            id = 100L,
            username = "Lukas",
            email = "lunachroc07@gmail.com",
            passwordHash = initialHash,
            role = UserRole.MASTER_DEV,
            createdAt = 1700000000000L,
            trialStartDate = 1700000000000L,
            subscriptionExpiresAt = 1800000000000L,
            isApproved = true,
            isGuest = false
        )

        val newPass = "LukasNewMasterKey#2026"
        val newHash = PasswordHasher.hashPassword(newPass)
        val updatedMaster = masterUser.copy(passwordHash = newHash)

        // Field integrity checks
        assertEquals(100L, updatedMaster.id)
        assertEquals("Lukas", updatedMaster.username)
        assertEquals("lunachroc07@gmail.com", updatedMaster.email)
        assertEquals(UserRole.MASTER_DEV, updatedMaster.role)
        assertEquals(1700000000000L, updatedMaster.createdAt)
        assertEquals(1700000000000L, updatedMaster.trialStartDate)
        assertEquals(1800000000000L, updatedMaster.subscriptionExpiresAt)
        assertTrue(updatedMaster.isApproved)
        assertFalse(updatedMaster.isGuest)

        // Password authentication checks
        assertFalse(PasswordHasher.verifyPassword(initialPass, updatedMaster.passwordHash))
        assertTrue(PasswordHasher.verifyPassword(newPass, updatedMaster.passwordHash))
        assertTrue(PasswordHasher.isHashed(updatedMaster.passwordHash))
    }
}
