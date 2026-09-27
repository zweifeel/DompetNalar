package com.example.ui.components

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.config.AppConfig
import com.example.data.model.User
import com.example.util.AdManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

@Composable
fun AdMobBanner(
    user: User?,
    modifier: Modifier = Modifier
) {
    if (!AdManager.shouldShowAds(user)) {
        return
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = { context ->
            try {
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = AppConfig.ADMOB_BANNER_AD_UNIT_ID
                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.d("AdMobBanner", "Banner ad failed to load gracefully: ${error.message}")
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            } catch (e: Exception) {
                Log.w("AdMobBanner", "Error instantiating AdView: ${e.message}")
                android.view.View(context)
            }
        }
    )
}
