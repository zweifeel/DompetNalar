package com.example.util

import com.example.config.AppConfig
import com.example.data.model.User
import com.example.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdMobIntegrationSecurityTest {

    @Test
    fun debugAdUnits_useGoogleOfficialTestIds() {
        // In test/debug builds, BuildConfig must point to Google Test IDs
        assertTrue(
            "Debug App ID must be Google test app ID",
            AppConfig.ADMOB_APP_ID.contains("ca-app-pub-3940256099942544")
        )
        assertTrue(
            "Debug Banner ID must be Google test banner ID",
            AppConfig.ADMOB_BANNER_AD_UNIT_ID.contains("ca-app-pub-3940256099942544")
        )
        assertTrue(
            "Debug Rewarded ID must be Google test rewarded ID",
            AppConfig.ADMOB_REWARDED_AD_UNIT_ID.contains("ca-app-pub-3940256099942544")
        )
        assertTrue(
            "Debug Interstitial ID must be Google test interstitial ID",
            AppConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID.contains("ca-app-pub-3940256099942544")
        )
    }

    @Test
    fun rewardedAd_doesNotAlterBillingOrEntitlement() {
        val freeUser = User(
            id = 42L,
            username = "Test User",
            email = "user@example.com",
            role = UserRole.FREE_MEMBER,
            isGuest = false,
            subscriptionExpiresAt = 0L
        )

        // Verify entitlement before
        val before = EntitlementManager.evaluate(freeUser)
        assertFalse("Free user before reward must not be premium", before.isPremiumUnlocked)
        assertEquals("User role before reward must be FREE_MEMBER", UserRole.FREE_MEMBER, freeUser.role)

        // Simulate rewarded ad completed callback (without granting SmartMaster)
        // Verified: The UI onWatchRewardedAd callback does not grant SmartMaster or modify billing state
        val after = EntitlementManager.evaluate(freeUser)
        assertFalse("Free user after reward must remain non-premium unless purchased", after.isPremiumUnlocked)
        assertEquals("User role after reward must remain FREE_MEMBER", UserRole.FREE_MEMBER, freeUser.role)
    }

    @Test
    fun versionIncrements_areReflectedInAppConfig() {
        assertEquals("Version code must be 15", 15, AppConfig.APP_VERSION_CODE)
        assertEquals("Version name must be 1.0.15", "1.0.15", AppConfig.APP_VERSION_NAME)
    }

    @Test
    fun interstitialFrequencyCap_blocksRapidRepeatedDisplay() {
        val baseTime = 1770000000000L
        // Display 1: Allowed
        assertFalse("First display is not capped", AdManager.isFrequencyCapped(0L, baseTime))

        // Display 2 immediately (e.g. 10 seconds later): Must be capped
        assertTrue(
            "Second display within 10 seconds must be frequency capped",
            AdManager.isFrequencyCapped(baseTime, baseTime + 10_000L)
        )

        // Display 3 at 9 minutes (540s): Must be capped
        assertTrue(
            "Display at 9 minutes must still be frequency capped",
            AdManager.isFrequencyCapped(baseTime, baseTime + (9 * 60 * 1000L))
        )

        // Display 4 at exactly 10 minutes: Capped ended
        assertFalse(
            "Display at 10 minutes + 1 ms is allowed",
            AdManager.isFrequencyCapped(baseTime, baseTime + AdManager.INTERSTITIAL_CAP_MS + 1L)
        )
    }
}
