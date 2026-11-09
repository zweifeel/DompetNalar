package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.viewmodel.AuthViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DompetNalar", appName)
  }

  @Test
  fun testNotificationCenterOpenAndDismissState() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val authViewModel = AuthViewModel(app)
    assertFalse(authViewModel.isNotificationCenterOpen.value)

    authViewModel.openNotificationCenter()
    assertTrue(authViewModel.isNotificationCenterOpen.value)

    authViewModel.closeNotificationCenter()
    assertFalse(authViewModel.isNotificationCenterOpen.value)
  }
}
