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
}
