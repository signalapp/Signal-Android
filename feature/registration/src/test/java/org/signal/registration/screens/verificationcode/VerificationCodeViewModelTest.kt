/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.signal.registration.KeyMaterial
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import kotlin.time.Duration.Companion.seconds

class VerificationCodeViewModelTest {

  private lateinit var viewModel: VerificationCodeViewModel
  private lateinit var mockRepository: RegistrationRepository
  private lateinit var parentState: MutableStateFlow<RegistrationFlowState>
  private lateinit var emittedEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit

  @Before
  fun setup() {
    mockRepository = mockk(relaxed = true)
    // Initialize with valid session data to prevent ResetState emission during ViewModel initialization
    parentState = MutableStateFlow(
      RegistrationFlowState(
        sessionMetadata = createSessionMetadata(),
        sessionE164 = "+15551234567"
      )
    )
    emittedEvents = mutableListOf()
    parentEventEmitter = { event -> emittedEvents.add(event) }
    viewModel = VerificationCodeViewModel(mockRepository, parentState, parentEventEmitter)
  }

  // ==================== applyParentState Tests ====================

  @Test
  fun `applyParentState with null sessionMetadata emits ResetState`() {
    val state = VerificationCodeState()
    val parentFlowState = RegistrationFlowState(
      sessionMetadata = null,
      sessionE164 = "+15551234567"
    )

    viewModel.applyParentState(state, parentFlowState)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `applyParentState with null sessionE164 emits ResetState`() {
    val state = VerificationCodeState()
    val parentFlowState = RegistrationFlowState(
      sessionMetadata = createSessionMetadata(),
      sessionE164 = null
    )

    viewModel.applyParentState(state, parentFlowState)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `applyParentState with both null values emits ResetState`() {
    val state = VerificationCodeState()
    val parentFlowState = RegistrationFlowState(
      sessionMetadata = null,
      sessionE164 = null
    )

    viewModel.applyParentState(state, parentFlowState)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `applyParentState with valid session copies metadata and e164`() {
    val state = VerificationCodeState()
    val sessionMetadata = createSessionMetadata(id = "test-session")
    val e164 = "+15551234567"
    val parentFlowState = RegistrationFlowState(
      sessionMetadata = sessionMetadata,
      sessionE164 = e164
    )

    val result = viewModel.applyParentState(state, parentFlowState)

    assertThat(emittedEvents).hasSize(0)
    assertThat(result.sessionMetadata).isEqualTo(sessionMetadata)
    assertThat(result.e164).isEqualTo(e164)
  }

  @Test
  fun `applyParentState preserves existing oneTimeEvent`() {
    val state = VerificationCodeState(oneTimeEvent = VerificationCodeState.OneTimeEvent.NetworkError)
    val sessionMetadata = createSessionMetadata()
    val parentFlowState = RegistrationFlowState(
      sessionMetadata = sessionMetadata,
      sessionE164 = "+15551234567"
    )

    val result = viewModel.applyParentState(state, parentFlowState)

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.NetworkError)
  }

  // ==================== applyEvent: ConsumeInnerOneTimeEvent Tests ====================

  @Test
  fun `ConsumeInnerOneTimeEvent clears oneTimeEvent`() = runTest {
    val initialState = VerificationCodeState(
      oneTimeEvent = VerificationCodeState.OneTimeEvent.NetworkError
    )

    val result = viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.ConsumeInnerOneTimeEvent
    )

    assertThat(result.oneTimeEvent).isNull()
  }

  @Test
  fun `ConsumeInnerOneTimeEvent with null event returns state with null event`() = runTest {
    val initialState = VerificationCodeState(oneTimeEvent = null)

    val result = viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.ConsumeInnerOneTimeEvent
    )

    assertThat(result.oneTimeEvent).isNull()
  }

  // ==================== applyEvent: WrongNumber Tests ====================

