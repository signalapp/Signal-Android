/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Before
import org.junit.Test
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.registration.RegistrationFlowEvent

class EnterAepForLocalBackupViewModelTest {

  private lateinit var viewModel: EnterAepForLocalBackupViewModel
  private lateinit var resultBus: ResultEventBus
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit

  private val resultKey = "test-result-key"

  @Before
  fun setup() {
    resultBus = ResultEventBus()
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    viewModel = EnterAepForLocalBackupViewModel(
      parentEventEmitter = parentEventEmitter,
      resultBus = resultBus,
      resultKey = resultKey
    )
  }

  // ==================== BackupKeyChanged Tests ====================

  @Test
  fun `BackupKeyChanged updates backup key in state`() {
    val testKey = VALID_AEP

    viewModel.onEvent(EnterAepEvents.BackupKeyChanged(testKey))

    assertThat(viewModel.state.value.backupKey).isEqualTo(testKey)
  }

  // ==================== Submit Tests ====================

  @Test
  fun `Submit with valid key sends result via resultBus and emits NavigateBack`() {
    viewModel.onEvent(EnterAepEvents.BackupKeyChanged(VALID_AEP))
    viewModel.onEvent(EnterAepEvents.Submit)

    val result = resultBus.channelMap[resultKey]?.tryReceive()?.getOrNull()
    assertThat(result).isEqualTo(VALID_AEP)
    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `Submit with invalid key does not send result or navigate`() {
    viewModel.onEvent(EnterAepEvents.BackupKeyChanged("too-short"))
    viewModel.onEvent(EnterAepEvents.Submit)

    assertThat(resultBus.channelMap[resultKey]).isNull()
    assertThat(emittedParentEvents).isEmpty()
  }

  // ==================== Cancel Tests ====================

  @Test
  fun `Cancel emits NavigateBack`() {
    viewModel.onEvent(EnterAepEvents.Cancel)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  // ==================== DismissError Tests ====================

  @Test
  fun `DismissError clears registrationError from state`() {
    viewModel.onEvent(EnterAepEvents.DismissError)

    assertThat(viewModel.state.value.registrationError).isNull()
  }

  // ==================== Constants ====================

  companion object {
    private const val VALID_AEP = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t"
  }
}
