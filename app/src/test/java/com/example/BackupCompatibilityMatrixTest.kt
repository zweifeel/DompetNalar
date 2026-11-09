package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import com.example.data.model.BackupDataPayload
import com.example.data.model.BackupEnvelopeV2
import com.example.data.model.CategoryItem
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.EncryptionUtils
import com.example.util.GoogleDriveService
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import kotlin.collections.AbstractList

/**
 * Phase 4C Step 1: Backup / Restore Comprehensive Test Matrix
 *
 * Implements 26 distinct verification scenarios across 5 categories:
 * - Group 1: Backup v1 Legacy (Scenarios 1-4)
 * - Group 2: JSON Compatibility (Scenarios 5-10)
 * - Group 3: Encryption & Integrity (Scenarios 11-16)
 * - Group 4: Restore Safety & Atomicity (Scenarios 17-21)
 * - Group 5: Room 10 Contract Specification (Scenarios 22-26)
 *
 * Mode: STRICT TEST IMPLEMENTATION ONLY — Production code unmodified.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupCompatibilityMatrixTest {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private lateinit var context: Context
    private lateinit var db: FinSmartDatabase

    companion object {
        const val TEST_PASSPHRASE = "DompetNalar#TestCryptoPass2026!"

        private fun loadResourceText(fileName: String): String {
            val stream = BackupCompatibilityMatrixTest::class.java.classLoader?.getResourceAsStream(fileName)
                ?: File("app/src/test/resources/$fileName").takeIf { it.exists() }?.inputStream()
                ?: File("src/test/resources/$fileName").takeIf { it.exists() }?.inputStream()
                ?: throw IllegalArgumentException("Test fixture resource not found: $fileName")
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

        private fun loadResourceBytes(fileName: String): ByteArray {
            val stream = BackupCompatibilityMatrixTest::class.java.classLoader?.getResourceAsStream(fileName)
                ?: File("app/src/test/resources/$fileName").takeIf { it.exists() }?.inputStream()
                ?: File("src/test/resources/$fileName").takeIf { it.exists() }?.inputStream()
                ?: throw IllegalArgumentException("Test fixture resource not found: $fileName")
            return stream.use { it.readBytes() }
        }

        @JvmStatic
        @BeforeClass
        fun generateTamperedTagFixtureIfMissing() {
            // Generate deterministic backup_v2_tampered_tag.bin if not already present
            val existing = listOf(
                File("src/test/resources/backup_v2_tampered_tag.bin"),
                File("app/src/test/resources/backup_v2_tampered_tag.bin")
            ).firstOrNull { it.exists() && it.length() > 0L }
            if (existing != null) return

            val targetFile = if (File("src/test/resources").isDirectory) {
                File("src/test/resources/backup_v2_tampered_tag.bin")
            } else {
                File("app/src/test/resources/backup_v2_tampered_tag.bin")
            }
            targetFile.parentFile?.mkdirs()
                val moshiLocal = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val payloadJson = "{\"version\":2,\"exportedAt\":1704067200000,\"wallets\":[{\"id\":1,\"name\":\"Wallet AEAD\",\"type\":\"BANK\",\"balance\":100000.0}]}"

                // 1. Generate valid Format v2 envelope with PBKDF2 passphrase
                val validEnvelopeJson = EncryptionUtils.encryptV2(payloadJson, TEST_PASSPHRASE)
                val envelopeAdapter = moshiLocal.adapter(BackupEnvelopeV2::class.java)
                val envelope = envelopeAdapter.fromJson(validEnvelopeJson)
                    ?: throw IllegalStateException("Failed to create valid test envelope")

                // 2. Decode ciphertext bytes (which contains AES-GCM ciphertext + 128-bit authentication tag)
                val cipherBytes = EncryptionUtils.Base64Compat.decode(envelope.ciphertext)
                assertTrue("Ciphertext must be longer than 16 bytes tag", cipherBytes.size > 16)

                // 3. Tamper with the very last byte (the authentication tag)
                val tamperedBytes = cipherBytes.copyOf()
                val lastIdx = tamperedBytes.size - 1
                tamperedBytes[lastIdx] = (tamperedBytes[lastIdx].toInt() xor 0xFF).toByte()

                // 4. Create tampered envelope with modified authentication tag
                val tamperedCiphertextBase64 = EncryptionUtils.Base64Compat.encodeToString(tamperedBytes)
                val tamperedEnvelope = envelope.copy(ciphertext = tamperedCiphertextBase64)
                val tamperedEnvelopeJson = envelopeAdapter.toJson(tamperedEnvelope)

                // 5. Write tampered envelope bytes to fixture file
                targetFile.writeBytes(tamperedEnvelopeJson.toByteArray(Charsets.UTF_8))
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // GROUP 1: BACKUP V1 LEGACY (SCENARIOS 1-4)
    // =========================================================================

    /**
     * Skenario 1: Payload legacy Room 9 berhasil dibaca dan diurai bersih oleh DTO produksi.
     */
    @Test
    fun test01_backupV1_legacyPayloadDeserialization() {
        val json = loadResourceText("backup_v1_legacy_synthetic.json")
        val adapter = moshi.adapter(BackupDataPayload::class.java)
        val payload = adapter.fromJson(json)

        assertNotNull("Payload legacy harus berhasil di-parse oleh Moshi", payload)
        assertEquals(1, payload?.version)
        assertEquals(2, payload?.wallets?.size)
        assertEquals("Dompet Tunai", payload?.wallets?.get(0)?.name)
        assertEquals("Rekening Bank", payload?.wallets?.get(1)?.name)
        assertEquals(3, payload?.transactions?.size)
        assertEquals(1, payload?.categories?.size)
        assertEquals(1, payload?.users?.size)
        assertEquals(1, payload?.splitBillHistory?.size)
        assertEquals(1, payload?.budgetLimits?.size)
        assertEquals(1, payload?.debts?.size)
        assertEquals(1, payload?.assets?.size)
        assertEquals(1, payload?.installmentPlans?.size)
    }

    /**
     * Skenario 2: Missing optional fields diproses sesuai kontrak dan default Kotlin berlaku.
     */
    @Test
    fun test02_backupV1_missingOptionalFieldsProcessAccordingToContract() {
        val json = loadResourceText("backup_v1_missing_optional.json")
        val adapter = moshi.adapter(BackupDataPayload::class.java)
        val payload = adapter.fromJson(json)

        assertNotNull(payload)
        assertEquals(1, payload?.version)
        assertEquals(1, payload?.wallets?.size)

        // Verifikasi default Kotlin pada Wallet
        val wallet = payload!!.wallets.first()
        assertEquals("Dompet Tanpa Opsional", wallet.name)
        assertEquals(WalletType.BANK, wallet.type) // Default Kotlin
        assertEquals("", wallet.accountNumber)      // Default Kotlin
        assertEquals(0.0, wallet.balance, 0.001)   // Default Kotlin
        assertEquals("#0B57D0", wallet.colorHex)   // Default Kotlin
        assertEquals(false, wallet.isDefault)       // Default Kotlin
        assertNull(wallet.userId)                  // Default Kotlin

        // Verifikasi default Kotlin pada TransactionItem
        val tx = payload.transactions.first()
        assertEquals("Transaksi Esensial Saja", tx.title)
        assertEquals(50000.0, tx.amount, 0.001)
        assertEquals("category", tx.categoryIcon)
        assertNull(tx.targetWalletId)
        assertNull(tx.targetWalletName)
        assertEquals("", tx.note)
        assertEquals("", tx.tags)

        // Verifikasi missing collections bernilai emptyList() bukan null atau crash
        assertTrue(payload.categories.isEmpty())
        assertTrue(payload.users.isEmpty())
        assertTrue(payload.splitBillHistory.isEmpty())
        assertTrue(payload.budgetLimits.isEmpty())
        assertTrue(payload.debts.isEmpty())
        assertTrue(payload.assets.isEmpty())
        assertTrue(payload.installmentPlans.isEmpty())
    }

    /**
     * Skenario 3: Data historis tidak berubah akibat default yang keliru.
     */
    @Test
    fun test03_backupV1_historicalDataNotCorruptedByDefaultValues() {
        val json = loadResourceText("backup_v1_legacy_synthetic.json")
        val adapter = moshi.adapter(BackupDataPayload::class.java)
        val payload = adapter.fromJson(json)!!

        // Nominal spesifik harus tetap eksak, tidak ter-round atau ter-overwrite
        val salaryTx = payload.transactions.first { it.title == "Gaji Bulanan" }
        assertEquals(3000000.0, salaryTx.amount, 0.0001)
        assertEquals(TransactionType.INCOME, salaryTx.type)
        assertEquals(1704153600000L, salaryTx.timestamp)

        val plan = payload.installmentPlans.first()
        assertEquals(450000.0, plan.installmentAmount, 0.0001)
        assertEquals(12, plan.totalTenor)
        assertEquals(2, plan.paidCount)
        assertEquals(10, plan.remainingTenor)
        assertEquals("Bulanan", plan.frequency)
    }

    /**
     * Skenario 4: Field yang tidak diketahui (unknown JSON keys) tidak menyebabkan parse gagal
     * atau menghapus data penting secara diam-diam.
     */
    @Test
    fun test04_backupV1_unknownFieldsDoNotSilentlyDeleteData() {
        // Payload memuat key tidak dikenal: "_metadata", "unknownEngineVersion", "futureFeatureFlag"
        val json = loadResourceText("backup_v1_legacy_synthetic.json")
        val adapter = moshi.adapter(BackupDataPayload::class.java)
        val payload = adapter.fromJson(json)

        assertNotNull(payload)
        // Data sah tetap terbaca utuh walaupun ada unknown metadata
        assertEquals(2, payload?.wallets?.size)
        assertEquals(3, payload?.transactions?.size)
    }

    // =========================================================================
    // GROUP 2: JSON COMPATIBILITY (SCENARIOS 5-10)
    // =========================================================================

    /**
     * Skenario 5: Perbedaan perilaku missing field dibanding explicit null pada field dengan default.
     */
    @Test
    fun test05_json_missingFieldVsExplicitNullOnOptional() {
        // A. Missing field "note": Moshi menerapkan default Kotlin note = ""
        val jsonMissingNote = """
            {
              "wallets": [],
              "transactions": [
                {
                  "id": 1,
                  "title": "Makan",
                  "amount": 25000.0,
                  "type": "EXPENSE",
                  "category": "Makanan",
                  "walletId": 1,
                  "walletName": "Kas"
                }
              ]
            }
        """.trimIndent()
        val payloadMissing = moshi.adapter(BackupDataPayload::class.java).fromJson(jsonMissingNote)
        assertNotNull(payloadMissing)
        assertEquals("", payloadMissing?.transactions?.first()?.note)

        // B. Explicit null pada non-nullable field "note": Moshi melempar JsonDataException!
        val jsonExplicitNullNote = """
            {
              "wallets": [],
              "transactions": [
                {
                  "id": 1,
                  "title": "Makan",
                  "amount": 25000.0,
                  "type": "EXPENSE",
                  "category": "Makanan",
                  "walletId": 1,
                  "walletName": "Kas",
                  "note": null
                }
              ]
            }
        """.trimIndent()
        try {
            moshi.adapter(BackupDataPayload::class.java).fromJson(jsonExplicitNullNote)
            fail("Moshi harus melempar JsonDataException ketika field non-nullable bernilai explicit null!")
        } catch (e: JsonDataException) {
            assertTrue("Exception harus mengindikasikan non-null was null", e.message?.contains("note") == true)
        }
    }

    /**
     * Skenario 6: Nullable field dengan explicit null diterima bersih oleh Moshi.
     */
    @Test
    fun test06_json_nullableFieldWithExplicitNull() {
        val json = loadResourceText("backup_v2_explicit_nulls.json")
        val adapter = moshi.adapter(BackupDataPayload::class.java)
        val payload = adapter.fromJson(json)

        assertNotNull(payload)
        val tx = payload!!.transactions.first()
        assertNull("targetWalletId adalah nullable dan harus bernilai null", tx.targetWalletId)
        assertNull("targetWalletName adalah nullable dan harus bernilai null", tx.targetWalletName)
        assertNull("userId adalah nullable dan harus bernilai null", tx.userId)

        val plan = payload.installmentPlans.first()
        assertNull("totalRemainingPrincipal adalah nullable dan harus bernilai null", plan.totalRemainingPrincipal)
        assertNull("lastPaymentDate adalah nullable dan harus bernilai null", plan.lastPaymentDate)
    }

    /**
     * Skenario 7: Non-nullable field dengan explicit null melempar JsonDataException.
     */
    @Test
    fun test07_json_nonNullableFieldWithExplicitNullThrowsException() {
        // name pada Wallet adalah non-nullable String tanpa default
        val invalidJson = """
            {
              "wallets": [
                {
                  "id": 1,
                  "name": null
                }
              ]
            }
        """.trimIndent()

        try {
            moshi.adapter(BackupDataPayload::class.java).fromJson(invalidJson)
            fail("Explicit null pada Wallet.name wajib melempar JsonDataException")
        } catch (e: JsonDataException) {
            assertTrue(e.message?.contains("name") == true)
        }
    }

    /**
     * Skenario 8: Unknown enum melempar JsonDataException tanpa adapter fallback.
     */
    @Test
    fun test08_json_unknownEnumThrowsException() {
        val invalidEnumJson = """
            {
              "transactions": [
                {
                  "id": 1,
                  "title": "Crypto Trade",
                  "amount": 500000.0,
                  "type": "UNKNOWN_TRANSACTION_TYPE",
                  "category": "Investasi",
                  "walletId": 1,
                  "walletName": "Kas"
                }
              ]
            }
        """.trimIndent()

        try {
            moshi.adapter(BackupDataPayload::class.java).fromJson(invalidEnumJson)
            fail("Nilai enum tidak dikenal pada TransactionType harus ditolak")
        } catch (e: JsonDataException) {
            assertTrue(e.message?.contains("Expected one of") == true || e.message?.contains("UNKNOWN_TRANSACTION_TYPE") == true)
        }
    }

    /**
     * Skenario 9: Unknown / non-monthly frequency pada PayLater dipertahankan tanpa pemaksaan.
     */
    @Test
    fun test09_json_unknownFrequencyInPayLaterPreserved() {
        val json = loadResourceText("backup_v2_unsupported_frequency.json")
        val adapter = moshi.adapter(BackupDataPayload::class.java)
        val payload = adapter.fromJson(json)

        assertNotNull(payload)
        val plans = payload!!.installmentPlans
        assertEquals(2, plans.size)

        val weeklyPlan = plans.first { it.id == "plan-weekly-legacy" }
        assertEquals("Mingguan", weeklyPlan.frequency)
        assertEquals(3, weeklyPlan.paidCount)
        assertEquals(8, weeklyPlan.totalTenor)
        assertEquals(5, weeklyPlan.remainingTenor)
        assertEquals("2024-01-22", weeklyPlan.nextDueDate)

        val lumpSumPlan = plans.first { it.id == "plan-lump-sum-legacy" }
        assertEquals("Sekali Bayar", lumpSumPlan.frequency)
        assertEquals(0, lumpSumPlan.paidCount)
        assertEquals(1, lumpSumPlan.totalTenor)
        assertEquals(1, lumpSumPlan.remainingTenor)
        assertNull(lumpSumPlan.lastPaymentDate)
    }

    /**
     * Skenario 10: Malformed JSON melempar exception parsing secara tepat.
     */
    @Test
    fun test10_json_malformedJsonThrowsException() {
        val malformedJson = "{ \"version\": 1, \"wallets\": [ { \"id\": 1, \"name\": \"Broken JSON\" "

        try {
            moshi.adapter(BackupDataPayload::class.java).fromJson(malformedJson)
            fail("JSON rusak / tidak tertutup harus melempar exception")
        } catch (e: Exception) {
            assertTrue(e is JsonEncodingException || e is IOException || e is JsonDataException)
        }
    }

    // =========================================================================
    // GROUP 3: ENCRYPTION & INTEGRITY (SCENARIOS 11-16)
    // =========================================================================

    /**
     * Skenario 11: AES-CBC legacy format v1 terenkripsi dan terdekripsi dengan benar.
     */
    @Test
    fun test11_encryption_aesCbcLegacyV1Format() {
        val sample = "{\"version\":1,\"test\":\"legacy_v1_aes_cbc\"}"
        val encrypted = EncryptionUtils.encryptLegacyV1(sample)

        assertFalse("Ciphertext v1 tidak boleh terdeteksi sebagai v2 envelope", EncryptionUtils.isFormatV2(encrypted))
        val decryptedDirect = EncryptionUtils.decryptLegacyV1(encrypted)
        assertEquals(sample, decryptedDirect)

        val decryptedDispatcher = EncryptionUtils.decrypt(encrypted)
        assertEquals(sample, decryptedDispatcher)
    }

    /**
     * Skenario 12: AES-GCM v2 format menghasilkan envelope valid dan terdekripsi dengan benar.
     */
    @Test
    fun test12_encryption_aesGcmV2Format() {
        val sample = "{\"version\":2,\"test\":\"v2_aes_gcm_aead\"}"
        val envelopeJson = EncryptionUtils.encryptV2(sample, TEST_PASSPHRASE)

        assertTrue(EncryptionUtils.isFormatV2(envelopeJson))
        val envelope = moshi.adapter(BackupEnvelopeV2::class.java).fromJson(envelopeJson)
        assertNotNull(envelope)
        assertEquals(2, envelope?.formatVersion)
        assertEquals("AES-GCM-256", envelope?.algorithm)
        assertEquals("PBKDF2", envelope?.keyProtection)
        assertNotNull(envelope?.iv)
        assertNotNull(envelope?.salt)

        val decrypted = EncryptionUtils.decrypt(envelopeJson, TEST_PASSPHRASE)
        assertEquals(sample, decrypted)
    }

    /**
     * Skenario 13: Invalid authentication tag pada Format v2 wajib gagal otentikasi.
     */
    @Test
    fun test13_encryption_invalidAuthenticationTagFails() {
        val tamperedEnvelopeBytes = loadResourceBytes("backup_v2_tampered_tag.bin")
        val tamperedEnvelopeJson = String(tamperedEnvelopeBytes, Charsets.UTF_8)

        assertTrue(EncryptionUtils.isFormatV2(tamperedEnvelopeJson))

        var decryptFailed = false
        try {
            EncryptionUtils.decrypt(tamperedEnvelopeJson, TEST_PASSPHRASE)
        } catch (e: Exception) {
            decryptFailed = true
        }
        assertTrue("Dekripsi data dengan authentication tag yang dimodifikasi wajib melempar Exception!", decryptFailed)
    }

    /**
     * Skenario 14: Data ciphertext yang terpotong wajib gagal dekripsi.
     */
    @Test
    fun test14_encryption_truncatedCiphertextFails() {
        val validEnvelopeJson = EncryptionUtils.encryptV2("{\"data\":\"confidential\"}", TEST_PASSPHRASE)
        val envelope = moshi.adapter(BackupEnvelopeV2::class.java).fromJson(validEnvelopeJson)!!

        val truncatedCiphertext = envelope.ciphertext.substring(0, envelope.ciphertext.length / 2)
        val truncatedEnvelope = envelope.copy(ciphertext = truncatedCiphertext)
        val truncatedJson = moshi.adapter(BackupEnvelopeV2::class.java).toJson(truncatedEnvelope)

        var failed = false
        try {
            EncryptionUtils.decrypt(truncatedJson, TEST_PASSPHRASE)
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("Ciphertext terpotong harus ditolak", failed)
    }

    /**
     * Skenario 15: IV yang tidak valid (panjang tidak sesuai atau karakter korup) wajib gagal.
     */
    @Test
    fun test15_encryption_invalidIvFails() {
        val validEnvelopeJson = EncryptionUtils.encryptV2("{\"data\":\"iv_test\"}", TEST_PASSPHRASE)
        val envelope = moshi.adapter(BackupEnvelopeV2::class.java).fromJson(validEnvelopeJson)!!

        // IV pada AES-GCM harus 12 byte (16 karakter Base64). Kita rusak menjadi 4 byte
        val invalidIvEnvelope = envelope.copy(iv = "AAAA")
        val invalidJson = moshi.adapter(BackupEnvelopeV2::class.java).toJson(invalidIvEnvelope)

        var failed = false
        try {
            EncryptionUtils.decrypt(invalidJson, TEST_PASSPHRASE)
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("IV tidak valid wajib menyebabkan dekripsi ditolak", failed)
    }

    /**
     * Skenario 16: Payload non-JSON hasil dekripsi yang korup wajib gagal di tahap deserialisasi.
     */
    @Test
    fun test16_encryption_corruptedPayloadAfterDecryptFailsDeserialization() {
        val nonJsonPlaintext = "THIS IS NOT A JSON PAYLOAD AT ALL 123456789"
        val encryptedEnvelope = EncryptionUtils.encryptV2(nonJsonPlaintext, TEST_PASSPHRASE)

        val decrypted = EncryptionUtils.decrypt(encryptedEnvelope, TEST_PASSPHRASE)
        assertEquals(nonJsonPlaintext, decrypted)

        // Tahap deserialisasi DTO harus menolak teks non-JSON
        try {
            moshi.adapter(BackupDataPayload::class.java).fromJson(decrypted)
            fail("Deserialisasi plaintext non-JSON ke BackupDataPayload wajib gagal!")
        } catch (e: Exception) {
            assertTrue(e is JsonEncodingException || e is JsonDataException || e is IOException)
        }
    }

    // =========================================================================
    // GROUP 4: RESTORE SAFETY & ATOMICITY (SCENARIOS 17-21)
    // =========================================================================

    /**
     * Helper untuk membuat user aktif dan data finansial awal.
     */
    private suspend fun seedUserAndBaseline(userId: Long): Wallet {
        db.userDao().insertUser(
            User(
                id = userId,
                username = "user_$userId",
                email = "user$userId@example.com",
                role = UserRole.FREE_MEMBER
            )
        )
        val wallet = Wallet(
            id = 500L + userId,
            name = "Dompet Awal $userId",
            balance = 1000000.0,
            type = WalletType.BANK,
            userId = userId
        )
        db.walletDao().insertWallet(wallet)
        return wallet
    }

    /**
     * Skenario 17: Restore sukses mempertahankan relasi data (wallet mapping & transactions).
     */
    @Test
    fun test17_restoreSafety_successfulRestoreMaintainsRelationships() = runBlocking {
        val userId = 101L
        seedUserAndBaseline(userId)

        val json = loadResourceText("backup_v1_legacy_synthetic.json")
        val payload = moshi.adapter(BackupDataPayload::class.java).fromJson(json)!!

        FinanceViewModel.restoreUserDataInTransaction(db, payload, userId)

        val restoredWallets = db.walletDao().getAllWallets(userId).first()
        assertEquals(2, restoredWallets.size)

        val restoredTx = db.transactionDao().getAllTransactions(userId).first()
        assertEquals(3, restoredTx.size)

        // Verifikasi transfer mempertahankan source dan target wallet id yang valid di database baru
        val transferTx = restoredTx.first { it.type == TransactionType.TRANSFER }
        val walletIds = restoredWallets.map { it.id }.toSet()
        assertTrue("Source wallet transfer harus ada dalam dompet yang dipulihkan", transferTx.walletId in walletIds)
        assertNotNull("Target wallet transfer tidak boleh null", transferTx.targetWalletId)
        assertTrue("Target wallet transfer harus ada dalam dompet yang dipulihkan", transferTx.targetWalletId in walletIds)
        assertNotEquals("Source dan target wallet tidak boleh sama", transferTx.walletId, transferTx.targetWalletId)
    }

    /**
     * Skenario 18: [PRE-VALIDATION PATH] Restore gagal akibat validasi nominal negatif pra-transaksi
     * melindungi basis data dari korupsi sebelum transaksi Room dimulai.
     */
    @Test
    fun test18_restoreSafety_failedRestoreDoesNotCorruptOldDatabase() = runBlocking {
        val userId = 101L
        val originalWallet = seedUserAndBaseline(userId)

        val initialWallets = db.walletDao().getAllWallets(userId).first()
        assertEquals(1, initialWallets.size)
        assertEquals(originalWallet.name, initialWallets[0].name)
        assertEquals(1000000.0, initialWallets[0].balance, 0.001)

        // Buat payload invalid dengan jumlah transaksi negatif
        val invalidPayload = BackupDataPayload(
            version = 1,
            wallets = listOf(Wallet(id = 1, name = "New Wallet", balance = 50000.0)),
            transactions = listOf(
                TransactionItem(
                    id = 1,
                    title = "Illegal Negative Tx",
                    amount = -100000.0, // INVALID
                    type = TransactionType.EXPENSE,
                    category = "Lain",
                    walletId = 1,
                    walletName = "New Wallet"
                )
            )
        )

        var restoreFailed = false
        try {
            FinanceViewModel.restoreUserDataInTransaction(db, invalidPayload, userId)
        } catch (e: IllegalArgumentException) {
            restoreFailed = true
        }
        assertTrue("Restore data invalid wajib melempar IllegalArgumentException", restoreFailed)

        // Database LAMA harus tetap 100% utuh tanpa modifikasi apa pun!
        val postWallets = db.walletDao().getAllWallets(userId).first()
        assertEquals("Jumlah dompet awal harus tetap 1", 1, postWallets.size)
        assertEquals(originalWallet.name, postWallets[0].name)
        assertEquals(1000000.0, postWallets[0].balance, 0.001)
    }

    /**
     * Skenario 19: [PRE-VALIDATION PATH] Restore dengan referensi foreign key dompet yang tidak terdaftar
     * ditolak pada tahap pra-validasi (FinanceViewModel.kt:1038) sebelum transaksi Room dieksekusi.
     */
    @Test
    fun test19_restoreSafety_failureMidTransactionRollsBackCompletely() = runBlocking {
        val userId = 101L
        seedUserAndBaseline(userId)

        // Payload di mana transaksi merujuk pada walletId 999 yang tidak ada di daftar dompet payload
        val brokenFkPayload = BackupDataPayload(
            version = 1,
            wallets = listOf(Wallet(id = 1, name = "Dompet Sah", balance = 500000.0)),
            transactions = listOf(
                TransactionItem(
                    id = 1,
                    title = "Tx Broken FK",
                    amount = 10000.0,
                    type = TransactionType.EXPENSE,
                    category = "Makan",
                    walletId = 999, // TIDAK ADA DI WALLETS
                    walletName = "Dompet Fiktif"
                )
            )
        )

        var failed = false
        try {
            FinanceViewModel.restoreUserDataInTransaction(db, brokenFkPayload, userId)
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)

        // Verifikasi rollback penuh: Dompet awal user tetap ada, dompet 1 tidak masuk
        val wallets = db.walletDao().getAllWallets(userId).first()
        assertEquals(1, wallets.size)
        assertEquals("Dompet Awal $userId", wallets[0].name)
    }

    /**
     * Skenario 20: Restore lintas user tidak mencampur data antar pengguna.
     */
    @Test
    fun test20_restoreSafety_crossUserRestoreDoesNotMixData() = runBlocking {
        val userAId = 101L
        val userBId = 202L
        seedUserAndBaseline(userAId)
        val userBWallet = seedUserAndBaseline(userBId)

        // User A melakukan restore
        val json = loadResourceText("backup_v1_legacy_synthetic.json")
        val payload = moshi.adapter(BackupDataPayload::class.java).fromJson(json)!!
        FinanceViewModel.restoreUserDataInTransaction(db, payload, userAId)

        // Verifikasi User A menerima data pulih
        val userAWallets = db.walletDao().getAllWallets(userAId).first()
        assertEquals(2, userAWallets.size)

        // Verifikasi User B sama sekali tidak terpengaruh
        val userBWallets = db.walletDao().getAllWallets(userBId).first()
        assertEquals(1, userBWallets.size)
        assertEquals(userBWallet.name, userBWallets[0].name)
        assertEquals(userBWallet.balance, userBWallets[0].balance, 0.001)
    }

    /**
     * Skenario 21: Restore ganda tidak menghasilkan transaksi duplikat.
     */
    @Test
    fun test21_restoreSafety_restoreDoesNotProduceDuplicateTransactions() = runBlocking {
        val userId = 101L
        seedUserAndBaseline(userId)

        val json = loadResourceText("backup_v1_legacy_synthetic.json")
        val payload = moshi.adapter(BackupDataPayload::class.java).fromJson(json)!!

        // Eksekusi restore pertama
        FinanceViewModel.restoreUserDataInTransaction(db, payload, userId)
        assertEquals(3, db.transactionDao().getAllTransactions(userId).first().size)
        assertEquals(2, db.walletDao().getAllWallets(userId).first().size)

        // Eksekusi restore kedua dengan payload yang sama
        FinanceViewModel.restoreUserDataInTransaction(db, payload, userId)
        val finalTx = db.transactionDao().getAllTransactions(userId).first()
        val finalWallets = db.walletDao().getAllWallets(userId).first()

        // Harus tetap persis 3 transaksi dan 2 dompet, tidak berlipat ganda
        assertEquals(3, finalTx.size)
        assertEquals(2, finalWallets.size)
    }

    // =========================================================================
    // GROUP 5: ROOM 10 CONTRACT SPECIFICATION (SCENARIOS 22-26)
    // [STATUS: CONTRACT TEST ONLY — PROPOSED ROOM 10 SPECIFICATION VERIFICATION]
    // =========================================================================

    /**
     * Helper untuk membaca JSON payload v2 Room 10 sebagai generic map tree
     * tanpa memerlukan entity / DTO Room 10 di source produksi saat ini.
     */
    private fun loadGenericPayloadMap(fileName: String): Map<String, Any?> {
        val json = loadResourceText(fileName)
        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val adapter = moshi.adapter<Map<String, Any?>>(type)
        return adapter.fromJson(json) ?: emptyMap()
    }

    /**
     * Skenario 22: Payload multi-currency memiliki identitas mata uang eksplisit.
     * [STATUS: CONTRACT TEST ONLY]
     */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun test22_room10Contract_multiCurrencyPayloadExplicitIdentity() {
        val map = loadGenericPayloadMap("backup_v2_multicurrency_full.json")
        val wallets = map["wallets"] as? List<Map<String, Any?>>
        assertNotNull("Kontrak Room 10 harus mendefinisikan array wallets", wallets)
        assertEquals(2, wallets?.size)

        val idrWallet = wallets?.first { it["id"] == 1.0 || it["id"] == 1 }
        assertEquals("IDR", idrWallet?.get("currency"))
        assertEquals("CONFIRMED_BASE", idrWallet?.get("currencyStatus"))
        assertEquals(false, idrWallet?.get("isArchived"))

        val usdWallet = wallets?.first { it["id"] == 2.0 || it["id"] == 2 }
        assertEquals("USD", usdWallet?.get("currency"))
        assertEquals("CONFIRMED_USER", usdWallet?.get("currencyStatus"))
        assertEquals(false, usdWallet?.get("isArchived"))
    }

    /**
     * Skenario 23: Transfer menyimpan source dan target currency secara konsisten.
     * [STATUS: CONTRACT TEST ONLY]
     */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun test23_room10Contract_crossCurrencyTransferSourceAndTargetCurrency() {
        val map = loadGenericPayloadMap("backup_v2_multicurrency_full.json")
        val transactions = map["transactions"] as? List<Map<String, Any?>>
        assertNotNull(transactions)

        val transferTx = transactions?.first { it["type"] == "TRANSFER" }
        assertNotNull(transferTx)
        assertEquals("IDR", transferTx?.get("currency"))
        assertEquals("CONFIRMED_BASE", transferTx?.get("currencyStatus"))
        assertEquals("USD", transferTx?.get("targetCurrency"))
        assertEquals(200.0, (transferTx?.get("targetAmount") as? Number)?.toDouble() ?: 0.0, 0.001)
        assertEquals(16000.0, (transferTx?.get("exchangeRate") as? Number)?.toDouble() ?: 0.0, 0.001)
        assertEquals(25000.0, (transferTx?.get("feeAmount") as? Number)?.toDouble() ?: 0.0, 0.001)
        assertEquals("IDR", transferTx?.get("feeCurrency"))
        assertEquals("xfer_idr_usd_102", transferTx?.get("idempotencyKey"))
    }

    /**
     * Skenario 24: Installment periods mempertahankan bukti historis tanpa pemalsuan.
     * [STATUS: CONTRACT TEST ONLY]
     */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun test24_room10Contract_installmentPeriodsHistoricalEvidence() {
        val map = loadGenericPayloadMap("backup_v2_multicurrency_full.json")
        val periods = map["installmentPeriods"] as? List<Map<String, Any?>>
        assertNotNull("Kontrak Room 10 harus mendefinisikan array installmentPeriods", periods)
        assertEquals(3, periods?.size)

        // Periode 1 sudah lunas dengan tanggal bayar nyata dan tx id
        val p1 = periods?.first { (it["periodNumber"] as? Number)?.toInt() == 1 }
        assertEquals(true, p1?.get("isPaid"))
        assertEquals("2026-01-14", p1?.get("paidDate"))
        assertEquals(91.0, (p1?.get("paymentTransactionId") as? Number)?.toDouble() ?: 0.0, 0.01)

        // Periode 3 belum lunas: paidDate dan paymentTransactionId harus null tanpa fabrikasi tanggal
        val p3 = periods?.first { (it["periodNumber"] as? Number)?.toInt() == 3 }
        assertEquals(false, p3?.get("isPaid"))
        assertNull("Periode belum bayar tidak boleh memiliki paidDate palsu", p3?.get("paidDate"))
        assertNull("Periode belum bayar tidak boleh memiliki paymentTransactionId palsu", p3?.get("paymentTransactionId"))
    }

    /**
     * Skenario 25: Recurring schedules mempertahankan DAILY, WEEKLY, dan MONTHLY dengan field yang tepat.
     * [STATUS: CONTRACT TEST ONLY]
     */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun test25_room10Contract_recurringSchedulesFrequencyCoverage() {
        val map = loadGenericPayloadMap("backup_v2_multicurrency_full.json")
        val schedules = map["recurringSchedules"] as? List<Map<String, Any?>>
        assertNotNull("Kontrak Room 10 harus mendefinisikan array recurringSchedules", schedules)
        assertEquals(3, schedules?.size)

        val daily = schedules?.first { it["frequency"] == "DAILY" }
        assertNotNull(daily)
        assertNull("DAILY tidak membutuhkan dayOfMonth", daily?.get("dayOfMonth"))
        assertNull("DAILY tidak membutuhkan dayOfWeek", daily?.get("dayOfWeek"))

        val weekly = schedules?.first { it["frequency"] == "WEEKLY" }
        assertNotNull(weekly)
        assertEquals(6.0, (weekly?.get("dayOfWeek") as? Number)?.toDouble() ?: 0.0, 0.01)
        assertNull("WEEKLY tidak menggunakan dayOfMonth", weekly?.get("dayOfMonth"))

        val monthly = schedules?.first { it["frequency"] == "MONTHLY" }
        assertNotNull(monthly)
        assertEquals(20.0, (monthly?.get("dayOfMonth") as? Number)?.toDouble() ?: 0.0, 0.01)
        assertNull("MONTHLY tidak menggunakan dayOfWeek", monthly?.get("dayOfWeek"))
    }

    /**
     * Skenario 26: Financial operations mempertahankan identitas operasi, state, dan idempotensi.
     * [STATUS: CONTRACT TEST ONLY]
     */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun test26_room10Contract_financialOperationsIdentityAndState() {
        val map = loadGenericPayloadMap("backup_v2_multicurrency_full.json")
        val operations = map["financialOperations"] as? List<Map<String, Any?>>
        assertNotNull("Kontrak Room 10 harus mendefinisikan array financialOperations", operations)
        assertEquals(1, operations?.size)

        val op = operations?.first()
        assertEquals("xfer_idr_usd_102", op?.get("idempotencyKey"))
        assertEquals("CROSS_CURRENCY_TRANSFER", op?.get("operationType"))
        assertEquals("CONFIRMED", op?.get("state"))
        assertEquals(1.0, (op?.get("sourceWalletId") as? Number)?.toDouble() ?: 0.0, 0.01)
        assertEquals(2.0, (op?.get("targetWalletId") as? Number)?.toDouble() ?: 0.0, 0.01)
        assertEquals("IDR", op?.get("sourceCurrency"))
        assertEquals("USD", op?.get("targetCurrency"))
        assertEquals(16000.0, (op?.get("exchangeRate") as? Number)?.toDouble() ?: 0.0, 0.01)
    }

    // =========================================================================
    // GROUP 6: REAL ROOM TRANSACTION ROLLBACK (STEP 1B — SCENARIOS 27-28)
    // =========================================================================

    /**
     * Skenario 27: REAL ROOM TRANSACTION ROLLBACK PADA PIPELINE RESTORE PRODUKSI.
     *
     * Bukti Empiris:
     * 1. Transaksi database dimulai via FinanceViewModel.restoreUserDataInTransaction.
     * 2. Scoped deletion menghapus baris lama User A di SQLite.
     * 3. Wallet baru (dan transaksi pertama) berhasil ditulis ke dalam SQLite via DAO produksi.
     * 4. Pengecualian deterministik diinjeksi pada saat iterasi transaksi kedua di dalam blok withTransaction.
     * 5. Room mengeksekusi ROLLBACK penuh.
     * 6. Snapshot database setelah kegagalan identik secara finansial dengan kondisi sebelum transaksi:
     *    - Dompet lama User A (A1 & A2) dan transaksi lamanya tetap utuh dengan saldo persis sama.
     *    - Tidak ada dompet atau transaksi baru dari payload yang tertinggal.
     *    - Data milik User B tidak mengalami perubahan (kontrol isolasi multi-user).
     */
    @Test
    fun test27_restoreSafety_midTransactionFailureRollsBackAllDatabaseWrites() = runBlocking {
        val userAId = 101L
        val userBId = 202L

        // 1. Setup User A Baseline
        db.userDao().insertUser(User(id = userAId, username = "user_a", email = "usera@example.com", role = UserRole.FREE_MEMBER))
        val walletA1 = Wallet(id = 11L, name = "Dompet Operasional A", balance = 5_000_000.0, type = WalletType.BANK, userId = userAId)
        val walletA2 = Wallet(id = 12L, name = "Tabungan Masa Depan A", balance = 12_000_000.0, type = WalletType.BANK, userId = userAId)
        db.walletDao().insertWallets(listOf(walletA1, walletA2))

        val txA1 = TransactionItem(id = 101L, title = "Gaji Pokok", amount = 10_000_000.0, type = TransactionType.INCOME, category = "Gaji", walletId = 11L, walletName = "Dompet Operasional A", userId = userAId)
        val txA2 = TransactionItem(id = 102L, title = "Transfer ke Tabungan", amount = 2_000_000.0, type = TransactionType.TRANSFER, category = "Transfer", walletId = 11L, walletName = "Dompet Operasional A", targetWalletId = 12L, targetWalletName = "Tabungan Masa Depan A", userId = userAId)
        db.transactionDao().insertTransaction(txA1)
        db.transactionDao().insertTransaction(txA2)

        // 2. Setup User B Baseline (Kontrol Isolasi Multi-User)
        db.userDao().insertUser(User(id = userBId, username = "user_b", email = "userb@example.com", role = UserRole.FREE_MEMBER))
        val walletB1 = Wallet(id = 21L, name = "Dompet Pribadi User B", balance = 8_500_000.0, type = WalletType.BANK, userId = userBId)
        db.walletDao().insertWallet(walletB1)
        val txB1 = TransactionItem(id = 201L, title = "Bonus User B", amount = 8_500_000.0, type = TransactionType.INCOME, category = "Bonus", walletId = 21L, walletName = "Dompet Pribadi User B", userId = userBId)
        db.transactionDao().insertTransaction(txB1)

        // 3. Catat Snapshot Sebelum Transaksi Dimulai
        val beforeWalletsA = db.walletDao().getAllWallets(userAId).first()
        val beforeTxA = db.transactionDao().getAllTransactions(userAId).first()
        val beforeWalletsB = db.walletDao().getAllWallets(userBId).first()
        val beforeTxB = db.transactionDao().getAllTransactions(userBId).first()

        assertEquals(2, beforeWalletsA.size)
        assertEquals(2, beforeTxA.size)
        assertEquals(1, beforeWalletsB.size)
        assertEquals(1, beforeTxB.size)
        assertEquals(17_000_000.0, beforeWalletsA.sumOf { it.balance }, 0.001)

        // 4. Siapkan Payload Khusus dengan Injeksi Kegagalan Mid-Transaction
        val restoredWallet = Wallet(id = 99L, name = "Dompet Baru Restore", balance = 1_000_000.0, type = WalletType.BANK)
        val txItem1 = TransactionItem(id = 901L, title = "Tx Pertama Sukses Ditulis", amount = 100_000.0, type = TransactionType.EXPENSE, category = "Makanan", walletId = 99L, walletName = "Dompet Baru Restore")
        val txItem2 = TransactionItem(id = 902L, title = "Tx Kedua Pemicu Crash", amount = 200_000.0, type = TransactionType.EXPENSE, category = "Transport", walletId = 99L, walletName = "Dompet Baru Restore")

        val underlyingTxList = listOf(txItem1, txItem2)

        var iteratorCallCount = 0
        var firstWriteObserved = false
        var failureInjected = false

        val failureInjectingTxList = object : AbstractList<TransactionItem>() {
            override val size: Int
                get() = underlyingTxList.size

            override fun get(index: Int): TransactionItem {
                return underlyingTxList[index]
            }

            override fun iterator(): Iterator<TransactionItem> {
                val realIterator = underlyingTxList.iterator()
                iteratorCallCount++
                val currentPass = iteratorCallCount

                return object : Iterator<TransactionItem> {
                    private var index = 0

                    override fun hasNext(): Boolean {
                        return realIterator.hasNext()
                    }

                    override fun next(): TransactionItem {
                        val item = realIterator.next()
                        if (currentPass >= 3) {
                            // Pass 1 = Line 1005 (validasi amount)
                            // Pass 2 = Line 1036 (validasi walletIdSet)
                            // Pass 3 = Line 1111 (INSIDE database.withTransaction, setelah clearAll dan insertWallet)
                            if (index == 0) {
                                firstWriteObserved = true
                            } else if (index == 1) {
                                failureInjected = true
                                throw IllegalStateException("SIMULATED_MID_TRANSACTION_FAILURE_DURING_DATABASE_WRITE")
                            }
                        }
                        index++
                        return item
                    }
                }
            }
        }

        val testPayload = BackupDataPayload(
            version = 1,
            wallets = listOf(restoredWallet),
            transactions = failureInjectingTxList
        )

        // 5. Eksekusi Restore Produksi dan Tangkap Kegagalan
        var caughtException: Exception? = null
        try {
            FinanceViewModel.restoreUserDataInTransaction(db, testPayload, userAId)
        } catch (e: Exception) {
            caughtException = e
        }

        // 6. Verifikasi Kegagalan Terjadi di Titik yang Dikehendaki
        assertNotNull("Exception wajib dilempar dari dalam blok transaksi", caughtException)
        assertTrue("Exception harus merupakan IllegalStateException pemicu crash", caughtException is IllegalStateException)
        assertEquals("SIMULATED_MID_TRANSACTION_FAILURE_DURING_DATABASE_WRITE", caughtException?.message)
        assertTrue("Operasi tulis pertama (wallet & tx 0) wajib telah dieksekusi sebelum kegagalan", firstWriteObserved)
        assertTrue("Injeksi kegagalan wajib telah dipicu di tengah transaksi", failureInjected)

        // 7. Verifikasi Rollback Penuh: Baca Ulang Database Melalui DAO Produksi
        val afterWalletsA = db.walletDao().getAllWallets(userAId).first()
        val afterTxA = db.transactionDao().getAllTransactions(userAId).first()
        val afterWalletsB = db.walletDao().getAllWallets(userBId).first()
        val afterTxB = db.transactionDao().getAllTransactions(userBId).first()

        // A. Dompet User A harus kembali utuh seperti sebelum transaksi
        assertEquals("Jumlah dompet User A setelah rollback harus persis sama dengan kondisi awal", beforeWalletsA.size, afterWalletsA.size)
        assertEquals(beforeWalletsA.map { it.name }.sorted(), afterWalletsA.map { it.name }.sorted())
        assertEquals(beforeWalletsA.map { it.balance }.sorted(), afterWalletsA.map { it.balance }.sorted())
        assertEquals(17_000_000.0, afterWalletsA.sumOf { it.balance }, 0.001)

        // B. Transaksi User A harus kembali utuh seperti sebelum transaksi
        assertEquals("Jumlah transaksi User A setelah rollback harus persis sama dengan kondisi awal", beforeTxA.size, afterTxA.size)
        assertEquals(beforeTxA.map { it.title }.sorted(), afterTxA.map { it.title }.sorted())
        assertEquals(beforeTxA.map { it.amount }.sorted(), afterTxA.map { it.amount }.sorted())

        // C. Tidak ada dompet atau transaksi parsial dari payload restore yang tertinggal
        assertFalse("Dompet baru dari payload restore tidak boleh tertinggal di basis data", afterWalletsA.any { it.name == "Dompet Baru Restore" })
        assertFalse("Transaksi pertama dari payload restore tidak boleh tertinggal di basis data", afterTxA.any { it.title == "Tx Pertama Sukses Ditulis" })

        // D. Kontrol Isolasi User B: Sama sekali tidak berubah
        assertEquals(beforeWalletsB.size, afterWalletsB.size)
        assertEquals(beforeWalletsB[0].balance, afterWalletsB[0].balance, 0.001)
        assertEquals(beforeTxB.size, afterTxB.size)
        assertEquals(beforeTxB[0].title, afterTxB[0].title)
    }

    /**
     * Skenario 28: REAL ROOM TRANSACTION ROLLBACK MENGGUNAKAN DAO PRODUKSI SECARA LANGSUNG.
     *
     * Bukti Empiris:
     * 1. Menjalankan db.withTransaction secara langsung dengan WalletDao produksi.
     * 2. Operasi tulis 1: walletDao.adjustBalance menambah saldo +1.000.000 (terbukti termutasi di SQLite dalam transaksi).
     * 3. Operasi tulis 2: walletDao.insertWallet menambahkan dompet baru (terbukti memiliki ID sah di SQLite dalam transaksi).
     * 4. Pengecualian deterministik dilempar di tengah transaksi sebelum commit.
     * 5. Room memanggil ROLLBACK.
     * 6. Verifikasi pasca-kegagalan: Saldo dompet awal kembali ke 5.000.000 dan dompet baru lenyap sepenuhnya.
     */
    @Test
    fun test28_roomTransaction_midTransactionFailureRollsBackProductionDaoWrites() = runBlocking {
        val userId = 303L
        db.userDao().insertUser(User(id = userId, username = "user_direct_tx", email = "direct@example.com", role = UserRole.FREE_MEMBER))
        val initialWallet = Wallet(id = 55L, name = "Dompet Uji Transaksi", balance = 5_000_000.0, type = WalletType.BANK, userId = userId)
        val initialWalletId = db.walletDao().insertWallet(initialWallet)

        val beforeWallet = db.walletDao().getWalletById(initialWalletId, userId)
        assertNotNull(beforeWallet)
        assertEquals(5_000_000.0, beforeWallet!!.balance, 0.001)

        var firstWriteVisibleInsideTx = false
        var secondWriteVisibleInsideTx = false
        var exceptionThrown = false

        try {
            db.withTransaction {
                // Operasi Tulis 1: Tambah saldo +1.000.000 menggunakan DAO produksi
                db.walletDao().adjustBalance(initialWalletId, userId, 1_000_000.0)

                // Buktikan di dalam transaksi bahwa mutasi 1 telah nyata terjadi di SQLite
                val inTxWallet = db.walletDao().getWalletById(initialWalletId, userId)
                if (inTxWallet?.balance == 6_000_000.0) {
                    firstWriteVisibleInsideTx = true
                }

                // Operasi Tulis 2: Sisipkan dompet baru menggunakan DAO produksi
                val tempWallet = Wallet(name = "Dompet Sementara Dalam Transaksi", balance = 250_000.0, type = WalletType.CASH, userId = userId)
                val tempWalletId = db.walletDao().insertWallet(tempWallet)
                val inTxTempWallet = db.walletDao().getWalletById(tempWalletId, userId)
                if (inTxTempWallet != null) {
                    secondWriteVisibleInsideTx = true
                }

                // Paksa kegagalan deterministik sebelum transaksi selesai (commit)
                throw RuntimeException("FORCED_MID_TRANSACTION_ROLLBACK_VERIFICATION")
            }
        } catch (e: RuntimeException) {
            if (e.message == "FORCED_MID_TRANSACTION_ROLLBACK_VERIFICATION") {
                exceptionThrown = true
            }
        }

        // Verifikasi kondisi eksekusi
        assertTrue("Operasi tulis 1 wajib nyata terjadi dan terbaca di dalam transaksi", firstWriteVisibleInsideTx)
        assertTrue("Operasi tulis 2 wajib nyata terjadi dan terbaca di dalam transaksi", secondWriteVisibleInsideTx)
        assertTrue("Pengecualian pembatalan transaksi wajib tertangkap", exceptionThrown)

        // Verifikasi Rollback Penuh pasca-kegagalan via DAO produksi
        val postWallets = db.walletDao().getAllWallets(userId).first()
        assertEquals("Jumlah dompet harus kembali tepat 1 (dompet sementara wajib di-rollback)", 1, postWallets.size)

        val afterWallet = db.walletDao().getWalletById(initialWalletId, userId)
        assertNotNull(afterWallet)
        assertEquals("Saldo dompet harus kembali ke 5.000.000,00 (penambahan 1.000.000 wajib di-rollback)", 5_000_000.0, afterWallet!!.balance, 0.001)
        assertFalse("Dompet sementara tidak boleh tertinggal di basis data", postWallets.any { it.name == "Dompet Sementara Dalam Transaksi" })
    }
}
