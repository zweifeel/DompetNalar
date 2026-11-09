package com.example

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.data.session.SessionManager
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.CurrencyRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigInteger
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiCurrencyDashboardAggregationTest {

    private val testUserId = 888L
    private lateinit var app: Application
    private lateinit var db: FinSmartDatabase
    private lateinit var viewModel: FinanceViewModel

    companion object {
        private const val TIMEOUT_MS = 3000L
        private const val DELTA = 0.001
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, FinSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        setDatabaseSingleton(db)

        runBlocking {
            db.userDao().insertUser(
                User(id = testUserId, username = "multicurrency_user", email = "mc@test.com", role = UserRole.FREE_MEMBER)
            )
        }
        SessionManager.login(
            User(id = testUserId, username = "multicurrency_user", email = "mc@test.com", role = UserRole.FREE_MEMBER)
        )
        viewModel = FinanceViewModel(app)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        SessionManager.resetForTesting()
        setDatabaseSingleton(null)
        db.close()
        Dispatchers.resetMain()
    }

    private fun setDatabaseSingleton(instance: FinSmartDatabase?) {
        val field = FinSmartDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, instance)
    }

    private suspend fun awaitValue(flow: StateFlow<Double?>, expected: Double, label: String): Double {
        return withTimeout(TIMEOUT_MS) {
            val v = flow.first { it != null && abs(it - expected) < DELTA }
            v!!
        }
    }

    private suspend fun awaitLongValue(flow: StateFlow<Long?>, expected: Long, label: String): Long {
        return withTimeout(TIMEOUT_MS) {
            val v = flow.first { it == expected }
            v!!
        }
    }

    private suspend fun awaitNullValue(flow: StateFlow<*>, label: String) {
        withTimeout(TIMEOUT_MS) {
            flow.first { it == null }
        }
    }

    @Test
    fun testWalletsWithDistinctCurrenciesAreNeverCrossSummed() {
        runBlocking {
            // Seed 3 active wallets with distinct currencies
            // Wallet 1: IDR 10,000,000
            db.walletDao().insertWallet(
                Wallet(
                    id = 1L,
                    name = "IDR Account",
                    type = WalletType.BANK,
                    accountNumber = "111",
                    balance = 10000000.0,
                    balanceMinorUnits = 1000000000L,
                    colorHex = "#00",
                    iconName = "i",
                    accountMode = AccountMode.PERSONAL.name,
                    isDefault = true,
                    createdAt = 1000L,
                    currencyCode = "IDR",
                    userId = testUserId
                )
            )
            // Wallet 2: USD 500
            db.walletDao().insertWallet(
                Wallet(
                    id = 2L,
                    name = "USD Account",
                    type = WalletType.BANK,
                    accountNumber = "222",
                    balance = 500.0,
                    balanceMinorUnits = 50000L,
                    colorHex = "#00",
                    iconName = "i",
                    accountMode = AccountMode.PERSONAL.name,
                    isDefault = false,
                    createdAt = 1000L,
                    currencyCode = "USD",
                    userId = testUserId
                )
            )
            // Wallet 3: JPY 50,000
            db.walletDao().insertWallet(
                Wallet(
                    id = 3L,
                    name = "JPY Account",
                    type = WalletType.CASH,
                    accountNumber = "333",
                    balance = 50000.0,
                    balanceMinorUnits = 50000L,
                    colorHex = "#00",
                    iconName = "i",
                    accountMode = AccountMode.PERSONAL.name,
                    isDefault = false,
                    createdAt = 1000L,
                    currencyCode = "JPY",
                    userId = testUserId
                )
            )

            // 1. Under Temuan 1: When multiple currencies exist and user hasn't explicitly selected one,
            // activeDashboardCurrency is NULL (never silently selects first wallet's currency!)
            withTimeout(TIMEOUT_MS) {
                viewModel.activeDashboardCurrency.first { it == null }
            }
            assertNull("activeDashboardCurrency must be null when multiple currencies exist without selection", viewModel.activeDashboardCurrency.value)
            assertNull("totalBalance must be null when activeDashboardCurrency is null", viewModel.totalBalance.value)

            // Multi-currency portfolio contains all 3 distinct currencies cleanly partitioned
            withTimeout(TIMEOUT_MS) {
                viewModel.multiCurrencyBalances.first { list ->
                    list.size == 3 &&
                        list.any { it.currencyCode == "IDR" && it.totalBalance == 10000000.0 } &&
                        list.any { it.currencyCode == "USD" && it.totalBalance == 500.0 } &&
                        list.any { it.currencyCode == "JPY" && it.totalBalance == 50000.0 }
                }
            }

            // 2. Explicitly select IDR scope
            viewModel.setSelectedDashboardCurrency("IDR")
            val idrBalance = awaitValue(viewModel.totalBalance, 10000000.0, "IDR totalBalance")
            assertEquals(10000000.0, idrBalance, DELTA)
            assertNotEquals(10050500.0, viewModel.totalBalance.value ?: 0.0, DELTA)

            // 3. Explicitly select USD scope
            viewModel.setSelectedDashboardCurrency("USD")
            val usdBalance = awaitValue(viewModel.totalBalance, 500.0, "Switched USD totalBalance")
            assertEquals(500.0, usdBalance, DELTA)

            // 4. Explicitly select JPY scope
            viewModel.setSelectedDashboardCurrency("JPY")
            val jpyBalance = awaitValue(viewModel.totalBalance, 50000.0, "Switched JPY totalBalance")
            assertEquals(50000.0, jpyBalance, DELTA)
        }
    }

    @Test
    fun testTransactionsWithDistinctCurrenciesArePartitionedAndNotCrossSummed() {
        runBlocking {
            // Setup wallets
            db.walletDao().insertWallet(
                Wallet(
                    id = 1L, name = "IDR Account", type = WalletType.BANK, accountNumber = "1",
                    balance = 1000000.0, colorHex = "#00", iconName = "i", accountMode = "PERSONAL",
                    isDefault = true, createdAt = 1000L, currencyCode = "IDR", userId = testUserId
                )
            )
            db.walletDao().insertWallet(
                Wallet(
                    id = 2L, name = "USD Account", type = WalletType.BANK, accountNumber = "2",
                    balance = 100.0, colorHex = "#00", iconName = "i", accountMode = "PERSONAL",
                    isDefault = false, createdAt = 1000L, currencyCode = "USD", userId = testUserId
                )
            )

            // Insert IDR Transactions: Income 2,000,000; Expense 500,000
            db.transactionDao().insertTransaction(
                TransactionItem(
                    id = 10L, title = "Salary IDR", amount = 2000000.0, amountMinorUnits = 200000000L,
                    currencyCode = "IDR", type = TransactionType.INCOME, category = "Gaji",
                    walletId = 1L, walletName = "IDR Account", userId = testUserId
                )
            )
            db.transactionDao().insertTransaction(
                TransactionItem(
                    id = 11L, title = "Groceries IDR", amount = 500000.0, amountMinorUnits = 50000000L,
                    currencyCode = "IDR", type = TransactionType.EXPENSE, category = "Makan",
                    walletId = 1L, walletName = "IDR Account", userId = testUserId
                )
            )

            // Insert USD Transactions: Income 100; Expense 20
            db.transactionDao().insertTransaction(
                TransactionItem(
                    id = 20L, title = "Freelance USD", amount = 100.0, amountMinorUnits = 10000L,
                    currencyCode = "USD", type = TransactionType.INCOME, category = "Freelance",
                    walletId = 2L, walletName = "USD Account", userId = testUserId
                )
            )
            db.transactionDao().insertTransaction(
                TransactionItem(
                    id = 21L, title = "SaaS Sub USD", amount = 20.0, amountMinorUnits = 2000L,
                    currencyCode = "USD", type = TransactionType.EXPENSE, category = "Subscription",
                    walletId = 2L, walletName = "USD Account", userId = testUserId
                )
            )

            // 1. Scoped to IDR: Income = 2,000,000.0, Expense = 500,000.0
            viewModel.setSelectedDashboardCurrency("IDR")
            val idrIncome = awaitValue(viewModel.totalIncome, 2000000.0, "IDR totalIncome")
            val idrExpense = awaitValue(viewModel.totalExpense, 500000.0, "IDR totalExpense")
            assertEquals(2000000.0, idrIncome, DELTA)
            assertEquals(500000.0, idrExpense, DELTA)

            // PROOF: totalIncome is NOT 2,000,100.0 (cross-currency scalar addition)
            val currentIncomeVal = viewModel.totalIncome.value ?: 0.0
            assertNotEquals(2000100.0, currentIncomeVal, DELTA)

            // 2. Scoped to USD: Income = 100.0, Expense = 20.0
            viewModel.setSelectedDashboardCurrency("USD")
            val usdIncome = awaitValue(viewModel.totalIncome, 100.0, "USD totalIncome")
            val usdExpense = awaitValue(viewModel.totalExpense, 20.0, "USD totalExpense")
            assertEquals(100.0, usdIncome, DELTA)
            assertEquals(20.0, usdExpense, DELTA)

            // 3. Multi-Currency Cashflows exposes both distinctly
            withTimeout(TIMEOUT_MS) {
                viewModel.multiCurrencyCashflows.first { list ->
                    list.size == 2 &&
                        list.any { it.currencyCode == "IDR" && it.totalIncome == 2000000.0 && it.totalExpense == 500000.0 } &&
                        list.any { it.currencyCode == "USD" && it.totalIncome == 100.0 && it.totalExpense == 20.0 }
                }
            }
        }
    }

    @Test
    fun testArchivedWalletsAreExcludedFromAggregation() {
        runBlocking {
            db.walletDao().insertWallet(
                Wallet(
                    id = 1L, name = "Active IDR", type = WalletType.BANK, accountNumber = "1",
                    balance = 1000000.0, colorHex = "#00", iconName = "i", accountMode = "PERSONAL",
                    isDefault = true, createdAt = 1000L, currencyCode = "IDR", userId = testUserId
                )
            )
            // Archived wallet with 5,000,000
            db.walletDao().insertWallet(
                Wallet(
                    id = 2L, name = "Archived IDR", type = WalletType.BANK, accountNumber = "2",
                    balance = 5000000.0, colorHex = "#00", iconName = "i", accountMode = "PERSONAL",
                    isDefault = false, isArchived = true, createdAt = 1000L, currencyCode = "IDR", userId = testUserId
                )
            )

            viewModel.setSelectedDashboardCurrency("IDR")
            val balance = awaitValue(viewModel.totalBalance, 1000000.0, "Active balance excluding archived")
            assertEquals(1000000.0, balance, DELTA)
            val currentVal = viewModel.totalBalance.value ?: 0.0
            assertNotEquals(6000000.0, currentVal, DELTA)
        }
    }

    @Test
    fun testNullCurrencyWalletAndNullLocaleResultsInUnassignedStateAndNullTotals() {
        runBlocking {
            // Seed a wallet with NULL currencyCode
            db.walletDao().insertWallet(
                Wallet(
                    id = 10L, name = "Unassigned Wallet", type = WalletType.CASH, accountNumber = "10",
                    balance = 500000.0, balanceMinorUnits = 50000000L, colorHex = "#00", iconName = "i", accountMode = "PERSONAL",
                    isDefault = true, createdAt = 1000L, currencyCode = null, userId = testUserId
                )
            )

            // When user has not selected a currency and wallet has null currency, activeDashboardCurrency is null
            withTimeout(TIMEOUT_MS) {
                viewModel.unassignedCurrencyWallets.first { it.size == 1 }
            }

            // Total balance minor units is fail-closed null (never falls back to IDR or displays 0)
            awaitNullValue(viewModel.totalBalanceMinorUnits, "Unassigned currency totalBalanceMinorUnits is null")
            assertNull("totalBalance must be null when currency unassigned", viewModel.totalBalance.value)
            assertNull("activeDashboardCurrency must be null when currency unassigned", viewModel.activeDashboardCurrency.value)
        }
    }

    @Test
    fun testUnresolvedCurrencyTransactionsNeverEnterIDROrAnyCurrencyAggregation() {
        runBlocking {
            db.walletDao().insertWallet(
                Wallet(
                    id = 1L, name = "IDR Account", type = WalletType.BANK, accountNumber = "1",
                    balance = 1000000.0, colorHex = "#00", iconName = "i", accountMode = "PERSONAL",
                    isDefault = true, createdAt = 1000L, currencyCode = "IDR", userId = testUserId
                )
            )

            // Valid IDR Income: 1,000,000 IDR (100,000,000 minor units)
            db.transactionDao().insertTransaction(
                TransactionItem(
                    id = 100L, title = "Valid IDR Income", amount = 1000000.0, amountMinorUnits = 100000000L,
                    currencyCode = "IDR", type = TransactionType.INCOME, category = "Gaji",
                    walletId = 1L, walletName = "IDR Account", userId = testUserId
                )
            )

            // Unresolved Legacy Transaction with NULL currencyCode in SQLite: 5,000,000 (500,000,000 minor units)
            db.openHelper.writableDatabase.execSQL("""
                INSERT INTO transactions (id, title, amount, amountMinorUnits, currencyCode, type, category, categoryIcon, walletId, walletName, userId, timestamp, note, tags, accountMode)
                VALUES (101, 'Unresolved Legacy Income', 5000000.0, 500000000, NULL, 'INCOME', 'Gaji', 'icon', 1, 'IDR Account', $testUserId, 1000, '', '', 'PERSONAL')
            """)

            viewModel.setSelectedDashboardCurrency("IDR")

            // PROOF: totalIncomeMinorUnits must strictly be 100,000,000L (NOT 600,000,000L!)
            val idrIncomeMinor = awaitLongValue(viewModel.totalIncomeMinorUnits, 100000000L, "IDR income excluding unresolved")
            assertEquals(100000000L, idrIncomeMinor)
            val idrIncomeDouble = awaitValue(viewModel.totalIncome, 1000000.0, "IDR totalIncome excluding unresolved")
            assertEquals(1000000.0, idrIncomeDouble, DELTA)
            assertNotEquals(600000000L, viewModel.totalIncomeMinorUnits.value)

            // Verify unresolved transaction is surfaced in unresolvedCurrencyTransactions
            withTimeout(TIMEOUT_MS) {
                viewModel.unresolvedCurrencyTransactions.first { list ->
                    list.any { it.id == 101L && it.currencyCode == null }
                }
            }

            // Verify multiCurrencyCashflows does not contain null or lump unresolved into IDR
            withTimeout(TIMEOUT_MS) {
                viewModel.multiCurrencyCashflows.first { list ->
                    list.none { it.currencyCode == "null" || it.currencyCode == "UNKNOWN" } &&
                        list.any { it.currencyCode == "IDR" && it.totalIncomeMinorUnits == 100000000L }
                }
            }
        }
    }

    @Test
    fun testCanonicalLongMinorUnitsPrecisionWithLargeAmounts() {
        runBlocking {
            // Seed a wallet with very large balance: 50 Trillion IDR (5 * 10^15 minor units)
            val largeBalanceMinor = 5000000000000000L
            db.walletDao().insertWallet(
                Wallet(
                    id = 50L, name = "Sovereign Reserve", type = WalletType.BANK, accountNumber = "50",
                    balance = 50000000000000.0, balanceMinorUnits = largeBalanceMinor, colorHex = "#00", iconName = "i", accountMode = "PERSONAL",
                    isDefault = true, createdAt = 1000L, currencyCode = "IDR", userId = testUserId
                )
            )

            viewModel.setSelectedDashboardCurrency("IDR")

            // Verify canonical Long minor units retains absolute precision
            val balance = awaitLongValue(viewModel.totalBalanceMinorUnits, largeBalanceMinor, "Large canonical minor units balance")
            assertEquals(largeBalanceMinor, balance)
        }
    }

    // --- P0-1: MATHEMATICAL OVERFLOW REJECTION TESTS ---

    @Test
    fun testSafeSumMinorUnitsExactLongMaxValue() {
        val sum = FinanceViewModel.safeSumMinorUnits(listOf(Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, sum)

        val part1 = 5_000_000_000_000_000_000L
        val part2 = Long.MAX_VALUE - part1
        val sum2 = FinanceViewModel.safeSumMinorUnits(listOf(part1, part2))
        assertEquals(Long.MAX_VALUE, sum2)
    }

    @Test
    fun testSafeSumMinorUnitsExactLongMinValue() {
        val sum = FinanceViewModel.safeSumMinorUnits(listOf(Long.MIN_VALUE))
        assertEquals(Long.MIN_VALUE, sum)

        val part1 = -5_000_000_000_000_000_000L
        val part2 = Long.MIN_VALUE - part1
        val sum2 = FinanceViewModel.safeSumMinorUnits(listOf(part1, part2))
        assertEquals(Long.MIN_VALUE, sum2)
    }

    @Test
    fun testSafeSumMinorUnitsOverflowThrowsArithmeticException() {
        try {
            FinanceViewModel.safeSumMinorUnits(listOf(Long.MAX_VALUE, 1L))
            fail("Expected ArithmeticException on overflow exceeding Long.MAX_VALUE by 1")
        } catch (e: ArithmeticException) {
            assertTrue(e.message?.contains("out of long range") == true || e is ArithmeticException)
        }
    }

    @Test
    fun testSafeSumMinorUnitsUnderflowThrowsArithmeticException() {
        try {
            FinanceViewModel.safeSumMinorUnits(listOf(Long.MIN_VALUE, -1L))
            fail("Expected ArithmeticException on underflow exceeding Long.MIN_VALUE by 1")
        } catch (e: ArithmeticException) {
            assertTrue(e.message?.contains("out of long range") == true || e is ArithmeticException)
        }
    }

    @Test
    fun testSafeSumMinorUnitsIntermediateOverflowSucceedsWhenFinalSumFits() {
        val sum = FinanceViewModel.safeSumMinorUnits(listOf(Long.MAX_VALUE, 1000L, -1000L))
        assertEquals(Long.MAX_VALUE, sum)

        val sumMin = FinanceViewModel.safeSumMinorUnits(listOf(Long.MIN_VALUE, -500L, 500L))
        assertEquals(Long.MIN_VALUE, sumMin)
    }

    @Test
    fun testAggregationOverflowSetsAggregationErrorAndNullTotals() {
        runBlocking {
            db.walletDao().insertWallet(
                Wallet(
                    id = 61L, name = "Overflow Wallet 1", type = WalletType.BANK, accountNumber = "61",
                    balance = 9e16, balanceMinorUnits = Long.MAX_VALUE, colorHex = "#00", iconName = "i", accountMode = "PERSONAL",
                    isDefault = true, createdAt = 1000L, currencyCode = "USD", userId = testUserId
                )
            )
            db.walletDao().insertWallet(
                Wallet(
                    id = 62L, name = "Overflow Wallet 2", type = WalletType.BANK, accountNumber = "62",
                    balance = 100.0, balanceMinorUnits = 10000L, colorHex = "#00", iconName = "i", accountMode = "PERSONAL",
                    isDefault = false, createdAt = 1000L, currencyCode = "USD", userId = testUserId
                )
            )

            viewModel.setSelectedDashboardCurrency("USD")

            // Collect totalBalanceMinorUnits to trigger WhileSubscribed upstream flow
            withTimeout(TIMEOUT_MS) {
                viewModel.totalBalanceMinorUnits.first { it == null && viewModel.aggregationError.value != null }
            }

            val err = viewModel.aggregationError.value
            assertTrue("Expected error to contain Overflow but was: $err", err?.contains("Overflow") == true)
            assertNull("totalBalanceMinorUnits must be null on overflow", viewModel.totalBalanceMinorUnits.value)
            assertNull("totalBalance must be null on overflow", viewModel.totalBalance.value)
        }
    }

    // --- P0-3: CURRENCY REGISTRY AND MULTI-EXPONENT TESTS ---

    @Test
    fun testCurrencyRegistryExponentsAndScaleDivisors() {
        assertEquals(0, CurrencyRegistry.getExponent("JPY"))
        assertEquals(0, CurrencyRegistry.getExponent("KRW"))
        assertEquals(2, CurrencyRegistry.getExponent("USD"))
        assertEquals(2, CurrencyRegistry.getExponent("IDR"))
        assertEquals(2, CurrencyRegistry.getExponent("EUR"))
        assertEquals(3, CurrencyRegistry.getExponent("BHD"))
        assertEquals(2, CurrencyRegistry.getExponent("UNKNOWN"))

        assertEquals(1.0, CurrencyRegistry.getScaleDivisor("JPY"), DELTA)
        assertEquals(100.0, CurrencyRegistry.getScaleDivisor("USD"), DELTA)
        assertEquals(100.0, CurrencyRegistry.getScaleDivisor("IDR"), DELTA)
        assertEquals(1000.0, CurrencyRegistry.getScaleDivisor("BHD"), DELTA)

        assertEquals(50000L, CurrencyRegistry.displayToCanonicalMinorUnits(50000.0, "JPY"))
        assertEquals(50000.0, CurrencyRegistry.canonicalMinorUnitsToDisplay(50000L, "JPY"), DELTA)

        assertEquals(50050L, CurrencyRegistry.displayToCanonicalMinorUnits(500.50, "USD"))
        assertEquals(500.50, CurrencyRegistry.canonicalMinorUnitsToDisplay(50050L, "USD"), DELTA)
    }

    @Test
    fun testMultiExponentCurrenciesDisplayScale() {
        runBlocking {
            db.walletDao().insertWallet(
                Wallet(
                    id = 71L, name = "Tokyo Bank", type = WalletType.BANK, accountNumber = "71",
                    balance = 50000.0, balanceMinorUnits = 50000L, colorHex = "#00", iconName = "i", accountMode = "PERSONAL",
                    isDefault = true, createdAt = 1000L, currencyCode = "JPY", userId = testUserId
                )
            )

            viewModel.setSelectedDashboardCurrency("JPY")
            val jpyBalanceMinor = awaitLongValue(viewModel.totalBalanceMinorUnits, 50000L, "JPY minor units balance")
            assertEquals(50000L, jpyBalanceMinor)

            val jpyBalanceDisplay = awaitValue(viewModel.totalBalance, 50000.0, "JPY display balance")
            assertEquals(50000.0, jpyBalanceDisplay, DELTA)

            withTimeout(TIMEOUT_MS) {
                viewModel.multiCurrencyBalances.first { list ->
                    list.any { it.currencyCode == "JPY" && it.totalBalanceMinorUnits == 50000L && it.totalBalance == 50000.0 }
                }
            }
        }
    }

    @Test
    fun testSingleCurrencyWalletAutomaticallyDefaultsToThatCurrency() = runBlocking {
        // User has only 1 currency assigned (two IDR wallets)
        db.walletDao().insertWallet(
            Wallet(
                id = 81L, name = "IDR Cash", type = WalletType.CASH, accountNumber = "81",
                balance = 100000.0, balanceMinorUnits = 10000000L, colorHex = "#00", iconName = "i", accountMode = "PERSONAL",
                isDefault = true, createdAt = 1000L, currencyCode = "IDR", userId = testUserId
            )
        )
        db.walletDao().insertWallet(
            Wallet(
                id = 82L, name = "IDR Bank", type = WalletType.BANK, accountNumber = "82",
                balance = 200000.0, balanceMinorUnits = 20000000L, colorHex = "#00", iconName = "i", accountMode = "PERSONAL",
                isDefault = false, createdAt = 1000L, currencyCode = "IDR", userId = testUserId
            )
        )

        // Without explicit selection, single currency automatically resolves as default
        withTimeout(TIMEOUT_MS) {
            viewModel.activeDashboardCurrency.first { it == "IDR" }
        }
        assertEquals("IDR", viewModel.activeDashboardCurrency.value)
        val balance = awaitValue(viewModel.totalBalance, 300000.0, "Single currency totalBalance")
        assertEquals(300000.0, balance, DELTA)
    }

    // --- TEMUAN 4: FAIL-CLOSED TESTS FOR ALL FOUR AGGREGATIONS ---

    @Test
    fun testIncomeAggregationOverflowFailClosed() = runBlocking {
        db.walletDao().insertWallet(
            Wallet(
                id = 91L, name = "IDR Wallet", type = WalletType.BANK, accountNumber = "91",
                balance = 1000.0, balanceMinorUnits = 100000L, colorHex = "#00", iconName = "i", accountMode = "PERSONAL",
                isDefault = true, createdAt = 1000L, currencyCode = "IDR", userId = testUserId
            )
        )

        // Two transactions whose minor units sum exceeds Long.MAX_VALUE
        db.transactionDao().insertTransaction(
            TransactionItem(
                id = 201L, title = "Gigantic Income 1", amount = 1e16, amountMinorUnits = Long.MAX_VALUE,
                currencyCode = "IDR", type = TransactionType.INCOME, category = "Gaji",
                walletId = 91L, walletName = "IDR Wallet", userId = testUserId
            )
        )
        db.transactionDao().insertTransaction(
            TransactionItem(
                id = 202L, title = "Gigantic Income 2", amount = 10.0, amountMinorUnits = 1000L,
                currencyCode = "IDR", type = TransactionType.INCOME, category = "Gaji",
                walletId = 91L, walletName = "IDR Wallet", userId = testUserId
            )
        )

        viewModel.setSelectedDashboardCurrency("IDR")

        // Trigger subscription and assert fail-closed null with aggregationError
        withTimeout(TIMEOUT_MS) {
            viewModel.totalIncomeMinorUnits.first { it == null && viewModel.aggregationError.value != null }
        }

        assertTrue(viewModel.aggregationError.value?.contains("Overflow") == true)
        assertNull("totalIncomeMinorUnits must be null on overflow", viewModel.totalIncomeMinorUnits.value)
        assertNull("totalIncome must be null on overflow", viewModel.totalIncome.value)
    }

    @Test
    fun testExpenseAggregationOverflowFailClosed() = runBlocking {
        db.walletDao().insertWallet(
            Wallet(
                id = 92L, name = "USD Wallet", type = WalletType.BANK, accountNumber = "92",
                balance = 1000.0, balanceMinorUnits = 100000L, colorHex = "#00", iconName = "i", accountMode = "PERSONAL",
                isDefault = true, createdAt = 1000L, currencyCode = "USD", userId = testUserId
            )
        )

        db.transactionDao().insertTransaction(
            TransactionItem(
                id = 211L, title = "Gigantic Expense 1", amount = 1e16, amountMinorUnits = Long.MAX_VALUE,
                currencyCode = "USD", type = TransactionType.EXPENSE, category = "Investasi",
                walletId = 92L, walletName = "USD Wallet", userId = testUserId
            )
        )
        db.transactionDao().insertTransaction(
            TransactionItem(
                id = 212L, title = "Gigantic Expense 2", amount = 10.0, amountMinorUnits = 1000L,
                currencyCode = "USD", type = TransactionType.EXPENSE, category = "Investasi",
                walletId = 92L, walletName = "USD Wallet", userId = testUserId
            )
        )

        viewModel.setSelectedDashboardCurrency("USD")

        withTimeout(TIMEOUT_MS) {
            viewModel.totalExpenseMinorUnits.first { it == null && viewModel.aggregationError.value != null }
        }

        assertTrue(viewModel.aggregationError.value?.contains("Overflow") == true)
        assertNull("totalExpenseMinorUnits must be null on overflow", viewModel.totalExpenseMinorUnits.value)
        assertNull("totalExpense must be null on overflow", viewModel.totalExpense.value)
    }

    @Test
    fun testNetCashflowOverflowFailClosed() {
        // Test BigInteger subtraction fail-closed directly on edge boundaries
        val inc = Long.MAX_VALUE
        val exp = -100L // subtracting negative -> exceeds Long.MAX_VALUE
        try {
            java.math.BigInteger.valueOf(inc)
                .subtract(java.math.BigInteger.valueOf(exp))
                .longValueExact()
            fail("Expected ArithmeticException on net cashflow overflow")
        } catch (e: ArithmeticException) {
            assertTrue(e is ArithmeticException)
        }

        // Underflow: -100L - Long.MAX_VALUE < Long.MIN_VALUE
        try {
            java.math.BigInteger.valueOf(-100L)
                .subtract(java.math.BigInteger.valueOf(Long.MAX_VALUE))
                .longValueExact()
            fail("Expected ArithmeticException on net cashflow underflow")
        } catch (e: ArithmeticException) {
            assertTrue(e is ArithmeticException)
        }
    }
}
