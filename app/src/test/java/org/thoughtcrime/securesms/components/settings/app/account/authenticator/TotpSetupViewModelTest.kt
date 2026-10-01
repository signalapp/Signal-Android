/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import org.signal.appsettings.totpsetup.TotpSetupAction
import org.signal.appsettings.totpsetup.TotpSetupEvent
import org.signal.appsettings.totpsetup.TotpSetupState.Dialog
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class TotpSetupViewModelTest {

  companion object {
    private const val ACCOUNT_NAME = "2026-09-15"
    private const val SETUP_URI = "otpauth://totp/Signal:%2B15551234567?secret=MZXW6YTBOI"
    private const val DISPLAY_KEY = "MZXW 6YTB OI"
    private const val CLIPBOARD_KEY = "MZXW6YTBOI"

    private val SETUP_SUCCESS = TotpRepository.BeginSetupResult.Success(
      setupUri = SETUP_URI,
      displayKey = DISPLAY_KEY,
      clipboardKey = CLIPBOARD_KEY
    )
  }

  private val testDispatcher = UnconfinedTestDispatcher()
  private val repository: TotpRepository = mockk(relaxed = true)

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { repository.getMaxApps() } returns 2
    coEvery { repository.beginSetup(any()) } returns SETUP_SUCCESS
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `the screen asks for a key as soon as it opens and shows it grouped`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.setupKey).isEqualTo(DISPLAY_KEY)
    assertThat(viewModel.state.value.loading).isFalse()
    assertThat(viewModel.state.value.canContinue).isTrue()
  }

  @Test
  fun `OpenTotpAppClicked hands off the setup link rather than the displayed key`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TotpSetupEvent.OpenTotpAppClicked)

    assertThat(actions.last()).isEqualTo(TotpSetupAction.LaunchTotpApp(SETUP_URI))
  }

  @Test
  fun `CopyKeyClicked copies the unbroken key rather than the grouped one`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TotpSetupEvent.CopyKeyClicked)

    assertThat(actions.first()).isEqualTo(TotpSetupAction.CopyKeyToClipboard(CLIPBOARD_KEY))
    assertThat(actions.last()).isEqualTo(TotpSetupAction.ShowKeyCopied)
  }

  @Test
  fun `ContinueClicked moves on to code entry`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TotpSetupEvent.ContinueClicked)

    assertThat(actions.last()).isEqualTo(TotpSetupAction.NavigateToCodeEntry)
  }

  @Test
  fun `NoTotpAppFound reports the failure`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TotpSetupEvent.NoTotpAppFound)

    assertThat(actions.last()).isEqualTo(TotpSetupAction.ShowNoTotpAppFound)
  }

  @Test
  fun `backing out leaves the screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TotpSetupEvent.NavigateBackClicked)

    assertThat(actions.last()).isEqualTo(TotpSetupAction.NavigateBack)
  }

  @Test
  fun `an account at its limit is told so rather than shown an empty key`() = runTest(testDispatcher) {
    coEvery { repository.beginSetup(any()) } returns TotpRepository.BeginSetupResult.TooManyApps

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.MaxAppsReached(2))
    assertThat(viewModel.state.value.canContinue).isFalse()
  }

  @Test
  fun `a network failure is reported rather than left spinning`() = runTest(testDispatcher) {
    coEvery { repository.beginSetup(any()) } returns TotpRepository.BeginSetupResult.NetworkFailure

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.NetworkFailure)
    assertThat(viewModel.state.value.loading).isFalse()
  }

  @Test
  fun `dismissing a failure dialog leaves the screen, since there's nothing to retry here`() = runTest(testDispatcher) {
    coEvery { repository.beginSetup(any()) } returns TotpRepository.BeginSetupResult.NetworkFailure

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TotpSetupEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions.last()).isEqualTo(TotpSetupAction.NavigateBack)
  }

  @Test
  fun `accountNameFor - names the app after the day it was set up, without the time of day`() {
    val time = LocalDateTime.of(2026, 9, 15, 13, 45, 30)

    assertThat(TotpSetupViewModel.accountNameFor(time)).isEqualTo("2026-09-15")
  }

  @Test
  fun `accountNameFor - pads single-digit months and days so the names sort`() {
    val time = LocalDateTime.of(2026, 1, 2, 0, 0)

    assertThat(TotpSetupViewModel.accountNameFor(time)).isEqualTo("2026-01-02")
  }

  private fun createViewModel() = TotpSetupViewModel(repository = repository, accountName = ACCOUNT_NAME)

  private fun TestScope.collectActions(actions: Flow<TotpSetupAction>): List<TotpSetupAction> {
    val collected = mutableListOf<TotpSetupAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
