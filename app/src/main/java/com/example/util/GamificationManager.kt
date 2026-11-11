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
                questionText = AppStrings.t(
                    "Bagaimana Anda merespons saat menerima gaji/pemasukan bulanan?",
                    "How do you respond when receiving your monthly salary/income?"
                ),
                options = listOf(
                    PersonalityOption(
                        AppStrings.t("Langsung alokasikan minimal 30% ke tabungan/dana darurat", "Immediately allocate at least 30% to savings/emergency fund"),
                        FinancialPersonality.SAVER
                    ),
                    PersonalityOption(
                        AppStrings.t("Sisihkan sebagian untuk investasi saham/reksadana/emas", "Set aside a portion for stocks/mutual funds/gold investments"),
                        FinancialPersonality.INVESTOR
                    ),
                    PersonalityOption(
                        AppStrings.t("Beli wishlist belanja/hobi yang sudah diincar sejak lama", "Buy wishlist items or hobby splurges you've eyed for a while"),
                        FinancialPersonality.IMPULSE_SHOPPER
                    ),
                    PersonalityOption(
                        AppStrings.t("Bagi rata untuk kebutuhan pokok, tabungan, dan hiburan", "Divide evenly between essentials, savings, and entertainment"),
                        FinancialPersonality.BALANCED
                    )
                )
            ),
            PersonalityQuestion(
                id = 2,
                questionText = AppStrings.t(
                    "Apa reaksi Anda saat melihat diskon kilat (Flash Sale) barang impian?",
                    "What is your reaction to a Flash Sale on your dream item?"
                ),
                options = listOf(
                    PersonalityOption(
                        AppStrings.t("Abaikan jika tidak ada di rencana anggaran bulanan", "Ignore it if it's not in the monthly budget plan"),
                        FinancialPersonality.SAVER
                    ),
                    PersonalityOption(
                        AppStrings.t("Hitung imbal hasil/potensi resale value sebelum membeli", "Calculate ROI or potential resale value before purchasing"),
                        FinancialPersonality.INVESTOR
                    ),
                    PersonalityOption(
                        AppStrings.t("Langsung check out sebelum kehabisan stok!", "Immediately check out before it runs out of stock!"),
                        FinancialPersonality.IMPULSE_SHOPPER
                    ),
                    PersonalityOption(
                        AppStrings.t("Tunggu 24 jam (cooldown) untuk memastikan apakah benar butuh", "Wait 24 hours (cooldown) to confirm if you truly need it"),
                        FinancialPersonality.BALANCED
                    )
                )
            ),
            PersonalityQuestion(
                id = 3,
                questionText = AppStrings.t(
                    "Bagaimana strategi Anda dalam menghadapi pengeluaran tak terduga?",
                    "What is your strategy when dealing with unexpected expenses?"
                ),
                options = listOf(
                    PersonalityOption(
                        AppStrings.t("Tenang karena sudah punya dana darurat khusus 6 bulan", "Calm because I already have a dedicated 6-month emergency fund"),
                        FinancialPersonality.SAVER
                    ),
                    PersonalityOption(
                        AppStrings.t("Cairkan sebagian instrumen investasi liquid", "Liquidate a portion of liquid investment instruments"),
                        FinancialPersonality.INVESTOR
                    ),
                    PersonalityOption(
                        AppStrings.t("Gunakan kartu kredit/paylater lalu bingung bayarnya", "Use credit cards/paylater and worry about repayments later"),
                        FinancialPersonality.IMPULSE_SHOPPER
                    ),
                    PersonalityOption(
                        AppStrings.t("Gunakan dana cadangan dan kurangi pos pos hiburan bulan ini", "Use reserve funds and cut back on this month's entertainment"),
                        FinancialPersonality.BALANCED
                    )
                )
            ),
            PersonalityQuestion(
                id = 4,
                questionText = AppStrings.t(
                    "Apa tujuan keuangan terbesar Anda dalam 3-5 tahun ke depan?",
                    "What is your biggest financial goal in the next 3-5 years?"
                ),
                options = listOf(
                    PersonalityOption(
                        AppStrings.t("Memiliki tabungan melimpah dan aman dari hutang", "Having abundant savings and staying completely debt-free"),
                        FinancialPersonality.SAVER
                    ),
                    PersonalityOption(
                        AppStrings.t("Mencapai kebebasan finansial melalui passive income investasi", "Achieving financial freedom via investment passive income"),
                        FinancialPersonality.INVESTOR
                    ),
                    PersonalityOption(
                        AppStrings.t("Menikmati hidup dan membeli barang-barang impian", "Enjoying life and purchasing dream lifestyle items"),
                        FinancialPersonality.IMPULSE_SHOPPER
                    ),
                    PersonalityOption(
                        AppStrings.t("Membeli rumah/aset produktif sambil tetap menikmati hidup", "Buying a home/productive assets while still enjoying life"),
                        FinancialPersonality.BALANCED
                    )
                )
            ),
            PersonalityQuestion(
                id = 5,
                questionText = AppStrings.t(
                    "Bagaimana Anda mencatat pengeluaran harian Anda?",
                    "How do you track your daily expenses?"
                ),
                options = listOf(
                    PersonalityOption(
                        AppStrings.t("Sangat teliti hingga pecahan rupiah terkecil", "Meticulously down to the smallest currency cent"),
                        FinancialPersonality.SAVER
                    ),
                    PersonalityOption(
                        AppStrings.t("Fokus mencatat pertumbuhan Net Worth dan Portofolio", "Focus on tracking Net Worth growth and Portfolio"),
                        FinancialPersonality.INVESTOR
                    ),
                    PersonalityOption(
                        AppStrings.t("Sering lupa dan baru sadar saat saldo dompet habis", "Often forget and only realize when the wallet runs empty"),
                        FinancialPersonality.IMPULSE_SHOPPER
                    ),
                    PersonalityOption(
                        AppStrings.t("Rutin mencatat garis besar pengeluaran per kategori", "Routinely log expense overviews per category"),
                        FinancialPersonality.BALANCED
                    )
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
                title = AppStrings.t("Rookie Planner 🥉", "Rookie Planner 🥉"),
                description = AppStrings.t(
                    "Berhasil mencatat transaksi 7 hari berturut-turut.",
                    "Successfully recorded transactions for 7 consecutive days."
                ),
                iconName = "EmojiEvents",
                isUnlocked = streak >= 7 || unlockedSet.contains("rookie_planner")
            ),
            GamificationBadge(
                id = "budget_master",
                title = AppStrings.t("Budget Master 🥈", "Budget Master 🥈"),
                description = AppStrings.t(
                    "Berhasil menjaga pengeluaran di bawah anggaran atau streak 30 hari.",
                    "Successfully maintained spending under budget or reached a 30-day streak."
                ),
                iconName = "MilitaryTech",
                isUnlocked = streak >= 30 || unlockedSet.contains("budget_master")
            ),
            GamificationBadge(
                id = "financial_guru",
                title = AppStrings.t("Financial Guru 🥇", "Financial Guru 🥇"),
                description = AppStrings.t(
                    "Konsistensi finansial luar biasa dengan streak 90 hari.",
                    "Outstanding financial consistency with a 90-day streak."
                ),
                iconName = "WorkspacePremium",
                isUnlocked = streak >= 90 || unlockedSet.contains("financial_guru")
            ),
            GamificationBadge(
                id = "centurion_finsmart",
                title = AppStrings.t("Centurion DompetNalar 👑", "Centurion DompetNalar 👑"),
                description = AppStrings.t(
                    "Pencapaian legendaris! Streak 365 hari penuh.",
                    "Legendary milestone! Reached a full 365-day streak."
                ),
                iconName = "Diamond",
                isUnlocked = streak >= 365 || unlockedSet.contains("centurion_finsmart")
            ),
            GamificationBadge(
                id = "zero_spend_hero",
                title = AppStrings.t("Zero Spend Hero 🦸", "Zero Spend Hero 🦸"),
                description = AppStrings.t(
                    "Berhasil melewati No-Spend Day Challenge 24 jam.",
                    "Successfully cleared a 24-hour No-Spend Day Challenge."
                ),
                iconName = "Security",
                isUnlocked = unlockedSet.contains("zero_spend_hero")
            )
        )
    }
}
