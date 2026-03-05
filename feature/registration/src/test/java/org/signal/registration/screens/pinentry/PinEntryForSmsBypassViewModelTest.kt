/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.core.models.MasterKey
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class PinEntryForSmsBypassViewModelTest {

  private lateinit var viewModel: PinEntryForSmsBypassViewModel
  private lateinit var mockRepository: RegistrationRepository
  private lateinit var parentState: MutableStateFlow<RegistrationFlowState>
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<PinEntryState>
  private lateinit var stateEmitter: (PinEntryState) -> Unit

  private val testSvrCredentials = NetworkController.SvrCredentials(
    username = "test-username",
    password = "test-password"
  )

  @Before
  fun setup() {
    mockRepository = mockk(relaxed = true)
    parentState = MutableStateFlow(
      RegistrationFlowState(
        sessionE164 = "+15551234567"
      )
    )
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
    viewModel = PinEntryForSmsBypassViewModel(
      repository = mockRepository,
      parentState = parentState,
      parentEventEmitter = parentEventEmitter,
      svrCredentials = testSvrCredentials
    )
  }

  // ==================== PinEntered - Restore Master Key Tests ====================

  @Test
  fun `PinEntered with correct PIN restores master key and registers successfully`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(mockk(relaxed = true))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredFromSvr>()
    assertThat(emittedParentEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.FullyComplete>()
  }

  @Test
  fun `PinEntered with correct PIN enqueues SVR guess reset job after successful registration`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(mockk(relaxed = true))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    coVerify { mockRepository.enqueueSvrResetGuessCountJob() }
  }

  @Test
  fun `PinEntered with wrong PIN returns state with tries remaining`() = runTest {
    val triesRemaining = 3
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RestoreMasterKeyError.WrongPin(triesRemaining)
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("wrong-pin"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(emittedStates.last().triesRemaining).isEqualTo(triesRemaining)
  }

  @Test
  fun `PinEntered with no SVR data emits RecoveryPasswordInvalid and navigates back`() = runTest {
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RestoreMasterKeyError.NoDataFound
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0]).isEqualTo(RegistrationFlowEvent.RecoveryPasswordInvalid)
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `PinEntered with network error restoring master key returns NetworkError event`() = runTest {
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.NetworkError(IOException("Network error"))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PinEntryState.OneTimeEvent.NetworkError)
  }

  @Test
  fun `PinEntered with application error restoring master key returns UnknownError event`() = runTest {
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.ApplicationError(RuntimeException("Unexpected"))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PinEntryState.OneTimeEvent.UnknownError)
  }

  @Test
  fun `PinEntered with missing e164 emits ResetState`() = runTest {
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = null)

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents[0]).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  // ==================== Registration Error Tests ====================

  @Test
  fun `PinEntered with registration network error returns NetworkError event`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.NetworkError(IOException("Network error"))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredFromSvr>()
    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PinEntryState.OneTimeEvent.NetworkError)
  }

  @Test
  fun `PinEntered with registration application error returns UnknownError event`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.ApplicationError(RuntimeException("Unexpected"))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredFromSvr>()
    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PinEntryState.OneTimeEvent.UnknownError)
  }

  @Test
  fun `PinEntered with DeviceTransferPossible during registration emits ResetState`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.DeviceTransferPossible
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredFromSvr>()
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `PinEntered with InvalidRequest during registration emits RecoveryPasswordInvalid and navigates back`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.InvalidRequest("Bad request")
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(3)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredFromSvr>()
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.RecoveryPasswordInvalid)
    assertThat(emittedParentEvents[2]).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `PinEntered with RateLimited during registration returns RateLimited event`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val retryAfter = 30.seconds
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.RateLimited(retryAfter)
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredFromSvr>()
    assertThat(emittedStates.last().oneTimeEvent).isNotNull()
      .isInstanceOf<PinEntryState.OneTimeEvent.RateLimited>()
      .prop(PinEntryState.OneTimeEvent.RateLimited::retryAfter)
      .isEqualTo(retryAfter)
  }

  @Test
  fun `PinEntered with RegistrationLock without provideRegistrationLock retries with reglock`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val registrationLockData = mockk<NetworkController.RegistrationLockResponse>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    // First call (without reglock) returns RegistrationLock error, second call (with reglock) succeeds
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), registrationLock = null, any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.RegistrationLock(registrationLockData)
      )
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), registrationLock = any<String>(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(mockk(relaxed = true))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredFromSvr>()
    assertThat(emittedParentEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.FullyComplete>()
  }

  @Test
  fun `PinEntered with RegistrationLock when already providing reglock emits RecoveryPasswordInvalid and navigates back`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val registrationLockData = mockk<NetworkController.RegistrationLockResponse>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    // Both calls (with and without reglock) return RegistrationLock error
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.RegistrationLock(registrationLockData)
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    // First retry (without reglock) -> reglock error -> retry with reglock -> reglock error again -> RecoveryPasswordInvalid + NavigateBack
    assertThat(emittedParentEvents).hasSize(3)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredFromSvr>()
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.RecoveryPasswordInvalid)
    assertThat(emittedParentEvents[2]).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `PinEntered with RegistrationRecoveryPasswordIncorrect emits RecoveryPasswordInvalid and navigates back`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect("Wrong password")
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(3)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredFromSvr>()
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.RecoveryPasswordInvalid)
    assertThat(emittedParentEvents[2]).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `PinEntered with SessionNotFoundOrNotVerified during registration emits ResetState`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = false) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.SessionNotFoundOrNotVerified("Not found")
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredFromSvr>()
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  // ==================== applyParentState Tests ====================

  @Test
  fun `applyParentState copies e164 from parent state`() {
    val state = PinEntryState(mode = PinEntryState.Mode.SmsBypass)
    val parentFlowState = RegistrationFlowState(sessionE164 = "+15559876543")

    val result = viewModel.applyParentState(state, parentFlowState)

    assertThat(result.e164).isEqualTo("+15559876543")
  }

  @Test
  fun `applyParentState with null e164 in parent state sets null e164`() {
    val state = PinEntryState(mode = PinEntryState.Mode.SmsBypass, e164 = "+15551234567")
    val parentFlowState = RegistrationFlowState(sessionE164 = null)

    val result = viewModel.applyParentState(state, parentFlowState)

    assertThat(result.e164).isEqualTo(null)
  }

  // ==================== ToggleKeyboard Tests ====================

  @Test
  fun `ToggleKeyboard toggles isAlphanumericKeyboard from false to true`() = runTest {
    val initialState = PinEntryState(isAlphanumericKeyboard = false)

    viewModel.applyEvent(initialState, PinEntryScreenEvents.ToggleKeyboard, stateEmitter, parentEventEmitter)

    assertThat(emittedStates.last().isAlphanumericKeyboard).isEqualTo(true)
  }

  @Test
  fun `ToggleKeyboard toggles isAlphanumericKeyboard from true to false`() = runTest {
    val initialState = PinEntryState(isAlphanumericKeyboard = true)

    viewModel.applyEvent(initialState, PinEntryScreenEvents.ToggleKeyboard, stateEmitter, parentEventEmitter)

    assertThat(emittedStates.last().isAlphanumericKeyboard).isEqualTo(false)
  }
}
