/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.v2.processing

import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.database.model.GroupsV2UpdateMessageConverter
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context
import org.thoughtcrime.securesms.database.model.databaseprotos.GV2UpdateDescription
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupMutation
import org.thoughtcrime.securesms.groups.GroupNotAMemberException
import org.thoughtcrime.securesms.groups.GroupProtoUtil
import org.thoughtcrime.securesms.groups.v2.ProfileKeySet
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor.Companion.LATEST
import org.thoughtcrime.securesms.jobs.AvatarGroupsV2DownloadJob
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob
import org.thoughtcrime.securesms.jobs.LeaveGroupV2Job
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil
import org.whispersystems.signalservice.api.groupsv2.GroupChangeReconstruct
import org.whispersystems.signalservice.api.groupsv2.GroupHistoryPage
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException
import org.whispersystems.signalservice.api.groupsv2.NotAbleToApplyGroupV2ChangeException
import org.whispersystems.signalservice.api.groupsv2.ReceivedGroupSendEndorsements
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceIds
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.push.exceptions.GroupNotFoundException
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException
import java.io.IOException
import java.util.Optional
import kotlin.math.max

/**
 * Given a group, provide various state operations to update from the current local revision to a
 * specific number or [LATEST] revision via a P2P update and/or by fetching changes from the server.
 */
