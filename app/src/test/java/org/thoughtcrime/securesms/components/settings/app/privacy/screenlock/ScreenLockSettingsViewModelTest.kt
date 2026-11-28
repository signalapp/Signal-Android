/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.screenlock

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
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
import org.signal.appsettings.screenlock.ScreenLockSettingsAction
import org.signal.appsettings.screenlock.ScreenLockSettingsEvent
import org.signal.appsettings.screenlock.ScreenLockSettingsState
import org.signal.appsettings.screenlock.ScreenLockSettingsState.Dialog
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenLockSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private var screenLockEnabled = false
  private var timeout: Duration = 1.minutes
  private var canAuthenticate = true

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(ScreenLockSettingsRepository)

    every { ScreenLockSettingsRepository.isScreenLockEnabled() } answers { screenLockEnabled }
    every { ScreenLockSettingsRepository.setScreenLockEnabled(any()) } answers { screenLockEnabled = firstArg() }
    every { ScreenLockSettingsRepository.getTimeout() } answers { timeout }
    every { ScreenLockSettingsRepository.setTimeout(any()) } just Runs
    every { ScreenLockSettingsRepository.canAuthenticate() } answers { canAuthenticate }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkObject(ScreenLockSettingsRepository)
  }

  @Test
  fun `initial state is read out of the repository`() = runTest(testDispatcher) {
    screenLockEnabled = true
    timeout = 3.hours
    canAuthenticate = false

    val viewModel = ScreenLockSettingsViewModel()

    assertThat(viewModel.state.value).isEqualTo(
      ScreenLockSettingsState(isScreenLockEnabled = true, timeout = 3.hours, canAuthenticate = false)
    )
  }

  @Test
  fun `ScreenResumed picks up a device that can no longer authenticate`() = runTest(testDispatcher) {
    val viewModel = ScreenLockSettingsViewModel()

    canAuthenticate = false
    viewModel.onEvent(ScreenLockSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.canAuthenticate).isFalse()
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = ScreenLockSettingsViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(ScreenLockSettingsEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(ScreenLockSettingsAction.NavigateBack)
  }

  @Test
  fun `ScreenLockToggled stores the new setting`() = runTest(testDispatcher) {
    val viewModel = ScreenLockSettingsViewModel()

    viewModel.onEvent(ScreenLockSettingsEvent.ScreenLockToggled(true))

    verify { ScreenLockSettingsRepository.setScreenLockEnabled(true) }
    assertThat(viewModel.state.value.isScreenLockEnabled).isTrue()
  }

  @Test
  fun `TimeoutSelected stores the timeout`() = runTest(testDispatcher) {
    stubSetTimeout(30.minutes)
    val viewModel = ScreenLockSettingsViewModel()

    viewModel.onEvent(ScreenLockSettingsEvent.TimeoutSelected(30.minutes))

    verify { ScreenLockSettingsRepository.setTimeout(30.minutes) }
    assertThat(viewModel.state.value.timeout).isEqualTo(30.minutes)
  }

  @Test
  fun `CustomTimeoutClicked shows the custom timeout picker`() = runTest(testDispatcher) {
    val viewModel = ScreenLockSettingsViewModel()

    viewModel.onEvent(ScreenLockSettingsEvent.CustomTimeoutClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.CustomTimeout)
  }

  @Test
  fun `CustomTimeoutSelected stores the timeout and closes the picker`() = runTest(testDispatcher) {
    stubSetTimeout(3.hours)
    val viewModel = ScreenLockSettingsViewModel()

    viewModel.onEvent(ScreenLockSettingsEvent.CustomTimeoutClicked)
    viewModel.onEvent(ScreenLockSettingsEvent.CustomTimeoutSelected(3.hours))

    verify { ScreenLockSettingsRepository.setTimeout(3.hours) }
    assertThat(viewModel.state.value.timeout).isEqualTo(3.hours)
    assertThat(viewModel.state.value.isCustomTimeout).isTrue()
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `CustomTimeoutSelected is ignored without the picker`() = runTest(testDispatcher) {
    val viewModel = ScreenLockSettingsViewModel()

    viewModel.onEvent(ScreenLockSettingsEvent.CustomTimeoutSelected(3.hours))

    verify(exactly = 0) { ScreenLockSettingsRepository.setTimeout(any()) }
  }

  @Test
  fun `DialogDismissed closes the picker without changing the timeout`() = runTest(testDispatcher) {
    val viewModel = ScreenLockSettingsViewModel()

    viewModel.onEvent(ScreenLockSettingsEvent.CustomTimeoutClicked)
    viewModel.onEvent(ScreenLockSettingsEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    verify(exactly = 0) { ScreenLockSettingsRepository.setTimeout(any()) }
  }

  /** MockK hands value class arguments like [Duration] to answers unboxed, so stub each expected value directly. */
  private fun stubSetTimeout(expected: Duration) {
    every { ScreenLockSettingsRepository.setTimeout(expected) } answers { timeout = expected }
  }

  private fun TestScope.collectActions(actions: Flow<ScreenLockSettingsAction>): List<ScreenLockSettingsAction> {
    val collected = mutableListOf<ScreenLockSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
