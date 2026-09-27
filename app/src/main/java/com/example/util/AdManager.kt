package com.example.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.config.AppConfig
import com.example.data.model.User
import com.example.data.model.UserRole
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AdManager {
    private const val TAG = "AdManager"
    private var rewardedAd: RewardedAd? = null
    private var isRewardedAdLoading = false

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
            Log.d(TAG, "Virtual/Emulator environment detected - AdServices IPC disabled to prevent service binding errors.")
            return
        }
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val requestConfig = com.google.android.gms.ads.RequestConfiguration.Builder()
                        .setTagForChildDirectedTreatment(com.google.android.gms.ads.RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
                        .build()
                    MobileAds.setRequestConfiguration(requestConfig)
                    MobileAds.initialize(context.applicationContext) { status ->
                        Log.d(TAG, "AdMob SDK Initialized: ${status.adapterStatusMap}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AdMob SDK background init non-fatal note: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Non-fatal error scheduling MobileAds init: ${e.message}")
        }
    }

    fun shouldShowAds(user: User?): Boolean {
        // Strict Policy:
        // 1. 100% hidden if IS_PLAY_STORE_BUILD = true
        if (AppConfig.IS_PLAY_STORE_BUILD) return false

        // 2. 100% hidden if user is Smart Master or Master Developer
        val isPremium = user?.role == UserRole.SMART_MASTER || user?.role == UserRole.MASTER_DEV
        if (isPremium) return false

        // 3. Skip native rendering in emulator to prevent Mesa render node errors
        if (isEmulatorOrVirtualDisplay()) return false

        return true
    }

    fun loadRewardedAd(context: Context, onLoaded: (Boolean) -> Unit = {}) {
        if (isEmulatorOrVirtualDisplay()) {
            onLoaded(true)
            return
        }
        if (rewardedAd != null) {
            onLoaded(true)
            return
        }
        if (isRewardedAdLoading) return

        isRewardedAdLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            AppConfig.ADMOB_REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Failed to load rewarded ad: ${adError.message}")
                    rewardedAd = null
                    isRewardedAdLoading = false
                    onLoaded(false)
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded successfully.")
                    rewardedAd = ad
                    isRewardedAdLoading = false
                    onLoaded(true)
                }
            }
        )
    }

    fun showRewardedAd(
        activity: Activity,
        onEarnedReward: () -> Unit,
        onAdClosedOrFailed: () -> Unit
    ) {
        if (isEmulatorOrVirtualDisplay()) {
            // Emulate rewarding for seamless testing in virtual environments
            onEarnedReward()
            onAdClosedOrFailed()
            return
        }
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad dismissed.")
                    rewardedAd = null
                    onAdClosedOrFailed()
                    // Pre-load next ad
                    loadRewardedAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    Log.e(TAG, "Rewarded ad failed to show: ${adError.message}")
                    rewardedAd = null
                    onAdClosedOrFailed()
                }
            }
            ad.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onEarnedReward()
            }
        } else {
            Log.e(TAG, "Rewarded ad not loaded yet.")
            onAdClosedOrFailed()
            loadRewardedAd(activity)
        }
    }

    fun isRewardedAdAvailable(): Boolean {
        if (isEmulatorOrVirtualDisplay()) return true
        return rewardedAd != null
    }
}
