/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.allowedmembers

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
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
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AddAllowedMembersViewModelTest {

  companion object {
    private const val PROFILE_ID = 4L
    private val ALICE = RecipientId.from(10L)
    private val BOB = RecipientId.from(11L)
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val profiles = MutableSharedFlow<AddAllowedMembersRepository.ProfileAndMembers>()
  private val alice = recipient(ALICE)
  private val profile = NotificationProfile(
    id = PROFILE_ID,
    name = "Work",
    emoji = "",
    createdAt = 0L,
    schedule = NotificationProfileSchedule(id = PROFILE_ID),
    allowedMembers = setOf(ALICE, BOB),
    notificationProfileId = NotificationProfileId(UUID(0L, PROFILE_ID))
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(AddAllowedMembersRepository)

    every { AddAllowedMembersRepository.observeProfile(PROFILE_ID) } returns profiles
    coEvery { AddAllowedMembersRepository.removeMember(PROFILE_ID, any()) } returns alice
    coEvery { AddAllowedMembersRepository.addMember(PROFILE_ID, any()) } just Runs
    coEvery { AddAllowedMembersRepository.setAllowAllCalls(PROFILE_ID, any()) } just Runs
    coEvery { AddAllowedMembersRepository.setAllowAllMentions(PROFILE_ID, any()) } just Runs
  }

  @After
  fun tearDown() {
    unmockkObject(AddAllowedMembersRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `the profile is unknown until the repository emits`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(AddAllowedMembersState())
  }

  @Test
  fun `each emission replaces the profile and its members`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    profiles.emit(AddAllowedMembersRepository.ProfileAndMembers(profile, listOf(alice)))

    assertThat(viewModel.state.value.profile).isEqualTo(profile)
    assertThat(viewModel.state.value.members).containsExactly(alice)
  }

  @Test
  fun `a failure reading the profile leaves the screen empty`() = runTest(testDispatcher) {
    every { AddAllowedMembersRepository.observeProfile(PROFILE_ID) } returns flow { throw IllegalStateException("missing") }

    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(AddAllowedMembersState())
  }

  @Test
  fun `RemoveMemberClicked removes the member and confirms with a snackbar`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AddAllowedMembersEvent.RemoveMemberClicked(ALICE))

    coVerify(exactly = 1) { AddAllowedMembersRepository.removeMember(PROFILE_ID, ALICE) }
    assertThat(viewModel.state.value.snackbar).isEqualTo(AddAllowedMembersState.Snackbar.MemberRemoved(alice))
  }

  @Test
  fun `UndoRemoveMemberClicked adds the member back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AddAllowedMembersEvent.UndoRemoveMemberClicked(ALICE))

    coVerify(exactly = 1) { AddAllowedMembersRepository.addMember(PROFILE_ID, ALICE) }
  }

  @Test
  fun `SnackbarDismissed clears the snackbar`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AddAllowedMembersEvent.RemoveMemberClicked(ALICE))
    viewModel.onEvent(AddAllowedMembersEvent.SnackbarDismissed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(AddAllowedMembersState.Snackbar.None)
  }

  @Test
  fun `AllowAllCallsToggled saves the new value`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AddAllowedMembersEvent.AllowAllCallsToggled(false))

    coVerify(exactly = 1) { AddAllowedMembersRepository.setAllowAllCalls(PROFILE_ID, false) }
  }

  @Test
  fun `NotifyForAllMentionsToggled saves the new value`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AddAllowedMembersEvent.NotifyForAllMentionsToggled(true))

    coVerify(exactly = 1) { AddAllowedMembersRepository.setAllowAllMentions(PROFILE_ID, true) }
  }

  @Test
  fun `AddMembersClicked opens the picker with the current members selected`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    profiles.emit(AddAllowedMembersRepository.ProfileAndMembers(profile, listOf(alice)))
    viewModel.onEvent(AddAllowedMembersEvent.AddMembersClicked)

    assertThat(actions).containsExactly(AddAllowedMembersAction.NavigateToSelectRecipients(PROFILE_ID, setOf(ALICE, BOB)))
  }

  @Test
  fun `AddMembersClicked before the profile loads does nothing`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AddAllowedMembersEvent.AddMembersClicked)

    assertThat(actions).isEmpty()
  }

  @Test
  fun `NextClicked continues to the schedule`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AddAllowedMembersEvent.NextClicked)

    assertThat(actions).containsExactly(AddAllowedMembersAction.NavigateToSchedule(PROFILE_ID))
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AddAllowedMembersEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(AddAllowedMembersAction.NavigateBack)
  }

  private fun createViewModel(): AddAllowedMembersViewModel = AddAllowedMembersViewModel(PROFILE_ID)

  private fun recipient(id: RecipientId): Recipient {
    return mockk<Recipient> { every { this@mockk.id } returns id }
  }

  private fun TestScope.collectActions(actions: Flow<AddAllowedMembersAction>): List<AddAllowedMembersAction> {
    val collected = mutableListOf<AddAllowedMembersAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
