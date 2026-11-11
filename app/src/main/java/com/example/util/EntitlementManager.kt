package com.example.util

import com.example.data.model.User
import com.example.data.model.UserRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single Source of Truth untuk evaluasi Entitlement dan Membership di FinSmart.
 *
 * Menggabungkan seluruh sumber akses premium:
 * 1. MASTER_DEV: Akses seumur hidup permanen tanpa batas.
 * 2. SMART_MASTER (Developer Grant / Google Play Billing): Berdasarkan masa berlaku [User.subscriptionExpiresAt].
 * 3. Trial 3 Hari: Berlaku eksklusif bagi akun terotentikasi Google (bukan Tamu) yang memenuhi syarat.
 * 4. Action Pass Credits: Akses per-fitur instan dari reward / milestone.
 * 5. Rewarded Ads: Akses berdurasi sementara dengan timestamp kedaluwarsa presisi.
 */
enum class EntitlementSource(val displayName: String) {
    MASTER_DEV("Hak Istimewa Developer (Lifetime)"),
    DEVELOPER_GRANT("Persetujuan Developer (Manual)"),
    TRIAL_THREE_DAYS("Uji Coba 3 Hari Akun Google"),
    REWARDED_AD("Reward Iklan (1 Jam)"),
    STREAK_REWARD_24H("Reward Streak 30 Hari (24 Jam)"),
    PLAY_STORE_BILLING("Pembelian Google Play Store"),
    ACTION_PASS_CREDIT("Action Pass Credit"),
    NONE("Tidak Ada")
}

data class UserEntitlementStatus(
    val isPremiumUnlocked: Boolean,
    val effectiveRole: UserRole,
    val primarySource: EntitlementSource,
    val expiresAt: Long?,
    val isExpired: Boolean,
    val trialDaysRemaining: Int,
    val actionCredits: Int
) {
    fun getFormattedExpiry(): String {
        return when {
            effectiveRole == UserRole.MASTER_DEV -> AppStrings.t("Permanen (Master Developer)", "Permanent (Master Developer)")
            expiresAt == null -> AppStrings.t("Tidak Terbatas", "Unlimited")
            expiresAt <= 0L -> "-"
            else -> {
                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", LocaleHelper.getActiveLocale())
                sdf.format(Date(expiresAt))
            }
        }
    }
}

object EntitlementManager {
    const val THREE_DAYS_MILLIS = 3L * 24 * 3600 * 1000L
    const val MAX_CUSTOM_DURATION_DAYS = 3650L // 10 tahun (kebijakan batas maksimum durasi)

    /**
     * Memeriksa apakah pengguna memiliki hak akses istimewa pengembang.
     * Pada build DEBUG: Memeriksa role MASTER_DEV atau email developer tepercaya.
     * Pada build RELEASE: Hak admin Web Admin dan hak Master Developer Android adalah otorisasi yang berbeda.
     * Admin Web TIDAK otomatis memperoleh MASTER_DEV di Android publik.
     */
    fun isDeveloperAuthorized(
        user: User?,
        isDebug: Boolean = com.example.BuildConfig.DEBUG
    ): Boolean {
        if (user == null || user.isGuest) return false
        return if (isDebug) {
            user.role == UserRole.MASTER_DEV || com.example.config.DeveloperAccountConfig.isTrustedDeveloperEmail(user.email)
        } else {
            // KEAMANAN KRITIS PHASE 4B: Pada build Release, dilarang otomatis memberikan hak MASTER_DEV
            // semata-mata karena email cocok dengan admin Web Admin.
            false
        }
    }

