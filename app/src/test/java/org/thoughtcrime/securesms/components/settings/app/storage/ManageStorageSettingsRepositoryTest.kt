/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import android.app.Application
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.managestorage.ChatLengthLimitSettingsState
import org.signal.core.models.storage.KeepMessagesDuration
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ManageStorageSettingsRepositoryTest {

  @get:Rule
  val signalStore = MockSignalStoreRule()

  private val threads = mockk<ThreadTable>(relaxUnitFun = true)
  private val repository = ManageStorageSettingsRepository(scope = CoroutineScope(UnconfinedTestDispatcher()))

  @Before
  fun setUp() {
    mockkObject(SignalDatabase.Companion)
    every { SignalDatabase.threads } returns threads
  }

  @After
  fun tearDown() {
    unmockkObject(SignalDatabase.Companion)
  }

  @Test
  fun `getLengthLimit returns the stored length when trimming by length is enabled`() {
    every { signalStore.settings.isTrimByLengthEnabled } returns true
    every { signalStore.settings.threadTrimLength } returns 500

    assertThat(repository.getLengthLimit()).isEqualTo(500)
  }

  @Test
  fun `getLengthLimit returns NO_LIMIT when trimming by length is disabled`() {
    every { signalStore.settings.isTrimByLengthEnabled } returns false
    every { signalStore.settings.threadTrimLength } returns 500

    assertThat(repository.getLengthLimit()).isEqualTo(ChatLengthLimitSettingsState.NO_LIMIT)
  }

  @Test
  fun `setLengthLimit with a limit enables trimming and stores the length`() {
    repository.setLengthLimit(1000)

    verify { signalStore.settings.isTrimByLengthEnabled = true }
    verify { signalStore.settings.threadTrimLength = 1000 }
  }

  @Test
  fun `setLengthLimit with NO_LIMIT disables trimming`() {
    repository.setLengthLimit(ChatLengthLimitSettingsState.NO_LIMIT)

    verify { signalStore.settings.isTrimByLengthEnabled = false }
    verify { signalStore.settings.threadTrimLength = ChatLengthLimitSettingsState.NO_LIMIT }
  }

  @Test
  fun `trimAllThreads does not trim by date when messages are kept forever`() {
    every { signalStore.settings.keepMessagesDuration } returns KeepMessagesDuration.FOREVER

    repository.trimAllThreads(1000)

    verify { threads.trimAllThreads(1000, ThreadTable.NO_TRIM_BEFORE_DATE_SET) }
  }

  @Test
  fun `trimAllThreads also trims by date when messages have a keep duration`() {
    every { signalStore.settings.keepMessagesDuration } returns KeepMessagesDuration.THIRTY_DAYS
    val trimBeforeDate = slot<Long>()

    val before = System.currentTimeMillis()
    repository.trimAllThreads(1000)
    val after = System.currentTimeMillis()

    verify { threads.trimAllThreads(1000, capture(trimBeforeDate)) }
    assertThat(trimBeforeDate.captured).isBetween(before - KeepMessagesDuration.THIRTY_DAYS.duration, after - KeepMessagesDuration.THIRTY_DAYS.duration)
  }
}
