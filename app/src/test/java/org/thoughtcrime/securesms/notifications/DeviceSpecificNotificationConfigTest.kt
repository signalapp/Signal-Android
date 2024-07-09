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
class DeviceSpecificNotificationConfigTest {

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
    every { RemoteConfig.deviceSpecificNotificationConfig } returns ""
    DeviceSpecificNotificationConfig.computeConfig() assertIs DeviceSpecificNotificationConfig.Config()
  }

  @Test
  fun `invalid config`() {
    every { RemoteConfig.deviceSpecificNotificationConfig } returns "bad"
    DeviceSpecificNotificationConfig.computeConfig() assertIs DeviceSpecificNotificationConfig.Config()
  }

  @Test
  fun `simple device match`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test")
    every { RemoteConfig.deviceSpecificNotificationConfig } returns """[ { "model": "test", "link": "test.com", "showConditionCode": "always", "localePercent": "*:500000", "version": 3 } ]"""
    DeviceSpecificNotificationConfig.computeConfig() assertIs DeviceSpecificNotificationConfig.Config(model = "test", link = "test.com", showConditionCode = "always", localePercent = "*:500000", version = 3)
  }

  @Test
  fun `complex device match`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test-1")
    every { RemoteConfig.deviceSpecificNotificationConfig } returns
      """
      [
        { "model": "test", "showConditionCode": "always", "localePercent": "*:10000", "version": 1 },
        { "model": "test-1", "showConditionCode": "has-battery-optimization-on", "localePercent": "*:20000", "version": 2 },
        { "model": "test-11", "showConditionCode": "has-slow-notifications", "localePercent": "*:30000", "version": 3 },
        { "model": "test-11*", "showConditionCode": "never", "localePercent": "*:40000", "version": 4 }
      ]
      """.trimMargin()
    DeviceSpecificNotificationConfig.computeConfig() assertIs DeviceSpecificNotificationConfig.Config(model = "test-1", showConditionCode = "has-battery-optimization-on", localePercent = "*:20000", version = 2)
  }

  @Test
  fun `simple wildcard device match`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test1")
    every { RemoteConfig.deviceSpecificNotificationConfig } returns """[ { "model": "test*", "link": "test.com", "showConditionCode": "never", "localePercent": "*:500000", "version": 1 } ]"""
    DeviceSpecificNotificationConfig.currentConfig assertIs DeviceSpecificNotificationConfig.Config(model = "test*", link = "test.com", showConditionCode = "never", localePercent = "*:500000", version = 1)
  }

  @Test
  fun `complex wildcard device match`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test-1")
    every { RemoteConfig.deviceSpecificNotificationConfig } returns
      """
      [
        { "model": "*", "showConditionCode": "always", "localePercent": "*:10000", "version": 1 },
        { "model": "test1", "showConditionCode": "has-slow-notifications", "localePercent": "*:20000", "version": 2 },
        { "model": "test-", "showConditionCode": "never", "localePercent": "*:30000", "version": 3 }
      ]
      """.trimMargin()
    DeviceSpecificNotificationConfig.computeConfig() assertIs DeviceSpecificNotificationConfig.Config(model = "*", showConditionCode = "always", localePercent = "*:10000", version = 1)
  }

  @Test
  fun `no device match`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "bad")
    every { RemoteConfig.deviceSpecificNotificationConfig } returns """[ { "model": "test", "link": "test.com", "showConditionCode": "always", "localePercent": "*:500000", "version": 1 } ]"""
    DeviceSpecificNotificationConfig.computeConfig() assertIs DeviceSpecificNotificationConfig.Config()
  }

  @Test
  fun `default fields is zero percent`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test")
    every { RemoteConfig.deviceSpecificNotificationConfig } returns """[ { "model": "test" } ]"""
    DeviceSpecificNotificationConfig.computeConfig() assertIs DeviceSpecificNotificationConfig.Config(model = "test", localePercent = "*", version = 0)
  }
}
