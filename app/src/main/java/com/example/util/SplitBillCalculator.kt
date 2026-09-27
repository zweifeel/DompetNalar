package com.example.util

import com.example.data.model.PersonBillBreakdown
import com.example.data.model.ReceiptItem
import com.example.data.model.ReceiptScanResult
import com.example.data.model.SplitBillPerson
import com.example.data.model.SplitBillResult
import java.util.Locale

object SplitBillCalculator {

    fun calculateSplitBill(
        receipt: ReceiptScanResult,
        people: List<SplitBillPerson>,
        items: List<ReceiptItem>,
        paymentTargetInfo: String = ""
    ): SplitBillResult {
        val peopleMap = people.associateBy { it.id }

        // Track items assigned per person and item subtotal per person
        val personItemsMap = mutableMapOf<String, MutableList<ReceiptItem>>()
        val personSubtotalMap = mutableMapOf<String, Double>()
        people.forEach { p ->
            personItemsMap[p.id] = mutableListOf()
            personSubtotalMap[p.id] = 0.0
        }

        val unassignedItems = mutableListOf<ReceiptItem>()

        items.forEach { item ->
            val assignedIds = item.assignedPersonIds.filter { peopleMap.containsKey(it) }
            if (assignedIds.isEmpty()) {
                unassignedItems.add(item)
            } else {
                val sharePerPerson = item.total / assignedIds.size.toDouble()
                assignedIds.forEach { personId ->
                    personItemsMap[personId]?.add(item)
                    val currentSub = personSubtotalMap[personId] ?: 0.0
                    personSubtotalMap[personId] = currentSub + sharePerPerson
                }
            }
        }

        val baseSubtotal = if (receipt.subtotal > 0) receipt.subtotal else items.sumOf { it.total }

        val breakdowns = people.map { person ->
            val pSubtotal = personSubtotalMap[person.id] ?: 0.0
            val ratio = if (baseSubtotal > 0) pSubtotal / baseSubtotal else if (people.isNotEmpty()) 1.0 / people.size else 0.0

            val taxShare = receipt.tax * ratio
            val serviceShare = receipt.serviceCharge * ratio
            val discountShare = receipt.discount * ratio
            val totalToPay = (pSubtotal + taxShare + serviceShare - discountShare).coerceAtLeast(0.0)

            PersonBillBreakdown(
                person = person,
                assignedItems = personItemsMap[person.id] ?: emptyList(),
                itemsSubtotal = pSubtotal,
                taxShare = taxShare,
                serviceShare = serviceShare,
                discountShare = discountShare,
                totalToPay = totalToPay
            )
        }

        val calculatedGrandTotal = if (receipt.totalAmount > 0) {
            receipt.totalAmount
        } else {
            (baseSubtotal + receipt.tax + receipt.serviceCharge - receipt.discount).coerceAtLeast(0.0)
        }

        return SplitBillResult(
            merchantName = receipt.merchantName.ifBlank { "Struk Belanja" },
            date = receipt.date,
            grandTotal = calculatedGrandTotal,
            breakdowns = breakdowns,
            unassignedItems = unassignedItems,
            paymentTargetInfo = paymentTargetInfo.ifBlank { "Rekening / Dompet Penyelenggara" }
        )
    }

    fun generateWhatsAppSummary(splitResult: SplitBillResult): String {
        val sb = StringBuilder()
        fun formatRp(amount: Double): String = "Rp" + String.format(Locale("id", "ID"), "%,d", amount.toLong()).replace(",", ".")

        sb.append("🧾 *RINCIAN SPLIT BILL - ${splitResult.merchantName.uppercase()}*\n")
        if (splitResult.date.isNotBlank()) {
            sb.append("📅 *Tanggal:* ${splitResult.date}\n")
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━\n\n")

        splitResult.breakdowns.forEach { breakdown ->
            sb.append("👤 *${breakdown.person.name}* ➔ *${formatRp(breakdown.totalToPay)}*\n")
            if (breakdown.assignedItems.isNotEmpty()) {
                breakdown.assignedItems.forEach { item ->
                    val isShared = item.assignedPersonIds.size > 1
                    val sharedNote = if (isShared) " _(dibagi ${item.assignedPersonIds.size} orang)_" else ""
                    val itemPriceShare = if (isShared) item.total / item.assignedPersonIds.size else item.total
                    sb.append("   • ${item.qty}x ${item.name}: ${formatRp(itemPriceShare)}$sharedNote\n")
                }
            } else {
                sb.append("   • _(Belum memilih item pesanan)_\n")
            }

            val extras = mutableListOf<String>()
            if (breakdown.taxShare > 0) extras.add("PB1/Pajak: ${formatRp(breakdown.taxShare)}")
            if (breakdown.serviceShare > 0) extras.add("Service: ${formatRp(breakdown.serviceShare)}")
            if (breakdown.discountShare > 0) extras.add("Diskon: -${formatRp(breakdown.discountShare)}")

            if (extras.isNotEmpty()) {
                sb.append("   └ _${extras.joinToString(", ")}_\n")
            }
            sb.append("\n")
        }

        if (splitResult.unassignedItems.isNotEmpty()) {
            val unassignedSum = splitResult.unassignedItems.sumOf { it.total }
            sb.append("⚠️ *Item Belum Dibagi (${formatRp(unassignedSum)}):*\n")
            splitResult.unassignedItems.forEach { item ->
                sb.append("   • ${item.qty}x ${item.name}: ${formatRp(item.total)}\n")
            }
            sb.append("\n")
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("💰 *Total Tagihan Keseluruhan:* *${formatRp(splitResult.grandTotal)}*\n\n")
        sb.append("💳 *Transfer Pembayaran ke Penyelenggara:*\n")
        sb.append("👉 *${splitResult.paymentTargetInfo}*\n\n")
        sb.append("⚡ _Dihitung & Dibagi Otomatis via FinSmart AI_")

        return sb.toString()
    }
}