    /**
     * Mengevaluasi status entitlement aktual pengguna secara deterministik.
     */
    fun evaluate(
        user: User?,
        actionCredits: Int = 0,
        now: Long = System.currentTimeMillis(),
        isDebug: Boolean = com.example.BuildConfig.DEBUG,
        explicitSource: EntitlementSource? = null
    ): UserEntitlementStatus {
        if (user == null) {
            return UserEntitlementStatus(
                isPremiumUnlocked = false,
                effectiveRole = UserRole.FREE_MEMBER,
                primarySource = EntitlementSource.NONE,
                expiresAt = null,
                isExpired = false,
                trialDaysRemaining = 0,
                actionCredits = actionCredits
            )
        }

        // 1. MASTER_DEV selalu memiliki hak akses penuh seumur hidup (hanya berlaku jika terverifikasi developer)
        if (isDeveloperAuthorized(user, isDebug)) {
            return UserEntitlementStatus(
                isPremiumUnlocked = true,
                effectiveRole = UserRole.MASTER_DEV,
                primarySource = EntitlementSource.MASTER_DEV,
                expiresAt = null,
                isExpired = false,
                trialDaysRemaining = 999,
                actionCredits = actionCredits
            )
        }

        // 2. Evaluasi langganan SMART_MASTER berdurasi (Remote Grant / Google Play Billing / Ad Rewards)
        if (user.role == UserRole.SMART_MASTER) {
            val expiry = user.subscriptionExpiresAt
            if (expiry == null || expiry > now) {
                val remainingMillis = if (expiry != null) expiry - now else Long.MAX_VALUE
                val source = explicitSource ?: when {
                    remainingMillis in 1L..(3600000L + 60000L) -> EntitlementSource.REWARDED_AD
                    remainingMillis in (3600000L + 60000L)..(24L * 3600000L + 60000L) -> EntitlementSource.STREAK_REWARD_24H
                    else -> EntitlementSource.DEVELOPER_GRANT
                }
                return UserEntitlementStatus(
                    isPremiumUnlocked = true,
                    effectiveRole = UserRole.SMART_MASTER,
                    primarySource = source,
                    expiresAt = expiry,
                    isExpired = false,
                    trialDaysRemaining = 999,
                    actionCredits = if (user.isGuest) 0 else actionCredits
                )
            }
            // Jika masa langganan sudah lewat, status SMART_MASTER dinyatakan EXPIRED.
            // Alur berlanjut ke evaluasi trial atau action credit.
        }

        // 3. Evaluasi Action Pass Credit (per-action usage)
        if (actionCredits > 0) {
            return UserEntitlementStatus(
                isPremiumUnlocked = true,
                effectiveRole = UserRole.FREE_MEMBER,
                primarySource = EntitlementSource.ACTION_PASS_CREDIT,
                expiresAt = null,
                isExpired = false,
                trialDaysRemaining = 0,
                actionCredits = actionCredits
            )
        }

        // 4. Penghapusan Permanen Uji Coba (Trial 3 Hari Ditiadakan)
        // Pengguna Free Member tidak lagi mendapatkan hak akses Smart Master dari mekanisme trial.

        // 5. Kondisi Kedaluwarsa atau Free Member Biasa
        val wasSmartMasterExpired = user.role == UserRole.SMART_MASTER &&
                user.subscriptionExpiresAt != null &&
                user.subscriptionExpiresAt <= now

        return UserEntitlementStatus(
            isPremiumUnlocked = false,
            effectiveRole = UserRole.FREE_MEMBER,
            primarySource = if (wasSmartMasterExpired) EntitlementSource.DEVELOPER_GRANT else EntitlementSource.NONE,
            expiresAt = user.subscriptionExpiresAt,
            isExpired = wasSmartMasterExpired,
            trialDaysRemaining = 0,
            actionCredits = actionCredits
        )
    }

    /**
     * Menghitung sisa hari uji coba.
     * Mekanisme trial 3 hari telah dihapus permanen, sehingga selalu mengembalikan 0.
     */
    fun calculateTrialDaysRemaining(
        user: User?,
        now: Long = System.currentTimeMillis(),
        isDebug: Boolean = com.example.BuildConfig.DEBUG
    ): Int {
        return 0
    }

    /**
     * Memvalidasi input durasi khusus dalam hari.
     */
    fun validateCustomDuration(daysInput: String): Result<Long> {
        val trimmed = daysInput.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Durasi tidak boleh kosong."))
        }
        val days = trimmed.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("Durasi harus berupa angka bulat positif."))
        if (days <= 0L) {
            return Result.failure(IllegalArgumentException("Durasi minimal adalah 1 hari."))
        }
        if (days > MAX_CUSTOM_DURATION_DAYS) {
            return Result.failure(IllegalArgumentException("Durasi melebihi batas kebijakan ($MAX_CUSTOM_DURATION_DAYS hari)."))
        }
        return Result.success(days)
    }

    /**
     * Determines authoritatively whether the user holds an active paid Google Play subscription,
     * an active manual developer grant, or a permanent MASTER_DEV entitlement.
     *
     * Returns false for Free Members, Guests, and active temporary Rewarded Ad recipients.
     * This ensures temporary rewarded access can NEVER overwrite or downgrade an active paid subscription.
     */
    fun isPaidOrPermanentSmartMaster(
        context: android.content.Context,
        user: User?,
        now: Long = System.currentTimeMillis(),
        isDebug: Boolean = com.example.BuildConfig.DEBUG
    ): Boolean {
        if (user == null) return false
        // 1. MASTER_DEV or trusted developer
        if (isDeveloperAuthorized(user, isDebug)) {
            return true
        }
        // 2. Must hold SMART_MASTER role
        if (user.role != UserRole.SMART_MASTER) {
            return false
        }
        // 3. Guest accounts cannot hold paid subscriptions
        if (user.isGuest || user.id <= 0L) {
            return false
        }
        // 4. Lifetime entitlement (null or <= 0L expiry)
        if (user.subscriptionExpiresAt == null || user.subscriptionExpiresAt!! <= 0L) {
            return true
        }
        // 5. Expired subscription
        if (user.subscriptionExpiresAt!! <= now) {
            return false
        }
        // 6. Check authoritative Billing or Developer Grant source in SharedPreferences
        val prefs = context.getSharedPreferences("finsmart_auth_prefs", android.content.Context.MODE_PRIVATE)
        val billingSource = prefs.getString("billing_purchase_source_user_${user.id}", null)
        if (billingSource == "PLAY_STORE_BILLING" || billingSource == "DEVELOPER_GRANT") {
            return true
        }
        // 7. Check if this is an active temporary rewarded ad entitlement
        val rewardManagerExpiry = RewardedAdRewardManager.getActiveRewardExpiry(context, user, now)
        val authAdExpiry = prefs.getLong("user_${user.id}_ad_reward_expires_at", 0L)
        val isTemporaryReward = (rewardManagerExpiry > now && user.subscriptionExpiresAt == rewardManagerExpiry) ||
                (authAdExpiry > now && user.subscriptionExpiresAt == authAdExpiry)

        return !isTemporaryReward
    }
}
