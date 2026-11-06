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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.PopUpTo
import org.signal.appsettings.totpcodeentry.TotpCodeEntryEvent
import org.signal.appsettings.totpcodeentry.TotpCodeEntryState.Error
import org.signal.uicomponents.codeentryfield.CodeEntryFieldEvents
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class TotpCodeEntryViewModelTest {

  companion object {
    private const val FULL_CODE = "123456"
    private const val APP_ID = 3L
    private const val CREATED_AT = 1_700_000_000_000L
  }

  private val testDispatcher = UnconfinedTestDispatcher()
  private val repository: TotpRepository = mockk(relaxed = true)
  private val parentEvents = mutableListOf<AppSettingsNavigationEvent>()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    coEvery { repository.confirmPendingApp(any()) } returns TotpRepository.ConfirmResult.Success(appId = APP_ID, createdAt = CREATED_AT)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `a partial code can't be submitted`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    enterCode(viewModel, "123")

    assertThat(viewModel.state.value.canSubmit).isFalse()

    viewModel.onEvent(TotpCodeEntryEvent.NextClicked)

    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `a confirmed code sends the user on to name the app the service just created`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    submit(viewModel)

    assertThat(parentEvents).isEqualTo(
      listOf(
        AppSettingsNavigationEvent.NavigateToScreen(
          AppSettingsRoute.AccountRoute.NameNewTwoFactorMethod(methodId = APP_ID, methodKind = TwoFactorMethod.Kind.AUTHENTICATOR_APP, methodCreatedAt = CREATED_AT)
        )
      )
    )
  }

  @Test
  fun `a rejected code is reported and the user stays put to try again`() = runTest(testDispatcher) {
    coEvery { repository.confirmPendingApp(any()) } returns TotpRepository.ConfirmResult.IncorrectCode

    val viewModel = createViewModel()

    submit(viewModel)

    assertThat(viewModel.state.value.error).isEqualTo(Error.IncorrectCode)
    assertThat(viewModel.state.value.submitting).isFalse()
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `an account that filled up mid-setup goes back to setup, which explains the limit`() = runTest(testDispatcher) {
    coEvery { repository.confirmPendingApp(any()) } returns TotpRepository.ConfirmResult.TooManyApps

    val viewModel = createViewModel()

    submit(viewModel)

    assertThat(viewModel.state.value.submitting).isFalse()
    assertThat(parentEvents).isEqualTo(listOf(AppSettingsNavigationEvent.NavigateBack(popUpTo = PopUpTo(AppSettingsRoute.AccountRoute.TotpSetup::class))))
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

    viewModel.onEvent(TotpCodeEntryEvent.NavigateBackClicked)

    assertThat(parentEvents).isEqualTo(listOf(AppSettingsNavigationEvent.NavigateBack()))
  }

  private fun submit(viewModel: TotpCodeEntryViewModel) {
    enterCode(viewModel, FULL_CODE)
    viewModel.onEvent(TotpCodeEntryEvent.NextClicked)
  }

  private fun enterCode(viewModel: TotpCodeEntryViewModel, code: String) {
    viewModel.onEvent(TotpCodeEntryEvent.CodeEntryEvent(CodeEntryFieldEvents.CodeChanged(code)))
  }

  private fun createViewModel() = TotpCodeEntryViewModel(parentEventEmitter = { parentEvents += it }, repository = repository)
}
