package org.thoughtcrime.securesms.notifications

import android.app.Application
import android.os.Build
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.thoughtcrime.securesms.assertIs
import org.thoughtcrime.securesms.util.RemoteConfig

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class DelayedNotificationConfigTest {

  @Before
  fun setup() {
    mockkObject(RemoteConfig)
  }

  @After
  fun tearDown() {
    unmockkObject(RemoteConfig)
  }

  @Test
  fun `empty config`() {
    every { RemoteConfig.promptDelayedNotificationConfig } returns ""
    DelayedNotificationConfig.computeConfig() assertIs DelayedNotificationConfig.Config()
  }

  @Test
  fun `invalid config`() {
    every { RemoteConfig.promptDelayedNotificationConfig } returns "bad"
    DelayedNotificationConfig.computeConfig() assertIs DelayedNotificationConfig.Config()
  }

  @Test
  fun `simple device match`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test")
    every { RemoteConfig.promptDelayedNotificationConfig } returns """[ { "model": "test", "link": "test.com", "showPreemptively": true, "localePercent": "*:500000" } ]"""
    DelayedNotificationConfig.computeConfig() assertIs DelayedNotificationConfig.Config(model = "test", link = "test.com", showPreemptively = true, localePercent = "*:500000")
  }

  @Test
  fun `complex device match`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test-1")
    every { RemoteConfig.promptDelayedNotificationConfig } returns
      """
      [
        { "model": "test", "showPreemptively": false, "localePercent": "*:10000" },
        { "model": "test-1", "showPreemptively": true, "localePercent": "*:20000" },
        { "model": "test-11", "showPreemptively": false, "localePercent": "*:30000" },
        { "model": "test-11*", "showPreemptively": false, "localePercent": "*:40000" }
      ]
      """.trimMargin()
    DelayedNotificationConfig.computeConfig() assertIs DelayedNotificationConfig.Config(model = "test-1", showPreemptively = true, localePercent = "*:20000")
  }

  @Test
  fun `simple wildcard device match`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test1")
    every { RemoteConfig.promptDelayedNotificationConfig } returns """[ { "model": "test*", "link": "test.com", "showPreemptively": true, "localePercent": "*:500000" } ]"""
    DelayedNotificationConfig.currentConfig assertIs DelayedNotificationConfig.Config(model = "test*", link = "test.com", showPreemptively = true, localePercent = "*:500000")
  }

  @Test
  fun `complex wildcard device match`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test-1")
    every { RemoteConfig.promptDelayedNotificationConfig } returns
      """
      [
        { "model": "*", "showPreemptively": false, "localePercent": "*:10000" },
        { "model": "test1", "showPreemptively": false, "localePercent": "*:20000" },
        { "model": "test-", "showPreemptively": false, "localePercent": "*:30000" }
      ]
      """.trimMargin()
    DelayedNotificationConfig.computeConfig() assertIs DelayedNotificationConfig.Config(model = "*", showPreemptively = false, localePercent = "*:10000")
  }

  @Test
  fun `no device match`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "bad")
    every { RemoteConfig.promptDelayedNotificationConfig } returns """[ { "model": "test", "link": "test.com", "showPreemptively": true, "localePercent": "*:500000" } ]"""
    DelayedNotificationConfig.computeConfig() assertIs DelayedNotificationConfig.Config()
  }

  @Test
  fun `default fields is zero percent`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test")
    every { RemoteConfig.promptDelayedNotificationConfig } returns """[ { "model": "test" } ]"""
    DelayedNotificationConfig.computeConfig() assertIs DelayedNotificationConfig.Config(model = "test", showPreemptively = false, localePercent = "*")
  }
}
