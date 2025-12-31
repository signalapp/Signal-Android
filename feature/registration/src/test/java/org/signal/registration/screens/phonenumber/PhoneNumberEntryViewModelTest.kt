/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
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
  private lateinit var emittedStates: MutableList<PhoneNumberEntryState>
  private lateinit var stateEmitter: (PhoneNumberEntryState) -> Unit
  private lateinit var emittedEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit

  @Before
  fun setup() {
    mockRepository = mockk(relaxed = true)
    parentState = MutableStateFlow(RegistrationFlowState())
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
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

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.PhoneNumberChanged("555-123-4567"),
      stateEmitter,
      parentEventEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().nationalNumber).isEqualTo("5551234567")
    assertThat(emittedStates.last().formattedNumber).isEqualTo("(555) 123-4567")
  }

  @Test
  fun `PhoneNumberChanged with raw digits formats correctly`() = runTest {
    val initialState = PhoneNumberEntryState()

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.PhoneNumberChanged("5551234567"),
      stateEmitter,
      parentEventEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().nationalNumber).isEqualTo("5551234567")
    assertThat(emittedStates.last().formattedNumber).isEqualTo("(555) 123-4567")
  }

  @Test
  fun `PhoneNumberChanged formats progressively as digits are added`() = runTest {
    var state = PhoneNumberEntryState()

    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.PhoneNumberChanged("5"), stateEmitter, parentEventEmitter)
    state = emittedStates.last()
    assertThat(state.nationalNumber).isEqualTo("5")

    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.PhoneNumberChanged("55"), stateEmitter, parentEventEmitter)
    state = emittedStates.last()
    assertThat(state.nationalNumber).isEqualTo("55")

    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.PhoneNumberChanged("555"), stateEmitter, parentEventEmitter)
    state = emittedStates.last()
    assertThat(state.nationalNumber).isEqualTo("555")

    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.PhoneNumberChanged("5551"), stateEmitter, parentEventEmitter)
    state = emittedStates.last()
    assertThat(state.nationalNumber).isEqualTo("5551")
    // libphonenumber formats progressively - at 4 digits it's still building the format
    assertThat(state.formattedNumber).isEqualTo("555-1")

    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.PhoneNumberChanged("55512"), stateEmitter, parentEventEmitter)
    state = emittedStates.last()
    assertThat(state.nationalNumber).isEqualTo("55512")
    assertThat(state.formattedNumber).isEqualTo("555-12")
  }

  @Test
  fun `PhoneNumberChanged ignores non-digit characters`() = runTest {
    val initialState = PhoneNumberEntryState()

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.PhoneNumberChanged("(555) abc 123-4567!"),
      stateEmitter,
      parentEventEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().nationalNumber).isEqualTo("5551234567")
  }

  @Test
  fun `PhoneNumberChanged with same digits does not emit new state`() = runTest {
    val initialState = PhoneNumberEntryState(nationalNumber = "5551234567", formattedNumber = "(555) 123-4567")

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.PhoneNumberChanged("555-123-4567"),
      stateEmitter,
      parentEventEmitter
    )

    // Should emit the same state since digits haven't changed
    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last()).isEqualTo(initialState)
  }

  @Test
  fun `CountryCodeChanged updates country code and region`() = runTest {
    val initialState = PhoneNumberEntryState()

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.CountryCodeChanged("44"),
      stateEmitter,
      parentEventEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().countryCode).isEqualTo("44")
    assertThat(emittedStates.last().regionCode).isEqualTo("GB")
  }

  @Test
  fun `CountryCodeChanged sanitizes input to digits only`() = runTest {
    val initialState = PhoneNumberEntryState()

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.CountryCodeChanged("+44abc"),
      stateEmitter,
      parentEventEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().countryCode).isEqualTo("44")
  }

  @Test
  fun `CountryCodeChanged limits to 3 digits`() = runTest {
    val initialState = PhoneNumberEntryState()

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.CountryCodeChanged("12345"),
      stateEmitter,
      parentEventEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().countryCode).isEqualTo("123")
  }

  @Test
  fun `CountryCodeChanged reformats existing phone number for new region`() = runTest {
    // Start with a US number
    val state = PhoneNumberEntryState(
      regionCode = "US",
      countryCode = "1",
      nationalNumber = "5551234567",
      formattedNumber = "(555) 123-4567"
    )

    // Change to UK
    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.CountryCodeChanged("44"), stateEmitter, parentEventEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().countryCode).isEqualTo("44")
    assertThat(emittedStates.last().regionCode).isEqualTo("GB")
    // The digits should be reformatted for UK format
    assertThat(emittedStates.last().nationalNumber).isEqualTo("5551234567")
  }

  @Test
  fun `CountryPicker emits NavigateToCountryPicker event`() = runTest {
    val initialState = PhoneNumberEntryState()

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.CountryPicker,
      stateEmitter,
      parentEventEmitter
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

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.ConsumeOneTimeEvent,
      stateEmitter,
      parentEventEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().oneTimeEvent).isNull()
  }

  @Test
  fun `German country code formats correctly`() = runTest {
    var state = PhoneNumberEntryState()

    // Set German country code
    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.CountryCodeChanged("49"), stateEmitter, parentEventEmitter)
    state = emittedStates.last()
    assertThat(state.regionCode).isEqualTo("DE")

    // Enter a German number
    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.PhoneNumberChanged("15123456789"), stateEmitter, parentEventEmitter)
    state = emittedStates.last()
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

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

    assertThat(emittedStates.last().sessionMetadata).isNotNull()
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

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

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

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

    assertThat(emittedStates.last().oneTimeEvent).isNotNull()
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

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.UnknownError)
  }

  @Test
  fun `PhoneNumberSubmitted handles network error`() = runTest {
    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.NetworkError(java.io.IOException("Network error"))

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.NetworkError)
  }

  @Test
  fun `PhoneNumberSubmitted handles application error`() = runTest {
    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.ApplicationError(RuntimeException("Unexpected error"))

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.UnknownError)
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

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

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

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

    assertThat(emittedStates.last().oneTimeEvent).isNotNull().isInstanceOf<PhoneNumberEntryState.OneTimeEvent.RateLimited>()
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

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

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

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.CouldNotRequestCodeWithSelectedTransport)
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

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.ThirdPartyError)
  }

  // ==================== Push Challenge Tests ====================

  @Test
  fun `PhoneNumberSubmitted with push challenge submits token when received`() = runTest {
    val sessionWithPushChallenge = createSessionMetadata(requestedInformation = listOf("pushChallenge"))
    val sessionAfterPushChallenge = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionWithPushChallenge)
    coEvery { mockRepository.awaitPushChallengeToken() } returns "test-push-challenge-token"
    coEvery { mockRepository.submitPushChallengeToken(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionAfterPushChallenge)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionAfterPushChallenge)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

    // Verify navigation to verification code entry
    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()

    // Verify push challenge token was submitted
    io.mockk.coVerify { mockRepository.submitPushChallengeToken(sessionWithPushChallenge.id, "test-push-challenge-token") }
  }

  @Test
  fun `PhoneNumberSubmitted with push challenge continues when token times out`() = runTest {
    val sessionWithPushChallenge = createSessionMetadata(requestedInformation = listOf("pushChallenge"))

    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionWithPushChallenge)
    coEvery { mockRepository.awaitPushChallengeToken() } returns null
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionWithPushChallenge)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

    // Verify navigation continues despite no push challenge token
    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()

    // Verify submit was never called since token was null
    io.mockk.coVerify(exactly = 0) { mockRepository.submitPushChallengeToken(any(), any()) }
  }

  @Test
  fun `PhoneNumberSubmitted with push challenge continues when submission fails`() = runTest {
    val sessionWithPushChallenge = createSessionMetadata(requestedInformation = listOf("pushChallenge"))

    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionWithPushChallenge)
    coEvery { mockRepository.awaitPushChallengeToken() } returns "test-push-challenge-token"
    coEvery { mockRepository.submitPushChallengeToken(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.UpdateSessionError.RejectedUpdate("Invalid token")
      )
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionWithPushChallenge)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

    // Verify navigation continues despite push challenge submission failure
    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with push challenge continues when submission has network error`() = runTest {
    val sessionWithPushChallenge = createSessionMetadata(requestedInformation = listOf("pushChallenge"))

    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionWithPushChallenge)
    coEvery { mockRepository.awaitPushChallengeToken() } returns "test-push-challenge-token"
    coEvery { mockRepository.submitPushChallengeToken(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.NetworkError(java.io.IOException("Connection lost"))
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionWithPushChallenge)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

    // Verify navigation continues despite network error
    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with push challenge continues when submission has application error`() = runTest {
    val sessionWithPushChallenge = createSessionMetadata(requestedInformation = listOf("pushChallenge"))

    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionWithPushChallenge)
    coEvery { mockRepository.awaitPushChallengeToken() } returns "test-push-challenge-token"
    coEvery { mockRepository.submitPushChallengeToken(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.ApplicationError(RuntimeException("Unexpected error"))
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionWithPushChallenge)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

    // Verify navigation continues despite application error
    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with push challenge navigates to captcha if still required after submission`() = runTest {
    val sessionWithPushChallenge = createSessionMetadata(requestedInformation = listOf("pushChallenge", "captcha"))
    val sessionAfterPushChallenge = createSessionMetadata(requestedInformation = listOf("captcha"))

    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionWithPushChallenge)
    coEvery { mockRepository.awaitPushChallengeToken() } returns "test-push-challenge-token"
    coEvery { mockRepository.submitPushChallengeToken(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionAfterPushChallenge)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showFullScreenSpinner).isTrue()
    assertThat(emittedStates.last().showFullScreenSpinner).isFalse()

    // Verify navigation to captcha
    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.Captcha>()
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

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"), stateEmitter, parentEventEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `CaptchaCompleted returns error when no session exists`() = runTest {
    val initialState = PhoneNumberEntryState(sessionMetadata = null)

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"), stateEmitter, parentEventEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.UnknownError)
  }

  @Test
  fun `CaptchaCompleted handles captcha still required after submission`() = runTest {
    val sessionWithCaptcha = createSessionMetadata(requestedInformation = listOf("captcha"))
    val initialState = PhoneNumberEntryState(sessionMetadata = sessionWithCaptcha)

    coEvery { mockRepository.submitCaptchaToken(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionWithCaptcha)

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"), stateEmitter, parentEventEmitter)

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

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"), stateEmitter, parentEventEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().oneTimeEvent).isNotNull()
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

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"), stateEmitter, parentEventEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.UnknownError)
  }

  @Test
  fun `CaptchaCompleted handles network error`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = PhoneNumberEntryState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.submitCaptchaToken(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.NetworkError(java.io.IOException("Connection lost"))

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"), stateEmitter, parentEventEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.NetworkError)
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
