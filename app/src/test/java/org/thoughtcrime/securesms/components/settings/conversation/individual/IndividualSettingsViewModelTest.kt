/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.individual

import androidx.compose.ui.unit.IntRect
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
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
import org.signal.core.models.database.AttachmentId
import org.signal.uicomponents.recentmediarail.RecentMediaRailEvents
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsAction
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsKind
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsRepository
import org.thoughtcrime.securesms.components.settings.conversation.individual.IndividualSettingsState.Dialog
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupChangeResult
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import org.signal.core.util.Result as CoreResult

@OptIn(ExperimentalCoroutinesApi::class)
class IndividualSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val repository = mockk<ConversationSettingsRepository>(relaxUnitFun = true)
  private val recipientFlow = MutableStateFlow(individual())

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { repository.isDeprecatedOrUnregistered() } returns false
    every { repository.isStarredMessagesEnabled() } returns false
    every { repository.isInternalRecipientDetailsEnabled() } returns false
    every { repository.isStoriesFeatureEnabled() } returns false
    every { repository.isBlockable(any()) } returns true
    every { repository.observeRecipient(RECIPIENT_ID) } returns recipientFlow
    every { repository.observeStoryViewState(RECIPIENT_ID) } returns flowOf(StoryViewState.NONE)
    every { repository.observeGroupsInCommon(RECIPIENT_ID) } returns flowOf(emptyList())
    every { repository.observeCalls(any<Flow<Long>>(), any()) } returns flowOf(emptyList())
    coEvery { repository.getThreadId(RECIPIENT_ID) } returns THREAD_ID
    coEvery { repository.hasGroups() } returns true
    coEvery { repository.getIdentity(RECIPIENT_ID) } returns null
    coEvery { repository.getSharedMedia(any(), any()) } returns emptyList()
    coEvery { repository.getGroupMembership(RECIPIENT_ID) } returns emptyList()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(
    callMessageIds: LongArray = longArrayOf(),
    kind: ConversationSettingsKind = ConversationSettingsKind.INDIVIDUAL,
    sharedMedia: List<MediaTable.MediaRecord> = emptyList()
  ): IndividualSettingsViewModel {
    coEvery { repository.getSharedMedia(any(), any()) } returns sharedMedia

    return IndividualSettingsViewModel(
      recipientId = RECIPIENT_ID,
      kind = kind,
      callMessageIds = callMessageIds,
      repository = repository
    )
  }

  private fun TestScope.collectActions(viewModel: IndividualSettingsViewModel): List<ConversationSettingsAction> {
    val actions = mutableListOf<ConversationSettingsAction>()
    backgroundScope.launch { viewModel.actions.collect { actions += it } }
    return actions
  }

  @Test
  fun `recipient updates populate the call bar`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    val callBar = viewModel.state.value.callBar
    assertTrue(callBar.isVideoAvailable)
    assertTrue(callBar.isAudioAvailable)
    assertTrue(callBar.isAudioSecure)
    assertTrue(callBar.isMuteAvailable)
    assertTrue(callBar.isSearchAvailable)
    assertFalse(callBar.isMessageAvailable)
    assertFalse(callBar.isAddToStoryAvailable)
  }

  @Test
  fun `message button replaces search when opened for a call`() = runTest(testDispatcher) {
    val viewModel = createViewModel(callMessageIds = longArrayOf(1L, 2L))

    assertTrue(viewModel.state.value.callBar.isMessageAvailable)
    assertFalse(viewModel.state.value.callBar.isSearchAvailable)
  }

  @Test
  fun `blocked recipient cannot call or be called`() = runTest(testDispatcher) {
    recipientFlow.value = individual(isBlocked = true)

    val viewModel = createViewModel()

    assertFalse(viewModel.state.value.callBar.isVideoAvailable)
    assertFalse(viewModel.state.value.callBar.isAudioAvailable)
  }

  @Test
  fun `contact link state is add when the recipient has a visible phone number`() = runTest(testDispatcher) {
    recipientFlow.value = individual(hasE164 = true, shouldShowE164 = true)

    val viewModel = createViewModel()

    assertEquals(ContactLinkState.ADD, viewModel.state.value.contactLinkState)
  }

  @Test
  fun `contact link state is open for a system contact`() = runTest(testDispatcher) {
    recipientFlow.value = individual(isSystemContact = true)

    val viewModel = createViewModel()

    assertEquals(ContactLinkState.OPEN, viewModel.state.value.contactLinkState)
  }

  @Test
  fun `contact link state is none when the recipient is blocked`() = runTest(testDispatcher) {
    recipientFlow.value = individual(isBlocked = true, isSystemContact = true)

    val viewModel = createViewModel()

    assertEquals(ContactLinkState.NONE, viewModel.state.value.contactLinkState)
  }

  @Test
  fun `disappearing messages lifespan comes from the recipient`() = runTest(testDispatcher) {
    recipientFlow.value = individual(expiresInSeconds = 3600)

    val viewModel = createViewModel()

    assertEquals(3600, viewModel.state.value.disappearingMessagesLifespan)
  }

  @Test
  fun `thread id and shared media load on start`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertEquals(THREAD_ID, viewModel.state.value.threadId)
    assertTrue(viewModel.state.value.mediaRail.loaded)
    assertTrue(viewModel.state.value.isLoaded)
  }

  @Test
  fun `identity record is loaded on start`() = runTest(testDispatcher) {
    val identityRecord = mockk<IdentityRecord>()
    coEvery { repository.getIdentity(RECIPIENT_ID) } returns identityRecord

    val viewModel = createViewModel()

    assertEquals(identityRecord, viewModel.state.value.identityRecord)
  }

  @Test
  fun `groups in common are shown in full when there are six or fewer`() = runTest(testDispatcher) {
    every { repository.observeGroupsInCommon(RECIPIENT_ID) } returns flowOf(groups(6))

    val viewModel = createViewModel()

    assertEquals(6, viewModel.state.value.groupsInCommon.size)
    assertFalse(viewModel.state.value.canShowMoreGroupsInCommon)
  }

  @Test
  fun `groups in common collapse to five when there are more than six`() = runTest(testDispatcher) {
    every { repository.observeGroupsInCommon(RECIPIENT_ID) } returns flowOf(groups(7))

    val viewModel = createViewModel()

    val state = viewModel.state.value
    assertEquals(5, state.groupsInCommon.size)
    assertEquals(7, state.allGroupsInCommon.size)
    assertTrue(state.canShowMoreGroupsInCommon)
  }

  @Test
  fun `revealing all groups in common expands the list`() = runTest(testDispatcher) {
    every { repository.observeGroupsInCommon(RECIPIENT_ID) } returns flowOf(groups(7))
    val viewModel = createViewModel()

    viewModel.onEvent(IndividualSettingsEvent.RevealAllGroupsInCommonClicked)

    val state = viewModel.state.value
    assertEquals(7, state.groupsInCommon.size)
    assertTrue(state.groupsInCommonExpanded)
    assertFalse(state.canShowMoreGroupsInCommon)
  }

  @Test
  fun `headline click opens the about sheet`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.HeadlineClicked)

    assertEquals(ConversationSettingsAction.ShowAboutSheet::class, actions.single()::class)
  }

  @Test
  fun `avatar click shows the story dialog when stories are enabled and the recipient has a story`() = runTest(testDispatcher) {
    every { repository.isStoriesFeatureEnabled() } returns true
    every { repository.observeStoryViewState(RECIPIENT_ID) } returns flowOf(StoryViewState.UNVIEWED)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.AvatarClicked)

    assertEquals(ConversationSettingsAction.ShowStoryOrAvatarDialog::class, actions.single()::class)
  }

  @Test
  fun `avatar click shows the avatar preview when there is no story`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.AvatarClicked)

    assertEquals(ConversationSettingsAction.ShowAvatarPreview(RECIPIENT_ID), actions.single())
  }

  @Test
  fun `badge click opens the badge sheet`() = runTest(testDispatcher) {
    val badge = mockk<Badge>()
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.BadgeClicked(badge))

    assertEquals(ConversationSettingsAction.ShowBadgeSheet(RECIPIENT_ID, badge), actions.single())
  }

  @Test
  fun `message and search clicks open the conversation`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.MessageClicked)
    viewModel.onEvent(IndividualSettingsEvent.SearchClicked)

    assertEquals(
      listOf(
        ConversationSettingsAction.OpenConversation(RECIPIENT_ID, THREAD_ID, withSearchOpen = false),
        ConversationSettingsAction.OpenConversation(RECIPIENT_ID, THREAD_ID, withSearchOpen = true)
      ),
      actions
    )
  }

  @Test
  fun `call buttons start the call`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.VideoCallClicked)
    viewModel.onEvent(IndividualSettingsEvent.AudioCallClicked)

    assertEquals(ConversationSettingsAction.StartVideoCall::class, actions[0]::class)
    assertEquals(ConversationSettingsAction.StartAudioCall::class, actions[1]::class)
  }

  @Test
  fun `disappearing messages click carries the current lifespan`() = runTest(testDispatcher) {
    recipientFlow.value = individual(expiresInSeconds = 3600)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.DisappearingMessagesClicked)

    assertEquals(ConversationSettingsAction.NavigateToDisappearingMessages(RECIPIENT_ID, 3600), actions.single())
  }

  @Test
  fun `nickname, wallpaper, and starred messages rows navigate`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.NicknameClicked)
    viewModel.onEvent(IndividualSettingsEvent.ChatColorAndWallpaperClicked)
    viewModel.onEvent(IndividualSettingsEvent.StarredMessagesClicked)

    assertEquals(
      listOf(
        ConversationSettingsAction.EditNickname(RECIPIENT_ID),
        ConversationSettingsAction.OpenChatWallpaper(RECIPIENT_ID),
        ConversationSettingsAction.OpenStarredMessages(THREAD_ID)
      ),
      actions
    )
  }

  @Test
  fun `sounds and notifications click navigates`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.SoundsAndNotificationsClicked)

    assertEquals(ConversationSettingsAction.NavigateToSoundsAndNotifications(RECIPIENT_ID), actions.single())
  }

  @Test
  fun `contact rows carry the recipient`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.ContactDetailsClicked)
    viewModel.onEvent(IndividualSettingsEvent.AddAsContactClicked)

    assertEquals(ConversationSettingsAction.ViewContact::class, actions[0]::class)
    assertEquals(ConversationSettingsAction.AddContact::class, actions[1]::class)
  }

  @Test
  fun `view safety number click carries the identity record`() = runTest(testDispatcher) {
    val identityRecord = mockk<IdentityRecord>()
    coEvery { repository.getIdentity(RECIPIENT_ID) } returns identityRecord
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.ViewSafetyNumberClicked)

    assertEquals(ConversationSettingsAction.ShowSafetyNumber(identityRecord), actions.single())
  }

  @Test
  fun `internal details click navigates to internal details`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.InternalDetailsClicked)

    assertEquals(ConversationSettingsAction.NavigateToInternalDetails(RECIPIENT_ID), actions.single())
  }

  @Test
  fun `see all shared media click opens the media overview for the thread`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.MediaRailEvent(RecentMediaRailEvents.SeeAllClicked))

    assertEquals(ConversationSettingsAction.ShowMediaOverview(THREAD_ID), actions.single())
  }

  @Test
  fun `shared media click shows the preview for a downloaded attachment`() = runTest(testDispatcher) {
    val record = mediaRecord(attachment(transferState = AttachmentTable.TRANSFER_PROGRESS_DONE, hasUri = true))
    val viewModel = createViewModel(sharedMedia = listOf(record))
    val actions = collectActions(viewModel)

    viewModel.onEvent(railItemClicked())

    assertEquals(ConversationSettingsAction.ShowMediaPreview(record, true, RAIL_ITEM_BOUNDS), actions.single())
  }

  @Test
  fun `shared media click downloads offloaded media that has no local file`() = runTest(testDispatcher) {
    val record = mediaRecord(attachment(transferState = AttachmentTable.TRANSFER_RESTORE_OFFLOADED, hasUri = false))
    val viewModel = createViewModel(sharedMedia = listOf(record))
    val actions = collectActions(viewModel)

    viewModel.onEvent(railItemClicked())

    assertEquals(ConversationSettingsAction.DownloadMedia(record), actions.single())
  }

  @Test
  fun `shared media click reports the media is not sent yet when there is no attachment`() = runTest(testDispatcher) {
    val viewModel = createViewModel(sharedMedia = listOf(mediaRecord(null)))
    val actions = collectActions(viewModel)

    viewModel.onEvent(railItemClicked())

    assertEquals(ConversationSettingsAction.ShowMediaNotSentYet, actions.single())
  }

  @Test
  fun `shared media click reports the media is not sent yet when it is still in flight`() = runTest(testDispatcher) {
    val record = mediaRecord(attachment(transferState = AttachmentTable.TRANSFER_PROGRESS_STARTED, hasUri = true))
    val viewModel = createViewModel(sharedMedia = listOf(record))
    val actions = collectActions(viewModel)

    viewModel.onEvent(railItemClicked())

    assertEquals(ConversationSettingsAction.ShowMediaNotSentYet, actions.single())
  }

  @Test
  fun `shared media click is ignored when the rail no longer has that item`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(railItemClicked())

    assertTrue(actions.isEmpty())
  }

  @Test
  fun `add to a group click looks up the current group membership`() = runTest(testDispatcher) {
    val membership = listOf(RecipientId.from(50L))
    coEvery { repository.getGroupMembership(RECIPIENT_ID) } returns membership
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.AddToAGroupClicked)

    assertEquals(ConversationSettingsAction.AddToAGroup(RECIPIENT_ID, membership), actions.single())
  }

  @Test
  fun `group in common click opens that group's conversation`() = runTest(testDispatcher) {
    val group = groups(1).first()
    every { repository.observeGroupsInCommon(RECIPIENT_ID) } returns flowOf(listOf(group))
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.GroupInCommonClicked(group.id))

    assertEquals(ConversationSettingsAction.OpenGroupConversation(group), actions.single())
  }

  @Test
  fun `group in common click for an unknown group does nothing`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.GroupInCommonClicked(RecipientId.from(999L)))

    assertEquals(emptyList<ConversationSettingsAction>(), actions)
  }

  @Test
  fun `mute click shows the mute menu, then selecting a duration mutes the recipient`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(IndividualSettingsEvent.MuteClicked)
    assertEquals(Dialog.MuteMenu, viewModel.state.value.dialog)

    viewModel.onEvent(IndividualSettingsEvent.MuteDurationSelected(1234L))

    coVerify(exactly = 1) { repository.setMuteUntil(RECIPIENT_ID, 1234L) }
    assertEquals(Dialog.None, viewModel.state.value.dialog)
  }

  @Test
  fun `mute click shows the unmute dialog when the chat is already muted`() = runTest(testDispatcher) {
    recipientFlow.value = individual(isMuted = true)
    val viewModel = createViewModel()

    viewModel.onEvent(IndividualSettingsEvent.MuteClicked)

    assertEquals(Dialog.Unmute, viewModel.state.value.dialog)
  }

  @Test
  fun `unmuting clears the mute until timestamp`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(IndividualSettingsEvent.UnmuteConfirmed)

    coVerify(exactly = 1) { repository.setMuteUntil(RECIPIENT_ID, 0L) }
    assertEquals(Dialog.None, viewModel.state.value.dialog)
  }

  @Test
  fun `custom mute time click closes the menu and asks the host for the time picker`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)
    viewModel.onEvent(IndividualSettingsEvent.MuteClicked)

    viewModel.onEvent(IndividualSettingsEvent.MuteUntilCustomTimeClicked)

    assertEquals(Dialog.None, viewModel.state.value.dialog)
    assertEquals(ConversationSettingsAction.ShowMuteUntilTimePicker, actions.single())
  }

  @Test
  fun `block click asks to block an unblocked recipient`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.BlockClicked)

    assertEquals(ConversationSettingsAction.ShowBlockDialog::class, actions.single()::class)
  }

  @Test
  fun `block click asks to unblock a blocked recipient`() = runTest(testDispatcher) {
    recipientFlow.value = individual(isBlocked = true)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.BlockClicked)

    assertEquals(ConversationSettingsAction.ShowUnblockDialog::class, actions.single()::class)
  }

  @Test
  fun `blocking succeeds quietly`() = runTest(testDispatcher) {
    coEvery { repository.block(RECIPIENT_ID) } returns GroupChangeResult.SUCCESS
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.BlockConfirmed)

    assertEquals(emptyList<ConversationSettingsAction>(), actions)
  }

  @Test
  fun `blocking surfaces the failure reason`() = runTest(testDispatcher) {
    coEvery { repository.block(RECIPIENT_ID) } returns GroupChangeResult.failure(GroupChangeFailureReason.NETWORK)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.BlockConfirmed)

    assertEquals(ConversationSettingsAction.ShowBlockError(GroupChangeFailureReason.NETWORK), actions.single())
  }

  @Test
  fun `unblocking calls through to the repository`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(IndividualSettingsEvent.UnblockConfirmed)

    coVerify(exactly = 1) { repository.unblock(RECIPIENT_ID) }
  }

  @Test
  fun `report spam click offers blocking when the recipient is not already blocked`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.ReportSpamClicked)

    assertEquals(true, (actions.single() as ConversationSettingsAction.ShowReportSpamDialog).canBlock)
  }

  @Test
  fun `report spam click does not offer blocking when the recipient is already blocked`() = runTest(testDispatcher) {
    recipientFlow.value = individual(isBlocked = true)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.ReportSpamClicked)

    assertEquals(false, (actions.single() as ConversationSettingsAction.ShowReportSpamDialog).canBlock)
  }

  @Test
  fun `report spam confirmed reports and leaves the screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.ReportSpamConfirmed)

    coVerify(exactly = 1) { repository.reportSpam(RECIPIENT_ID, THREAD_ID) }
    assertEquals(
      listOf(ConversationSettingsAction.ShowSpamReported, ConversationSettingsAction.GoToConversationList),
      actions
    )
  }

  @Test
  fun `report spam confirmed does nothing without a thread`() = runTest(testDispatcher) {
    coEvery { repository.getThreadId(RECIPIENT_ID) } returns -1L
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.ReportSpamConfirmed)

    coVerify(exactly = 0) { repository.reportSpam(any(), any()) }
    assertEquals(emptyList<ConversationSettingsAction>(), actions)
  }

  @Test
  fun `block and report spam confirmed reports and leaves the screen on success`() = runTest(testDispatcher) {
    coEvery { repository.blockAndReportSpam(RECIPIENT_ID, THREAD_ID) } returns CoreResult.success(Unit)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.BlockAndReportSpamConfirmed)

    assertEquals(
      listOf(ConversationSettingsAction.ShowSpamReportedAndBlocked, ConversationSettingsAction.GoToConversationList),
      actions
    )
  }

  @Test
  fun `block and report spam confirmed surfaces the failure reason`() = runTest(testDispatcher) {
    coEvery { repository.blockAndReportSpam(RECIPIENT_ID, THREAD_ID) } returns CoreResult.failure(GroupChangeFailureReason.NETWORK)
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.BlockAndReportSpamConfirmed)

    assertEquals(ConversationSettingsAction.ShowBlockError(GroupChangeFailureReason.NETWORK), actions.single())
  }

  @Test
  fun `dialog dismissed clears the dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.onEvent(IndividualSettingsEvent.MuteClicked)

    viewModel.onEvent(IndividualSettingsEvent.DialogDismissed)

    assertEquals(Dialog.None, viewModel.state.value.dialog)
  }

  @Test
  fun `recipient refresh requests a contact discovery refresh`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(IndividualSettingsEvent.RecipientRefreshRequested)

    verify(exactly = 1) { repository.refreshRecipient(RECIPIENT_ID) }
  }

  @Test
  fun `note to self can only be searched -- there is nobody to call, mute, or block`() = runTest(testDispatcher) {
    recipientFlow.value = individual(isSelf = true)

    val viewModel = createViewModel(kind = ConversationSettingsKind.NOTE_TO_SELF)

    val callBar = viewModel.state.value.callBar
    assertTrue(callBar.isSearchAvailable)
    assertFalse(callBar.isMuteAvailable)
    assertFalse(callBar.isVideoAvailable)
    assertFalse(callBar.isAudioAvailable)
    assertFalse(viewModel.state.value.canModifyBlockedState)
  }

  @Test
  fun `the release notes chat can be muted and blocked but not called`() = runTest(testDispatcher) {
    recipientFlow.value = individual(isReleaseNotes = true)

    val viewModel = createViewModel(kind = ConversationSettingsKind.RELEASE_NOTES)

    val callBar = viewModel.state.value.callBar
    assertTrue(callBar.isSearchAvailable)
    assertTrue(callBar.isMuteAvailable)
    assertFalse(callBar.isVideoAvailable)
    assertFalse(callBar.isAudioAvailable)
    assertTrue(viewModel.state.value.canModifyBlockedState)
  }

  @Test
  fun `note to self has no groups in common or safety number to load`() = runTest(testDispatcher) {
    recipientFlow.value = individual(isSelf = true)

    createViewModel(kind = ConversationSettingsKind.NOTE_TO_SELF)

    verify(exactly = 0) { repository.observeGroupsInCommon(any()) }
    coVerify(exactly = 0) { repository.getIdentity(any()) }
  }

  @Test
  fun `the release notes chat has no groups in common or safety number to load`() = runTest(testDispatcher) {
    recipientFlow.value = individual(isReleaseNotes = true)

    createViewModel(kind = ConversationSettingsKind.RELEASE_NOTES)

    verify(exactly = 0) { repository.observeGroupsInCommon(any()) }
    coVerify(exactly = 0) { repository.getIdentity(any()) }
  }

  @Test
  fun `note to self does not offer a contact link`() = runTest(testDispatcher) {
    recipientFlow.value = individual(isSelf = true, isSystemContact = true)

    val viewModel = createViewModel(kind = ConversationSettingsKind.NOTE_TO_SELF)

    assertEquals(ContactLinkState.NONE, viewModel.state.value.contactLinkState)
  }

  @Test
  fun `the release notes chat does not offer a contact link`() = runTest(testDispatcher) {
    recipientFlow.value = individual(isReleaseNotes = true, isSystemContact = true)

    val viewModel = createViewModel(kind = ConversationSettingsKind.RELEASE_NOTES)

    assertEquals(ContactLinkState.NONE, viewModel.state.value.contactLinkState)
  }

  @Test
  fun `release notes help rows open their destinations`() = runTest(testDispatcher) {
    recipientFlow.value = individual(isReleaseNotes = true)
    val viewModel = createViewModel(kind = ConversationSettingsKind.RELEASE_NOTES)
    val actions = collectActions(viewModel)

    viewModel.onEvent(IndividualSettingsEvent.SupportCenterClicked)
    viewModel.onEvent(IndividualSettingsEvent.ContactUsClicked)
    viewModel.onEvent(IndividualSettingsEvent.DonateClicked)

    assertEquals(
      listOf(
        ConversationSettingsAction.OpenSupportCenter,
        ConversationSettingsAction.OpenContactUs,
        ConversationSettingsAction.OpenDonate
      ),
      actions
    )
  }

  private companion object {
    val RECIPIENT_ID: RecipientId = RecipientId.from(1L)
    const val THREAD_ID = 5L
    val RAIL_ITEM_BOUNDS = IntRect(left = 16, top = 100, right = 96, bottom = 180)

    fun individual(
      isBlocked: Boolean = false,
      isSelf: Boolean = false,
      isReleaseNotes: Boolean = false,
      isSystemContact: Boolean = false,
      hasE164: Boolean = false,
      shouldShowE164: Boolean = false,
      expiresInSeconds: Int = 0,
      isMuted: Boolean = false
    ): Recipient {
      return mockk<Recipient>(relaxed = true) {
        every { id } returns RECIPIENT_ID
        every { this@mockk.isSelf } returns isSelf
        every { this@mockk.isReleaseNotes } returns isReleaseNotes
        every { this@mockk.isBlocked } returns isBlocked
        every { this@mockk.isSystemContact } returns isSystemContact
        every { this@mockk.hasE164 } returns hasE164
        every { this@mockk.shouldShowE164 } returns shouldShowE164
        every { this@mockk.expiresInSeconds } returns expiresInSeconds
        every { this@mockk.isMuted } returns isMuted
        every { isIndividual } returns (!isSelf && !isReleaseNotes)
        every { isGroup } returns false
        every { isRegistered } returns true
        every { registered } returns RecipientTable.RegisteredState.REGISTERED
      }
    }

    fun groups(count: Int): List<Recipient> {
      return (1..count).map { index ->
        mockk<Recipient>(relaxed = true) {
          every { id } returns RecipientId.from(100L + index)
        }
      }
    }

    /** The first rail item being tapped, which is all these tests ever need. */
    fun railItemClicked(): IndividualSettingsEvent.MediaRailEvent {
      return IndividualSettingsEvent.MediaRailEvent(RecentMediaRailEvents.ItemClicked(index = 0, bounds = RAIL_ITEM_BOUNDS, leftToRight = true))
    }

    fun mediaRecord(attachment: DatabaseAttachment?): MediaTable.MediaRecord {
      return MediaTable.MediaRecord(
        attachment = attachment,
        recipientId = RECIPIENT_ID,
        threadRecipientId = RecipientId.from(2L),
        threadId = THREAD_ID,
        messageId = 4L,
        date = 1000L,
        isOutgoing = false
      )
    }

    /**
     * [Attachment.transferState] is a `@JvmField`, so it can't be stubbed -- we have to build a real attachment and spy
     * on the parts of it we do want to control.
     */
    fun attachment(transferState: Int, hasUri: Boolean): DatabaseAttachment {
      val attachment = spyk(databaseAttachment(transferState))
      every { attachment.uri } returns if (hasUri) mockk() else null
      every { attachment.thumbnailUri } returns null
      return attachment
    }

    fun databaseAttachment(transferState: Int): DatabaseAttachment {
      return DatabaseAttachment(
        attachmentId = AttachmentId(1L),
        mmsId = 1L,
        hasData = false,
        hasThumbnail = false,
        contentType = "image/jpeg",
        transferProgress = transferState,
        size = 1024L,
        fileName = "photo.jpg",
        cdn = Cdn.CDN_3,
        location = null,
        key = null,
        digest = null,
        incrementalDigest = null,
        incrementalMacChunkSize = 0,
        fastPreflightId = null,
        voiceNote = false,
        borderless = false,
        videoGif = false,
        width = 0,
        height = 0,
        quote = false,
        caption = null,
        stickerLocator = null,
        blurHash = null,
        audioHash = null,
        transformProperties = null,
        displayOrder = 0,
        uploadTimestamp = 0,
        dataHash = null,
        archiveCdn = null,
        thumbnailRestoreState = AttachmentTable.ThumbnailRestoreState.NONE,
        archiveTransferState = AttachmentTable.ArchiveTransferState.NONE,
        archiveThumbnailTransferState = AttachmentTable.ArchiveTransferState.NONE,
        uuid = null,
        quoteTargetContentType = null,
        metadata = null
      )
    }
  }
}
