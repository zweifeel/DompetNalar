package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.SleepyKoalaStartupScreen
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SleepyKoalaStartupScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSleepyKoalaStartupScreen_rendersBrandAndSubtitle() {
        var completed = false

        val config = android.content.res.Configuration().apply {
            orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }
        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides config
            ) {
                SleepyKoalaStartupScreen(
                    durationMs = 10000L,
                    onLoadingComplete = { completed = true }
                )
            }
        }

        // Verify test tag and brand text are present
        composeTestRule.onNodeWithTag("sleepykoala_startup_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("DOMPETNALAR").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cerdas Finansial Tanpa Beban").assertIsDisplayed()
        composeTestRule.onNodeWithText("Menyiapkan DompetNalar...").assertExists()
    }

    @Test
    fun testStartupResources_areValidDrawables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        val logoDrawable = context.getDrawable(R.drawable.dompetnalar_startup_logo)
        assertNotNull("Static fallback logo drawable must exist", logoDrawable)

        val mascotDrawable = context.getDrawable(R.drawable.sleepykoala_mascot_logo)
        assertNotNull("Mascot mark drawable must exist", mascotDrawable)
    }

    @Test
    fun testActiveIcons_remainUntouched() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Verify default launcher mipmaps remain active and present
        val defaultLauncher = context.resources.getIdentifier("ic_launcher", "mipmap", context.packageName)
        assertTrue("Active default ic_launcher mipmap must exist", defaultLauncher != 0)

        val defaultRound = context.resources.getIdentifier("ic_launcher_round", "mipmap", context.packageName)
        assertTrue("Active default ic_launcher_round mipmap must exist", defaultRound != 0)
    }
}
