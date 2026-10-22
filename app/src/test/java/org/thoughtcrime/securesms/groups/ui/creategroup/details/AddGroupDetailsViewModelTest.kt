/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import org.signal.core.models.media.Media
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.util.RemoteConfig

@OptIn(ExperimentalCoroutinesApi::class)
class AddGroupDetailsViewModelTest {

  companion object {
    private val ALICE = registered(1)
    private val BOB = registered(2)
    private val SAME_GROUP = Recipient(id = RecipientId.from(10), isResolving = false, groupName = "Book club")

    private val AVATAR_BYTES = byteArrayOf(1, 2, 3)

    private fun registered(id: Long): Recipient {
      return Recipient(id = RecipientId.from(id), isResolving = false, registeredValue = RecipientTable.RegisteredState.REGISTERED)
    }
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @get:Rule
  val signalStore = MockSignalStoreRule()

  private val repository = mockk<AddGroupDetailsRepository>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { signalStore.settings.universalExpireTimer } returns 0
    mockkStatic(RemoteConfig::class)
    every { RemoteConfig.maxGroupNameGraphemeLength } returns 32
    coEvery { repository.resolveMembers(any()) } returns listOf(ALICE, BOB)
    coEvery { repository.getGroupsWithSameMembers(any()) } returns emptyList()
    coEvery { repository.loadAvatar(any()) } returns AVATAR_BYTES
    coEvery { repository.createGroup(any(), any(), any(), any()) } returns GroupCreateResult.Success(RecipientId.from(99), 5L, emptyList())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkStatic(RemoteConfig::class)
  }

