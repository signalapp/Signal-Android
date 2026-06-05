/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var mockRepository: RegistrationRepository

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ==================== Restore Flow State Tests ====================

  @Test
  fun `no saved state starts fresh and loads preExistingRegistrationData`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val state = viewModel.state.value
    assertThat(state.backStack).isEqualTo(listOf(RegistrationRoute.Welcome))
    assertThat(state.sessionMetadata).isNull()
  }

  @Test
  fun `restore with valid session proceeds normally with updated session metadata`() = runTest(testDispatcher) {
    val savedSession = createSessionMetadata("session-1")
    val freshSession = createSessionMetadata("session-1").copy(nextSms = 9999L)

    val savedState = RegistrationFlowState(
      backStack = listOf(
        RegistrationRoute.Welcome,
        RegistrationRoute.Permissions(nextRoute = RegistrationRoute.PhoneNumberEntry),
        RegistrationRoute.PhoneNumberEntry,
        RegistrationRoute.VerificationCodeEntry
      ),
      sessionMetadata = savedSession,
      sessionE164 = "+15551234567"
    )

    coEvery { mockRepository.restoreFlowState() } returns savedState
    coEvery { mockRepository.validateSession("session-1") } returns freshSession

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val state = viewModel.state.value
    assertThat(state.backStack).isEqualTo(savedState.backStack)
    assertThat(state.sessionMetadata).isEqualTo(freshSession)
    assertThat(state.sessionE164).isEqualTo("+15551234567")
  }

  @Test
  fun `restore with expired session and not registered resets to PhoneNumberEntry with e164 preserved`() = runTest(testDispatcher) {
    val savedSession = createSessionMetadata("session-expired")

    val savedState = RegistrationFlowState(
      backStack = listOf(
        RegistrationRoute.Welcome,
        RegistrationRoute.Permissions(nextRoute = RegistrationRoute.PhoneNumberEntry),
        RegistrationRoute.PhoneNumberEntry,
        RegistrationRoute.VerificationCodeEntry
      ),
      sessionMetadata = savedSession,
      sessionE164 = "+15559876543"
    )

    coEvery { mockRepository.restoreFlowState() } returns savedState
    coEvery { mockRepository.validateSession("session-expired") } returns null
    coEvery { mockRepository.isRegistered() } returns false

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val state = viewModel.state.value
    assertThat(state.backStack).isEqualTo(
      listOf(
        RegistrationRoute.Welcome,
        RegistrationRoute.Permissions(nextRoute = RegistrationRoute.PhoneNumberEntry),
        RegistrationRoute.PhoneNumberEntry
      )
    )
    assertThat(state.sessionMetadata).isNull()
    assertThat(state.sessionE164).isEqualTo("+15559876543")
  }

  @Test
  fun `restore with expired session and already registered proceeds with null session`() = runTest(testDispatcher) {
    val savedSession = createSessionMetadata("session-expired-2")

    val savedState = RegistrationFlowState(
      backStack = listOf(
        RegistrationRoute.Welcome,
        RegistrationRoute.PinCreate
      ),
      sessionMetadata = savedSession,
      sessionE164 = "+15551234567"
    )

    coEvery { mockRepository.restoreFlowState() } returns savedState
    coEvery { mockRepository.validateSession("session-expired-2") } returns null
    coEvery { mockRepository.isRegistered() } returns true

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val state = viewModel.state.value
    assertThat(state.backStack).isEqualTo(listOf(RegistrationRoute.Welcome, RegistrationRoute.PinCreate))
    assertThat(state.sessionMetadata).isNull()
    assertThat(state.sessionE164).isEqualTo("+15551234567")
  }

  @Test
  fun `restore with no session skips validation`() = runTest(testDispatcher) {
    val savedState = RegistrationFlowState(
      backStack = listOf(
        RegistrationRoute.Welcome,
        RegistrationRoute.PinCreate,
        RegistrationRoute.ArchiveRestoreSelection.forManualRestore()
      ),
      sessionMetadata = null,
      sessionE164 = "+15551234567",
      doNotAttemptRecoveryPassword = true
    )

    coEvery { mockRepository.restoreFlowState() } returns savedState

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val state = viewModel.state.value
    assertThat(state.backStack).isEqualTo(savedState.backStack)
    assertThat(state.sessionMetadata).isNull()
    assertThat(state.doNotAttemptRecoveryPassword).isEqualTo(true)

    coVerify(exactly = 0) { mockRepository.validateSession(any()) }
  }

  @Test
  fun `init clears isRestoringNavigationState after restore completes`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    assertThat(viewModel.state.value.isRestoringNavigationState).isEqualTo(false)
  }

  // ==================== Persistence Side-Effect Tests ====================

  @Test
  fun `onEvent ResetState clears flow state`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    viewModel.onEvent(RegistrationFlowEvent.ResetState)
    advanceUntilIdle()

    coVerify { mockRepository.clearFlowState() }
  }

  @Test
  fun `onEvent NavigateToScreen FullyComplete clears flow state`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    viewModel.onEvent(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.FullyComplete))
    advanceUntilIdle()

    coVerify { mockRepository.clearFlowState() }
  }

  @Test
  fun `onEvent NavigateToScreen saves flow state`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    viewModel.onEvent(RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.PhoneNumberEntry))
    advanceUntilIdle()

    coVerify { mockRepository.saveFlowState(any()) }
  }

  @Test
  fun `onEvent Registered does not save flow state`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    viewModel.onEvent(RegistrationFlowEvent.Registered(AccountEntropyPool.generate()))
    advanceUntilIdle()

    coVerify(exactly = 0) { mockRepository.saveFlowState(any()) }
  }

  @Test
  fun `onEvent MasterKeyRestoredFromSvr does not save flow state`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    viewModel.onEvent(RegistrationFlowEvent.MasterKeyRestoredFromSvr(MasterKey(ByteArray(32))))
    advanceUntilIdle()

    coVerify(exactly = 0) { mockRepository.saveFlowState(any()) }
  }

  @Test
  fun `onEvent RegistrationComplete commits final data and clears flow state`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    viewModel.onEvent(RegistrationFlowEvent.RegistrationComplete)
    advanceUntilIdle()

    coVerify { mockRepository.commitFinalRegistrationData() }
    coVerify { mockRepository.clearFlowState() }
  }

  // ==================== applyEvent Tests (Navigation & State Reducers) ====================

  @Test
  fun `applyEvent NavigateToScreen appends to backStack`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val initialState = RegistrationFlowState(backStack = listOf(RegistrationRoute.Welcome))

    val result = viewModel.applyEvent(
      initialState,
      RegistrationFlowEvent.NavigateToScreen(RegistrationRoute.PhoneNumberEntry)
    )

    assertThat(result.backStack).isEqualTo(
      listOf(RegistrationRoute.Welcome, RegistrationRoute.PhoneNumberEntry)
    )
  }

  @Test
  fun `applyEvent NavigateBack pops last from backStack`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val initialState = RegistrationFlowState(
      backStack = listOf(RegistrationRoute.Welcome, RegistrationRoute.PhoneNumberEntry)
    )

    val result = viewModel.applyEvent(initialState, RegistrationFlowEvent.NavigateBack)

    assertThat(result.backStack).isEqualTo(listOf(RegistrationRoute.Welcome))
  }

  @Test
  fun `applyEvent ResetState returns default state`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val populatedState = RegistrationFlowState(
      backStack = listOf(RegistrationRoute.Welcome, RegistrationRoute.PhoneNumberEntry),
      sessionMetadata = createSessionMetadata(),
      sessionE164 = "+15551234567",
      doNotAttemptRecoveryPassword = true
    )

    val result = viewModel.applyEvent(populatedState, RegistrationFlowEvent.ResetState)

    assertThat(result.backStack).isEqualTo(listOf(RegistrationRoute.Welcome))
    assertThat(result.sessionMetadata).isNull()
    assertThat(result.sessionE164).isNull()
    assertThat(result.doNotAttemptRecoveryPassword).isEqualTo(false)
    assertThat(result.isRestoringNavigationState).isEqualTo(false)
  }

  @Test
  fun `applyEvent SessionUpdated updates sessionMetadata`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val newSession = createSessionMetadata("new-session")

    val result = viewModel.applyEvent(
      RegistrationFlowState(),
      RegistrationFlowEvent.SessionUpdated(newSession)
    )

    assertThat(result.sessionMetadata).isEqualTo(newSession)
  }

  @Test
  fun `applyEvent E164Chosen updates sessionE164`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val result = viewModel.applyEvent(
      RegistrationFlowState(),
      RegistrationFlowEvent.E164Chosen("+15551234567")
    )

    assertThat(result.sessionE164).isEqualTo("+15551234567")
  }

  @Test
  fun `applyEvent Registered updates accountEntropyPool`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val aep = AccountEntropyPool.generate()

    val result = viewModel.applyEvent(
      RegistrationFlowState(),
      RegistrationFlowEvent.Registered(aep)
    )

    assertThat(result.accountEntropyPool).isEqualTo(aep)
  }

  @Test
  fun `applyEvent MasterKeyRestoredFromSvr updates temporaryMasterKey`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val masterKey = MasterKey(ByteArray(32))

    val result = viewModel.applyEvent(
      RegistrationFlowState(),
      RegistrationFlowEvent.MasterKeyRestoredFromSvr(masterKey)
    )

    assertThat(result.temporaryMasterKey).isEqualTo(masterKey)
  }

  @Test
  fun `applyEvent RecoveryPasswordInvalid sets doNotAttemptRecoveryPassword to true`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val result = viewModel.applyEvent(
      RegistrationFlowState(),
      RegistrationFlowEvent.RecoveryPasswordInvalid
    )

    assertThat(result.doNotAttemptRecoveryPassword).isTrue()
  }

  @Test
  fun `applyEvent PendingRestoreOptionSelected updates pendingRestoreOption`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val result = viewModel.applyEvent(
      RegistrationFlowState(),
      RegistrationFlowEvent.PendingRestoreOptionSelected(PendingRestoreOption.RemoteBackup)
    )

    assertThat(result.pendingRestoreOption).isEqualTo(PendingRestoreOption.RemoteBackup)
  }

  @Test
  fun `applyEvent UserSuppliedAepSubmitted updates unverifiedRestoredAep`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val aep = AccountEntropyPool.generate()

    val result = viewModel.applyEvent(
      RegistrationFlowState(),
      RegistrationFlowEvent.UserSuppliedAepSubmitted(aep)
    )

    assertThat(result.unverifiedRestoredAep).isEqualTo(aep)
  }

  @Test
  fun `applyEvent UserSuppliedAepVerified saves and updates accountEntropyPool`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val aep = AccountEntropyPool.generate()

    val result = viewModel.applyEvent(
      RegistrationFlowState(),
      RegistrationFlowEvent.UserSuppliedAepVerified(aep)
    )

    assertThat(result.accountEntropyPool).isEqualTo(aep)
    coVerify { mockRepository.saveVerifiedUserSuppliedAep(aep) }
  }

  @Test
  fun `applyEvent RegistrationComplete commits data and navigates to FullyComplete`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val initialState = RegistrationFlowState(backStack = listOf(RegistrationRoute.Welcome))

    val result = viewModel.applyEvent(initialState, RegistrationFlowEvent.RegistrationComplete)

    assertThat(result.backStack.last()).isEqualTo(RegistrationRoute.FullyComplete)
    coVerify { mockRepository.commitFinalRegistrationData() }
  }

  // ==================== getRequiredPermissions Tests ====================

  @Test
  fun `getRequiredPermissions always includes contacts and phone state`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    val permissions = viewModel.getRequiredPermissions()

    assertThat(permissions.contains(android.Manifest.permission.READ_CONTACTS)).isTrue()
    assertThat(permissions.contains(android.Manifest.permission.WRITE_CONTACTS)).isTrue()
    assertThat(permissions.contains(android.Manifest.permission.READ_PHONE_STATE)).isTrue()
  }

  // ==================== preExistingRegistrationData Load Test ====================

  @Test
  fun `no saved state loads preExistingRegistrationData when present`() = runTest(testDispatcher) {
    val preExisting = mockk<PreExistingRegistrationData>(relaxed = true)
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns preExisting

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    assertThat(viewModel.state.value.preExistingRegistrationData).isEqualTo(preExisting)
  }

  // ==================== resultBus Tests ====================

  @Test
  fun `resultBus is initialized`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    assertThat(viewModel.resultBus).isNotNull()
  }

  // ==================== Initial State Tests ====================

  @Test
  fun `initial state has isRestoringNavigationState true before init completes`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())

    assertThat(viewModel.state.value.isRestoringNavigationState).isTrue()

    advanceUntilIdle()
  }

  // ==================== Helpers ====================

  private fun createSessionMetadata(id: String = "test-session"): NetworkController.SessionMetadata {
    return NetworkController.SessionMetadata(
      id = id,
      nextSms = 1000L,
      nextCall = 2000L,
      nextVerificationAttempt = 3000L,
      allowedToRequestCode = true,
      requestedInformation = emptyList(),
      verified = false
    )
  }
}