  @Test
  fun `WrongNumber navigates to PhoneNumberEntry`() = runTest {
    val initialState = VerificationCodeState()

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.WrongNumber)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PhoneNumberEntry>()
  }

  // ==================== applyEvent: CodeEntered Tests ====================

  @Test
  fun `CodeEntered emits ResetState when sessionMetadata is null`() = runTest {
    val initialState = VerificationCodeState(sessionMetadata = null)

    val result = viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456")
    )

    assertThat(result).isEqualTo(initialState)
    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.ResetState>()
  }

  @Test
  fun `CodeEntered with success registers account and navigates to PinCreate for new user`() = runTest {
    val sessionMetadata = createSessionMetadata(verified = true)
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    val registerResponse = createRegisterAccountResponse(storageCapable = false)
    val keyMaterial = mockk<KeyMaterial>(relaxed = true)

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccount(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(registerResponse to keyMaterial)

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CodeEntered("123456"))

    assertThat(emittedEvents).hasSize(2)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.Registered>()
    assertThat(emittedEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinCreate>()
  }

  @Test
  fun `CodeEntered with incorrect code returns IncorrectVerificationCode event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.SubmitVerificationCodeError.InvalidSessionIdOrVerificationCode("Wrong code")
      )

    val result = viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456")
    )

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.IncorrectVerificationCode)
  }

  @Test
  fun `CodeEntered with session not found emits ResetState`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.SubmitVerificationCodeError.SessionNotFound("Session expired")
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CodeEntered("123456"))

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `CodeEntered with already verified session continues to register`() = runTest {
    val verifiedSession = createSessionMetadata(verified = true)
    val initialState = VerificationCodeState(
      sessionMetadata = createSessionMetadata(verified = false),
      e164 = "+15551234567"
    )

    val registerResponse = createRegisterAccountResponse(storageCapable = false)
    val keyMaterial = mockk<KeyMaterial>(relaxed = true)

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.SubmitVerificationCodeError.SessionAlreadyVerifiedOrNoCodeRequested(verifiedSession)
      )
    coEvery { mockRepository.registerAccount(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(registerResponse to keyMaterial)

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CodeEntered("123456"))

    assertThat(emittedEvents).hasSize(2)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.Registered>()
    assertThat(emittedEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinCreate>()
  }

  @Test
  fun `CodeEntered with no code requested and not verified navigates back`() = runTest {
    val unverifiedSession = createSessionMetadata(verified = false)
    val initialState = VerificationCodeState(
      sessionMetadata = createSessionMetadata(),
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.SubmitVerificationCodeError.SessionAlreadyVerifiedOrNoCodeRequested(unverifiedSession)
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CodeEntered("123456"))

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `CodeEntered with rate limit from submitVerificationCode returns RateLimited event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.SubmitVerificationCodeError.RateLimited(60.seconds, sessionMetadata)
      )

    val result = viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456")
    )

    assertThat(result.oneTimeEvent).isNotNull()
      .isInstanceOf<VerificationCodeState.OneTimeEvent.RateLimited>()
      .prop(VerificationCodeState.OneTimeEvent.RateLimited::retryAfter)
      .isEqualTo(60.seconds)
  }

  @Test
  fun `CodeEntered with network error from submitVerificationCode returns NetworkError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.NetworkError(java.io.IOException("Network error"))

    val result = viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456")
    )

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.NetworkError)
  }

  @Test
  fun `CodeEntered with application error from submitVerificationCode returns UnknownError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.ApplicationError(RuntimeException("Unexpected"))

    val result = viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456")
    )

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.UnknownError)
  }

  // ==================== applyEvent: CodeEntered - Registration Errors ====================

  @Ignore
  @Test
  fun `CodeEntered with DeviceTransferPossible emits ResetState`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccount(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.DeviceTransferPossible
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CodeEntered("123456"))

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Ignore
  @Test
  fun `CodeEntered with rate limit from registerAccount returns RateLimited event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccount(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.RateLimited(30.seconds)
      )

    val result = viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456")
    )

    assertThat(result.oneTimeEvent).isNotNull()
      .isInstanceOf<VerificationCodeState.OneTimeEvent.RateLimited>()
      .prop(VerificationCodeState.OneTimeEvent.RateLimited::retryAfter)
      .isEqualTo(30.seconds)
  }

  @Ignore
  @Test
  fun `CodeEntered with InvalidRequest from registerAccount returns RegistrationError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccount(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.InvalidRequest("Bad request")
      )

    val result = viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456")
    )

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.RegistrationError)
  }

  @Ignore
  @Test
  fun `CodeEntered with RegistrationRecoveryPasswordIncorrect returns RegistrationError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccount(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect("Wrong password")
      )

    val result = viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456")
    )

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.RegistrationError)
  }

  @Ignore
  @Test
  fun `CodeEntered with network error from registerAccount returns NetworkError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccount(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.NetworkError(java.io.IOException("Network error"))

    val result = viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456")
    )

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.NetworkError)
  }

  @Ignore
  @Test
  fun `CodeEntered with application error from registerAccount returns UnknownError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccount(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.ApplicationError(RuntimeException("Unexpected"))

    val result = viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456")
    )

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.UnknownError)
  }

  // ==================== applyEvent: ResendSms Tests ====================

  @Test
  fun `ResendSms with null sessionMetadata emits ResetState`() = runTest {
    val initialState = VerificationCodeState(sessionMetadata = null)

    val result = viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
    assertThat(result).isEqualTo(initialState)
  }

  @Test
  fun `ResendSms with success updates sessionMetadata`() = runTest {
    val sessionMetadata = createSessionMetadata(id = "original-session")
    val updatedSession = createSessionMetadata(id = "updated-session")
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      NetworkController.RegistrationNetworkResult.Success(updatedSession)

    val result = viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms)

    assertThat(result.sessionMetadata).isEqualTo(updatedSession)
  }

  @Test
  fun `ResendSms with rate limit returns RateLimited event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RequestVerificationCodeError.RateLimited(45.seconds, sessionMetadata)
      )

    val result = viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms)

    assertThat(result.oneTimeEvent).isNotNull()
      .isInstanceOf<VerificationCodeState.OneTimeEvent.RateLimited>()
      .prop(VerificationCodeState.OneTimeEvent.RateLimited::retryAfter)
      .isEqualTo(45.seconds)
  }

  @Test
  fun `ResendSms with InvalidRequest returns UnknownError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RequestVerificationCodeError.InvalidRequest("Bad request")
      )

    val result = viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms)

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.UnknownError)
  }

  @Test
  fun `ResendSms with CouldNotFulfillWithRequestedTransport returns appropriate event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport(sessionMetadata)
      )

    val result = viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms)

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.CouldNotRequestCodeWithSelectedTransport)
  }

  @Test
  fun `ResendSms with InvalidSessionId emits ResetState`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RequestVerificationCodeError.InvalidSessionId("Invalid session")
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `ResendSms with SessionNotFound emits ResetState`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RequestVerificationCodeError.SessionNotFound("Session not found")
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `ResendSms with MissingRequestInformationOrAlreadyVerified returns NetworkError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RequestVerificationCodeError.MissingRequestInformationOrAlreadyVerified(sessionMetadata)
      )

    val result = viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms)

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.NetworkError)
  }

  @Test
  fun `ResendSms with ThirdPartyServiceError returns ThirdPartyError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RequestVerificationCodeError.ThirdPartyServiceError(
          NetworkController.ThirdPartyServiceErrorResponse("Provider error", false)
        )
      )

    val result = viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms)

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.ThirdPartyError)
  }

  @Test
  fun `ResendSms with network error returns NetworkError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      NetworkController.RegistrationNetworkResult.NetworkError(java.io.IOException("Network error"))

    val result = viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms)

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.NetworkError)
  }

  @Test
  fun `ResendSms with application error returns UnknownError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      NetworkController.RegistrationNetworkResult.ApplicationError(RuntimeException("Unexpected"))

    val result = viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms)

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.UnknownError)
  }

  // ==================== applyEvent: CallMe Tests ====================

  @Test
  fun `CallMe with null sessionMetadata emits ResetState`() = runTest {
    val initialState = VerificationCodeState(sessionMetadata = null)

    val result = viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CallMe)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
    assertThat(result).isEqualTo(initialState)
  }

  @Test
  fun `CallMe with success updates sessionMetadata`() = runTest {
    val sessionMetadata = createSessionMetadata(id = "original-session")
    val updatedSession = createSessionMetadata(id = "updated-session")
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.VOICE)) } returns
      NetworkController.RegistrationNetworkResult.Success(updatedSession)

    val result = viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CallMe)

    assertThat(result.sessionMetadata).isEqualTo(updatedSession)
  }

  @Test
  fun `CallMe with rate limit returns RateLimited event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.VOICE)) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RequestVerificationCodeError.RateLimited(90.seconds, sessionMetadata)
      )

    val result = viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CallMe)

    assertThat(result.oneTimeEvent).isNotNull()
      .isInstanceOf<VerificationCodeState.OneTimeEvent.RateLimited>()
      .prop(VerificationCodeState.OneTimeEvent.RateLimited::retryAfter)
      .isEqualTo(90.seconds)
  }

  @Test
  fun `CallMe with CouldNotFulfillWithRequestedTransport returns appropriate event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.VOICE)) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport(sessionMetadata)
      )

    val result = viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CallMe)

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.CouldNotRequestCodeWithSelectedTransport)
  }

  @Test
  fun `CallMe with ThirdPartyServiceError returns ThirdPartyError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.VOICE)) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RequestVerificationCodeError.ThirdPartyServiceError(
          NetworkController.ThirdPartyServiceErrorResponse("Voice provider error", true)
        )
      )

    val result = viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CallMe)

    assertThat(result.oneTimeEvent).isEqualTo(VerificationCodeState.OneTimeEvent.ThirdPartyError)
  }

  // ==================== Helper Functions ====================

  private fun createSessionMetadata(
    id: String = "test-session-id",
    requestedInformation: List<String> = emptyList(),
    verified: Boolean = false
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
    storageCapable: Boolean = false
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
