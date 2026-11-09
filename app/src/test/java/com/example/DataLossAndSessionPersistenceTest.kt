package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.CategoryItem
import com.example.data.model.DebtItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.repository.FinanceRepository
import com.example.data.session.SessionManager
import com.example.data.session.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DataLossAndSessionPersistenceTest {

    private lateinit var context: Context
    private lateinit var database: FinSmartDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        SessionManager.resetForTesting()

        // In-memory isolated database with foreign keys enabled
        database = Room.inMemoryDatabaseBuilder(context, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = FinanceRepository(database.walletDao(), database)
    }

    @After
    fun tearDown() {
        database.close()
        SessionManager.resetForTesting()
    }

    // =========================================================================
    // P0 AUTO-LOGIN VERIFICATIONS
    // =========================================================================

    @Test
    fun p0_freshDatabase_hasNoDefaultMasterDevUserUntilExplicitlySeeded() = runBlocking {
        val userCount = database.userDao().getUserCount()
        assertEquals("Database kosong tidak boleh otomatis berisi user", 0, userCount)
        assertNull(database.userDao().getUserById(1L))
    }

    @Test
    fun p0_freshInstall_sessionManagerHasNoActiveUserAndIsUnauthenticated() {
        val prefs = context.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        SessionManager.initialize(context)
        assertEquals(SessionState.Unauthenticated, SessionManager.sessionState.value)
        assertNull(SessionManager.getActiveUserId())
        assertNull(SessionManager.currentUser.value)
    }

    @Test
    fun p0_releaseBuild_restoringMasterDevSessionFromLocalDb_isRejected() = runBlocking {
        val masterDev = User(
            id = 1L,
            username = "Lukas",
            email = "lunachroc07@gmail.com",
            role = UserRole.MASTER_DEV,
            isApproved = true
        )
        database.userDao().insertUser(masterDev)

        val prefs = context.getSharedPreferences("finsmart_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("saved_user_id", 1L).commit()

        SessionManager.initialize(context)
        // Simulate Release mode (isDebug = false)
        val restored = SessionManager.restoreSession(database.userDao(), isDebug = false)

        assertFalse("Sesi Master Dev tidak boleh dipulihkan otomatis pada release build", restored)
        assertEquals(SessionState.Unauthenticated, SessionManager.sessionState.value)
        assertNull(SessionManager.currentUser.value)
    }

    // =========================================================================
    // P0 ZERO DATA LOSS VERIFICATIONS
    // =========================================================================

    @Test
    fun p0_guestDataMigration_reassignsAllWalletsTransactionsAndDebtsToNewUser() = runBlocking {
        // 1. Setup Guest User
        val guest = database.userDao().let { dao ->
            val guestUser = User(
                username = "Tamu DompetNalar",
                email = "guest_test@finsmart.local",
                role = UserRole.FREE_MEMBER,
                isGuest = true
            )
            val id = dao.insertUser(guestUser)
            guestUser.copy(id = id)
        }
        val guestId = guest.id
        assertTrue("Guest ID must be > 0", guestId > 0L)

        // 2. Insert Wallets and Transactions for Guest
        val wallet = Wallet(
            name = "Dompet Tunai Tamu",
            type = WalletType.CASH,
            balance = 500000.0,
            accountMode = AccountMode.PERSONAL.name,
            userId = guestId
        )
        val walletId = repository.insertWallet(wallet)
        assertTrue(walletId > 0L)

        val transaction = TransactionItem(
            title = "Makan Siang Tamu",
            amount = 50000.0,
            type = TransactionType.EXPENSE,
            category = "Makanan & Minuman",
            walletId = walletId,
            walletName = "Dompet Tunai Tamu",
            userId = guestId
        )
        val txId = repository.insertTransaction(guestId, transaction)
        assertTrue(txId > 0L)

        val debt = DebtItem(
            id = "debt_guest_1",
            name = "Pinjaman Teman",
            amount = 200000.0,
            interestRatePercent = 0.0,
            minMonthlyPayment = 50000.0,
            category = "Pinjaman Pribadi",
            dueDate = "Tgl 25",
            userId = guestId
        )
        database.debtDao().insertDebt(debt)

        // 3. User registers or signs in with Google -> New User ID
        val authUser = User(
            username = "Pengguna Asli",
            email = "user.asli@gmail.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false
        )
        val authUserId = database.userDao().insertUser(authUser)
        assertTrue(authUserId > 0L && authUserId != guestId)

        // 4. Execute atomic reassignment (Data Loss Prevention)
        repository.reassignUserData(fromUserId = guestId, toUserId = authUserId)

        // 5. Verify all data now belongs to authUserId
        val authWallets = repository.getAllWallets(authUserId).first()
        assertEquals(1, authWallets.size)
        assertEquals("Dompet Tunai Tamu", authWallets[0].name)
        assertEquals(450000.0, authWallets[0].balance, 0.001) // 500k initial - 50k expense

        val authTransactions = repository.getTransactions(authUserId, AccountMode.PERSONAL).first()
        assertEquals(1, authTransactions.size)
        assertEquals("Makan Siang Tamu", authTransactions[0].title)
        assertEquals(authUserId, authTransactions[0].userId)

        val authDebts = database.debtDao().getAllDebts(authUserId).first()
        assertEquals(1, authDebts.size)
        assertEquals("Pinjaman Teman", authDebts[0].name)

        // 6. Verify guest account row is safely removed and holds no data
        val guestWallets = repository.getAllWallets(guestId).first()
        assertEquals(0, guestWallets.size)
        val guestTransactions = repository.getTransactions(guestId, AccountMode.PERSONAL).first()
        assertEquals(0, guestTransactions.size)
        assertNull("Guest user should be cleaned up after migration", database.userDao().getUserById(guestId))
    }

    @Test
    fun p0_accountIsolation_userADataIsStrictlyIsolatedFromUserB() = runBlocking {
        // User A
        val userA = database.userDao().let {
            val u = User(username = "User A", email = "usera@example.com", role = UserRole.FREE_MEMBER)
            u.copy(id = it.insertUser(u))
        }

        // User B
        val userB = database.userDao().let {
            val u = User(username = "User B", email = "userb@example.com", role = UserRole.FREE_MEMBER)
            u.copy(id = it.insertUser(u))
        }

        // User A records
        val walletA = Wallet(name = "Wallet A", type = WalletType.BANK, balance = 1000000.0, userId = userA.id)
        val walletAId = repository.insertWallet(walletA)
        repository.insertTransaction(userA.id, TransactionItem(
            title = "Tx User A", amount = 100000.0, type = TransactionType.EXPENSE,
            category = "Umum", walletId = walletAId, walletName = "Wallet A", userId = userA.id
        ))

        // User B records
        val walletB = Wallet(name = "Wallet B", type = WalletType.CASH, balance = 200000.0, userId = userB.id)
        val walletBId = repository.insertWallet(walletB)
        repository.insertTransaction(userB.id, TransactionItem(
            title = "Tx User B", amount = 25000.0, type = TransactionType.EXPENSE,
            category = "Umum", walletId = walletBId, walletName = "Wallet B", userId = userB.id
        ))

        // Assert Isolation for User A
        val listA = repository.getTransactions(userA.id, AccountMode.PERSONAL).first()
        assertEquals(1, listA.size)
        assertEquals("Tx User A", listA[0].title)

        // Assert Isolation for User B
        val listB = repository.getTransactions(userB.id, AccountMode.PERSONAL).first()
        assertEquals(1, listB.size)
        assertEquals("Tx User B", listB[0].title)
    }

    @Test
    fun p0_deleteAccount_doesNotAffectOtherUsers() = runBlocking {
        // User 1
        val user1 = database.userDao().let {
            val u = User(username = "User 1", email = "user1@example.com", role = UserRole.FREE_MEMBER)
            u.copy(id = it.insertUser(u))
        }
        val wallet1 = repository.insertWallet(Wallet(name = "W1", type = WalletType.CASH, balance = 100.0, userId = user1.id))
        repository.insertTransaction(user1.id, TransactionItem(title = "T1", amount = 10.0, type = TransactionType.EXPENSE, category = "Umum", walletId = wallet1, walletName = "W1", userId = user1.id))

        // User 2
        val user2 = database.userDao().let {
            val u = User(username = "User 2", email = "user2@example.com", role = UserRole.FREE_MEMBER)
            u.copy(id = it.insertUser(u))
        }
        val wallet2 = repository.insertWallet(Wallet(name = "W2", type = WalletType.CASH, balance = 200.0, userId = user2.id))
        repository.insertTransaction(user2.id, TransactionItem(title = "T2", amount = 20.0, type = TransactionType.EXPENSE, category = "Umum", walletId = wallet2, walletName = "W2", userId = user2.id))

        // Delete User 1
        FinSmartDatabase.deleteUserAccountInTransaction(database, user1)

        // User 1 data gone
        assertEquals(0, repository.getAllWallets(user1.id).first().size)
        assertEquals(0, repository.getTransactions(user1.id, AccountMode.PERSONAL).first().size)
        assertNull(database.userDao().getUserById(user1.id))

        // User 2 data completely intact!
        assertEquals(1, repository.getAllWallets(user2.id).first().size)
        assertEquals(1, repository.getTransactions(user2.id, AccountMode.PERSONAL).first().size)
        assertNotNull(database.userDao().getUserById(user2.id))
    }

    @Test
    fun p0_budgetConflictResolution_doesNotViolateUniqueConstraint() = runBlocking {
        // 1. Setup Guest with a budget limit for "total_monthly"
        val guest = database.userDao().let {
            val u = User(username = "Guest Budget", email = "guest_b@test.local", role = UserRole.FREE_MEMBER, isGuest = true)
            u.copy(id = it.insertUser(u))
        }
        database.budgetLimitDao().insertBudgetLimit(
            com.example.data.model.BudgetLimitItem(id = "total_monthly", limitAmount = 2000000.0, isCategory = false, userId = guest.id)
        )

        // 2. Setup Registered user who ALREADY has a budget limit for "total_monthly"
        val authUser = database.userDao().let {
            val u = User(username = "Auth Budget", email = "auth_b@test.local", role = UserRole.FREE_MEMBER, isGuest = false)
            u.copy(id = it.insertUser(u))
        }
        database.budgetLimitDao().insertBudgetLimit(
            com.example.data.model.BudgetLimitItem(id = "total_monthly", limitAmount = 5000000.0, isCategory = false, userId = authUser.id)
        )

        // 3. Migrate guest -> auth user. Must NOT throw SQLiteConstraintException!
        repository.reassignUserData(fromUserId = guest.id, toUserId = authUser.id)

        // 4. Auth user's budget remains authoritative (5,000,000)
        val authBudgets = database.budgetLimitDao().getAllBudgetLimits(authUser.id).first()
        assertEquals(1, authBudgets.size)
        assertEquals(5000000.0, authBudgets[0].limitAmount, 0.001)
    }

    @Test
    fun p0_nonGuestAccount_isNeverReassigned() = runBlocking {
        // If fromUserId is NOT a guest, reassignUserData must abort immediately
        val user1 = database.userDao().let {
            val u = User(username = "Real User 1", email = "real1@example.com", role = UserRole.FREE_MEMBER, isGuest = false)
            u.copy(id = it.insertUser(u))
        }
        val user2 = database.userDao().let {
            val u = User(username = "Real User 2", email = "real2@example.com", role = UserRole.FREE_MEMBER, isGuest = false)
            u.copy(id = it.insertUser(u))
        }

        val w1 = repository.insertWallet(Wallet(name = "User 1 Wallet", type = WalletType.CASH, balance = 1000.0, userId = user1.id))

        // Attempt reassignment from non-guest user1 to user2
        repository.reassignUserData(fromUserId = user1.id, toUserId = user2.id)

        // User 1 wallet must remain with user 1!
        val u1Wallets = repository.getAllWallets(user1.id).first()
        assertEquals(1, u1Wallets.size)
        assertEquals("User 1 Wallet", u1Wallets[0].name)
        val u2Wallets = repository.getAllWallets(user2.id).first()
        assertEquals(0, u2Wallets.size)
    }

    @Test
    fun p0_systemCategories_areNeverReassigned() = runBlocking {
        // Seed canonical system category (isSystem = 1, userId = null)
        val sysCat = CategoryItem(name = "Makanan & Minuman", type = TransactionType.EXPENSE, isSystem = true, userId = null)
        val sysCatId = database.categoryDao().insertCategory(sysCat)

        val guest = database.userDao().let {
            val u = User(username = "Guest Cat", email = "guest_c@test.local", role = UserRole.FREE_MEMBER, isGuest = true)
            u.copy(id = it.insertUser(u))
        }
        val customCat = CategoryItem(name = "Kopi Favorit", type = TransactionType.EXPENSE, isSystem = false, userId = guest.id)
        val customCatId = database.categoryDao().insertCategory(customCat)

        val authUser = database.userDao().let {
            val u = User(username = "Auth Cat", email = "auth_c@test.local", role = UserRole.FREE_MEMBER, isGuest = false)
            u.copy(id = it.insertUser(u))
        }

        repository.reassignUserData(fromUserId = guest.id, toUserId = authUser.id)

        // System category still belongs to system (isSystem = true, userId = null)
        val sysLoaded = database.categoryDao().getCategoryById(sysCatId, authUser.id)
        assertNotNull(sysLoaded)
        assertTrue(sysLoaded!!.isSystem)
        assertNull(sysLoaded.userId)

        // Custom category reassigned to authUser
        val customLoaded = database.categoryDao().getCategoryById(customCatId, authUser.id)
        assertNotNull(customLoaded)
        assertEquals(authUser.id, customLoaded!!.userId)
    }
}
