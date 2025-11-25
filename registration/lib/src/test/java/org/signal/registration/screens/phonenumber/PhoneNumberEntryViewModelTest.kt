/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

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
import org.junit.Test
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import kotlin.time.Duration.Companion.seconds

class PhoneNumberEntryViewModelTest {

  private lateinit var viewModel: PhoneNumberEntryViewModel
  private lateinit var mockRepository: RegistrationRepository
  private lateinit var parentState: MutableStateFlow<RegistrationFlowState>
  private lateinit var emittedEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit

  @Before
  fun setup() {
    mockRepository = mockk(relaxed = true)
    parentState = MutableStateFlow(RegistrationFlowState())
    emittedEvents = mutableListOf()
    parentEventEmitter = { event -> emittedEvents.add(event) }
    viewModel = PhoneNumberEntryViewModel(mockRepository, parentState, parentEventEmitter)
  }

  @Test
  fun `initial state has default US region and country code`() {
    val state = PhoneNumberEntryState()

    assertThat(state.regionCode).isEqualTo("US")
    assertThat(state.countryCode).isEqualTo("1")
    assertThat(state.nationalNumber).isEqualTo("")
    assertThat(state.formattedNumber).isEqualTo("")
  }

  @Test
  fun `PhoneNumberChanged extracts digits and formats number`() = runTest {
    val initialState = PhoneNumberEntryState()

    val result = viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.PhoneNumberChanged("555-123-4567")
    )

