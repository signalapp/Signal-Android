/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.passkeyverification

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.screens.twofactorselection.WebAuthnParameters

@OptIn(ExperimentalCoroutinesApi::class)
class PasskeyVerificationViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private val emittedParentEvents = mutableListOf<RegistrationFlowEvent>()

  private val parameters = WebAuthnParameters(challenge = byteArrayOf(1, 2, 3), timeoutSeconds = 60, allowedCredentialIds = listOf(byteArrayOf(4, 5, 6)))

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state shows no error`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(PasskeyVerificationState())
  }

  @Test
  fun `ContinueClicked starts the passkey flow with the parameters it was constructed with`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PasskeyVerificationScreenEvents.ContinueClicked)

    assertThat(actions).containsExactly(PasskeyVerificationAction.AuthenticateWithPasskey(parameters))
    assertThat(emittedParentEvents).isEmpty()
  }

  @Test
  fun `CancelClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PasskeyVerificationScreenEvents.CancelClicked)

    assertThat(emittedParentEvents).containsExactly(RegistrationFlowEvent.NavigateBack)
    assertThat(actions).isEmpty()
  }

  @Test
  fun `PasskeyCeremonyFailed puts the error dialog up`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PasskeyVerificationScreenEvents.PasskeyCeremonyFailed)

    assertThat(viewModel.state.value.showPasskeyError).isTrue()
    assertThat(emittedParentEvents).isEmpty()
  }

  /** The user stays on this screen after acknowledging, so they can try again. */
  @Test
  fun `PasskeyErrorDismissed takes the error dialog back down`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.onEvent(PasskeyVerificationScreenEvents.PasskeyCeremonyFailed)

    viewModel.onEvent(PasskeyVerificationScreenEvents.PasskeyErrorDismissed)

    assertThat(viewModel.state.value.showPasskeyError).isFalse()
    assertThat(emittedParentEvents).isEmpty()
  }

  private fun createViewModel(): PasskeyVerificationViewModel {
    return PasskeyVerificationViewModel(parameters, parentEventEmitter = { emittedParentEvents.add(it) })
  }

  private fun TestScope.collectActions(actions: Flow<PasskeyVerificationAction>): List<PasskeyVerificationAction> {
    val collected = mutableListOf<PasskeyVerificationAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
