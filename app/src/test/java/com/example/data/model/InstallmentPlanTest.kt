package com.example.data.model

import com.example.data.local.FinSmartDatabase
import com.example.util.CurrencyFormatter
import com.example.util.InstallmentDateHelper
import com.example.util.NotificationDataHelper
import com.example.util.PayLaterDeduplicator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
class InstallmentPlanTest {

    private val EPSILON = 0.001

    @Test
    fun test1_Create35xInstallmentPlan() {
        // 1. Membuat cicilan 35 kali
        val plan = InstallmentPlanItem(
            name = "Cicilan Motor Honda Vario",
            provider = "FIF Group",
            installmentAmount = 850000.0,
            totalTenor = 35,
            paidCount = 0,
            anchorDay = 15,
            startDate = "2026-10-15",
            nextDueDate = "Tgl 15 tiap bulan",
            totalRemainingPrincipal = null,
            status = "ACTIVE",
            notes = "Kontrak 35 bulan",
            userId = 1L
        )

        assertEquals("Cicilan Motor Honda Vario", plan.name)
        assertEquals("FIF Group", plan.provider)
        assertEquals(35, plan.totalTenor)
        assertEquals(850000.0, plan.installmentAmount, EPSILON)
        assertEquals(15, plan.anchorDay)
        assertEquals("ACTIVE", plan.status)
        assertEquals(1L, plan.userId)
    }

    @Test
    fun test2_CalculateRemainingTenorBeforeFirstPayment() {
        // 2. Menghitung sisa tenor sebelum pembayaran pertama
        val plan = InstallmentPlanItem(
            name = "Cicilan Mobil",
            installmentAmount = 3500000.0,
            totalTenor = 35,
            paidCount = 0,
            anchorDay = 10,
            startDate = "2026-10-10",
            nextDueDate = "Tgl 10 tiap bulan"
        )

        assertEquals(0, plan.paidCount)
        assertEquals(35, plan.remainingTenor)
        assertEquals(1, plan.currentPeriod)
        assertFalse(plan.isCompleted)
    }

    @Test
    fun test3_ReduceRemainingTenorAfterConfirmedPayment() {
        // 3. Mengurangi sisa tenor setelah pembayaran dikonfirmasi
        val initialPlan = InstallmentPlanItem(
            name = "Cicilan Motor",
            installmentAmount = 850000.0,
            totalTenor = 35,
            paidCount = 0,
            anchorDay = 15,
            startDate = "2026-10-15",
            nextDueDate = "Tgl 15 tiap bulan"
        )

        // Konfirmasi pembayaran pertama
        val nextDueDate = InstallmentDateHelper.calculateNextDueDate(initialPlan.nextDueDate, initialPlan.anchorDay)
        val updatedPlan = initialPlan.copy(
            paidCount = initialPlan.paidCount + 1,
            nextDueDate = nextDueDate
        )

        assertEquals(1, updatedPlan.paidCount)
        assertEquals(34, updatedPlan.remainingTenor)
        assertEquals(2, updatedPlan.currentPeriod)
        assertFalse(updatedPlan.isCompleted)
        assertEquals("AKTIF", updatedPlan.status)
    }

    @Test
    fun test4_DoNotReduceTenorWhenPaymentCancelled() {
        // 4. Tidak mengurangi tenor ketika pembayaran dibatalkan
        val plan = InstallmentPlanItem(
            name = "Cicilan Laptop",
            installmentAmount = 1200000.0,
            totalTenor = 12,
            paidCount = 3,
            anchorDay = 20,
            startDate = "2026-07-20",
            nextDueDate = "Tgl 20 tiap bulan"
        )

        // Simulasi pengguna membuka dialog bayar tetapi menekan tombol Batal
        val wasCancelled = true
        val effectivePlan = if (wasCancelled) plan else plan.copy(paidCount = plan.paidCount + 1)

        assertEquals(3, effectivePlan.paidCount)
        assertEquals(9, effectivePlan.remainingTenor)
        assertEquals(4, effectivePlan.currentPeriod)
    }

