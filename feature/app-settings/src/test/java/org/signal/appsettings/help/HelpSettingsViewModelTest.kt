/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
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
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute

@OptIn(ExperimentalCoroutinesApi::class)
class HelpSettingsViewModelTest {

  companion object {
    private const val VERSION_NAME = "8.33.0"
  }

  private val testDispatcher = UnconfinedTestDispatcher()
  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state contains the version name`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(HelpSettingsState(versionName = VERSION_NAME))
  }

  @Test
  fun `NavigateBackClicked navigates back`() = assertEventNavigates(HelpSettingsEvent.NavigateBackClicked, AppSettingsNavigationEvent.NavigateBack())

  @Test
  fun `ContactUsClicked opens the contact form`() = assertEventNavigates(
    HelpSettingsEvent.ContactUsClicked,
    AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.HelpRoute.Help())
  )

  @Test
  fun `DebugLogClicked opens the debug log`() = assertEventNavigates(
    HelpSettingsEvent.DebugLogClicked,
    AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.HelpRoute.DebugLog)
  )

  @Test
  fun `LicensesClicked opens the licenses`() = assertEventNavigates(
    HelpSettingsEvent.LicensesClicked,
    AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.HelpRoute.Licenses)
  )

  @Test
  fun `SupportCenterClicked opens the support center`() = assertEventEmits(HelpSettingsEvent.SupportCenterClicked, HelpSettingsAction.OpenSupportCenter)

  @Test
  fun `TermsAndPrivacyPolicyClicked opens the terms and privacy policy`() = assertEventEmits(HelpSettingsEvent.TermsAndPrivacyPolicyClicked, HelpSettingsAction.OpenTermsAndPrivacyPolicy)

  @Test
  fun `VersionLongClicked copies the version name and says so until the snackbar is dismissed`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(HelpSettingsEvent.VersionLongClicked)

    assertThat(actions).containsExactly(HelpSettingsAction.CopyVersionToClipboard(VERSION_NAME))
    assertThat(viewModel.state.value.snackbar).isEqualTo(HelpSettingsState.Snackbar.VersionCopied)

    viewModel.onEvent(HelpSettingsEvent.SnackbarDismissed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(HelpSettingsState.Snackbar.None)
  }

  private fun assertEventEmits(event: HelpSettingsEvent, expected: HelpSettingsAction) = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(event)

    assertThat(actions).containsExactly(expected)
    assertThat(navigationEvents).isEmpty()
  }

  private fun assertEventNavigates(event: HelpSettingsEvent, expected: AppSettingsNavigationEvent) = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(event)

    assertThat(navigationEvents).containsExactly(expected)
    assertThat(actions).isEmpty()
  }

  private fun createViewModel(): HelpSettingsViewModel = HelpSettingsViewModel(versionName = VERSION_NAME, parentEventEmitter = { navigationEvents += it })

  private fun TestScope.collectActions(actions: Flow<HelpSettingsAction>): List<HelpSettingsAction> {
    val collected = mutableListOf<HelpSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
