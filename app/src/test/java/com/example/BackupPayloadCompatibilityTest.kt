package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FinSmartDatabase
import com.example.data.model.AccountMode
import com.example.data.model.AssetCategory
import com.example.data.model.AssetItem
import com.example.data.model.BackupDataPayload
import com.example.data.model.CategoryItem
import com.example.data.model.DebtItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.model.Wallet
import com.example.data.model.WalletType
import com.example.util.GoogleDriveService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupPayloadCompatibilityTest {

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

    private suspend fun seedComprehensiveData(): Pair<DebtItem, AssetItem> {
        val userId = 101L
        db.userDao().insertUser(User(id = userId, username = "compatuser", email = "compat@example.com", role = UserRole.FREE_MEMBER))

        val wallet = Wallet(
            id = 101L,
            name = "Dompet BCA",
            type = WalletType.BANK,
            accountNumber = "9876543210",
            balance = 15000000.0,
            colorHex = "#0B57D0",
            iconName = "account_balance",
            accountMode = AccountMode.PERSONAL.name,
            isDefault = true,
            userId = userId
        )
        db.walletDao().insertWallet(wallet)

        val transaction = TransactionItem(
            id = 201L,
            title = "Bayar Kuliah",
            amount = 5000000.0,
            type = TransactionType.EXPENSE,
            category = "Pendidikan",
            categoryIcon = "school",
            walletId = 101L,
            walletName = "Dompet BCA",
            timestamp = 1700000000000L,
            note = "Semester 5",
            tags = "#kuliah",
            accountMode = AccountMode.PERSONAL.name,
            userId = userId
        )
        db.transactionDao().insertTransaction(transaction)

        val debt = DebtItem(
            id = "debt-compat-01",
            name = "KPR BTN",
            amount = 250000000.0,
            interestRatePercent = 7.5,
            minMonthlyPayment = 3200000.0,
            category = "Pinjaman Bank",
            dueDate = "Tgl 5 tiap bulan",
            createdAt = 1700000000000L,
            userId = userId
        )
        db.debtDao().insertDebt(debt)

        val asset = AssetItem(
            id = "asset-compat-01",
            name = "Rumah Minimalis",
            initialValue = 450000000.0,
            purchaseYear = 2021,
            category = AssetCategory.PROPERTY,
            createdAt = 1700000000000L,
            userId = userId
        )
        db.assetDao().insertAsset(asset)

        return Pair(debt, asset)
    }

    @Test
    fun testLegacyV1PayloadWithoutDebtsAndAssetsDeserializesCleanly() {
        val legacyJson = """
            {
              "wallets": [
                {
                  "id": 1,
                  "name": "Legacy Wallet",
                  "type": "CASH",
                  "accountNumber": "001",
                  "balance": 1000000.0,
                  "colorHex": "#123456",
                  "iconName": "cash",
                  "accountMode": "PERSONAL",
                  "isDefault": true,
                  "createdAt": 1690000000000
                }
              ],
              "transactions": [],
              "categories": [],
              "users": [],
              "splitBillHistory": [],
              "budgetLimits": [],
              "feedbacks": [],
              "version": 1,
              "exportedAt": 1690000000000
            }
        """.trimIndent()

        val adapter = moshi.adapter(BackupDataPayload::class.java)
        val payload = adapter.fromJson(legacyJson)

        assertNotNull("Payload legacy harus berhasil di-parse", payload)
        assertEquals(1, payload?.wallets?.size)
        assertEquals("Legacy Wallet", payload?.wallets?.first()?.name)
        assertNotNull("Field debts harus memiliki nilai default non-null", payload?.debts)
        assertTrue("Debts harus default emptyList() pada legacy payload", payload?.debts?.isEmpty() == true)
        assertNotNull("Field assets harus memiliki nilai default non-null", payload?.assets)
        assertTrue("Assets harus default emptyList() pada legacy payload", payload?.assets?.isEmpty() == true)
    }

    @Test
    fun testLegacyV2PayloadWithoutDebtsAndAssetsDeserializesCleanly() {
        val legacyV2PayloadJson = """
            {
              "wallets": [
                {
                  "id": 2,
                  "name": "Dompet Digital",
                  "type": "EWALLET",
                  "accountNumber": "0812345678",
                  "balance": 750000.0,
                  "colorHex": "#00AA13",
                  "iconName": "wallet",
                  "accountMode": "PERSONAL",
                  "isDefault": false,
                  "createdAt": 1700000000000
                }
              ],
              "transactions": [],
              "categories": [],
              "users": [],
              "splitBillHistory": [],
              "budgetLimits": [],
              "feedbacks": [],
              "version": 2,
              "exportedAt": 1700000000000
            }
        """.trimIndent()

        val adapter = moshi.adapter(BackupDataPayload::class.java)
        val payload = adapter.fromJson(legacyV2PayloadJson)

        assertNotNull(payload)
        assertEquals(1, payload?.wallets?.size)
        assertEquals("Dompet Digital", payload?.wallets?.first()?.name)
        assertEquals(0, payload?.debts?.size)
        assertEquals(0, payload?.assets?.size)
    }

    @Test
    fun testNewV2PayloadWithDebtsAndAssetsRoundtrip() {
        val debt = DebtItem(
            id = "debt-roundtrip-1",
            name = "Cicilan Mobil",
            amount = 120000000.0,
            interestRatePercent = 8.0,
            minMonthlyPayment = 3500000.0,
            category = "Cicilan Kendaraan",
            dueDate = "Tgl 25 tiap bulan",
            createdAt = 1705000000000L
        )
        val asset = AssetItem(
            id = "asset-roundtrip-1",
            name = "Toyota Yaris",
            initialValue = 240000000.0,
            purchaseYear = 2022,
            category = AssetCategory.VEHICLE,
            createdAt = 1705000000000L
        )

        val originalPayload = BackupDataPayload(
            debts = listOf(debt),
            assets = listOf(asset),
            version = 2,
            exportedAt = System.currentTimeMillis()
        )

        val adapter = moshi.adapter(BackupDataPayload::class.java)
        val serializedJson = adapter.toJson(originalPayload)

        assertTrue(serializedJson.contains("\"debts\""))
        assertTrue(serializedJson.contains("\"assets\""))
        assertTrue(serializedJson.contains("Toyota Yaris"))
        assertTrue(serializedJson.contains("Cicilan Mobil"))

        val deserializedPayload = adapter.fromJson(serializedJson)
        assertNotNull(deserializedPayload)
        assertEquals(1, deserializedPayload?.debts?.size)
        assertEquals(1, deserializedPayload?.assets?.size)

        val restoredDebt = deserializedPayload?.debts?.first()!!
        assertEquals("debt-roundtrip-1", restoredDebt.id)
        assertEquals("Cicilan Mobil", restoredDebt.name)
        assertEquals(120000000.0, restoredDebt.amount, 0.01)
        assertEquals(8.0, restoredDebt.interestRatePercent, 0.01)
        assertEquals("Cicilan Kendaraan", restoredDebt.category)

        val restoredAsset = deserializedPayload.assets.first()
        assertEquals("asset-roundtrip-1", restoredAsset.id)
        assertEquals("Toyota Yaris", restoredAsset.name)
        assertEquals(240000000.0, restoredAsset.initialValue, 0.01)
        assertEquals(2022, restoredAsset.purchaseYear)
        assertEquals(AssetCategory.VEHICLE, restoredAsset.category)
    }

    @Test
    fun testFullDeviceLocalBackupAndRestoreRoundtripWithDebtsAndAssets() = runBlocking {
        val (seededDebt, seededAsset) = seedComprehensiveData()
        val userId = 101L

        // 1. Eksekusi Device-Local Backup (Format v2)
        val (backupSuccess, backupMsg) = GoogleDriveService.backupNow(context, db, userId)
        assertTrue("Device-local backup harus berhasil: $backupMsg", backupSuccess)

        // 2. Modifikasi database untuk memverifikasi pemulihan
        db.walletDao().clearAllWallets(userId)
        db.debtDao().clearAllDebts(userId)
        db.assetDao().clearAllAssets(userId)
        assertEquals(0, db.debtDao().getDebtCount(userId))
        assertEquals(0, db.assetDao().getAssetCount(userId))
        assertEquals(0, db.walletDao().getAllWallets(userId).first().size)

        // 3. Eksekusi Restore
        val (restoreSuccess, restoreMsg) = GoogleDriveService.restoreNow(context, db, userId)
        assertTrue("Device-local restore harus berhasil: $restoreMsg", restoreSuccess)

        // 4. Verifikasi data Room pulih secara utuh
        val restoredDebts = db.debtDao().getAllDebts(userId).first()
        assertEquals(1, restoredDebts.size)
        assertEquals(seededDebt.name, restoredDebts[0].name)
        assertEquals(seededDebt.amount, restoredDebts[0].amount, 0.01)

        val restoredAssets = db.assetDao().getAllAssets(userId).first()
        assertEquals(1, restoredAssets.size)
        assertEquals(seededAsset.name, restoredAssets[0].name)
        assertEquals(seededAsset.initialValue, restoredAssets[0].initialValue, 0.01)
        assertEquals(seededAsset.category, restoredAssets[0].category)

        val restoredWallets = db.walletDao().getAllWallets(userId).first()
        assertEquals(1, restoredWallets.size)
        assertEquals("Dompet BCA", restoredWallets[0].name)
    }

    @Test
    fun testFullPortableBackupAndRestoreRoundtripWithDebtsAndAssets() = runBlocking {
        val (seededDebt, seededAsset) = seedComprehensiveData()
        val userId = 101L
        val password = "StrongPassword#2026"

        // 1. Eksekusi Portable Backup
        val (exportSuccess, exportMsg, exportFile) = GoogleDriveService.backupPortableNow(context, db, password, userId)
        assertTrue("Portable backup harus berhasil: $exportMsg", exportSuccess)
        assertNotNull(exportFile)

        val backupJson = exportFile!!.readText()

        // 2. Modifikasi data di Room
        db.walletDao().clearAllWallets(userId)
        db.debtDao().clearAllDebts(userId)
        db.assetDao().clearAllAssets(userId)

        // 3. Eksekusi Restore Portable
        val (restoreSuccess, restoreMsg) = GoogleDriveService.restorePortableNow(context, db, backupJson, password, userId)
        assertTrue("Portable restore harus berhasil: $restoreMsg", restoreSuccess)

        // 4. Verifikasi pemulihan
        val restoredDebts = db.debtDao().getAllDebts(userId).first()
        assertEquals(1, restoredDebts.size)
        assertEquals(seededDebt.name, restoredDebts[0].name)

        val restoredAssets = db.assetDao().getAllAssets(userId).first()
        assertEquals(1, restoredAssets.size)
        assertEquals(seededAsset.name, restoredAssets[0].name)

        val restoredWallets = db.walletDao().getAllWallets(userId).first()
        assertEquals(1, restoredWallets.size)
    }

    @Test
    fun testRestoreLegacyBackupSemanticsClearsTablesAndLeavesDebtsAssetsEmpty() = runBlocking {
        // Seed initial debts and assets in DB
        seedComprehensiveData()
        val userId = 101L
        assertEquals(1, db.debtDao().getDebtCount(userId))
        assertEquals(1, db.assetDao().getAssetCount(userId))

        // Create legacy payload without debts and assets
        val legacyPayload = BackupDataPayload(
            wallets = listOf(
                Wallet(
                    id = 999L,
                    name = "Legacy Restore Only Wallet",
                    type = WalletType.CASH,
                    accountNumber = "999",
                    balance = 500000.0,
                    colorHex = "#999999",
                    iconName = "cash",
                    accountMode = AccountMode.PERSONAL.name,
                    isDefault = true,
                    userId = userId
                )
            ),
            transactions = emptyList(),
            categories = emptyList(),
            users = emptyList(),
            splitBillHistory = emptyList(),
            budgetLimits = emptyList(),
            debts = emptyList(), // legacy payload has no debts
            assets = emptyList(), // legacy payload has no assets
            version = 1,
            exportedAt = 1690000000000L
        )

        // Simulate restore of this legacy payload using scoped restoreUserDataInTransaction (DEF-04)
        com.example.ui.viewmodel.FinanceViewModel.restoreUserDataInTransaction(db, legacyPayload, userId)

        // Verify: Legacy wallets are restored
        val wallets = db.walletDao().getAllWallets(userId).first()
        assertEquals(1, wallets.size)
        assertEquals("Legacy Restore Only Wallet", wallets[0].name)

        // Verify: Debts and assets are empty because legacy backup contained no debts/assets
        assertEquals(0, db.debtDao().getDebtCount(userId))
        assertEquals(0, db.assetDao().getAssetCount(userId))
    }
}
