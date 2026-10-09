package com.example.data.model

import java.util.UUID

data class ReceiptItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val qty: Int = 1,
    val price: Double = 0.0,
    val total: Double = price * qty,
    val assignedPersonIds: List<String> = emptyList()
)

enum class TaxTreatment {
    INCLUSIVE,  // Pajak sudah termasuk dalam harga / subtotal (contoh: PPN ritel minimarket/supermarket)
    EXCLUSIVE,  // Pajak ditambahkan di atas subtotal (contoh: PB1 / Pajak Restoran 10%)
    UNKNOWN     // Belum dapat dipastikan, pertahankan total OCR dan minta pemeriksaan pengguna
}

data class ReceiptScanResult(
    val id: String = UUID.randomUUID().toString(),
    val merchantName: String = "",
    val date: String = "",
    val category: String = "Makanan & Minuman",
    val categoryIcon: String = "restaurant",
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val serviceCharge: Double = 0.0,
    val discount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val taxTreatment: TaxTreatment = TaxTreatment.UNKNOWN,
    val items: List<ReceiptItem> = emptyList(),
    val imageUri: String? = null,
    val rawText: String? = null
)

data class SplitBillPerson(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#2563EB"
)

data class PersonBillBreakdown(
    val person: SplitBillPerson,
    val assignedItems: List<ReceiptItem>,
    val itemsSubtotal: Double,
    val taxShare: Double,
    val serviceShare: Double,
    val discountShare: Double,
    val totalToPay: Double
)

data class SplitBillResult(
    val merchantName: String,
    val date: String,
    val grandTotal: Double,
    val breakdowns: List<PersonBillBreakdown>,
    val unassignedItems: List<ReceiptItem>,
    val paymentTargetInfo: String = "Rekening / Dompet Penyelenggara"
)
