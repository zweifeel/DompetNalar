package com.example.util

import com.example.data.model.PersonBillBreakdown
import com.example.data.model.ReceiptItem
import com.example.data.model.ReceiptScanResult
import com.example.data.model.SplitBillPerson
import com.example.data.model.SplitBillResult
import com.example.data.model.TaxTreatment
import java.util.Locale

data class SimpleSplitResult(
    val grandTotal: Double,
    val perPersonAmount: List<Double>,
)

object SplitBillCalculator {

    fun calculateSplit(
        totalAmount: Double = 0.0,
        taxAmount: Double = 0.0,
        serviceFee: Double = 0.0,
        discountAmount: Double = 0.0,
        itemPrices: List<Double> = emptyList(),
    ): SimpleSplitResult {
        val subtotal = if (totalAmount > 0) totalAmount else itemPrices.sum()
        val netSurcharge = (taxAmount + serviceFee) - discountAmount
        val calculatedGrandTotal = (subtotal + netSurcharge).coerceAtLeast(0.0)

        val count = itemPrices.size.coerceAtLeast(1)
        val ratio = 1.0 / count
        val perPerson = itemPrices.map { price ->
            val taxShare = taxAmount * ratio
            val serviceShare = serviceFee * ratio
            val discountShare = discountAmount * ratio
            ((price + taxShare + serviceShare) - discountShare).coerceAtLeast(0.0)
        }

        return SimpleSplitResult(
            grandTotal = calculatedGrandTotal,
            perPersonAmount = perPerson,
        )
    }

    fun calculateSplitBill(
        receipt: ReceiptScanResult,
        people: List<SplitBillPerson>,
        items: List<ReceiptItem>,
        paymentTargetInfo: String = "",
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
        val isTaxInclusive = receipt.taxTreatment == TaxTreatment.INCLUSIVE

        val breakdowns = people.map { person ->
            val pSubtotal = personSubtotalMap[person.id] ?: 0.0
            val ratio = if (baseSubtotal > 0) pSubtotal / baseSubtotal else if (people.isNotEmpty()) 1.0 / people.size else 0.0

            val taxShare = if (isTaxInclusive) 0.0 else receipt.tax * ratio
            val serviceShare = receipt.serviceCharge * ratio
            val discountShare = receipt.discount * ratio
            val totalToPay = ((pSubtotal + taxShare + serviceShare) - discountShare).coerceAtLeast(0.0)

            PersonBillBreakdown(
                person = person,
                assignedItems = personItemsMap[person.id] ?: emptyList(),
                itemsSubtotal = pSubtotal,
                taxShare = taxShare,
                serviceShare = serviceShare,
                discountShare = discountShare,
                totalToPay = totalToPay,
            )
        }

        val calculatedGrandTotal = if (receipt.totalAmount > 0) {
            receipt.totalAmount
        } else {
            val taxToAdd = if (isTaxInclusive) 0.0 else receipt.tax
            ((baseSubtotal + taxToAdd + receipt.serviceCharge) - receipt.discount).coerceAtLeast(0.0)
        }

        return SplitBillResult(
            merchantName = receipt.merchantName.ifBlank { "Struk Belanja" },
            date = receipt.date,
            grandTotal = calculatedGrandTotal,
            breakdowns = breakdowns,
            unassignedItems = unassignedItems,
            paymentTargetInfo = paymentTargetInfo.ifBlank { "Rekening / Dompet Penyelenggara" },
        )
    }

    fun generateWhatsAppSummary(splitResult: SplitBillResult): String {
        val sb = StringBuilder()
        fun formatRp(amount: Double): String = "Rp" + String.format(Locale.forLanguageTag("id-ID"), "%,d", amount.toLong()).replace(",", ".")

        val headerTitle = AppStrings.t("RINCIAN SPLIT BILL", "SPLIT BILL BREAKDOWN")
        val dateLabel = AppStrings.t("Tanggal", "Date")
        sb.append("🧾 *$headerTitle - ${splitResult.merchantName.uppercase()}*\n")
        if (splitResult.date.isNotBlank()) {
            sb.append("📅 *$dateLabel:* ${splitResult.date}\n")
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━\n\n")

        val dividedText = AppStrings.t("dibagi", "split among")
        val peopleText = AppStrings.t("orang", "people")
        val noItemText = AppStrings.t("Belum memilih item pesanan", "No items selected yet")
        val taxText = AppStrings.t("PB1/Pajak", "Tax")
        val serviceText = AppStrings.t("Service", "Service")
        val discountText = AppStrings.t("Diskon", "Discount")

        splitResult.breakdowns.forEach { breakdown ->
            sb.append("👤 *${breakdown.person.name}* ➔ *${formatRp(breakdown.totalToPay)}*\n")
            if (breakdown.assignedItems.isNotEmpty()) {
                breakdown.assignedItems.forEach { item ->
                    val isShared = item.assignedPersonIds.size > 1
                    val sharedNote = if (isShared) " _($dividedText ${item.assignedPersonIds.size} $peopleText)_" else ""
                    val itemPriceShare = if (isShared) item.total / item.assignedPersonIds.size else item.total
                    sb.append("   • ${item.qty}x ${item.name}: ${formatRp(itemPriceShare)}$sharedNote\n")
                }
            } else {
                sb.append("   • _($noItemText)_\n")
            }

            val extras = mutableListOf<String>()
            if (breakdown.taxShare > 0) extras.add("$taxText: ${formatRp(breakdown.taxShare)}")
            if (breakdown.serviceShare > 0) extras.add("$serviceText: ${formatRp(breakdown.serviceShare)}")
            if (breakdown.discountShare > 0) extras.add("$discountText: -${formatRp(breakdown.discountShare)}")

            if (extras.isNotEmpty()) {
                sb.append("   └ _${extras.joinToString(", ")}_\n")
            }
            sb.append("\n")
        }

        if (splitResult.unassignedItems.isNotEmpty()) {
            val unassignedSum = splitResult.unassignedItems.sumOf { it.total }
            val unassignedLabel = AppStrings.t("Item Belum Dibagi", "Unassigned Items")
            sb.append("⚠️ *$unassignedLabel (${formatRp(unassignedSum)}):*\n")
            splitResult.unassignedItems.forEach { item ->
                sb.append("   • ${item.qty}x ${item.name}: ${formatRp(item.total)}\n")
            }
            sb.append("\n")
        }

        val totalLabel = AppStrings.t("Total Tagihan Keseluruhan", "Grand Total Bill")
        val transferLabel = AppStrings.t("Transfer Pembayaran ke Penyelenggara", "Payment Transfer to Organizer")
        val footerLabel = AppStrings.t("Dihitung & Dibagi Otomatis via DompetNalar AI", "Automatically calculated & split via DompetNalar AI")

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("💰 *$totalLabel:* *${formatRp(splitResult.grandTotal)}*\n\n")
        sb.append("💳 *$transferLabel:*\n")
        sb.append("👉 *${splitResult.paymentTargetInfo}*\n\n")
        sb.append("⚡ _${footerLabel}_")

        return sb.toString()
    }
}
