package com.example.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.config.AppConfig
import com.example.data.model.User
import com.example.data.model.UserRole
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AdManager {
    private const val TAG = "AdManager"

    // 10-Minute Frequency Cap for Interstitials (600,000 ms)
    const val INTERSTITIAL_CAP_MS = 10 * 60 * 1000L
    private const val AD_PREFS_NAME = "ad_prefs"
    private const val KEY_INTERSTITIAL_LAST_TS = "interstitial_last_ts"

    private val adLock = Any()
    private val interstitialLock = Any()

    @Volatile
    private var rewardedAd: RewardedAd? = null
    @Volatile
    private var isRewardedAdLoading = false
    private val pendingRewardedCallbacks = mutableListOf<(Boolean) -> Unit>()

    @Volatile
    private var interstitialAd: InterstitialAd? = null
    @Volatile
    private var isInterstitialLoading = false
    @Volatile
    private var isInterstitialShowing = false
    private val pendingInterstitialCallbacks = mutableListOf<(Boolean) -> Unit>()

    fun isEmulatorOrVirtualDisplay(): Boolean {
        val fingerprint = android.os.Build.FINGERPRINT ?: ""
        val model = android.os.Build.MODEL ?: ""
        val brand = android.os.Build.BRAND ?: ""
        val device = android.os.Build.DEVICE ?: ""
        val product = android.os.Build.PRODUCT ?: ""
        val hardware = android.os.Build.HARDWARE ?: ""
        return fingerprint.startsWith("generic") ||
                fingerprint.startsWith("unknown") ||
                model.contains("google_sdk") ||
                model.contains("Emulator") ||
                model.contains("Android SDK built for") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu") ||
                product.contains("sdk") ||
                product.contains("google_sdk") ||
                brand.startsWith("generic") ||
                device.startsWith("generic")
    }

    fun initialize(context: Context) {
        if (isEmulatorOrVirtualDisplay()) {
            Log.d(TAG, "Virtual/Emulator environment detected - AdServices IPC initialization handled safely.")
        }
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val requestConfig = com.google.android.gms.ads.RequestConfiguration.Builder()
                        .setTagForChildDirectedTreatment(com.google.android.gms.ads.RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
                        .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                        .build()
                    MobileAds.setRequestConfiguration(requestConfig)
                    MobileAds.initialize(context.applicationContext) { status ->
                        Log.d(TAG, "AdMob SDK Initialized: ${status.adapterStatusMap}")
                        // Preload background ads so they are ready when requested
                        loadRewardedAd(context.applicationContext)
                        loadInterstitialAd(context.applicationContext)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AdMob SDK background init non-fatal note: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Non-fatal error scheduling MobileAds init: ${e.message}")
        }
    }

    /**
     * Determines whether banner/interstitial ads should be displayed to the user.
     * Premium users (Smart Master, Master Dev) never see ads.
     * Free and Guest users are eligible to see ads.
     */
    fun shouldShowAds(user: User? = null): Boolean {
        val activeUser = user ?: com.example.data.session.SessionManager.currentUser.value
        val entitlement = EntitlementManager.evaluate(activeUser)
        if (entitlement.isPremiumUnlocked) return false
        return true
    }

    fun canRequestAds(context: Context): Boolean {
        if (isEmulatorOrVirtualDisplay()) return true
        return try {
            ConsentManager.canRequestAds(context)
        } catch (_: Exception) {
            true
        }
    }

    // =========================================================================
    // INTERSTITIAL ADS
    // =========================================================================

    /**
     * Pure testable helper for the 10-minute frequency cap.
     */
    fun isFrequencyCapped(lastShownTimestamp: Long, currentTime: Long = System.currentTimeMillis()): Boolean {
        if (lastShownTimestamp <= 0L) return false
        return (currentTime - lastShownTimestamp) < INTERSTITIAL_CAP_MS
    }

    fun canShowInterstitial(context: Context, currentTime: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(AD_PREFS_NAME, Context.MODE_PRIVATE)
        val lastShown = prefs.getLong(KEY_INTERSTITIAL_LAST_TS, 0L)
        return !isFrequencyCapped(lastShown, currentTime)
    }

    fun recordInterstitialShown(context: Context, timestamp: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(AD_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_INTERSTITIAL_LAST_TS, timestamp)
            .apply()
    }

    fun isInterstitialAvailable(): Boolean {
        synchronized(interstitialLock) {
            return interstitialAd != null
        }
    }

    fun loadInterstitialAd(context: Context, onLoaded: (Boolean) -> Unit = {}) {
        val appContext = context.applicationContext
        if (!canRequestAds(appContext)) {
            Log.d(TAG, "Cannot load interstitial ad: consent not granted or cannot request ads.")
            onLoaded(false)
            return
        }
        synchronized(interstitialLock) {
            if (interstitialAd != null) {
                onLoaded(true)
                return
            }
            pendingInterstitialCallbacks.add(onLoaded)
            if (isInterstitialLoading) return
            isInterstitialLoading = true
        }

        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                appContext,
                AppConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        Log.d(TAG, "Interstitial ad loaded successfully.")
                        val callbacksToNotify: List<(Boolean) -> Unit>
                        synchronized(interstitialLock) {
                            interstitialAd = ad
                            isInterstitialLoading = false
                            callbacksToNotify = ArrayList(pendingInterstitialCallbacks)
                            pendingInterstitialCallbacks.clear()
                        }
                        callbacksToNotify.forEach { cb ->
                            try { cb(true) } catch (_: Exception) {}
                        }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.w(TAG, "Failed to load interstitial ad [code=${loadAdError.code}]: ${loadAdError.message}")
                        val callbacksToNotify: List<(Boolean) -> Unit>
                        synchronized(interstitialLock) {
                            interstitialAd = null
                            isInterstitialLoading = false
                            callbacksToNotify = ArrayList(pendingInterstitialCallbacks)
                            pendingInterstitialCallbacks.clear()
                        }
                        callbacksToNotify.forEach { cb ->
                            try { cb(false) } catch (_: Exception) {}
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Exception loading interstitial: ${e.message}")
            val callbacksToNotify: List<(Boolean) -> Unit>
            synchronized(interstitialLock) {
                interstitialAd = null
                isInterstitialLoading = false
                callbacksToNotify = ArrayList(pendingInterstitialCallbacks)
                pendingInterstitialCallbacks.clear()
            }
            callbacksToNotify.forEach { cb ->
                try { cb(false) } catch (_: Exception) {}
            }
        }
    }

    /**
     * Attempts to show an interstitial ad after a natural pause point (e.g. Export completion).
     * Enforces:
     * 1. 10-minute frequency cap.
     * 2. No concurrent showing.
     * 3. Safe Activity reference (aborts cleanly if finishing/destroyed).
     * 4. Preloads the next ad on dismiss or error.
     * 5. Never throws or halts host business logic.
     */
    fun maybeShowInterstitial(
        activity: Activity,
        user: User? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        val currentUser = user ?: com.example.data.session.SessionManager.currentUser.value
        if (!shouldShowAds(currentUser)) {
            Log.d(TAG, "Interstitial skipped: user is exempt from ads.")
            onResult(false)
            return
        }

        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Interstitial skipped: Activity is finishing or destroyed.")
            onResult(false)
            return
        }

        if (!canShowInterstitial(activity)) {
            Log.d(TAG, "Interstitial skipped: frequency capped (within 10-min window).")
            onResult(false)
            return
        }

        val adToShow: InterstitialAd?
        synchronized(interstitialLock) {
            if (isInterstitialShowing) {
                Log.d(TAG, "Interstitial skipped: another ad is currently showing.")
                onResult(false)
                return
            }
            adToShow = interstitialAd
            if (adToShow != null) {
                isInterstitialShowing = true
                interstitialAd = null // Consume the ad reference immediately
            }
        }

        if (adToShow == null) {
            Log.d(TAG, "Interstitial ad not ready; triggering background preload.")
            loadInterstitialAd(activity.applicationContext)
            onResult(false)
            return
        }

        val appContext = activity.applicationContext
        adToShow.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial onAdShowedFullScreenContent confirmed display.")
                // Frequency cap timestamp recorded when actually displayed
                recordInterstitialShown(appContext)
                onResult(true)
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed.")
                synchronized(interstitialLock) {
                    isInterstitialShowing = false
                }
                loadInterstitialAd(appContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Interstitial failed to show [code=${adError.code}]: ${adError.message}")
                synchronized(interstitialLock) {
                    isInterstitialShowing = false
                }
                loadInterstitialAd(appContext)
            }
        }

        try {
            adToShow.show(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error displaying interstitial: ${e.message}")
            synchronized(interstitialLock) {
                isInterstitialShowing = false
            }
            loadInterstitialAd(appContext)
            onResult(false)
        }
    }

    // =========================================================================
    // REWARDED ADS
    // =========================================================================

    fun isRewardedAdAvailable(): Boolean {
        synchronized(adLock) {
            return rewardedAd != null
        }
    }

    fun loadRewardedAd(
        context: Context,
        user: User? = null,
        onLoaded: (Boolean) -> Unit = {}
    ) {
        val currentUser = user ?: com.example.data.session.SessionManager.currentUser.value
        if (!shouldShowAds(currentUser)) {
            Log.d(TAG, "Cannot load rewarded ad: user is active SmartMaster.")
            onLoaded(false)
            return
        }
        val appContext = context.applicationContext
        if (!canRequestAds(appContext)) {
            Log.d(TAG, "Cannot load rewarded ad: consent not granted or cannot request ads.")
            onLoaded(false)
            return
        }
        synchronized(adLock) {
            if (rewardedAd != null) {
                onLoaded(true)
                return
            }
            pendingRewardedCallbacks.add(onLoaded)
            if (isRewardedAdLoading) return
            isRewardedAdLoading = true
        }

        try {
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(
                appContext,
                AppConfig.ADMOB_REWARDED_AD_UNIT_ID,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        Log.w(TAG, "Failed to load rewarded ad [code=${adError.code}]: ${adError.message}")
                        val callbacksToNotify: List<(Boolean) -> Unit>
                        synchronized(adLock) {
                            rewardedAd = null
                            isRewardedAdLoading = false
                            callbacksToNotify = ArrayList(pendingRewardedCallbacks)
                            pendingRewardedCallbacks.clear()
                        }
                        callbacksToNotify.forEach { cb ->
                            try { cb(false) } catch (_: Exception) {}
                        }
                    }

                    override fun onAdLoaded(ad: RewardedAd) {
                        Log.d(TAG, "Rewarded ad loaded successfully.")
                        val callbacksToNotify: List<(Boolean) -> Unit>
                        synchronized(adLock) {
                            rewardedAd = ad
                            isRewardedAdLoading = false
                            callbacksToNotify = ArrayList(pendingRewardedCallbacks)
                            pendingRewardedCallbacks.clear()
                        }
                        callbacksToNotify.forEach { cb ->
                            try { cb(true) } catch (_: Exception) {}
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Exception loading rewarded ad: ${e.message}")
            val callbacksToNotify: List<(Boolean) -> Unit>
            synchronized(adLock) {
                rewardedAd = null
                isRewardedAdLoading = false
                callbacksToNotify = ArrayList(pendingRewardedCallbacks)
                pendingRewardedCallbacks.clear()
            }
            callbacksToNotify.forEach { cb ->
                try { cb(false) } catch (_: Exception) {}
            }
        }
    }

    fun loadAndShowRewardedAd(
        activity: Activity,
        user: User? = null,
        onLoading: (Boolean) -> Unit = {},
        onEarnedReward: () -> Unit,
        onAdClosedOrFailed: (String?) -> Unit
    ) {
        val initialUser = user ?: com.example.data.session.SessionManager.currentUser.value
        val expectedUserId = initialUser?.id ?: com.example.data.session.SessionManager.getActiveUserId()

        if (!shouldShowAds(initialUser)) {
            Log.d(TAG, "Rewarded ad skipped: user is an active SmartMaster.")
            onAdClosedOrFailed("Anda sudah memiliki akses SmartMaster aktif tanpa iklan.")
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {
            onAdClosedOrFailed("Activity tidak aktif.")
            return
        }

        val currentAd: RewardedAd?
        synchronized(adLock) {
            currentAd = rewardedAd
        }

        if (currentAd != null) {
            onLoading(false)
            showLoadedRewardedAd(activity, currentAd, expectedUserId, onEarnedReward) {
                onAdClosedOrFailed(null)
            }
            return
        }

        onLoading(true)
        loadRewardedAd(activity.applicationContext, initialUser) { success ->
            onLoading(false)
            val readyAd: RewardedAd?
            synchronized(adLock) {
                readyAd = rewardedAd
            }
            if (success && readyAd != null) {
                showLoadedRewardedAd(activity, readyAd, expectedUserId, onEarnedReward) {
                    onAdClosedOrFailed(null)
                }
            } else {
                onAdClosedOrFailed("Iklan video belum tersedia. Periksa koneksi internet Anda dan coba lagi.")
            }
        }
    }

    private fun showLoadedRewardedAd(
        activity: Activity,
        ad: RewardedAd,
        expectedUserId: Long? = null,
        onEarnedReward: () -> Unit,
        onAdDismissed: () -> Unit
    ) {
        // Revalidate entitlement immediately prior to display
        val activeUser = com.example.data.session.SessionManager.currentUser.value
        if (!shouldShowAds(activeUser)) {
            Log.d(TAG, "Rewarded ad display aborted: user is an active SmartMaster.")
            synchronized(adLock) {
                if (rewardedAd === ad) {
                    rewardedAd = null // Safely dispose stale loaded ad
                }
            }
            onAdDismissed()
            return
        }

        var hasEarnedReward = false
        val appContext = activity.applicationContext
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed.")
                synchronized(adLock) {
                    if (rewardedAd === ad) {
                        rewardedAd = null
                    }
                }
                onAdDismissed()
                if (shouldShowAds(com.example.data.session.SessionManager.currentUser.value)) {
                    loadRewardedAd(appContext)
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Rewarded ad failed to show: ${adError.message}")
                synchronized(adLock) {
                    if (rewardedAd === ad) {
                        rewardedAd = null
                    }
                }
                onAdDismissed()
                if (shouldShowAds(com.example.data.session.SessionManager.currentUser.value)) {
                    loadRewardedAd(appContext)
                }
            }
        }

        try {
            ad.show(activity) { rewardItem ->
                // Account switching defense: verify callback belongs to intended active account
                val currentActiveId = com.example.data.session.SessionManager.getActiveUserId()
                if (expectedUserId != null && currentActiveId != expectedUserId) {
                    Log.w(TAG, "Account switched during ad display! Expected $expectedUserId, active is $currentActiveId. Dropping reward.")
                    return@show
                }
                if (!hasEarnedReward) {
                    hasEarnedReward = true
                    Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                    onEarnedReward()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying rewarded ad: ${e.message}")
            synchronized(adLock) {
                if (rewardedAd === ad) {
                    rewardedAd = null
                }
            }
            onAdDismissed()
            if (shouldShowAds(com.example.data.session.SessionManager.currentUser.value)) {
                loadRewardedAd(appContext)
            }
        }
    }

    fun showRewardedAd(
        activity: Activity,
        onEarnedReward: () -> Unit,
        onAdClosedOrFailed: () -> Unit
    ) {
        loadAndShowRewardedAd(
            activity = activity,
            onLoading = {},
            onEarnedReward = onEarnedReward,
            onAdClosedOrFailed = { onAdClosedOrFailed() }
        )
    }
}