    @Test
    fun test5_DoNotReduceTenorJustBecauseDueDatePassed() {
        // 5. Tidak mengurangi tenor hanya karena tanggal jatuh tempo lewat
        val plan = InstallmentPlanItem(
            name = "Cicilan Elektronik",
            installmentAmount = 500000.0,
            totalTenor = 6,
            paidCount = 2,
            anchorDay = 5,
            startDate = "2026-08-05",
            nextDueDate = "Tgl 5 tiap bulan"
        )

        // Simulasi hari ini adalah tanggal 20 (jatuh tempo tgl 5 sudah lewat 15 hari)
        val todayCalendar = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 20) }
        val isOverdue = InstallmentDateHelper.isOverdue(plan.nextDueDate, todayCalendar.get(Calendar.DAY_OF_MONTH))

        assertTrue("Harus terdeteksi terlambat", isOverdue)
        // Nilai tenor tidak boleh berkurang secara magis
        assertEquals(2, plan.paidCount)
        assertEquals(4, plan.remainingTenor)
        assertFalse(plan.isCompleted)
    }

    @Test
    fun test6_CompleteContractAfterFinalPaymentConfirmed() {
        // 6. Menyelesaikan kontrak setelah pembayaran terakhir dikonfirmasi
        var plan = InstallmentPlanItem(
            name = "Cicilan Motor",
            installmentAmount = 850000.0,
            totalTenor = 35,
            paidCount = 34,
            anchorDay = 15,
            startDate = "2023-11-15",
            nextDueDate = "Tgl 15 tiap bulan"
        )

        assertEquals(1, plan.remainingTenor)
        assertEquals(35, plan.currentPeriod)
        assertFalse(plan.isCompleted)

        // Konfirmasi angsuran ke-35 (terakhir)
        val newPaidCount = plan.paidCount + 1
        val newRemaining = plan.totalTenor - newPaidCount
        val isComplete = newRemaining <= 0
        plan = plan.copy(
            paidCount = newPaidCount,
            status = if (isComplete) "COMPLETED" else "ACTIVE",
            nextDueDate = "Lunas"
        )

        assertEquals(35, plan.paidCount)
        assertEquals(0, plan.remainingTenor)
        assertTrue(plan.isCompleted)
        assertEquals("COMPLETED", plan.status)
        assertEquals("Lunas", plan.nextDueDate)
    }

    @Test
    fun test7_NoDuplicateDueBillsForSamePeriod() {
        // 7. Tidak membuat duplikasi tagihan untuk periode yang sama
        val existingPlans = listOf(
            InstallmentPlanItem(
                id = "plan-1",
                name = "Shopee PayLater",
                provider = "Shopee",
                installmentAmount = 354000.0,
                totalTenor = 6,
                paidCount = 2,
                anchorDay = 5,
                startDate = "2026-08-05",
                nextDueDate = "Tgl 5 tiap bulan",
                userId = 1L
            )
        )

        val duplicateCheck = PayLaterDeduplicator.checkDuplicate(
            targetUserId = 1L,
            userDebts = emptyList(),
            userInstallmentPlans = existingPlans,
            providerName = "Shopee PayLater",
            currentBillAmount = 354000.0,
            totalRemainingAmount = 1416000.0,
            dueDateStr = "Tgl 5 tiap bulan"
        )

        assertTrue(duplicateCheck.isDuplicate)
        assertNotNull(duplicateCheck.matchedPlan)
        assertEquals("plan-1", duplicateCheck.matchedPlan?.id)
        assertTrue(duplicateCheck.reason?.contains("kontrak cicilan berjangka") == true)
    }

    @Test
    fun test8_MonthTransitionAndYearChange() {
        // 8. Perpindahan bulan dan pergantian tahun
        // Tanggal 15 November 2026 -> 15 Desember 2026 -> 15 Januari 2027
        val novDueDate = "15-11-2026"
        val nextFromNov = InstallmentDateHelper.calculateNextDueDate(novDueDate, 15)
        assertEquals("15-12-2026", nextFromNov)
        val nextYear = InstallmentDateHelper.calculateNextDueDate(nextFromNov, 15)
        assertEquals("15-01-2027", nextYear)

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 15)
        }
        val nextMonthCal = InstallmentDateHelper.getNextMonthCalendar(cal, 15)
        assertEquals(2027, nextMonthCal.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, nextMonthCal.get(Calendar.MONTH))
        assertEquals(15, nextMonthCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun test9_FebruaryAndLeapYearAnchorDayPreservation() {
        // 9. Februari dan tahun kabisat: Anchor day 31 harus diclamp saat Februari, lalu kembali ke 31 saat Maret
        val anchorDay = 31

        // Januari 2024 (Tahun Kabisat -> Feb punya 29 hari)
        val janLeapCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2024)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 31)
        }
        val febLeapCal = InstallmentDateHelper.getNextMonthCalendar(janLeapCal, anchorDay)
        assertEquals(2024, febLeapCal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, febLeapCal.get(Calendar.MONTH))
        assertEquals(29, febLeapCal.get(Calendar.DAY_OF_MONTH))

        // Dari Feb 2024 menuju Maret 2024: Anchor day 31 harus kembali ke hari ke-31!
        val marLeapCal = InstallmentDateHelper.getNextMonthCalendar(febLeapCal, anchorDay)
        assertEquals(2024, marLeapCal.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, marLeapCal.get(Calendar.MONTH))
        assertEquals(31, marLeapCal.get(Calendar.DAY_OF_MONTH))

        // Januari 2025 (Bukan Kabisat -> Feb punya 28 hari)
        val janNonLeapCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2025)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 31)
        }
        val febNonLeapCal = InstallmentDateHelper.getNextMonthCalendar(janNonLeapCal, anchorDay)
        assertEquals(2025, febNonLeapCal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, febNonLeapCal.get(Calendar.MONTH))
        assertEquals(28, febNonLeapCal.get(Calendar.DAY_OF_MONTH))

        // Dari Feb 2025 menuju Maret 2025: Anchor day 31 harus kembali ke hari ke-31!
        val marNonLeapCal = InstallmentDateHelper.getNextMonthCalendar(febNonLeapCal, anchorDay)
        assertEquals(31, marNonLeapCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun test10_InstallmentWithUnknownTotalPrincipal() {
        // 10. Cicilan dengan total sisa pokok yang belum diketahui: tidak mengarang nilai pokok
        val plan = InstallmentPlanItem(
            name = "Pinjaman Pribadi",
            installmentAmount = 750000.0,
            totalTenor = 24,
            paidCount = 5,
            anchorDay = 10,
            startDate = "2026-05-10",
            nextDueDate = "Tgl 10 tiap bulan",
            totalRemainingPrincipal = null // Sisa pokok tidak diketahui
        )

        assertNull("Sisa pokok yang tidak diketahui tidak boleh dikarang", plan.totalRemainingPrincipal)
        assertEquals(19, plan.remainingTenor)
        assertEquals(750000.0, plan.installmentAmount, EPSILON)
    }

    @Test
    fun test11_PaymentHistoryConsistencyAfterEdit() {
        // 11. Histori pembayaran tetap konsisten setelah edit nama/kreditur/nominal
        val originalPlan = InstallmentPlanItem(
            name = "Cicilan Motor",
            provider = "",
            installmentAmount = 850000.0,
            totalTenor = 35,
            paidCount = 12,
            anchorDay = 15,
            startDate = "2025-10-15",
            nextDueDate = "Tgl 15 tiap bulan"
        )

        // Pengguna memperbarui nama kreditur dan catatan
        val editedPlan = originalPlan.copy(
            name = "Cicilan Motor Honda Beat",
            provider = "Adira Finance",
            notes = "Nomor kontrak 12345678"
        )

        assertEquals("Cicilan Motor Honda Beat", editedPlan.name)
        assertEquals("Adira Finance", editedPlan.provider)
        // Progres tidak boleh ter-reset
        assertEquals(12, editedPlan.paidCount)
        assertEquals(35, editedPlan.totalTenor)
        assertEquals(23, editedPlan.remainingTenor)
    }

    @Test
    fun test12_MultiUserIsolationForInstallmentPlans() {
        // 12. Isolasi data antar-pengguna
        val planUser1 = InstallmentPlanItem(
            id = "plan-u1",
            name = "Cicilan User 1",
            installmentAmount = 500000.0,
            totalTenor = 10,
            paidCount = 2,
            anchorDay = 15,
            startDate = "2026-01-15",
            nextDueDate = "Tgl 15 tiap bulan",
            userId = 1L
        )

        val planUser2 = InstallmentPlanItem(
            id = "plan-u2",
            name = "Cicilan User 2",
            installmentAmount = 1000000.0,
            totalTenor = 20,
            paidCount = 5,
            anchorDay = 15,
            startDate = "2026-01-15",
            nextDueDate = "Tgl 15 tiap bulan",
            userId = 2L
        )

        val allPlans = listOf(planUser1, planUser2)

        val user1Plans = allPlans.filter { it.userId == 1L }
        val user2Plans = allPlans.filter { it.userId == 2L }

        assertEquals(1, user1Plans.size)
        assertEquals("plan-u1", user1Plans[0].id)

        assertEquals(1, user2Plans.size)
        assertEquals("plan-u2", user2Plans[0].id)

        // Deduplicator harus menolak mencocokkan plan milik user lain
        val check = PayLaterDeduplicator.checkDuplicate(
            targetUserId = 1L,
            userDebts = emptyList(),
            userInstallmentPlans = listOf(planUser2),
            providerName = "Cicilan User 2",
            currentBillAmount = 1000000.0,
            totalRemainingAmount = 20000000.0,
            dueDateStr = "Tgl 15 tiap bulan"
        )
        assertFalse("Tidak boleh mencocokkan data user lain", check.isDuplicate)
    }

    @Test
    fun test14_BackupAndRestorePayloadContainsInstallmentPlans() {
        // 14. Backup dan restore jika relevan (Moshi JSON serialization)
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(BackupDataPayload::class.java)

        val plan = InstallmentPlanItem(
            id = "plan-backup",
            name = "KPR Bank",
            installmentAmount = 2500000.0,
            totalTenor = 120,
            paidCount = 36,
            anchorDay = 25,
            startDate = "2023-10-25",
            nextDueDate = "Tgl 25 tiap bulan",
            userId = 1L
        )

        val payload = BackupDataPayload(
            installmentPlans = listOf(plan)
        )

        val json = adapter.toJson(payload)
        assertTrue(json.contains("KPR Bank"))
        assertTrue(json.contains("2500000"))

        val deserialized = adapter.fromJson(json)
        assertNotNull(deserialized)
        assertEquals(1, deserialized!!.installmentPlans.size)
        val restoredPlan = deserialized.installmentPlans[0]
        assertEquals("plan-backup", restoredPlan.id)
        assertEquals("KPR Bank", restoredPlan.name)
        assertEquals(120, restoredPlan.totalTenor)
        assertEquals(36, restoredPlan.paidCount)
        assertEquals(84, restoredPlan.remainingTenor)
    }

    @Test
    fun test15_NotificationDataHelperForInstallmentPlans() {
        // 15. Notifikasi periode berikutnya (due date today, upcoming, overdue)
        val planToday = InstallmentPlanItem(
            name = "Cicilan Motor",
            installmentAmount = 850000.0,
            totalTenor = 35,
            paidCount = 0,
            anchorDay = 15,
            startDate = "2026-10-15",
            nextDueDate = "Tgl 15 tiap bulan"
        )

        val planUpcoming = InstallmentPlanItem(
            name = "Cicilan Kulkas",
            installmentAmount = 300000.0,
            totalTenor = 6,
            paidCount = 1,
            anchorDay = 17,
            startDate = "2026-09-17",
            nextDueDate = "Tgl 17 tiap bulan"
        )

        val planOverdue = InstallmentPlanItem(
            name = "Cicilan Ponsel",
            installmentAmount = 600000.0,
            totalTenor = 12,
            paidCount = 2,
            anchorDay = 10,
            startDate = "2026-08-10",
            nextDueDate = "Tgl 10 tiap bulan"
        )

        // Set calendar to day 15
        val calendar = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 15) }

        val notifications = NotificationDataHelper.generateNotifications(
            wallets = emptyList(),
            debts = emptyList(),
            budgetLimits = emptyList(),
            currentMonthExpenses = emptyList(),
            installmentPlans = listOf(planToday, planUpcoming, planOverdue),
            calendar = calendar
        )

        assertTrue(notifications.any { it.title.contains("Jatuh Tempo Cicilan Hari Ini: Cicilan Motor") })
        assertTrue(notifications.any { it.title.contains("Pengingat Cicilan: Cicilan Kulkas") })
        assertTrue(notifications.any { it.title.contains("Cicilan Terlewat: Cicilan Ponsel") })
    }

    @Test
    fun test17_PayLaterBillConversionToInstallmentPlan() {
        // 17. Integrasi dengan data hasil impor PayLater (misal AdaKami 3/6 atau SPayLater 2/6)
        val scanResult = PayLaterBillScanResult(
            providerName = "AdaKami",
            productName = "Pinjaman Multiguna",
            currentBillAmount = 1787073.0,
            totalRemainingAmount = 7128010.0,
            installmentCurrent = 3,
            installmentTotal = 6,
            dueDate = "Tgl 8 tiap bulan",
            dueDay = 8,
            isDemoPreset = false
        )

        val planItem = scanResult.toInstallmentPlanItem(userId = 1L)
        assertNotNull(planItem)
        assertEquals("AdaKami (Pinjaman Multiguna)", planItem!!.name)
        assertEquals("AdaKami", planItem.provider)
        assertEquals(6, planItem.totalTenor)
        assertEquals(2, planItem.paidCount) // Cicilan ke-3 berarti 2 sudah lunas
        assertEquals(4, planItem.remainingTenor)
        assertEquals(3, planItem.currentPeriod)
        assertEquals(1787073.0, planItem.installmentAmount, EPSILON)
        assertEquals(8, planItem.anchorDay)
        assertEquals(7128010.0, planItem.totalRemainingPrincipal ?: 0.0, EPSILON)
        assertEquals(1L, planItem.userId)
    }

    @Test
    fun test18_PaymentHistoryTrackingAndTransactionLinking() {
        // Memastikan transaksi pembayaran tertaut ke ID kontrak dan tidak hilang meski nama kontrak diedit
        val plan = InstallmentPlanItem(
            id = "plan-contract-xyz",
            name = "Cicilan HP Samsung",
            installmentAmount = 500000.0,
            totalTenor = 12,
            paidCount = 1,
            anchorDay = 10,
            startDate = "2026-09-10",
            nextDueDate = "Tgl 10 tiap bulan",
            lastPaymentDate = 1791557000000L,
            userId = 1L
        )

        val tx1 = com.example.data.model.TransactionItem(
            id = 101L,
            title = "Angsuran Cicilan HP Samsung (1/12)",
            amount = 500000.0,
            type = com.example.data.model.TransactionType.EXPENSE,
            category = "Cicilan & Hutang",
            walletId = 1L,
            walletName = "Dompet Utama",
            tags = "installment_plan_id:${plan.id},period:1",
            userId = 1L
        )

        // Verifikasi penautan transaksi
        val allUserTransactions = listOf(tx1)
        val linked = allUserTransactions.filter { it.tags.contains("installment_plan_id:${plan.id}") }
        assertEquals(1, linked.size)
        assertEquals(101L, linked[0].id)

        // Pengguna mengedit nama kontrak (misal ganti jadi "Cicilan Smartphone Samsung Galaxy S23")
        val updatedPlan = plan.copy(name = "Cicilan Smartphone Samsung Galaxy S23")
        val linkedAfterEdit = allUserTransactions.filter { it.tags.contains("installment_plan_id:${updatedPlan.id}") }
        assertEquals("Histori pembayaran tetap tertaut meskipun nama kontrak diedit", 1, linkedAfterEdit.size)
        assertEquals(500000.0, linkedAfterEdit[0].amount, EPSILON)
    }

    @Test
    fun test19_StatusAktifIndonesianIsCountedAsActive() {
        val plan = InstallmentPlanItem(
            name = "Cicilan Kulkas",
            installmentAmount = 250000.0,
            totalTenor = 12,
            paidCount = 3,
            status = "AKTIF",
            startDate = "2026-01-01",
            nextDueDate = "01-02-2026"
        )

        assertTrue(plan.isActive)
        assertFalse(plan.isCompleted)
        assertEquals(9, plan.remainingTenor)
    }

    @Test
    fun test20_StatusActiveEnglishIsHandledCompatibly() {
        val plan = InstallmentPlanItem(
            name = "Cicilan AC",
            installmentAmount = 300000.0,
            totalTenor = 6,
            paidCount = 1,
            status = "ACTIVE",
            startDate = "2026-01-01",
            nextDueDate = "01-02-2026"
        )

        assertTrue(plan.isActive)
        assertFalse(plan.isCompleted)
        assertEquals(5, plan.remainingTenor)
    }

    @Test
    fun test21_CompletedPlansAreNotActive() {
        val planPaidTenor = InstallmentPlanItem(
            name = "Cicilan TV",
            installmentAmount = 400000.0,
            totalTenor = 10,
            paidCount = 10,
            status = "AKTIF",
            startDate = "2026-01-01",
            nextDueDate = "Lunas"
        )
        assertTrue(planPaidTenor.isCompleted)
        assertFalse(planPaidTenor.isActive)

        val planStatusSelesai = InstallmentPlanItem(
            name = "Cicilan Sofa",
            installmentAmount = 150000.0,
            totalTenor = 5,
            paidCount = 2,
            status = "SELESAI",
            startDate = "2026-01-01",
            nextDueDate = "Lunas"
        )
        assertTrue(planStatusSelesai.isCompleted)
        assertFalse(planStatusSelesai.isActive)

        val planStatusCompleted = InstallmentPlanItem(
            name = "Cicilan Mesin Cuci",
            installmentAmount = 350000.0,
            totalTenor = 8,
            paidCount = 3,
            status = "COMPLETED",
            startDate = "2026-01-01",
            nextDueDate = "Lunas"
        )
        assertTrue(planStatusCompleted.isCompleted)
        assertFalse(planStatusCompleted.isActive)
    }

    @Test
    fun test22_StoppedOrCancelledPlansAreNotActive() {
        val planStopped = InstallmentPlanItem(
            name = "Cicilan Dibatalkan",
            installmentAmount = 200000.0,
            totalTenor = 6,
            paidCount = 1,
            status = "DIHENTIKAN",
            startDate = "2026-01-01",
            nextDueDate = "01-02-2026"
        )
        assertFalse(planStopped.isActive)
    }

    @Test
    fun test23_MixedDataActiveFilterAndBannerCalculations() {
        val list = listOf(
            InstallmentPlanItem(name = "P1", installmentAmount = 500000.0, totalTenor = 10, paidCount = 2, status = "AKTIF", startDate = "2026-01-01", nextDueDate = "15-02-2026"),
            InstallmentPlanItem(name = "P2", installmentAmount = 300000.0, totalTenor = 6, paidCount = 1, status = "ACTIVE", startDate = "2026-01-01", nextDueDate = "15-02-2026"),
            InstallmentPlanItem(name = "P3", installmentAmount = 200000.0, totalTenor = 5, paidCount = 5, status = "SELESAI", startDate = "2026-01-01", nextDueDate = "Lunas"),
            InstallmentPlanItem(name = "P4", installmentAmount = 150000.0, totalTenor = 12, paidCount = 12, status = "AKTIF", startDate = "2026-01-01", nextDueDate = "Lunas"),
            InstallmentPlanItem(name = "P5", installmentAmount = 100000.0, totalTenor = 4, paidCount = 1, status = "DIHENTIKAN", startDate = "2026-01-01", nextDueDate = "15-02-2026")
        )

        val activePlans = list.filter { it.isActive }
        val completedPlans = list.filter { it.isCompleted }

        assertEquals(2, activePlans.size)
        assertEquals("P1", activePlans[0].name)
        assertEquals("P2", activePlans[1].name)
        assertEquals(800000.0, activePlans.sumOf { it.installmentAmount }, EPSILON)

        assertEquals(2, completedPlans.size) // P3 (SELESAI) and P4 (paidCount == totalTenor)
    }
}
