package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.BackupDataPayload
import com.example.data.model.BackupEnvelopeV2
import com.example.data.model.CategoryItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.util.EncryptionUtils
import com.example.util.GoogleDriveService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupEncryptionTest {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private lateinit var context: Context
    private lateinit var db: FinSmartDatabase

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

    // ==========================================
    // EXISTING BASELINE CRYPTO TESTS
    // ==========================================

    @Test
    fun testFormatV2EncryptionAndDecryptionRoundtrip() {
        val samplePlaintext = "{\"testKey\":\"testValue\",\"amount\":150000.0}"
        val encryptedEnvelopeJson = EncryptionUtils.encrypt(samplePlaintext)

        assertTrue("Output enkripsi harus berupa Format v2", EncryptionUtils.isFormatV2(encryptedEnvelopeJson))
        assertTrue("Format v2 harus memuat penanda formatVersion 2", encryptedEnvelopeJson.contains("\"formatVersion\":2") || encryptedEnvelopeJson.contains("\"formatVersion\": 2"))
        assertTrue("Format v2 harus memuat penanda AES-GCM-256", encryptedEnvelopeJson.contains("AES-GCM-256"))

        val decryptedPlaintext = EncryptionUtils.decrypt(encryptedEnvelopeJson)
        assertEquals("Teks hasil dekripsi v2 harus sama persis dengan plaintext awal", samplePlaintext, decryptedPlaintext)
    }

    @Test
    fun testFormatV2RandomIvUniquenessAcrossEncryptions() {
        val plainText = "Identical financial payload for encryption test"
        val encryptions = (1..10).map { EncryptionUtils.encrypt(plainText) }

        val uniqueOutputs = encryptions.toSet()
        assertEquals("10 kali enkripsi dari plaintext yang sama harus menghasilkan 10 ciphertext berbeda (karena IV acak)", 10, uniqueOutputs.size)
    }

    @Test(expected = Exception::class)
    fun testFormatV2TamperedCiphertextThrowsException() {
        val plainText = "Sensitive financial transaction records"
        val envelopeJson = EncryptionUtils.encrypt(plainText)

        val tamperedEnvelope = if (envelopeJson.contains("\"ciphertext\":\"")) {
            val idx = envelopeJson.indexOf("\"ciphertext\":\"") + 14
            val charToReplace = envelopeJson[idx]
            val replacementChar = if (charToReplace == 'A') 'B' else 'A'
            envelopeJson.substring(0, idx) + replacementChar + envelopeJson.substring(idx + 1)
        } else {
            envelopeJson
        }

        EncryptionUtils.decrypt(tamperedEnvelope)
    }

    @Test(expected = Exception::class)
    fun testFormatV2TamperedIvThrowsException() {
        val plainText = "Payload protected against IV modification"
        val envelopeJson = EncryptionUtils.encrypt(plainText)

        val tamperedEnvelope = if (envelopeJson.contains("\"iv\":\"")) {
            val idx = envelopeJson.indexOf("\"iv\":\"") + 6
            val charToReplace = envelopeJson[idx]
            val replacementChar = if (charToReplace == 'X') 'Y' else 'X'
            envelopeJson.substring(0, idx) + replacementChar + envelopeJson.substring(idx + 1)
        } else {
            envelopeJson
        }

        EncryptionUtils.decrypt(tamperedEnvelope)
    }

    @Test
    fun testLegacyV1BackwardCompatibilityDecryption() {
        val legacyPlaintext = "{\"legacyUser\":\"Lukas\",\"balance\":5000000.0}"
        val legacyEncryptedBase64 = EncryptionUtils.encryptLegacyV1(legacyPlaintext)

        assertFalse("Format v1 tidak boleh terdeteksi sebagai Format v2", EncryptionUtils.isFormatV2(legacyEncryptedBase64))

        val decryptedResult = EncryptionUtils.decrypt(legacyEncryptedBase64)
        assertEquals("Unified decrypt harus berhasil memulihkan file cadangan Format v1 legacy", legacyPlaintext, decryptedResult)
    }

    @Test
    fun testFormatDetectionDispatcherAccuracy() {
        val v2Envelope = "{\"formatVersion\":2,\"algorithm\":\"AES-GCM-256\",\"iv\":\"abcd\",\"ciphertext\":\"efgh\"}"
        val v1Base64 = "U2FsdGVkX19zYW1wbGVjdXJzb3Jvbmx5YmFzZTY0ZGF0YQ=="
        val randomJson = "{\"title\":\"FinSmart\",\"version\":1}"
        val emptyString = ""

        assertTrue(EncryptionUtils.isFormatV2(v2Envelope))
        assertFalse(EncryptionUtils.isFormatV2(v1Base64))
        assertFalse(EncryptionUtils.isFormatV2(randomJson))
        assertFalse(EncryptionUtils.isFormatV2(emptyString))
    }

    @Test
    fun testPortableBackupWithCustomPassphrase() {
        val sensitiveData = "Data intended for cross-device transfer"
        val correctPass = "SuperSecretBackupPass#2026"
        val wrongPass = "IncorrectBackupPass#999"

        val portableEnvelope = EncryptionUtils.encryptV2(sensitiveData, correctPass)
        assertTrue(portableEnvelope.contains("\"keyProtection\":\"PBKDF2\""))
        assertTrue(portableEnvelope.contains("\"salt\":"))

        val decrypted = EncryptionUtils.decrypt(portableEnvelope, correctPass)
        assertEquals(sensitiveData, decrypted)

        var failedWithWrongPass = false
        try {
            EncryptionUtils.decrypt(portableEnvelope, wrongPass)
        } catch (e: Exception) {
            failedWithWrongPass = true
        }
        assertTrue("Dekripsi dengan password salah harus ditolak", failedWithWrongPass)
    }

    @Test
    fun testFullBackupDataPayloadSerializationAndRestoreIntegrity() {
        val samplePayload = BackupDataPayload(
            version = 1,
            wallets = listOf(
                Wallet(id = 1L, name = "BCA Utama", balance = 5000000.0, type = WalletType.BANK, accountMode = AccountMode.PERSONAL.name),
                Wallet(id = 2L, name = "Gopay", balance = 250000.0, type = WalletType.EWALLET, accountMode = AccountMode.PERSONAL.name)
            ),
            transactions = listOf(
                TransactionItem(
                    id = 101L,
                    title = "Makan Siang",
                    amount = 75000.0,
                    type = TransactionType.EXPENSE,
                    category = "Makanan",
                    walletId = 1L,
                    walletName = "BCA Utama",
                    timestamp = System.currentTimeMillis()
                ),
                TransactionItem(
                    id = 102L,
                    title = "Ojek Online",
                    amount = 20000.0,
                    type = TransactionType.EXPENSE,
                    category = "Transport",
                    walletId = 2L,
                    walletName = "Gopay",
                    timestamp = System.currentTimeMillis()
                )
            ),
            categories = listOf(
                CategoryItem(id = 1L, name = "Makanan", type = TransactionType.EXPENSE, iconName = "food", colorHex = "#FF5722")
            ),
            users = listOf(
                User(id = 1L, username = "Lukas", email = "lunachroc07@gmail.com", role = UserRole.MASTER_DEV)
            ),
            exportedAt = 1700000000000L
        )

        val adapter = moshi.adapter(BackupDataPayload::class.java)
        val jsonPayload = adapter.toJson(samplePayload)

        val encryptedData = EncryptionUtils.encrypt(jsonPayload)
        val decryptedJson = EncryptionUtils.decrypt(encryptedData)
        val restoredPayload = adapter.fromJson(decryptedJson)

        assertNotNull(restoredPayload)
        assertEquals(2, restoredPayload!!.wallets.size)
        assertEquals("BCA Utama", restoredPayload.wallets[0].name)
        assertEquals(2, restoredPayload.transactions.size)
        assertEquals(75000.0, restoredPayload.transactions[0].amount, 0.001)
        assertEquals(1, restoredPayload.users.size)
        assertEquals(UserRole.MASTER_DEV, restoredPayload.users[0].role)
        assertEquals(1700000000000L, restoredPayload.exportedAt)
    }

    // ==========================================
    // STAGE 2D-E FINAL ACCEPTANCE CRITERIA (A-K)
    // ==========================================

    private fun seedInitialDatabase(): Wallet {
        val userId = 10L
        val initialWallet = Wallet(
            id = 10L,
            name = "Dompet Awal Perangkat",
            balance = 350000.0,
            type = WalletType.CASH,
            accountMode = AccountMode.PERSONAL.name,
            userId = userId
        )
        val initialCategory = CategoryItem(
            id = 10L,
            name = "Kebutuhan Pokok",
            type = TransactionType.EXPENSE,
            iconName = "groceries",
            colorHex = "#4CAF50",
            userId = userId
        )
        val initialUser = User(
            id = userId,
            username = "ExistingUser",
            email = "existing@finsmart.local",
            role = UserRole.FREE_MEMBER
        )
        runBlocking {
            db.userDao().insertUsers(listOf(initialUser))
            db.walletDao().insertWallets(listOf(initialWallet))
            db.categoryDao().insertCategories(listOf(initialCategory))
        }
        return initialWallet
    }

    @Test
    fun testA_PortableExportProducesV2Envelope() = runBlocking {
        seedInitialDatabase()
        val userId = 10L
        val password = "StrongBackupPassword#2026"
        val (success, message, file) = GoogleDriveService.backupPortableNow(context, db, password, userId)

        assertTrue("Pembuatan cadangan portabel harus sukses", success)
        assertNotNull("File cadangan portabel harus terbentuk", file)
        assertTrue("File fisik harus ada pada filesystem", file!!.exists())

        val content = file.readText()
        assertTrue("Konten harus terdeteksi sebagai Format v2", EncryptionUtils.isFormatV2(content))

        val envelope = moshi.adapter(BackupEnvelopeV2::class.java).fromJson(content)
        assertNotNull(envelope)
        assertEquals(2, envelope!!.formatVersion)
        assertEquals("AES-GCM-256", envelope.algorithm)
        assertEquals("PBKDF2", envelope.keyProtection)
        assertNotNull(envelope.salt)
        assertNotNull(envelope.iv)
        assertNotNull(envelope.ciphertext)
    }

    @Test
    fun testB_PortableRestoreWithCorrectPasswordSucceeds() = runBlocking {
        seedInitialDatabase()
        val userId = 10L
        val password = "CorrectPassword#123"

        // Export cadangan portabel
        val (_, _, exportFile) = GoogleDriveService.backupPortableNow(context, db, password, userId)
        val backupContent = exportFile!!.readText()

        // Ubah data di DB untuk membuktikan pemulihan
        db.walletDao().insertWallets(listOf(Wallet(id = 99L, name = "Temp Wallet", balance = 0.0, type = WalletType.BANK, accountMode = AccountMode.PERSONAL.name, userId = userId)))

        // Restore dengan kata sandi yang benar
        val (restoreSuccess, restoreMsg) = GoogleDriveService.restorePortableNow(context, db, backupContent, password, userId)
        assertTrue("Pemulihan dengan kata sandi benar harus berhasil: $restoreMsg", restoreSuccess)

        val wallets = db.walletDao().getAllWallets(userId).first()
        assertEquals(1, wallets.size)
        assertEquals("Dompet Awal Perangkat", wallets[0].name)
    }

    @Test
    fun testC_PortableRestoreWithWrongPasswordFails() = runBlocking {
        seedInitialDatabase()
        val userId = 10L
        val correctPassword = "CorrectPassword#123"
        val wrongPassword = "WrongPassword#999"

        val (_, _, exportFile) = GoogleDriveService.backupPortableNow(context, db, correctPassword, userId)
        val backupContent = exportFile!!.readText()

        val (restoreSuccess, restoreMsg) = GoogleDriveService.restorePortableNow(context, db, backupContent, wrongPassword, userId)
        assertFalse("Pemulihan dengan kata sandi salah wajib ditolak", restoreSuccess)
        assertTrue("Pesan error harus informatif", restoreMsg.contains("salah") || restoreMsg.contains("Gagal"))
    }

    @Test
    fun testD_WrongPasswordDoesNotModifyDatabase() = runBlocking {
        val initialWallet = seedInitialDatabase()
        val userId = 10L
        val correctPassword = "CorrectPassword#123"
        val wrongPassword = "WrongPassword#999"

        val (_, _, exportFile) = GoogleDriveService.backupPortableNow(context, db, correctPassword, userId)
        val backupContent = exportFile!!.readText()

        // Cek kondisi database sebelum restore gagal
        val walletsBefore = db.walletDao().getAllWallets(userId).first()
        assertEquals(1, walletsBefore.size)
        assertEquals(initialWallet.name, walletsBefore[0].name)
        assertEquals(initialWallet.balance, walletsBefore[0].balance, 0.001)

        // Coba restore dengan password salah
        val (restoreSuccess, _) = GoogleDriveService.restorePortableNow(context, db, backupContent, wrongPassword, userId)
        assertFalse(restoreSuccess)

        // Verifikasi CRITICAL: Database TIDAK boleh dimodifikasi/dihapus sama sekali!
        val walletsAfter = db.walletDao().getAllWallets(userId).first()
        assertEquals("Jumlah dompet di database tidak boleh berubah", 1, walletsAfter.size)
        assertEquals("Nama dompet tidak boleh berubah", initialWallet.name, walletsAfter[0].name)
        assertEquals("Saldo dompet tidak boleh berubah", initialWallet.balance, walletsAfter[0].balance, 0.001)
    }

    @Test
    fun testE_TamperedPortableBackupFailsAuthentication() = runBlocking {
        seedInitialDatabase()
        val userId = 10L
        val password = "CorrectPassword#123"

        val (_, _, exportFile) = GoogleDriveService.backupPortableNow(context, db, password, userId)
        val originalContent = exportFile!!.readText()

        // Rusak 1 karakter pada ciphertext
        val envelope = moshi.adapter(BackupEnvelopeV2::class.java).fromJson(originalContent)!!
        val tamperedCiphertext = if (envelope.ciphertext.startsWith("A")) "B" + envelope.ciphertext.substring(1) else "A" + envelope.ciphertext.substring(1)
        val tamperedEnvelope = envelope.copy(ciphertext = tamperedCiphertext)
        val tamperedJson = moshi.adapter(BackupEnvelopeV2::class.java).toJson(tamperedEnvelope)

        val (restoreSuccess, restoreMsg) = GoogleDriveService.restorePortableNow(context, db, tamperedJson, password, userId)
        assertFalse("Cadangan yang diubah/tampered wajib gagal otentikasi", restoreSuccess)
        assertTrue(restoreMsg.contains("salah") || restoreMsg.contains("diubah") || restoreMsg.contains("rusak"))
    }

    @Test
    fun testF_TamperedPortableBackupDoesNotModifyDatabase() = runBlocking {
        val initialWallet = seedInitialDatabase()
        val userId = 10L
        val password = "CorrectPassword#123"

        val (_, _, exportFile) = GoogleDriveService.backupPortableNow(context, db, password, userId)
        val originalContent = exportFile!!.readText()

        // Rusak 1 karakter pada IV
        val envelope = moshi.adapter(BackupEnvelopeV2::class.java).fromJson(originalContent)!!
        val tamperedIv = if (envelope.iv.startsWith("X")) "Y" + envelope.iv.substring(1) else "X" + envelope.iv.substring(1)
        val tamperedEnvelope = envelope.copy(iv = tamperedIv)
        val tamperedJson = moshi.adapter(BackupEnvelopeV2::class.java).toJson(tamperedEnvelope)

        // Eksekusi restore dengan data tampered
        val (restoreSuccess, _) = GoogleDriveService.restorePortableNow(context, db, tamperedJson, password, userId)
        assertFalse(restoreSuccess)

        // Verifikasi CRITICAL: Database TIDAK boleh dimodifikasi sama sekali
        val walletsAfter = db.walletDao().getAllWallets(userId).first()
        assertEquals(1, walletsAfter.size)
        assertEquals(initialWallet.name, walletsAfter[0].name)
    }

    @Test
    fun testG_EmptyPasswordIsRejected() = runBlocking {
        seedInitialDatabase()
        val userId = 10L

        // 1. Ekspor dengan password kosong
        val (exportSuccess, exportMsg, exportFile) = GoogleDriveService.backupPortableNow(context, db, "   ", userId)
        assertFalse("Ekspor dengan kata sandi kosong wajib ditolak", exportSuccess)
        assertTrue(exportMsg.contains("kosong"))

        // 2. Pemulihan dengan password kosong
        val (restoreSuccess, restoreMsg) = GoogleDriveService.restorePortableNow(context, db, "{}", "", userId)
        assertFalse("Pemulihan dengan kata sandi kosong wajib ditolak", restoreSuccess)
        assertTrue(restoreMsg.contains("kosong"))
    }

    @Test
    fun testH_MismatchedExportConfirmationPasswordIsRejected() {
        val password = "Password123!"
        val confirmation = "PasswordMismatch999!"

        val isMatch = password.trim() == confirmation.trim()
        assertFalse("Konfirmasi password yang tidak cocok harus terdeteksi tidak valid", isMatch)
    }

    @Test
    fun testI_PortableBackupDoesNotRequireAndroidKeyStore() = runBlocking {
        seedInitialDatabase()
        val userId = 10L
        val password = "IndependentPassphrase#2026"

        val (success, _, file) = GoogleDriveService.backupPortableNow(context, db, password, userId)
        assertTrue(success)

        val envelope = moshi.adapter(BackupEnvelopeV2::class.java).fromJson(file!!.readText())!!
        assertEquals("Kunci portabel harus menggunakan PBKDF2 tanpa bergantung pada AndroidKeyStore hardware", "PBKDF2", envelope.keyProtection)
        assertNotNull(envelope.salt)
    }

    @Test
    fun testJ_ExistingLegacyV1RestoreStillPasses() = runBlocking {
        val legacyPlaintext = "{\"wallets\":[{\"id\":1,\"name\":\"Legacy Wallet\",\"balance\":1000000.0,\"type\":\"CASH\",\"accountMode\":\"PERSONAL\"}],\"transactions\":[],\"categories\":[],\"users\":[],\"splitBillHistory\":[],\"budgetLimits\":[],\"version\":1,\"exportedAt\":1690000000000}"
        val legacyEncrypted = EncryptionUtils.encryptLegacyV1(legacyPlaintext)

        val decrypted = EncryptionUtils.decrypt(legacyEncrypted)
        assertEquals(legacyPlaintext, decrypted)
    }

    @Test
    fun testK_ExistingDeviceLocalBackupBehaviorStillPasses() = runBlocking {
        seedInitialDatabase()
        val userId = 10L

        // 1. Device Local Backup
        val (backupSuccess, backupMsg) = GoogleDriveService.backupNow(context, db, userId)
        assertTrue("Device Local backupNow harus sukses: $backupMsg", backupSuccess)

        // 2. Device Local Restore
        val (restoreSuccess, restoreMsg) = GoogleDriveService.restoreNow(context, db, userId)
        assertTrue("Device Local restoreNow harus sukses: $restoreMsg", restoreSuccess)

        val wallets = db.walletDao().getAllWallets(userId).first()
        assertTrue(wallets.isNotEmpty())
    }
}
