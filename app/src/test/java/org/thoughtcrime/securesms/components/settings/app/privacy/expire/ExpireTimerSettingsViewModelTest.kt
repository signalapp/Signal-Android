/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.expire

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.signal.appsettings.expiretimer.ExpireTimerSettingsAction
import org.signal.appsettings.expiretimer.ExpireTimerSettingsEvent
import org.signal.appsettings.expiretimer.ExpireTimerSettingsState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ExpireTimerSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val recipientId = RecipientId.from(42)
  private val recipientExpiration = MutableStateFlow(0)
  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(ExpireTimerSettingsRepository)

    every { ExpireTimerSettingsRepository.getUniversalExpireTimer() } returns 0
    every { ExpireTimerSettingsRepository.observeExpiration(recipientId) } returns recipientExpiration
    coEvery { ExpireTimerSettingsRepository.setUniversalExpireTimer(any()) } just runs
    coEvery { ExpireTimerSettingsRepository.setExpiration(any(), any()) } returns ExpireTimerSettingsRepository.SetExpirationResult.Success
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkObject(ExpireTimerSettingsRepository)
  }

  @Test
  fun `initial state for new chats is the universal timer`() = runTest(testDispatcher) {
    every { ExpireTimerSettingsRepository.getUniversalExpireTimer() } returns 3600

    val viewModel = createViewModel(ExpireTimerSettingsViewModel.Config())

    assertThat(viewModel.state.value).isEqualTo(ExpireTimerSettingsState(mode = ExpireTimerSettingsState.Mode.UNIVERSAL, initialTimer = 1.hours))
  }

  @Test
  fun `initial state for a group being created is the value it was opened with`() = runTest(testDispatcher) {
    val viewModel = createViewModel(ExpireTimerSettingsViewModel.Config(forResultMode = true, initialValue = 86400))

    assertThat(viewModel.state.value).isEqualTo(ExpireTimerSettingsState(mode = ExpireTimerSettingsState.Mode.GROUP_CREATE, initialTimer = 1.days))
  }

  @Test
  fun `a chat's timer follows the chat as it changes`() = runTest(testDispatcher) {
    recipientExpiration.value = 30
    val viewModel = createViewModel(ExpireTimerSettingsViewModel.Config(recipientId = recipientId))

    assertThat(viewModel.state.value.mode).isEqualTo(ExpireTimerSettingsState.Mode.RECIPIENT)
    assertThat(viewModel.state.value.initialTimer).isEqualTo(30.seconds)

    recipientExpiration.value = 3600

    assertThat(viewModel.state.value.initialTimer).isEqualTo(1.hours)
  }

  @Test
  fun `TimerSelected selects the timer`() = runTest(testDispatcher) {
    val viewModel = createViewModel(ExpireTimerSettingsViewModel.Config())

    viewModel.onEvent(ExpireTimerSettingsEvent.TimerSelected(1.days))

    assertThat(viewModel.state.value.currentTimer).isEqualTo(1.days)
  }

  @Test
  fun `CustomTimeClicked opens the custom time dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel(ExpireTimerSettingsViewModel.Config())

    viewModel.onEvent(ExpireTimerSettingsEvent.CustomTimeClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(ExpireTimerSettingsState.Dialog.CustomTimer)
  }

  @Test
  fun `CustomTimerSelected selects the timer and closes the dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel(ExpireTimerSettingsViewModel.Config())

    viewModel.onEvent(ExpireTimerSettingsEvent.CustomTimeClicked)
    viewModel.onEvent(ExpireTimerSettingsEvent.CustomTimerSelected(3.hours))

    assertThat(viewModel.state.value.currentTimer).isEqualTo(3.hours)
    assertThat(viewModel.state.value.dialog).isEqualTo(ExpireTimerSettingsState.Dialog.None)
  }

  @Test
  fun `DialogDismissed closes the dialog without changing the timer`() = runTest(testDispatcher) {
    val viewModel = createViewModel(ExpireTimerSettingsViewModel.Config())

    viewModel.onEvent(ExpireTimerSettingsEvent.CustomTimeClicked)
    viewModel.onEvent(ExpireTimerSettingsEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(ExpireTimerSettingsState.Dialog.None)
    assertThat(viewModel.state.value.selectedTimer).isEqualTo(null)
  }

  @Test
  fun `SaveClicked without a change navigates back without saving`() = runTest(testDispatcher) {
    val viewModel = createViewModel(ExpireTimerSettingsViewModel.Config())

    viewModel.onEvent(ExpireTimerSettingsEvent.SaveClicked)

    coVerify(exactly = 0) { ExpireTimerSettingsRepository.setUniversalExpireTimer(any()) }
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack())
  }

  @Test
  fun `SaveClicked saves the universal timer and navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel(ExpireTimerSettingsViewModel.Config())

    viewModel.onEvent(ExpireTimerSettingsEvent.TimerSelected(1.days))
    viewModel.onEvent(ExpireTimerSettingsEvent.SaveClicked)

    coVerify { ExpireTimerSettingsRepository.setUniversalExpireTimer(86400) }
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack())
  }

  @Test
  fun `SaveClicked saves a chat's timer and navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel(ExpireTimerSettingsViewModel.Config(recipientId = recipientId))

    viewModel.onEvent(ExpireTimerSettingsEvent.TimerSelected(1.hours))
    viewModel.onEvent(ExpireTimerSettingsEvent.SaveClicked)

    coVerify { ExpireTimerSettingsRepository.setExpiration(recipientId, 3600) }
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack())
  }

  @Test
  fun `SaveClicked shows the failure and stays when a chat's timer can't be saved`() = runTest(testDispatcher) {
    coEvery { ExpireTimerSettingsRepository.setExpiration(any(), any()) } returns ExpireTimerSettingsRepository.SetExpirationResult.Failure(123)
    val viewModel = createViewModel(ExpireTimerSettingsViewModel.Config(recipientId = recipientId))

    viewModel.onEvent(ExpireTimerSettingsEvent.TimerSelected(1.hours))
    viewModel.onEvent(ExpireTimerSettingsEvent.SaveClicked)

    assertThat(viewModel.state.value.snackbar).isEqualTo(ExpireTimerSettingsState.Snackbar.SaveFailed(123))
    assertThat(viewModel.state.value.isSaving).isFalse()
    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `SnackbarDismissed clears the snackbar`() = runTest(testDispatcher) {
    coEvery { ExpireTimerSettingsRepository.setExpiration(any(), any()) } returns ExpireTimerSettingsRepository.SetExpirationResult.Failure(123)
    val viewModel = createViewModel(ExpireTimerSettingsViewModel.Config(recipientId = recipientId))

    viewModel.onEvent(ExpireTimerSettingsEvent.TimerSelected(1.hours))
    viewModel.onEvent(ExpireTimerSettingsEvent.SaveClicked)
    viewModel.onEvent(ExpireTimerSettingsEvent.SnackbarDismissed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(ExpireTimerSettingsState.Snackbar.None)
  }

  @Test
  fun `SaveClicked for a group being created returns the timer without saving`() = runTest(testDispatcher) {
    val viewModel = createViewModel(ExpireTimerSettingsViewModel.Config(forResultMode = true))
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(ExpireTimerSettingsEvent.SaveClicked)

    assertThat(actions).containsExactly(ExpireTimerSettingsAction.ReturnResult(0.seconds))
    coVerify(exactly = 0) { ExpireTimerSettingsRepository.setUniversalExpireTimer(any()) }
    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel(ExpireTimerSettingsViewModel.Config())

    viewModel.onEvent(ExpireTimerSettingsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack())
  }

  private fun createViewModel(config: ExpireTimerSettingsViewModel.Config): ExpireTimerSettingsViewModel {
    return ExpireTimerSettingsViewModel(config, parentEventEmitter = { navigationEvents += it })
  }

  private fun TestScope.collectActions(actions: Flow<ExpireTimerSettingsAction>): List<ExpireTimerSettingsAction> {
    val collected = mutableListOf<ExpireTimerSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
