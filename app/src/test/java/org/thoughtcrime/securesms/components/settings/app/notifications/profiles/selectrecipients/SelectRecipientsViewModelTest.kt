/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.selectrecipients

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
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
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class SelectRecipientsViewModelTest {

  companion object {
    private const val PROFILE_ID = 8L
    private val ALICE = RecipientId.from(10L)
    private val BOB = RecipientId.from(11L)
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(SelectRecipientsRepository)
    coEvery { SelectRecipientsRepository.setAllowedMembers(PROFILE_ID, any()) } just Runs
  }

  @After
  fun tearDown() {
    unmockkObject(SelectRecipientsRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `nothing can be added until someone is picked`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    assertThat(viewModel.state.value.canSave).isFalse()
    viewModel.onEvent(SelectRecipientsEvent.AddClicked)

    coVerify(exactly = 0) { SelectRecipientsRepository.setAllowedMembers(any(), any()) }
    assertThat(actions).isEmpty()
  }

  @Test
  fun `adding saves the picks and goes back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(SelectRecipientsEvent.SelectionChanged(setOf(ALICE, BOB)))
    viewModel.onEvent(SelectRecipientsEvent.AddClicked)

    coVerify(exactly = 1) { SelectRecipientsRepository.setAllowedMembers(PROFILE_ID, setOf(ALICE, BOB)) }
    assertThat(actions).containsExactly(SelectRecipientsAction.NavigateBack)
  }

  @Test
  fun `the latest selection is the one saved`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(SelectRecipientsEvent.SelectionChanged(setOf(ALICE, BOB)))
    viewModel.onEvent(SelectRecipientsEvent.SelectionChanged(setOf(BOB)))
    viewModel.onEvent(SelectRecipientsEvent.AddClicked)

    coVerify(exactly = 1) { SelectRecipientsRepository.setAllowedMembers(PROFILE_ID, setOf(BOB)) }
  }

  @Test
  fun `adding is disabled while saving`() = runTest(testDispatcher) {
    val saved = CompletableDeferred<Unit>()
    coEvery { SelectRecipientsRepository.setAllowedMembers(PROFILE_ID, any()) } coAnswers { saved.await() }

    val viewModel = createViewModel()
    viewModel.onEvent(SelectRecipientsEvent.SelectionChanged(setOf(ALICE)))
    viewModel.onEvent(SelectRecipientsEvent.AddClicked)

    assertThat(viewModel.state.value.isSaving).isTrue()
    assertThat(viewModel.state.value.canSave).isFalse()

    saved.complete(Unit)
    assertThat(viewModel.state.value.isSaving).isFalse()
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(SelectRecipientsEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(SelectRecipientsAction.NavigateBack)
  }

  @Test
  fun `adding keeps the members the profile already has`() = runTest(testDispatcher) {
    val viewModel = createViewModel(currentMembers = setOf(ALICE))

    viewModel.onEvent(SelectRecipientsEvent.SelectionChanged(setOf(BOB)))
    viewModel.onEvent(SelectRecipientsEvent.AddClicked)

    coVerify(exactly = 1) { SelectRecipientsRepository.setAllowedMembers(PROFILE_ID, setOf(ALICE, BOB)) }
  }

  @Test
  fun `a profile with members can be saved before anything new is picked`() = runTest(testDispatcher) {
    val viewModel = createViewModel(currentMembers = setOf(ALICE))
    val actions = collectActions(viewModel.actions)

    assertThat(viewModel.state.value.canSave).isTrue()
    viewModel.onEvent(SelectRecipientsEvent.AddClicked)

    coVerify(exactly = 1) { SelectRecipientsRepository.setAllowedMembers(PROFILE_ID, setOf(ALICE)) }
    assertThat(actions).containsExactly(SelectRecipientsAction.NavigateBack)
  }

  private fun createViewModel(currentMembers: Set<RecipientId> = emptySet()): SelectRecipientsViewModel {
    return SelectRecipientsViewModel(profileId = PROFILE_ID, currentMembers = currentMembers)
  }

  private fun TestScope.collectActions(actions: Flow<SelectRecipientsAction>): List<SelectRecipientsAction> {
    val collected = mutableListOf<SelectRecipientsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
