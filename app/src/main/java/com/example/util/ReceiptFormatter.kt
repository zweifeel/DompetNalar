package com.example.util

import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility for formatting personal finance transactions into deterministic,
 * human-readable digital receipts for FEAT-34 (Phase A).
 */
object ReceiptFormatter {

    private val LOCALE_ID = Locale("id", "ID")

    fun formatReceiptReference(id: Long): String {
        return "FSM-${id.toString().padStart(6, '0')}"
    }

    fun formatTransactionDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", LOCALE_ID)
        return sdf.format(Date(timestamp))
    }

    fun getTransactionTypeLabel(type: TransactionType): String {
        return when (type) {
            TransactionType.EXPENSE -> "PENGELUARAN"
            TransactionType.INCOME -> "PEMASUKAN"
            TransactionType.TRANSFER -> "TRANSFER ANTAR DOMPET"
        }
    }

    /**
     * Formats a TransactionItem into a monospace ASCII-style receipt text string,
     * suitable for sharing via Intent.ACTION_SEND, copying to clipboard, or thermal preview.
     *
     * @param transaction The real transaction item.
     * @param is80mm If true, formats for 80mm paper width (42 columns); otherwise 58mm (32 columns).
     */
    fun formatReceiptText(transaction: TransactionItem, is80mm: Boolean = false): String {
        val width = if (is80mm) 42 else 32
        val divider = "-".repeat(width)

        val reference = formatReceiptReference(transaction.id)
        val dateStr = formatTransactionDate(transaction.timestamp)
        val typeLabel = getTransactionTypeLabel(transaction.type)
        val amountStr = CurrencyFormatter.formatRupiah(transaction.amount)

        val sb = StringBuilder()
        sb.append(centerText("BUKTI TRANSAKSI DIGITAL", width)).append("\n")
        sb.append(centerText("DOMPETNALAR PERSONAL FINANCE", width)).append("\n")
        sb.append(divider).append("\n")

        sb.append(formatKeyValue("No. Bukti", reference, width)).append("\n")
        sb.append(formatKeyValue("Tanggal", dateStr, width)).append("\n")
        sb.append(formatKeyValue("Jenis", typeLabel, width)).append("\n")
        sb.append(divider).append("\n")

        sb.append(formatKeyValue("Deskripsi", transaction.title, width)).append("\n")
        sb.append(formatKeyValue("Kategori", transaction.category, width)).append("\n")

        if (transaction.type == TransactionType.TRANSFER) {
            val target = transaction.targetWalletName ?: "Dompet Tujuan"
            sb.append(formatKeyValue("Dari", transaction.walletName, width)).append("\n")
            sb.append(formatKeyValue("Ke", target, width)).append("\n")
        } else {
            sb.append(formatKeyValue("Sumber Dana", transaction.walletName, width)).append("\n")
        }

        if (transaction.note.isNotBlank()) {
            sb.append(formatKeyValue("Catatan", transaction.note, width)).append("\n")
        }

        sb.append(divider).append("\n")
        val totalLabel = when (transaction.type) {
            TransactionType.EXPENSE -> "TOTAL KELUAR"
            TransactionType.INCOME -> "TOTAL MASUK"
            TransactionType.TRANSFER -> "TOTAL TRANSFER"
        }
        sb.append(formatKeyValue(totalLabel, amountStr, width)).append("\n")
        sb.append(divider).append("\n")

        sb.append(centerText("Simpan bukti ini sebagai referensi", width)).append("\n")
        sb.append(centerText("pencatatan keuangan Anda.", width)).append("\n")
        sb.append(centerText("--- DompetNalar App ---", width))

        return sb.toString()
    }

    private fun centerText(text: String, width: Int): String {
        if (text.length >= width) return text
        val padding = (width - text.length) / 2
        return " ".repeat(padding) + text
    }

    private fun formatKeyValue(key: String, value: String, width: Int): String {
        val label = "$key:"
        val availableForValue = width - label.length - 1
        return if (availableForValue > 0 && value.length <= availableForValue) {
            val spaces = " ".repeat(width - label.length - value.length)
            "$label$spaces$value"
        } else {
            "$label $value"
        }
    }
}
