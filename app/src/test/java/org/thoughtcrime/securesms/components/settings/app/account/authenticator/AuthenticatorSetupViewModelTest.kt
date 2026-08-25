/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import android.app.Application
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.authenticatorsetup.AuthenticatorSetupAction
import org.signal.appsettings.authenticatorsetup.AuthenticatorSetupEvent
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AuthenticatorSetupViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `the setup key is available as soon as the screen opens`() = runTest(testDispatcher) {
    val viewModel = AuthenticatorSetupViewModel()

    assertThat(viewModel.state.value.setupKey).isEqualTo(AuthenticatorAppStore.MOCK_SETUP_KEY)
  }

  @Test
  fun `OpenAuthenticatorAppClicked hands off a link carrying the setup key`() = runTest(testDispatcher) {
    val viewModel = AuthenticatorSetupViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AuthenticatorSetupEvent.OpenAuthenticatorAppClicked)

    val action = actions.last()
    assertThat(action).isInstanceOf(AuthenticatorSetupAction.LaunchAuthenticatorApp::class)
    assertThat((action as AuthenticatorSetupAction.LaunchAuthenticatorApp).uri).contains(AuthenticatorAppStore.MOCK_SETUP_KEY)
  }

  @Test
  fun `CopyKeyClicked copies the key and tells the user`() = runTest(testDispatcher) {
    val viewModel = AuthenticatorSetupViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AuthenticatorSetupEvent.CopyKeyClicked)

    assertThat(actions).contains(AuthenticatorSetupAction.CopyKeyToClipboard(AuthenticatorAppStore.MOCK_SETUP_KEY))
    assertThat(actions.last()).isEqualTo(AuthenticatorSetupAction.ShowKeyCopied)
  }

  @Test
  fun `ContinueClicked moves on to code entry`() = runTest(testDispatcher) {
    val viewModel = AuthenticatorSetupViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AuthenticatorSetupEvent.ContinueClicked)

    assertThat(actions.last()).isEqualTo(AuthenticatorSetupAction.NavigateToCodeEntry)
  }

  @Test
  fun `NoAuthenticatorAppFound reports the failure`() = runTest(testDispatcher) {
    val viewModel = AuthenticatorSetupViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AuthenticatorSetupEvent.NoAuthenticatorAppFound)

    assertThat(actions.last()).isEqualTo(AuthenticatorSetupAction.ShowNoAuthenticatorAppFound)
  }

  private fun TestScope.collectActions(actions: Flow<AuthenticatorSetupAction>): List<AuthenticatorSetupAction> {
    val collected = mutableListOf<AuthenticatorSetupAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
