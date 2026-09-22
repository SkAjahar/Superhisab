package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Super Hisab", appName)
  }

  @Test
  fun `test calculator widget string resources exist`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val widgetTitle = context.getString(R.string.widget_calc_title)
    assertEquals("Super Hisab Calculator", widgetTitle)
  }
}
