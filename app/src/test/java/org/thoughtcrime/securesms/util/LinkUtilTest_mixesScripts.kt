package org.thoughtcrime.securesms.util

import android.app.Application
import android.os.Build
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Character.UnicodeScript only exists from API 24, so LinkUtil falls back to an
 * all-ASCII-or-all-non-ASCII rule on API 23. These tests cover both sides.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class LinkUtilTest_mixesScripts {

  // Dotless ı (U+0131) is Latin, so this domain doesn't mix scripts, but it does
  // mix ASCII and non-ASCII.
  private val kairos = "https://kaıros.com"

  private var originalSdkInt = 0

  @Before
  fun setUp() {
    originalSdkInt = Build.VERSION.SDK_INT
  }

  @After
  fun tearDown() {
    setSdkInt(originalSdkInt)
  }

  @Test
  fun `the test domain contains a dotless i`() {
    assertEquals('ı', kairos[10])
  }

  @Test
  fun `api 24 accepts a single-script domain with ASCII and non-ASCII letters`() {
    setSdkInt(24)
    assertTrue(LinkUtil.isLegalUrl(kairos))
  }

  @Test
  fun `api 23 rejects a domain with ASCII and non-ASCII letters`() {
    setSdkInt(23)
    assertFalse(LinkUtil.isLegalUrl(kairos))
  }

  @Test
  fun `api 23 accepts an all-ASCII domain`() {
    setSdkInt(23)
    assertTrue(LinkUtil.isLegalUrl("https://example.com"))
  }

  @Test
  fun `api 23 accepts an all-non-ASCII domain`() {
    setSdkInt(23)
    assertTrue(LinkUtil.isLegalUrl("https://例子.中国"))
  }

  private fun setSdkInt(sdk: Int) {
    ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
  }
}
