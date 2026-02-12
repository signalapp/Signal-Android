/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.registration.KeyMaterial
import org.signal.registration.NetworkController
import org.signal.registration.PreExistingRegistrationData
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import java.io.IOException
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

  // ==================== applyParentState Tests ====================

  @Test
  fun `applyParentState copies preExistingRegistrationData from parent`() {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true)
    val state = PhoneNumberEntryState()
    val parentFlowState = RegistrationFlowState(preExistingRegistrationData = preExistingData)

    val result = viewModel.applyParentState(state, parentFlowState)

    assertThat(result.preExistingRegistrationData).isEqualTo(preExistingData)
  }

  @Test
  fun `applyParentState clears restoredSvrCredentials when doNotAttemptRecoveryPassword is true`() {
    val credentials = listOf(
      NetworkController.SvrCredentials(username = "user", password = "pass")
    )
    val state = PhoneNumberEntryState(restoredSvrCredentials = credentials)
    val parentFlowState = RegistrationFlowState(doNotAttemptRecoveryPassword = true)

    val result = viewModel.applyParentState(state, parentFlowState)

    assertThat(result.restoredSvrCredentials).isEmpty()
  }

  @Test
  fun `applyParentState keeps restoredSvrCredentials when doNotAttemptRecoveryPassword is false`() {
    val credentials = listOf(
      NetworkController.SvrCredentials(username = "user", password = "pass")
    )
    val state = PhoneNumberEntryState(restoredSvrCredentials = credentials)
    val parentFlowState = RegistrationFlowState(doNotAttemptRecoveryPassword = false)

    val result = viewModel.applyParentState(state, parentFlowState)

    assertThat(result.restoredSvrCredentials).isEqualTo(credentials)
  }

  // ==================== Pre-existing Registration Data (RRP) Tests ====================

  @Test
  fun `PhoneNumberSubmitted with matching preExistingRegistrationData registers with RRP and navigates to PinEntryForSvrRestore`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }
    val keyMaterial = mockk<KeyMaterial>(relaxed = true)
    val registerResponse = createRegisterAccountResponse(storageCapable = true)

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(registerResponse to keyMaterial)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    assertThat(emittedEvents.first()).isInstanceOf<RegistrationFlowEvent.Registered>()
    assertThat(emittedEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinEntryForSvrRestore>()
  }

  @Test
  fun `PhoneNumberSubmitted with matching preExistingRegistrationData navigates to PinCreate when not storage capable`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }
    val keyMaterial = mockk<KeyMaterial>(relaxed = true)
    val registerResponse = createRegisterAccountResponse(storageCapable = false)

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(registerResponse to keyMaterial)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    assertThat(emittedEvents.first()).isInstanceOf<RegistrationFlowEvent.Registered>()
    assertThat(emittedEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinCreate>()
  }

  @Test
  fun `PhoneNumberSubmitted with preExistingRegistrationData and SessionNotFoundOrNotVerified emits ResetState`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.SessionNotFoundOrNotVerified("Not found")
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `PhoneNumberSubmitted with preExistingRegistrationData and DeviceTransferPossible emits ResetState`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.DeviceTransferPossible
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `PhoneNumberSubmitted with preExistingRegistrationData and RegistrationLock navigates to PinEntryForRegistrationLock`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }
    val svrCredentials = NetworkController.SvrCredentials(username = "user", password = "pass")
    val registrationLockData = NetworkController.RegistrationLockResponse(
      timeRemaining = 60000L,
      svr2Credentials = svrCredentials
    )

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.RegistrationLock(registrationLockData)
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinEntryForRegistrationLock>()
  }

  @Test
  fun `PhoneNumberSubmitted with preExistingRegistrationData and RateLimited returns RateLimited event`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.RateLimited(30.seconds)
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    assertThat(emittedStates.last().oneTimeEvent).isNotNull()
      .isInstanceOf<PhoneNumberEntryState.OneTimeEvent.RateLimited>()
      .prop(PhoneNumberEntryState.OneTimeEvent.RateLimited::retryAfter)
      .isEqualTo(30.seconds)
  }

  @Test
  fun `PhoneNumberSubmitted with preExistingRegistrationData and InvalidRequest emits RecoveryPasswordInvalid and falls through to session creation`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.InvalidRequest("Bad request")
      )
    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Should emit RecoveryPasswordInvalid and then continue to session creation
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.RecoveryPasswordInvalid)
    // Should ultimately navigate to verification code entry after falling through
    assertThat(emittedStates.last().preExistingRegistrationData).isNull()
  }

  @Test
  fun `PhoneNumberSubmitted with preExistingRegistrationData and RegistrationRecoveryPasswordIncorrect emits RecoveryPasswordInvalid and falls through`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect("Wrong password")
      )
    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.RecoveryPasswordInvalid)
    assertThat(emittedStates.last().preExistingRegistrationData).isNull()
  }

  @Test
  fun `PhoneNumberSubmitted with preExistingRegistrationData and NetworkError returns NetworkError event`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.NetworkError(IOException("Network error"))

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.NetworkError)
  }

  @Test
  fun `PhoneNumberSubmitted with preExistingRegistrationData and ApplicationError returns UnknownError event`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.ApplicationError(RuntimeException("Unexpected"))

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    assertThat(emittedStates.last().oneTimeEvent).isEqualTo(PhoneNumberEntryState.OneTimeEvent.UnknownError)
  }

  @Test
  fun `PhoneNumberSubmitted with non-matching preExistingRegistrationData skips RRP and creates session`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15559999999"
      coEvery { registrationLockEnabled } returns false
    }
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Should skip RRP and go to session creation flow
    coVerify(exactly = 0) { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) }
    assertThat(emittedEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  // ==================== SVR Credential Checking Tests ====================

  @Test
  fun `PhoneNumberSubmitted with valid SVR credentials navigates to PinEntryForSmsBypass`() = runTest {
    val svrCredentials = listOf(
      NetworkController.SvrCredentials(username = "user", password = "pass")
    )
    val validCredential = NetworkController.SvrCredentials(username = "user", password = "pass")
    val checkResponse = NetworkController.CheckSvrCredentialsResponse(
      matches = mapOf("user:pass" to "match")
    )

    coEvery { mockRepository.checkSvrCredentials(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(checkResponse)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      restoredSvrCredentials = svrCredentials
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    assertThat(emittedEvents).hasSize(2)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.E164Chosen>()
    assertThat(emittedEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinEntryForSmsBypass>()
  }

  @Test
  fun `PhoneNumberSubmitted with no matching SVR credentials falls through to session creation`() = runTest {
    val svrCredentials = listOf(
      NetworkController.SvrCredentials(username = "user", password = "pass")
    )
    val checkResponse = NetworkController.CheckSvrCredentialsResponse(
      matches = mapOf("user:pass" to "no-match")
    )
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.checkSvrCredentials(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(checkResponse)
    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      restoredSvrCredentials = svrCredentials
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Should fall through to session creation
    assertThat(emittedEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with SVR credentials network error falls through to session creation`() = runTest {
    val svrCredentials = listOf(
      NetworkController.SvrCredentials(username = "user", password = "pass")
    )
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.checkSvrCredentials(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.NetworkError(IOException("Network error"))
    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      restoredSvrCredentials = svrCredentials
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    // Should ignore error and fall through
    assertThat(emittedEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with SVR credentials application error falls through to session creation`() = runTest {
    val svrCredentials = listOf(
      NetworkController.SvrCredentials(username = "user", password = "pass")
    )
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.checkSvrCredentials(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.ApplicationError(RuntimeException("Unexpected"))
    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      restoredSvrCredentials = svrCredentials
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    assertThat(emittedEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with SVR credentials invalid request falls through to session creation`() = runTest {
    val svrCredentials = listOf(
      NetworkController.SvrCredentials(username = "user", password = "pass")
    )
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.checkSvrCredentials(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.CheckSvrCredentialsError.InvalidRequest("Bad request")
      )
    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      restoredSvrCredentials = svrCredentials
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    assertThat(emittedEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with SVR credentials unauthorized falls through to session creation`() = runTest {
    val svrCredentials = listOf(
      NetworkController.SvrCredentials(username = "user", password = "pass")
    )
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.checkSvrCredentials(any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Failure(
        NetworkController.CheckSvrCredentialsError.Unauthorized
      )
    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      restoredSvrCredentials = svrCredentials
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    assertThat(emittedEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with empty restoredSvrCredentials skips SVR check`() = runTest {
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.createSession(any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      NetworkController.RegistrationNetworkResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      restoredSvrCredentials = emptyList()
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberSubmitted, stateEmitter, parentEventEmitter)

    coVerify(exactly = 0) { mockRepository.checkSvrCredentials(any(), any()) }
    assertThat(emittedEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
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
