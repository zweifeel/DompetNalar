package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import com.example.data.model.BackupDataPayload
import com.example.data.model.BudgetLimitItem
import com.example.data.model.CategoryItem
import com.example.data.model.DebtItem
import com.example.data.model.SplitBillHistoryItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.GoogleDriveService
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiUserBackupRestoreIsolationTest {

    private lateinit var context: Context
    private lateinit var db: FinSmartDatabase

    private val userAId = 101L
    private val userBId = 202L

    @Before
    fun setUp() {
        runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // 1. Seed 13 Canonical System Categories (isSystem = 1, userId = null)
        FinSmartDatabase.DEFAULT_CATEGORIES.forEach {
            db.categoryDao().insertCategory(it)
        }
        assertEquals(13, db.categoryDao().getCategoryCount())

        // 2. Seed User A & User B
        db.userDao().insertUser(
            User(
                id = userAId,
                username = "usera",
                email = "usera@example.com",
                passwordHash = "hash_usera_secret_123",
                role = UserRole.FREE_MEMBER
            )
        )
        db.userDao().insertUser(
            User(
                id = userBId,
                username = "userb",
                email = "userb@example.com",
                passwordHash = "hash_userb_secret_456",
                role = UserRole.SMART_MASTER
            )
        )

        // 3. Seed User B baseline financial data
        db.walletDao().insertWallet(
            Wallet(
                id = 50L,
                name = "Dompet BCA User B",
                balance = 5_000_000.0,
                type = WalletType.BANK,
                userId = userBId
            )
        )
        db.transactionDao().insertTransaction(
            TransactionItem(
                id = 501L,
                title = "Gaji User B",
                amount = 10_000_000.0,
                type = TransactionType.INCOME,
                category = "Gaji Pokok",
                walletId = 50L,
                walletName = "Dompet BCA User B",
                userId = userBId
            )
        )
        db.debtDao().insertDebt(
            DebtItem(
                id = "debt-b-01",
                name = "KPR User B",
                amount = 250_000_000.0,
                interestRatePercent = 7.5,
                minMonthlyPayment = 3_000_000.0,
                userId = userBId
            )
        )
        db.assetDao().insertAsset(
            AssetItem(
                id = "asset-b-01",
                name = "Mobil User B",
                initialValue = 180_000_000.0,
                purchaseYear = 2022,
                category = AssetCategory.VEHICLE,
                userId = userBId
            )
        )
        db.budgetLimitDao().insertBudgetLimit(
            BudgetLimitItem(
                id = "total_monthly",
                limitAmount = 4_000_000.0,
                isCategory = false,
                userId = userBId
            )
        )

        // 4. Seed Legacy Quarantined Data (userId = null)
        db.walletDao().insertWallet(
            Wallet(
                id = 999L,
                name = "Dompet Warisan Karantina",
                balance = 750_000.0,
                type = WalletType.CASH,
                userId = null
            )
        )

        // 5. Seed User A baseline financial data (yang akan digantikan saat restore)
        db.walletDao().insertWallet(
            Wallet(
                id = 10L,
                name = "Dompet Lama A",
                balance = 100_000.0,
                type = WalletType.CASH,
                userId = userAId
            )
        )
        db.transactionDao().insertTransaction(
            TransactionItem(
                id = 101L,
                title = "Makan Siang Lama A",
                amount = 25_000.0,
                type = TransactionType.EXPENSE,
                category = "Makanan & Minuman",
                walletId = 10L,
                walletName = "Dompet Lama A",
                userId = userAId
            )
        )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // TEST 1: Restore akun A tidak mengubah data akun B
    @Test
    fun test01_RestoreUserADoesNotModifyUserBData() = runBlocking {
        val payloadUserA = BackupDataPayload(
            wallets = listOf(
                Wallet(id = 1L, name = "Dompet Baru A", balance = 3_000_000.0, type = WalletType.BANK, userId = userAId)
            ),
            transactions = listOf(
                TransactionItem(
                    id = 1L,
                    title = "Bonus A",
                    amount = 3_000_000.0,
                    type = TransactionType.INCOME,
                    category = "Bonus & Tunjangan",
                    walletId = 1L,
                    walletName = "Dompet Baru A",
                    userId = userAId
                )
            ),
            debts = listOf(
                DebtItem(id = "debt-a-new", name = "Kartu Kredit A", amount = 1_500_000.0, interestRatePercent = 2.0, minMonthlyPayment = 150_000.0, userId = userAId)
            ),
            assets = listOf(
                AssetItem(id = "asset-a-new", name = "Laptop A", initialValue = 15_000_000.0, purchaseYear = 2024, category = AssetCategory.ELECTRONICS, userId = userAId)
            ),
            budgetLimits = listOf(
                BudgetLimitItem(id = "total_monthly", limitAmount = 2_500_000.0, isCategory = false, userId = userAId)
            ),
            version = 2
        )

        FinanceViewModel.restoreUserDataInTransaction(db, payloadUserA, userAId)

        // Verifikasi User B 100% identik
        val walletsB = db.walletDao().getAllWallets(userBId).first()
        assertEquals(1, walletsB.size)
        assertEquals("Dompet BCA User B", walletsB[0].name)
        assertEquals(5_000_000.0, walletsB[0].balance, 0.001)

        val transactionsB = db.transactionDao().getAllTransactions(userBId).first()
        assertEquals(1, transactionsB.size)
        assertEquals("Gaji User B", transactionsB[0].title)

        val debtsB = db.debtDao().getAllDebts(userBId).first()
        assertEquals(1, debtsB.size)
        assertEquals("KPR User B", debtsB[0].name)

        val assetsB = db.assetDao().getAllAssets(userBId).first()
        assertEquals(1, assetsB.size)
        assertEquals("Mobil User B", assetsB[0].name)

        val budgetB = db.budgetLimitDao().getBudgetLimit(userBId, "total_monthly")
        assertNotNull(budgetB)
        assertEquals(4_000_000.0, budgetB!!.limitAmount, 0.001)
    }

    // TEST 2: Data akun B tidak dapat dibaca akun A setelah restore
    @Test
    fun test02_UserACannotReadUserBDataAfterRestore() = runBlocking {
        val payloadUserA = BackupDataPayload(
            wallets = listOf(
                Wallet(id = 1L, name = "Dompet Mandiri A", balance = 500_000.0, userId = userAId)
            ),
            version = 2
        )
        FinanceViewModel.restoreUserDataInTransaction(db, payloadUserA, userAId)

        val walletsA = db.walletDao().getAllWallets(userAId).first()
        assertEquals(1, walletsA.size)
        assertEquals("Dompet Mandiri A", walletsA[0].name)

        // Pastikan tidak ada satupun data B yang terbaca oleh A
        walletsA.forEach { assertNotEquals(userBId, it.userId) }

        val transactionsA = db.transactionDao().getAllTransactions(userAId).first()
        assertEquals(0, transactionsA.size)

        val debtsA = db.debtDao().getAllDebts(userAId).first()
        assertEquals(0, debtsA.size)

        val assetsA = db.assetDao().getAllAssets(userAId).first()
        assertEquals(0, assetsA.size)
    }

    // TEST 3: Kategori sistem tetap utuh
    @Test
    fun test03_SystemCategoriesRemainIntactAndUnduplicated() = runBlocking {
        val payloadUserA = BackupDataPayload(
            categories = listOf(
                // Upaya memasukkan kategori sistem kanonik yang sudah ada
                CategoryItem(name = "Makanan & Minuman", type = TransactionType.EXPENSE, isSystem = true),
                CategoryItem(name = "Kategori Kustom A", type = TransactionType.EXPENSE, isSystem = false)
            ),
            version = 2
        )

        FinanceViewModel.restoreUserDataInTransaction(db, payloadUserA, userAId)

        val systemCats = db.categoryDao().getSystemCategories().first()
        assertEquals(13, systemCats.size)
        systemCats.forEach {
            assertTrue(it.isSystem)
            assertNull(it.userId)
        }

        val allCatsA = db.categoryDao().getAllCategories(userAId).first()
        // 13 system + 1 custom
        assertEquals(14, allCatsA.size)
        assertTrue(allCatsA.any { it.name == "Kategori Kustom A" && it.userId == userAId })
    }

    // TEST 4: Data legacy userId = NULL tetap utuh
    @Test
    fun test04_LegacyQuarantinedDataUserIdNullRemainsIntact() = runBlocking {
        val payloadUserA = BackupDataPayload(
            wallets = listOf(
                Wallet(id = 1L, name = "Dompet A Baru", userId = userAId)
            ),
            version = 2
        )

        FinanceViewModel.restoreUserDataInTransaction(db, payloadUserA, userAId)

        // Cek baris karantina tetap ada
        val quarantinedWallet = db.walletDao().getWalletById(999L, userAId)
        assertNull("Quarantined wallet tidak boleh dapat diakses oleh query userAId", quarantinedWallet)

        // Langsung cek eksistensi baris legacy di basis data
        assertEquals(3, db.walletDao().getWalletCount()) // User B (1) + Quarantined (1) + User A restored (1)
    }

    // TEST 5: Profil dan hash kata sandi lokal tidak tertimpa
    @Test
    fun test05_LocalUserProfileAndPasswordHashNotOverwritten() = runBlocking {
        val payloadWithMaliciousUser = BackupDataPayload(
            users = listOf(
                User(
                    id = userAId,
                    username = "hacked_user",
                    email = "hacked@attacker.com",
                    passwordHash = "attacker_injected_hash",
                    role = UserRole.SMART_MASTER
                )
            ),
            wallets = listOf(Wallet(id = 1L, name = "A Wallet", userId = userAId)),
            version = 2
        )

        FinanceViewModel.restoreUserDataInTransaction(db, payloadWithMaliciousUser, userAId)

        val userA = db.userDao().getUserById(userAId)
        assertNotNull(userA)
        assertEquals("usera", userA!!.username)
        assertEquals("usera@example.com", userA.email)
        assertEquals("hash_usera_secret_123", userA.passwordHash)
        assertEquals(UserRole.FREE_MEMBER, userA.role)
    }

    // TEST 6: Restore ditolak tanpa sesi valid
    @Test
    fun test06_RestoreRejectedWithoutValidSessionOrNonExistentUser() = runBlocking {
        val payload = BackupDataPayload(version = 2)

        // ID 0L / negatif
        try {
            FinanceViewModel.restoreUserDataInTransaction(db, payload, 0L)
            fail("Harus melempar IllegalArgumentException untuk userId <= 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("tidak valid"))
        }

        // Akun tidak ada di basis data
        try {
            FinanceViewModel.restoreUserDataInTransaction(db, payload, 99999L)
            fail("Harus melempar IllegalStateException untuk akun tidak ditemukan")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("tidak ditemukan"))
        }

        // Melalui GoogleDriveService.restoreNow
        val (success, msg) = GoogleDriveService.restoreNow(context, db, 99999L)
        assertFalse(success)
        assertTrue(msg.contains("tidak ditemukan"))
    }

    // TEST 7: Restore berhasil untuk payload valid yang kepemilikannya dapat dipetakan
    @Test
    fun test07_RestoreSucceedsForValidPayloadWithMappableOwnership() = runBlocking {
        val payload = BackupDataPayload(
            wallets = listOf(
                Wallet(id = 88L, name = "Jago Syariah", balance = 2_500_000.0, userId = 999L) // Payload dari device lama
            ),
            transactions = listOf(
                TransactionItem(
                    id = 111L,
                    title = "Beli Kopi",
                    amount = 45_000.0,
                    type = TransactionType.EXPENSE,
                    category = "Makanan & Minuman",
                    walletId = 88L,
                    walletName = "Jago Syariah",
                    userId = 999L
                )
            ),
            version = 2
        )

        FinanceViewModel.restoreUserDataInTransaction(db, payload, userAId)

        val wallets = db.walletDao().getAllWallets(userAId).first()
        assertEquals(1, wallets.size)
        assertEquals("Jago Syariah", wallets[0].name)
        assertEquals(userAId, wallets[0].userId)

        val txs = db.transactionDao().getAllTransactions(userAId).first()
        assertEquals(1, txs.size)
        assertEquals("Beli Kopi", txs[0].title)
        assertEquals(userAId, txs[0].userId)
        assertEquals(wallets[0].id, txs[0].walletId)
    }

    // TEST 8: Payload rusak atau gagal didekripsi tidak mengubah database
    @Test
    fun test08_CorruptedPayloadOrDecryptionFailureDoesNotModifyDatabase() = runBlocking {
        val initialWalletsA = db.walletDao().getAllWallets(userAId).first()
        assertEquals(1, initialWalletsA.size)
        val initialName = initialWalletsA[0].name

        // Eksekusi restore dengan bad password
        val (success, msg) = GoogleDriveService.restorePortableNow(context, db, "{corrupted}", "wrong_pass", userAId)
        assertFalse(success)

        val walletsAfter = db.walletDao().getAllWallets(userAId).first()
        assertEquals(1, walletsAfter.size)
        assertEquals(initialName, walletsAfter[0].name)
    }

    // TEST 9: Kegagalan di tengah transaksi me-rollback seluruh perubahan
    @Test
    fun test09_MidTransactionFailureRollsBackAllChanges() = runBlocking {
        val initialWalletsA = db.walletDao().getAllWallets(userAId).first()
        assertEquals(1, initialWalletsA.size)
        assertEquals("Dompet Lama A", initialWalletsA[0].name)

        // Payload yang valid di wallet namun corrupt/invalid di transaksi
        val invalidPayload = BackupDataPayload(
            wallets = listOf(Wallet(id = 1L, name = "Dompet Baru A", userId = userAId)),
            transactions = listOf(
                TransactionItem(
                    id = 1L,
                    title = "Transaksi Invalid",
                    amount = Double.NaN, // Pelanggaran nilai numerik
                    type = TransactionType.EXPENSE,
                    category = "Makanan",
                    walletId = 1L,
                    walletName = "Dompet Baru A",
                    userId = userAId
                )
            ),
            version = 2
        )

        try {
            FinanceViewModel.restoreUserDataInTransaction(db, invalidPayload, userAId)
            fail("Harus melempar exception karena amount NaN")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("tidak valid"))
        }

        // Verifikasi Rollback Penuh: Dompet Lama A masih utuh!
        val walletsAfter = db.walletDao().getAllWallets(userAId).first()
        assertEquals(1, walletsAfter.size)
        assertEquals("Dompet Lama A", walletsAfter[0].name)
    }

    // TEST 10: ID wallet yang bertabrakan tidak mengubah relasi akun lain
    @Test
    fun test10_CollidingWalletIdsDoNotAffectOtherAccounts() = runBlocking {
        // User B sudah punya wallet dengan ID = 50L ("Dompet BCA User B")
        val walletBBefore = db.walletDao().getWalletById(50L, userBId)
        assertNotNull(walletBBefore)
        assertEquals("Dompet BCA User B", walletBBefore!!.name)

        // Payload User A membawa wallet dengan ID = 50L yang bertabrakan secara numerik
        val collidingPayload = BackupDataPayload(
            wallets = listOf(
                Wallet(id = 50L, name = "Dompet Tabrakan A", balance = 777.0, userId = userAId)
            ),
            transactions = listOf(
                TransactionItem(
                    id = 99L,
                    title = "Transaksi A",
                    amount = 50.0,
                    type = TransactionType.EXPENSE,
                    category = "Lain-lain",
                    walletId = 50L,
                    walletName = "Dompet Tabrakan A",
                    userId = userAId
                )
            ),
            version = 2
        )

        FinanceViewModel.restoreUserDataInTransaction(db, collidingPayload, userAId)

        // Verifikasi User B: ID 50L tetap milik User B dan tidak berubah!
        val walletBAfter = db.walletDao().getWalletById(50L, userBId)
        assertNotNull(walletBAfter)
        assertEquals("Dompet BCA User B", walletBAfter!!.name)
        assertEquals(5_000_000.0, walletBAfter.balance, 0.001)

        // Verifikasi User A: Mendapatkan auto-generated ID baru (> 50L) yang tidak menimpa User B
        val walletsA = db.walletDao().getAllWallets(userAId).first()
        assertEquals(1, walletsA.size)
        assertEquals("Dompet Tabrakan A", walletsA[0].name)
        assertNotEquals(50L, walletsA[0].id)

        // Transaksi A menunjuk ID baru tersebut
        val txA = db.transactionDao().getAllTransactions(userAId).first()
        assertEquals(1, txA.size)
        assertEquals(walletsA[0].id, txA[0].walletId)
    }

    // TEST 11: Relasi transaksi dan transfer tetap benar setelah ID remapping
    @Test
    fun test11_TransactionAndTransferRelationsRemainCorrectAfterRemapping() = runBlocking {
        val payload = BackupDataPayload(
            wallets = listOf(
                Wallet(id = 1001L, name = "Sumber Transfer", balance = 1_000_000.0, userId = userAId),
                Wallet(id = 1002L, name = "Tujuan Transfer", balance = 500_000.0, userId = userAId)
            ),
            transactions = listOf(
                TransactionItem(
                    id = 1L,
                    title = "Pindah Dana Tabungan",
                    amount = 200_000.0,
                    type = TransactionType.EXPENSE,
                    category = "Lain-lain",
                    walletId = 1001L,
                    walletName = "Sumber Transfer",
                    targetWalletId = 1002L,
                    targetWalletName = "Tujuan Transfer",
                    userId = userAId
                )
            ),
            version = 2
        )

        FinanceViewModel.restoreUserDataInTransaction(db, payload, userAId)

        val wallets = db.walletDao().getAllWallets(userAId).first()
        assertEquals(2, wallets.size)
        val sumber = wallets.first { it.name == "Sumber Transfer" }
        val tujuan = wallets.first { it.name == "Tujuan Transfer" }

        val txs = db.transactionDao().getAllTransactions(userAId).first()
        assertEquals(1, txs.size)
        assertEquals("Pindah Dana Tabungan", txs[0].title)
        assertEquals(sumber.id, txs[0].walletId)
        assertEquals(tujuan.id, txs[0].targetWalletId)
        assertEquals("Sumber Transfer", txs[0].walletName)
        assertEquals("Tujuan Transfer", txs[0].targetWalletName)
    }

    // TEST 12: Backup lama yang tidak aman dipetakan ditolak tanpa perubahan database
    @Test
    fun test12_UnsafeLegacyBackupWithInvalidRelationsRejectedWithoutDatabaseChanges() = runBlocking {
        val initialWalletsA = db.walletDao().getAllWallets(userAId).first()
        assertEquals(1, initialWalletsA.size)

        // Payload di mana transaksi menunjuk walletId yang tidak ada di daftar wallets
        val orphanTxPayload = BackupDataPayload(
            wallets = listOf(Wallet(id = 1L, name = "Dompet A", userId = userAId)),
            transactions = listOf(
                TransactionItem(
                    id = 1L,
                    title = "Transaksi Yatim",
                    amount = 10_000.0,
                    type = TransactionType.EXPENSE,
                    category = "Makanan & Minuman",
                    walletId = 9999L, // Wallet ID tidak ada dalam payload!
                    walletName = "Dompet Hantu",
                    userId = userAId
                )
            ),
            version = 2
        )

        try {
            FinanceViewModel.restoreUserDataInTransaction(db, orphanTxPayload, userAId)
            fail("Harus melempar IllegalArgumentException karena wallet id tidak ada")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("tidak ditemukan dalam data cadangan"))
        }

        // Database tidak berubah
        val walletsAfter = db.walletDao().getAllWallets(userAId).first()
        assertEquals(1, walletsAfter.size)
        assertEquals(initialWalletsA[0].name, walletsAfter[0].name)
    }

    // TEST 13: Restore tidak menduplikasi kategori sistem atau kategori kustom secara tidak sengaja
    @Test
    fun test13_RestoreDoesNotAccidentallyDuplicateSystemOrCustomCategories() = runBlocking {
        val payload = BackupDataPayload(
            categories = listOf(
                CategoryItem(name = "Makanan & Minuman", type = TransactionType.EXPENSE, isSystem = true),
                CategoryItem(name = "makanan & minuman", type = TransactionType.EXPENSE, isSystem = false), // Kasus huruf kecil
                CategoryItem(name = "Kategori Kustom Baru", type = TransactionType.EXPENSE, isSystem = false)
            ),
            version = 2
        )

        FinanceViewModel.restoreUserDataInTransaction(db, payload, userAId)

        // Kategori sistem tetap 13
        val systemCats = db.categoryDao().getSystemCategories().first()
        assertEquals(13, systemCats.size)

        // Kategori kustom hanya bertambah 1 ("Kategori Kustom Baru")
        val customCount = db.categoryDao().getCustomCategoryCount(userAId)
        assertEquals(1, customCount)
    }
}
