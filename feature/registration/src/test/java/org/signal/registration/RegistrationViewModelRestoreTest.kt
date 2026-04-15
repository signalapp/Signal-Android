/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelRestoreTest {

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

    viewModel.onEvent(RegistrationFlowEvent.Registered(org.signal.core.models.AccountEntropyPool.generate()))
    advanceUntilIdle()

    coVerify(exactly = 0) { mockRepository.saveFlowState(any()) }
  }

  @Test
  fun `onEvent MasterKeyRestoredFromSvr does not save flow state`() = runTest(testDispatcher) {
    coEvery { mockRepository.restoreFlowState() } returns null
    coEvery { mockRepository.getPreExistingRegistrationData() } returns null

    val viewModel = RegistrationViewModel(mockRepository, SavedStateHandle())
    advanceUntilIdle()

    viewModel.onEvent(RegistrationFlowEvent.MasterKeyRestoredFromSvr(org.signal.core.models.MasterKey(ByteArray(32))))
    advanceUntilIdle()

    coVerify(exactly = 0) { mockRepository.saveFlowState(any()) }
  }

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
