/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.signal.registration.screens.aepentry.AepInput
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SignalLoginVerifyViewModelTest {

  companion object {
    private val ACI_VALUE = ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91"))
    private const val RAW_ACCOUNT_ID = "a6b284822e3283d07f2391360a4c2b91"
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  private val aep = AccountEntropyPool.generate()

  private lateinit var viewModel: SignalLoginVerifyViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    viewModel = SignalLoginVerifyViewModel(expectedAci = ACI_VALUE, expectedAep = aep)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `the screen starts in verify mode`() {
    assertThat(viewModel.state.value.mode).isEqualTo(SignalLoginCredentialEntryState.Mode.Verify)
  }

  @Test
  fun `NextClicked with the account's login verifies it`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginVerifyActions>()

    viewModel.applyEvent(matchingState(), SignalLoginCredentialEntryScreenEvents.NextClicked, { actions.add(it) }) {}

    assertThat(actions).containsExactly(SignalLoginVerifyActions.Verified)
  }

  @Test
  fun `NextClicked with a mistyped recovery key flags both fields and stays put`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginVerifyActions>()
    var emittedState: SignalLoginCredentialEntryState? = null
    val state = matchingState().copy(recoveryKey = AepInput.from(AccountEntropyPool.generate().displayValue))

    viewModel.applyEvent(state, SignalLoginCredentialEntryScreenEvents.NextClicked, { actions.add(it) }) { emittedState = it }

    assertThat(emittedState?.areCredentialsIncorrect).isEqualTo(true)
    assertThat(actions).isEmpty()
  }

  @Test
  fun `NextClicked with a mistyped account ID flags both fields and stays put`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginVerifyActions>()
    var emittedState: SignalLoginCredentialEntryState? = null
    val state = matchingState().copy(accountId = "a6b284822e3283d07f2391360a4c2b92")

    viewModel.applyEvent(state, SignalLoginCredentialEntryScreenEvents.NextClicked, { actions.add(it) }) { emittedState = it }

    assertThat(emittedState?.areCredentialsIncorrect).isEqualTo(true)
    assertThat(actions).isEmpty()
  }

  @Test
  fun `a password manager credential that matches verifies straight away`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginVerifyActions>()

    viewModel.applyEvent(
      SignalLoginCredentialEntryState(mode = SignalLoginCredentialEntryState.Mode.Verify),
      SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected(accountId = RAW_ACCOUNT_ID, recoveryKey = aep.displayValue),
      { actions.add(it) }
    ) {}

    assertThat(actions).containsExactly(SignalLoginVerifyActions.Verified)
  }

  @Test
  fun `a password manager credential that doesn't match flags both fields`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginVerifyActions>()
    var emittedState: SignalLoginCredentialEntryState? = null

    viewModel.applyEvent(
      SignalLoginCredentialEntryState(mode = SignalLoginCredentialEntryState.Mode.Verify),
      SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected(accountId = RAW_ACCOUNT_ID, recoveryKey = AccountEntropyPool.generate().displayValue),
      { actions.add(it) }
    ) { emittedState = it }

    assertThat(emittedState?.areCredentialsIncorrect).isEqualTo(true)
    assertThat(actions).isEmpty()
  }

  @Test
  fun `BackClicked closes the screen`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginVerifyActions>()

    viewModel.applyEvent(matchingState(), SignalLoginCredentialEntryScreenEvents.BackClicked, { actions.add(it) }) {}

    assertThat(actions).containsExactly(SignalLoginVerifyActions.Close)
  }

  @Test
  fun `NeedHelpClicked opens the help article`() = runTest(testDispatcher) {
    val actions = mutableListOf<SignalLoginVerifyActions>()

    viewModel.applyEvent(matchingState(), SignalLoginCredentialEntryScreenEvents.NeedHelpClicked, { actions.add(it) }) {}

    assertThat(actions).containsExactly(SignalLoginVerifyActions.OpenNeedHelpArticle)
  }

  @Test
  fun `AccountIdChanged clears the mismatch flag`() = runTest(testDispatcher) {
    var emittedState: SignalLoginCredentialEntryState? = null
    val state = matchingState().copy(areCredentialsIncorrect = true)

    viewModel.applyEvent(state, SignalLoginCredentialEntryScreenEvents.AccountIdChanged(RAW_ACCOUNT_ID), {}) { emittedState = it }

    assertThat(emittedState?.areCredentialsIncorrect).isEqualTo(false)
  }

  private fun matchingState(): SignalLoginCredentialEntryState {
    return SignalLoginCredentialEntryState(
      mode = SignalLoginCredentialEntryState.Mode.Verify,
      accountId = RAW_ACCOUNT_ID,
      recoveryKey = AepInput.from(aep.displayValue)
    )
  }
}