    assertThat(result.nationalNumber).isEqualTo("5551234567")
    assertThat(result.formattedNumber).isEqualTo("(555) 123-4567")
  }

  @Test
  fun `PhoneNumberChanged with raw digits formats correctly`() = runTest {
    val initialState = PhoneNumberEntryState()

    val result = viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.PhoneNumberChanged("5551234567")
    )

    assertThat(result.nationalNumber).isEqualTo("5551234567")
    assertThat(result.formattedNumber).isEqualTo("(555) 123-4567")
  }

  @Test
  fun `PhoneNumberChanged formats progressively as digits are added`() = runTest {
    var state = PhoneNumberEntryState()

    state = viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.PhoneNumberChanged("5"))
    assertThat(state.nationalNumber).isEqualTo("5")

    state = viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.PhoneNumberChanged("55"))
    assertThat(state.nationalNumber).isEqualTo("55")

    state = viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.PhoneNumberChanged("555"))
    assertThat(state.nationalNumber).isEqualTo("555")

    state = viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.PhoneNumberChanged("5551"))
    assertThat(state.nationalNumber).isEqualTo("5551")
    // libphonenumber formats progressively - at 4 digits it's still building the format
    assertThat(state.formattedNumber).isEqualTo("555-1")

    state = viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.PhoneNumberChanged("55512"))
    assertThat(state.nationalNumber).isEqualTo("55512")
    assertThat(state.formattedNumber).isEqualTo("555-12")
  }

  @Test
  fun `PhoneNumberChanged ignores non-digit characters`() = runTest {
    val initialState = PhoneNumberEntryState()

    val result = viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.PhoneNumberChanged("(555) abc 123-4567!")
    )

    assertThat(result.nationalNumber).isEqualTo("5551234567")
  }

  @Test
  fun `PhoneNumberChanged with same digits returns same state`() = runTest {
    val initialState = PhoneNumberEntryState(nationalNumber = "5551234567", formattedNumber = "(555) 123-4567")

    val result = viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.PhoneNumberChanged("555-123-4567")
    )

    // Should return the same state object since digits haven't changed
    assertThat(result).isEqualTo(initialState)
  }

  @Test
  fun `CountryCodeChanged updates country code and region`() = runTest {
    val initialState = PhoneNumberEntryState()

    val result = viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.CountryCodeChanged("44")
    )

    assertThat(result.countryCode).isEqualTo("44")
    assertThat(result.regionCode).isEqualTo("GB")
  }

  @Test
  fun `CountryCodeChanged sanitizes input to digits only`() = runTest {
    val initialState = PhoneNumberEntryState()

    val result = viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.CountryCodeChanged("+44abc")
    )

    assertThat(result.countryCode).isEqualTo("44")
  }

  @Test
  fun `CountryCodeChanged limits to 3 digits`() = runTest {
    val initialState = PhoneNumberEntryState()

    val result = viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.CountryCodeChanged("12345")
    )

    assertThat(result.countryCode).isEqualTo("123")
  }

  @Test
  fun `CountryCodeChanged reformats existing phone number for new region`() = runTest {
    // Start with a US number
    var state = PhoneNumberEntryState(
      regionCode = "US",
      countryCode = "1",
      nationalNumber = "5551234567",
      formattedNumber = "(555) 123-4567"
    )

    // Change to UK
    state = viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.CountryCodeChanged("44"))

    assertThat(state.countryCode).isEqualTo("44")
    assertThat(state.regionCode).isEqualTo("GB")
    // The digits should be reformatted for UK format
    assertThat(state.nationalNumber).isEqualTo("5551234567")
  }

  @Test
  fun `CountryPicker emits NavigateToCountryPicker event`() = runTest {
    val initialState = PhoneNumberEntryState()

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.CountryPicker
    )

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(
      RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.CountryCodePicker)
    )
  }

  @Test
  fun `ConsumeInnerOneTimeEvent clears inner event`() = runTest {
    val initialState = PhoneNumberEntryState(
      oneTimeEvent = PhoneNumberEntryState.OneTimeEvent.NetworkError
    )

    val result = viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.ConsumeOneTimeEvent
    )

    assertThat(result.oneTimeEvent).isNull()
  }

  @Test
  fun `German country code formats correctly`() = runTest {
    var state = PhoneNumberEntryState()

    // Set German country code
    state = viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.CountryCodeChanged("49"))
    assertThat(state.regionCode).isEqualTo("DE")

    // Enter a German number
    state = viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.PhoneNumberChanged("15123456789"))
    assertThat(state.nationalNumber).isEqualTo("15123456789")
  }

  // ==================== PhoneNumberSubmitted Tests ====================

  @Test
  fun `PhoneNumberSubmitted creates session and requests code on success`() = runTest {
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    val result = viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted)

    assertThat(result.sessionMetadata).isNotNull()
    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted navigates to captcha when required`() = runTest {
    val sessionMetadata = createSessionMetadata(requestedInformation = listOf("captcha"))

    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.Captcha>()
  }

  @Test
  fun `PhoneNumberSubmitted handles rate limiting from createSession`() = runTest {
    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.CreateSessionError.RateLimited(60.seconds)
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    val result = viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted)

    assertThat(result.oneTimeEvent).isNotNull()
      .isInstanceOf<PhoneNumberEntryState.OneTimeEvent.RateLimited>()
      .prop(PhoneNumberEntryState.OneTimeEvent.RateLimited::retryAfter)
      .isEqualTo(60.seconds)
  }

  @Test
  fun `PhoneNumberSubmitted handles invalid request from createSession`() = runTest {
    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.CreateSessionError.InvalidRequest("Bad request")
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    val result = viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted)

    assertThat(result.oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.UnknownError)
  }

  @Test
  fun `PhoneNumberSubmitted handles network error`() = runTest {
    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.NetworkError(java.io.IOException("Network error"))

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    val result = viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted)

    assertThat(result.oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.NetworkError)
  }

  @Test
  fun `PhoneNumberSubmitted handles application error`() = runTest {
    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.ApplicationError(RuntimeException("Unexpected error"))

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    val result = viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted)

    assertThat(result.oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.UnknownError)
  }

  @Test
  fun `PhoneNumberSubmitted reuses existing session`() = runTest {
    val existingSession = createSessionMetadata(id = "existing-session")
    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      sessionMetadata = existingSession
    )

    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(existingSession)

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted)

    // Should not create a new session, just request verification code
    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted handles rate limiting from requestVerificationCode`() = runTest {
    val sessionMetadata = createSessionMetadata()

    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RequestVerificationCodeError.RateLimited(30.seconds, sessionMetadata)
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    val result = viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted)

    assertThat(result.oneTimeEvent).isNotNull().isInstanceOf<PhoneNumberEntryState.OneTimeEvent.RateLimited>()
  }

  @Test
  fun `PhoneNumberSubmitted handles session not found`() = runTest {
    val sessionMetadata = createSessionMetadata()

    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RequestVerificationCodeError.SessionNotFound("Session expired")
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `PhoneNumberSubmitted handles transport not supported`() = runTest {
    val sessionMetadata = createSessionMetadata()

    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport(sessionMetadata)
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    val result = viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted)

    assertThat(result.oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.CouldNotRequestCodeWithSelectedTransport)
  }

  @Test
  fun `PhoneNumberSubmitted handles third party service error`() = runTest {
    val sessionMetadata = createSessionMetadata()

    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RequestVerificationCodeError.ThirdPartyServiceError(
          NetworkController.ThirdPartyServiceErrorResponse("Provider error", false)
        )
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    val result = viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted)

    assertThat(result.oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.ThirdPartyError)
  }

  // ==================== CaptchaCompleted Tests ====================

  @Test
  fun `CaptchaCompleted submits token and navigates to verification code`() = runTest {
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())
    val initialState = PhoneNumberEntryState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.submitCaptchaToken(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"))

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `CaptchaCompleted returns error when no session exists`() = runTest {
    val initialState = PhoneNumberEntryState(sessionMetadata = null)

    val result = viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"))

    assertThat(result.oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.UnknownError)
  }

  @Test
  fun `CaptchaCompleted handles captcha still required after submission`() = runTest {
    val sessionWithCaptcha = createSessionMetadata(requestedInformation = listOf("captcha"))
    val initialState = PhoneNumberEntryState(sessionMetadata = sessionWithCaptcha)

    coEvery { mockRepository.submitCaptchaToken(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionWithCaptcha)

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"))

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.Captcha>()
  }

  @Test
  fun `CaptchaCompleted handles rate limiting`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = PhoneNumberEntryState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.submitCaptchaToken(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.UpdateSessionError.RateLimited(45.seconds, sessionMetadata)
      )

    val result = viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"))

    assertThat(result.oneTimeEvent).isNotNull()
      .isInstanceOf<PhoneNumberEntryState.OneTimeEvent.RateLimited>()
      .prop(PhoneNumberEntryState.OneTimeEvent.RateLimited::retryAfter)
      .isEqualTo(45.seconds)
  }

  @Test
  fun `CaptchaCompleted handles rejected update`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = PhoneNumberEntryState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.submitCaptchaToken(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.UpdateSessionError.RejectedUpdate("Invalid captcha")
      )

    val result = viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"))

    assertThat(result.oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.UnknownError)
  }

  @Test
  fun `CaptchaCompleted handles network error`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = PhoneNumberEntryState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.submitCaptchaToken(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.NetworkError(java.io.IOException("Connection lost"))

    val result = viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"))

    assertThat(result.oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.NetworkError)
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
}
