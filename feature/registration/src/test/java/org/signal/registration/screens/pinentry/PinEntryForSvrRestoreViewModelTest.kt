/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.core.models.MasterKey
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.RestoreDecision

class PinEntryForSvrRestoreViewModelTest {

  private lateinit var viewModel: PinEntryForSvrRestoreViewModel
  private lateinit var mockRepository: RegistrationRepository
  private lateinit var parentState: MutableStateFlow<RegistrationFlowState>
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<PinEntryState>
  private lateinit var stateEmitter: (PinEntryState) -> Unit

  @Before
  fun setup() {
    mockRepository = mockk(relaxed = true)
    parentState = MutableStateFlow(
      RegistrationFlowState(
        sessionMetadata = createSessionMetadata(),
        sessionE164 = "+15551234567"
      )
    )
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
    viewModel = PinEntryForSvrRestoreViewModel(
      repository = mockRepository,
      parentState = parentState,
      parentEventEmitter = parentEventEmitter
    )
  }

  // ==================== PinEntered Success Tests ====================

  @Test
  fun `PinEntered with correct PIN restores master key and completes registration`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val svrCredentials = NetworkController.SvrCredentials(
      username = "test-username",
      password = "test-password"
    )
    val initialState = PinEntryState(mode = PinEntryState.Mode.SvrRestore)

