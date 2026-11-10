package com.example

import com.example.data.model.ReceiptItem
import com.example.data.model.ReceiptScanResult
import com.example.data.model.SplitBillPerson
import com.example.data.model.UserRole
import com.example.util.CurrencyFormatter
import com.example.util.SplitBillCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Phase4UiResponsivenessAndLayoutContractTest {

    private fun resolveSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File tidak ditemukan: $relativePath (working dir: ${File(".").absolutePath})")
    }

    // =========================================================================
    // LAPIS 1 [DATA-FIRST]: Standarisasi Label Badge & Perhitungan Nominal Panjang
    // =========================================================================

    @Test
    fun `layer1 userRole resolveBadgeLabel returns accurate bilingual guest and member labels`() {
        assertEquals("Tamu", UserRole.FREE_MEMBER.resolveBadgeLabel(isGuest = true, isEnglish = false))
        assertEquals("Guest", UserRole.FREE_MEMBER.resolveBadgeLabel(isGuest = true, isEnglish = true))
        assertEquals("Member", UserRole.FREE_MEMBER.resolveBadgeLabel(isGuest = false, isEnglish = false))
        assertEquals("Smart Master 👑", UserRole.SMART_MASTER.resolveBadgeLabel(isGuest = false, isEnglish = true))
        assertEquals("MasterDev 👑", UserRole.MASTER_DEV.resolveBadgeLabel(isGuest = false, isEnglish = false))
    }

    @Test
    fun `layer1 splitBillCalculator and currencyFormatter handle extreme names and large amounts without line breaks`() {
        val people = listOf(
            SplitBillPerson(id = "p1", name = "Muhammad Raditya Pratama Wijaya Kusuma", colorHex = "#10B981"),
            SplitBillPerson(id = "p2", name = "Anastasia Frederica Permatasari", colorHex = "#3B82F6")
        )
        val items = listOf(
            ReceiptItem(
                id = "i1",
                name = "Paket Wagyu Meltique Tenderloin Double Cheese Special Edition",
                qty = 3,
                price = 1_250_000.0,
                total = 3_750_000.0,
                assignedPersonIds = listOf("p1", "p2")
            )
        )
        val receipt = ReceiptScanResult(
            merchantName = "Restoran Grand Imperial Nusantara Cabang Utama",
            date = "2026-11-10",
            subtotal = 3_750_000.0,
            tax = 375_000.0,
            serviceCharge = 187_500.0,
            discount = 0.0,
            totalAmount = 4_312_500.0,
            items = items
        )
        val result = SplitBillCalculator.calculateSplitBill(
            receipt = receipt,
            people = people,
            items = items,
            paymentTargetInfo = "BCA 1234567890"
        )

        assertEquals(2, result.breakdowns.size)
        val formattedTotal = CurrencyFormatter.formatRupiah(result.grandTotal)
        assertFalse("Formatted Rupiah tidak boleh mengandung karakter baris baru", formattedTotal.contains("\n"))
        assertTrue(result.breakdowns.all { it.totalToPay > 0.0 })
    }

    // =========================================================================
    // LAPIS 2 & 3 [CONTRACT]: 4.1 SplitBillScreen Anti-Vertical-Text
    // =========================================================================

    @Test
    fun `phase4_1 SplitBillScreen enforces weight fill false on left and single line no softWrap on right currency texts`() {
        val file = resolveSourceFile("src/main/java/com/example/ui/screens/SplitBillScreen.kt")
        val content = file.readText()

        // 1. Receipt Header Overview Card (currentReceiptState.totalAmount)
        val headerIdx = content.indexOf("CurrencyFormatter.formatRupiah(currentReceiptState.totalAmount)")
        assertTrue("CurrencyFormatter.formatRupiah(currentReceiptState.totalAmount) harus ada di SplitBillScreen.kt", headerIdx > 0)
        val headerWindow = content.substring((headerIdx - 1500).coerceAtLeast(0), (headerIdx + 500).coerceAtMost(content.length))
        assertTrue(
            "Kartu Ringkasan Total Tagihan wajib memakai weight(1f, fill = false) di sisi kiri",
            headerWindow.contains("weight(1f, fill = false)")
        )
        assertTrue(
            "Nominal Total Tagihan wajib memakai maxLines = 1 dan softWrap = false",
            headerWindow.contains("maxLines = 1") && headerWindow.contains("softWrap = false")
        )

        // 2. Proportional Breakdown Per Person (breakdown.totalToPay, itemShare, taxShare + serviceShare)
        val breakdownIdx = content.indexOf("CurrencyFormatter.formatRupiah(breakdown.totalToPay)")
        assertTrue("breakdown.totalToPay harus ada di SplitBillScreen.kt", breakdownIdx > 0)
        val breakdownWindow = content.substring((breakdownIdx - 3000).coerceAtLeast(0), (breakdownIdx + 3000).coerceAtMost(content.length))
        assertTrue(
            "Kartu Rincian Per Orang wajib memakai weight(1f, fill = false)",
            breakdownWindow.contains("weight(1f, fill = false)")
        )
        assertTrue(
            "Nominal breakdown.totalToPay & itemShare wajib memakai softWrap = false",
            breakdownWindow.contains("softWrap = false")
        )

        // 3. Split Bill History Card (item.totalAmount)
        val historyIdx = content.indexOf("CurrencyFormatter.formatRupiah(item.totalAmount)")
        assertTrue("item.totalAmount harus ada di SplitBillScreen.kt", historyIdx > 0)
        val historyWindow = content.substring((historyIdx - 1500).coerceAtLeast(0), (historyIdx + 500).coerceAtMost(content.length))
        assertTrue(
            "Kartu Riwayat Split Bill wajib memakai weight(1f, fill = false) di kiri",
            historyWindow.contains("weight(1f, fill = false)")
        )
        assertTrue(
            "Nominal Riwayat Split Bill wajib memakai maxLines = 1 dan softWrap = false",
            historyWindow.contains("maxLines = 1") && historyWindow.contains("softWrap = false")
        )

        // 4. SplitBillItemCard (CurrencyFormatter.formatRupiah(item.total))
        val itemCardIdx = content.indexOf("CurrencyFormatter.formatRupiah(item.total)")
        assertTrue("item.total pada SplitBillItemCard harus ada di SplitBillScreen.kt", itemCardIdx > 0)
        val itemCardWindow = content.substring((itemCardIdx - 1000).coerceAtLeast(0), (itemCardIdx + 500).coerceAtMost(content.length))
        assertTrue(
            "SplitBillItemCard wajib memakai weight(1f, fill = false) di kiri",
            itemCardWindow.contains("weight(1f, fill = false)")
        )
        assertTrue(
            "Nominal item.total pada SplitBillItemCard wajib memakai maxLines = 1 dan softWrap = false",
            itemCardWindow.contains("maxLines = 1") && itemCardWindow.contains("softWrap = false")
        )
    }

    // =========================================================================
    // LAPIS 2 & 3 [CONTRACT]: 4.2 Audit Menyeluruh UI Kaku
    // =========================================================================

    @Test
    fun `phase4_2 TransactionItemRow WalletComponents DebtPlanner Budget and AssetTracker prevent vertical currency wrapping`() {
        val transactionRow = resolveSourceFile("src/main/java/com/example/ui/components/TransactionItemRow.kt").readText()
        assertTrue(
            "TransactionItemRow wajib memakai weight(1f, fill = false)",
            transactionRow.contains("weight(1f, fill = false)")
        )
        assertTrue(
            "TransactionItemRow wajib memakai softWrap = false pada nominal",
            transactionRow.contains("softWrap = false")
        )

        val walletComponents = resolveSourceFile("src/main/java/com/example/ui/components/WalletComponents.kt").readText()
        assertTrue(
            "WalletComponents wajib memakai weight(1f, fill = false)",
            walletComponents.contains("weight(1f, fill = false)")
        )
        assertTrue(
            "WalletComponents wajib memakai softWrap = false pada nominal dompet",
            walletComponents.contains("softWrap = false")
        )

        val debtPlanner = resolveSourceFile("src/main/java/com/example/ui/components/DebtPlannerModalDialog.kt").readText()
        assertTrue(
            "DebtPlannerModalDialog wajib memakai weight(1f, fill = false)",
            debtPlanner.contains("weight(1f, fill = false)")
        )
        assertTrue(
            "DebtPlannerModalDialog wajib memakai softWrap = false pada nominal utang/cicilan",
            debtPlanner.contains("softWrap = false")
        )

        val budgetScreen = resolveSourceFile("src/main/java/com/example/ui/screens/BudgetScreen.kt").readText()
        assertTrue(
            "BudgetScreen wajib memakai softWrap = false pada nominal anggaran",
            budgetScreen.contains("softWrap = false")
        )

        val assetTracker = resolveSourceFile("src/main/java/com/example/ui/components/AssetTrackerModalDialog.kt").readText()
        assertTrue(
            "AssetTrackerModalDialog wajib memakai softWrap = false pada nominal aset",
            assetTracker.contains("softWrap = false")
        )
    }

    // =========================================================================
    // LAPIS 2 & 3 [CONTRACT]: 4.3 Perapian Badge Smart Member di SettingsScreen
    // =========================================================================

    @Test
    fun `phase4_3 SettingsScreen places Smart Member badge vertically below username inside Column`() {
        val settingsContent = resolveSourceFile("src/main/java/com/example/ui/screens/SettingsScreen.kt").readText()
        val usernameIdx = settingsContent.indexOf("text = displayedUsername")
        assertTrue("text = displayedUsername harus ada di SettingsScreen.kt", usernameIdx > 0)

        val profileSection = settingsContent.substring(
            (usernameIdx - 250).coerceAtLeast(0),
            (usernameIdx + 1200).coerceAtMost(settingsContent.length)
        )

        assertFalse(
            "Badge tidak boleh lagi diletakkan berdesakan di samping nama dalam Row yang sama",
            profileSection.contains("Row(verticalAlignment = Alignment.CenterVertically) {\n                                Text(\n                                    text = displayedUsername")
        )
        assertTrue(
            "Column profil pengguna wajib memakai weight(1f, fill = false) dan menaruh badge di bawah nama",
            profileSection.contains("Column(modifier = Modifier.weight(1f, fill = false))") &&
                profileSection.contains("Spacer(modifier = Modifier.height(4.dp))")
        )
    }

    // =========================================================================
    // LAPIS 2 & 3 [CONTRACT]: 4.4 Animasi Halus Transisi Tab & Vertical Scroll Dialog
    // =========================================================================

    @Test
    fun `phase4_4 FinSmartApp uses AnimatedContent for tab transitions and dialogs support verticalScroll`() {
        val appContent = resolveSourceFile("src/main/java/com/example/ui/FinSmartApp.kt").readText()
        assertTrue(
            "FinSmartApp.kt wajib menggunakan AnimatedContent untuk transisi antar tab",
            appContent.contains("AnimatedContent(") && appContent.contains("targetState = currentScreen")
        )

        val changePassContent = resolveSourceFile("src/main/java/com/example/ui/components/ChangePasswordDialog.kt").readText()
        assertTrue(
            "ChangePasswordDialog.kt wajib mendukung verticalScroll(rememberScrollState())",
            changePassContent.contains("verticalScroll(rememberScrollState())")
        )

        val deleteAccountContent = resolveSourceFile("src/main/java/com/example/ui/components/DeleteAccountDialog.kt").readText()
        assertTrue(
            "DeleteAccountDialog.kt wajib mendukung verticalScroll(rememberScrollState())",
            deleteAccountContent.contains("verticalScroll(rememberScrollState())")
        )

        val initialMasterContent = resolveSourceFile("src/main/java/com/example/ui/components/InitialMasterSetupDialog.kt").readText()
        assertTrue(
            "InitialMasterSetupDialog.kt wajib mendukung verticalScroll(rememberScrollState())",
            initialMasterContent.contains("verticalScroll(rememberScrollState())")
        )
    }
}
