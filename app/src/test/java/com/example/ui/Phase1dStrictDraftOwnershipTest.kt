package com.example.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.ExpenseAiParser
import com.example.data.chatbot.parser.LocalFinancialIntentParser
import com.example.data.chatbot.parser.TransactionDraftManager
import com.example.data.chatbot.parser.WalletDraftManager
import com.example.data.local.FinSmartDatabase
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.repository.FinanceRepository
import com.example.data.session.SessionManager
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.EntitlementManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase1dStrictDraftOwnershipTest {

    private lateinit var context: Context
    private lateinit var database: FinSmartDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        SessionManager.initialize(context)
        database = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        setDatabaseSingleton(database)
        repository = FinanceRepository(
            walletDao = database.walletDao(),
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            database = database
        )
        WalletDraftManager.clearAllDrafts()
        TransactionDraftManager.clearAllDrafts()
        SessionManager.resetForTesting()
    }

    @After
    fun tearDown() {
        SessionManager.resetForTesting()
        WalletDraftManager.clearAllDrafts()
        TransactionDraftManager.clearAllDrafts()
        setDatabaseSingleton(null)
        database.close()
        Dispatchers.resetMain()
    }

    private fun setDatabaseSingleton(instance: FinSmartDatabase?) {
        val field = FinSmartDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, instance)
    }

    // ==========================================
    // 1. OWNERSHIP TESTS
    // ==========================================

    @Test
    fun test01_draftOwnerZero_rejectedAtCreation() = runBlocking {
        val cmd = LocalFinancialIntentParser.parse("Create wallet Kas opening balance 10000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = 0L)
        assertNull("Draft dompet dengan owner 0L wajib ditolak saat pembuatan", draft)

        val txCmd = LocalFinancialIntentParser.parse("Beli kopi 20rb", emptyList())
        val txDraft = TransactionDraftManager.createDraft(txCmd, ownerUserId = 0L)
        assertNull("Draft transaksi dengan owner 0L wajib ditolak saat pembuatan", txDraft)
    }

    @Test
    fun test02_draftOwnerNegative_rejectedAtCreation() = runBlocking {
        val cmd = LocalFinancialIntentParser.parse("Create wallet Kas opening balance 10000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = -1L)
        assertNull("Draft dompet dengan owner negatif wajib ditolak saat pembuatan", draft)

        val txCmd = LocalFinancialIntentParser.parse("Beli kopi 20rb", emptyList())
        val txDraft = TransactionDraftManager.createDraft(txCmd, ownerUserId = -99L)
        assertNull("Draft transaksi dengan owner negatif wajib ditolak saat pembuatan", txDraft)
    }

    @Test
    fun test03_userIdZero_cannotConfirmDraft() = runBlocking {
        val user = User(id = 801L, username = "Owner", email = "o@example.com", role = UserRole.SMART_MASTER)
        database.userDao().insertUser(user)
        SessionManager.login(user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Bank Jago opening balance 50000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = user.id)!!

        val resWallet = WalletDraftManager.confirmAndSave(draft.id, repository, userId = 0L, isEntitlementUnlocked = { true })
        assertTrue("Konfirmasi dengan userId 0L harus ditolak", resWallet.isFailure)
        assertTrue(resWallet.exceptionOrNull() is SecurityException)
        assertEquals(0, database.walletDao().getWalletCount(user.id))
    }

    @Test
    fun test04_userIdNegative_cannotConfirmDraft() = runBlocking {
        val user = User(id = 802L, username = "Owner", email = "o2@example.com", role = UserRole.SMART_MASTER)
        database.userDao().insertUser(user)
        SessionManager.login(user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet BCA opening balance 75000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = user.id)!!

        val resWallet = WalletDraftManager.confirmAndSave(draft.id, repository, userId = -10L, isEntitlementUnlocked = { true })
        assertTrue("Konfirmasi dengan userId negatif harus ditolak", resWallet.isFailure)
        assertTrue(resWallet.exceptionOrNull() is SecurityException)
        assertEquals(0, database.walletDao().getWalletCount(user.id))
    }

    @Test
    fun test05_ownerMismatch_rejectedWithSecurityException() = runBlocking {
        val userA = User(id = 803L, username = "A", email = "a@example.com", role = UserRole.SMART_MASTER)
        val userB = User(id = 804L, username = "B", email = "b@example.com", role = UserRole.SMART_MASTER)
        database.userDao().insertUser(userA)
        database.userDao().insertUser(userB)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Permata opening balance 100000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = userA.id)!!

        val res = WalletDraftManager.confirmAndSave(draft.id, repository, userId = userB.id, isEntitlementUnlocked = { true })
        assertTrue("Owner mismatch harus ditolak", res.isFailure)
        assertTrue(res.exceptionOrNull() is SecurityException)
        assertEquals(0, database.walletDao().getWalletCount(userA.id))
        assertEquals(0, database.walletDao().getWalletCount(userB.id))
    }

    @Test
    fun test06_ownerMatching_withValidEntitlement_succeeds() = runBlocking {
        val user = User(id = 805L, username = "Valid", email = "v@example.com", role = UserRole.SMART_MASTER)
        database.userDao().insertUser(user)
        SessionManager.login(user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet BNI opening balance 250000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = user.id)!!

        val res = WalletDraftManager.confirmAndSave(draft.id, repository, userId = user.id, isEntitlementUnlocked = { true })
        assertTrue("Owner valid harus berhasil", res.isSuccess)
        assertEquals(1, database.walletDao().getWalletCount(user.id))
    }

    @Test
    fun test07_draftWithoutSession_cannotBeCreated() = runBlocking {
        SessionManager.resetForTesting()
        // No user logged in -> SessionManager.getActiveUserId() is null
        val cmd = LocalFinancialIntentParser.parse("Create wallet Mandiri opening balance 10000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd)
        assertNull("Draft tanpa sesi aktif wajib menghasilkan null", draft)

        val parsedResult = ExpenseAiParser.parseExpenseMessage("Create wallet Mandiri opening balance 10000", emptyList())
        assertFalse("ParsedResult harus invalid jika sesi null", parsedResult.isValid)
        assertNull("DraftId harus null jika sesi tidak ada", parsedResult.draftId)
        assertNull("WalletDraftId harus null jika sesi tidak ada", parsedResult.walletDraftId)
    }

    @Test
    fun test08_oldDraft_cannotBeConfirmedAfterAccountSwitch() = runBlocking {
        val userA = User(id = 806L, username = "UserA", email = "ua@example.com", role = UserRole.SMART_MASTER)
        val userB = User(id = 807L, username = "UserB", email = "ub@example.com", role = UserRole.SMART_MASTER)
        database.userDao().insertUser(userA)
        database.userDao().insertUser(userB)

        SessionManager.login(userA)
        val cmd = LocalFinancialIntentParser.parse("Create wallet CIMB opening balance 50000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = userA.id)!!

        // Switch to User B
        SessionManager.login(userB)
        assertNull("Draft user A harus dibersihkan dari memori saat login user B", WalletDraftManager.getDraft(draft.id))

        val res = WalletDraftManager.confirmAndSave(draft.id, repository, userId = userB.id, isEntitlementUnlocked = { true })
        assertTrue("Draft lama akun sebelumnya harus gagal dikonfirmasi", res.isFailure)
        assertEquals(0, database.walletDao().getWalletCount(userB.id))
    }

    // ==========================================
    // 2. ENTITLEMENT TESTS
    // ==========================================

    @Test
    fun test09_guestWithoutEntitlement_rejected() = runBlocking {
        val guest = User(id = 808L, username = "Guest", email = "g@example.com", role = UserRole.FREE_MEMBER, isGuest = true)
        database.userDao().insertUser(guest)
        SessionManager.login(guest)

        val cmd = LocalFinancialIntentParser.parse("Create wallet OVO opening balance 15000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = guest.id)!!

        val res = WalletDraftManager.confirmAndSave(
            draft.id,
            repository,
            userId = guest.id,
            isEntitlementUnlocked = { EntitlementManager.evaluate(guest, 0, System.currentTimeMillis()).isPremiumUnlocked }
        )
        assertTrue("Guest tanpa SmartMaster harus ditolak", res.isFailure)
        assertTrue(res.exceptionOrNull() is SecurityException)
        assertEquals(0, database.walletDao().getWalletCount(guest.id))
    }

    @Test
    fun test10_freeMemberWithoutEntitlement_rejected() = runBlocking {
        val free = User(id = 809L, username = "Free", email = "f@example.com", role = UserRole.FREE_MEMBER, isGuest = false)
        database.userDao().insertUser(free)
        SessionManager.login(free)

        val cmd = LocalFinancialIntentParser.parse("Create wallet DANA opening balance 20000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = free.id)!!

        val res = WalletDraftManager.confirmAndSave(
            draft.id,
            repository,
            userId = free.id,
            isEntitlementUnlocked = { EntitlementManager.evaluate(free, 0, System.currentTimeMillis()).isPremiumUnlocked }
        )
        assertTrue("Free member tanpa entitlement harus ditolak", res.isFailure)
        assertTrue(res.exceptionOrNull() is SecurityException)
        assertEquals(0, database.walletDao().getWalletCount(free.id))
    }

    @Test
    fun test11_rewardedSmartMasterActive_succeeds() = runBlocking {
        val now = System.currentTimeMillis()
        val rewarded = User(
            id = 810L,
            username = "Rewarded",
            email = "r@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = now + 1800000L
        )
        database.userDao().insertUser(rewarded)
        SessionManager.login(rewarded)

        val cmd = LocalFinancialIntentParser.parse("Create wallet GoPay opening balance 30000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = rewarded.id)!!

        val res = WalletDraftManager.confirmAndSave(
            draft.id,
            repository,
            userId = rewarded.id,
            isEntitlementUnlocked = { EntitlementManager.evaluate(rewarded, 0, System.currentTimeMillis()).isPremiumUnlocked }
        )
        assertTrue("Rewarded aktif harus berhasil", res.isSuccess)
        assertEquals(1, database.walletDao().getWalletCount(rewarded.id))
    }

    @Test
    fun test12_rewardedSmartMasterExpired_rejected() = runBlocking {
        val now = System.currentTimeMillis()
        val expired = User(
            id = 811L,
            username = "Expired",
            email = "exp@example.com",
            role = UserRole.SMART_MASTER,
            isGuest = false,
            subscriptionExpiresAt = now - 1000L // sudah kedaluwarsa
        )
        database.userDao().insertUser(expired)
        SessionManager.login(expired)

        val cmd = LocalFinancialIntentParser.parse("Create wallet LinkAja opening balance 40000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = expired.id)!!

        val res = WalletDraftManager.confirmAndSave(
            draft.id,
            repository,
            userId = expired.id,
            isEntitlementUnlocked = { EntitlementManager.evaluate(expired, 0, System.currentTimeMillis()).isPremiumUnlocked }
        )
        assertTrue("Rewarded kedaluwarsa harus ditolak", res.isFailure)
        assertTrue(res.exceptionOrNull() is SecurityException)
        assertEquals(0, database.walletDao().getWalletCount(expired.id))
    }

    @Test
    fun test13_paidSmartMasterActive_succeeds() = runBlocking {
        val paid = User(id = 812L, username = "Paid", email = "p@example.com", role = UserRole.SMART_MASTER, isGuest = false)
        database.userDao().insertUser(paid)
        SessionManager.login(paid)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Jenius opening balance 500000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = paid.id)!!

        val res = WalletDraftManager.confirmAndSave(
            draft.id,
            repository,
            userId = paid.id,
            isEntitlementUnlocked = { EntitlementManager.evaluate(paid, 0, System.currentTimeMillis()).isPremiumUnlocked }
        )
        assertTrue("Paid SmartMaster harus berhasil", res.isSuccess)
        assertEquals(1, database.walletDao().getWalletCount(paid.id))
    }

    @Test
    fun test14_callbackException_rejectedFailClosed() = runBlocking {
        val user = User(id = 813L, username = "Err", email = "err@example.com", role = UserRole.SMART_MASTER)
        database.userDao().insertUser(user)
        SessionManager.login(user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Dompet X opening balance 10000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = user.id)!!

        val res = WalletDraftManager.confirmAndSave(
            draft.id,
            repository,
            userId = user.id,
            isEntitlementUnlocked = { throw RuntimeException("Billing service crashed") }
        )
        assertTrue("Callback exception harus fail-closed", res.isFailure)
        assertTrue(res.exceptionOrNull() is SecurityException)
        assertEquals(0, database.walletDao().getWalletCount(user.id))
    }

    @Test
    fun test15_callbackFalse_rejected() = runBlocking {
        val user = User(id = 814L, username = "FalseUser", email = "fu@example.com", role = UserRole.SMART_MASTER)
        database.userDao().insertUser(user)
        SessionManager.login(user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Dompet Y opening balance 10000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = user.id)!!

        val res = WalletDraftManager.confirmAndSave(
            draft.id,
            repository,
            userId = user.id,
            isEntitlementUnlocked = { false }
        )
        assertTrue("Callback false harus ditolak", res.isFailure)
        assertTrue(res.exceptionOrNull() is SecurityException)
        assertEquals(0, database.walletDao().getWalletCount(user.id))
    }

    @Test
    fun test16_accountSwitchingBeforeConfirmation_rejected() = runBlocking {
        val userA = User(id = 815L, username = "SwitchA", email = "sa@example.com", role = UserRole.SMART_MASTER)
        val userB = User(id = 816L, username = "SwitchB", email = "sb@example.com", role = UserRole.SMART_MASTER)
        database.userDao().insertUser(userA)
        database.userDao().insertUser(userB)

        SessionManager.login(userA)
        val cmd = LocalFinancialIntentParser.parse("Create wallet Tabungan Switch opening balance 25000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = userA.id)!!

        // Switch to user B
        SessionManager.login(userB)
        val res = WalletDraftManager.confirmAndSave(draft.id, repository, userId = userB.id, isEntitlementUnlocked = { true })
        assertTrue("Draft milik A tidak boleh dikonfirmasi setelah beralih ke B", res.isFailure)
        assertEquals(0, database.walletDao().getWalletCount(userA.id))
        assertEquals(0, database.walletDao().getWalletCount(userB.id))
    }

    @Test
    fun test17_logoutBeforeConfirmation_rejected() = runBlocking {
        val user = User(id = 817L, username = "LogoutUser", email = "lo@example.com", role = UserRole.SMART_MASTER)
        database.userDao().insertUser(user)
        SessionManager.login(user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Sebelum Logout opening balance 10000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = user.id)!!

        SessionManager.logout()
        val res = WalletDraftManager.confirmAndSave(draft.id, repository, userId = user.id, isEntitlementUnlocked = { true })
        assertTrue("Draft harus terhapus dan ditolak setelah logout", res.isFailure)
        assertEquals(0, database.walletDao().getWalletCount(user.id))
    }

    // ==========================================
    // 3. DATABASE INTEGRITY TESTS
    // ==========================================

    @Test
    fun test18_rejectionsEnsureDatabaseUnchanged() = runBlocking {
        val user = User(id = 818L, username = "DBCheck", email = "db@example.com", role = UserRole.FREE_MEMBER)
        database.userDao().insertUser(user)
        SessionManager.login(user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet DB Test opening balance 90000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = user.id)!!

        val res = WalletDraftManager.confirmAndSave(draft.id, repository, userId = user.id, isEntitlementUnlocked = { false })
        assertTrue(res.isFailure)
        assertEquals("Jumlah dompet di basis data harus tetap 0", 0, database.walletDao().getWalletCount(user.id))
        assertEquals("Jumlah transaksi di basis data harus tetap 0", 0, database.transactionDao().getTransactionCount(user.id))
    }

    @Test
    fun test19_idempotency_secondConfirmationRejected_noDuplicate() = runBlocking {
        val user = User(id = 819L, username = "Idempotent", email = "idemp@example.com", role = UserRole.SMART_MASTER)
        database.userDao().insertUser(user)
        SessionManager.login(user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Sekali opening balance 100000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = user.id)!!

        val res1 = WalletDraftManager.confirmAndSave(draft.id, repository, userId = user.id, isEntitlementUnlocked = { true })
        assertTrue("Konfirmasi pertama berhasil", res1.isSuccess)
        assertEquals(1, database.walletDao().getWalletCount(user.id))

        val res2 = WalletDraftManager.confirmAndSave(draft.id, repository, userId = user.id, isEntitlementUnlocked = { true })
        assertTrue("Konfirmasi kedua harus ditolak idempotency", res2.isFailure)
        assertTrue(res2.exceptionOrNull() is IllegalStateException)
        assertEquals("Jumlah dompet tidak boleh bertambah ganda", 1, database.walletDao().getWalletCount(user.id))
    }

    @Test
    fun test20_crossUserDraftMutationBlocked() = runBlocking {
        val userA = User(id = 820L, username = "CrossA", email = "ca@example.com", role = UserRole.SMART_MASTER)
        val userB = User(id = 821L, username = "CrossB", email = "cb@example.com", role = UserRole.SMART_MASTER)
        database.userDao().insertUser(userA)
        database.userDao().insertUser(userB)

        val wId = database.walletDao().insertWallet(Wallet(name = "Dompet A", type = WalletType.BANK, balance = 100000.0, userId = userA.id, currencyCode = "IDR"))
        val walletA = database.walletDao().getWalletById(wId, userA.id)!!

        SessionManager.login(userA)
        val cmd = LocalFinancialIntentParser.parse("Beli pulsa 50rb pakai Dompet A", listOf(walletA))
        val draft = TransactionDraftManager.createDraft(cmd, ownerUserId = userA.id)!!

        val res = TransactionDraftManager.confirmAndSave(draft.id, repository, userId = userB.id, isEntitlementUnlocked = { true })
        assertTrue("Konfirmasi lintas user harus ditolak", res.isFailure)
        assertTrue(res.exceptionOrNull() is SecurityException)
        assertEquals(0, database.transactionDao().getTransactionCount(userA.id))
        assertEquals(0, database.transactionDao().getTransactionCount(userB.id))
    }

    // ==========================================
    // 4. PRODUCTION PATH TESTS
    // ==========================================

    @Test
    fun test21_productionPath_confirmTransactionDraft_strictAndEntitled() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>() as android.app.Application
        val fvm = FinanceViewModel(app)

        val user = User(id = 822L, username = "ProdUser", email = "prod@example.com", role = UserRole.SMART_MASTER)
        database.userDao().insertUser(user)
        SessionManager.login(user)

        val wId = database.walletDao().insertWallet(Wallet(name = "Kasir", type = WalletType.CASH, balance = 200000.0, userId = user.id, currencyCode = "IDR"))
        val wallet = database.walletDao().getWalletById(wId, user.id)!!

        val cmd = LocalFinancialIntentParser.parse("Makan siang 25000 pakai Kasir", listOf(wallet))
        val draft = TransactionDraftManager.createDraft(cmd, ownerUserId = user.id)!!

        var successCalled = false
        var errorMsg: String? = null

        fvm.confirmTransactionDraft(
            draftId = draft.id,
            isEntitlementUnlocked = { true },
            onSuccess = { successCalled = true },
            onError = { errorMsg = it }
        )

        // Allow coroutine execution
        kotlinx.coroutines.delay(200)
        assertTrue("Jalur produksi konfirmasi transaksi harus sukses jika valid", successCalled)
        assertNull(errorMsg)
    }

    @Test
    fun test22_productionPath_confirmWalletDraft_strictAndEntitled() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>() as android.app.Application
        val fvm = FinanceViewModel(app)

        val user = User(id = 823L, username = "ProdWalletUser", email = "pw@example.com", role = UserRole.SMART_MASTER)
        database.userDao().insertUser(user)
        SessionManager.login(user)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Tabungan Masa Depan opening balance 500000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = user.id)!!

        var successCalled = false
        var errorMsg: String? = null

        fvm.confirmWalletDraft(
            draftId = draft.id,
            isEntitlementUnlocked = { true },
            onSuccess = { successCalled = true },
            onError = { errorMsg = it }
        )

        kotlinx.coroutines.delay(200)
        assertTrue("Jalur produksi konfirmasi dompet harus sukses jika valid", successCalled)
        assertNull(errorMsg)
    }

    @Test
    fun test23_productionPath_unentitledOrUnowned_rejectedFailClosed() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>() as android.app.Application
        val fvm = FinanceViewModel(app)

        val freeUser = User(id = 824L, username = "FreeProd", email = "fp@example.com", role = UserRole.FREE_MEMBER)
        database.userDao().insertUser(freeUser)
        SessionManager.login(freeUser)

        val cmd = LocalFinancialIntentParser.parse("Create wallet Ilegal opening balance 10000", emptyList())
        val draft = WalletDraftManager.createDraft(cmd, ownerUserId = freeUser.id)!!

        var successCalled = false
        var errorMsg: String? = null

        // Free member mencoba konfirmasi melalui ViewModel
        fvm.confirmWalletDraft(
            draftId = draft.id,
            isEntitlementUnlocked = { false },
            onSuccess = { successCalled = true },
            onError = { errorMsg = it }
        )

        kotlinx.coroutines.delay(200)
        assertFalse("Free member tidak boleh berhasil mengonfirmasi draft melalui ViewModel", successCalled)
        assertNotNull("Pesan error harus dikembalikan", errorMsg)
        assertEquals(0, database.walletDao().getWalletCount(freeUser.id))
    }
}
