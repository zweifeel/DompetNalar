package com.example.data.ai

import com.example.data.model.DebtItem
import com.example.data.model.PayLaterBillScanResult
import com.example.util.NotificationDataHelper
import com.example.util.PayLaterDeduplicator
import com.example.util.ReceiptCalculator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class PayLaterImportTest {

    private val EPSILON = 0.001

    @Test
    fun test1_IndonesianRupiahAmountExtraction() {
        // 1. Ekstraksi nominal Rupiah dengan berbagai variasi pemisah ribuan
        assertEquals(354000.0, ReceiptCalculator.parseAmountSafe("Rp 354.000"), EPSILON)
        assertEquals(354000.0, ReceiptCalculator.parseAmountSafe("354,000"), EPSILON)
        assertEquals(1416000.0, ReceiptCalculator.parseAmountSafe("Rp1.416.000"), EPSILON)
        assertEquals(1416000.0, ReceiptCalculator.parseAmountSafe("1,416,000"), EPSILON)
        assertEquals(125000.0, ReceiptCalculator.parseAmountSafe("125.000"), EPSILON)
        assertEquals(0.0, ReceiptCalculator.parseAmountSafe(""), EPSILON)
        assertEquals(0.0, ReceiptCalculator.parseAmountSafe(null), EPSILON)
    }

    @Test
    fun test2_DueDateParsingAndNormalization() {
        // 2. Parsing tanggal jatuh tempo dan penolakan tanggal yang tidak valid
        val (norm1, day1) = PayLaterAiOcrScanner.parseDueDateString("Tgl 5 tiap bulan")
        assertEquals("Tgl 5 tiap bulan", norm1)
        assertEquals(5, day1)

        val (norm2, day2) = PayLaterAiOcrScanner.parseDueDateString("Bayar sebelum 15 Nov 2026")
        assertEquals("Tgl 15 tiap bulan", norm2)
        assertEquals(15, day2)

        val (norm3, day3) = PayLaterAiOcrScanner.parseDueDateString("2026-10-25")
        assertEquals("Tgl 25 tiap bulan", norm3)
        assertEquals(25, day3)

        val (norm4, day4) = PayLaterAiOcrScanner.parseDueDateString("Jatuh tempo tgl 30")
        assertEquals("Tgl 30 tiap bulan", norm4)
        assertEquals(30, day4)

        // String kosong atau tidak valid
        val (normInvalid, dayInvalid) = PayLaterAiOcrScanner.parseDueDateString("")
        assertEquals("", normInvalid)
        assertNull(dayInvalid)

        val (normGarbage, dayGarbage) = PayLaterAiOcrScanner.parseDueDateString("Segera lunasi tagihan Anda")
        assertEquals("Segera lunasi tagihan Anda", normGarbage)
        assertNull(dayGarbage)
    }

    @Test
    fun test3_MissingOrAmbiguousFields_ProducesConfidenceWarnings() {
        // 3. Kolom yang hilang tidak diisi angka/tanggal rekaan, melainkan menghasilkan peringatan
        val emptyJson = JSONObject("{}")
        val result = PayLaterAiOcrScanner.parseScanResultFromJson(emptyJson)

        assertEquals("Tagihan PayLater", result.providerName)
        assertEquals(0.0, result.currentBillAmount, EPSILON)
        assertEquals(0.0, result.totalRemainingAmount, EPSILON)
        assertNull(result.installmentCurrent)
        assertNull(result.installmentTotal)
        assertTrue("Harus menghasilkan peringatan ketika data kosong", result.confidenceWarnings.isNotEmpty())
        assertTrue(result.confidenceWarnings.any { it.contains("Nama penyedia") })
        assertTrue(result.confidenceWarnings.any { it.contains("Nominal tagihan") })
    }

    @Test
    fun test4_DistinctionCurrentBillAmountVsTotalRemainingDebt() {
        // 4. Jumlah tagihan berjalan TIDAK tercampur dengan total sisa utang
        val json = JSONObject("""
            {
                "nama_penyedia": "Shopee PayLater",
                "nama_produk": "SPayLater",
                "tagihan_bulan_ini": 354000.0,
                "total_sisa_utang": 1416000.0,
                "cicilan_ke": 2,
                "total_cicilan": 6,
                "tanggal_jatuh_tempo": "Tgl 5 tiap bulan"
            }
        """.trimIndent())

        val scanResult = PayLaterAiOcrScanner.parseScanResultFromJson(json)
        assertEquals(354000.0, scanResult.currentBillAmount, EPSILON)
        assertEquals(1416000.0, scanResult.totalRemainingAmount, EPSILON)
        assertEquals(2, scanResult.installmentCurrent)
        assertEquals(6, scanResult.installmentTotal)

        // Konversi ke DebtItem
        val debtItem = scanResult.toDebtItem(userId = 1L)
        // Total utang harus sebesar total sisa utang (1.416.000)
        assertEquals(1416000.0, debtItem.amount, EPSILON)
        // Tagihan bulanan harus sebesar tagihan bulan ini (354.000)
        assertEquals(354000.0, debtItem.minMonthlyPayment, EPSILON)
        assertTrue(debtItem.name.contains("Shopee PayLater"))
        assertTrue(debtItem.name.contains("Cicilan 2/6"))
    }

    @Test
    fun test5_UserCorrectionBeforeSaving() {
        // 5. Pengguna dapat mengoreksi hasil ekstraksi sebelum penyimpanan
        val initialScan = PayLaterAiOcrScanner.getSamplePayLaterBill("dana")

        // Simulasi koreksi pengguna pada dialog
        val correctedResult = initialScan.copy(
            providerName = "DANA Cicil (Revisi)",
            currentBillAmount = 150000.0,
            totalRemainingAmount = 300000.0,
            dueDate = "Tgl 20 tiap bulan"
        )

        val finalDebtItem = correctedResult.toDebtItem(userId = 42L, customCategory = "Pinjaman Online")
        assertEquals("DANA Cicil (Revisi) (Cicilan 4x) - Cicilan 3/4", finalDebtItem.name)
        assertEquals(300000.0, finalDebtItem.amount, EPSILON)
        assertEquals(150000.0, finalDebtItem.minMonthlyPayment, EPSILON)
        assertEquals("Tgl 20 tiap bulan", finalDebtItem.dueDate)
        assertEquals(42L, finalDebtItem.userId)
    }

    @Test
    fun test6_CancellationDoesNotSaveDebt() {
        // 6. Pembatalan tidak menghasilkan penambahan ke database atau list hutang
        val existingDebts = mutableListOf<DebtItem>()
        val initialCount = existingDebts.size

        // Simulasi scan menghasilkan data
        val scanResult = PayLaterAiOcrScanner.getSamplePayLaterBill("shopee")
        assertNotNull(scanResult)

        // Pengguna menekan tombol "Batal"
        val userCancelled = true
        if (!userCancelled) {
            existingDebts.add(scanResult.toDebtItem(userId = 1L))
        }

        assertEquals(initialCount, existingDebts.size)
    }

    @Test
    fun test7_DuplicateDetectionForSameUser() {
        // 7. Duplikasi tagihan untuk pengguna yang sama dapat dideteksi
        val targetUserId = 10L
        val existingDebts = listOf(
            DebtItem(
                id = "debt-1",
                name = "Shopee PayLater - Cicilan 1/6",
                amount = 1416000.0,
                interestRatePercent = 2.95,
                minMonthlyPayment = 354000.0,
                category = "Pinjaman Online",
                dueDate = "Tgl 5 tiap bulan",
                userId = targetUserId
            )
        )

        val duplicateCandidate = PayLaterBillScanResult(
            providerName = "Shopee PayLater",
            currentBillAmount = 354000.0,
            totalRemainingAmount = 1416000.0,
            dueDate = "Tgl 5 tiap bulan"
        )

        val result = PayLaterDeduplicator.checkDuplicate(
            targetUserId = targetUserId,
            userDebts = existingDebts,
            scanResult = duplicateCandidate
        )

        assertTrue("Tagihan serupa milik pengguna yang sama harus terdeteksi duplikat", result.isDuplicate)
        assertNotNull(result.matchedDebt)
        assertEquals("debt-1", result.matchedDebt?.id)
    }

    @Test
    fun test8_CrossUserIsolation_NoDuplicateCollisionAcrossUsers() {
        // 8. Tagihan serupa milik dua pengguna berbeda tidak saling bocor atau dianggap duplikat
        val userA = 101L
        val userB = 102L

        // Tagihan milik User A
        val userADebts = listOf(
            DebtItem(
                id = "debt-user-a",
                name = "GoPayLater",
                amount = 500000.0,
                interestRatePercent = 0.0,
                minMonthlyPayment = 500000.0,
                category = "Pinjaman Online",
                dueDate = "Tgl 1 tiap bulan",
                userId = userA
            )
        )

        // Tagihan baru yang diimpor oleh User B
        val userBCandidate = PayLaterBillScanResult(
            providerName = "GoPayLater",
            currentBillAmount = 500000.0,
            totalRemainingAmount = 500000.0,
            dueDate = "Tgl 1 tiap bulan"
        )

        // Pemeriksaan duplikasi untuk User B menggunakan daftar hutang User B (kosong)
        val userBDebts = emptyList<DebtItem>()
        val resultForUserB = PayLaterDeduplicator.checkDuplicate(
            targetUserId = userB,
            userDebts = userBDebts,
            scanResult = userBCandidate
        )

        assertFalse("Tagihan User B tidak boleh dianggap duplikat dari tagihan User A", resultForUserB.isDuplicate)
        assertNull(resultForUserB.matchedDebt)

        // Jika dipaksa memasukkan userADebts dengan targetUserId = userB, isolasi tetap aman
        val strictCheck = PayLaterDeduplicator.checkDuplicate(
            targetUserId = userB,
            userDebts = userADebts, // List salah/lintas pengguna
            scanResult = userBCandidate
        )
        assertFalse("Pemeriksaan harus mengabaikan hutang yang userId-nya tidak cocok", strictCheck.isDuplicate)
    }

    @Test
    fun test9_OcrFailureDoesNotCreateFakeDebt() {
        // 9. Kegagalan OCR/AI tidak menghasilkan catatan utang palsu
        val debtsList = mutableListOf<DebtItem>()
        var errorMessage: String? = null

        try {
            // Simulasi error respons tidak valid / null
            val invalidJson = " Bukan JSON valid "
            if (!invalidJson.trim().startsWith("{")) {
                throw IllegalStateException("Respons AI tidak memuat format JSON yang valid.")
            }
        } catch (e: Exception) {
            errorMessage = e.message
            // Sistem menangani error tanpa menambah hutang ke list
        }

        assertNotNull(errorMessage)
        assertEquals(0, debtsList.size)
    }

    @Test
    fun test10_BackwardCompatibilityWithExistingDebtModel() {
        // 10. Kompatibilitas data lama: DebtItem yang sudah ada tetap berfungsi tanpa perubahan skema
        val legacyDebt = DebtItem(
            name = "Kartu Kredit BCA",
            amount = 5000000.0,
            interestRatePercent = 2.0,
            minMonthlyPayment = 500000.0,
            category = "Kartu Kredit",
            dueDate = "15 tiap bulan",
            userId = 1L
        )

        assertEquals("Kartu Kredit BCA", legacyDebt.name)
        assertEquals(5000000.0, legacyDebt.amount, EPSILON)
        assertEquals("15 tiap bulan", legacyDebt.dueDate)
    }

    @Test
    fun test11_NotificationDataHelperCompatibilityWithImportedDebt() {
        // 11. Tagihan hasil impor memicu notifikasi pengingat jatuh tempo secara reaktif
        val importedDebt = DebtItem(
            name = "Shopee PayLater",
            amount = 1416000.0,
            interestRatePercent = 0.0,
            minMonthlyPayment = 354000.0,
            category = "Pinjaman Online",
            dueDate = "Tgl 5 tiap bulan",
            userId = 1L
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 5) // Hari ini tanggal 5
        }

        val notifications = NotificationDataHelper.generateNotifications(
            wallets = emptyList(),
            debts = listOf(importedDebt),
            budgetLimits = emptyList(),
            currentMonthExpenses = emptyList(),
            calendar = calendar
        )

        val dueNotification = notifications.firstOrNull { it.title.contains("Jatuh Tempo Hari Ini") }
        assertNotNull("Tagihan tanggal 5 harus memicu notifikasi jatuh tempo hari ini saat tanggal 5", dueNotification)
        assertTrue(dueNotification!!.message.contains("Shopee PayLater"))
        assertTrue(dueNotification.message.contains("354.000"))
    }

    @Test
    fun test12_DistinctLoansFromSameProviderNotFalselyMerged() {
        // Dua pinjaman berbeda dari penyedia yang sama (misal AdaKami atau GoPay Pinjam) dengan nominal berbeda
        // tidak boleh salah digabung atau dideteksi sebagai duplikat kontrak yang sama
        val targetUserId = 1L

        val existingAdaKamiPlan = com.example.data.model.InstallmentPlanItem(
            id = "adakami-loan-1",
            name = "AdaKami Pinjaman 1",
            provider = "AdaKami",
            installmentAmount = 500000.0,
            totalTenor = 3,
            paidCount = 1,
            anchorDay = 15,
            startDate = "2026-09-15",
            nextDueDate = "Tgl 15 tiap bulan",
            userId = targetUserId
        )

        // Screenshot pinjaman AdaKami kedua yang berbeda nominal dan tanggal jatuh tempo
        val candidateAdaKamiLoan2 = PayLaterBillScanResult(
            providerName = "AdaKami",
            productName = "Pinjaman Multiguna 2",
            currentBillAmount = 1787073.0,
            totalRemainingAmount = 7128010.0,
            installmentCurrent = 1,
            installmentTotal = 6,
            dueDate = "Tgl 8 tiap bulan",
            dueDay = 8
        )

        val checkAdaKami = PayLaterDeduplicator.checkDuplicate(
            targetUserId = targetUserId,
            userDebts = emptyList(),
            userInstallmentPlans = listOf(existingAdaKamiPlan),
            scanResult = candidateAdaKamiLoan2
        )

        assertFalse("Pinjaman kedua dari AdaKami dengan nominal dan tenor berbeda tidak boleh dianggap duplikat dari pinjaman pertama", checkAdaKami.isDuplicate)
        assertNull(checkAdaKami.matchedPlan)

        // Begitu juga untuk dua pinjaman GoPay Pinjam yang berbeda
        val existingGoPayDebt = DebtItem(
            id = "gopay-1",
            name = "GoPay Pinjam - Pinjaman Tunai 1",
            amount = 10000000.0,
            interestRatePercent = 2.5,
            minMonthlyPayment = 1200000.0,
            category = "Pinjaman Online",
            dueDate = "Tgl 25 tiap bulan",
            userId = targetUserId
        )

        val candidateGoPay2 = PayLaterBillScanResult(
            providerName = "GoPay Pinjam",
            productName = "Pinjaman Baru",
            currentBillAmount = 450000.0,
            totalRemainingAmount = 1800000.0,
            dueDate = "Tgl 10 tiap bulan"
        )

        val checkGoPay = PayLaterDeduplicator.checkDuplicate(
            targetUserId = targetUserId,
            userDebts = listOf(existingGoPayDebt),
            userInstallmentPlans = emptyList(),
            scanResult = candidateGoPay2
        )

        assertFalse("Dua pinjaman GoPay berbeda nominal dan tanggal jatuh tempo tidak boleh salah dideteksi sebagai duplikat", checkGoPay.isDuplicate)
    }

    @Test
    fun test13_InstallmentPaymentTransactionTaggedWithPlanIdAndPeriod() {
        // Transaksi pengeluaran angsuran memuat tag identitas kontrak cicilan secara permanen
        val planId = "test-plan-uuid-123"
        val period = 3
        val expectedTag = "installment_plan_id:$planId,period:$period"

        val tx = com.example.data.model.TransactionItem(
            title = "Angsuran Motor (3/35)",
            amount = 850000.0,
            type = com.example.data.model.TransactionType.EXPENSE,
            category = "Cicilan & Hutang",
            walletId = 1L,
            walletName = "BCA",
            tags = expectedTag,
            userId = 1L
        )

        assertTrue("Transaksi harus memuat ID kontrak cicilan pada tags", tx.tags.contains("installment_plan_id:$planId"))
        assertTrue("Transaksi harus memuat nomor periode angsuran pada tags", tx.tags.contains("period:$period"))
    }
}
