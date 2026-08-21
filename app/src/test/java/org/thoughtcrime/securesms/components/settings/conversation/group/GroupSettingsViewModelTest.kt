/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.group

import androidx.compose.ui.unit.IntRect
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.uicomponents.recentmediarail.RecentMediaRailEvents
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsAction
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsRepository
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsRepository.GroupDetails
import org.thoughtcrime.securesms.components.settings.conversation.GroupCapacityResult
import org.thoughtcrime.securesms.components.settings.conversation.group.GroupSettingsState.Dialog
import org.thoughtcrime.securesms.components.settings.conversation.shared.GroupMember
import org.thoughtcrime.securesms.components.settings.conversation.shared.LegacyGroupState
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabel
import org.thoughtcrime.securesms.groups.memberlabel.StyledMemberLabel
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupChangeResult
import org.thoughtcrime.securesms.groups.v2.GroupAddMembersResult
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val repository = mockk<ConversationSettingsRepository>(relaxUnitFun = true)
  private val detailsFlow = MutableStateFlow(groupDetails())

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { repository.isDeprecatedOrUnregistered() } returns false
    every { repository.isStarredMessagesEnabled() } returns false
    every { repository.isInternalRecipientDetailsEnabled() } returns false
    every { repository.isStoriesFeatureEnabled() } returns false
    every { repository.isAddToStoryAvailable() } returns true
    every { repository.isBlockable(any()) } returns true
    every { repository.observeGroupDetails(GROUP_ID) } returns detailsFlow
    every { repository.observeStoryViewState(GROUP_ID) } returns flowOf(StoryViewState.NONE)
    every { repository.observeCalls(any<Flow<Long>>(), any()) } returns flowOf(emptyList())
    coEvery { repository.getThreadId(GROUP_ID) } returns THREAD_ID
    coEvery { repository.isArchived(any()) } returns false
    coEvery { repository.getSharedMedia(any(), any()) } returns emptyList()
    coEvery { repository.getMemberLabels(any(), any()) } returns emptyMap()
    coEvery { repository.canSetOwnMemberLabel(any()) } returns false
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(callMessageIds: LongArray = longArrayOf()): GroupSettingsViewModel {
    return GroupSettingsViewModel(
      groupId = GROUP_ID,
      callMessageIds = callMessageIds,
      repository = repository
    )
  }

  private fun TestScope.collectActions(viewModel: GroupSettingsViewModel): List<ConversationSettingsAction> {
    val actions = mutableListOf<ConversationSettingsAction>()
    backgroundScope.launch { viewModel.actions.collect { actions += it } }
    return actions
  }

  @Test
  fun `group details populate the state`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    val state = viewModel.state.value
    assertEquals(GROUP_ID, state.groupId)
    assertEquals("Deep Space Nine", state.title)
    assertEquals("Bajoran space station", state.description)
    assertEquals("2 members", state.membershipCountDescription)
    assertTrue(state.isSelfAdmin)
    assertTrue(state.isActive)
    assertTrue(state.detailsLoaded)
    assertTrue(state.isLoaded)
  }

  @Test
  fun `group call bar offers video, mute, search, and stories`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    val callBar = viewModel.state.value.callBar
    assertTrue(callBar.isVideoAvailable)
    assertTrue(callBar.isMuteAvailable)
    assertTrue(callBar.isSearchAvailable)
    assertTrue(callBar.isAddToStoryAvailable)
    assertFalse(callBar.isAudioAvailable)
  }

  @Test
  fun `stories are unavailable when the feature is turned off`() = runTest(testDispatcher) {
    every { repository.isAddToStoryAvailable() } returns false

    val viewModel = createViewModel()

    assertFalse(viewModel.state.value.callBar.isAddToStoryAvailable)
  }

  @Test
  fun `message button replaces search when opened for a call`() = runTest(testDispatcher) {
    val viewModel = createViewModel(callMessageIds = longArrayOf(1L, 2L))

    assertTrue(viewModel.state.value.callBar.isMessageAvailable)
    assertFalse(viewModel.state.value.callBar.isSearchAvailable)
  }

  @Test
  fun `members are shown in full when there are six or fewer`() = runTest(testDispatcher) {
    detailsFlow.value = groupDetails(members = members(6))

    val viewModel = createViewModel()

    assertEquals(6, viewModel.state.value.members.size)
    assertFalse(viewModel.state.value.canShowMoreMembers)
  }

  @Test
  fun `members collapse to five when there are more than six`() = runTest(testDispatcher) {
    detailsFlow.value = groupDetails(members = members(7))

    val viewModel = createViewModel()

    val state = viewModel.state.value
    assertEquals(5, state.members.size)
    assertEquals(7, state.allMembers.size)
    assertTrue(state.canShowMoreMembers)
  }

  @Test
  fun `revealing all members expands the list`() = runTest(testDispatcher) {
    detailsFlow.value = groupDetails(members = members(7))
    val viewModel = createViewModel()

    viewModel.onEvent(GroupSettingsEvent.RevealAllMembersClicked)

    val state = viewModel.state.value
    assertEquals(7, state.members.size)
    assertTrue(state.membersExpanded)
    assertFalse(state.canShowMoreMembers)
  }

  @Test
  fun `an expanded member list stays expanded across group updates`() = runTest(testDispatcher) {
    detailsFlow.value = groupDetails(members = members(7))
    val viewModel = createViewModel()
    viewModel.onEvent(GroupSettingsEvent.RevealAllMembersClicked)

    detailsFlow.value = groupDetails(members = members(7), title = "Terok Nor")

    assertEquals(7, viewModel.state.value.members.size)
    assertFalse(viewModel.state.value.canShowMoreMembers)
  }

  @Test
  fun `member labels load whenever the membership changes`() = runTest(testDispatcher) {
    val label = StyledMemberLabel(MemberLabel(emoji = null, text = "Captain"), tintColor = 1)
    coEvery { repository.getMemberLabels(any(), any()) } returns mapOf(MEMBER_ID to label)
    coEvery { repository.canSetOwnMemberLabel(any()) } returns true

    val viewModel = createViewModel()

    assertEquals(label, viewModel.state.value.memberLabels[MEMBER_ID])
    assertTrue(viewModel.state.value.canSetOwnMemberLabel)
  }

  @Test
  fun `edit group click opens the group profile editor`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.EditGroupClicked)

    assertEquals(ConversationSettingsAction.EditGroupProfile(GROUP_ID), actions.single())
  }

  @Test
  fun `group description rows open the editor and the viewer`() = runTest(testDispatcher) {
    detailsFlow.value = groupDetails(descriptionShouldLinkify = true)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.EditGroupDescriptionClicked)
    viewModel.onEvent(GroupSettingsEvent.ViewGroupDescriptionClicked)

    assertEquals(
      listOf(
        ConversationSettingsAction.EditGroupDescription(GROUP_ID),
        ConversationSettingsAction.ShowGroupDescriptionDialog(GROUP_ID, shouldLinkify = true)
      ),
      actions
    )
  }

  @Test
  fun `legacy group rows open the explainer and the invite flow`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.LegacyGroupLearnMoreClicked)
    viewModel.onEvent(GroupSettingsEvent.LegacyGroupMmsWarningClicked)

    assertEquals(
      listOf(ConversationSettingsAction.ShowGroupsLearnMore, ConversationSettingsAction.ShowInviteFriends),
      actions
    )
  }

  @Test
  fun `member search click allows adding when the group is active and we have permission`() = runTest(testDispatcher) {
    detailsFlow.value = groupDetails(canAddMembers = true, groupLinkEnabled = true)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.MemberSearchClicked)

    assertEquals(ConversationSettingsAction.NavigateToMemberSearch(GROUP_ID, canAdd = true, hasGroupLink = true), actions.single())
  }

  @Test
  fun `member search click disallows adding to a terminated group`() = runTest(testDispatcher) {
    detailsFlow.value = groupDetails(canAddMembers = true, isTerminated = true)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.MemberSearchClicked)

    assertEquals(ConversationSettingsAction.NavigateToMemberSearch(GROUP_ID, canAdd = false, hasGroupLink = false), actions.single())
  }

  @Test
  fun `add members click opens the picker when the group has room`() = runTest(testDispatcher) {
    coEvery { repository.getGroupCapacity(GROUP_ID) } returns capacity(remaining = 10)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.AddMembersClicked)

    val action = actions.single() as ConversationSettingsAction.AddMembersToGroup
    assertEquals(GROUP_ID, action.groupId)
  }

  @Test
  fun `add members click warns when the group is full`() = runTest(testDispatcher) {
    coEvery { repository.getGroupCapacity(GROUP_ID) } returns capacity(remaining = 0)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.AddMembersClicked)

    assertEquals(ConversationSettingsAction.ShowGroupHardLimitDialog, actions.single())
  }

  @Test
  fun `adding members reports how many were added`() = runTest(testDispatcher) {
    val selected = listOf(RecipientId.from(77L))
    coEvery { repository.addMembers(GROUP_ID, selected) } returns GroupAddMembersResult.Success(2, emptyList())
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.AddMembersSelected(selected))

    assertEquals(ConversationSettingsAction.ShowMembersAdded(2), actions.single())
    assertEquals(Dialog.None, viewModel.state.value.dialog)
  }

  @Test
  fun `adding members reports who was invited`() = runTest(testDispatcher) {
    val selected = listOf(RecipientId.from(77L))
    val invited = listOf(mockk<Recipient>(relaxed = true))
    coEvery { repository.addMembers(GROUP_ID, selected) } returns GroupAddMembersResult.Success(0, invited)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.AddMembersSelected(selected))

    assertEquals(ConversationSettingsAction.ShowGroupInvitesSentDialog(invited), actions.single())
  }

  @Test
  fun `adding members surfaces the failure reason`() = runTest(testDispatcher) {
    val selected = listOf(RecipientId.from(77L))
    coEvery { repository.addMembers(GROUP_ID, selected) } returns GroupAddMembersResult.Failure(GroupChangeFailureReason.NETWORK)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.AddMembersSelected(selected))

    assertEquals(ConversationSettingsAction.ShowAddMembersError(GroupChangeFailureReason.NETWORK), actions.single())
  }

  @Test
  fun `member click opens the recipient sheet`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.MemberClicked(MEMBER_ID))

    assertEquals(ConversationSettingsAction.ShowRecipientBottomSheet(MEMBER_ID, GROUP_ID), actions.single())
  }

  @Test
  fun `clicking yourself opens the member label editor when you have no label yet`() = runTest(testDispatcher) {
    coEvery { repository.canSetOwnMemberLabel(any()) } returns true
    detailsFlow.value = groupDetails(members = listOf(GroupMember(recipient(SELF_MEMBER_ID, isSelf = true), isAdmin = false)))
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.MemberClicked(SELF_MEMBER_ID))

    assertEquals(ConversationSettingsAction.NavigateToMemberLabel(GROUP_ID), actions.single())
  }

  @Test
  fun `clicking yourself opens the recipient sheet once you already have a label`() = runTest(testDispatcher) {
    coEvery { repository.canSetOwnMemberLabel(any()) } returns true
    coEvery { repository.getMemberLabels(any(), any()) } returns mapOf(SELF_MEMBER_ID to StyledMemberLabel(MemberLabel(null, "Captain"), 1))
    detailsFlow.value = groupDetails(members = listOf(GroupMember(recipient(SELF_MEMBER_ID, isSelf = true), isAdmin = false)))
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.MemberClicked(SELF_MEMBER_ID))

    assertEquals(ConversationSettingsAction.ShowRecipientBottomSheet(SELF_MEMBER_ID, GROUP_ID), actions.single())
  }

  @Test
  fun `clicking yourself opens the recipient sheet when you cannot set a label`() = runTest(testDispatcher) {
    coEvery { repository.canSetOwnMemberLabel(any()) } returns false
    detailsFlow.value = groupDetails(members = listOf(GroupMember(recipient(SELF_MEMBER_ID, isSelf = true), isAdmin = false)))
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.MemberClicked(SELF_MEMBER_ID))

    assertEquals(ConversationSettingsAction.ShowRecipientBottomSheet(SELF_MEMBER_ID, GROUP_ID), actions.single())
  }

  @Test
  fun `member avatar click always opens the recipient sheet`() = runTest(testDispatcher) {
    coEvery { repository.canSetOwnMemberLabel(any()) } returns true
    detailsFlow.value = groupDetails(members = listOf(GroupMember(recipient(SELF_MEMBER_ID, isSelf = true), isAdmin = false)))
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.MemberAvatarClicked(SELF_MEMBER_ID))

    assertEquals(ConversationSettingsAction.ShowRecipientBottomSheet(SELF_MEMBER_ID, GROUP_ID), actions.single())
  }

  @Test
  fun `group management rows navigate to their destinations`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.GroupLinkClicked)
    viewModel.onEvent(GroupSettingsEvent.GroupMemberLabelClicked)
    viewModel.onEvent(GroupSettingsEvent.RequestsAndInvitesClicked)
    viewModel.onEvent(GroupSettingsEvent.PermissionsClicked)
    viewModel.onEvent(GroupSettingsEvent.LeaveGroupClicked)

    assertEquals(
      listOf(
        ConversationSettingsAction.NavigateToShareableGroupLink(GROUP_ID),
        ConversationSettingsAction.NavigateToMemberLabel(GROUP_ID),
        ConversationSettingsAction.OpenRequestsAndInvites(GROUP_ID.requireV2()),
        ConversationSettingsAction.NavigateToPermissions(GROUP_ID),
        ConversationSettingsAction.ShowLeaveGroupDialog(GROUP_ID)
      ),
      actions
    )
  }

  @Test
  fun `tapping the disabled member label row explains why it is disabled`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.GroupMemberLabelDisabledClicked)

    assertEquals(ConversationSettingsAction.ShowMemberLabelPermissionError, actions.single())
  }

  @Test
  fun `end group click carries the group title`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.EndGroupClicked)

    assertEquals(ConversationSettingsAction.ShowEndGroupDialog(GROUP_ID.requireV2(), "Deep Space Nine"), actions.single())
  }

  @Test
  fun `video call click warns non-admins of an announcement group instead of starting a call`() = runTest(testDispatcher) {
    detailsFlow.value = groupDetails(isAnnouncementGroup = true, isSelfAdmin = false)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.VideoCallClicked)

    assertEquals(emptyList<ConversationSettingsAction>(), actions)
    assertEquals(Dialog.CannotStartGroupCall, viewModel.state.value.dialog)
  }

  @Test
  fun `video call click starts the call for an admin of an announcement group`() = runTest(testDispatcher) {
    detailsFlow.value = groupDetails(isAnnouncementGroup = true, isSelfAdmin = true)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.VideoCallClicked)

    assertEquals(ConversationSettingsAction.StartVideoCall::class, actions.single()::class)
    assertEquals(Dialog.None, viewModel.state.value.dialog)
  }

  @Test
  fun `add to story click warns non-admins of an announcement group`() = runTest(testDispatcher) {
    detailsFlow.value = groupDetails(isAnnouncementGroup = true, isSelfAdmin = false)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.AddToStoryClicked)

    assertEquals(emptyList<ConversationSettingsAction>(), actions)
    assertEquals(Dialog.CannotAddToGroupStory, viewModel.state.value.dialog)
  }

  @Test
  fun `add to story click adds to the story for an admin`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.AddToStoryClicked)

    assertEquals(ConversationSettingsAction.AddToGroupStory(GROUP_RECIPIENT_ID), actions.single())
  }

  @Test
  fun `mute click shows the mute menu, then selecting a duration mutes the group`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(GroupSettingsEvent.MuteClicked)
    assertEquals(Dialog.MuteMenu, viewModel.state.value.dialog)

    viewModel.onEvent(GroupSettingsEvent.MuteDurationSelected(4321L))

    coVerify(exactly = 1) { repository.setMuteUntil(GROUP_ID, 4321L) }
    assertEquals(Dialog.None, viewModel.state.value.dialog)
  }

  @Test
  fun `mute click shows the unmute dialog when the group is already muted`() = runTest(testDispatcher) {
    detailsFlow.value = groupDetails(isMuted = true)
    val viewModel = createViewModel()

    viewModel.onEvent(GroupSettingsEvent.MuteClicked)

    assertEquals(Dialog.Unmute, viewModel.state.value.dialog)
  }

  @Test
  fun `unmuting clears the group's mute until timestamp`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(GroupSettingsEvent.UnmuteConfirmed)

    coVerify(exactly = 1) { repository.setMuteUntil(GROUP_ID, 0L) }
  }

  @Test
  fun `custom mute time click closes the menu and asks the host for the time picker`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)
    viewModel.onEvent(GroupSettingsEvent.MuteClicked)

    viewModel.onEvent(GroupSettingsEvent.MuteUntilCustomTimeClicked)

    assertEquals(Dialog.None, viewModel.state.value.dialog)
    assertEquals(ConversationSettingsAction.ShowMuteUntilTimePicker, actions.single())
  }

  @Test
  fun `blocking the group succeeds quietly`() = runTest(testDispatcher) {
    coEvery { repository.block(GROUP_ID) } returns GroupChangeResult.SUCCESS
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.BlockConfirmed)

    assertEquals(emptyList<ConversationSettingsAction>(), actions)
  }

  @Test
  fun `blocking the group surfaces the failure reason`() = runTest(testDispatcher) {
    coEvery { repository.block(GROUP_ID) } returns GroupChangeResult.failure(GroupChangeFailureReason.NO_RIGHTS)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.BlockConfirmed)

    assertEquals(ConversationSettingsAction.ShowBlockError(GroupChangeFailureReason.NO_RIGHTS), actions.single())
  }

  @Test
  fun `unblocking the group calls through to the repository`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(GroupSettingsEvent.UnblockConfirmed)

    coVerify(exactly = 1) { repository.unblock(GROUP_ID) }
  }

  @Test
  fun `archived state comes from the thread`() = runTest(testDispatcher) {
    coEvery { repository.isArchived(any()) } returns true

    val viewModel = createViewModel()

    assertTrue(viewModel.state.value.isArchived)
  }

  @Test
  fun `archive chat click archives and leaves the screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.ArchiveChatClicked)

    coVerify(exactly = 1) { repository.setArchived(THREAD_ID, true) }
    assertTrue(viewModel.state.value.isArchived)
    assertEquals(ConversationSettingsAction.GoToConversationList, actions.single())
  }

  @Test
  fun `archive chat click unarchives without leaving the screen`() = runTest(testDispatcher) {
    coEvery { repository.isArchived(any()) } returns true
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.ArchiveChatClicked)

    coVerify(exactly = 1) { repository.setArchived(THREAD_ID, false) }
    assertFalse(viewModel.state.value.isArchived)
    assertEquals(emptyList<ConversationSettingsAction>(), actions)
  }

  @Test
  fun `delete chat click deletes, closes the progress dialog, and leaves the screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.DeleteChatClicked)

    coVerify(exactly = 1) { repository.deleteChat(THREAD_ID) }
    assertEquals(Dialog.None, viewModel.state.value.dialog)
    assertEquals(ConversationSettingsAction.GoToConversationList, actions.single())
  }

  @Test
  fun `see all shared media click opens the media overview for the thread`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.MediaRailEvent(RecentMediaRailEvents.SeeAllClicked))

    assertEquals(ConversationSettingsAction.ShowMediaOverview(THREAD_ID), actions.single())
  }

  @Test
  fun `shared media click reports the media is not sent yet when there is no attachment`() = runTest(testDispatcher) {
    coEvery { repository.getSharedMedia(any(), any()) } returns listOf(mediaRecord())
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(GroupSettingsEvent.MediaRailEvent(RecentMediaRailEvents.ItemClicked(index = 0, bounds = IntRect.Zero, leftToRight = true)))

    assertEquals(ConversationSettingsAction.ShowMediaNotSentYet, actions.single())
  }

  private companion object {
    val GROUP_ID: GroupId.V2 = GroupId.v2(GroupMasterKey(ByteArray(GroupMasterKey.SIZE) { 1 }))
    val GROUP_RECIPIENT_ID: RecipientId = RecipientId.from(1L)
    val MEMBER_ID: RecipientId = RecipientId.from(10L)
    val SELF_MEMBER_ID: RecipientId = RecipientId.from(11L)
    const val THREAD_ID = 5L

    fun mediaRecord(): MediaTable.MediaRecord {
      return MediaTable.MediaRecord(
        attachment = null,
        recipientId = MEMBER_ID,
        threadRecipientId = GROUP_RECIPIENT_ID,
        threadId = THREAD_ID,
        messageId = 4L,
        date = 1000L,
        isOutgoing = false
      )
    }

    fun groupDetails(
      title: String = "Deep Space Nine",
      description: String? = "Bajoran space station",
      descriptionShouldLinkify: Boolean = false,
      members: List<GroupMember> = members(2),
      isSelfAdmin: Boolean = true,
      canEditGroupAttributes: Boolean = true,
      canAddMembers: Boolean = true,
      isActive: Boolean = true,
      isTerminated: Boolean = false,
      isAnnouncementGroup: Boolean = false,
      groupLinkEnabled: Boolean = false,
      isMuted: Boolean = false
    ): GroupDetails {
      return GroupDetails(
        recipient = groupRecipient(isMuted),
        title = title,
        description = description,
        descriptionShouldLinkify = descriptionShouldLinkify,
        members = members,
        isSelfAdmin = isSelfAdmin,
        canEditGroupAttributes = canEditGroupAttributes,
        canAddMembers = canAddMembers,
        isActive = isActive,
        isTerminated = isTerminated,
        isAnnouncementGroup = isAnnouncementGroup,
        groupLinkEnabled = groupLinkEnabled,
        membershipCountDescription = "${members.size} members",
        legacyGroupState = LegacyGroupState.NONE
      )
    }

    fun groupRecipient(isMuted: Boolean = false): Recipient {
      return mockk<Recipient>(relaxed = true) {
        every { id } returns GROUP_RECIPIENT_ID
        every { isPushV2Group } returns true
        every { isPushGroup } returns true
        every { isGroup } returns true
        every { isIndividual } returns false
        every { isSelf } returns false
        every { isBlocked } returns false
        every { isActiveGroup } returns true
        every { isReleaseNotes } returns false
        every { this@mockk.isMuted } returns isMuted
        every { expiresInSeconds } returns 0
      }
    }

    fun members(count: Int): List<GroupMember> {
      return (0 until count).map { index ->
        GroupMember(recipient(RecipientId.from(10L + index), isSelf = false), isAdmin = index == 0)
      }
    }

    fun recipient(recipientId: RecipientId, isSelf: Boolean): Recipient {
      return mockk<Recipient>(relaxed = true) {
        every { id } returns recipientId
        every { this@mockk.isSelf } returns isSelf
      }
    }

    /** A real capacity result, so that the remaining/limit numbers stay consistent with each other. */
    fun capacity(remaining: Int, memberCount: Int = 2): GroupCapacityResult {
      val members = (0 until memberCount).map { RecipientId.from(100L + it) }

      return GroupCapacityResult(
        SELF_MEMBER_ID,
        members,
        SelectionLimits(members.size + remaining, members.size + remaining),
        false
      )
    }
  }
}
