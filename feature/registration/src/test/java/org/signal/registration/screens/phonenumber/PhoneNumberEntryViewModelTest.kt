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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.RegistrationApiV2.CheckSvrCredentialsError
import org.signal.network.api.RegistrationApiV2.CheckSvrCredentialsResponse
import org.signal.network.api.RegistrationApiV2.CreateSessionError
import org.signal.network.api.RegistrationApiV2.RegisterAccountError
import org.signal.network.api.RegistrationApiV2.RegisterAccountResponse
import org.signal.network.api.RegistrationApiV2.RegistrationLockResponse
import org.signal.network.api.RegistrationApiV2.RequestVerificationCodeError
import org.signal.network.api.RegistrationApiV2.SessionMetadata
import org.signal.network.api.RegistrationApiV2.SvrCredentials
import org.signal.network.api.RegistrationApiV2.ThirdPartyServiceErrorResponse
import org.signal.network.api.RegistrationApiV2.UpdateSessionError
import org.signal.registration.KeyMaterial
import org.signal.registration.PreExistingRegistrationData
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.VerificationCodeRequest
import org.signal.registration.screens.localbackuprestore.LocalBackupRestoreResult
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneNumberEntryViewModelTest {

  private lateinit var viewModel: PhoneNumberEntryViewModel
  private lateinit var mockRepository: RegistrationRepository
  private lateinit var parentState: MutableStateFlow<RegistrationFlowState>
  private lateinit var emittedStates: MutableList<PhoneNumberEntryState>
  private lateinit var stateEmitter: (PhoneNumberEntryState) -> Unit
  private lateinit var emittedEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    every { mockRepository.getDefaultRegionCode() } returns "US"

    parentState = MutableStateFlow(RegistrationFlowState())
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
    emittedEvents = mutableListOf()
    parentEventEmitter = { event -> emittedEvents.add(event) }
    viewModel = PhoneNumberEntryViewModel(mockRepository, parentState, parentEventEmitter)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `PhoneNumberChanged extracts digits and formats number`() = runTest {
    val initialState = PhoneNumberEntryState()

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "555-123-4567"),
      parentEventEmitter,
      stateEmitter
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
      PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "5551234567"),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().nationalNumber).isEqualTo("5551234567")
    assertThat(emittedStates.last().formattedNumber).isEqualTo("(555) 123-4567")
  }

  @Test
  fun `PhoneNumberChanged formats progressively as digits are added`() = runTest {
    var state = PhoneNumberEntryState()

    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "5"), parentEventEmitter, stateEmitter)
    state = emittedStates.last()
    assertThat(state.nationalNumber).isEqualTo("5")

    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "5", newValue = "55"), parentEventEmitter, stateEmitter)
    state = emittedStates.last()
    assertThat(state.nationalNumber).isEqualTo("55")

    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "55", newValue = "555"), parentEventEmitter, stateEmitter)
    state = emittedStates.last()
    assertThat(state.nationalNumber).isEqualTo("555")

    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "555", newValue = "5551"), parentEventEmitter, stateEmitter)
    state = emittedStates.last()
    assertThat(state.nationalNumber).isEqualTo("5551")
    // libphonenumber formats progressively - at 4 digits it's still building the format
    assertThat(state.formattedNumber).isEqualTo("555-1")

    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "555-1", newValue = "55512"), parentEventEmitter, stateEmitter)
    state = emittedStates.last()
    assertThat(state.nationalNumber).isEqualTo("55512")
    assertThat(state.formattedNumber).isEqualTo("555-12")
  }

  @Test
  fun `PhoneNumberChanged ignores non-digit characters`() = runTest {
    val initialState = PhoneNumberEntryState()

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "(555) abc 123-4567!"),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().nationalNumber).isEqualTo("5551234567")
  }

  @Test
  fun `PhoneNumberChanged with same digits does not emit new state`() = runTest {
    val initialState = PhoneNumberEntryState(nationalNumber = "5551234567", formattedNumber = "(555) 123-4567")

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "(555) 123-4567", newValue = "555-123-4567"),
      parentEventEmitter,
      stateEmitter
    )

    // Should emit the same state since digits haven't changed
    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last()).isEqualTo(initialState)
  }

  @Test
  fun `PhoneNumberChanged with a full number including country code splits out the country code`() = runTest {
    // Simulates OS autofill dumping a full E164 into the national number field.
    val initialState = PhoneNumberEntryState(regionCode = "GB", countryCode = "44")

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "+1 (555) 123-4567"),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    val result = emittedStates.last()
    assertThat(result.countryCode).isEqualTo("1")
    assertThat(result.regionCode).isEqualTo("US")
    assertThat(result.nationalNumber).isEqualTo("5551234567")
    assertThat(result.formattedNumber).isEqualTo("(555) 123-4567")
  }

  @Test
  fun `PhoneNumberChanged with a pasted number including the country code but no plus splits out the country code`() = runTest {
    val initialState = PhoneNumberEntryState(regionCode = "US", countryCode = "1")

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "16105550103"),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    val result = emittedStates.last()
    assertThat(result.countryCode).isEqualTo("1")
    assertThat(result.regionCode).isEqualTo("US")
    assertThat(result.nationalNumber).isEqualTo("6105550103")
    assertThat(result.formattedNumber).isEqualTo("(610) 555-0103")
  }

  @Test
  fun `PhoneNumberChanged with a pasted number whose explicit country code differs splits it out`() = runTest {
    // GB number pasted (with country code, no plus) while US is selected.
    val initialState = PhoneNumberEntryState(regionCode = "US", countryCode = "1")

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "442079460958"),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    val result = emittedStates.last()
    assertThat(result.countryCode).isEqualTo("44")
    assertThat(result.regionCode).isEqualTo("GB")
    assertThat(result.nationalNumber).isEqualTo("2079460958")
  }

  @Test
  fun `PhoneNumberChanged strips a redundant leading trunk prefix`() = runTest {
    // GB number pasted with a leading national trunk '0'.
    val initialState = PhoneNumberEntryState(regionCode = "GB", countryCode = "44")

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "02079460958"),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    val result = emittedStates.last()
    assertThat(result.countryCode).isEqualTo("44")
    assertThat(result.nationalNumber).isEqualTo("2079460958")
  }

  @Test
  fun `PhoneNumberChanged with a valid-length national number does not reinterpret leading digits as a country code`() = runTest {
    val initialState = PhoneNumberEntryState(regionCode = "US", countryCode = "1")

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "6105550103"),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    val result = emittedStates.last()
    assertThat(result.countryCode).isEqualTo("1")
    assertThat(result.nationalNumber).isEqualTo("6105550103")
  }

  @Test
  fun `PhoneNumberChanged does not split out the country code when the number was typed rather than pasted`() = runTest {
    // Reaching the same digits as the paste test, but by typing one final digit: the leading 1 must be kept.
    val initialState = PhoneNumberEntryState(regionCode = "US", countryCode = "1", nationalNumber = "1610555010", formattedNumber = "1610555010")

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "1610555010", newValue = "16105550103"),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    val result = emittedStates.last()
    assertThat(result.countryCode).isEqualTo("1")
    assertThat(result.nationalNumber).isEqualTo("16105550103")
  }

  @Test
  fun `PhoneNumberChanged with a leading plus but no usable number keeps the digits as the national number`() = runTest {
    val initialState = PhoneNumberEntryState(regionCode = "US", countryCode = "1")

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "+5"),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().nationalNumber).isEqualTo("5")
    assertThat(emittedStates.last().countryCode).isEqualTo("1")
  }

  @Test
  fun `derived validity is not invalid while a number is still too short`() = runTest {
    val initialState = PhoneNumberEntryState(regionCode = "US", countryCode = "1")

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "555"), parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().isNumberInvalid).isFalse()
    assertThat(emittedStates.last().isNumberPossible).isFalse()
  }

  @Test
  fun `derived validity is not invalid for a single freshly typed digit that cannot yet be parsed`() = runTest {
    val initialState = PhoneNumberEntryState(regionCode = "US", countryCode = "1")

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "1"), parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().isNumberInvalid).isFalse()
  }

  @Test
  fun `derived validity is invalid when a number is too long`() = runTest {
    val initialState = PhoneNumberEntryState(regionCode = "US", countryCode = "1")

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "5551234567890123"), parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().isNumberInvalid).isTrue()
  }

  @Test
  fun `derived validity is possible and not invalid for a possible number`() = runTest {
    val initialState = PhoneNumberEntryState(regionCode = "US", countryCode = "1")

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "5551234567"), parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().isNumberInvalid).isFalse()
    assertThat(emittedStates.last().isNumberPossible).isTrue()
  }

  @Test
  fun `CountryCodeChanged updates country code and region`() = runTest {
    val initialState = PhoneNumberEntryState()

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.CountryCodeChanged("44"),
      parentEventEmitter,
      stateEmitter
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
      parentEventEmitter,
      stateEmitter
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
      parentEventEmitter,
      stateEmitter
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
    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.CountryCodeChanged("44"), parentEventEmitter, stateEmitter)

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
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(
      RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.CountryCodePicker())
    )
  }

  @Test
  fun `LinkDevice navigates to the link account flow`() = runTest {
    viewModel.applyEvent(
      PhoneNumberEntryState(),
      PhoneNumberEntryScreenEvents.LinkDevice,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.LinkAccount>()
  }

  @Test
  fun `initial state reflects repository link and sync availability`() = runTest {
    every { mockRepository.isLinkAndSyncAvailable } returns true

    val viewModel = PhoneNumberEntryViewModel(mockRepository, parentState, parentEventEmitter)
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.isLinkAndSyncAvailable).isTrue()
  }

  @Test
  fun `NetworkErrorDialogDismissed clears only the network error dialog`() = runTest {
    val initialState = PhoneNumberEntryState(
      dialogs = PhoneNumberEntryState.Dialogs(networkError = true, unknownError = true)
    )

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.NetworkErrorDialogDismissed,
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().dialogs).isEqualTo(PhoneNumberEntryState.Dialogs(unknownError = true))
  }

  @Test
  fun `German country code formats correctly`() = runTest {
    var state = PhoneNumberEntryState()

    // Set German country code
    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.CountryCodeChanged("49"), parentEventEmitter, stateEmitter)
    state = emittedStates.last()
    assertThat(state.regionCode).isEqualTo("DE")

    // Enter a German number
    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.NationalNumberChanged(oldValue = "", newValue = "15123456789"), parentEventEmitter, stateEmitter)
    state = emittedStates.last()
    assertThat(state.nationalNumber).isEqualTo("15123456789")
  }

  // ==================== Trunk Prefix Normalization Tests ====================

  @Test
  fun `NextClicked strips a redundant leading trunk prefix before showing the confirmation dialog`() = runTest {
    // Dutch users habitually type their number with the leading national '0' (e.g. 0612345678), which must not end
    // up in the E164 (+31612345678, not +310612345678).
    val initialState = PhoneNumberEntryState(regionCode = "NL", countryCode = "31", nationalNumber = "0612345678", formattedNumber = "06 12345678")

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.NextClicked, parentEventEmitter, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    val result = emittedStates.last()
    assertThat(result.nationalNumber).isEqualTo("612345678")
    assertThat(result.countryCode).isEqualTo("31")
    assertThat(result.isNumberPossible).isTrue()
    assertThat(result.dialogs.confirmNumber).isTrue()
  }

  @Test
  fun `NextClicked leaves a number without a trunk prefix unchanged`() = runTest {
    val initialState = PhoneNumberEntryState(regionCode = "US", countryCode = "1", nationalNumber = "5551234567", formattedNumber = "(555) 123-4567")

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.NextClicked, parentEventEmitter, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    val result = emittedStates.last()
    assertThat(result.nationalNumber).isEqualTo("5551234567")
    assertThat(result.formattedNumber).isEqualTo("(555) 123-4567")
    assertThat(result.dialogs.confirmNumber).isTrue()
  }

  @Test
  fun `NextClicked preserves a leading zero that is a significant part of the number`() = runTest {
    // Italian landlines include the leading zero as part of the number itself, so it must not be stripped.
    val initialState = PhoneNumberEntryState(regionCode = "IT", countryCode = "39", nationalNumber = "0612345678", formattedNumber = "06 1234 5678")

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.NextClicked, parentEventEmitter, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    val result = emittedStates.last()
    assertThat(result.nationalNumber).isEqualTo("0612345678")
    assertThat(result.dialogs.confirmNumber).isTrue()
  }

  @Test
  fun `PhoneNumberConfirmed submits the E164 without a redundant leading trunk prefix`() = runTest {
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      regionCode = "NL",
      countryCode = "31",
      nationalNumber = "0612345678"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    coVerify(exactly = 1) { mockRepository.createSession("+31612345678") }
    assertThat(emittedStates.last().nationalNumber).isEqualTo("612345678")
    assertThat(emittedEvents.filterIsInstance<RegistrationFlowEvent.E164Chosen>())
      .isEqualTo(listOf(RegistrationFlowEvent.E164Chosen("+31612345678")))
  }

  // ==================== FullPhoneNumberEntered Tests ====================

  @Test
  fun `PhoneNumberHintSelected populates country and number from US E164`() = runTest {
    viewModel.applyEvent(
      PhoneNumberEntryState(),
      PhoneNumberEntryScreenEvents.FullPhoneNumberEntered("+15551234567"),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    val result = emittedStates.last()
    assertThat(result.countryCode).isEqualTo("1")
    assertThat(result.regionCode).isEqualTo("US")
    assertThat(result.nationalNumber).isEqualTo("5551234567")
    assertThat(result.formattedNumber).isEqualTo("(555) 123-4567")
  }

  @Test
  fun `PhoneNumberHintSelected populates country and number from GB E164`() = runTest {
    viewModel.applyEvent(
      PhoneNumberEntryState(),
      PhoneNumberEntryScreenEvents.FullPhoneNumberEntered("+442079460958"),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    val result = emittedStates.last()
    assertThat(result.countryCode).isEqualTo("44")
    assertThat(result.regionCode).isEqualTo("GB")
    assertThat(result.nationalNumber).isEqualTo("2079460958")
  }

  @Test
  fun `PhoneNumberHintSelected leaves state unchanged for unparseable number`() = runTest {
    val initialState = PhoneNumberEntryState(countryCode = "1", regionCode = "US")

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.FullPhoneNumberEntered("not-a-number"),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last()).isEqualTo(initialState)
  }

  @Test
  fun `FullPhoneNumberEntered with autoConfirm populates and opens the confirmation dialog`() = runTest {
    viewModel.applyEvent(
      PhoneNumberEntryState(),
      PhoneNumberEntryScreenEvents.FullPhoneNumberEntered("+15551234567", autoConfirm = true),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    val result = emittedStates.last()
    assertThat(result.nationalNumber).isEqualTo("5551234567")
    assertThat(result.dialogs.confirmNumber).isTrue()

    // We only open the dialog; we do not submit on our own.
    assertThat(emittedEvents).isEmpty()
    coVerify(exactly = 0) { mockRepository.createSession(any()) }
  }

  @Test
  fun `FullPhoneNumberEntered with autoConfirm does not open dialog when number is not possible`() = runTest {
    viewModel.applyEvent(
      PhoneNumberEntryState(),
      PhoneNumberEntryScreenEvents.FullPhoneNumberEntered("not-a-number", autoConfirm = true),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().dialogs.confirmNumber).isFalse()
    assertThat(emittedEvents).isEmpty()
  }

  @Test
  fun `FullPhoneNumberEntered without autoConfirm only populates and does not open dialog`() = runTest {
    viewModel.applyEvent(
      PhoneNumberEntryState(),
      PhoneNumberEntryScreenEvents.FullPhoneNumberEntered("+15551234567", autoConfirm = false),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().nationalNumber).isEqualTo("5551234567")
    assertThat(emittedStates.last().dialogs.confirmNumber).isFalse()
    assertThat(emittedEvents).isEmpty()
  }

  // ==================== Initialize Tests ====================

  @Test
  fun `state is populated with the default country and initialized once construction settles`() {
    // The view model was constructed and its event loop advanced to idle in setup().
    val state = viewModel.state.value
    assertThat(state.regionCode).isEqualTo("US")
    assertThat(state.countryCode).isEqualTo("1")
    assertThat(state.countryName).isEqualTo("United States")
    assertThat(state.initialized).isTrue()
  }

  @Test
  fun `Initialize loads restored SVR credentials into state and marks it initialized`() = runTest {
    val credentials = listOf(SvrCredentials(username = "user", password = "pass"))
    coEvery { mockRepository.getRestoredSvrCredentials() } returns credentials

    viewModel.applyEvent(PhoneNumberEntryState(), PhoneNumberEntryScreenEvents.Initialize, parentEventEmitter, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().restoredSvrCredentials).isEqualTo(credentials)
    assertThat(emittedStates.last().initialized).isTrue()
  }

  @Test
  fun `Initialize does not prefill when the number field is already populated`() = runTest {
    val preExisting = mockk<PreExistingRegistrationData>(relaxed = true)
    every { preExisting.e164 } returns "+15551234567"
    parentState.value = RegistrationFlowState(preExistingRegistrationData = preExisting)

    val alreadyPopulated = PhoneNumberEntryState(countryCode = "44", regionCode = "GB", nationalNumber = "2079460958", formattedNumber = "2079460958")
    viewModel.applyEvent(alreadyPopulated, PhoneNumberEntryScreenEvents.Initialize, parentEventEmitter, stateEmitter)

    val result = emittedStates.last()
    assertThat(result.nationalNumber).isEqualTo("2079460958")
    assertThat(result.countryCode).isEqualTo("44")
  }

  // ==================== Parent State Tests ====================

  @Test
  fun `parent state changes are merged into state through the event stream`() = runTest {
    parentState.value = RegistrationFlowState(sessionE164 = "+15551234567")
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.state.value.sessionE164).isEqualTo("+15551234567")
    assertThat(viewModel.state.value.regionCode).isEqualTo("US")
  }

  // ==================== Pre-existing Registration Data Prefill Tests ====================

  @Test
  fun `prefills phone number from preExistingRegistrationData when number is empty`() = runTest {
    val preExisting = mockk<PreExistingRegistrationData>(relaxed = true)
    every { preExisting.e164 } returns "+15551234567"

    val populatedParentState = MutableStateFlow(RegistrationFlowState(preExistingRegistrationData = preExisting))
    val vm = PhoneNumberEntryViewModel(mockRepository, populatedParentState, parentEventEmitter)

    val states = mutableListOf<PhoneNumberEntryState>()
    val job = launch { vm.state.collect { states.add(it) } }
    advanceUntilIdle()
    job.cancel()

    val latest = states.last()
    assertThat(latest.nationalNumber).isEqualTo("5551234567")
    assertThat(latest.countryCode).isEqualTo("1")
    assertThat(latest.regionCode).isEqualTo("US")
  }

  @Test
  fun `does not prefill phone number when there is no preExistingRegistrationData`() = runTest {
    val emptyParentState = MutableStateFlow(RegistrationFlowState())
    val vm = PhoneNumberEntryViewModel(mockRepository, emptyParentState, parentEventEmitter)

    val states = mutableListOf<PhoneNumberEntryState>()
    val job = launch { vm.state.collect { states.add(it) } }
    advanceUntilIdle()
    job.cancel()

    assertThat(states.last().nationalNumber).isEmpty()
  }

  // ==================== PhoneNumberSubmitted Tests ====================

  @Test
  fun `PhoneNumberSubmitted creates session and requests code on success`() = runTest {
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    assertThat(emittedStates.last().sessionMetadata).isNotNull()
    assertThat(emittedEvents).hasSize(4)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.VerificationCodeRequested>()
    assertThat(emittedEvents[1]).isInstanceOf<RegistrationFlowEvent.SessionUpdated>()
    assertThat(emittedEvents[2]).isInstanceOf<RegistrationFlowEvent.E164Chosen>()
    assertThat(emittedEvents[3])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted navigates to captcha when required`() = runTest {
    val sessionMetadata = createSessionMetadata(requestedInformation = listOf("captcha"))

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    assertThat(emittedEvents).hasSize(3)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.SessionUpdated>()
    assertThat(emittedEvents[1]).isInstanceOf<RegistrationFlowEvent.E164Chosen>()
    assertThat(emittedEvents[2])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.Captcha>()
  }

  @Test
  fun `PhoneNumberSubmitted handles rate limiting from createSession`() = runTest {
    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.NonSuccess(
        CreateSessionError.RateLimited(60.seconds)
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    assertThat(emittedStates.last().dialogs.rateLimitedRetryAfter).isEqualTo(60.seconds)
  }

  @Test
  fun `PhoneNumberSubmitted handles invalid request from createSession`() = runTest {
    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.NonSuccess(
        CreateSessionError.InvalidRequest("Bad request")
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    assertThat(emittedStates.last().dialogs.invalidPhoneNumber).isTrue()
  }

  @Test
  fun `PhoneNumberSubmitted handles network error`() = runTest {
    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.RetryableNetworkError(java.io.IOException("Network error"))

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    assertThat(emittedStates.last().dialogs.networkError).isTrue()
  }

  @Test
  fun `PhoneNumberSubmitted handles application error`() = runTest {
    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.ApplicationError(RuntimeException("Unexpected error"))

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    assertThat(emittedStates.last().dialogs.unknownError).isTrue()
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
      RequestResult.Success(existingSession)

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    // Should not create a new session, just request verification code
    assertThat(emittedEvents).hasSize(4)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.VerificationCodeRequested>()
    assertThat(emittedEvents[1]).isInstanceOf<RegistrationFlowEvent.SessionUpdated>()
    assertThat(emittedEvents[2]).isInstanceOf<RegistrationFlowEvent.E164Chosen>()
    assertThat(emittedEvents[3])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted skips the SMS request when one was recently sent for the same number`() = runTest {
    val existingSession = createSessionMetadata()
    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      sessionE164 = "+15551234567",
      sessionMetadata = existingSession,
      smsVerificationCodeRequest = VerificationCodeRequest("+15551234567", System.currentTimeMillis() + 30_000)
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    coVerify(exactly = 0) { mockRepository.createSession(any()) }
    coVerify(exactly = 0) { mockRepository.requestVerificationCode(any(), any(), any()) }

    assertThat(emittedEvents).hasSize(2)
    assertThat(emittedEvents[0]).isEqualTo(RegistrationFlowEvent.E164Chosen("+15551234567"))
    assertThat(emittedEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted requests a new SMS when the previous request window has expired`() = runTest {
    val existingSession = createSessionMetadata()

    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(existingSession)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      sessionE164 = "+15551234567",
      sessionMetadata = existingSession,
      smsVerificationCodeRequest = VerificationCodeRequest("+15551234567", System.currentTimeMillis() - 1_000)
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    coVerify(exactly = 1) { mockRepository.requestVerificationCode(any(), any(), any()) }
    assertThat(emittedEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted requests a new SMS when the recent request was for a different number`() = runTest {
    val existingSession = createSessionMetadata()

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(existingSession)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(existingSession)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      sessionE164 = "+15559999999",
      sessionMetadata = existingSession,
      smsVerificationCodeRequest = VerificationCodeRequest("+15559999999", System.currentTimeMillis() + 30_000)
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    coVerify(exactly = 1) { mockRepository.createSession("+15551234567") }
    coVerify(exactly = 1) { mockRepository.requestVerificationCode(any(), any(), any()) }
    assertThat(emittedEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `successful SMS request records the next allowed SMS and call request times from the response`() = runTest {
    val fixedNow = 1_000_000L
    val clockedViewModel = PhoneNumberEntryViewModel(mockRepository, parentState, parentEventEmitter, clock = { fixedNow })
    testDispatcher.scheduler.advanceUntilIdle()
    emittedEvents.clear()

    val sessionMetadata = createSessionMetadata(nextSms = 45L, nextCall = 120L)
    coEvery { mockRepository.createSession(any()) } returns RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns RequestResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(countryCode = "1", nationalNumber = "5551234567")

    clockedViewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    assertThat(emittedEvents.filterIsInstance<RegistrationFlowEvent.VerificationCodeRequested>())
      .isEqualTo(listOf(RegistrationFlowEvent.VerificationCodeRequested("+15551234567", nextSmsAllowedTimestamp = fixedNow + 45_000, nextCallAllowedTimestamp = fixedNow + 120_000)))
  }

  @Test
  fun `successful SMS request defaults to a 60 second window when the response has no nextSms`() = runTest {
    val fixedNow = 1_000_000L
    val clockedViewModel = PhoneNumberEntryViewModel(mockRepository, parentState, parentEventEmitter, clock = { fixedNow })
    testDispatcher.scheduler.advanceUntilIdle()
    emittedEvents.clear()

    val sessionMetadata = createSessionMetadata(nextSms = null)
    coEvery { mockRepository.createSession(any()) } returns RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns RequestResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(countryCode = "1", nationalNumber = "5551234567")

    clockedViewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    assertThat(emittedEvents.filterIsInstance<RegistrationFlowEvent.VerificationCodeRequested>())
      .isEqualTo(listOf(RegistrationFlowEvent.VerificationCodeRequested("+15551234567", nextSmsAllowedTimestamp = fixedNow + 60_000, nextCallAllowedTimestamp = null)))
  }

  @Test
  fun `PhoneNumberSubmitted rate limited by requestVerificationCode navigates to code entry with the wait recorded`() = runTest {
    val fixedNow = 1_000_000L
    val clockedViewModel = PhoneNumberEntryViewModel(mockRepository, parentState, parentEventEmitter, clock = { fixedNow })
    testDispatcher.scheduler.advanceUntilIdle()
    emittedEvents.clear()

    val sessionMetadata = createSessionMetadata()

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RequestVerificationCodeError.RateLimited(30.seconds, sessionMetadata)
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    clockedViewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    assertThat(emittedStates.last().dialogs.rateLimitedRetryAfter).isNull()
    assertThat(emittedStates.last().sessionMetadata).isEqualTo(sessionMetadata)

    assertThat(emittedEvents).hasSize(4)
    assertThat(emittedEvents[0]).isEqualTo(RegistrationFlowEvent.VerificationCodeRequested("+15551234567", nextSmsAllowedTimestamp = fixedNow + 30_000, nextCallAllowedTimestamp = null))
    assertThat(emittedEvents[1]).isEqualTo(RegistrationFlowEvent.SessionUpdated(sessionMetadata))
    assertThat(emittedEvents[2]).isEqualTo(RegistrationFlowEvent.E164Chosen("+15551234567"))
    assertThat(emittedEvents[3])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted handles session not found`() = runTest {
    val sessionMetadata = createSessionMetadata()

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RequestVerificationCodeError.SessionNotFound("Session expired")
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `PhoneNumberSubmitted handles transport not supported`() = runTest {
    val sessionMetadata = createSessionMetadata()

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport(sessionMetadata)
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    assertThat(emittedStates.last().dialogs.couldNotRequestCodeWithSelectedTransport).isTrue()
  }

  @Test
  fun `PhoneNumberSubmitted handles third party service error`() = runTest {
    val sessionMetadata = createSessionMetadata()

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RequestVerificationCodeError.ThirdPartyServiceError(
          ThirdPartyServiceErrorResponse("Provider error", false)
        )
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    assertThat(emittedStates.last().dialogs.unableToSendSms).isTrue()
  }

  // ==================== Push Challenge Tests ====================

  @Test
  fun `PhoneNumberSubmitted with push challenge submits token when received`() = runTest {
    val sessionWithPushChallenge = createSessionMetadata(requestedInformation = listOf("pushChallenge"))
    val sessionAfterPushChallenge = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionWithPushChallenge)
    coEvery { mockRepository.awaitPushChallengeToken() } returns "test-push-challenge-token"
    coEvery { mockRepository.submitPushChallengeToken(any(), any()) } returns
      RequestResult.Success(sessionAfterPushChallenge)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionAfterPushChallenge)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    // Verify navigation to verification code entry
    assertThat(emittedEvents).hasSize(4)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.VerificationCodeRequested>()
    assertThat(emittedEvents[1]).isInstanceOf<RegistrationFlowEvent.SessionUpdated>()
    assertThat(emittedEvents[2]).isInstanceOf<RegistrationFlowEvent.E164Chosen>()
    assertThat(emittedEvents[3])
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
      RequestResult.Success(sessionWithPushChallenge)
    coEvery { mockRepository.awaitPushChallengeToken() } returns null
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionWithPushChallenge)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    // Verify navigation continues despite no push challenge token
    assertThat(emittedEvents).hasSize(4)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.VerificationCodeRequested>()
    assertThat(emittedEvents[1]).isInstanceOf<RegistrationFlowEvent.SessionUpdated>()
    assertThat(emittedEvents[2]).isInstanceOf<RegistrationFlowEvent.E164Chosen>()
    assertThat(emittedEvents[3])
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
      RequestResult.Success(sessionWithPushChallenge)
    coEvery { mockRepository.awaitPushChallengeToken() } returns "test-push-challenge-token"
    coEvery { mockRepository.submitPushChallengeToken(any(), any()) } returns
      RequestResult.NonSuccess(
        UpdateSessionError.RejectedUpdate("Invalid token")
      )
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionWithPushChallenge)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    // Verify navigation continues despite push challenge submission failure
    assertThat(emittedEvents).hasSize(4)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.VerificationCodeRequested>()
    assertThat(emittedEvents[1]).isInstanceOf<RegistrationFlowEvent.SessionUpdated>()
    assertThat(emittedEvents[2]).isInstanceOf<RegistrationFlowEvent.E164Chosen>()
    assertThat(emittedEvents[3])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with push challenge continues when submission has network error`() = runTest {
    val sessionWithPushChallenge = createSessionMetadata(requestedInformation = listOf("pushChallenge"))

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionWithPushChallenge)
    coEvery { mockRepository.awaitPushChallengeToken() } returns "test-push-challenge-token"
    coEvery { mockRepository.submitPushChallengeToken(any(), any()) } returns
      RequestResult.RetryableNetworkError(java.io.IOException("Connection lost"))
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionWithPushChallenge)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    // Verify navigation continues despite network error
    assertThat(emittedEvents).hasSize(4)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.VerificationCodeRequested>()
    assertThat(emittedEvents[1]).isInstanceOf<RegistrationFlowEvent.SessionUpdated>()
    assertThat(emittedEvents[2]).isInstanceOf<RegistrationFlowEvent.E164Chosen>()
    assertThat(emittedEvents[3])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with push challenge continues when submission has application error`() = runTest {
    val sessionWithPushChallenge = createSessionMetadata(requestedInformation = listOf("pushChallenge"))

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionWithPushChallenge)
    coEvery { mockRepository.awaitPushChallengeToken() } returns "test-push-challenge-token"
    coEvery { mockRepository.submitPushChallengeToken(any(), any()) } returns
      RequestResult.ApplicationError(RuntimeException("Unexpected error"))
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionWithPushChallenge)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    // Verify navigation continues despite application error
    assertThat(emittedEvents).hasSize(4)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.VerificationCodeRequested>()
    assertThat(emittedEvents[1]).isInstanceOf<RegistrationFlowEvent.SessionUpdated>()
    assertThat(emittedEvents[2]).isInstanceOf<RegistrationFlowEvent.E164Chosen>()
    assertThat(emittedEvents[3])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with push challenge navigates to captcha if still required after submission`() = runTest {
    val sessionWithPushChallenge = createSessionMetadata(requestedInformation = listOf("pushChallenge", "captcha"))
    val sessionAfterPushChallenge = createSessionMetadata(requestedInformation = listOf("captcha"))

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionWithPushChallenge)
    coEvery { mockRepository.awaitPushChallengeToken() } returns "test-push-challenge-token"
    coEvery { mockRepository.submitPushChallengeToken(any(), any()) } returns
      RequestResult.Success(sessionAfterPushChallenge)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    // Verify navigation to captcha
    assertThat(emittedEvents).hasSize(3)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.SessionUpdated>()
    assertThat(emittedEvents[1]).isInstanceOf<RegistrationFlowEvent.E164Chosen>()
    assertThat(emittedEvents[2])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.Captcha>()
  }

  @Test
  fun `PhoneNumberSubmitted with push challenge resets state when session not found`() = runTest {
    val sessionWithPushChallenge = createSessionMetadata(requestedInformation = listOf("pushChallenge"))

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionWithPushChallenge)
    coEvery { mockRepository.awaitPushChallengeToken() } returns "test-push-challenge-token"
    coEvery { mockRepository.submitPushChallengeToken(any(), any()) } returns
      RequestResult.NonSuccess(
        UpdateSessionError.SessionNotFound("Session expired")
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567"
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Verify spinner states
    assertThat(emittedStates.first().showSpinner).isTrue()
    assertThat(emittedStates.last().showSpinner).isFalse()

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  // ==================== CaptchaCompleted Tests ====================

  @Test
  fun `CaptchaCompleted submits token and navigates to verification code`() = runTest {
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())
    val initialState = PhoneNumberEntryState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.submitCaptchaToken(any(), any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionMetadata)

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"), parentEventEmitter, stateEmitter)

    assertThat(emittedEvents).hasSize(4)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.VerificationCodeRequested>()
    assertThat(emittedEvents[1]).isInstanceOf<RegistrationFlowEvent.SessionUpdated>()
    assertThat(emittedEvents[2]).isInstanceOf<RegistrationFlowEvent.E164Chosen>()
    assertThat(emittedEvents[3])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `CaptchaCompleted returns error when no session exists`() = runTest {
    val initialState = PhoneNumberEntryState(sessionMetadata = null)

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"), parentEventEmitter, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().dialogs.unknownError).isTrue()
  }

  @Test
  fun `CaptchaCompleted handles captcha still required after submission`() = runTest {
    val sessionWithCaptcha = createSessionMetadata(requestedInformation = listOf("captcha"))
    val initialState = PhoneNumberEntryState(sessionMetadata = sessionWithCaptcha)

    coEvery { mockRepository.submitCaptchaToken(any(), any()) } returns
      RequestResult.Success(sessionWithCaptcha)

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"), parentEventEmitter, stateEmitter)

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
      RequestResult.NonSuccess(
        UpdateSessionError.RateLimited(45.seconds, sessionMetadata)
      )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"), parentEventEmitter, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().dialogs.rateLimitedRetryAfter).isEqualTo(45.seconds)
  }

  @Test
  fun `CaptchaCompleted rate limited when requesting the code navigates to code entry with the wait recorded`() = runTest {
    val fixedNow = 1_000_000L
    val clockedViewModel = PhoneNumberEntryViewModel(mockRepository, parentState, parentEventEmitter, clock = { fixedNow })
    testDispatcher.scheduler.advanceUntilIdle()
    emittedEvents.clear()

    val sessionMetadata = createSessionMetadata()
    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      sessionMetadata = sessionMetadata
    )

    coEvery { mockRepository.submitCaptchaToken(any(), any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RequestVerificationCodeError.RateLimited(45.seconds, sessionMetadata)
      )

    clockedViewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"), parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().dialogs.rateLimitedRetryAfter).isNull()
    assertThat(emittedEvents).hasSize(4)
    assertThat(emittedEvents[0]).isEqualTo(RegistrationFlowEvent.VerificationCodeRequested("+15551234567", nextSmsAllowedTimestamp = fixedNow + 45_000, nextCallAllowedTimestamp = null))
    assertThat(emittedEvents[1]).isEqualTo(RegistrationFlowEvent.SessionUpdated(sessionMetadata))
    assertThat(emittedEvents[2]).isEqualTo(RegistrationFlowEvent.E164Chosen("+15551234567"))
    assertThat(emittedEvents[3])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `CaptchaCompleted handles rejected update`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = PhoneNumberEntryState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.submitCaptchaToken(any(), any()) } returns
      RequestResult.NonSuccess(
        UpdateSessionError.RejectedUpdate("Invalid captcha")
      )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"), parentEventEmitter, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().dialogs.unknownError).isTrue()
  }

  @Test
  fun `CaptchaCompleted handles session not found`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = PhoneNumberEntryState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.submitCaptchaToken(any(), any()) } returns
      RequestResult.NonSuccess(
        UpdateSessionError.SessionNotFound("Session expired")
      )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"), parentEventEmitter, stateEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `CaptchaCompleted handles network error`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = PhoneNumberEntryState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.submitCaptchaToken(any(), any()) } returns
      RequestResult.RetryableNetworkError(java.io.IOException("Connection lost"))

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.CaptchaCompleted("captcha-token"), parentEventEmitter, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().dialogs.networkError).isTrue()
  }

  // ==================== ParentStateChanged Tests ====================

  @Test
  fun `ParentStateChanged copies preExistingRegistrationData from parent`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true)
    val parentFlowState = RegistrationFlowState(preExistingRegistrationData = preExistingData)

    viewModel.applyEvent(PhoneNumberEntryState(), PhoneNumberEntryScreenEvents.ParentStateChanged(parentFlowState), parentEventEmitter, stateEmitter)

    assertThat(emittedStates).hasSize(1)
    assertThat(emittedStates.last().preExistingRegistrationData).isEqualTo(preExistingData)
  }

  @Test
  fun `ParentStateChanged clears restoredSvrCredentials when doNotAttemptRecoveryPassword is true`() = runTest {
    val credentials = listOf(
      SvrCredentials(username = "user", password = "pass")
    )
    val state = PhoneNumberEntryState(restoredSvrCredentials = credentials)
    val parentFlowState = RegistrationFlowState(doNotAttemptRecoveryPassword = true)

    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.ParentStateChanged(parentFlowState), parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().restoredSvrCredentials).isEmpty()
  }

  @Test
  fun `ParentStateChanged keeps restoredSvrCredentials when doNotAttemptRecoveryPassword is false`() = runTest {
    val credentials = listOf(
      SvrCredentials(username = "user", password = "pass")
    )
    val state = PhoneNumberEntryState(restoredSvrCredentials = credentials)
    val parentFlowState = RegistrationFlowState(doNotAttemptRecoveryPassword = false)

    viewModel.applyEvent(state, PhoneNumberEntryScreenEvents.ParentStateChanged(parentFlowState), parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().restoredSvrCredentials).isEqualTo(credentials)
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
      RequestResult.Success(registerResponse to keyMaterial)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

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
      RequestResult.Success(registerResponse to keyMaterial)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    assertThat(emittedEvents.first()).isInstanceOf<RegistrationFlowEvent.Registered>()
    assertThat(emittedEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinCreate>()
  }

  @Test(expected = IllegalStateException::class)
  fun `PhoneNumberSubmitted with preExistingRegistrationData and SessionNotFoundOrNotVerified throws`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RegisterAccountError.SessionNotFoundOrNotVerified("Not found")
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)
  }

  @Test(expected = IllegalStateException::class)
  fun `PhoneNumberSubmitted with preExistingRegistrationData and DeviceTransferPossible throws`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RegisterAccountError.DeviceTransferPossible
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)
  }

  @Test
  fun `PhoneNumberSubmitted with preExistingRegistrationData and RegistrationLock navigates to PinEntryForRegistrationLock`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }
    val svrCredentials = SvrCredentials(username = "user", password = "pass")
    val registrationLockData = RegistrationLockResponse(
      timeRemaining = 60000L,
      svr2Credentials = svrCredentials
    )

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RegisterAccountError.RegistrationLock(registrationLockData)
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // The e164 must be recorded before navigating, otherwise the PIN entry screen has nothing to register with and resets the flow
    assertThat(emittedEvents).hasSize(2)
    assertThat(emittedEvents[0]).isEqualTo(RegistrationFlowEvent.E164Chosen("+15551234567"))
    assertThat(emittedEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinEntryForRegistrationLock>()
  }

  @Test
  fun `LocalBackupRestoreCompleted with RegistrationLock retries with the reglock token derived from the restored AEP`() = runTest {
    val aep = AccountEntropyPool.generate()
    val keyMaterial = mockk<KeyMaterial>(relaxed = true) {
      every { accountEntropyPool } returns aep
    }
    val response = mockk<RegisterAccountResponse>(relaxed = true)
    val registrationLockData = RegistrationLockResponse(
      timeRemaining = 60000L,
      svr2Credentials = SvrCredentials(username = "user", password = "pass")
    )

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), registrationLock = any<String>(), any(), any(), any()) } returns
      RequestResult.Success(response to keyMaterial)
    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), registrationLock = null, any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RegisterAccountError.RegistrationLock(registrationLockData)
      )

    val initialState = PhoneNumberEntryState(sessionE164 = "+15551234567")

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.LocalBackupRestoreCompleted(LocalBackupRestoreResult.Success(aep)),
      parentEventEmitter,
      stateEmitter
    )

    coVerify {
      mockRepository.registerAccountWithRecoveryPassword(any(), any(), registrationLock = aep.deriveMasterKey().deriveRegistrationLock(), any(), any(), any())
    }
    assertThat(emittedEvents).hasSize(3)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.UserSuppliedAepSubmitted>()
    assertThat(emittedEvents[1]).isInstanceOf<RegistrationFlowEvent.Registered>()
    assertThat(emittedEvents[2])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.PinCreate)
  }

  @Test
  fun `LocalBackupRestoreCompleted with RegistrationLock when already providing the reglock token navigates to PinEntryForRegistrationLock`() = runTest {
    val aep = AccountEntropyPool.generate()
    val registrationLockData = RegistrationLockResponse(
      timeRemaining = 60000L,
      svr2Credentials = SvrCredentials(username = "user", password = "pass")
    )

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RegisterAccountError.RegistrationLock(registrationLockData)
      )

    val initialState = PhoneNumberEntryState(sessionE164 = "+15551234567")

    viewModel.applyEvent(
      initialState,
      PhoneNumberEntryScreenEvents.LocalBackupRestoreCompleted(LocalBackupRestoreResult.Success(aep)),
      parentEventEmitter,
      stateEmitter
    )

    assertThat(emittedEvents).hasSize(2)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.UserSuppliedAepSubmitted>()
    assertThat(emittedEvents[1])
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
      RequestResult.NonSuccess(
        RegisterAccountError.RateLimited(30.seconds)
      )

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().dialogs.rateLimitedRetryAfter).isEqualTo(30.seconds)
  }

  @Test
  fun `PhoneNumberSubmitted with preExistingRegistrationData and InvalidRequest emits RecoveryPasswordInvalid and falls through to session creation`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(
        RegisterAccountError.InvalidRequest("Bad request")
      )
    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

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
      RequestResult.NonSuccess(
        RegisterAccountError.RegistrationRecoveryPasswordIncorrect("Wrong password")
      )
    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

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
      RequestResult.RetryableNetworkError(IOException("Network error"))

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().dialogs.networkError).isTrue()
  }

  @Test
  fun `PhoneNumberSubmitted with preExistingRegistrationData and ApplicationError returns UnknownError event`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15551234567"
      coEvery { registrationLockEnabled } returns false
    }

    coEvery { mockRepository.registerAccountWithRecoveryPassword(any(), any(), any(), any(), any()) } returns
      RequestResult.ApplicationError(RuntimeException("Unexpected"))

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().dialogs.unknownError).isTrue()
  }

  @Test
  fun `PhoneNumberSubmitted with non-matching preExistingRegistrationData skips RRP and creates session`() = runTest {
    val preExistingData = mockk<PreExistingRegistrationData>(relaxed = true) {
      coEvery { e164 } returns "+15559999999"
      coEvery { registrationLockEnabled } returns false
    }
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      preExistingRegistrationData = preExistingData
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

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
      SvrCredentials(username = "user", password = "pass")
    )
    val validCredential = SvrCredentials(username = "user", password = "pass")
    val checkResponse = CheckSvrCredentialsResponse(
      matches = mapOf("user:pass" to "match")
    )

    coEvery { mockRepository.checkSvrCredentials(any(), any()) } returns
      RequestResult.Success(checkResponse)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      restoredSvrCredentials = svrCredentials
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

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
      SvrCredentials(username = "user", password = "pass")
    )
    val checkResponse = CheckSvrCredentialsResponse(
      matches = mapOf("user:pass" to "no-match")
    )
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.checkSvrCredentials(any(), any()) } returns
      RequestResult.Success(checkResponse)
    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      restoredSvrCredentials = svrCredentials
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Should fall through to session creation
    assertThat(emittedEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with SVR credentials network error falls through to session creation`() = runTest {
    val svrCredentials = listOf(
      SvrCredentials(username = "user", password = "pass")
    )
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.checkSvrCredentials(any(), any()) } returns
      RequestResult.RetryableNetworkError(IOException("Network error"))
    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      restoredSvrCredentials = svrCredentials
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    // Should ignore error and fall through
    assertThat(emittedEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with SVR credentials application error falls through to session creation`() = runTest {
    val svrCredentials = listOf(
      SvrCredentials(username = "user", password = "pass")
    )
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.checkSvrCredentials(any(), any()) } returns
      RequestResult.ApplicationError(RuntimeException("Unexpected"))
    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      restoredSvrCredentials = svrCredentials
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    assertThat(emittedEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with SVR credentials invalid request falls through to session creation`() = runTest {
    val svrCredentials = listOf(
      SvrCredentials(username = "user", password = "pass")
    )
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.checkSvrCredentials(any(), any()) } returns
      RequestResult.NonSuccess(
        CheckSvrCredentialsError.InvalidRequest("Bad request")
      )
    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      restoredSvrCredentials = svrCredentials
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    assertThat(emittedEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with SVR credentials unauthorized falls through to session creation`() = runTest {
    val svrCredentials = listOf(
      SvrCredentials(username = "user", password = "pass")
    )
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.checkSvrCredentials(any(), any()) } returns
      RequestResult.NonSuccess(
        CheckSvrCredentialsError.Unauthorized
      )
    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      restoredSvrCredentials = svrCredentials
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

    assertThat(emittedEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.VerificationCodeEntry>()
  }

  @Test
  fun `PhoneNumberSubmitted with empty restoredSvrCredentials skips SVR check`() = runTest {
    val sessionMetadata = createSessionMetadata(requestedInformation = emptyList())

    coEvery { mockRepository.createSession(any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionMetadata)

    val initialState = PhoneNumberEntryState(
      countryCode = "1",
      nationalNumber = "5551234567",
      restoredSvrCredentials = emptyList()
    )

    viewModel.applyEvent(initialState, PhoneNumberEntryScreenEvents.PhoneNumberConfirmed, parentEventEmitter, stateEmitter)

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
    verified: Boolean = false,
    nextSms: Long? = null,
    nextCall: Long? = null
  ) = SessionMetadata(
    id = id,
    nextSms = nextSms,
    nextCall = nextCall,
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
  ) = RegisterAccountResponse(
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
