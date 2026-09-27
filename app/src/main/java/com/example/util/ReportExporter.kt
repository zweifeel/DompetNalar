package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    fun exportToPdf(
        context: Context,
        transactions: List<TransactionItem>,
        periodTitle: String,
        username: String
    ): File {
        val pdfDocument = PdfDocument()

        val a4Width = 595
        val a4Height = 842
        val rowHeight = 35
        val headerHeight = 220
        val footerHeight = 80

        val requiredHeight = headerHeight + (transactions.size * rowHeight) + footerHeight
        val pageHeight = if (requiredHeight > a4Height) requiredHeight else a4Height

        val pageInfo = PdfDocument.PageInfo.Builder(a4Width, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Paints
        val primaryPaint = Paint().apply {
            color = 0xFF00897B.toInt() // FinSmart Primary Teal
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textWhiteBold = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val textWhiteSmall = Paint().apply {
            color = 0xFFE0F2F1.toInt()
            textSize = 10f
            isAntiAlias = true
        }

        val textBlackBold = Paint().apply {
            color = 0xFF212121.toInt()
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val textBlackNormal = Paint().apply {
            color = 0xFF424242.toInt()
            textSize = 10f
            isAntiAlias = true
        }

        val textGray = Paint().apply {
            color = 0xFF757575.toInt()
            textSize = 9f
            isAntiAlias = true
        }

        val incomePaint = Paint().apply {
            color = 0xFF2E7D32.toInt() // Green
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val expensePaint = Paint().apply {
            color = 0xFFC62828.toInt() // Red
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val transferPaint = Paint().apply {
            color = 0xFF1565C0.toInt() // Blue
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val dividerPaint = Paint().apply {
            color = 0xFFE0E0E0.toInt()
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        // 1. Header Band
        canvas.drawRoundRect(RectF(20f, 20f, 575f, 110f), 12f, 12f, primaryPaint)
        canvas.drawText("Laporan Keuangan FinSmart", 40f, 55f, textWhiteBold)
        canvas.drawText("Konsultan Finansial & Manajemen Mutasi Mandiri", 40f, 75f, textWhiteSmall)
        canvas.drawText("Periode: $periodTitle  |  Pengguna: $username", 40f, 92f, textWhiteSmall)

        // 2. Calculations
        val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val netBalance = totalIncome - totalExpense

        // 3. Summary Cards
        // Income Box (Light Green)
        val bgIncome = Paint().apply { color = 0xFFE8F5E9.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
        canvas.drawRoundRect(RectF(20f, 125f, 195f, 180f), 8f, 8f, bgIncome)
        canvas.drawText("TOTAL PEMASUKAN", 30f, 143f, Paint(textGray).apply { textSize = 8f; isFakeBoldText = true })
        canvas.drawText(CurrencyFormatter.formatRupiah(totalIncome), 30f, 168f, Paint(incomePaint).apply { textSize = 12f })

        // Expense Box (Light Red)
        val bgExpense = Paint().apply { color = 0xFFFFEBEE.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
        canvas.drawRoundRect(RectF(210f, 125f, 385f, 180f), 8f, 8f, bgExpense)
        canvas.drawText("TOTAL PENGELUARAN", 220f, 143f, Paint(textGray).apply { textSize = 8f; isFakeBoldText = true })
        canvas.drawText(CurrencyFormatter.formatRupiah(totalExpense), 220f, 168f, Paint(expensePaint).apply { textSize = 12f })

        // Net Box (Light Teal/Blue)
        val bgNet = Paint().apply { color = if (netBalance >= 0) 0xFFE0F2F1.toInt() else 0xFFFCE4EC.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
        canvas.drawRoundRect(RectF(400f, 125f, 575f, 180f), 8f, 8f, bgNet)
        canvas.drawText("SELISIH (NET WORTH)", 410f, 143f, Paint(textGray).apply { textSize = 8f; isFakeBoldText = true })
        canvas.drawText(
            CurrencyFormatter.formatRupiah(netBalance),
            410f,
            168f,
            Paint(if (netBalance >= 0) incomePaint else expensePaint).apply { textSize = 12f }
        )

        // 4. Table Header
        val tableHeaderY = 205f
        canvas.drawText("TANGGAL", 30f, tableHeaderY, textBlackBold)
        canvas.drawText("JUDUL / DESKRIPSI", 115f, tableHeaderY, textBlackBold)
        canvas.drawText("KATEGORI", 270f, tableHeaderY, textBlackBold)
        canvas.drawText("DOMPET", 390f, tableHeaderY, textBlackBold)
        canvas.drawText("NOMINAL", 490f, tableHeaderY, textBlackBold)
        canvas.drawLine(20f, tableHeaderY + 8f, 575f, tableHeaderY + 8f, dividerPaint)

        // 5. Table Rows
        var currentY = tableHeaderY + 14f
        val zebraPaint = Paint().apply { color = 0xFFF9F9F9.toInt(); style = Paint.Style.FILL }

        transactions.forEachIndexed { index, tx ->
            // Zebra Background
            if (index % 2 == 1) {
                canvas.drawRect(20f, currentY, 575f, currentY + rowHeight, zebraPaint)
            }

            // Values
            val dateStr = CurrencyFormatter.formatDate(tx.timestamp, "dd/MM/yyyy")
            val amountStr = when (tx.type) {
                TransactionType.INCOME -> "+" + CurrencyFormatter.formatRupiah(tx.amount)
                TransactionType.EXPENSE -> "-" + CurrencyFormatter.formatRupiah(tx.amount)
                TransactionType.TRANSFER -> "→ " + CurrencyFormatter.formatRupiah(tx.amount)
            }
            val amountPaint = when (tx.type) {
                TransactionType.INCOME -> incomePaint
                TransactionType.EXPENSE -> expensePaint
                TransactionType.TRANSFER -> transferPaint
            }

            val textY = currentY + 21f
            canvas.drawText(dateStr, 30f, textY, textBlackNormal)

            // Title (Truncate if too long)
            val titleText = if (tx.title.length > 22) tx.title.take(20) + ".." else tx.title
            canvas.drawText(titleText, 115f, textY, textBlackNormal)

            // Category
            val catText = if (tx.category.length > 18) tx.category.take(16) + ".." else tx.category
            canvas.drawText(catText, 270f, textY, textBlackNormal)

            // Wallet name
            val walletText = if (tx.type == TransactionType.TRANSFER) {
                "${tx.walletName} → ${tx.targetWalletName ?: ""}"
            } else {
                tx.walletName
            }
            val displayWallet = if (walletText.length > 18) walletText.take(16) + ".." else walletText
            canvas.drawText(displayWallet, 390f, textY, textGray)

            // Amount
            canvas.drawText(amountStr, 490f, textY, amountPaint)

            canvas.drawLine(20f, currentY + rowHeight, 575f, currentY + rowHeight, dividerPaint)
            currentY += rowHeight
        }

        // 6. Footer section
        val printDateStr = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("id", "ID")).format(Date())
        canvas.drawText(
            "Dokumen laporan resmi diekspor otomatis via FinSmart pada $printDateStr.",
            30f,
            pageHeight - 45f,
            textGray
        )
        canvas.drawText(
            "© 2026 Lukas • FinSmart Kelola Keuangan Cerdas. Seluruh data dienkripsi lokal.",
            30f,
            pageHeight - 30f,
            textGray
        )

        pdfDocument.finishPage(page)

        // Save PDF File
        val exportDir = File(context.cacheDir, "reports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val sdfFilename = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val pdfFile = File(exportDir, "FinSmart_Laporan_$sdfFilename.pdf")
        val fileOutputStream = FileOutputStream(pdfFile)
        pdfDocument.writeTo(fileOutputStream)
        pdfDocument.close()
        fileOutputStream.close()

        return pdfFile
    }

    fun exportToCsv(
        context: Context,
        transactions: List<TransactionItem>
    ): File {
        val exportDir = File(context.cacheDir, "reports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val sdfFilename = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val csvFile = File(exportDir, "FinSmart_Laporan_$sdfFilename.csv")
        val fileOutputStream = FileOutputStream(csvFile)

        // Write UTF-8 BOM to make Excel recognize it as UTF-8 immediately
        fileOutputStream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

        val writer = csvFile.bufferedWriter()
        
        // Write header
        writer.write("ID,Tanggal,Tipe,Judul,Kategori,Sumber Dompet,Tujuan Dompet,Nominal,Catatan,Tag,Mode\n")

        transactions.forEach { tx ->
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(tx.timestamp))
            
            // Escape CSV values
            val titleEscaped = escapeCsvField(tx.title)
            val categoryEscaped = escapeCsvField(tx.category)
            val walletEscaped = escapeCsvField(tx.walletName)
            val targetWalletEscaped = escapeCsvField(tx.targetWalletName ?: "")
            val noteEscaped = escapeCsvField(tx.note)
            val tagsEscaped = escapeCsvField(tx.tags)

            writer.write(
                "${tx.id},\"$dateStr\",\"${tx.type.name}\",\"$titleEscaped\",\"$categoryEscaped\"," +
                        "\"$walletEscaped\",\"$targetWalletEscaped\",${tx.amount},\"$noteEscaped\",\"$tagsEscaped\",\"${tx.accountMode}\"\n"
            )
        }

        writer.flush()
        writer.close()
        fileOutputStream.close()

        return csvFile
    }

    private fun escapeCsvField(field: String): String {
        return field.replace("\"", "\"\"")
    }

    fun saveToPublicDownloads(context: Context, cachedFile: File): File? {
        return try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = File(downloadsDir, cachedFile.name)
            cachedFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            // Fallback to app's external files downloads directory (no-permission fallback)
            try {
                val extDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (extDir != null && !extDir.exists()) extDir.mkdirs()
                val destFile = File(extDir, cachedFile.name)
                cachedFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                destFile
            } catch (ex: Exception) {
                null
            }
        }
    }
}
