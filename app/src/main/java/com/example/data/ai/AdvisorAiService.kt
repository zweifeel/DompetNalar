package com.example.data.ai

import com.example.data.model.BudgetLimitItem
import com.example.data.model.TransactionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object AdvisorAiService {
    suspend fun getAdvisorResponse(
        userMessage: String,
        transactions: List<TransactionItem>,
        budgetLimits: List<BudgetLimitItem>
    ): String = withContext(Dispatchers.IO) {
        val apiKey = GeminiClient.getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext getOfflineResponse(userMessage, transactions, budgetLimits)
        }

        try {
            val contextPrompt = buildContextPrompt(userMessage, transactions, budgetLimits)
            val request = GeminiGenerateRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = contextPrompt))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.5f
                )
            )
            val response = GeminiClient.apiService.generateContent(apiKey, request)
            return@withContext response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Mohon maaf, Gemini tidak memberikan respon."
        } catch (e: Exception) {
            return@withContext "Koneksi terganggu: ${e.localizedMessage}. Berikut adalah analisis offline cepat:\n\n" +
                    getOfflineResponse(userMessage, transactions, budgetLimits)
        }
    }

    private fun buildContextPrompt(
        userQuery: String,
        transactions: List<TransactionItem>,
        budgetLimits: List<BudgetLimitItem>
    ): String {
        val incomeTotal = transactions.filter { it.type == com.example.data.model.TransactionType.INCOME }.sumOf { it.amount }
        val expenseTotal = transactions.filter { it.type == com.example.data.model.TransactionType.EXPENSE }.sumOf { it.amount }
        val savings = incomeTotal - expenseTotal
        val savingsRatio = if (incomeTotal > 0) (savings / incomeTotal) * 100 else 0.0

        val categorySpending = transactions.filter { it.type == com.example.data.model.TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { it.amount } }

        val limitTotal = budgetLimits.firstOrNull { it.id == "total_monthly" }?.limitAmount ?: 0.0
        val limitCategories = budgetLimits.filter { it.isCategory }.joinToString("\n") { "- ${it.id}: Limit ${it.limitAmount}" }

        val spendingBreakdown = categorySpending.entries.joinToString("\n") { "- ${it.key}: Rp ${it.value}" }

        return """
            Anda adalah FinSmart AI Financial Health Advisor - konsultan keuangan pribadi cerdas dan suportif dari Indonesia.
            Gunakan data keuangan asli pengguna bulan ini untuk menjawab pertanyaan mereka dengan analisis mendalam, hangat, informatif, dan terstruktur.

            Query Pengguna: "$userQuery"

            Data Keuangan Pengguna Bulan Ini:
            - Total Pemasukan: Rp ${incomeTotal}
            - Total Pengeluaran: Rp ${expenseTotal}
            - Tabungan Bersih: Rp ${savings}
            - Rasio Tabungan: ${String.format("%.1f", savingsRatio)}% (Target ideal: >= 20% dari pemasukan)
            
            Rincian Pengeluaran Per Kategori:
            $spendingBreakdown

            Batas Anggaran (Budget Limits) Konfigurasi Pengguna:
            - Limit Bulanan Gabungan: Rp $limitTotal
            $limitCategories

            Petunjuk Analisis Khusus:
            1. Evaluasi Rasio Tabungan: Jika rasio tabungan < 10%, ingatkan dengan sopan dan sarankan cara menekan pengeluaran.
            2. Identifikasi Pengeluaran Impulsif/Terbanyak: Cari kategori dengan porsi pengeluaran tertinggi (biasanya Makanan/Hiburan/Belanja) dan berikan tips konkret.
            3. Evaluasi Limit Anggaran: Bandingkan pengeluaran riil dengan batas limit yang ditentukan pengguna. Beri tahu jika ada yang overbudget atau kritis (>85%).
            4. Berikan saran penghematan konkret yang dipersonalisasi dalam Bahasa Indonesia yang profesional dan memotivasi. Gunakan emoji secara elegan.
        """.trimIndent()
    }

    private fun getOfflineResponse(
        userQuery: String,
        transactions: List<TransactionItem>,
        budgetLimits: List<BudgetLimitItem>
    ): String {
        val incomeTotal = transactions.filter { it.type == com.example.data.model.TransactionType.INCOME }.sumOf { it.amount }
        val expenseTotal = transactions.filter { it.type == com.example.data.model.TransactionType.EXPENSE }.sumOf { it.amount }
        val savings = incomeTotal - expenseTotal
        val savingsRatio = if (incomeTotal > 0) (savings / incomeTotal) * 100 else 0.0

        val topCategory = transactions.filter { it.type == com.example.data.model.TransactionType.EXPENSE }
            .groupBy { it.category }
            .maxByOrNull { it.value.sumOf { it.amount } }?.key ?: "Lain-lain"

        return """
            💡 **Hasil Analisis Offline FinSmart Advisor**:
            
            *   **Kondisi Kas**: Pemasukan Anda adalah Rp ${incomeTotal} dengan pengeluaran Rp ${expenseTotal}.
            *   **Tabungan Bersih**: Rp ${savings} (Rasio Tabungan: ${String.format("%.1f", savingsRatio)}%).
            *   **Kategori Terbanyak**: Kategori pengeluaran terbanyak Anda bulan ini adalah **$topCategory**.
            
            📌 *Saran Advisor*:
            ${if (savingsRatio < 20) "Rasio tabungan Anda berada di bawah target ideal (20%). Pertimbangkan untuk menunda pembelian non-esensial dan batasi budget kategori **$topCategory**." else "Luar biasa! Rasio tabungan Anda sangat sehat di atas 20%. Pertahankan kedisiplinan finansial Anda!"}
            
            *(Catatan: Sambungkan ke internet untuk menikmati analisis cerdas mendalam dari Gemini AI)*
        """.trimIndent()
    }
}
