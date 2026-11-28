/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.core.util.PlayServicesUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SettingsValues.ForceWebsocketMode
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import kotlin.time.Duration.Companion.days

class FcmRefreshJobTest {

  @get:Rule
  val signalStore = MockSignalStoreRule()

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())

    mockkStatic(PlayServicesUtil::class)
    every { PlayServicesUtil.getPlayServicesStatus(any()) } returns PlayServicesUtil.PlayServicesStatus.SUCCESS

    mockkStatic(AppDependencies::class)
    every { AppDependencies.resetNetwork() } just runs
    every { AppDependencies.startNetwork() } just runs

    every { signalStore.account.fcmTokenLastSetTime } returns System.currentTimeMillis() - 4.days.inWholeMilliseconds
  }

  @After
  fun tearDown() {
    unmockkStatic(PlayServicesUtil::class)
    unmockkStatic(AppDependencies::class)
  }

  @Test
  fun `given fcm has failed for days and the mode is disabled automatically, when the job fails, then forced websocket mode is enabled automatically`() {
    every { signalStore.settings.forceWebsocketMode } returns ForceWebsocketMode.DISABLED_AUTOMATICALLY

    createJob().onFailure()

    verify { signalStore.settings.forceWebsocketMode = ForceWebsocketMode.ENABLED_AUTOMATICALLY }
    verify { AppDependencies.startNetwork() }
  }

  @Test
  fun `given fcm has failed for days and the user disabled the mode, when the job fails, then forced websocket mode is left alone`() {
    every { signalStore.settings.forceWebsocketMode } returns ForceWebsocketMode.DISABLED_BY_USER

    createJob().onFailure()

    verify(exactly = 0) { signalStore.settings.forceWebsocketMode = any() }
    verify(exactly = 0) { AppDependencies.startNetwork() }
  }

  private fun createJob(): FcmRefreshJob {
    return FcmRefreshJob().apply { setContext(mockk<Application>(relaxed = true)) }
  }
}
