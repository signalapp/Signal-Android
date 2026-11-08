/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.invite

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
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
class InviteViewModelTest {

  companion object {
    private const val DEFAULT_INVITE_TEXT = "Let's switch to Signal: https://signal.org/install"
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state contains the default invite text`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(InviteState(inviteText = DEFAULT_INVITE_TEXT))
  }

  @Test
  fun `InviteTextChanged updates the invite text`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(InviteEvent.InviteTextChanged("Come chat with me on Signal!"))

    assertThat(viewModel.state.value.inviteText).isEqualTo("Come chat with me on Signal!")
  }

  @Test
  fun `InviteTextChanged emits no actions`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(InviteEvent.InviteTextChanged("Come chat with me on Signal!"))

    assertThat(actions).isEmpty()
  }

  @Test
  fun `ShareClicked shares the default invite text when it has not been edited`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(InviteEvent.ShareClicked)

    assertThat(actions).containsExactly(InviteAction.ShareInvite(DEFAULT_INVITE_TEXT))
  }

  @Test
  fun `ShareClicked shares the edited invite text`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(InviteEvent.InviteTextChanged("Come chat with me on Signal!"))
    viewModel.onEvent(InviteEvent.ShareClicked)

    assertThat(actions).containsExactly(InviteAction.ShareInvite("Come chat with me on Signal!"))
  }

  @Test
  fun `ShareClicked leaves the invite text alone`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(InviteEvent.ShareClicked)

    assertThat(viewModel.state.value).isEqualTo(InviteState(inviteText = DEFAULT_INVITE_TEXT))
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(InviteEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(InviteAction.NavigateBack)
  }

  @Test
  fun `logged state, events, and actions do not contain the invite text`() {
    val inviteText = "Come chat with me on Signal!"

    assertThat(InviteState(inviteText).toString()).doesNotContain(inviteText)
    assertThat(InviteEvent.InviteTextChanged(inviteText).toString()).doesNotContain(inviteText)
    assertThat(InviteAction.ShareInvite(inviteText).toString()).doesNotContain(inviteText)
  }

  private fun createViewModel(defaultInviteText: String = DEFAULT_INVITE_TEXT): InviteViewModel {
    return InviteViewModel(defaultInviteText)
  }

  private fun TestScope.collectActions(actions: Flow<InviteAction>): List<InviteAction> {
    val collected = mutableListOf<InviteAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