  @Test
  fun `initial state resolves members and the default disappearing messages timer`() = runTest(testDispatcher) {
    every { signalStore.settings.universalExpireTimer } returns 3600

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.members).containsExactly(ALICE, BOB)
    assertThat(viewModel.state.value.disappearingMessagesTimer).isEqualTo(3600)
  }

  @Test
  fun `initial state includes groups with the same members`() = runTest(testDispatcher) {
    coEvery { repository.getGroupsWithSameMembers(setOf(ALICE.id, BOB.id)) } returns listOf(SAME_GROUP)

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.sameGroups).containsExactly(SAME_GROUP)
  }

  @Test
  fun `no members means no lookup for groups with the same members`() = runTest(testDispatcher) {
    coEvery { repository.resolveMembers(any()) } returns emptyList()

    createViewModel()

    coVerify(exactly = 0) { repository.getGroupsWithSameMembers(any()) }
  }

  @Test
  fun `NameChanged stores the truncated name and enables create`() = runTest(testDispatcher) {
    every { RemoteConfig.maxGroupNameGraphemeLength } returns 6
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.canCreate).isFalse()

    viewModel.onEvent(AddGroupDetailsEvent.NameChanged("A very long name"))

    assertThat(viewModel.state.value.name).isEqualTo("A very")
    assertThat(viewModel.state.value.canCreate).isTrue()
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AddGroupDetailsEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(AddGroupDetailsAction.NavigateBack)
  }

  @Test
  fun `AvatarClicked opens the picker on the previously selected media`() = runTest(testDispatcher) {
    val media = mockk<Media>()
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AddGroupDetailsEvent.AvatarClicked)
    viewModel.onEvent(AddGroupDetailsEvent.AvatarSelected(media))
    viewModel.onEvent(AddGroupDetailsEvent.AvatarClicked)

    assertThat(actions).containsExactly(
      AddGroupDetailsAction.NavigateToAvatarPicker(null),
      AddGroupDetailsAction.NavigateToAvatarPicker(media)
    )
  }

  @Test
  fun `AvatarSelected loads the avatar and AvatarCleared removes it`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AddGroupDetailsEvent.AvatarSelected(mockk()))
    assertThat(viewModel.state.value.avatar).isEqualTo(AVATAR_BYTES)

    viewModel.onEvent(AddGroupDetailsEvent.AvatarCleared)
    assertThat(viewModel.state.value.avatar).isNull()
  }

  @Test
  fun `a failed avatar load keeps the previous avatar`() = runTest(testDispatcher) {
    val previous = mockk<Media>()
    val broken = mockk<Media>()
    coEvery { repository.loadAvatar(broken) } returns null
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AddGroupDetailsEvent.AvatarSelected(previous))
    viewModel.onEvent(AddGroupDetailsEvent.AvatarSelected(broken))
    viewModel.onEvent(AddGroupDetailsEvent.AvatarClicked)

    assertThat(viewModel.state.value.avatar).isEqualTo(AVATAR_BYTES)
    assertThat(actions).containsExactly(AddGroupDetailsAction.NavigateToAvatarPicker(previous))
  }

  @Test
  fun `typing while the avatar loads updates the name`() = runTest(testDispatcher) {
    val avatarLoaded = CompletableDeferred<ByteArray?>()
    coEvery { repository.loadAvatar(any()) } coAnswers { avatarLoaded.await() }
    val viewModel = createViewModel()

    viewModel.onEvent(AddGroupDetailsEvent.AvatarSelected(mockk()))
    viewModel.onEvent(AddGroupDetailsEvent.NameChanged("Hikers"))

    assertThat(viewModel.state.value.name).isEqualTo("Hikers")

    avatarLoaded.complete(AVATAR_BYTES)

    assertThat(viewModel.state.value.avatar).isEqualTo(AVATAR_BYTES)
  }

  @Test
  fun `picking another avatar drops the load still in progress`() = runTest(testDispatcher) {
    val slow = mockk<Media>()
    val fast = mockk<Media>()
    val slowLoaded = CompletableDeferred<ByteArray?>()
    val fastBytes = byteArrayOf(4, 5, 6)
    coEvery { repository.loadAvatar(slow) } coAnswers { slowLoaded.await() }
    coEvery { repository.loadAvatar(fast) } returns fastBytes
    val viewModel = createViewModel()

    viewModel.onEvent(AddGroupDetailsEvent.AvatarSelected(slow))
    viewModel.onEvent(AddGroupDetailsEvent.AvatarSelected(fast))
    slowLoaded.complete(AVATAR_BYTES)

    assertThat(viewModel.state.value.avatar).isEqualTo(fastBytes)
  }

  @Test
  fun `removing a member drops the same members lookup still in progress`() = runTest(testDispatcher) {
    val initialLookup = CompletableDeferred<List<Recipient>>()
    coEvery { repository.getGroupsWithSameMembers(setOf(ALICE.id, BOB.id)) } coAnswers { initialLookup.await() }
    coEvery { repository.getGroupsWithSameMembers(setOf(ALICE.id)) } returns emptyList()
    val viewModel = createViewModel()

    viewModel.onEvent(AddGroupDetailsEvent.MemberClicked(BOB.id))
    viewModel.onEvent(AddGroupDetailsEvent.RemoveMemberConfirmed)
    initialLookup.complete(listOf(SAME_GROUP))

    assertThat(viewModel.state.value.sameGroups).isEmpty()
  }

  @Test
  fun `DisappearingMessagesClicked launches the picker with the current timer`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AddGroupDetailsEvent.DisappearingMessagesTimerSelected(86400))
    viewModel.onEvent(AddGroupDetailsEvent.DisappearingMessagesClicked)

    assertThat(viewModel.state.value.disappearingMessagesTimer).isEqualTo(86400)
    assertThat(actions).containsExactly(AddGroupDetailsAction.LaunchDisappearingMessagesPicker(86400))
  }

  @Test
  fun `MemberClicked asks for confirmation before removing`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AddGroupDetailsEvent.MemberClicked(BOB.id))

    assertThat(viewModel.state.value.dialog).isEqualTo(AddGroupDetailsState.Dialog.ConfirmRemoveMember(BOB))
    assertThat(viewModel.state.value.members).containsExactly(ALICE, BOB)
  }

  @Test
  fun `RemoveMemberConfirmed removes the member and refreshes groups with the same members`() = runTest(testDispatcher) {
    coEvery { repository.getGroupsWithSameMembers(setOf(ALICE.id)) } returns listOf(SAME_GROUP)
    val viewModel = createViewModel()

    viewModel.onEvent(AddGroupDetailsEvent.MemberClicked(BOB.id))
    viewModel.onEvent(AddGroupDetailsEvent.RemoveMemberConfirmed)

    assertThat(viewModel.state.value.members).containsExactly(ALICE)
    assertThat(viewModel.state.value.sameGroups).containsExactly(SAME_GROUP)
    assertThat(viewModel.state.value.dialog).isEqualTo(AddGroupDetailsState.Dialog.None)
  }

  @Test
  fun `RemoveMemberConfirmed without a confirmation dialog does nothing`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AddGroupDetailsEvent.RemoveMemberConfirmed)

    assertThat(viewModel.state.value.members).containsExactly(ALICE, BOB)
  }

  @Test
  fun `DialogDismissed keeps the member`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AddGroupDetailsEvent.MemberClicked(BOB.id))
    viewModel.onEvent(AddGroupDetailsEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(AddGroupDetailsState.Dialog.None)
    assertThat(viewModel.state.value.members).containsExactly(ALICE, BOB)
  }

  @Test
  fun `DiscardConfirmed opens the existing group`() = runTest(testDispatcher) {
    coEvery { repository.getGroupsWithSameMembers(any()) } returns listOf(SAME_GROUP)
    coEvery { repository.getOrCreateThreadId(SAME_GROUP) } returns 7L
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AddGroupDetailsEvent.SameGroupClicked(SAME_GROUP.id))
    assertThat(viewModel.state.value.dialog).isEqualTo(AddGroupDetailsState.Dialog.ConfirmDiscard(SAME_GROUP))
    assertThat(actions).isEmpty()

    viewModel.onEvent(AddGroupDetailsEvent.DiscardConfirmed)

    assertThat(actions).containsExactly(AddGroupDetailsAction.OpenConversation(SAME_GROUP.id, 7L))
    assertThat(viewModel.state.value.dialog).isEqualTo(AddGroupDetailsState.Dialog.None)
  }

  @Test
  fun `CreateClicked creates the group with the current details`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AddGroupDetailsEvent.NameChanged("Hikers"))
    viewModel.onEvent(AddGroupDetailsEvent.AvatarSelected(mockk()))
    viewModel.onEvent(AddGroupDetailsEvent.DisappearingMessagesTimerSelected(60))
    viewModel.onEvent(AddGroupDetailsEvent.CreateClicked)

    coVerify { repository.createGroup(setOf(ALICE.id, BOB.id), AVATAR_BYTES, "Hikers", 60) }
    assertThat(actions).containsExactly(AddGroupDetailsAction.GroupCreated(RecipientId.from(99), 5L, emptyList()))
    assertThat(viewModel.state.value.isCreating).isTrue()
  }

  @Test
  fun `NavigateBackClicked while creating still navigates back`() = runTest(testDispatcher) {
    val created = CompletableDeferred<GroupCreateResult>()
    coEvery { repository.createGroup(any(), any(), any(), any()) } coAnswers { created.await() }
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AddGroupDetailsEvent.NameChanged("Hikers"))
    viewModel.onEvent(AddGroupDetailsEvent.CreateClicked)
    viewModel.onEvent(AddGroupDetailsEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(AddGroupDetailsAction.NavigateBack)
  }

  @Test
  fun `CreateClicked twice creates the group once`() = runTest(testDispatcher) {
    val created = CompletableDeferred<GroupCreateResult>()
    coEvery { repository.createGroup(any(), any(), any(), any()) } coAnswers { created.await() }
    val viewModel = createViewModel()

    viewModel.onEvent(AddGroupDetailsEvent.NameChanged("Hikers"))
    viewModel.onEvent(AddGroupDetailsEvent.CreateClicked)
    viewModel.onEvent(AddGroupDetailsEvent.CreateClicked)
    created.complete(GroupCreateResult.Failure)

    coVerify(exactly = 1) { repository.createGroup(any(), any(), any(), any()) }
  }

  @Test
  fun `CreateClicked without a name does nothing`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(AddGroupDetailsEvent.CreateClicked)

    coVerify(exactly = 0) { repository.createGroup(any(), any(), any(), any()) }
  }

  @Test
  fun `a retryable failure tells the user to try again later`() = runTest(testDispatcher) {
    coEvery { repository.createGroup(any(), any(), any(), any()) } returns GroupCreateResult.RetryableFailure
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AddGroupDetailsEvent.NameChanged("Hikers"))
    viewModel.onEvent(AddGroupDetailsEvent.CreateClicked)

    assertThat(actions).containsExactly(AddGroupDetailsAction.ShowTryAgainLater)
    assertThat(viewModel.state.value.isCreating).isFalse()
  }

  @Test
  fun `a failure tells the user group creation failed`() = runTest(testDispatcher) {
    coEvery { repository.createGroup(any(), any(), any(), any()) } returns GroupCreateResult.Failure
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(AddGroupDetailsEvent.NameChanged("Hikers"))
    viewModel.onEvent(AddGroupDetailsEvent.CreateClicked)

    assertThat(actions).containsExactly(AddGroupDetailsAction.ShowGroupCreationFailed)
    assertThat(viewModel.state.value.isCreating).isFalse()
  }

  private fun createViewModel(): AddGroupDetailsViewModel {
    return AddGroupDetailsViewModel(recipientIds = listOf(ALICE.id, BOB.id), repository = repository)
  }

  private fun TestScope.collectActions(actions: Flow<AddGroupDetailsAction>): List<AddGroupDetailsAction> {
    val collected = mutableListOf<AddGroupDetailsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
