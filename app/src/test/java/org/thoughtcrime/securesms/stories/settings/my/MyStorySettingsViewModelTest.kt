/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stories.settings.my

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
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
import org.junit.Rule
import org.junit.Test
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class MyStorySettingsViewModelTest {

  companion object {
    private val ALL_EXCEPT_THREE = MyStoryPrivacyState(privacyMode = DistributionListPrivacyMode.ALL_EXCEPT, connectionCount = 3)
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val repository = mockk<MyStorySettingsRepository>(relaxUnitFun = true)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { repository.hasUserPerformedManualSelection() } returns true
    coEvery { repository.getPrivacyState() } returns ALL_EXCEPT_THREE
    coEvery { repository.getRepliesAndReactionsEnabled() } returns true
    coEvery { repository.getAllSignalConnectionsCount() } returns 42
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state reflects whether the user has made a selection`() = runTest(testDispatcher) {
    every { repository.hasUserPerformedManualSelection() } returns false

    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(MyStorySettingsState(hasUserPerformedManualSelection = false))
    assertThat(viewModel.state.value.selectedPrivacyMode).isNull()
  }

  @Test
  fun `ScreenResumed reads the settings out of the repository`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(MyStorySettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value).isEqualTo(
      MyStorySettingsState(
        myStoryPrivacyState = ALL_EXCEPT_THREE,
        areRepliesAndReactionsEnabled = true,
        allSignalConnectionsCount = 42,
        hasUserPerformedManualSelection = true
      )
    )
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MyStorySettingsEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(MyStorySettingsAction.NavigateBack)
  }

  @Test
  fun `AllSignalConnectionsClicked changes the privacy mode and records the selection without navigating`() = runTest(testDispatcher) {
    every { repository.hasUserPerformedManualSelection() } returns false
    val viewModel = createViewModel()
    viewModel.onEvent(MyStorySettingsEvent.ScreenResumed)
    val actions = collectActions(viewModel.actions)

    coEvery { repository.getPrivacyState() } returns MyStoryPrivacyState(privacyMode = DistributionListPrivacyMode.ALL)
    viewModel.onEvent(MyStorySettingsEvent.AllSignalConnectionsClicked)

    coVerify { repository.setMyStoryPrivacyMode(DistributionListPrivacyMode.ALL) }
    verify { repository.markUserPerformedManualSelection() }
    assertThat(viewModel.state.value.hasUserPerformedManualSelection).isTrue()
    assertThat(viewModel.state.value.selectedPrivacyMode).isEqualTo(DistributionListPrivacyMode.ALL)
    assertThat(actions).containsExactly()
  }

  @Test
  fun `selecting the current privacy mode only notifies that settings changed`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.onEvent(MyStorySettingsEvent.ScreenResumed)

    viewModel.onEvent(MyStorySettingsEvent.AllExceptClicked)

    coVerify(exactly = 0) { repository.setMyStoryPrivacyMode(any()) }
    coVerify { repository.notifyStorySettingsChanged() }
  }

  @Test
  fun `AllExceptClicked saves the privacy mode before navigating to the picker`() = runTest(testDispatcher) {
    coEvery { repository.getPrivacyState() } returns MyStoryPrivacyState(privacyMode = DistributionListPrivacyMode.ALL)
    val viewModel = createViewModel()
    viewModel.onEvent(MyStorySettingsEvent.ScreenResumed)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MyStorySettingsEvent.AllExceptClicked)

    coVerify { repository.setMyStoryPrivacyMode(DistributionListPrivacyMode.ALL_EXCEPT) }
    assertThat(actions).containsExactly(MyStorySettingsAction.NavigateToAllExcept)
  }

  @Test
  fun `OnlyShareWithClicked saves the privacy mode before navigating to the picker`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.onEvent(MyStorySettingsEvent.ScreenResumed)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MyStorySettingsEvent.OnlyShareWithClicked)

    coVerify { repository.setMyStoryPrivacyMode(DistributionListPrivacyMode.ONLY_WITH) }
    assertThat(actions).containsExactly(MyStorySettingsAction.NavigateToOnlyShareWith)
  }

  @Test
  fun `ViewAllSignalConnectionsClicked shows all signal connections`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MyStorySettingsEvent.ViewAllSignalConnectionsClicked)

    assertThat(actions).containsExactly(MyStorySettingsAction.ShowAllSignalConnections)
    coVerify(exactly = 0) { repository.setMyStoryPrivacyMode(any()) }
  }

  @Test
  fun `LearnMoreClicked shows the signal connections education`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(MyStorySettingsEvent.LearnMoreClicked)

    assertThat(actions).containsExactly(MyStorySettingsAction.ShowSignalConnectionsEducation)
  }

  @Test
  fun `RepliesAndReactionsToggled saves the setting and refreshes`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.onEvent(MyStorySettingsEvent.ScreenResumed)

    coEvery { repository.getRepliesAndReactionsEnabled() } returns false
    viewModel.onEvent(MyStorySettingsEvent.RepliesAndReactionsToggled(false))

    coVerifyOrder {
      repository.setRepliesAndReactionsEnabled(false)
      repository.getRepliesAndReactionsEnabled()
    }
    assertThat(viewModel.state.value.areRepliesAndReactionsEnabled).isFalse()
  }

  private fun createViewModel(): MyStorySettingsViewModel = MyStorySettingsViewModel(repository)

  private fun TestScope.collectActions(actions: Flow<MyStorySettingsAction>): List<MyStorySettingsAction> {
    val collected = mutableListOf<MyStorySettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
