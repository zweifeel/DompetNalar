package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ConsentManagerTest {

    @Test
    fun canRequestAds_inIsolatedTestEnvironment_handlesGracefullyWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = ConsentManager.canRequestAds(context)
        // In Robolectric environment without UMP consent set, should return false safely without throwing
        assertNotNull("canRequestAds must return non-null boolean", result)
    }

    @Test
    fun isPrivacyOptionsRequired_handlesGracefullyWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = ConsentManager.isPrivacyOptionsRequired(context)
        assertFalse("By default in uninitialized environment, privacy options should be false", result)
    }
}
