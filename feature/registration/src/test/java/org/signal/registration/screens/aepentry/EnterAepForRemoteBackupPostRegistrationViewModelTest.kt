/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.prop
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRoute

class EnterAepForRemoteBackupPostRegistrationViewModelTest {

  private lateinit var viewModel: EnterAepForRemoteBackupPostRegistrationViewModel
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit

  @Before
  fun setup() {
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    viewModel = EnterAepForRemoteBackupPostRegistrationViewModel(
      parentEventEmitter = parentEventEmitter
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
  fun `Submit with valid key emits UserSuppliedAepSubmitted then NavigateToScreen with RemoteRestore`() {
    viewModel.onEvent(EnterAepEvents.BackupKeyChanged(VALID_AEP))
    viewModel.onEvent(EnterAepEvents.Submit)

    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0])
      .isInstanceOf<RegistrationFlowEvent.UserSuppliedAepSubmitted>()
      .prop(RegistrationFlowEvent.UserSuppliedAepSubmitted::aep)
      .prop(AccountEntropyPool::value)
      .isEqualTo(VALID_AEP)
    assertThat(emittedParentEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.RemoteRestore>()
  }

  @Test
  fun `Submit with invalid key emits nothing`() {
    viewModel.onEvent(EnterAepEvents.BackupKeyChanged("too-short"))
    viewModel.onEvent(EnterAepEvents.Submit)

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
