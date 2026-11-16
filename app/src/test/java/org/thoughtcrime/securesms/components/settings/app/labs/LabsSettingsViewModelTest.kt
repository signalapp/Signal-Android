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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.labs.LabsSettingsEvent
import org.signal.appsettings.labs.LabsSettingsState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class LabsSettingsViewModelTest {

  companion object {
    private val INITIAL_STATE = LabsSettingsState(storyArchive = true, betterSearch = true)
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

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
    val viewModel = LabsSettingsViewModel(parentEventEmitter = { navigationEvents += it })

    assertThat(viewModel.state.value).isEqualTo(INITIAL_STATE)
  }

  @Test
  fun `IndividualChatPlaintextExportToggled persists and updates state`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel(parentEventEmitter = { navigationEvents += it })

    viewModel.onEvent(LabsSettingsEvent.IndividualChatPlaintextExportToggled(true))

    verify { LabsSettingsRepository.setIndividualChatPlaintextExport(true) }
    assertThat(viewModel.state.value).isEqualTo(INITIAL_STATE.copy(individualChatPlaintextExport = true))
  }

  @Test
  fun `StoryArchiveToggled persists and updates state`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel(parentEventEmitter = { navigationEvents += it })

    viewModel.onEvent(LabsSettingsEvent.StoryArchiveToggled(false))

    verify { LabsSettingsRepository.setStoryArchive(false) }
    assertThat(viewModel.state.value).isEqualTo(INITIAL_STATE.copy(storyArchive = false))
  }

  @Test
  fun `IncognitoToggled persists and updates state`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel(parentEventEmitter = { navigationEvents += it })

    viewModel.onEvent(LabsSettingsEvent.IncognitoToggled(true))

    verify { LabsSettingsRepository.setIncognito(true) }
    assertThat(viewModel.state.value).isEqualTo(INITIAL_STATE.copy(incognito = true))
  }

  @Test
  fun `BetterSearchToggled persists and updates state`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel(parentEventEmitter = { navigationEvents += it })

    viewModel.onEvent(LabsSettingsEvent.BetterSearchToggled(false))

    verify { LabsSettingsRepository.setBetterSearch(false) }
    assertThat(viewModel.state.value).isEqualTo(INITIAL_STATE.copy(betterSearch = false))
  }

  @Test
  fun `StarredMessagesToggled persists and updates state`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel(parentEventEmitter = { navigationEvents += it })

    viewModel.onEvent(LabsSettingsEvent.StarredMessagesToggled(true))

    verify { LabsSettingsRepository.setStarredMessages(true) }
    assertThat(viewModel.state.value).isEqualTo(INITIAL_STATE.copy(starredMessages = true))
  }

  @Test
  fun `ImprovedMessageDeletionToggled persists and updates state`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel(parentEventEmitter = { navigationEvents += it })

    viewModel.onEvent(LabsSettingsEvent.ImprovedMessageDeletionToggled(true))

    verify { LabsSettingsRepository.setImprovedMessageDeletion(true) }
    assertThat(viewModel.state.value).isEqualTo(INITIAL_STATE.copy(improvedMessageDeletion = true))
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = LabsSettingsViewModel(parentEventEmitter = { navigationEvents += it })

    viewModel.onEvent(LabsSettingsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack())
  }
}
