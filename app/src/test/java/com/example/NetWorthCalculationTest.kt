package com.example

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import com.example.data.model.BackupDataPayload
import com.example.data.model.DebtItem
import com.example.data.model.InstallmentPlanItem
import com.example.data.model.TransactionItem
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
@Config(sdk = [34])
class NetWorthCalculationTest {

    private val testUserId = 777L
    private lateinit var app: Application
    private lateinit var db: FinSmartDatabase
    private lateinit var viewModel: FinanceViewModel

    @Before
    fun setUp() {
        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
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
        com.example.data.local.DatabaseWriteCoordinator.resetForTesting()
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
    private suspend fun awaitValue(flow: StateFlow<Double?>, expected: Double, label: String): Double {
        return try {
            withTimeout(TIMEOUT_MS) { flow.first { it != null && abs(it - expected) < DELTA }!! }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("$label: expected $expected but last value was ${flow.value}", e)
        }
    }

    @JvmName("awaitValueNonNull")
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
                currencyCode = "IDR",
                userId = testUserId
            )
        )
    }

    private fun asset(id: String, value: Double, year: Int = 2026, category: AssetCategory = AssetCategory.GOLD_INVESTMENT) =
        AssetItem(id = id, name = "Asset $id", initialValue = value, purchaseYear = year, category = category, userId = testUserId)

    private fun debt(id: String, amount: Double) =
        DebtItem(id = id, name = "Debt $id", amount = amount, interestRatePercent = 12.0, minMonthlyPayment = 100_000.0, userId = testUserId)

    private fun plan(id: String, principal: Double?, installmentAmount: Double = 500_000.0, totalTenor: Int = 10, paidCount: Int = 0, status: String = "AKTIF") =
        InstallmentPlanItem(
            id = id,
            name = "Plan $id",
            installmentAmount = installmentAmount,
            totalTenor = totalTenor,
            paidCount = paidCount,
            totalRemainingPrincipal = principal,
            status = status,
            startDate = "2026-01-01",
            nextDueDate = "2026-02-15",
            userId = testUserId
        )

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
                currencyCode = "IDR",
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
                    currencyCode = "IDR",
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

    // ==========================================
    // 5. INSTALLMENT PLAN & HYBRID LIABILITIES SCENARIOS
    // ==========================================

    @Test
    fun test13_InstallmentPlanOnly_netWorthSubtractsRemainingPrincipal() = runBlocking {
        insertPersonalWallet(1L, 10_000_000.0)
        // Installment with 4.5jt remaining principal
        viewModel.saveInstallmentPlan(plan("p1", principal = 4_500_000.0))

        awaitList(viewModel.installmentPlans, "installmentPlans after insert") { it.size == 1 }
        // 10jt wallet + 0 assets - 4.5jt installment = 5.5jt net worth
        assertNetWorth(wallet = 10_000_000.0, assets = 0.0, debts = 4_500_000.0, expectedNetWorth = 5_500_000.0)
    }

    @Test
    fun test14_StandardDebtPlusInstallmentPlan_combinedLiabilities() = runBlocking {
        insertPersonalWallet(1L, 15_000_000.0)
        db.assetDao().insertAsset(asset("a1", 5_000_000.0))
        db.debtDao().insertDebt(debt("d1", 3_000_000.0))
        viewModel.saveInstallmentPlan(plan("p1", principal = 2_000_000.0))

        awaitList(viewModel.installmentPlans, "installmentPlans ready") { it.size == 1 }
        // 15jt wallet + 5jt assets - (3jt debt + 2jt plan) = 15jt net worth
        assertNetWorth(wallet = 15_000_000.0, assets = 5_000_000.0, debts = 5_000_000.0, expectedNetWorth = 15_000_000.0)
    }

    @Test
    fun test15_CompletedInstallmentPlan_doesNotCountTowardsLiabilities() = runBlocking {
        insertPersonalWallet(1L, 10_000_000.0)
        // Completed plan (status SELESAI or paidCount == totalTenor)
        val completedPlan = plan("p_done", principal = 2_000_000.0, totalTenor = 5, paidCount = 5, status = "SELESAI")
        viewModel.saveInstallmentPlan(completedPlan)

        awaitList(viewModel.installmentPlans, "installmentPlans ready") { it.size == 1 }
        // Completed plan contributes 0 liability -> debts = 0.0, net worth = 10jt
        assertNetWorth(wallet = 10_000_000.0, assets = 0.0, debts = 0.0, expectedNetWorth = 10_000_000.0)
    }

    @Test
    fun test16_InstallmentPlanPayment_reducesRemainingPrincipalAndWallet() = runBlocking {
        insertPersonalWallet(1L, 5_000_000.0)
        val initialPlan = plan("p_pay", principal = 3_000_000.0, installmentAmount = 500_000.0, totalTenor = 6, paidCount = 0)
        viewModel.saveInstallmentPlan(initialPlan)

        awaitList(viewModel.installmentPlans, "installmentPlans inserted") { it.size == 1 }
        // Initial: 5jt wallet - 3jt plan = 2jt net worth
        assertNetWorth(wallet = 5_000_000.0, assets = 0.0, debts = 3_000_000.0, expectedNetWorth = 2_000_000.0)

        // Confirm payment from wallet 1:
        // Wallet decreases by 500k -> 4.5jt
        // Principal decreases by 500k -> 2.5jt
        // Net worth remains 4.5jt - 2.5jt = 2.0jt!
        viewModel.confirmInstallmentPayment(initialPlan, walletId = 1L)

        awaitList(viewModel.installmentPlans, "installmentPlans paid") { it.first().paidCount == 1 }
        assertNetWorth(wallet = 4_500_000.0, assets = 0.0, debts = 2_500_000.0, expectedNetWorth = 2_000_000.0)
    }

    @Test
    fun test17_NoDoubleCounting_separateDebtsAndInstallments() = runBlocking {
        insertPersonalWallet(1L, 20_000_000.0)
        // User has a standard debt of 5jt and an installment plan of 4jt
        db.debtDao().insertDebt(debt("d_distinct", 5_000_000.0))
        viewModel.saveInstallmentPlan(plan("p_distinct", principal = 4_000_000.0))

        awaitList(viewModel.installmentPlans, "installmentPlans distinct") { it.size == 1 }
        // Each liability is counted once: 5jt + 4jt = 9jt total debt
        assertNetWorth(wallet = 20_000_000.0, assets = 0.0, debts = 9_000_000.0, expectedNetWorth = 11_000_000.0)
    }

    @Test
    fun test18_InstallmentPayment_insufficientBalance_noDeductionAndNoTransaction(): Unit = runBlocking {
        insertPersonalWallet(1L, 200_000.0) // Insufficient: balance 200k < installment 500k
        val initialPlan = plan("p_insufficient", principal = 3_000_000.0, installmentAmount = 500_000.0, totalTenor = 6, paidCount = 0)
        viewModel.saveInstallmentPlan(initialPlan)

        awaitList(viewModel.installmentPlans, "installmentPlans ready") { it.size == 1 }

        viewModel.confirmInstallmentPayment(initialPlan, walletId = 1L)

        // Wallet balance must remain exactly 200k
        assertEquals(200_000.0, awaitValue(viewModel.totalBalance, 200_000.0, "totalBalance"), DELTA)
        val walletInDb = db.walletDao().getWalletById(1L, testUserId)
        assertEquals(200_000.0, walletInDb?.balance ?: 0.0, DELTA)

        // No transactions should be created
        val transactionsInDb = db.transactionDao().getAllTransactions(testUserId).first()
        assertTrue(transactionsInDb.isEmpty())

        // Plan paidCount must remain 0
        val planInDb = db.installmentPlanDao().getInstallmentPlanById("p_insufficient", testUserId)
        assertEquals(0, planInDb?.paidCount)
    }

    @Test
    fun test19_InstallmentPayment_completedPlan_cannotBePaidAgain(): Unit = runBlocking {
        insertPersonalWallet(1L, 5_000_000.0)
        val completedPlan = plan("p_already_done", principal = 0.0, installmentAmount = 500_000.0, totalTenor = 6, paidCount = 6, status = "SELESAI")
        viewModel.saveInstallmentPlan(completedPlan)

        awaitList(viewModel.installmentPlans, "installmentPlans ready") { it.size == 1 }

        viewModel.confirmInstallmentPayment(completedPlan, walletId = 1L)

        // Wallet balance must not be deducted
        assertEquals(5_000_000.0, awaitValue(viewModel.totalBalance, 5_000_000.0, "totalBalance"), DELTA)
        val walletInDb = db.walletDao().getWalletById(1L, testUserId)
        assertEquals(5_000_000.0, walletInDb?.balance ?: 0.0, DELTA)

        // No transaction recorded
        val transactionsInDb = db.transactionDao().getAllTransactions(testUserId).first()
        assertTrue(transactionsInDb.isEmpty())

        // Plan remains paidCount = 6
        val planInDb = db.installmentPlanDao().getInstallmentPlanById("p_already_done", testUserId)
        assertEquals(6, planInDb?.paidCount)
    }

    @Test
    fun test20_InstallmentPayment_crossUserIsolation_cannotPayOtherUserPlan(): Unit = runBlocking {
        val otherUserId = 999L
        db.userDao().insertUser(User(id = otherUserId, username = "other_user", email = "other@example.com", role = UserRole.FREE_MEMBER))
        val otherPlan = InstallmentPlanItem(
            id = "p_other_user",
            name = "Other User Plan",
            installmentAmount = 500_000.0,
            totalTenor = 10,
            paidCount = 0,
            totalRemainingPrincipal = 5_000_000.0,
            status = "AKTIF",
            startDate = "2026-01-01",
            nextDueDate = "2026-02-15",
            userId = otherUserId
        )
        db.installmentPlanDao().insertInstallmentPlan(otherPlan)

        insertPersonalWallet(1L, 5_000_000.0) // Belonging to testUserId (777L)

        // Attempt to pay other user's plan
        viewModel.confirmInstallmentPayment(otherPlan, walletId = 1L)

        // Active user's wallet must remain 5jt
        assertEquals(5_000_000.0, awaitValue(viewModel.totalBalance, 5_000_000.0, "totalBalance"), DELTA)
        val walletInDb = db.walletDao().getWalletById(1L, testUserId)
        assertEquals(5_000_000.0, walletInDb?.balance ?: 0.0, DELTA)

        // No transactions created
        val transactionsInDb = db.transactionDao().getAllTransactions(testUserId).first()
        assertTrue(transactionsInDb.isEmpty())

        // Other user's plan is untouched
        val planInDb = db.installmentPlanDao().getInstallmentPlanById("p_other_user", otherUserId)
        assertEquals(0, planInDb?.paidCount)
    }

    @Test
    fun test21_InstallmentPayment_concurrencyProtection_preventsDoublePayment(): Unit = runBlocking {
        insertPersonalWallet(1L, 5_000_000.0)
        val initialPlan = plan("p_concurrent", principal = 3_000_000.0, installmentAmount = 500_000.0, totalTenor = 6, paidCount = 0)
        viewModel.saveInstallmentPlan(initialPlan)

        awaitList(viewModel.installmentPlans, "installmentPlans ready") { it.size == 1 }

        // Acquire lock via reflection simulating an ongoing in-flight payment
        val lockField = FinanceViewModel::class.java.getDeclaredField("activePaymentPlanIds")
        lockField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val activeSet = lockField.get(viewModel) as java.util.Set<String>
        activeSet.add("p_concurrent")

        // Call confirmInstallmentPayment while lock is held
        viewModel.confirmInstallmentPayment(initialPlan, walletId = 1L)

        // Wallet should NOT be deducted because call was rejected by concurrency guard
        assertEquals(5_000_000.0, awaitValue(viewModel.totalBalance, 5_000_000.0, "totalBalance"), DELTA)
        assertEquals(0, db.transactionDao().getAllTransactions(testUserId).first().size)
        assertEquals(0, db.installmentPlanDao().getInstallmentPlanById("p_concurrent", testUserId)?.paidCount)

        // Release lock
        activeSet.remove("p_concurrent")
        Unit
    }

    @Test
    fun test22_InstallmentPayment_atomicRollbackOnFailure(): Unit = runBlocking {
        insertPersonalWallet(1L, 5_000_000.0)

        // Test Room's atomic transaction rollback semantics directly:
        // If an exception occurs inside database.withTransaction, all DAO mutations must be rolled back.
        var exceptionThrown = false
        try {
            db.withTransaction {
                // Mutate wallet balance
                db.walletDao().adjustBalance(1L, testUserId, -500_000.0)

                // Insert a transaction record
                db.transactionDao().insertTransaction(
                    TransactionItem(
                        id = 0L,
                        title = "Simulated Fail Payment",
                        amount = 500_000.0,
                        type = TransactionType.EXPENSE,
                        category = "Cicilan & Hutang",
                        walletId = 1L,
                        walletName = "Wallet 1",
                        timestamp = System.currentTimeMillis(),
                        userId = testUserId
                    )
                )

                // Simulate unexpected failure before transaction commits
                throw IllegalStateException("Simulated database failure during payment sequence")
            }
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown)

        // Verify that wallet was NOT deducted (rolled back to 5_000_000.0)
        val wallet = db.walletDao().getWalletById(1L, testUserId)
        assertEquals(5_000_000.0, wallet?.balance ?: 0.0, DELTA)

        // Verify that transaction was NOT persisted (rolled back to empty)
        val transactions = db.transactionDao().getAllTransactions(testUserId).first()
        assertTrue(transactions.isEmpty())
    }

    private companion object {
        const val DELTA = 0.01
        const val TIMEOUT_MS = 10_000L
    }
}
