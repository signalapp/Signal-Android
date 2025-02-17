package org.thoughtcrime.securesms.notifications

import android.app.Application
import android.os.Build
import assertk.assertThat
import assertk.assertions.isEqualTo
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
    assertThat(DeviceSpecificNotificationConfig.computeConfig()).isEqualTo(DeviceSpecificNotificationConfig.Config())
  }

  @Test
  fun `invalid config`() {
    every { RemoteConfig.deviceSpecificNotificationConfig } returns "bad"
    assertThat(DeviceSpecificNotificationConfig.computeConfig()).isEqualTo(DeviceSpecificNotificationConfig.Config())
  }

  @Test
  fun `simple device match`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test")
    every { RemoteConfig.deviceSpecificNotificationConfig } returns """[ { "model": "test", "link": "test.com", "showConditionCode": "always", "localePercent": "*:500000", "version": 3 } ]"""
    assertThat(DeviceSpecificNotificationConfig.computeConfig())
      .isEqualTo(DeviceSpecificNotificationConfig.Config(model = "test", link = "test.com", showConditionCode = "always", localePercent = "*:500000", version = 3))
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
    assertThat(DeviceSpecificNotificationConfig.computeConfig())
      .isEqualTo(DeviceSpecificNotificationConfig.Config(model = "test-1", showConditionCode = "has-battery-optimization-on", localePercent = "*:20000", version = 2))
  }

  @Test
  fun `simple wildcard device match`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test1")
    every { RemoteConfig.deviceSpecificNotificationConfig } returns """[ { "model": "test*", "link": "test.com", "showConditionCode": "never", "localePercent": "*:500000", "version": 1 } ]"""
    assertThat(DeviceSpecificNotificationConfig.currentConfig)
      .isEqualTo(DeviceSpecificNotificationConfig.Config(model = "test*", link = "test.com", showConditionCode = "never", localePercent = "*:500000", version = 1))
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
    assertThat(DeviceSpecificNotificationConfig.computeConfig())
      .isEqualTo(DeviceSpecificNotificationConfig.Config(model = "*", showConditionCode = "always", localePercent = "*:10000", version = 1))
  }

  @Test
  fun `no device match`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "bad")
    every { RemoteConfig.deviceSpecificNotificationConfig } returns """[ { "model": "test", "link": "test.com", "showConditionCode": "always", "localePercent": "*:500000", "version": 1 } ]"""
    assertThat(DeviceSpecificNotificationConfig.computeConfig())
      .isEqualTo(DeviceSpecificNotificationConfig.Config())
  }

  @Test
  fun `default fields is zero percent`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test")
    every { RemoteConfig.deviceSpecificNotificationConfig } returns """[ { "model": "test" } ]"""
    assertThat(DeviceSpecificNotificationConfig.computeConfig())
      .isEqualTo(DeviceSpecificNotificationConfig.Config(model = "test", localePercent = "*", version = 0))
  }

  @Test
  fun `manufacturer match`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test-model")
    ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "test-manufacturer")
    every { RemoteConfig.deviceSpecificNotificationConfig } returns
      """
      [
        { "manufacturer": "test-manufacturer", "showConditionCode": "always", "localePercent": "*:10000", "version": 1 }
      ]
      """.trimMargin()
    assertThat(DeviceSpecificNotificationConfig.computeConfig())
      .isEqualTo(DeviceSpecificNotificationConfig.Config(manufacturer = "test-manufacturer", showConditionCode = "always", localePercent = "*:10000", version = 1))
  }

  @Test
  fun `device model is matched over device manufacturer`() {
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "test-model")
    ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "test-manufacturer")
    every { RemoteConfig.deviceSpecificNotificationConfig } returns
      """
      [
        { "manufacturer": "test-manufacturer", "showConditionCode": "always", "localePercent": "*:10000", "version": 1 },
        { "model": "test-model", "showConditionCode": "has-battery-optimization-on", "localePercent": "*:20000", "version": 2 }
      ]
      """.trimMargin()
    assertThat(DeviceSpecificNotificationConfig.computeConfig())
      .isEqualTo(DeviceSpecificNotificationConfig.Config(model = "test-model", showConditionCode = "has-battery-optimization-on", localePercent = "*:20000", version = 2))
  }
}