    coEvery { mockRepository.getSvrCredentials() } returns
      RequestResult.Success(svrCredentials)
    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), forRegistrationLock = false) } returns
      RequestResult.Success(NetworkController.MasterKeyResponse(masterKey))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredFromSvr>()
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.RegistrationComplete)
    coVerify { mockRepository.setRestoreDecision(RestoreDecision.COMPLETED) }
    coVerify { mockRepository.restoreAccountRecord(any()) }
    assertThat(emittedStates.last().loading).isEqualTo(true)
  }

  // ==================== GetSvrCredentials Error Tests ====================

  @Test
  fun `PinEntered with NoServiceCredentialsAvailable emits ResetState`() = runTest {
    val initialState = PinEntryState(mode = PinEntryState.Mode.SvrRestore)

    coEvery { mockRepository.getSvrCredentials() } returns
      RequestResult.NonSuccess(
        NetworkController.GetSvrCredentialsError.NoServiceCredentialsAvailable
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
    assertThat(emittedStates.last().loading).isEqualTo(true)
  }

  @Test
  fun `PinEntered with Unauthorized emits ResetState`() = runTest {
    val initialState = PinEntryState(mode = PinEntryState.Mode.SvrRestore)

    coEvery { mockRepository.getSvrCredentials() } returns
      RequestResult.NonSuccess(
        NetworkController.GetSvrCredentialsError.Unauthorized
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
    assertThat(emittedStates.last().loading).isEqualTo(true)
  }

  @Test
  fun `PinEntered with network error getting SVR credentials returns NetworkError event`() = runTest {
    val initialState = PinEntryState(mode = PinEntryState.Mode.SvrRestore)

    coEvery { mockRepository.getSvrCredentials() } returns
      RequestResult.RetryableNetworkError(java.io.IOException("Network error"))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(emittedStates.last().dialogs.networkError).isTrue()
    assertThat(emittedStates.last().loading).isEqualTo(false)
  }

  @Test
  fun `PinEntered with application error getting SVR credentials returns UnknownError event`() = runTest {
    val initialState = PinEntryState(mode = PinEntryState.Mode.SvrRestore)

    coEvery { mockRepository.getSvrCredentials() } returns
      RequestResult.ApplicationError(RuntimeException("Unexpected"))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(emittedStates.last().dialogs.unknownError).isTrue()
    assertThat(emittedStates.last().loading).isEqualTo(false)
  }

  // ==================== RestoreMasterKey Error Tests ====================

  @Test
  fun `PinEntered with wrong PIN returns state with tries remaining`() = runTest {
    val svrCredentials = NetworkController.SvrCredentials(
      username = "test-username",
      password = "test-password"
    )
    val triesRemaining = 3
    val initialState = PinEntryState(mode = PinEntryState.Mode.SvrRestore)

    coEvery { mockRepository.getSvrCredentials() } returns
      RequestResult.Success(svrCredentials)
    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), forRegistrationLock = false) } returns
      RequestResult.NonSuccess(
        NetworkController.RestoreMasterKeyError.WrongPin(triesRemaining)
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("wrong-pin"), parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(emittedStates.last().triesRemaining).isEqualTo(triesRemaining)
    assertThat(emittedStates.last().loading).isEqualTo(false)
  }

  @Test
  fun `PinEntered with no SVR data shows the no-data-to-restore dialog without navigating`() = runTest {
    val svrCredentials = NetworkController.SvrCredentials(
      username = "test-username",
      password = "test-password"
    )
    val initialState = PinEntryState(mode = PinEntryState.Mode.SvrRestore)

    coEvery { mockRepository.getSvrCredentials() } returns
      RequestResult.Success(svrCredentials)
    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), forRegistrationLock = false) } returns
      RequestResult.NonSuccess(
        NetworkController.RestoreMasterKeyError.NoDataFound
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(emittedStates.last().showNoDataToRestoreDialog).isEqualTo(true)
    assertThat(emittedStates.last().loading).isEqualTo(false)
  }

  // ==================== No Data To Restore Dialog Tests ====================

  @Test
  fun `CreateNewPin dismisses the dialog and navigates to PinCreate`() = runTest {
    val initialState = PinEntryState(mode = PinEntryState.Mode.SvrRestore, showNoDataToRestoreDialog = true)

    viewModel.applyEvent(initialState, PinEntryScreenEvents.CreateNewPin, parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().showNoDataToRestoreDialog).isEqualTo(false)
    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinCreate>()
  }

  @Test
  fun `ContactSupport dismisses the dialog without navigating`() = runTest {
    val initialState = PinEntryState(mode = PinEntryState.Mode.SvrRestore, showNoDataToRestoreDialog = true)

    viewModel.applyEvent(initialState, PinEntryScreenEvents.ContactSupport, parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().showNoDataToRestoreDialog).isEqualTo(false)
    assertThat(emittedParentEvents).hasSize(0)
  }

  @Test
  fun `PinEntered with network error restoring master key returns NetworkError event`() = runTest {
    val svrCredentials = NetworkController.SvrCredentials(
      username = "test-username",
      password = "test-password"
    )
    val initialState = PinEntryState(mode = PinEntryState.Mode.SvrRestore)

    coEvery { mockRepository.getSvrCredentials() } returns
      RequestResult.Success(svrCredentials)
    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), forRegistrationLock = false) } returns
      RequestResult.RetryableNetworkError(java.io.IOException("Network error"))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(emittedStates.last().dialogs.networkError).isTrue()
    assertThat(emittedStates.last().loading).isEqualTo(false)
  }

  @Test
  fun `PinEntered with application error restoring master key returns UnknownError event`() = runTest {
    val svrCredentials = NetworkController.SvrCredentials(
      username = "test-username",
      password = "test-password"
    )
    val initialState = PinEntryState(mode = PinEntryState.Mode.SvrRestore)

    coEvery { mockRepository.getSvrCredentials() } returns
      RequestResult.Success(svrCredentials)
    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), forRegistrationLock = false) } returns
      RequestResult.ApplicationError(RuntimeException("Unexpected"))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(emittedStates.last().dialogs.unknownError).isTrue()
    assertThat(emittedStates.last().loading).isEqualTo(false)
  }

  // ==================== Skip Tests ====================

  @Test
  fun `Skip navigates to PinCreate`() = runTest {
    val initialState = PinEntryState(mode = PinEntryState.Mode.SvrRestore)

    viewModel.applyEvent(initialState, PinEntryScreenEvents.Skip, parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinCreate>()
  }

  // ==================== ToggleKeyboard Tests ====================

  @Test
  fun `ToggleKeyboard toggles isAlphanumericKeyboard from false to true`() = runTest {
    val initialState = PinEntryState(isAlphanumericKeyboard = false)

    viewModel.applyEvent(initialState, PinEntryScreenEvents.ToggleKeyboard, parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().isAlphanumericKeyboard).isEqualTo(true)
  }

  @Test
  fun `ToggleKeyboard toggles isAlphanumericKeyboard from true to false`() = runTest {
    val initialState = PinEntryState(isAlphanumericKeyboard = true)

    viewModel.applyEvent(initialState, PinEntryScreenEvents.ToggleKeyboard, parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().isAlphanumericKeyboard).isEqualTo(false)
  }

  // ==================== Helper Functions ====================

  private fun createSessionMetadata(
    id: String = "test-session-id",
    requestedInformation: List<String> = emptyList(),
    verified: Boolean = true
  ) = NetworkController.SessionMetadata(
    id = id,
    nextSms = null,
    nextCall = null,
    nextVerificationAttempt = null,
    allowedToRequestCode = true,
    requestedInformation = requestedInformation,
    verified = verified
  )
}
