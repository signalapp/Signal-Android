/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.signal.core.util.Result
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.core.util.readToList
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallEntry
import org.thoughtcrime.securesms.components.settings.conversation.shared.GroupMember
import org.thoughtcrime.securesms.components.settings.conversation.shared.LegacyGroupState
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery
import org.thoughtcrime.securesms.conversation.colors.ColorizerV2
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.RxDatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupChangeException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupProtoUtil
import org.thoughtcrime.securesms.groups.GroupsInCommonRepository
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabelRepository
import org.thoughtcrime.securesms.groups.memberlabel.StyledMemberLabel
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupChangeResult
import org.thoughtcrime.securesms.groups.ui.GroupMemberOrder
import org.thoughtcrime.securesms.groups.v2.GroupAddMembersResult
import org.thoughtcrime.securesms.groups.v2.GroupManagementRepository
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messagerequests.MessageRequestRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.io.IOException
import kotlin.coroutines.resume

private val TAG = Log.tag(ConversationSettingsRepository::class)

/**
 * Data access shared by all four conversation settings screens.
 */
class ConversationSettingsRepository(
  private val context: Context,
  private val groupManagementRepository: GroupManagementRepository = GroupManagementRepository(context),
  private val messageRequestRepository: MessageRequestRepository = MessageRequestRepository(context),
  private val memberLabelRepository: MemberLabelRepository = MemberLabelRepository.instance
) {

  fun isDeprecatedOrUnregistered(): Boolean {
    return SignalStore.misc.isClientDeprecated || TextSecurePreferences.isUnauthorizedReceived(context)
  }

  fun isInternalRecipientDetailsEnabled(): Boolean {
    return SignalStore.internal.recipientDetails
  }

  fun isStarredMessagesEnabled(): Boolean {
    return SignalStore.labs.starredMessages
  }

  fun isAddToStoryAvailable(): Boolean {
    return !SignalStore.story.isFeatureDisabled
  }

  fun isStoriesFeatureEnabled(): Boolean {
    return Stories.isFeatureEnabled()
  }

  fun getSelfId(): RecipientId {
    return Recipient.self().id
  }

  fun isBlockable(recipient: Recipient): Boolean {
    return RecipientUtil.isBlockable(recipient)
  }

  fun observeRecipient(recipientId: RecipientId): Flow<Recipient> {
    return Recipient.observable(recipientId).asFlow()
  }

  /**
   * Emits the group's recipient whenever it changes. The initial lookup of the group's [RecipientId] hits the database,
   * so this can't be a plain [observeRecipient] call.
   */
  fun observeGroupRecipient(groupId: GroupId): Flow<Recipient> {
    return flow {
      val recipientId = withContext(SignalDispatchers.Default) { Recipient.externalGroupExact(groupId).id }
      emitAll(observeRecipient(recipientId))
    }
  }

  /**
   * Emits everything we can derive from the group's record. Recipient changes drive this, since a group change always
   * touches its recipient.
   */
  fun observeGroupDetails(groupId: GroupId): Flow<GroupDetails> {
    return observeGroupRecipient(groupId).mapNotNull { recipient ->
      withContext(SignalDispatchers.Default) {
        SignalDatabase.groups.getGroup(recipient.id).orNull()?.let { buildGroupDetails(groupId, recipient, it) }
      }
    }
  }

  fun observeStoryViewState(recipientId: RecipientId): Flow<StoryViewState> {
    return StoryViewState.getForRecipientId(recipientId).asFlow()
  }

  fun observeStoryViewState(groupId: GroupId): Flow<StoryViewState> {
    return flow {
      val recipientId = withContext(SignalDispatchers.Default) { Recipient.externalGroupExact(groupId).id }
      emitAll(observeStoryViewState(recipientId))
    }
  }

  fun observeGroupsInCommon(recipientId: RecipientId): Flow<List<Recipient>> {
    return GroupsInCommonRepository.getGroupsInCommon(context, recipientId)
  }

  /** Emits the calls backing the call-info variant of this screen every time the conversation changes. */
  fun observeCalls(threadId: Long, callRowIds: LongArray): Flow<List<CallEntry>> {
    return RxDatabaseObserver.conversation(threadId).toObservable().asFlow().map { getCallEntries(callRowIds) }
  }

  /** [observeCalls], but re-subscribing as the caller learns which thread it's looking at. */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeCalls(threadIdFlow: Flow<Long>, callRowIds: LongArray): Flow<List<CallEntry>> {
    return threadIdFlow
      .distinctUntilChanged()
      .filter { it > 0 }
      .flatMapLatest { observeCalls(it, callRowIds) }
  }

  suspend fun getCallEntries(callRowIds: LongArray): List<CallEntry> {
    if (callRowIds.isEmpty()) {
      return emptyList()
    }

    return withContext(SignalDispatchers.Default) {
      val callMap: Map<Long, CallTable.Call> = SignalDatabase.calls.getCallsByRowIds(callRowIds.toList())
      val messageIds = callMap.values.mapNotNull { it.messageId }

      SignalDatabase.messages
        .getMessages(messageIds)
        .iterator()
        .asSequence()
        .filter { callMap.containsKey(it.id) }
        .map { CallEntry(callMap.getValue(it.id), it) }
        .sortedByDescending { it.call.timestamp }
        .toList()
    }
  }

  suspend fun getSharedMedia(threadId: Long, limit: Int): List<MediaTable.MediaRecord> {
    if (threadId <= 0) {
      return emptyList()
    }

    return withContext(SignalDispatchers.Default) {
      SignalDatabase.media
        .getGalleryMediaForThread(threadId, MediaTable.Sorting.Newest, limit)
        ?.readToList { MediaTable.MediaRecord.from(it) }
        ?: emptyList()
    }
  }

  suspend fun getThreadId(recipientId: RecipientId): Long {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.threads.getThreadIdIfExistsFor(recipientId)
    }
  }

  suspend fun getThreadId(groupId: GroupId): Long {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.threads.getThreadIdIfExistsFor(Recipient.externalGroupExact(groupId).id)
    }
  }

  suspend fun hasGroups(): Boolean {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.groups.getActiveGroupCount() > 0
    }
  }

  suspend fun getIdentity(recipientId: RecipientId): IdentityRecord? {
    return withContext(SignalDispatchers.Default) {
      if (SignalStore.account.aci != null && SignalStore.account.pni != null) {
        AppDependencies.protocolStore.aci().identities().getIdentityRecord(recipientId).orNull()
      } else {
        null
      }
    }
  }

  suspend fun getGroupMembership(recipientId: RecipientId): List<RecipientId> {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.groups.getPushGroupsContainingMember(recipientId).map { it.recipientId }
    }
  }

  suspend fun getMemberLabels(groupId: GroupId.V2, members: List<Recipient>): Map<RecipientId, StyledMemberLabel> {
    val labels = memberLabelRepository.getLabels(groupId, members)
    if (labels.isEmpty()) {
      return emptyMap()
    }

    return withContext(SignalDispatchers.Default) {
      val colorizer = ColorizerV2(members.mapNotNull { it.serviceId.orNull() })

      members
        .mapNotNull { member -> labels[member.id]?.let { member to it } }
        .associate { (member, label) -> member.id to StyledMemberLabel(label, colorizer.getIncomingGroupSenderColor(context, member)) }
    }
  }

  suspend fun canSetOwnMemberLabel(groupId: GroupId.V2): Boolean {
    return memberLabelRepository.canSetLabel(groupId, Recipient.self())
  }

  fun refreshRecipient(recipientId: RecipientId) {
    SignalExecutors.UNBOUNDED.execute {
      try {
        ContactDiscovery.refresh(context, Recipient.resolved(recipientId), false)
      } catch (e: IOException) {
        Log.w(TAG, "Failed to refresh user after adding to contacts.")
      }
    }
  }

  suspend fun setMuteUntil(recipientId: RecipientId, until: Long) {
    withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.setMuted(recipientId, until)
    }
  }

  suspend fun setMuteUntil(groupId: GroupId, until: Long) {
    withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.setMuted(Recipient.externalGroupExact(groupId).id, until)
    }
  }

  suspend fun getGroupCapacity(groupId: GroupId): GroupCapacityResult? {
    return withContext(SignalDispatchers.Default) {
      val groupRecord: GroupRecord = SignalDatabase.groups.getGroup(groupId).orNull() ?: return@withContext null

      if (groupRecord.hasV2GroupProperties) {
        val pendingMembers: List<RecipientId> = groupRecord
          .requireV2GroupProperties()
          .decryptedGroup
          .pendingMembers
          .map { GroupProtoUtil.serviceIdBinaryToRecipientId(it.serviceIdBytes) }

        GroupCapacityResult(Recipient.self().id, groupRecord.members + pendingMembers, RemoteConfig.groupLimits, groupRecord.isAnnouncementGroup)
      } else {
        GroupCapacityResult(Recipient.self().id, groupRecord.members, RemoteConfig.groupLimits, false)
      }
    }
  }

  suspend fun addMembers(groupId: GroupId, selected: List<RecipientId>): GroupAddMembersResult {
    return suspendCancellableCoroutine { continuation ->
      groupManagementRepository.addMembers(groupId, selected) { continuation.resume(it) }
    }
  }

  suspend fun block(recipientId: RecipientId): GroupChangeResult {
    return withContext(SignalDispatchers.IO) {
      try {
        val recipient = Recipient.resolved(recipientId)
        if (recipient.isGroup) {
          RecipientUtil.block(context, recipient)
        } else {
          RecipientUtil.blockNonGroup(context, recipient)
        }
        GroupChangeResult.SUCCESS
      } catch (e: IOException) {
        Log.w(TAG, "Failed to block recipient.", e)
        GroupChangeResult.failure(GroupChangeFailureReason.fromException(e))
      } catch (e: GroupChangeException) {
        Log.w(TAG, "Failed to block recipient.", e)
        GroupChangeResult.failure(GroupChangeFailureReason.fromException(e))
      }
    }
  }

  suspend fun block(groupId: GroupId): GroupChangeResult {
    return withContext(SignalDispatchers.IO) {
      try {
        RecipientUtil.block(context, Recipient.externalGroupExact(groupId))
        GroupChangeResult.SUCCESS
      } catch (e: IOException) {
        Log.w(TAG, "Failed to block group.", e)
        GroupChangeResult.failure(GroupChangeFailureReason.fromException(e))
      } catch (e: GroupChangeException) {
        Log.w(TAG, "Failed to block group.", e)
        GroupChangeResult.failure(GroupChangeFailureReason.fromException(e))
      }
    }
  }

  suspend fun unblock(recipientId: RecipientId) {
    withContext(SignalDispatchers.Default) {
      RecipientUtil.unblock(Recipient.resolved(recipientId))
    }
  }

  suspend fun unblock(groupId: GroupId) {
    withContext(SignalDispatchers.Default) {
      RecipientUtil.unblock(Recipient.externalGroupExact(groupId))
    }
  }

  suspend fun reportSpam(recipientId: RecipientId, threadId: Long) {
    messageRequestRepository.reportSpamMessageRequest(recipientId, threadId).await()
  }

  suspend fun blockAndReportSpam(recipientId: RecipientId, threadId: Long): Result<Unit, GroupChangeFailureReason> {
    return messageRequestRepository.blockAndReportSpamMessageRequest(recipientId, threadId).await()
  }

  suspend fun isArchived(recipientId: RecipientId): Boolean {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.threads.isArchived(recipientId)
    }
  }

  suspend fun setArchived(threadId: Long, archived: Boolean) {
    withContext(SignalDispatchers.Default) {
      SignalDatabase.threads.setArchived(setOf(threadId), archived)
    }
  }

  suspend fun deleteChat(threadId: Long) {
    withContext(SignalDispatchers.Default) {
      SignalDatabase.threads.deleteConversation(threadId)
    }
  }

  private fun buildGroupDetails(groupId: GroupId, recipient: Recipient, record: GroupRecord): GroupDetails {
    val self = Recipient.self()
    val selfMemberLevel = record.memberLevel(self)
    val members = record.members
      .map { Recipient.resolved(it) }
      .map { GroupMember(recipient = it, isAdmin = record.isAdmin(it)) }
      .sortedWith(MEMBER_ORDER)

    val pendingMemberCount = if (record.hasV2GroupProperties) {
      record.requireV2GroupProperties().decryptedGroup.pendingMembers.size
    } else {
      0
    }

    return GroupDetails(
      recipient = recipient,
      title = record.title?.takeIf { it.isNotEmpty() } ?: recipient.getDisplayName(context),
      description = record.description,
      descriptionShouldLinkify = RecipientUtil.isMessageRequestAccepted(recipient),
      members = members,
      isSelfAdmin = record.isAdmin(self),
      canEditGroupAttributes = record.isActive && record.attributesAccessControl.allows(selfMemberLevel),
      canAddMembers = record.isActive && record.membershipAdditionAccessControl.allows(selfMemberLevel),
      isActive = record.isActive,
      isTerminated = record.isTerminated,
      isAnnouncementGroup = record.isAnnouncementGroup,
      groupLinkEnabled = record.isGroupLinkEnabled,
      membershipCountDescription = membershipCountDescription(pendingMemberCount, members.size),
      legacyGroupState = if (groupId.isMms) LegacyGroupState.MMS_WARNING else LegacyGroupState.NONE
    )
  }

  private fun membershipCountDescription(invitedCount: Int, fullMemberCount: Int): String {
    val resources = context.resources
    return if (invitedCount > 0) {
      val invited = resources.getQuantityString(R.plurals.MessageRequestProfileView_invited, invitedCount, invitedCount)
      resources.getQuantityString(R.plurals.MessageRequestProfileView_members_and_invited, fullMemberCount, fullMemberCount, invited)
    } else {
      resources.getQuantityString(R.plurals.MessageRequestProfileView_members, fullMemberCount, fullMemberCount)
    }
  }

  /** Everything about a group that we can pull off of its [GroupRecord] and [Recipient]. */
  data class GroupDetails(
    val recipient: Recipient,
    val title: String,
    val description: String?,
    val descriptionShouldLinkify: Boolean,
    val members: List<GroupMember>,
    val isSelfAdmin: Boolean,
    val canEditGroupAttributes: Boolean,
    val canAddMembers: Boolean,
    val isActive: Boolean,
    val isTerminated: Boolean,
    val isAnnouncementGroup: Boolean,
    val groupLinkEnabled: Boolean,
    val membershipCountDescription: String,
    val legacyGroupState: LegacyGroupState
  )

  companion object {
    private val MEMBER_ORDER: Comparator<GroupMember> = GroupMemberOrder.comparator(
      { it.recipient.isSelf },
      { it.isAdmin },
      { it.recipient.hasAUserSetDisplayName(AppDependencies.application) },
      { it.recipient.getDisplayName(AppDependencies.application) }
    )
  }
}
