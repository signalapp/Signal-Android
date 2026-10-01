/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testutil.LogRecorder
import org.thoughtcrime.securesms.testutil.SignalStoreRule

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class RemoteConfigOverrideTest {

  companion object {
    private const val INTERNAL_USER = "android.internalUser"
    private const val BOOLEAN_FLAG = "android.contactSharingV2"
    private const val INT_FLAG = "global.pinnedChatLimit"
  }

  @get:Rule
  val signalStore = SignalStoreRule()

  @Before
  fun setup() {
    Log.initialize(LogRecorder())
    RemoteConfig.underTest = true
    reset()
  }

  @After
  fun tearDown() {
    reset()
    RemoteConfig.underTest = false
  }

  @Test
  fun `override wins over the value from the service`() {
    setServiceValues(INTERNAL_USER to true, BOOLEAN_FLAG to false)
    SignalStore.internal.remoteConfigOverrides = mapOf(BOOLEAN_FLAG to "true")

    RemoteConfig.init()

    assertThat(RemoteConfig.contactSharingV2).isTrue()
  }

  @Test
  fun `override string is coerced by the config transformer`() {
    setServiceValues(INTERNAL_USER to true, INT_FLAG to 4)
    SignalStore.internal.remoteConfigOverrides = mapOf(INT_FLAG to "9")

    RemoteConfig.init()

    assertThat(RemoteConfig.pinnedChatLimit).isEqualTo(9)
  }

  @Test
  fun `overrides are ignored for non-internal users`() {
    setServiceValues(INTERNAL_USER to false, BOOLEAN_FLAG to false)
    SignalStore.internal.remoteConfigOverrides = mapOf(BOOLEAN_FLAG to "true")

    RemoteConfig.init()

    assertThat(RemoteConfig.overrides).isEmpty()
    assertThat(RemoteConfig.contactSharingV2).isFalse()
  }

  @Test
  fun `internalUser cannot be overridden`() {
    setServiceValues(INTERNAL_USER to true)
    SignalStore.internal.remoteConfigOverrides = mapOf(INTERNAL_USER to "false")

    RemoteConfig.init()

    assertThat(RemoteConfig.overrides).isEmpty()
    assertThat(RemoteConfig.internalUser).isTrue()
  }

  @Test
  fun `overrides for unknown keys are dropped`() {
    setServiceValues(INTERNAL_USER to true)
    SignalStore.internal.remoteConfigOverrides = mapOf("android.notARealFlag" to "true")

    RemoteConfig.init()

    assertThat(RemoteConfig.overrides).isEmpty()
  }

  @Test
  fun `setting overrides applies immediately`() {
    setServiceValues(INTERNAL_USER to true, BOOLEAN_FLAG to false)
    RemoteConfig.init()

    RemoteConfig.overrides = mapOf(BOOLEAN_FLAG to "true")
    assertThat(RemoteConfig.contactSharingV2).isTrue()

    RemoteConfig.overrides = emptyMap()
    assertThat(RemoteConfig.contactSharingV2).isFalse()
  }

  @Test
  fun `overridableConfigs excludes internalUser`() {
    assertThat(RemoteConfig.overridableConfigs.containsKey(INTERNAL_USER)).isFalse()
    assertThat(RemoteConfig.overridableConfigs.containsKey(BOOLEAN_FLAG)).isTrue()
  }

  @Test
  fun `persisted overrides round-trip through the store`() {
    val overrides = mapOf(BOOLEAN_FLAG to "true", INT_FLAG to "9")

    SignalStore.internal.remoteConfigOverrides = overrides

    assertThat(SignalStore.internal.remoteConfigOverrides).isEqualTo(overrides)
  }

  private fun setServiceValues(vararg values: Pair<String, Any>) {
    val json = JSONObject()
    values.forEach { (key, value) -> json.put(key, value) }
    SignalStore.remoteConfig.currentConfig = json.toString()
  }

  private fun reset() {
    RemoteConfig.overrides = emptyMap()
    RemoteConfig.REMOTE_VALUES.clear()
    RemoteConfig.initialized = false
  }
}
