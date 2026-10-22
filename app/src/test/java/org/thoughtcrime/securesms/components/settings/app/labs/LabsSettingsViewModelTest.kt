/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.labs

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
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
import org.signal.appsettings.labs.LabsSettingsAction
import org.signal.appsettings.labs.LabsSettingsEvent
import org.signal.appsettings.labs.LabsSettingsState
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class LabsSettingsViewModelTest {

  companion object {
    private val INITIAL_STATE = LabsSettingsState(storyArchive = true, betterSearch = true)
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(LabsSettingsRepository)

    every { LabsSettingsRepository.getState() } returns INITIAL_STATE
    every { LabsSettingsRepository.setIndividualChatPlaintextExport(any()) } just Runs
    every { LabsSettingsRepository.setStoryArchive(any()) } just Runs
    every { LabsSettingsRepository.setIncognito(any()) } just Runs
    every { LabsSettingsRepository.setBetterSearch(any()) } just Runs
    every { LabsSettingsRepository.setStarredMessages(any()) } just Runs
    every { LabsSettingsRepository.setImprovedMessageDeletion(any()) } just Runs
  }

  @After
  fun tearDown() {
    unmockkObject(LabsSettingsRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state is read out of the repository`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel()

    assertThat(viewModel.state.value).isEqualTo(INITIAL_STATE)
  }

  @Test
  fun `IndividualChatPlaintextExportToggled persists and updates state`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel()

    viewModel.onEvent(LabsSettingsEvent.IndividualChatPlaintextExportToggled(true))

    verify { LabsSettingsRepository.setIndividualChatPlaintextExport(true) }
    assertThat(viewModel.state.value).isEqualTo(INITIAL_STATE.copy(individualChatPlaintextExport = true))
  }

  @Test
  fun `StoryArchiveToggled persists and updates state`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel()

    viewModel.onEvent(LabsSettingsEvent.StoryArchiveToggled(false))

    verify { LabsSettingsRepository.setStoryArchive(false) }
    assertThat(viewModel.state.value).isEqualTo(INITIAL_STATE.copy(storyArchive = false))
  }

  @Test
  fun `IncognitoToggled persists and updates state`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel()

    viewModel.onEvent(LabsSettingsEvent.IncognitoToggled(true))

    verify { LabsSettingsRepository.setIncognito(true) }
    assertThat(viewModel.state.value).isEqualTo(INITIAL_STATE.copy(incognito = true))
  }

  @Test
  fun `BetterSearchToggled persists and updates state`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel()

    viewModel.onEvent(LabsSettingsEvent.BetterSearchToggled(false))

    verify { LabsSettingsRepository.setBetterSearch(false) }
    assertThat(viewModel.state.value).isEqualTo(INITIAL_STATE.copy(betterSearch = false))
  }

  @Test
  fun `StarredMessagesToggled persists and updates state`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel()

    viewModel.onEvent(LabsSettingsEvent.StarredMessagesToggled(true))

    verify { LabsSettingsRepository.setStarredMessages(true) }
    assertThat(viewModel.state.value).isEqualTo(INITIAL_STATE.copy(starredMessages = true))
  }

  @Test
  fun `ImprovedMessageDeletionToggled persists and updates state`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel()

    viewModel.onEvent(LabsSettingsEvent.ImprovedMessageDeletionToggled(true))

    verify { LabsSettingsRepository.setImprovedMessageDeletion(true) }
    assertThat(viewModel.state.value).isEqualTo(INITIAL_STATE.copy(improvedMessageDeletion = true))
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(LabsSettingsEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(LabsSettingsAction.NavigateBack)
  }

  private fun TestScope.collectActions(actions: Flow<LabsSettingsAction>): List<LabsSettingsAction> {
    val collected = mutableListOf<LabsSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
