package com.example

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import com.example.data.model.BackupDataPayload
import com.example.data.model.DebtItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Stage 3B — verifies the centralized reactive Net Worth calculation exposed by
 * [FinanceViewModel.netWorth]:
 *
 *   Net Worth = SUM(wallet.balance for active account mode)
 *             + SUM(asset.currentValue)
 *             - SUM(debt.amount)
 *
 * Every scenario drives real Room data through the real ViewModel StateFlows.
 *
 * Test harness notes:
 * - An isolated in-memory Room database is injected into the FinSmartDatabase singleton
 *   so the ViewModel uses it and the production onCreate demo-data seeding never runs.
 * - Dispatchers.Main is replaced so viewModelScope does not depend on Robolectric's
 *   main looper (which is blocked by runBlocking on the test thread).
 * - All waits are bounded by a timeout, so a regression fails fast instead of hanging.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetWorthCalculationTest {

    private val testUserId = 777L
    private lateinit var app: Application
    private lateinit var db: FinSmartDatabase
    private lateinit var viewModel: FinanceViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        setDatabaseSingleton(db)
        runBlocking {
            db.userDao().insertUser(User(id = testUserId, username = "networth_user", email = "nw@example.com", role = UserRole.FREE_MEMBER))
        }
        com.example.data.session.SessionManager.login(User(id = testUserId, username = "networth_user", email = "nw@example.com", role = UserRole.FREE_MEMBER))
        viewModel = FinanceViewModel(app)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        com.example.data.session.SessionManager.resetForTesting()
        setDatabaseSingleton(null)
        db.close()
        Dispatchers.resetMain()
    }

    // ==========================================
    // Helpers
    // ==========================================

    private fun setDatabaseSingleton(instance: FinSmartDatabase?) {
        val field = FinSmartDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, instance)
    }

    /** Suspends until [flow] emits [expected] (±0.01) or fails after [TIMEOUT_MS]. */
    private suspend fun awaitValue(flow: StateFlow<Double>, expected: Double, label: String): Double {
        return try {
            withTimeout(TIMEOUT_MS) { flow.first { abs(it - expected) < DELTA } }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("$label: expected $expected but last value was ${flow.value}", e)
        }
    }

    private suspend fun <T> awaitList(flow: StateFlow<List<T>>, label: String, predicate: (List<T>) -> Boolean): List<T> {
        return try {
            withTimeout(TIMEOUT_MS) { flow.first(predicate) }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("$label: condition not met, last value was ${flow.value}", e)
        }
    }

    /** Asserts the three breakdown components and the resulting Net Worth. */
    private suspend fun assertNetWorth(wallet: Double, assets: Double, debts: Double, expectedNetWorth: Double) {
        assertEquals(wallet, awaitValue(viewModel.totalBalance, wallet, "totalBalance"), DELTA)
        assertEquals(assets, awaitValue(viewModel.totalAssetsValue, assets, "totalAssetsValue"), DELTA)
        assertEquals(debts, awaitValue(viewModel.totalDebtsAmount, debts, "totalDebtsAmount"), DELTA)
        assertEquals(expectedNetWorth, awaitValue(viewModel.netWorth, expectedNetWorth, "netWorth"), DELTA)
    }

    private suspend fun insertPersonalWallet(id: Long, balance: Double) {
        db.walletDao().insertWallet(
            Wallet(
                id = id,
                name = "Wallet $id",
                type = WalletType.BANK,
                balance = balance,
                accountMode = AccountMode.PERSONAL.name,
                userId = testUserId
            )
        )
    }

    private fun asset(id: String, value: Double, year: Int = 2026, category: AssetCategory = AssetCategory.GOLD_INVESTMENT) =
        AssetItem(id = id, name = "Asset $id", initialValue = value, purchaseYear = year, category = category, userId = testUserId)

    private fun debt(id: String, amount: Double) =
        DebtItem(id = id, name = "Debt $id", amount = amount, interestRatePercent = 12.0, minMonthlyPayment = 100_000.0, userId = testUserId)

    // ==========================================
    // 1. FORMULA SCENARIOS (through FinanceViewModel.netWorth)
    // ==========================================

    @Test
    fun test1_WalletOnly_netWorthEqualsWalletBalance() = runBlocking {
        insertPersonalWallet(1L, 10_000_000.0)

        assertNetWorth(wallet = 10_000_000.0, assets = 0.0, debts = 0.0, expectedNetWorth = 10_000_000.0)
    }

    @Test
    fun test2_WalletPlusAsset_netWorthAddsAssetValue() = runBlocking {
        insertPersonalWallet(1L, 10_000_000.0)
        db.assetDao().insertAsset(asset("a1", 5_000_000.0))

        assertNetWorth(wallet = 10_000_000.0, assets = 5_000_000.0, debts = 0.0, expectedNetWorth = 15_000_000.0)
    }

    @Test
    fun test3_WalletMinusDebt_netWorthSubtractsDebt() = runBlocking {
        insertPersonalWallet(1L, 10_000_000.0)
        db.debtDao().insertDebt(debt("d1", 4_000_000.0))

        assertNetWorth(wallet = 10_000_000.0, assets = 0.0, debts = 4_000_000.0, expectedNetWorth = 6_000_000.0)
    }

    @Test
    fun test4_FullFormula_walletPlusAssetMinusDebt() = runBlocking {
        insertPersonalWallet(1L, 10_000_000.0)
        db.assetDao().insertAsset(asset("a1", 5_000_000.0))
        db.debtDao().insertDebt(debt("d1", 4_000_000.0))

        assertNetWorth(wallet = 10_000_000.0, assets = 5_000_000.0, debts = 4_000_000.0, expectedNetWorth = 11_000_000.0)
    }

    @Test
    fun test5_NetWorthZero_whenWalletPlusAssetEqualsDebt() = runBlocking {
        // Prove the pipeline is live with a non-zero value first, so the final 0.0
        // cannot be confused with the StateFlow initial value.
        insertPersonalWallet(1L, 6_000_000.0)
        db.assetDao().insertAsset(asset("a1", 4_000_000.0))
        assertNetWorth(wallet = 6_000_000.0, assets = 4_000_000.0, debts = 0.0, expectedNetWorth = 10_000_000.0)

        db.debtDao().insertDebt(debt("d1", 10_000_000.0))
        assertNetWorth(wallet = 6_000_000.0, assets = 4_000_000.0, debts = 10_000_000.0, expectedNetWorth = 0.0)
    }

    @Test
    fun test6_NetWorthNegative_whenDebtExceedsWalletPlusAsset() = runBlocking {
        insertPersonalWallet(1L, 3_000_000.0)
        db.assetDao().insertAsset(asset("a1", 2_000_000.0))
        db.debtDao().insertDebt(debt("d1", 8_000_000.0))

        assertNetWorth(wallet = 3_000_000.0, assets = 2_000_000.0, debts = 8_000_000.0, expectedNetWorth = -3_000_000.0)
        assertTrue("Net Worth harus negatif ketika hutang melebihi kas + aset", viewModel.netWorth.value < 0)
    }

    @Test
    fun test7_AssetUsesCurrentValueNotInitialValue() = runBlocking {
        // Electronics bought 2024, 20%/yr depreciation: 10jt -> 8jt -> 6.4jt
        val laptop = asset("laptop", 10_000_000.0, year = 2024, category = AssetCategory.ELECTRONICS)
        assertEquals(6_400_000.0, laptop.currentValue, DELTA)

        insertPersonalWallet(1L, 5_000_000.0)
        db.assetDao().insertAsset(laptop)
        db.debtDao().insertDebt(debt("d1", 2_000_000.0))

        // 5jt + 6.4jt (currentValue) - 2jt = 9.4jt
        assertNetWorth(wallet = 5_000_000.0, assets = 6_400_000.0, debts = 2_000_000.0, expectedNetWorth = 9_400_000.0)

        val wrongWithInitialValue = 5_000_000.0 + laptop.initialValue - 2_000_000.0
        assertNotEquals(wrongWithInitialValue, viewModel.netWorth.value, DELTA)
        assertNotEquals(laptop.initialValue, viewModel.totalAssetsValue.value, DELTA)
    }

    // ==========================================
    // 2. REACTIVITY SCENARIOS
    // ==========================================

    @Test
    fun test8_DebtReactivity_insertUpdateDelete() = runBlocking {
        insertPersonalWallet(1L, 10_000_000.0)
        assertNetWorth(wallet = 10_000_000.0, assets = 0.0, debts = 0.0, expectedNetWorth = 10_000_000.0)

        // Insert -> Net Worth turun
        val debt = debt("debt-react-1", 3_000_000.0)
        viewModel.saveDebt(debt)
        awaitList(viewModel.debts, "debts after insert") { it.size == 1 }
        assertNetWorth(wallet = 10_000_000.0, assets = 0.0, debts = 3_000_000.0, expectedNetWorth = 7_000_000.0)

        // Update amount (sisa pokok berkurang) -> Net Worth naik
        val updated = debt.copy(amount = 2_000_000.0)
        viewModel.saveDebt(updated)
        assertNetWorth(wallet = 10_000_000.0, assets = 0.0, debts = 2_000_000.0, expectedNetWorth = 8_000_000.0)

        // Delete -> Net Worth kembali ke saldo wallet
        viewModel.deleteDebt(updated)
        awaitList(viewModel.debts, "debts after delete") { it.isEmpty() }
        assertNetWorth(wallet = 10_000_000.0, assets = 0.0, debts = 0.0, expectedNetWorth = 10_000_000.0)
    }

    @Test
    fun test9_AssetReactivity_insertUpdateDelete() = runBlocking {
        insertPersonalWallet(1L, 1_000_000.0)
        assertNetWorth(wallet = 1_000_000.0, assets = 0.0, debts = 0.0, expectedNetWorth = 1_000_000.0)

        // Insert (purchased 2026 -> currentValue == initialValue)
        val gold = asset("asset-react-1", 10_000_000.0, year = 2026, category = AssetCategory.GOLD_INVESTMENT)
        viewModel.saveAsset(gold)
        awaitList(viewModel.assets, "assets after insert") { it.size == 1 }
        assertNetWorth(wallet = 1_000_000.0, assets = 10_000_000.0, debts = 0.0, expectedNetWorth = 11_000_000.0)

        // Update purchase year -> currentValue changes via existing model (-5%/yr => +5% appreciation)
        val updated = gold.copy(purchaseYear = 2025)
        assertEquals(10_500_000.0, updated.currentValue, DELTA)
        viewModel.saveAsset(updated)
        assertNetWorth(wallet = 1_000_000.0, assets = 10_500_000.0, debts = 0.0, expectedNetWorth = 11_500_000.0)

        // Delete
        viewModel.deleteAsset(updated)
        awaitList(viewModel.assets, "assets after delete") { it.isEmpty() }
        assertNetWorth(wallet = 1_000_000.0, assets = 0.0, debts = 0.0, expectedNetWorth = 1_000_000.0)
    }

    @Test
    fun test10_WalletReactivity_incomeAndExpenseChangeNetWorth() = runBlocking {
        db.debtDao().insertDebt(debt("d1", 1_000_000.0))

        viewModel.saveWallet(
            name = "BCA Rekening",
            type = WalletType.BANK,
            accountNumber = "123456",
            initialBalance = 5_000_000.0,
            colorHex = "#000000",
            iconName = "bank"
        )
        val wallet = awaitList(viewModel.wallets, "wallets after create") { it.isNotEmpty() }.first()
        assertNetWorth(wallet = 5_000_000.0, assets = 0.0, debts = 1_000_000.0, expectedNetWorth = 4_000_000.0)

        // Income 2jt -> wallet 7jt
        viewModel.saveTransaction(
            title = "Gaji Bonus",
            amount = 2_000_000.0,
            type = TransactionType.INCOME,
            category = "Gaji",
            categoryIcon = "work",
            walletId = wallet.id,
            walletName = wallet.name
        )
        assertNetWorth(wallet = 7_000_000.0, assets = 0.0, debts = 1_000_000.0, expectedNetWorth = 6_000_000.0)

        // Expense 1jt -> wallet 6jt
        viewModel.saveTransaction(
            title = "Belanja Bulanan",
            amount = 1_000_000.0,
            type = TransactionType.EXPENSE,
            category = "Belanja",
            categoryIcon = "shopping_cart",
            walletId = wallet.id,
            walletName = wallet.name
        )
        assertNetWorth(wallet = 6_000_000.0, assets = 0.0, debts = 1_000_000.0, expectedNetWorth = 5_000_000.0)
    }

    @Test
    fun test11_AccountMode_walletComponentFollowsPersonalBusinessMode() = runBlocking {
        insertPersonalWallet(10L, 8_000_000.0)
        db.walletDao().insertWallet(
            Wallet(
                id = 20L,
                name = "Kas Toko",
                type = WalletType.BANK,
                balance = 20_000_000.0,
                accountMode = AccountMode.BUSINESS.name,
                userId = testUserId
            )
        )
        db.assetDao().insertAsset(asset("a1", 2_000_000.0))
        db.debtDao().insertDebt(debt("d1", 5_000_000.0))

        // Personal: 8jt + 2jt - 5jt = 5jt
        assertEquals(AccountMode.PERSONAL, viewModel.accountMode.value)
        assertNetWorth(wallet = 8_000_000.0, assets = 2_000_000.0, debts = 5_000_000.0, expectedNetWorth = 5_000_000.0)

        // Business: 20jt + 2jt - 5jt = 17jt
        viewModel.toggleAccountMode()
        assertEquals(AccountMode.BUSINESS, viewModel.accountMode.value)
        assertNetWorth(wallet = 20_000_000.0, assets = 2_000_000.0, debts = 5_000_000.0, expectedNetWorth = 17_000_000.0)

        // Back to Personal
        viewModel.toggleAccountMode()
        assertEquals(AccountMode.PERSONAL, viewModel.accountMode.value)
        assertNetWorth(wallet = 8_000_000.0, assets = 2_000_000.0, debts = 5_000_000.0, expectedNetWorth = 5_000_000.0)
    }

    @Test
    fun test12_RestoreRegression_netWorthRecomputedFromRestoredRoomData() = runBlocking {
        // Pre-restore state: 1jt - 9jt = -8jt
        insertPersonalWallet(1L, 1_000_000.0)
        db.debtDao().insertDebt(debt("old-debt", 9_000_000.0))
        assertNetWorth(wallet = 1_000_000.0, assets = 0.0, debts = 9_000_000.0, expectedNetWorth = -8_000_000.0)

        val restoredPayload = BackupDataPayload(
            wallets = listOf(
                Wallet(
                    id = 101L,
                    name = "Dompet Pulih",
                    type = WalletType.BANK,
                    balance = 15_000_000.0,
                    accountMode = AccountMode.PERSONAL.name,
                    userId = testUserId
                )
            ),
            assets = listOf(asset("asset-pulih", 10_000_000.0)),
            debts = listOf(debt("debt-pulih", 5_000_000.0)),
            users = listOf(User(id = testUserId, username = "networth_user", email = "nw@example.com", role = UserRole.FREE_MEMBER)),
            version = 2,
            exportedAt = System.currentTimeMillis()
        )

        FinanceViewModel.restoreUserDataInTransaction(db, restoredPayload, testUserId)

        // Post-restore: 15jt + 10jt - 5jt = 20jt (old wallet/debt fully replaced)
        assertNetWorth(wallet = 15_000_000.0, assets = 10_000_000.0, debts = 5_000_000.0, expectedNetWorth = 20_000_000.0)
        val restoredDebts = awaitList(viewModel.debts, "debts after restore") { list -> list.size == 1 }
        assertEquals("debt-pulih", restoredDebts.single().id)
    }

    private companion object {
        const val DELTA = 0.01
        const val TIMEOUT_MS = 10_000L
    }
}