class GroupsV2StateProcessor private constructor(
  private val serviceIds: ServiceIds,
  private val groupMasterKey: GroupMasterKey,
  private val groupSecretParams: GroupSecretParams
) {

  companion object {
    private val TAG = Log.tag(GroupsV2StateProcessor::class.java)

    const val LATEST = GroupStatePatcher.LATEST

    /**
     * Used to mark a group state as a placeholder when there is partial knowledge (title and avater)
     * gathered from a group join link.
     */
    const val PLACEHOLDER_REVISION = GroupStatePatcher.PLACEHOLDER_REVISION

    /**
     * Used to mark a group state as a placeholder when you have no knowledge at all of the group
     * e.g. from a group master key from a storage service restore.
     */
    const val RESTORE_PLACEHOLDER_REVISION = GroupStatePatcher.RESTORE_PLACEHOLDER_REVISION

    @JvmStatic
    @JvmOverloads
    fun forGroup(
      serviceIds: ServiceIds,
      groupMasterKey: GroupMasterKey,
      groupSecretParams: GroupSecretParams? = null
    ): GroupsV2StateProcessor {
      return GroupsV2StateProcessor(
        serviceIds = serviceIds,
        groupMasterKey = groupMasterKey,
        groupSecretParams = groupSecretParams ?: GroupSecretParams.deriveFromMasterKey(groupMasterKey)
      )
    }
  }

  private val groupsApi = AppDependencies.signalServiceAccountManager.getGroupsV2Api()
  private val groupsV2Authorization = AppDependencies.groupsV2Authorization
  private val groupOperations = AppDependencies.groupsV2Operations.forGroup(groupSecretParams)
  private val groupId = GroupId.v2(groupSecretParams.getPublicParams().getGroupIdentifier())
  private val profileAndMessageHelper = ProfileAndMessageHelper.create(serviceIds.aci, groupMasterKey, groupId)

  private val logPrefix = "[$groupId]"

  /**
   * Forces the local state to be updated directly to the latest available from the server without
   * processing individual changes and instead inserting one large change to go from local to server.
   */
  @WorkerThread
  @Throws(IOException::class, GroupNotAMemberException::class)
  fun forceSanityUpdateFromServer(timestamp: Long): GroupUpdateResult {
    val currentLocalState: DecryptedGroup? = SignalDatabase.groups.getGroup(groupId).map { it.requireV2GroupProperties().decryptedGroup }.orNull()

    if (currentLocalState == null) {
      Log.i(TAG, "$logPrefix No local state to force update")
      return GroupUpdateResult.CONSISTENT_OR_AHEAD
    }

    return when (val result = updateToLatestViaServer(timestamp, currentLocalState, reconstructChange = true)) {
      InternalUpdateResult.NoUpdateNeeded -> GroupUpdateResult.CONSISTENT_OR_AHEAD
      is InternalUpdateResult.Updated -> GroupUpdateResult(GroupUpdateResult.UpdateStatus.GROUP_UPDATED, result.updatedLocalState)
      is InternalUpdateResult.NotAMember -> throw result.exception
      is InternalUpdateResult.UpdateFailed -> throw result.throwable
    }
  }

  /**
   * Fetch and save the latest group send endorsements from the server. This endorsements returned may
   * not match our local view of the membership if the membership has changed on the server and we haven't updated the
   * group state yet. This is only an issue when trying to send to a group member that has been removed and should be handled
   * gracefully as a fallback in the sending flow.
   */
  @WorkerThread
  @Throws(IOException::class, GroupNotAMemberException::class)
  fun updateGroupSendEndorsements() {
    val result = groupsApi.getGroupAsResult(groupSecretParams, groupsV2Authorization.getAuthorizationForToday(serviceIds, groupSecretParams))

    val groupResponse = when (result) {
      is NetworkResult.Success -> result.result
      else -> when (val cause = result.getCause()!!) {
        is NotInGroupException, is GroupNotFoundException -> throw GroupNotAMemberException(cause)
        is IOException -> throw cause
        else -> throw IOException(cause)
      }
    }

    val receivedGroupSendEndorsements = groupOperations.receiveGroupSendEndorsements(serviceIds.aci, groupResponse.group, groupResponse.groupSendEndorsementsResponse)

    if (receivedGroupSendEndorsements != null) {
      Log.i(TAG, "$logPrefix Updating group send endorsements")
      SignalDatabase.groups.updateGroupSendEndorsements(groupId, receivedGroupSendEndorsements)
    } else {
      Log.w(TAG, "$logPrefix No group send endorsements on response")
    }
  }

  /**
   * Using network where required, will attempt to bring the local copy of the group up to the revision specified.
   *
   * @param targetRevision use [.LATEST] to get latest.
   */
  @JvmOverloads
  @WorkerThread
  @Throws(IOException::class, GroupNotAMemberException::class)
  fun updateLocalGroupToRevision(
    targetRevision: Int,
    timestamp: Long,
    signedGroupChange: DecryptedGroupChange? = null,
    groupRecord: Optional<GroupRecord> = SignalDatabase.groups.getGroup(groupId),
    serverGuid: String? = null
  ): GroupUpdateResult {
    if (localIsAtLeast(groupRecord, targetRevision)) {
      return GroupUpdateResult(GroupUpdateResult.UpdateStatus.GROUP_CONSISTENT_OR_AHEAD, null)
    }

    val currentLocalState: DecryptedGroup? = groupRecord.map { it.requireV2GroupProperties().decryptedGroup }.orNull()

    if (signedGroupChange != null && canApplyP2pChange(targetRevision, signedGroupChange, currentLocalState, groupRecord)) {
      when (val p2pUpdateResult = updateViaPeerGroupChange(timestamp, serverGuid, signedGroupChange, currentLocalState!!, forceApply = false)) {
        InternalUpdateResult.NoUpdateNeeded -> return GroupUpdateResult.CONSISTENT_OR_AHEAD
        is InternalUpdateResult.Updated -> return GroupUpdateResult.updated(p2pUpdateResult.updatedLocalState)
        else -> Log.w(TAG, "$logPrefix P2P update was not successfully processed, falling back to server update")
      }
    }

    val serverUpdateResult = updateViaServer(targetRevision, timestamp, serverGuid, groupRecord)

    when (serverUpdateResult) {
      InternalUpdateResult.NoUpdateNeeded -> return GroupUpdateResult.CONSISTENT_OR_AHEAD
      is InternalUpdateResult.Updated -> return GroupUpdateResult.updated(serverUpdateResult.updatedLocalState)
      is InternalUpdateResult.UpdateFailed -> throw serverUpdateResult.throwable
      is InternalUpdateResult.NotAMember -> Unit
    }

    Log.w(TAG, "$logPrefix Unable to query server for group, says we're not in group, trying to resolve locally")

    if (currentLocalState != null && signedGroupChange != null) {
      if (notInGroupAndNotBeingAdded(groupRecord, signedGroupChange)) {
        Log.w(TAG, "$logPrefix Server says we're not a member. Ignoring P2P group change because we're not currently in the group and this change doesn't add us in.")
      } else {
        Log.i(TAG, "$logPrefix Server says we're not a member. Force applying P2P group change.")
        when (val forcedP2pUpdateResult = updateViaPeerGroupChange(timestamp, serverGuid, signedGroupChange, currentLocalState, forceApply = true)) {
          is InternalUpdateResult.Updated -> return GroupUpdateResult.updated(forcedP2pUpdateResult.updatedLocalState)
          InternalUpdateResult.NoUpdateNeeded -> return GroupUpdateResult.CONSISTENT_OR_AHEAD
          is InternalUpdateResult.NotAMember, is InternalUpdateResult.UpdateFailed -> Log.w(TAG, "$logPrefix Unable to apply P2P group change when not a member: $forcedP2pUpdateResult")
        }
      }
    }

    if (currentLocalState != null && DecryptedGroupUtil.isPendingOrRequesting(currentLocalState, serviceIds)) {
      Log.w(TAG, "$logPrefix Unable to query server for group. Server says we're not in group, but we think we are a pending or requesting member")
    } else {
      Log.w(TAG, "$logPrefix Unable to query server for group $groupId server says we're not in group, we agree, inserting leave message")
      profileAndMessageHelper.leaveGroupLocally(serviceIds)
    }

    throw GroupNotAMemberException(serverUpdateResult.exception)
  }

  private fun canApplyP2pChange(
    targetRevision: Int,
    signedGroupChange: DecryptedGroupChange,
    currentLocalState: DecryptedGroup?,
    groupRecord: Optional<GroupRecord>
  ): Boolean {
    if (SignalStore.internal.gv2IgnoreP2PChanges()) {
      Log.w(TAG, "$logPrefix Ignoring P2P group change by setting")
      return false
    }

    if (currentLocalState == null || currentLocalState.revision + 1 != signedGroupChange.revision || targetRevision != signedGroupChange.revision) {
      return false
    }

    if (notInGroupAndNotBeingAdded(groupRecord, signedGroupChange) && notHavingInviteRevoked(signedGroupChange)) {
      Log.w(TAG, "$logPrefix Ignoring P2P group change because we're not currently in the group and this change doesn't add us in.")
      return false
    }

    return true
  }

  private fun updateViaPeerGroupChange(
    timestamp: Long,
    serverGuid: String?,
    signedGroupChange: DecryptedGroupChange,
    currentLocalState: DecryptedGroup,
    forceApply: Boolean
  ): InternalUpdateResult {
    val updatedGroupState = try {
      if (forceApply) {
        DecryptedGroupUtil.applyWithoutRevisionCheck(currentLocalState, signedGroupChange)
      } else {
        DecryptedGroupUtil.apply(currentLocalState, signedGroupChange)
      }
    } catch (e: NotAbleToApplyGroupV2ChangeException) {
      Log.w(TAG, "$logPrefix Unable to apply P2P group change", e)
      return InternalUpdateResult.UpdateFailed(e)
    }

    val groupStateDiff = GroupStateDiff(currentLocalState, updatedGroupState, signedGroupChange)

    return saveGroupUpdate(
      timestamp = timestamp,
      serverGuid = serverGuid,
      groupStateDiff = groupStateDiff,
      groupSendEndorsements = null
    )
  }

  private fun updateViaServer(
    targetRevision: Int,
    timestamp: Long,
    serverGuid: String?,
    groupRecord: Optional<GroupRecord> = SignalDatabase.groups.getGroup(groupId)
  ): InternalUpdateResult {
    var currentLocalState: DecryptedGroup? = groupRecord.map { it.requireV2GroupProperties().decryptedGroup }.orNull()

    if (targetRevision == LATEST && (currentLocalState == null || currentLocalState.revision == RESTORE_PLACEHOLDER_REVISION)) {
      Log.i(TAG, "$logPrefix Latest revision only, update to latest directly")
      return updateToLatestViaServer(timestamp, currentLocalState, reconstructChange = false)
    }

    Log.i(TAG, "$logPrefix Paging from server targetRevision: ${if (targetRevision == LATEST) "latest" else targetRevision}")

    val joinedAtResult = groupsApi.getGroupJoinedAt(groupsV2Authorization.getAuthorizationForToday(serviceIds, groupSecretParams))

    if (joinedAtResult !is NetworkResult.Success) {
      val joinedAtFailure = InternalUpdateResult.from(joinedAtResult.getCause()!!)
      if (joinedAtFailure is InternalUpdateResult.NotAMember) {
        Log.i(TAG, "$logPrefix Not a member, try to update to latest directly")
        return updateToLatestViaServer(timestamp, currentLocalState, reconstructChange = currentLocalState != null)
      } else {
        return joinedAtFailure
      }
    }

    val joinedAtRevision = joinedAtResult.result

    val sendEndorsementExpiration = groupRecord.map { it.groupSendEndorsementExpiration }.orElse(0L)

    var includeFirstState = currentLocalState == null ||
      currentLocalState.revision < 0 ||
      currentLocalState.revision == joinedAtRevision ||
      !GroupProtoUtil.isMember(serviceIds.aci, currentLocalState.members)

    val profileKeys = ProfileKeySet()
    val addMessagesForAllUpdates = currentLocalState == null || currentLocalState.revision != RESTORE_PLACEHOLDER_REVISION
    var logsNeededFrom = if (currentLocalState != null) max(currentLocalState.revision, joinedAtRevision) else joinedAtRevision
    var hasMore = true
    var runningTimestamp = timestamp
    var performCdsLookup = false
    var hasRemainingRemoteChanges = false

    while (hasMore) {
      Log.i(TAG, "$logPrefix Requesting change logs from server, currentRevision=${currentLocalState?.revision ?: "null"} logsNeededFrom=$logsNeededFrom includeFirstState=$includeFirstState sendEndorsementExpiration=${sendEndorsementExpiration > 0}")

      val (remoteGroupStateDiff, pagingData) = getGroupChangeLogs(currentLocalState, logsNeededFrom, includeFirstState, sendEndorsementExpiration)
      val applyGroupStateDiffResult: AdvanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(remoteGroupStateDiff, targetRevision)
      val updatedGroupState: DecryptedGroup? = applyGroupStateDiffResult.updatedGroupState

      if (updatedGroupState == null || updatedGroupState == remoteGroupStateDiff.previousGroupState) {
        Log.i(TAG, "$logPrefix Local state is at or later than server revision: ${currentLocalState?.revision ?: "null"}")

        if (currentLocalState != null) {
          val endorsements = groupOperations.receiveGroupSendEndorsements(serviceIds.aci, currentLocalState, remoteGroupStateDiff.groupSendEndorsementsResponse)

          if (endorsements != null) {
            Log.i(TAG, "$logPrefix Received updated send endorsements, saving")
            SignalDatabase.groups.updateGroupSendEndorsements(groupId, endorsements)
          }
        }

        return InternalUpdateResult.NoUpdateNeeded
      }

      Log.i(TAG, "$logPrefix Saving updated group state at revision: ${updatedGroupState.revision}")

      saveGroupState(remoteGroupStateDiff, updatedGroupState, groupOperations.receiveGroupSendEndorsements(serviceIds.aci, updatedGroupState, remoteGroupStateDiff.groupSendEndorsementsResponse))

      if (addMessagesForAllUpdates) {
        Log.d(TAG, "$logPrefix Inserting group changes into chat history")
        runningTimestamp = profileAndMessageHelper.insertUpdateMessages(runningTimestamp, currentLocalState, applyGroupStateDiffResult.processedLogEntries, serverGuid)
      }

      remoteGroupStateDiff
        .serverHistory
        .forEach { entry ->
          entry.group?.let { profileKeys.addKeysFromGroupState(it) }

          entry.change?.let { change ->
            profileKeys.addKeysFromGroupChange(change)
            if (change.promotePendingPniAciMembers.isNotEmpty()) {
              performCdsLookup = true
            }
          }
        }

      currentLocalState = updatedGroupState
      hasMore = pagingData.hasMorePages

      if (hasMore) {
        includeFirstState = false
        logsNeededFrom = pagingData.nextPageRevision
      } else {
        if (applyGroupStateDiffResult.remainingRemoteGroupChanges.isNotEmpty()) {
          hasRemainingRemoteChanges = true
        }
      }
    }

    if (!addMessagesForAllUpdates) {
      Log.i(TAG, "$logPrefix Inserting single update message for restore placeholder")
      profileAndMessageHelper.insertUpdateMessages(runningTimestamp, null, setOf(AppliedGroupChangeLog(currentLocalState!!, null)), serverGuid)
    }

    profileAndMessageHelper.persistLearnedProfileKeys(profileKeys)

    if (performCdsLookup) {
      AppDependencies.jobManager.add(DirectoryRefreshJob(false))
    }

    if (hasRemainingRemoteChanges) {
      Log.i(TAG, "$logPrefix There are more revisions on the server for this group, scheduling for later")
      AppDependencies.jobManager.add(RequestGroupV2InfoJob(groupId))
    }

    return InternalUpdateResult.Updated(currentLocalState!!)
  }

  private fun updateToLatestViaServer(timestamp: Long, currentLocalState: DecryptedGroup?, reconstructChange: Boolean): InternalUpdateResult {
    val result = groupsApi.getGroupAsResult(groupSecretParams, groupsV2Authorization.getAuthorizationForToday(serviceIds, groupSecretParams))

    val groupResponse = if (result is NetworkResult.Success) {
      result.result
    } else {
      return InternalUpdateResult.from(result.getCause()!!)
    }

    val completeGroupChange = if (reconstructChange) GroupChangeReconstruct.reconstructGroupChange(currentLocalState, groupResponse.group) else null
    val remoteGroupStateDiff = GroupStateDiff(currentLocalState, groupResponse.group, completeGroupChange)

    return saveGroupUpdate(
      timestamp = timestamp,
      serverGuid = null,
      groupStateDiff = remoteGroupStateDiff,
      groupSendEndorsements = groupOperations.receiveGroupSendEndorsements(serviceIds.aci, groupResponse.group, groupResponse.groupSendEndorsementsResponse)
    )
  }

  /**
   * @return true iff group exists locally and is at least the specified revision.
   */
  private fun localIsAtLeast(groupRecord: Optional<GroupRecord>, revision: Int): Boolean {
    if (revision == LATEST || groupRecord.isEmpty || SignalDatabase.groups.isUnknownGroup(groupRecord)) {
      return false
    }

    return revision <= groupRecord.get().requireV2GroupProperties().groupRevision
  }

  private fun notInGroupAndNotBeingAdded(groupRecord: Optional<GroupRecord>, signedGroupChange: DecryptedGroupChange): Boolean {
    val currentlyInGroup = groupRecord.isPresent && groupRecord.get().isActive

    val addedAsMember = signedGroupChange
      .newMembers
      .asSequence()
      .mapNotNull { ACI.parseOrNull(it.aciBytes) }
      .any { serviceIds.matches(it) }

    val addedAsPendingMember = signedGroupChange
      .newPendingMembers
      .asSequence()
      .map { it.serviceIdBytes }
      .any { serviceIds.matches(it) }

    val addedAsRequestingMember = signedGroupChange
      .newRequestingMembers
      .asSequence()
      .mapNotNull { ACI.parseOrNull(it.aciBytes) }
      .any { serviceIds.matches(it) }

    return !currentlyInGroup && !addedAsMember && !addedAsPendingMember && !addedAsRequestingMember
  }

  private fun notHavingInviteRevoked(signedGroupChange: DecryptedGroupChange): Boolean {
    val havingInviteRevoked = signedGroupChange
      .deletePendingMembers
      .asSequence()
      .map { it.serviceIdBytes }
      .any { serviceIds.matches(it) }

    return !havingInviteRevoked
  }

  @Throws(IOException::class)
  private fun getGroupChangeLogs(
    localState: DecryptedGroup?,
    logsNeededFromRevision: Int,
    includeFirstState: Boolean,
    sendEndorsementsExpirationMs: Long
  ): Pair<GroupStateDiff, GroupHistoryPage.PagingData> {
    try {
      val groupHistoryPage = groupsApi.getGroupHistoryPage(groupSecretParams, logsNeededFromRevision, groupsV2Authorization.getAuthorizationForToday(serviceIds, groupSecretParams), includeFirstState, sendEndorsementsExpirationMs)

      return GroupStateDiff(localState, groupHistoryPage.changeLogs, groupHistoryPage.groupSendEndorsementsResponse) to groupHistoryPage.pagingData
    } catch (e: InvalidGroupStateException) {
      throw IOException(e)
    } catch (e: VerificationFailedException) {
      throw IOException(e)
    } catch (e: InvalidInputException) {
      throw IOException(e)
    }
  }

  private fun saveGroupUpdate(
    timestamp: Long,
    serverGuid: String?,
    groupStateDiff: GroupStateDiff,
    groupSendEndorsements: ReceivedGroupSendEndorsements?
  ): InternalUpdateResult {
    val currentLocalState: DecryptedGroup? = groupStateDiff.previousGroupState
    val applyGroupStateDiffResult = GroupStatePatcher.applyGroupStateDiff(groupStateDiff, GroupStatePatcher.LATEST)
    val updatedGroupState = applyGroupStateDiffResult.updatedGroupState

    if (updatedGroupState == null || updatedGroupState == groupStateDiff.previousGroupState) {
      Log.i(TAG, "$logPrefix Local state and server state are equal")

      if (groupSendEndorsements != null) {
        Log.i(TAG, "$logPrefix Saving new send endorsements")
        SignalDatabase.groups.updateGroupSendEndorsements(groupId, groupSendEndorsements)
      }

      return InternalUpdateResult.NoUpdateNeeded
    } else {
      Log.i(TAG, "$logPrefix Local state (revision: ${currentLocalState?.revision}) does not match, updating to ${updatedGroupState.revision}")
    }

    saveGroupState(groupStateDiff, updatedGroupState, groupSendEndorsements)

    if (currentLocalState == null || currentLocalState.revision == RESTORE_PLACEHOLDER_REVISION) {
      Log.i(TAG, "$logPrefix Inserting single update message for no local state or restore placeholder")
      profileAndMessageHelper.insertUpdateMessages(timestamp, null, setOf(AppliedGroupChangeLog(updatedGroupState, null)), null)
    } else {
      profileAndMessageHelper.insertUpdateMessages(timestamp, currentLocalState, applyGroupStateDiffResult.processedLogEntries, serverGuid)
    }
    profileAndMessageHelper.persistLearnedProfileKeys(groupStateDiff)

    val performCdsLookup = groupStateDiff
      .serverHistory
      .mapNotNull { it.change }
      .any { it.promotePendingPniAciMembers.isNotEmpty() }

    if (performCdsLookup) {
      AppDependencies.jobManager.add(DirectoryRefreshJob(false))
    }

    return InternalUpdateResult.Updated(updatedGroupState)
  }

  private fun saveGroupState(groupStateDiff: GroupStateDiff, updatedGroupState: DecryptedGroup, groupSendEndorsements: ReceivedGroupSendEndorsements?) {
    val previousGroupState = groupStateDiff.previousGroupState

    if (groupSendEndorsements != null) {
      Log.i(TAG, "$logPrefix Updating send endorsements")
    }

    val needsAvatarFetch = if (previousGroupState == null) {
      val groupId = SignalDatabase.groups.create(groupMasterKey, updatedGroupState, groupSendEndorsements)

      if (groupId == null) {
        Log.w(TAG, "$logPrefix Group create failed, trying to update")
        SignalDatabase.groups.update(groupMasterKey, updatedGroupState, groupSendEndorsements)
      }

      updatedGroupState.avatar.isNotEmpty()
    } else {
      SignalDatabase.groups.update(groupMasterKey, updatedGroupState, groupSendEndorsements)

      updatedGroupState.avatar != previousGroupState.avatar
    }

    if (needsAvatarFetch) {
      AppDependencies.jobManager.add(AvatarGroupsV2DownloadJob(groupId, updatedGroupState.avatar))
    }

    profileAndMessageHelper.setProfileSharing(groupStateDiff, updatedGroupState)
  }

  @VisibleForTesting
  internal class ProfileAndMessageHelper(private val aci: ACI, private val masterKey: GroupMasterKey, private val groupId: GroupId.V2) {

    fun setProfileSharing(groupStateDiff: GroupStateDiff, newLocalState: DecryptedGroup) {
      val previousGroupState = groupStateDiff.previousGroupState

      if (previousGroupState != null && DecryptedGroupUtil.findMemberByAci(previousGroupState.members, aci).isPresent) {
        // Was already a member before update, profile sharing state previously determined
        return
      }

      val selfAsMember = DecryptedGroupUtil.findMemberByAci(newLocalState.members, aci).orNull()
      val selfAsPending = DecryptedGroupUtil.findPendingByServiceId(newLocalState.pendingMembers, aci).orNull()

      if (selfAsMember != null) {
        val revisionJoinedAt = selfAsMember.joinedAtRevision

        val addedAtChange = groupStateDiff
          .serverHistory
          .mapNotNull { it.change }
          .firstOrNull { it.revision == revisionJoinedAt }

        val addedBy = ServiceId.parseOrNull(addedAtChange?.editorServiceIdBytes)?.let { Recipient.externalPush(it) }

        if (addedBy != null) {
          Log.i(TAG, "Added as a full member of $groupId by ${addedBy.id}")

          if (addedBy.isBlocked && (previousGroupState == null || !DecryptedGroupUtil.isRequesting(previousGroupState, aci))) {
            Log.i(TAG, "Added by a blocked user. Leaving group.")
            AppDependencies.jobManager.add(LeaveGroupV2Job(groupId))
            return
          } else if ((addedBy.isSystemContact || addedBy.isProfileSharing) && !addedBy.isHidden) {
            Log.i(TAG, "Group 'adder' is trusted. contact: " + addedBy.isSystemContact + ", profileSharing: " + addedBy.isProfileSharing)
            Log.i(TAG, "Added to a group and auto-enabling profile sharing")
            SignalDatabase.recipients.setProfileSharing(Recipient.externalGroupExact(groupId).id, true)
          } else {
            Log.i(TAG, "Added to a group, but not enabling profile sharing, as 'adder' is not trusted")
          }
        } else {
          Log.w(TAG, "Could not find founding member during gv2 create. Not enabling profile sharing.")
        }
      } else if (selfAsPending != null) {
        val addedBy = UuidUtil.fromByteStringOrNull(selfAsPending.addedByAci)?.let { Recipient.externalPush(ACI.from(it)) }

        if (addedBy?.isBlocked == true) {
          Log.i(TAG, "Added to group $groupId by a blocked user ${addedBy.id}. Leaving group.")
          AppDependencies.jobManager.add(LeaveGroupV2Job(groupId))
          return
        } else {
          Log.i(TAG, "Added to $groupId, but not enabling profile sharing as we are a pending member.")
        }
      } else {
        Log.i(TAG, "Added to $groupId, but not enabling profile sharing as not a fullMember.")
      }
    }

    fun insertUpdateMessages(
      timestamp: Long,
      previousGroupState: DecryptedGroup?,
      processedLogEntries: Collection<AppliedGroupChangeLog>,
      serverGuid: String?
    ): Long {
      var runningTimestamp = timestamp
      var runningGroupState = previousGroupState

      for (entry in processedLogEntries) {
        if (entry.change != null && DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(entry.change) && !DecryptedGroupUtil.changeIsEmpty(entry.change)) {
          Log.d(TAG, "Skipping profile key changes only update message")
        } else if (entry.change != null && DecryptedGroupUtil.changeIsEmptyExceptForBanChangesAndOptionalProfileKeyChanges(entry.change)) {
          Log.d(TAG, "Skipping ban changes only update message")
        } else {
          if (entry.change != null && DecryptedGroupUtil.changeIsEmpty(entry.change) && runningGroupState != null) {
            Log.w(TAG, "Empty group update message seen. Not inserting.")
          } else {
            storeMessage(GroupProtoUtil.createDecryptedGroupV2Context(masterKey, GroupMutation(runningGroupState, entry.change, entry.group), null), runningTimestamp, serverGuid)
            runningTimestamp++
          }
        }

        runningGroupState = entry.group
      }

      return runningTimestamp
    }

    fun leaveGroupLocally(serviceIds: ServiceIds) {
      if (!SignalDatabase.groups.isActive(groupId)) {
        Log.w(TAG, "Group $groupId has already been left.")

        val groupRecipient = Recipient.externalGroupExact(groupId)
        val threadId = SignalDatabase.threads.getThreadIdFor(groupRecipient.id)

        if (threadId != null) {
          SignalDatabase.drafts.clearDrafts(threadId)
          SignalDatabase.threads.update(threadId, unarchive = false, allowDeletion = false)
        }

        return
      }

      val groupRecipient = Recipient.externalGroupExact(groupId)

      val decryptedGroup = SignalDatabase
        .groups
        .requireGroup(groupId)
        .requireV2GroupProperties()
        .decryptedGroup

      val simulatedGroupState = DecryptedGroupUtil.removeMember(decryptedGroup, serviceIds.aci, decryptedGroup.revision + 1)

      val simulatedGroupChange = DecryptedGroupChange(
        editorServiceIdBytes = ACI.UNKNOWN.toByteString(),
        revision = simulatedGroupState.revision,
        deleteMembers = listOf(serviceIds.aci.toByteString())
      )

      val updateDescription = GroupProtoUtil.createOutgoingGroupV2UpdateDescription(masterKey, GroupMutation(decryptedGroup, simulatedGroupChange, simulatedGroupState), null)
      val leaveMessage = OutgoingMessage.groupUpdateMessage(groupRecipient, updateDescription, System.currentTimeMillis())

      try {
        val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(groupRecipient)
        val id = SignalDatabase.messages.insertMessageOutbox(leaveMessage, threadId, false, null)
        SignalDatabase.messages.markAsSent(id, true)
        SignalDatabase.drafts.clearDrafts(threadId)
        SignalDatabase.threads.update(threadId, unarchive = false, allowDeletion = false)
      } catch (e: MmsException) {
        Log.w(TAG, "Failed to insert leave message for $groupId", e)
      }

      SignalDatabase.groups.setActive(groupId, false)
      SignalDatabase.groups.remove(groupId, Recipient.self().id)
    }

    fun persistLearnedProfileKeys(groupStateDiff: GroupStateDiff) {
      val profileKeys = ProfileKeySet()

      for (entry in groupStateDiff.serverHistory) {
        if (entry.group != null) {
          profileKeys.addKeysFromGroupState(entry.group!!)
        }
        if (entry.change != null) {
          profileKeys.addKeysFromGroupChange(entry.change!!)
        }
      }

      persistLearnedProfileKeys(profileKeys)
    }

    fun persistLearnedProfileKeys(profileKeys: ProfileKeySet) {
      val updated = SignalDatabase.recipients.persistProfileKeySet(profileKeys)

      if (updated.isNotEmpty()) {
        Log.i(TAG, "Learned ${updated.size} new profile keys, fetching profiles")

        for (job in RetrieveProfileJob.forRecipients(updated)) {
          AppDependencies.jobManager.runSynchronously(job, 5000)
        }
      }
    }

    @VisibleForTesting
    fun storeMessage(decryptedGroupV2Context: DecryptedGroupV2Context, timestamp: Long, serverGuid: String?) {
      val editor: Optional<ServiceId> = getEditor(decryptedGroupV2Context)

      val serviceIds = SignalStore.account.getServiceIds()
      val outgoing = editor.isEmpty || aci == editor.get()

      val updateDescription = GV2UpdateDescription(
        gv2ChangeDescription = decryptedGroupV2Context,
        groupChangeUpdate = GroupsV2UpdateMessageConverter.translateDecryptedChange(serviceIds, decryptedGroupV2Context)
      )

      if (outgoing) {
        try {
          val recipientId = SignalDatabase.recipients.getOrInsertFromGroupId(groupId)
          val recipient = Recipient.resolved(recipientId)
          val outgoingMessage = OutgoingMessage.groupUpdateMessage(recipient, updateDescription, timestamp)
          val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
          val messageId = SignalDatabase.messages.insertMessageOutbox(outgoingMessage, threadId, false, null)

          SignalDatabase.messages.markAsSent(messageId, true)
          SignalDatabase.threads.update(threadId, unarchive = false, allowDeletion = false)
        } catch (e: MmsException) {
          Log.w(TAG, "Failed to insert outgoing update message!", e)
        }
      } else {
        try {
          val isGroupAdd = updateDescription
            .groupChangeUpdate!!
            .updates
            .asSequence()
            .mapNotNull { it.groupMemberAddedUpdate }
            .any { serviceIds.matches(it.newMemberAci) }

          val groupMessage = IncomingMessage.groupUpdate(RecipientId.from(editor.get()), timestamp, groupId, updateDescription, isGroupAdd, serverGuid)
          val insertResult = SignalDatabase.messages.insertMessageInbox(groupMessage)

          if (insertResult.isPresent) {
            SignalDatabase.threads.update(insertResult.get().threadId, unarchive = false, allowDeletion = false)

            if (isGroupAdd) {
              AppDependencies.messageNotifier.updateNotification(AppDependencies.application)
            }
          } else {
            Log.w(TAG, "Could not insert update message")
          }
        } catch (e: MmsException) {
          Log.w(TAG, "Failed to insert incoming update message!", e)
        }
      }
    }

    private fun getEditor(decryptedGroupV2Context: DecryptedGroupV2Context): Optional<ServiceId> {
      val changeEditor = DecryptedGroupUtil.editorServiceId(decryptedGroupV2Context.change)

      if (changeEditor.isPresent) {
        return changeEditor
      } else {
        val pending = DecryptedGroupUtil.findPendingByServiceId(decryptedGroupV2Context.groupState?.pendingMembers ?: emptyList(), aci)

        if (pending.isPresent) {
          return Optional.ofNullable(ACI.parseOrNull(pending.get().addedByAci))
        }
      }

      return Optional.empty()
    }

    companion object {
      @VisibleForTesting
      fun create(aci: ACI, masterKey: GroupMasterKey, groupId: GroupId.V2): ProfileAndMessageHelper {
        return ProfileAndMessageHelper(aci, masterKey, groupId)
      }
    }
  }

  private sealed interface InternalUpdateResult {
    class Updated(val updatedLocalState: DecryptedGroup) : InternalUpdateResult
    object NoUpdateNeeded : InternalUpdateResult
    class NotAMember(val exception: GroupNotAMemberException) : InternalUpdateResult
    class UpdateFailed(val throwable: Throwable) : InternalUpdateResult

    companion object {
      fun from(cause: Throwable): InternalUpdateResult {
        return when (cause) {
          is NotInGroupException,
          is GroupNotFoundException -> NotAMember(GroupNotAMemberException(cause))

          is IOException -> UpdateFailed(cause)
          else -> UpdateFailed(IOException(cause))
        }
      }
    }
  }
}
