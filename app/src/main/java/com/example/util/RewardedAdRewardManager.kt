package com.example.util

import android.content.Context
import com.example.data.model.User
import com.example.data.model.UserRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RewardedAdRewardManager
 *
 * Implements the Owner-Approved Rewarded Ads Policy:
 * 1. Each user may earn exactly ONE rewarded SmartMaster entitlement per local calendar day.
 * 2. One successfully earned Rewarded Ad grants 60 minutes of temporary SmartMaster access.
 * 3. After the first successful reward, the user may still load and watch additional Rewarded Ads.
 * 4. Additional ads watched on the same local calendar day MUST NOT grant any further reward
 *    or extend the existing expiration timestamp.
 * 5. The daily reward opportunity resets on the next local calendar day.
 * 6. Failed ad loading, failed ad display, early dismissal, or completion without the official
 *    reward callback does not consume the daily reward opportunity.
 * 7. Next day's reward may extend an entitlement that is still active by adding 60 minutes.
 * 8. Strict User Isolation: Guest and authenticated user accounts have separated storage.
 * 9. Offline Notice: Device clock manipulation cannot be 100% prevented in offline-first mode.
 */
object RewardedAdRewardManager {
    const val REWARD_DURATION_MILLIS = 60 * 60 * 1000L // 60 minutes (3,600,000 ms)
    const val PREFS_NAME = "finsmart_rewarded_ad_prefs"

    private val lock = Any()
    @Volatile
    private var lastCallbackTimestamp = 0L

    fun resetCallbackThrottleForTesting() {
        synchronized(lock) {
            lastCallbackTimestamp = 0L
        }
    }

    data class ClaimResult(
        val isGranted: Boolean,
        val newExpiryMillis: Long,
        val isDailyLimitAlreadyReached: Boolean,
        val message: String
    )

    fun formatLocalDate(timestamp: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getUserStorageKey(user: User?): String {
        return if (user == null || user.isGuest || user.id <= 0L) "guest" else "user_${user.id}"
    }

    /**
     * Pure testable logic to evaluate if user can claim today.
     */
    fun canClaimToday(
        lastClaimDate: String?,
        currentLocalDate: String
    ): Boolean {
        if (lastClaimDate.isNullOrBlank()) return true
        return lastClaimDate != currentLocalDate
    }

    /**
     * Pure testable logic to compute new expiration timestamp.
     * If currentExpiry is in the future (> now), extends it by durationMillis.
     * Otherwise sets to now + durationMillis.
     */
    fun computeNewExpiry(
        currentExpiry: Long,
        now: Long,
        durationMillis: Long = REWARD_DURATION_MILLIS
    ): Long {
        val base = if (currentExpiry > now) currentExpiry else now
        return base + durationMillis
    }

    fun isDailyRewardClaimed(
        context: Context,
        user: User?,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = getUserStorageKey(user)
        val lastClaimDate = prefs.getString("${key}_last_claim_date", null)
        val todayStr = formatLocalDate(now)
        return !canClaimToday(lastClaimDate, todayStr)
    }

    fun getActiveRewardExpiry(
        context: Context,
        user: User?,
        now: Long = System.currentTimeMillis()
    ): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = getUserStorageKey(user)
        val expiry = prefs.getLong("${key}_reward_expires_at", 0L)
        return if (expiry > now) expiry else 0L
    }

    fun getRemainingRewardSeconds(
        context: Context,
        user: User?,
        now: Long = System.currentTimeMillis()
    ): Long {
        val expiry = getActiveRewardExpiry(context, user, now)
        return if (expiry > now) (expiry - now) / 1000L else 0L
    }

    /**
     * Executes the daily reward claim upon AdMob onUserEarnedReward callback.
     * Enforces:
     * - 5-second debounce against rapid duplicate callbacks
     * - One claim per local calendar day
     * - User account isolation
     */
    fun processEarnedReward(
        context: Context,
        user: User?,
        now: Long = System.currentTimeMillis()
    ): ClaimResult {
        synchronized(lock) {
            if (now - lastCallbackTimestamp < 5000L) {
                val currentExpiry = getActiveRewardExpiry(context, user, now)
                return ClaimResult(
                    isGranted = false,
                    newExpiryMillis = currentExpiry,
                    isDailyLimitAlreadyReached = false,
                    message = "Duplicate callback ignored"
                )
            }
            lastCallbackTimestamp = now

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = getUserStorageKey(user)
            val lastClaimDate = prefs.getString("${key}_last_claim_date", null)
            val todayStr = formatLocalDate(now)
            val currentExpiry = prefs.getLong("${key}_reward_expires_at", 0L)

            // Check if already claimed today
            if (!canClaimToday(lastClaimDate, todayStr)) {
                // Additional ads watched today DO NOT grant any further reward or extension
                return ClaimResult(
                    isGranted = false,
                    newExpiryMillis = currentExpiry,
                    isDailyLimitAlreadyReached = true,
                    message = "Hadiah SmartMaster hari ini sudah digunakan. Anda masih dapat menonton iklan, tetapi tidak memperoleh hadiah tambahan."
                )
            }

            // First claim today: grant 60 minutes (extending unexpired access if applicable)
            val newExpiry = computeNewExpiry(currentExpiry, now, REWARD_DURATION_MILLIS)

            prefs.edit()
                .putString("${key}_last_claim_date", todayStr)
                .putLong("${key}_reward_granted_at", now)
                .putLong("${key}_reward_expires_at", newExpiry)
                .apply()

            return ClaimResult(
                isGranted = true,
                newExpiryMillis = newExpiry,
                isDailyLimitAlreadyReached = false,
                message = "Selamat! Akses SmartMaster 1 Jam Aktif! 🎉"
            )
        }
    }

    fun clearUserData(context: Context, user: User?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = getUserStorageKey(user)
        prefs.edit()
            .remove("${key}_last_claim_date")
            .remove("${key}_reward_granted_at")
            .remove("${key}_reward_expires_at")
            .apply()
    }
}
