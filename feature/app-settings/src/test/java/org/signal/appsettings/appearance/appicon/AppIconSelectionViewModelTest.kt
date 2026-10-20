/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appearance.appicon

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppIconSelectionViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private val appIconUtility = mockk<AppIconUtility>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { appIconUtility.currentAppIcon } returns AppIconPreset.DEFAULT
    every { appIconUtility.isCurrentlySelected(any()) } answers { firstArg<AppIconPreset>() == AppIconPreset.DEFAULT }
    every { appIconUtility.setNewAppIcon(any()) } just Runs
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state is the launcher's current icon`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(AppIconSelectionState(currentIcon = AppIconPreset.DEFAULT))
  }

  @Test
  fun `IconClicked asks for confirmation and highlights the pending icon`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppIconSelectionEvent.IconClicked(AppIconPreset.WEATHER))

    assertThat(viewModel.state.value.dialog).isEqualTo(AppIconSelectionState.Dialog.ConfirmIconChange(AppIconPreset.WEATHER))
    assertThat(viewModel.state.value.selectedIcon).isEqualTo(AppIconPreset.WEATHER)
    verify(exactly = 0) { appIconUtility.setNewAppIcon(any()) }
  }

  @Test
  fun `IconClicked on the selected icon does nothing`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppIconSelectionEvent.IconClicked(AppIconPreset.DEFAULT))

    assertThat(viewModel.state.value.dialog).isEqualTo(AppIconSelectionState.Dialog.None)
  }

  @Test
  fun `ChangeConfirmed applies the pending icon`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppIconSelectionEvent.IconClicked(AppIconPreset.WEATHER))
    viewModel.onEvent(AppIconSelectionEvent.ChangeConfirmed)

    verify(exactly = 1) { appIconUtility.setNewAppIcon(AppIconPreset.WEATHER) }
    assertThat(viewModel.state.value).isEqualTo(AppIconSelectionState(currentIcon = AppIconPreset.WEATHER))
  }

  @Test
  fun `ChangeConfirmed without a pending icon does nothing`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppIconSelectionEvent.ChangeConfirmed)

    verify(exactly = 0) { appIconUtility.setNewAppIcon(any()) }
  }

  @Test
  fun `DialogDismissed reverts the highlight to the current icon`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AppIconSelectionEvent.IconClicked(AppIconPreset.WEATHER))
    viewModel.onEvent(AppIconSelectionEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(AppIconSelectionState.Dialog.None)
    assertThat(viewModel.state.value.selectedIcon).isEqualTo(AppIconPreset.DEFAULT)
    verify(exactly = 0) { appIconUtility.setNewAppIcon(any()) }
  }

  @Test
  fun `LearnMoreClicked opens the tutorial`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AppIconSelectionEvent.LearnMoreClicked)

    assertThat(actions).containsExactly(AppIconSelectionAction.NavigateToTutorial)
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AppIconSelectionEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(AppIconSelectionAction.NavigateBack)
  }

  private fun createViewModel(): AppIconSelectionViewModel = AppIconSelectionViewModel(appIconUtility)

  private fun TestScope.collectActions(actions: Flow<AppIconSelectionAction>): List<AppIconSelectionAction> {
    val collected = mutableListOf<AppIconSelectionAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
