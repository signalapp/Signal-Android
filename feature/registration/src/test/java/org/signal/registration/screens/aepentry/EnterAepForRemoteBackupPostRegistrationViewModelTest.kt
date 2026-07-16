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
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class EnterAepForRemoteBackupPostRegistrationViewModelTest {

  private lateinit var viewModel: EnterAepForRemoteBackupPostRegistrationViewModel
  private lateinit var mockRepository: RegistrationRepository
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<EnterAepState>
  private lateinit var stateEmitter: (EnterAepState) -> Unit

  @Before
  fun setup() {
    mockRepository = mockk(relaxed = true)
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
    viewModel = EnterAepForRemoteBackupPostRegistrationViewModel(
      repository = mockRepository,
      parentEventEmitter = parentEventEmitter
    )
  }

  // ==================== BackupKeyChanged Tests ====================

  @Test
  fun `BackupKeyChanged updates state with new key`() = runTest {
    viewModel.applyEvent(EnterAepState(), EnterAepEvents.BackupKeyChanged(VALID_AEP), stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().backupKey).isEqualTo(VALID_AEP)
  }

  // ==================== Submit Tests ====================

  @Test
  fun `Submit with verified key emits UserSuppliedAepSubmitted then NavigateToScreen with RemoteRestore`() = runTest {
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true)
    coEvery { mockRepository.verifyBackupKeyAssociatedWithAccount(any()) } returns RequestResult.Success(Unit)

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

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
  fun `Submit sets isRegistering true before verification then false`() = runTest {
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true)
    coEvery { mockRepository.verifyBackupKeyAssociatedWithAccount(any()) } returns RequestResult.Success(Unit)

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedStates).hasSize(2)
    assertThat(emittedStates[0].isRegistering).isEqualTo(true)
    assertThat(emittedStates[1].isRegistering).isEqualTo(false)
  }

  @Test
  fun `Submit with incorrect key sets aepValidationError and does not navigate`() = runTest {
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true)
    coEvery { mockRepository.verifyBackupKeyAssociatedWithAccount(any()) } returns
      RequestResult.NonSuccess(NetworkController.VerifyBackupKeyError.IncorrectKey)

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedParentEvents).isEmpty()
    assertThat(emittedStates.last().aepValidationError).isEqualTo(AepValidationError.Incorrect)
  }

  @Test
  fun `Submit with no backup is treated as an incorrect key and does not navigate`() = runTest {
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true)
    coEvery { mockRepository.verifyBackupKeyAssociatedWithAccount(any()) } returns
      RequestResult.NonSuccess(NetworkController.VerifyBackupKeyError.NoBackup)

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedParentEvents).isEmpty()
    assertThat(emittedStates.last().aepValidationError).isEqualTo(AepValidationError.Incorrect)
  }

  @Test
  fun `Submit with rate limited sets registrationError and does not navigate`() = runTest {
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true)
    coEvery { mockRepository.verifyBackupKeyAssociatedWithAccount(any()) } returns
      RequestResult.NonSuccess(NetworkController.VerifyBackupKeyError.RateLimited(30.seconds))

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedParentEvents).isEmpty()
    assertThat(emittedStates.last().registrationError).isEqualTo(RegistrationError.RateLimited)
  }

  @Test
  fun `Submit with network error sets registrationError and does not navigate`() = runTest {
    val initialState = EnterAepState(backupKey = VALID_AEP, isBackupKeyValid = true)
    coEvery { mockRepository.verifyBackupKeyAssociatedWithAccount(any()) } returns
      RequestResult.RetryableNetworkError(IOException("network"))

    viewModel.applyEvent(initialState, EnterAepEvents.Submit, stateEmitter)

    assertThat(emittedParentEvents).isEmpty()
    assertThat(emittedStates.last().registrationError).isEqualTo(RegistrationError.NetworkError)
  }

  // ==================== Cancel Tests ====================

  @Test
  fun `Cancel emits NavigateBack`() = runTest {
    viewModel.applyEvent(EnterAepState(), EnterAepEvents.Cancel, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  // ==================== DismissError Tests ====================

  @Test
  fun `DismissError clears registrationError from state`() = runTest {
    val initialState = EnterAepState(registrationError = RegistrationError.UnknownError)

    viewModel.applyEvent(initialState, EnterAepEvents.DismissError, stateEmitter)

    assertThat(emittedStates.last().registrationError).isNull()
  }

  // ==================== Constants ====================

  companion object {
    private const val VALID_AEP = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t"
  }
}
