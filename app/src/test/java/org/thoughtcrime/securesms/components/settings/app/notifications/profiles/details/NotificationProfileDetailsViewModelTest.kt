/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.details

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
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
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfileEdits
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationProfileDetailsViewModelTest {

  companion object {
    private const val PROFILE_ID = 4L
    private val ALICE = RecipientId.from(10L)
    private val BOB = RecipientId.from(11L)
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val details = MutableSharedFlow<NotificationProfileDetailsRepository.ProfileDetails?>()
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
    mockkObject(NotificationProfileDetailsRepository)
    mockkObject(NotificationProfileEdits)

    every { NotificationProfileDetailsRepository.observeProfile(PROFILE_ID) } returns details
    coEvery { NotificationProfileDetailsRepository.toggleProfile(any()) } just Runs
    coEvery { NotificationProfileEdits.removeMember(PROFILE_ID, any()) } returns alice
    coEvery { NotificationProfileEdits.addMember(PROFILE_ID, any()) } just Runs
    coEvery { NotificationProfileEdits.setAllowAllCalls(PROFILE_ID, any()) } just Runs
    coEvery { NotificationProfileEdits.setAllowAllMentions(PROFILE_ID, any()) } just Runs
    coEvery { NotificationProfileDetailsRepository.deleteProfile(PROFILE_ID) } just Runs
  }

  @After
  fun tearDown() {
    unmockkObject(NotificationProfileDetailsRepository)
    unmockkObject(NotificationProfileEdits)
    Dispatchers.resetMain()
  }

  @Test
  fun `the profile, its members and whether it is on are read out of the repository`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val activeStatus = NotificationProfiles.ActiveStatus.On

    details.emit(NotificationProfileDetailsRepository.ProfileDetails(profile, listOf(alice), activeStatus))

    assertThat(viewModel.state.value).isEqualTo(NotificationProfileDetailsState(profile = profile, members = listOf(alice), activeStatus = activeStatus))
  }

  @Test
  fun `the screen closes once the profile no longer exists`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    details.emit(loaded())
    details.emit(null)

    assertThat(actions).containsExactly(NotificationProfileDetailsAction.NavigateBack)
  }

  @Test
  fun `expanding the member list survives a profile update`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    details.emit(loaded())

    viewModel.onEvent(NotificationProfileDetailsEvent.SeeAllMembersClicked)
    details.emit(loaded())

    assertThat(viewModel.state.value.expanded).isTrue()
  }

  @Test
  fun `only the first few members show until the list is expanded`() {
    val members = (1L..7L).map { recipient(RecipientId.from(it)) }
    val state = NotificationProfileDetailsState(profile = profile, members = members)

    assertThat(state.visibleMembers).isEqualTo(members.take(NotificationProfileDetailsState.COLLAPSED_MEMBER_COUNT))
    assertThat(state.showSeeAll).isTrue()

    val expanded = state.copy(expanded = true)
    assertThat(expanded.visibleMembers).isEqualTo(members)
    assertThat(expanded.showSeeAll).isFalse()
  }

  @Test
  fun `see all is hidden when every member already fits`() {
    val members = (1L..NotificationProfileDetailsState.COLLAPSED_MEMBER_COUNT.toLong()).map { recipient(RecipientId.from(it)) }

    assertThat(NotificationProfileDetailsState(profile = profile, members = members).showSeeAll).isFalse()
  }

  @Test
  fun `ProfileToggled toggles the loaded profile`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    details.emit(loaded())

    viewModel.onEvent(NotificationProfileDetailsEvent.ProfileToggled)

    coVerify(exactly = 1) { NotificationProfileDetailsRepository.toggleProfile(profile) }
  }

  @Test
  fun `ProfileToggled does nothing before the profile loads`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationProfileDetailsEvent.ProfileToggled)

    coVerify(exactly = 0) { NotificationProfileDetailsRepository.toggleProfile(any()) }
  }

  @Test
  fun `EditClicked opens the editor`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(NotificationProfileDetailsEvent.EditClicked)

    assertThat(actions).containsExactly(NotificationProfileDetailsAction.NavigateToEdit(PROFILE_ID))
  }

  @Test
  fun `AddMembersClicked opens the picker with the current members selected`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)
    details.emit(loaded())

    viewModel.onEvent(NotificationProfileDetailsEvent.AddMembersClicked)

    assertThat(actions).containsExactly(NotificationProfileDetailsAction.NavigateToSelectRecipients(PROFILE_ID, setOf(ALICE, BOB)))
  }

  @Test
  fun `AddMembersClicked does nothing before the profile loads`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(NotificationProfileDetailsEvent.AddMembersClicked)

    assertThat(actions).isEmpty()
  }

  @Test
  fun `removing a member shows a snackbar naming them`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationProfileDetailsEvent.RemoveMemberClicked(ALICE))

    coVerify(exactly = 1) { NotificationProfileEdits.removeMember(PROFILE_ID, ALICE) }
    assertThat(viewModel.state.value.snackbar).isEqualTo(NotificationProfileDetailsState.Snackbar.MemberRemoved(alice))
  }

  @Test
  fun `undoing a removal adds the member back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationProfileDetailsEvent.UndoRemoveMemberClicked(ALICE))

    coVerify(exactly = 1) { NotificationProfileEdits.addMember(PROFILE_ID, ALICE) }
  }

  @Test
  fun `SnackbarDismissed clears the snackbar`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationProfileDetailsEvent.RemoveMemberClicked(ALICE))
    viewModel.onEvent(NotificationProfileDetailsEvent.SnackbarDismissed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(NotificationProfileDetailsState.Snackbar.None)
  }

  @Test
  fun `ScheduleClicked opens the schedule editor`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(NotificationProfileDetailsEvent.ScheduleClicked)

    assertThat(actions).containsExactly(NotificationProfileDetailsAction.NavigateToSchedule(PROFILE_ID))
  }

  @Test
  fun `the exception toggles are saved`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationProfileDetailsEvent.AllowAllCallsToggled(false))
    viewModel.onEvent(NotificationProfileDetailsEvent.NotifyForAllMentionsToggled(true))

    coVerify(exactly = 1) { NotificationProfileEdits.setAllowAllCalls(PROFILE_ID, false) }
    coVerify(exactly = 1) { NotificationProfileEdits.setAllowAllMentions(PROFILE_ID, true) }
  }

  @Test
  fun `DeleteClicked asks for confirmation without deleting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationProfileDetailsEvent.DeleteClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(NotificationProfileDetailsState.Dialog.ConfirmDelete)
    coVerify(exactly = 0) { NotificationProfileDetailsRepository.deleteProfile(any()) }
  }

  @Test
  fun `confirming the delete closes the dialog and deletes the profile`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationProfileDetailsEvent.DeleteClicked)
    viewModel.onEvent(NotificationProfileDetailsEvent.DeleteConfirmed)

    assertThat(viewModel.state.value.dialog).isEqualTo(NotificationProfileDetailsState.Dialog.None)
    coVerify(exactly = 1) { NotificationProfileDetailsRepository.deleteProfile(PROFILE_ID) }
  }

  @Test
  fun `DialogDismissed closes the dialog without deleting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(NotificationProfileDetailsEvent.DeleteClicked)
    viewModel.onEvent(NotificationProfileDetailsEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(NotificationProfileDetailsState.Dialog.None)
    coVerify(exactly = 0) { NotificationProfileDetailsRepository.deleteProfile(any()) }
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(NotificationProfileDetailsEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(NotificationProfileDetailsAction.NavigateBack)
  }

  private fun createViewModel(): NotificationProfileDetailsViewModel {
    return NotificationProfileDetailsViewModel(profileId = PROFILE_ID)
  }

  private fun loaded(): NotificationProfileDetailsRepository.ProfileDetails {
    return NotificationProfileDetailsRepository.ProfileDetails(profile, listOf(alice), activeStatus = null)
  }

  private fun recipient(id: RecipientId): Recipient {
    return mockk<Recipient> {
      every { this@mockk.id } returns id
    }
  }

  private fun TestScope.collectActions(actions: Flow<NotificationProfileDetailsAction>): List<NotificationProfileDetailsAction> {
    val collected = mutableListOf<NotificationProfileDetailsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
