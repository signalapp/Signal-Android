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
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.core.models.MasterKey
import org.signal.registration.KeyMaterial
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import kotlin.time.Duration.Companion.seconds

class PinEntryForRegistrationLockViewModelTest {

  private lateinit var viewModel: PinEntryForRegistrationLockViewModel
  private lateinit var mockRepository: RegistrationRepository
  private lateinit var parentState: MutableStateFlow<RegistrationFlowState>
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<PinEntryState>
  private lateinit var stateEmitter: (PinEntryState) -> Unit

  private val testTimeRemaining = 60000L

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
    viewModel = PinEntryForRegistrationLockViewModel(
      repository = mockRepository,
      parentState = parentState,
      parentEventEmitter = parentEventEmitter,
      timeRemaining = testTimeRemaining,
      svrCredentials = NetworkController.SvrCredentials(
        username = "test-username",
        password = "test-password"
      )
    )
  }

  // ==================== PinEntered Tests ====================

  @Test
  fun `PinEntered with correct PIN restores master key and registers successfully`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val keyMaterial = mockk<KeyMaterial>(relaxed = true)
    val registerResponse = createRegisterAccountResponse()
    val initialState = PinEntryState(mode = PinEntryState.Mode.RegistrationLock)

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = true) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccount(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(registerResponse to keyMaterial)

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(3)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredViaRegistrationLock>()
    assertThat(emittedParentEvents[1]).isInstanceOf<RegistrationFlowEvent.Registered>()
    assertThat(emittedParentEvents[2])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.FullyComplete>()
  }

  @Test
  fun `PinEntered with wrong PIN returns state with tries remaining`() = runTest {
    val triesRemaining = 3
    val initialState = PinEntryState(mode = PinEntryState.Mode.RegistrationLock)

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = true) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RestoreMasterKeyError.WrongPin(triesRemaining)
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("wrong-pin"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(emittedStates.last().triesRemaining).isEqualTo(triesRemaining)
  }

  @Test
  fun `PinEntered with no SVR data navigates to AccountLocked`() = runTest {
    val initialState = PinEntryState(mode = PinEntryState.Mode.RegistrationLock)

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = true) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RestoreMasterKeyError.NoDataFound
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.AccountLocked>()
      .prop(RegistrationRoute.AccountLocked::timeRemainingMs)
      .isEqualTo(testTimeRemaining)
  }

  @Test
  fun `PinEntered with network error when restoring master key returns NetworkError event`() = runTest {
    val initialState = PinEntryState(mode = PinEntryState.Mode.RegistrationLock)

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = true) } returns
      NetworkController.RegistrationNetworkResult.NetworkError(java.io.IOException("Network error"))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PinEntryState.OneTimeEvent.NetworkError)
  }

  @Test
  fun `PinEntered with application error when restoring master key returns UnknownError event`() = runTest {
    val initialState = PinEntryState(mode = PinEntryState.Mode.RegistrationLock)

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = true) } returns
      NetworkController.RegistrationNetworkResult.ApplicationError(RuntimeException("Unexpected"))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(0)
    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PinEntryState.OneTimeEvent.UnknownError)
  }

  @Test
  fun `PinEntered with missing e164 emits ResetState`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.RegistrationLock)

    parentState.value = RegistrationFlowState(
      sessionMetadata = createSessionMetadata(),
      sessionE164 = null
    )

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = true) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredViaRegistrationLock>()
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `PinEntered with missing sessionId emits ResetState`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.RegistrationLock)

    parentState.value = RegistrationFlowState(
      sessionMetadata = null,
      sessionE164 = "+15551234567"
    )

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = true) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredViaRegistrationLock>()
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  // ==================== Registration Error Tests ====================

  @Test
  fun `PinEntered with registration lock error during registration emits ResetState`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val registrationLockData = mockk<NetworkController.RegistrationLockResponse>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.RegistrationLock)

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = true) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccount(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.RegistrationLock(registrationLockData)
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(2)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredViaRegistrationLock>()
    assertThat(emittedParentEvents[1]).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `PinEntered with rate limit during registration returns RateLimited event`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val retryAfter = 30.seconds
    val initialState = PinEntryState(mode = PinEntryState.Mode.RegistrationLock)

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = true) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccount(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.RateLimited(retryAfter)
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredViaRegistrationLock>()
    assertThat(emittedStates.last().oneTimeEvent).isNotNull()
      .isInstanceOf<PinEntryState.OneTimeEvent.RateLimited>()
      .prop(PinEntryState.OneTimeEvent.RateLimited::retryAfter)
      .isEqualTo(retryAfter)
  }

  @Test
  fun `PinEntered with InvalidRequest during registration returns UnknownError event`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.RegistrationLock)

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = true) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccount(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.InvalidRequest("Bad request")
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredViaRegistrationLock>()
    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PinEntryState.OneTimeEvent.UnknownError)
  }

  @Test
  fun `PinEntered with DeviceTransferPossible during registration returns UnknownError event`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.RegistrationLock)

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = true) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccount(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.DeviceTransferPossible
      )

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredViaRegistrationLock>()
    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PinEntryState.OneTimeEvent.UnknownError)
  }

  @Test
  fun `PinEntered with network error during registration returns NetworkError event`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.RegistrationLock)

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = true) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccount(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.NetworkError(java.io.IOException("Network error"))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredViaRegistrationLock>()
    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PinEntryState.OneTimeEvent.NetworkError)
  }

  @Test
  fun `PinEntered with application error during registration returns UnknownError event`() = runTest {
    val masterKey = mockk<MasterKey>(relaxed = true)
    val initialState = PinEntryState(mode = PinEntryState.Mode.RegistrationLock)

    coEvery { mockRepository.restoreMasterKeyFromSvr(any(), any(), any(), forRegistrationLock = true) } returns
      NetworkController.RegistrationNetworkResult.Success(NetworkController.MasterKeyResponse(masterKey))
    coEvery { mockRepository.registerAccount(any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.ApplicationError(RuntimeException("Unexpected"))

    viewModel.applyEvent(initialState, PinEntryScreenEvents.PinEntered("123456"), stateEmitter, parentEventEmitter)

    assertThat(emittedParentEvents).hasSize(1)
    assertThat(emittedParentEvents[0]).isInstanceOf<RegistrationFlowEvent.MasterKeyRestoredViaRegistrationLock>()
    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PinEntryState.OneTimeEvent.UnknownError)
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

  private fun createRegisterAccountResponse(
    aci: String = "test-aci",
    pni: String = "test-pni",
    e164: String = "+15551234567",
    storageCapable: Boolean = true
  ) = NetworkController.RegisterAccountResponse(
    aci = aci,
    pni = pni,
    e164 = e164,
    usernameHash = null,
    usernameLinkHandle = null,
    storageCapable = storageCapable,
    entitlements = null,
    reregistration = false
  )
}
