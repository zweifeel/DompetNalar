package com.example.data.model

enum class FinancialPersonality(val title: String, val description: String, val aiToneInstruction: String) {
    SAVER("The Saver (Penyimpan Hemat)", "Fokus pada keamanan finansial & menekan pengeluaran.", "Berikan saran yang mengapresiasi penghematan dan sarankan instrumen investasi berisiko rendah."),
    INVESTOR("The Investor (Pengembang Aset)", "Fokus pada akumulasi kekayaan & pertumbuhan aset.", "Berikan wawasan seputar alokasi portofolio, diversifikasi, dan imbal hasil jangka panjang."),
    IMPULSE_SHOPPER("Impulse Guard (Pembeli Impulsif)", "Rentan terhadap belanja emosional/hobi.", "Gunakan gaya bicara yang tegas, ramah, serta ingatkan Cooldown Buying Guard."),
    BALANCED("Balanced Planner (Perencana Seimbang)", "Keseimbangan yang baik antara konsumsi & tabungan.", "Berikan saran finansial yang seimbang dan fleksibel.")
}

data class PersonalityQuestion(
    val id: Int,
    val questionText: String,
    val options: List<PersonalityOption>
)

data class PersonalityOption(
    val optionText: String,
    val personality: FinancialPersonality
)

enum class AppIconEdition(val displayName: String, val requiredStreak: Int, val isLockedByDefault: Boolean) {
    DEFAULT_CLASSIC("Default Classic", 0, false),
    GOLD_LUXURY("Gold Luxury Edition 👑", 14, true),
    DARK_OBSIDIAN("Dark Obsidian Edition 🖤", 14, true),
    CYBERPUNK_NEON("Cyberpunk Neon Edition ⚡", 14, true);

    val localizedDisplayName: String get() = when (this) {
        DEFAULT_CLASSIC -> com.example.util.AppStrings.t("Klasik Bawaan (Default Classic)", "Default Classic")
        GOLD_LUXURY -> com.example.util.AppStrings.t("Edisi Emas Mewah 👑", "Gold Luxury Edition 👑")
        DARK_OBSIDIAN -> com.example.util.AppStrings.t("Edisi Obsidian Gelap 🖤", "Dark Obsidian Edition 🖤")
        CYBERPUNK_NEON -> com.example.util.AppStrings.t("Edisi Neon Cyberpunk ⚡", "Cyberpunk Neon Edition ⚡")
    }
}

data class GamificationBadge(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null
)

data class UserGamificationState(
    val level: Int = 1,
    val currentXp: Int = 0,
    val xpForNextLevel: Int = 100,
    val dailyStreak: Int = 1,
    val bestStreak: Int = 1,
    val personality: FinancialPersonality = FinancialPersonality.BALANCED,
    val activeIconEdition: AppIconEdition = AppIconEdition.DEFAULT_CLASSIC,
    val customBackgroundUri: String? = null,
    val customBackgroundOpacity: Float = 0.3f,
    val isNoSpendChallengeActive: Boolean = false,
    val noSpendSuccessToday: Boolean = false,
    val streakMultiplier: Int = 1,
    val mainPin: String = "123456",
    val duressPin: String = "999999",
    val isBiometricEnabled: Boolean = false,
    val isStealthMode: Boolean = false,
    val unlockedBadgeIds: Set<String> = setOf()
)
