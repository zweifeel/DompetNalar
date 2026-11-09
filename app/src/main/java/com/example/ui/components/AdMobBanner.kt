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

    val currentContext = androidx.compose.ui.platform.LocalContext.current
    if (!AdManager.isEmulatorOrVirtualDisplay() && !com.example.util.ConsentManager.canRequestAds(currentContext)) {
        return
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            factory = { context ->
                val adContext = (context as? android.app.Activity) ?: context
                try {
                    AdView(adContext).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = AppConfig.ADMOB_BANNER_AD_UNIT_ID
                        adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                Log.i("AdMobBanner", "Banner ad loaded successfully [adUnit=${AppConfig.ADMOB_BANNER_AD_UNIT_ID}].")
                            }
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                Log.w("AdMobBanner", "Banner ad failed to load: code=${error.code}, message=${error.message}, domain=${error.domain}")
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                } catch (e: Exception) {
                    Log.w("AdMobBanner", "Error instantiating AdView: ${e.message}")
                    android.view.View(context)
                }
            },
            onRelease = { view ->
                try {
                    (view as? AdView)?.destroy()
                } catch (_: Exception) {}
            }
        )
    }
}
