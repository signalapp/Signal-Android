/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.signal.libsignal.net.RequestResult
import org.signal.registration.KeyMaterial
import org.signal.registration.NetworkController
import org.signal.registration.PendingRestoreOption
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationCodeViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private lateinit var viewModel: VerificationCodeViewModel
  private lateinit var mockRepository: RegistrationRepository
  private lateinit var parentState: MutableStateFlow<RegistrationFlowState>
  private lateinit var emittedEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<VerificationCodeState>
  private lateinit var stateEmitter: (VerificationCodeState) -> Unit

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
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
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
    viewModel = VerificationCodeViewModel(mockRepository, parentState, parentEventEmitter)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ==================== ParentStateChanged Tests ====================

  @Test
  fun `ParentStateChanged with null sessionMetadata emits ResetState`() = runTest {
    val state = VerificationCodeState()
    val parentFlowState = RegistrationFlowState(
      sessionMetadata = null,
      sessionE164 = "+15551234567"
    )

    viewModel.applyEvent(state, VerificationCodeScreenEvents.ParentStateChanged(parentFlowState), stateEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `ParentStateChanged with null sessionE164 emits ResetState`() = runTest {
    val state = VerificationCodeState()
    val parentFlowState = RegistrationFlowState(
      sessionMetadata = createSessionMetadata(),
      sessionE164 = null
    )

    viewModel.applyEvent(state, VerificationCodeScreenEvents.ParentStateChanged(parentFlowState), stateEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `ParentStateChanged with both null values emits ResetState`() = runTest {
    val state = VerificationCodeState()
    val parentFlowState = RegistrationFlowState(
      sessionMetadata = null,
      sessionE164 = null
    )

    viewModel.applyEvent(state, VerificationCodeScreenEvents.ParentStateChanged(parentFlowState), stateEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `ParentStateChanged with valid session copies metadata and e164`() = runTest {
    val state = VerificationCodeState()
    val sessionMetadata = createSessionMetadata(id = "test-session")
    val e164 = "+15551234567"
    val parentFlowState = RegistrationFlowState(
      sessionMetadata = sessionMetadata,
      sessionE164 = e164
    )

    viewModel.applyEvent(state, VerificationCodeScreenEvents.ParentStateChanged(parentFlowState), stateEmitter)

    assertThat(emittedEvents).hasSize(0)
    assertThat(emittedStates.last().sessionMetadata).isEqualTo(sessionMetadata)
    assertThat(emittedStates.last().e164).isEqualTo(e164)
  }

  @Test
  fun `ParentStateChanged preserves existing snackbars`() = runTest {
    val state = VerificationCodeState(snackbars = VerificationCodeState.Snackbars(networkError = true))
    val sessionMetadata = createSessionMetadata()
    val parentFlowState = RegistrationFlowState(
      sessionMetadata = sessionMetadata,
      sessionE164 = "+15551234567"
    )

    viewModel.applyEvent(state, VerificationCodeScreenEvents.ParentStateChanged(parentFlowState), stateEmitter)

    assertThat(emittedStates.last().snackbars.networkError).isTrue()
  }

  // ==================== applyEvent: Snackbar Dismissal Tests ====================

  @Test
  fun `NetworkErrorSnackbarDismissed clears only the network error snackbar`() = runTest {
    val initialState = VerificationCodeState(
      snackbars = VerificationCodeState.Snackbars(networkError = true, incorrectVerificationCode = true)
    )

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.NetworkErrorSnackbarDismissed,
      stateEmitter
    )

    assertThat(emittedStates.last().snackbars).isEqualTo(VerificationCodeState.Snackbars(incorrectVerificationCode = true))
  }

  @Test
  fun `NetworkErrorSnackbarDismissed with no snackbars showing leaves snackbars cleared`() = runTest {
    val initialState = VerificationCodeState()

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.NetworkErrorSnackbarDismissed,
      stateEmitter
    )

    assertThat(emittedStates.last().snackbars).isEqualTo(VerificationCodeState.Snackbars())
  }

  // ==================== applyEvent: SMS Auto-Fill Tests ====================

  @Test
  fun `CodeAutoFilled stores the code in autoFillCode`() = runTest {
    val initialState = VerificationCodeState()

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeAutoFilled("123456"),
      stateEmitter
    )

    assertThat(emittedStates.last().autoFillCode).isEqualTo("123456")
  }

  @Test
  fun `DigitChanged with pasted hyphenated text populates all digits and submits`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.SubmitVerificationCodeError.InvalidSessionIdOrVerificationCode("Wrong code")
      )

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.DigitChanged(0, "123-456"),
      stateEmitter
    )

    coVerify { mockRepository.submitVerificationCode(sessionMetadata.id, "123456") }
    assertThat(emittedStates.first().digits).isEqualTo(listOf("1", "2", "3", "4", "5", "6"))
    assertThat(emittedStates.first().isSubmittingCode).isTrue()
  }

  @Test
  fun `DigitChanged with a pasted plain code populates all digits and submits`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.SubmitVerificationCodeError.InvalidSessionIdOrVerificationCode("Wrong code")
      )

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.DigitChanged(0, "123456"),
      stateEmitter
    )

    coVerify { mockRepository.submitVerificationCode(sessionMetadata.id, "123456") }
    assertThat(emittedStates.first().digits).isEqualTo(listOf("1", "2", "3", "4", "5", "6"))
    assertThat(emittedStates.first().isSubmittingCode).isTrue()
  }

  @Test
  fun `DigitChanged with pasted text of the wrong length is ignored`() = runTest {
    val initialState = VerificationCodeState(
      sessionMetadata = createSessionMetadata(),
      e164 = "+15551234567"
    )

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.DigitChanged(0, "12-345"),
      stateEmitter
    )

    coVerify(exactly = 0) { mockRepository.submitVerificationCode(any(), any()) }
    assertThat(emittedStates.last().digits).isEqualTo(listOf("", "", "", "", "", ""))
  }

  @Test
  fun `ConsumeAutoFillCode clears autoFillCode`() = runTest {
    val initialState = VerificationCodeState(autoFillCode = "123456")

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.ConsumeAutoFillCode,
      stateEmitter
    )

    assertThat(emittedStates.last().autoFillCode).isNull()
  }

  @Test
  fun `codes from the SMS retriever flow are pushed into the state`() = runTest(testDispatcher) {
    val smsCodes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val vm = VerificationCodeViewModel(mockRepository, parentState, parentEventEmitter, smsCodes)

    backgroundScope.launch { vm.state.collect {} }
    advanceUntilIdle()

    smsCodes.emit("123456")
    advanceUntilIdle()

    assertThat(vm.state.value.autoFillCode).isEqualTo("123456")
  }

  @Test
  fun `DigitChanged with a full code dispatched through the event channel submits it in a single pass`() = runTest(testDispatcher) {
    val sessionMetadata = createSessionMetadata()
    parentState.value = RegistrationFlowState(
      sessionMetadata = sessionMetadata,
      sessionE164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.SubmitVerificationCodeError.InvalidSessionIdOrVerificationCode("Wrong code")
      )

    backgroundScope.launch { viewModel.state.collect {} }
    advanceUntilIdle()

    viewModel.onEvent(VerificationCodeScreenEvents.DigitChanged(0, "123456"))
    advanceUntilIdle()

    coVerify { mockRepository.submitVerificationCode(sessionMetadata.id, "123456") }
  }

  // ==================== applyEvent: DigitChanged Tests ====================

  @Test
  fun `DigitChanged records the value at the given index`() = runTest {
    val initialState = VerificationCodeState()

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.DigitChanged(2, "7"),
      stateEmitter
    )

    assertThat(emittedStates.last().digits).isEqualTo(listOf("", "", "7", "", "", ""))
  }

  @Test
  fun `DigitChanged advances the focused digit index`() = runTest {
    val initialState = VerificationCodeState()

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.DigitChanged(2, "7"),
      stateEmitter
    )

    assertThat(emittedStates.last().focusedDigitIndex).isEqualTo(3)
  }

  @Test
  fun `DigitChanged with an empty value moves the focused digit index back`() = runTest {
    val initialState = VerificationCodeState(digits = listOf("1", "2", "3", "", "", ""))

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.DigitChanged(2, ""),
      stateEmitter
    )

    assertThat(emittedStates.last().focusedDigitIndex).isEqualTo(1)
  }

  @Test
  fun `DigitChanged with an out-of-bounds index throws`() = runTest {
    var threw = false
    try {
      viewModel.applyEvent(
        VerificationCodeState(),
        VerificationCodeScreenEvents.DigitChanged(9, "7"),
        stateEmitter
      )
    } catch (e: IllegalStateException) {
      threw = true
    }

    assertThat(threw).isTrue()
  }

  @Test
  fun `DigitChanged completing the code submits it`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567",
      digits = listOf("1", "2", "3", "4", "5", "")
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.SubmitVerificationCodeError.InvalidSessionIdOrVerificationCode("Wrong code")
      )

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.DigitChanged(5, "6"),
      stateEmitter
    )

    coVerify { mockRepository.submitVerificationCode(sessionMetadata.id, "123456") }
    assertThat(emittedStates.first().isSubmittingCode).isTrue()
    assertThat(emittedStates.last().isSubmittingCode).isEqualTo(false)
  }

  @Test
  fun `DigitChanged does not submit until the code is complete`() = runTest {
    val initialState = VerificationCodeState(
      sessionMetadata = createSessionMetadata(),
      e164 = "+15551234567",
      digits = listOf("1", "2", "3", "4", "", "")
    )

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.DigitChanged(4, "5"),
      stateEmitter
    )

    coVerify(exactly = 0) { mockRepository.submitVerificationCode(any(), any()) }
    assertThat(emittedStates.last().isSubmittingCode).isEqualTo(false)
  }

  @Test
  fun `an incorrect code clears the entered digits`() = runTest {
    val initialState = VerificationCodeState(
      sessionMetadata = createSessionMetadata(),
      e164 = "+15551234567",
      digits = listOf("1", "2", "3", "4", "5", "")
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.SubmitVerificationCodeError.InvalidSessionIdOrVerificationCode("Wrong code")
      )

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.DigitChanged(5, "6"),
      stateEmitter
    )

    assertThat(emittedStates.last().digits).isEqualTo(listOf("", "", "", "", "", ""))
    assertThat(emittedStates.last().snackbars.incorrectVerificationCode).isTrue()
  }

  @Test
  fun `DigitChanged with an empty value clears the digit at the index`() = runTest {
    val initialState = VerificationCodeState(digits = listOf("1", "2", "3", "", "", ""))

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.DigitChanged(2, ""),
      stateEmitter
    )

    assertThat(emittedStates.last().digits).isEqualTo(listOf("1", "2", "", "", "", ""))
  }

  @Test
  fun `DigitChanged with an empty value shifts the following digits left`() = runTest {
    val initialState = VerificationCodeState(digits = listOf("1", "2", "3", "4", "5", "6"))

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.DigitChanged(2, ""),
      stateEmitter
    )

    assertThat(emittedStates.last().digits).isEqualTo(listOf("1", "2", "4", "5", "6", ""))
  }

  @Test
  fun `DigitChanged with an empty value on an empty field clears the previous digit`() = runTest {
    val initialState = VerificationCodeState(digits = listOf("1", "2", "", "", "", ""))

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.DigitChanged(2, ""),
      stateEmitter
    )

    assertThat(emittedStates.last().digits).isEqualTo(listOf("1", "", "", "", "", ""))
  }

  // ==================== applyEvent: WrongNumber Tests ====================

  @Test
  fun `WrongNumber navigates to PhoneNumberEntry`() = runTest {
    val initialState = VerificationCodeState()

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.WrongNumber, stateEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PhoneNumberEntry>()
  }

  // ==================== applyEvent: CodeEntered Tests ====================

  @Test
  fun `CodeEntered emits isSubmittingCode true then false`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.SubmitVerificationCodeError.InvalidSessionIdOrVerificationCode("Wrong code")
      )

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456"),
      stateEmitter
    )

    // First emitted state should have isSubmittingCode = true
    assertThat(emittedStates.first().isSubmittingCode).isTrue()
    // Final emitted state should have isSubmittingCode = false
    assertThat(emittedStates.last().isSubmittingCode).isEqualTo(false)
  }

  @Test
  fun `CodeEntered emits ResetState when sessionMetadata is null`() = runTest {
    val initialState = VerificationCodeState(sessionMetadata = null)

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456"),
      stateEmitter
    )

    assertThat(emittedStates.last()).isEqualTo(initialState)
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
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccountWithSession(any(), any(), any()) } returns
      RequestResult.Success(registerResponse to keyMaterial)

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CodeEntered("123456"), stateEmitter)

    assertThat(emittedEvents).hasSize(2)
    assertThat(emittedEvents[0]).isInstanceOf<RegistrationFlowEvent.Registered>()
    assertThat(emittedEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinCreate>()
  }

  @Test
  fun `CodeEntered reregistration with no pending restore option navigates to ArchiveRestoreSelection`() = runTest {
    val sessionMetadata = createSessionMetadata(verified = true)
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    val registerResponse = createRegisterAccountResponse(storageCapable = true, reregistration = true)
    val keyMaterial = mockk<KeyMaterial>(relaxed = true)

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccountWithSession(any(), any(), any()) } returns
      RequestResult.Success(registerResponse to keyMaterial)

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CodeEntered("123456"), stateEmitter)

    assertThat(emittedEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.ArchiveRestoreSelection>()
  }

  @Test
  fun `CodeEntered reregistration after pre-registration restore skips ArchiveRestoreSelection`() = runTest {
    parentState.value = parentState.value.copy(pendingRestoreOption = PendingRestoreOption.LocalBackup)

    val sessionMetadata = createSessionMetadata(verified = true)
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    val registerResponse = createRegisterAccountResponse(storageCapable = true, reregistration = true)
    val keyMaterial = mockk<KeyMaterial>(relaxed = true)

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccountWithSession(any(), any(), any()) } returns
      RequestResult.Success(registerResponse to keyMaterial)

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CodeEntered("123456"), stateEmitter)

    assertThat(emittedEvents[1])
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.PinEntryForSvrRestore>()
  }

  @Test
  fun `CodeEntered with incorrect code returns IncorrectVerificationCode event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.SubmitVerificationCodeError.InvalidSessionIdOrVerificationCode("Wrong code")
      )

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456"),
      stateEmitter
    )

    assertThat(emittedStates.last().snackbars.incorrectVerificationCode).isTrue()
  }

  @Test
  fun `CodeEntered with session not found navigates back to phone number entry`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.SubmitVerificationCodeError.SessionNotFound("Session expired")
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CodeEntered("123456"), stateEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
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
      RequestResult.NonSuccess(
        NetworkController.SubmitVerificationCodeError.SessionAlreadyVerifiedOrNoCodeRequested(verifiedSession)
      )
    coEvery { mockRepository.registerAccountWithSession(any(), any(), any()) } returns
      RequestResult.Success(registerResponse to keyMaterial)

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CodeEntered("123456"), stateEmitter)

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
      RequestResult.NonSuccess(
        NetworkController.SubmitVerificationCodeError.SessionAlreadyVerifiedOrNoCodeRequested(unverifiedSession)
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CodeEntered("123456"), stateEmitter)

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
      RequestResult.NonSuccess(
        NetworkController.SubmitVerificationCodeError.RateLimited(60.seconds, sessionMetadata)
      )

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456"),
      stateEmitter
    )

    assertThat(emittedStates.last().snackbars.rateLimitedRetryAfter).isEqualTo(60.seconds)
  }

  @Test
  fun `CodeEntered with network error from submitVerificationCode returns NetworkError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      RequestResult.RetryableNetworkError(java.io.IOException("Network error"))

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456"),
      stateEmitter
    )

    assertThat(emittedStates.last().snackbars.networkError).isTrue()
  }

  @Test
  fun `CodeEntered with application error from submitVerificationCode returns UnknownError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(
      sessionMetadata = sessionMetadata,
      e164 = "+15551234567"
    )

    coEvery { mockRepository.submitVerificationCode(any(), any()) } returns
      RequestResult.ApplicationError(RuntimeException("Unexpected"))

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456"),
      stateEmitter
    )

    assertThat(emittedStates.last().snackbars.unknownError).isTrue()
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
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccountWithSession(any(), any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.RegisterAccountError.DeviceTransferPossible
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CodeEntered("123456"), stateEmitter)

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
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccountWithSession(any(), any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.RegisterAccountError.RateLimited(30.seconds)
      )

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456"),
      stateEmitter
    )

    assertThat(emittedStates.last().snackbars.rateLimitedRetryAfter).isEqualTo(30.seconds)
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
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccountWithSession(any(), any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.RegisterAccountError.InvalidRequest("Bad request")
      )

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456"),
      stateEmitter
    )

    assertThat(emittedStates.last().snackbars.registrationError).isTrue()
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
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccountWithSession(any(), any(), any()) } returns
      RequestResult.NonSuccess(
        NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect("Wrong password")
      )

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456"),
      stateEmitter
    )

    assertThat(emittedStates.last().snackbars.registrationError).isTrue()
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
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccountWithSession(any(), any(), any()) } returns
      RequestResult.RetryableNetworkError(java.io.IOException("Network error"))

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456"),
      stateEmitter
    )

    assertThat(emittedStates.last().snackbars.networkError).isTrue()
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
      RequestResult.Success(sessionMetadata)
    coEvery { mockRepository.registerAccountWithSession(any(), any(), any()) } returns
      RequestResult.ApplicationError(RuntimeException("Unexpected"))

    viewModel.applyEvent(
      initialState,
      VerificationCodeScreenEvents.CodeEntered("123456"),
      stateEmitter
    )

    assertThat(emittedStates.last().snackbars.unknownError).isTrue()
  }

  // ==================== applyEvent: ResendSms Tests ====================

  @Test
  fun `ResendSms with null sessionMetadata emits ResetState`() = runTest {
    val initialState = VerificationCodeState(sessionMetadata = null)

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms, stateEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
    assertThat(emittedStates.last()).isEqualTo(initialState)
  }

  @Test
  fun `ResendSms with success updates sessionMetadata`() = runTest {
    val sessionMetadata = createSessionMetadata(id = "original-session")
    val updatedSession = createSessionMetadata(id = "updated-session")
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      RequestResult.Success(updatedSession)

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms, stateEmitter)

    assertThat(emittedStates.last().sessionMetadata).isEqualTo(updatedSession)
  }

  @Test
  fun `ResendSms passes registerSmsListener result as smsAutoRetrieveCodeSupported`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.registerSmsListener() } returns true
    coEvery { mockRepository.requestVerificationCode(any(), any(), any()) } returns
      RequestResult.Success(sessionMetadata)

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms, stateEmitter)

    coVerify {
      mockRepository.requestVerificationCode(
        sessionId = sessionMetadata.id,
        smsAutoRetrieveCodeSupported = true,
        transport = NetworkController.VerificationCodeTransport.SMS
      )
    }
  }

  @Test
  fun `ResendSms with rate limit returns RateLimited event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      RequestResult.NonSuccess(
        NetworkController.RequestVerificationCodeError.RateLimited(45.seconds, sessionMetadata)
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms, stateEmitter)

    assertThat(emittedStates.last().snackbars.rateLimitedRetryAfter).isEqualTo(45.seconds)
  }

  @Test
  fun `ResendSms with InvalidRequest returns UnknownError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      RequestResult.NonSuccess(
        NetworkController.RequestVerificationCodeError.InvalidRequest("Bad request")
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms, stateEmitter)

    assertThat(emittedStates.last().snackbars.unknownError).isTrue()
  }

  @Test
  fun `ResendSms with CouldNotFulfillWithRequestedTransport returns appropriate event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      RequestResult.NonSuccess(
        NetworkController.RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport(sessionMetadata)
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms, stateEmitter)

    assertThat(emittedStates.last().snackbars.couldNotRequestCodeWithSelectedTransport).isTrue()
  }

  @Test
  fun `ResendSms with InvalidSessionId navigates back to phone number entry`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      RequestResult.NonSuccess(
        NetworkController.RequestVerificationCodeError.InvalidSessionId("Invalid session")
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms, stateEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `ResendSms with SessionNotFound navigates back to phone number entry`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      RequestResult.NonSuccess(
        NetworkController.RequestVerificationCodeError.SessionNotFound("Session not found")
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms, stateEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `ResendSms with MissingRequestInformationOrAlreadyVerified returns UnableToSendSms event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      RequestResult.NonSuccess(
        NetworkController.RequestVerificationCodeError.MissingRequestInformationOrAlreadyVerified(sessionMetadata)
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms, stateEmitter)

    assertThat(emittedStates.last().snackbars.unableToSendSms).isTrue()
  }

  @Test
  fun `ResendSms with ThirdPartyServiceError returns UnableToSendSms event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      RequestResult.NonSuccess(
        NetworkController.RequestVerificationCodeError.ThirdPartyServiceError(
          NetworkController.ThirdPartyServiceErrorResponse("Provider error", false)
        )
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms, stateEmitter)

    assertThat(emittedStates.last().snackbars.unableToSendSms).isTrue()
  }

  @Test
  fun `ResendSms with network error returns NetworkError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      RequestResult.RetryableNetworkError(java.io.IOException("Network error"))

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms, stateEmitter)

    assertThat(emittedStates.last().snackbars.networkError).isTrue()
  }

  @Test
  fun `ResendSms with application error returns UnknownError event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.SMS)) } returns
      RequestResult.ApplicationError(RuntimeException("Unexpected"))

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.ResendSms, stateEmitter)

    assertThat(emittedStates.last().snackbars.unknownError).isTrue()
  }

  // ==================== applyEvent: CallMe Tests ====================

  @Test
  fun `CallMe with null sessionMetadata emits ResetState`() = runTest {
    val initialState = VerificationCodeState(sessionMetadata = null)

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CallMe, stateEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
    assertThat(emittedStates.last()).isEqualTo(initialState)
  }

  @Test
  fun `CallMe with success updates sessionMetadata`() = runTest {
    val sessionMetadata = createSessionMetadata(id = "original-session")
    val updatedSession = createSessionMetadata(id = "updated-session")
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.VOICE)) } returns
      RequestResult.Success(updatedSession)

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CallMe, stateEmitter)

    assertThat(emittedStates.last().sessionMetadata).isEqualTo(updatedSession)
  }

  @Test
  fun `CallMe with rate limit returns RateLimited event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.VOICE)) } returns
      RequestResult.NonSuccess(
        NetworkController.RequestVerificationCodeError.RateLimited(90.seconds, sessionMetadata)
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CallMe, stateEmitter)

    assertThat(emittedStates.last().snackbars.rateLimitedRetryAfter).isEqualTo(90.seconds)
  }

  @Test
  fun `CallMe with CouldNotFulfillWithRequestedTransport returns appropriate event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.VOICE)) } returns
      RequestResult.NonSuccess(
        NetworkController.RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport(sessionMetadata)
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CallMe, stateEmitter)

    assertThat(emittedStates.last().snackbars.couldNotRequestCodeWithSelectedTransport).isTrue()
  }

  @Test
  fun `CallMe with ThirdPartyServiceError returns UnableToSendSms event`() = runTest {
    val sessionMetadata = createSessionMetadata()
    val initialState = VerificationCodeState(sessionMetadata = sessionMetadata)

    coEvery { mockRepository.requestVerificationCode(any(), any(), eq(NetworkController.VerificationCodeTransport.VOICE)) } returns
      RequestResult.NonSuccess(
        NetworkController.RequestVerificationCodeError.ThirdPartyServiceError(
          NetworkController.ThirdPartyServiceErrorResponse("Voice provider error", true)
        )
      )

    viewModel.applyEvent(initialState, VerificationCodeScreenEvents.CallMe, stateEmitter)

    assertThat(emittedStates.last().snackbars.unableToSendSms).isTrue()
  }

  // ==================== applyEvent: Foregrounded Tests ====================

  @Test
  fun `Foregrounded emits ResetState when in-progress data is older than the timeout`() = runTest {
    val now = 100.minutes.inWholeMilliseconds
    coEvery { mockRepository.getInProgressRegistrationDataLastUpdated() } returns now - 16.minutes.inWholeMilliseconds

    val vm = VerificationCodeViewModel(mockRepository, parentState, parentEventEmitter, clock = { now })
    vm.applyEvent(VerificationCodeState(), VerificationCodeScreenEvents.Foregrounded, stateEmitter)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.ResetState)
  }

  @Test
  fun `Foregrounded does not emit ResetState when in-progress data is within the timeout`() = runTest {
    val now = 100.minutes.inWholeMilliseconds
    coEvery { mockRepository.getInProgressRegistrationDataLastUpdated() } returns now - 14.minutes.inWholeMilliseconds

    val vm = VerificationCodeViewModel(mockRepository, parentState, parentEventEmitter, clock = { now })
    vm.applyEvent(VerificationCodeState(), VerificationCodeScreenEvents.Foregrounded, stateEmitter)

    assertThat(emittedEvents).hasSize(0)
  }

  @Test
  fun `Foregrounded does not emit ResetState when there is no in-progress data`() = runTest {
    coEvery { mockRepository.getInProgressRegistrationDataLastUpdated() } returns null

    viewModel.applyEvent(VerificationCodeState(), VerificationCodeScreenEvents.Foregrounded, stateEmitter)

    assertThat(emittedEvents).hasSize(0)
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
    storageCapable: Boolean = false,
    reregistration: Boolean = false
  ) = NetworkController.RegisterAccountResponse(
    aci = aci,
    pni = pni,
    e164 = e164,
    usernameHash = null,
    usernameLinkHandle = null,
    storageCapable = storageCapable,
    entitlements = null,
    reregistration = reregistration
  )
}
