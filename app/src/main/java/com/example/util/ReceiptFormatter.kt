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

    fun formatReceiptReference(id: Long): String {
        return "FSM-${id.toString().padStart(6, '0')}"
    }

    fun formatTransactionDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", LocaleHelper.getActiveLocale())
        return sdf.format(Date(timestamp))
    }

    fun getTransactionTypeLabel(type: TransactionType): String {
        return when (type) {
            TransactionType.EXPENSE -> AppStrings.t("PENGELUARAN", "EXPENSE")
            TransactionType.INCOME -> AppStrings.t("PEMASUKAN", "INCOME")
            TransactionType.TRANSFER -> AppStrings.t("TRANSFER ANTAR DOMPET", "WALLET TRANSFER")
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
        sb.append(centerText(AppStrings.t("BUKTI TRANSAKSI DIGITAL", "DIGITAL TRANSACTION RECEIPT"), width)).append("\n")
        sb.append(centerText("DOMPETNALAR PERSONAL FINANCE", width)).append("\n")
        sb.append(divider).append("\n")

        sb.append(formatKeyValue(AppStrings.t("No. Bukti", "Receipt No"), reference, width)).append("\n")
        sb.append(formatKeyValue(AppStrings.t("Tanggal", "Date"), dateStr, width)).append("\n")
        sb.append(formatKeyValue(AppStrings.t("Jenis", "Type"), typeLabel, width)).append("\n")
        sb.append(divider).append("\n")

        sb.append(formatKeyValue(AppStrings.t("Deskripsi", "Description"), transaction.title, width)).append("\n")
        sb.append(formatKeyValue(AppStrings.t("Kategori", "Category"), AppStrings.translateCategory(transaction.category), width)).append("\n")

        if (transaction.type == TransactionType.TRANSFER) {
            val target = transaction.targetWalletName ?: AppStrings.t("Dompet Tujuan", "Target Wallet")
            sb.append(formatKeyValue(AppStrings.t("Dari", "From"), transaction.walletName, width)).append("\n")
            sb.append(formatKeyValue(AppStrings.t("Ke", "To"), target, width)).append("\n")
        } else {
            sb.append(formatKeyValue(AppStrings.t("Sumber Dana", "Source Account"), transaction.walletName, width)).append("\n")
        }

        if (transaction.note.isNotBlank()) {
            sb.append(formatKeyValue(AppStrings.t("Catatan", "Notes"), transaction.note, width)).append("\n")
        }

        sb.append(divider).append("\n")
        val totalLabel = when (transaction.type) {
            TransactionType.EXPENSE -> AppStrings.t("TOTAL KELUAR", "TOTAL EXPENSE")
            TransactionType.INCOME -> AppStrings.t("TOTAL MASUK", "TOTAL INCOME")
            TransactionType.TRANSFER -> AppStrings.t("TOTAL TRANSFER", "TOTAL TRANSFER")
        }
        sb.append(formatKeyValue(totalLabel, amountStr, width)).append("\n")
        sb.append(divider).append("\n")

        sb.append(centerText(AppStrings.t("Simpan bukti ini sebagai referensi", "Keep this receipt as reference"), width)).append("\n")
        sb.append(centerText(AppStrings.t("pencatatan keuangan Anda.", "for your financial records."), width)).append("\n")
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
