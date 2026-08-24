/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import assertk.assertThat
import assertk.assertions.contains
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
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.authenticatorcodeentry.AuthenticatorCodeEntryAction
import org.signal.appsettings.authenticatorcodeentry.AuthenticatorCodeEntryEvent
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticatorCodeEntryViewModelTest {

  companion object {
    private const val FULL_CODE = "123456"
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    AuthenticatorAppStore.hasAuthenticatorApp = false
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    AuthenticatorAppStore.hasAuthenticatorApp = false
  }

  @Test
  fun `non-digits are dropped and the code is capped at six digits`() = runTest(testDispatcher) {
    val viewModel = AuthenticatorCodeEntryViewModel()

    viewModel.onEvent(AuthenticatorCodeEntryEvent.CodeChanged("12a34 5678"))

    assertThat(viewModel.state.value.code).isEqualTo(FULL_CODE)
  }

  @Test
  fun `a partial code can't be submitted`() = runTest(testDispatcher) {
    val viewModel = AuthenticatorCodeEntryViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AuthenticatorCodeEntryEvent.CodeChanged("123"))

    assertThat(viewModel.state.value.canSubmit).isFalse()

    viewModel.onEvent(AuthenticatorCodeEntryEvent.DoneClicked)

    assertThat(actions).isEmpty()
    assertThat(AuthenticatorAppStore.hasAuthenticatorApp).isFalse()
  }

  @Test
  fun `a full code is accepted and sends the user back to account settings`() = runTest(testDispatcher) {
    val viewModel = AuthenticatorCodeEntryViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AuthenticatorCodeEntryEvent.CodeChanged(FULL_CODE))
    viewModel.onEvent(AuthenticatorCodeEntryEvent.DoneClicked)

    assertThat(AuthenticatorAppStore.hasAuthenticatorApp).isTrue()
    assertThat(actions).contains(AuthenticatorCodeEntryAction.ShowAuthenticatorAppAdded)
    assertThat(actions.last()).isEqualTo(AuthenticatorCodeEntryAction.NavigateToAccountSettings)
  }

  @Test
  fun `NavigateBackClicked leaves the screen`() = runTest(testDispatcher) {
    val viewModel = AuthenticatorCodeEntryViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AuthenticatorCodeEntryEvent.NavigateBackClicked)

    assertThat(actions.last()).isEqualTo(AuthenticatorCodeEntryAction.NavigateBack)
  }

  private fun TestScope.collectActions(actions: Flow<AuthenticatorCodeEntryAction>): List<AuthenticatorCodeEntryAction> {
    val collected = mutableListOf<AuthenticatorCodeEntryAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
