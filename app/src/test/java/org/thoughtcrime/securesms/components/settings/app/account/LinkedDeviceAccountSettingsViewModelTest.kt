/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.components.settings.app.account.LinkedDeviceAccountSettingsState.OneTimeEvent

@OptIn(ExperimentalCoroutinesApi::class)
class LinkedDeviceAccountSettingsViewModelTest {

  companion object {
    private const val SELF_DEVICE_ID = 3
  }

  private val testDispatcher = StandardTestDispatcher()

  private lateinit var emittedStates: MutableList<LinkedDeviceAccountSettingsState>
  private lateinit var stateEmitter: (LinkedDeviceAccountSettingsState) -> Unit

  private var removedDeviceId: Int? = null

  private lateinit var viewModel: LinkedDeviceAccountSettingsViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
    removedDeviceId = null
    viewModel = LinkedDeviceAccountSettingsViewModel(
      selfDeviceId = { SELF_DEVICE_ID },
      removeDevice = { deviceId ->
        removedDeviceId = deviceId
        true
      }
    )
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `DeleteAppDataClicked shows the confirmation dialog`() = runTest {
    viewModel.applyEvent(LinkedDeviceAccountSettingsState(), LinkedDeviceAccountSettingsEvent.DeleteAppDataClicked, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().showDeleteConfirmationDialog).isTrue()
  }

  @Test
  fun `DeleteDismissed hides the confirmation dialog`() = runTest {
    viewModel.applyEvent(LinkedDeviceAccountSettingsState(showDeleteConfirmationDialog = true), LinkedDeviceAccountSettingsEvent.DeleteDismissed, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().showDeleteConfirmationDialog).isFalse()
  }

  @Test
  fun `LearnMoreClicked requests the learn more event`() = runTest {
    viewModel.applyEvent(LinkedDeviceAccountSettingsState(), LinkedDeviceAccountSettingsEvent.LearnMoreClicked, stateEmitter)

    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(OneTimeEvent.OpenLearnMore)
  }

  @Test
  fun `NavigateBackClicked requests the navigate back event`() = runTest {
    viewModel.applyEvent(LinkedDeviceAccountSettingsState(), LinkedDeviceAccountSettingsEvent.NavigateBackClicked, stateEmitter)

    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(OneTimeEvent.NavigateBack)
  }

  @Test
  fun `DeleteConfirmed unlinks this device then requests the wipe`() = runTest {
    viewModel.applyEvent(LinkedDeviceAccountSettingsState(showDeleteConfirmationDialog = true), LinkedDeviceAccountSettingsEvent.DeleteConfirmed, stateEmitter)

    assertThat(emittedStates).hasSize(2)

    val deletingState = emittedStates.first()
    assertThat(deletingState.showDeleteConfirmationDialog).isFalse()
    assertThat(deletingState.deleting).isTrue()
    assertThat(deletingState.oneTimeEvent).isNull()

    val wipeState = emittedStates.last()
    assertThat(wipeState.deleting).isTrue()
    assertThat(wipeState.oneTimeEvent).isEqualTo(OneTimeEvent.WipeData)

    assertThat(removedDeviceId).isEqualTo(SELF_DEVICE_ID)
  }

  @Test
  fun `DataWipeFailed clears the spinner and reports the failure`() = runTest {
    viewModel.applyEvent(LinkedDeviceAccountSettingsState(deleting = true), LinkedDeviceAccountSettingsEvent.DataWipeFailed, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().deleting).isFalse()
    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(OneTimeEvent.DeleteFailed)
  }

  @Test
  fun `ConsumeOneTimeEvent clears the pending event`() = runTest {
    viewModel.applyEvent(LinkedDeviceAccountSettingsState(oneTimeEvent = OneTimeEvent.WipeData), LinkedDeviceAccountSettingsEvent.ConsumeOneTimeEvent, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().oneTimeEvent).isNull()
  }
}
