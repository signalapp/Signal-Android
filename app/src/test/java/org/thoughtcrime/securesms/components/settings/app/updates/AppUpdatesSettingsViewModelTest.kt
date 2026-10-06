/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.updates

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.updates.AppUpdatesSettingsAction
import org.signal.appsettings.updates.AppUpdatesSettingsEvent
import org.signal.appsettings.updates.AppUpdatesSettingsState
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.ApkUpdateJob
import org.thoughtcrime.securesms.keyvalue.ApkUpdateValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AppUpdatesSettingsViewModelTest {

  companion object {
    private const val LAST_SUCCESSFUL_CHECK = 1_700_000_000_000L
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private val apkUpdateValues = mockk<ApkUpdateValues>(relaxUnitFun = true)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    mockkObject(SignalStore)
    every { SignalStore.apkUpdate } returns apkUpdateValues
    every { apkUpdateValues.lastSuccessfulCheck } returns LAST_SUCCESSFUL_CHECK
    every { apkUpdateValues.autoUpdate } returns false
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  @Test
  fun `initial state reflects storage`() = runTest(testDispatcher) {
    every { apkUpdateValues.autoUpdate } returns true

    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(
      AppUpdatesSettingsState(
        lastCheckedTime = LAST_SUCCESSFUL_CHECK.milliseconds,
        autoUpdateEnabled = true,
        isAutoUpdateSupported = true
      )
    )
  }

  @Test
  fun `initial state reports a device that cannot auto update`() = runTest(testDispatcher) {
    val viewModel = createViewModel(isAutoUpdateSupported = false)

    assertThat(viewModel.state.value.isAutoUpdateSupported).isFalse()
  }

  @Test
  fun `initial state reports never having checked`() = runTest(testDispatcher) {
    every { apkUpdateValues.lastSuccessfulCheck } returns 0L

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.lastCheckedTime).isEqualTo(Duration.ZERO)
  }

  @Test
  fun `ScreenResumed picks up a check that finished while the screen was away`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    every { apkUpdateValues.lastSuccessfulCheck } returns LAST_SUCCESSFUL_CHECK + 1000

    viewModel.onEvent(AppUpdatesSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.lastCheckedTime).isEqualTo((LAST_SUCCESSFUL_CHECK + 1000).milliseconds)
  }

  @Test
  fun `AutoUpdateToggled on saves and shows the new value`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    every { apkUpdateValues.autoUpdate = true } answers { every { apkUpdateValues.autoUpdate } returns true }

    viewModel.onEvent(AppUpdatesSettingsEvent.AutoUpdateToggled(true))

    verify { apkUpdateValues.autoUpdate = true }
    assertThat(viewModel.state.value.autoUpdateEnabled).isTrue()
  }

  @Test
  fun `AutoUpdateToggled off saves and shows the new value`() = runTest(testDispatcher) {
    every { apkUpdateValues.autoUpdate } returns true
    val viewModel = createViewModel()
    every { apkUpdateValues.autoUpdate = false } answers { every { apkUpdateValues.autoUpdate } returns false }

    viewModel.onEvent(AppUpdatesSettingsEvent.AutoUpdateToggled(false))

    verify { apkUpdateValues.autoUpdate = false }
    assertThat(viewModel.state.value.autoUpdateEnabled).isFalse()
  }

  @Test
  fun `CheckForUpdatesClicked enqueues an update check`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AppUpdatesSettingsEvent.CheckForUpdatesClicked)

    verify(exactly = 1) { AppDependencies.jobManager.add(ofType<ApkUpdateJob>()) }
    assertThat(actions).isEmpty()
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AppUpdatesSettingsEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(AppUpdatesSettingsAction.NavigateBack)
  }

  private fun createViewModel(isAutoUpdateSupported: Boolean = true): AppUpdatesSettingsViewModel {
    return AppUpdatesSettingsViewModel(isAutoUpdateSupported = isAutoUpdateSupported)
  }

  private fun TestScope.collectActions(actions: Flow<AppUpdatesSettingsAction>): List<AppUpdatesSettingsAction> {
    val collected = mutableListOf<AppUpdatesSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
