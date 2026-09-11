/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import io.mockk.coEvery
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
import org.signal.appsettings.totpcodeentry.TotpCodeEntryAction
import org.signal.appsettings.totpcodeentry.TotpCodeEntryEvent
import org.signal.appsettings.totpcodeentry.TotpCodeEntryState.Error
import org.signal.uicomponents.codeentryfield.CodeEntryFieldEvents
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class TotpCodeEntryViewModelTest {

  companion object {
    private const val FULL_CODE = "123456"
    private const val APP_ID = 3L
  }

  private val testDispatcher = UnconfinedTestDispatcher()
  private val repository: TotpRepository = mockk(relaxed = true)

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    coEvery { repository.confirmPendingApp(any()) } returns TotpRepository.ConfirmResult.Success(APP_ID)
    coEvery { repository.removeTotpApp(any()) } returns TotpRepository.UpdateResult.Success
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `a partial code can't be submitted`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    enterCode(viewModel, "123")

    assertThat(viewModel.state.value.canSubmit).isFalse()

    viewModel.onEvent(TotpCodeEntryEvent.NextClicked)

    assertThat(actions).isEmpty()
  }

  @Test
  fun `a confirmed code sends the user on to name the app the service just created`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    submit(viewModel)

    assertThat(actions.last()).isEqualTo(TotpCodeEntryAction.NavigateToNaming(APP_ID))
  }

  @Test
  fun `a rejected code is reported and the user stays put to try again`() = runTest(testDispatcher) {
    coEvery { repository.confirmPendingApp(any()) } returns TotpRepository.ConfirmResult.IncorrectCode

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    submit(viewModel)

    assertThat(viewModel.state.value.error).isEqualTo(Error.IncorrectCode)
    assertThat(viewModel.state.value.submitting).isFalse()
    assertThat(actions).isEmpty()
  }

  @Test
  fun `an account that filled up mid-setup goes back to setup, which explains the limit`() = runTest(testDispatcher) {
    coEvery { repository.confirmPendingApp(any()) } returns TotpRepository.ConfirmResult.TooManyApps

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    submit(viewModel)

    assertThat(viewModel.state.value.submitting).isFalse()
    assertThat(actions.last()).isEqualTo(TotpCodeEntryAction.NavigateToSetup)
  }

  @Test
  fun `typing again clears the last error`() = runTest(testDispatcher) {
    coEvery { repository.confirmPendingApp(any()) } returns TotpRepository.ConfirmResult.IncorrectCode

    val viewModel = createViewModel()
    submit(viewModel)

    enterCode(viewModel, "1")

    assertThat(viewModel.state.value.error).isEqualTo(Error.None)
  }

  @Test
  fun `NavigateBackClicked leaves the screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TotpCodeEntryEvent.NavigateBackClicked)

    assertThat(actions.last()).isEqualTo(TotpCodeEntryAction.NavigateBack)
  }

  private fun submit(viewModel: TotpCodeEntryViewModel) {
    enterCode(viewModel, FULL_CODE)
    viewModel.onEvent(TotpCodeEntryEvent.NextClicked)
  }

  private fun enterCode(viewModel: TotpCodeEntryViewModel, code: String) {
    code.forEachIndexed { index, digit ->
      viewModel.onEvent(TotpCodeEntryEvent.CodeEntryEvent(CodeEntryFieldEvents.DigitChanged(index, digit.toString())))
    }
  }

  private fun createViewModel() = TotpCodeEntryViewModel(repository = repository)

  private fun TestScope.collectActions(actions: Flow<TotpCodeEntryAction>): List<TotpCodeEntryAction> {
    val collected = mutableListOf<TotpCodeEntryAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
