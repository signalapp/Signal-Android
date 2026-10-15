/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.incommon

import android.content.Context
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
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
import org.thoughtcrime.securesms.groups.GroupInCommon
import org.thoughtcrime.securesms.groups.GroupsInCommonRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsInCommonViewModelTest {

  companion object {
    private val RECIPIENT_ID = RecipientId.from(100)

    private val GROUP_1 = GroupInCommon(recipient = Recipient(id = RecipientId.from(1), isResolving = false), name = "Family")
    private val GROUP_2 = GroupInCommon(recipient = Recipient(id = RecipientId.from(2), isResolving = false), name = "Roommates")
    private val GROUP_3 = GroupInCommon(recipient = Recipient(id = RecipientId.from(3), isResolving = false), name = "Parkdale Run Club")
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val context = mockk<Context>()
  private val groupsInCommon = MutableStateFlow<List<GroupInCommon>>(emptyList())
  private val repository = mockk<GroupsInCommonRepository> {
    every { getNamedGroupsInCommon(context, RECIPIENT_ID) } returns groupsInCommon
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state has no groups when there are none in common`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.groups).isEmpty()
  }

  @Test
  fun `groups in common are mapped to their id and display name`() = runTest(testDispatcher) {
    groupsInCommon.value = listOf(GROUP_1, GROUP_2)

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.groups).containsExactly(GROUP_1, GROUP_2)
  }

  @Test
  fun `state updates when the groups in common change`() = runTest(testDispatcher) {
    groupsInCommon.value = listOf(GROUP_1)
    val viewModel = createViewModel()

    groupsInCommon.value = listOf(GROUP_1, GROUP_2, GROUP_3)

    assertThat(viewModel.state.value.groups).containsExactly(GROUP_1, GROUP_2, GROUP_3)
  }

  @Test
  fun `GroupClicked opens the conversation for that group`() = runTest(testDispatcher) {
    groupsInCommon.value = listOf(GROUP_1, GROUP_2)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(GroupsInCommonEvent.GroupClicked(GROUP_2.recipient.id))

    assertThat(actions).containsExactly(GroupsInCommonAction.OpenGroupConversation(GROUP_2.recipient))
  }

  @Test
  fun `GroupClicked for a group that is not in common is ignored`() = runTest(testDispatcher) {
    groupsInCommon.value = listOf(GROUP_1)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(GroupsInCommonEvent.GroupClicked(GROUP_2.recipient.id))

    assertThat(actions).isEmpty()
  }

  @Test
  fun `repeated GroupClicked only opens the first conversation`() = runTest(testDispatcher) {
    groupsInCommon.value = listOf(GROUP_1, GROUP_2)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(GroupsInCommonEvent.GroupClicked(GROUP_1.recipient.id))
    viewModel.onEvent(GroupsInCommonEvent.GroupClicked(GROUP_1.recipient.id))
    viewModel.onEvent(GroupsInCommonEvent.GroupClicked(GROUP_2.recipient.id))

    assertThat(actions).containsExactly(GroupsInCommonAction.OpenGroupConversation(GROUP_1.recipient))
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(GroupsInCommonEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(GroupsInCommonAction.NavigateBack)
  }

  @Test
  fun `GroupInCommon toString does not leak the group name`() {
    assertThat(GROUP_1.toString()).isEqualTo("GroupInCommon(recipientId=${GROUP_1.recipient.id})")
  }

  private fun TestScope.createViewModel(): GroupsInCommonViewModel {
    val viewModel = GroupsInCommonViewModel(context, RECIPIENT_ID, repository)
    backgroundScope.launch { viewModel.state.collect() }
    return viewModel
  }

  private fun TestScope.collectActions(actions: Flow<GroupsInCommonAction>): List<GroupsInCommonAction> {
    val collected = mutableListOf<GroupsInCommonAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
