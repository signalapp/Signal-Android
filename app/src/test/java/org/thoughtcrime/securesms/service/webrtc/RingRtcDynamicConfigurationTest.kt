/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import android.app.Application
import android.os.Build
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RingRtcDynamicConfigurationTest {

  @get:Rule
  val signalStore = MockSignalStoreRule()

  @Before
  fun setUp() {
    mockkObject(RemoteConfig)
    every { RemoteConfig.useJetPackTelecom } returns true
    every { RemoteConfig.telecomManufacturerAllowlist } returns "google"
    setDevice(manufacturer = "Google", model = "Pixel 10 Pro")
  }

  @After
  fun tearDown() {
    unmockkObject(RemoteConfig)
  }

  @Test
  fun `Given an allowlisted manufacturer, when I check the device, then I expect telecom to be allowed`() {
    assertThat(RingRtcDynamicConfiguration.isTelecomAllowedForDevice()).isTrue()
  }

  @Test
  fun `Given telecom is disabled, when I check an allowlisted device, then I expect telecom to be disallowed`() {
    every { RemoteConfig.useJetPackTelecom } returns false

    assertThat(RingRtcDynamicConfiguration.isTelecomAllowedForDevice()).isFalse()
  }

  @Test
  fun `Given an empty allowlist, when I check the device, then I expect telecom to be disallowed`() {
    every { RemoteConfig.telecomManufacturerAllowlist } returns ""

    assertThat(RingRtcDynamicConfiguration.isTelecomAllowedForDevice()).isFalse()
  }

  @Test
  fun `Given a manufacturer not on the allowlist, when I check the device, then I expect telecom to be disallowed`() {
    setDevice(manufacturer = "samsung", model = "SM-A515U1")

    assertThat(RingRtcDynamicConfiguration.isTelecomAllowedForDevice()).isFalse()
  }

  @Test
  fun `Given an allowlist with different casing and spacing, when I check the device, then I expect telecom to be allowed`() {
    every { RemoteConfig.telecomManufacturerAllowlist } returns "Samsung, GOOGLE"

    assertThat(RingRtcDynamicConfiguration.isTelecomAllowedForDevice()).isTrue()
  }

  @Test
  fun `Given a wildcard allowlist, when I check any device, then I expect telecom to be allowed`() {
    every { RemoteConfig.telecomManufacturerAllowlist } returns "*"
    setDevice(manufacturer = "samsung", model = "SM-A515U1")

    assertThat(RingRtcDynamicConfiguration.isTelecomAllowedForDevice()).isTrue()
  }

  private fun setDevice(manufacturer: String, model: String) {
    ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", manufacturer)
    ReflectionHelpers.setStaticField(Build::class.java, "MODEL", model)
  }
}
