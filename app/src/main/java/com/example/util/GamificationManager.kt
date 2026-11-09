package com.example.util

import com.example.data.model.AppIconEdition
import com.example.data.model.FinancialPersonality
import com.example.data.model.GamificationBadge
import com.example.data.model.PersonalityOption
import com.example.data.model.PersonalityQuestion

object GamificationManager {

    fun calculateLevel(xp: Int): Pair<Int, Int> {
        var level = 1
        var remainingXp = xp
        var reqXp = 100

        while (remainingXp >= reqXp && level < 50) {
            remainingXp -= reqXp
            level++
            reqXp = 100 + (level * 20)
        }

        return Pair(level.coerceAtMost(50), reqXp)
    }

    fun getPersonalityQuestions(): List<PersonalityQuestion> {
        return listOf(
            PersonalityQuestion(
                id = 1,
                questionText = "Bagaimana Anda merespons saat menerima gaji/pemasukan bulanan?",
                options = listOf(
                    PersonalityOption("Langsung alokasikan minimal 30% ke tabungan/dana darurat", FinancialPersonality.SAVER),
                    PersonalityOption("Sisihkan sebagian untuk investasi saham/reksadana/emas", FinancialPersonality.INVESTOR),
                    PersonalityOption("Beli wishlist belanja/hobi yang sudah diincar sejak lama", FinancialPersonality.IMPULSE_SHOPPER),
                    PersonalityOption("Bagi rata untuk kebutuhan pokok, tabungan, dan hiburan", FinancialPersonality.BALANCED)
                )
            ),
            PersonalityQuestion(
                id = 2,
                questionText = "Apa reaksi Anda saat melihat diskon kilat (Flash Sale) barang impian?",
                options = listOf(
                    PersonalityOption("Abaikan jika tidak ada di rencana anggaran bulanan", FinancialPersonality.SAVER),
                    PersonalityOption("Hitung imbal hasil/potensi resale value sebelum membeli", FinancialPersonality.INVESTOR),
                    PersonalityOption("Langsung check out sebelum kehabisan stok!", FinancialPersonality.IMPULSE_SHOPPER),
                    PersonalityOption("Tunggu 24 jam (cooldown) untuk memastikan apakah benar butuh", FinancialPersonality.BALANCED)
                )
            ),
            PersonalityQuestion(
                id = 3,
                questionText = "Bagaimana strategi Anda dalam menghadapi pengeluaran tak terduga?",
                options = listOf(
                    PersonalityOption("Tenang karena sudah punya dana darurat khusus 6 bulan", FinancialPersonality.SAVER),
                    PersonalityOption("Cairkan sebagian instrumen investasi liquid", FinancialPersonality.INVESTOR),
                    PersonalityOption("Gunakan kartu kredit/paylater lalu bingung bayarnya", FinancialPersonality.IMPULSE_SHOPPER),
                    PersonalityOption("Gunakan dana cadangan dan kurangi pos pos hiburan bulan ini", FinancialPersonality.BALANCED)
                )
            ),
            PersonalityQuestion(
                id = 4,
                questionText = "Apa tujuan keuangan terbesar Anda dalam 3-5 tahun ke depan?",
                options = listOf(
                    PersonalityOption("Memiliki tabungan melimpah dan aman dari hutang", FinancialPersonality.SAVER),
                    PersonalityOption("Mencapai kebebasan finansial melalui passive income investasi", FinancialPersonality.INVESTOR),
                    PersonalityOption("Menikmati hidup dan membeli barang-barang impian", FinancialPersonality.IMPULSE_SHOPPER),
                    PersonalityOption("Membeli rumah/aset produktif sambil tetap menikmati hidup", FinancialPersonality.BALANCED)
                )
            ),
            PersonalityQuestion(
                id = 5,
                questionText = "Bagaimana Anda mencatat pengeluaran harian Anda?",
                options = listOf(
                    PersonalityOption("Sangat teliti hingga pecahan rupiah terkecil", FinancialPersonality.SAVER),
                    PersonalityOption("Fokus mencatat pertumbuhan Net Worth dan Portofolio", FinancialPersonality.INVESTOR),
                    PersonalityOption("Sering lupa dan baru sadar saat saldo dompet habis", FinancialPersonality.IMPULSE_SHOPPER),
                    PersonalityOption("Rutin mencatat garis besar pengeluaran per kategori", FinancialPersonality.BALANCED)
                )
            )
        )
    }

    fun determinePersonality(selectedOptions: List<FinancialPersonality>): FinancialPersonality {
        if (selectedOptions.isEmpty()) return FinancialPersonality.BALANCED
        val counts = selectedOptions.groupingBy { it }.eachCount()
        return counts.maxByOrNull { it.value }?.key ?: FinancialPersonality.BALANCED
    }

    fun getAllBadges(streak: Int, level: Int, unlockedSet: Set<String>): List<GamificationBadge> {
        return listOf(
            GamificationBadge(
                id = "rookie_planner",
                title = "Rookie Planner 🥉",
                description = "Berhasil mencatat transaksi 7 hari berturut-turut.",
                iconName = "EmojiEvents",
                isUnlocked = streak >= 7 || unlockedSet.contains("rookie_planner")
            ),
            GamificationBadge(
                id = "budget_master",
                title = "Budget Master 🥈",
                description = "Berhasil menjaga pengeluaran di bawah anggaran atau streak 30 hari.",
                iconName = "MilitaryTech",
                isUnlocked = streak >= 30 || unlockedSet.contains("budget_master")
            ),
            GamificationBadge(
                id = "financial_guru",
                title = "Financial Guru 🥇",
                description = "Konsistensi finansial luar biasa dengan streak 90 hari.",
                iconName = "WorkspacePremium",
                isUnlocked = streak >= 90 || unlockedSet.contains("financial_guru")
            ),
            GamificationBadge(
                id = "centurion_finsmart",
                title = "Centurion DompetNalar 👑",
                description = "Pencapaian legendaris! Streak 365 hari penuh.",
                iconName = "Diamond",
                isUnlocked = streak >= 365 || unlockedSet.contains("centurion_finsmart")
            ),
            GamificationBadge(
                id = "zero_spend_hero",
                title = "Zero Spend Hero 🦸",
                description = "Berhasil melewati No-Spend Day Challenge 24 jam.",
                iconName = "Security",
                isUnlocked = unlockedSet.contains("zero_spend_hero")
            )
        )
    }
}
