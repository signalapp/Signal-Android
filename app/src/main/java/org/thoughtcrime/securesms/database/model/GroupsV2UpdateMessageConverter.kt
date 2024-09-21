/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.model

import okio.ByteString
import org.signal.core.util.StringUtil
import org.signal.core.util.isNullOrEmpty
import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.AccessControl.AccessRequired
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember
import org.signal.storageservice.protos.groups.local.EnabledState
import org.thoughtcrime.securesms.backup.v2.proto.GenericGroupUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupAdminStatusUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupAnnouncementOnlyChangeUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupAttributesAccessLevelChangeUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupAvatarUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupChangeChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupCreationUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupDescriptionUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupExpirationTimerUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupInvitationAcceptedUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupInvitationDeclinedUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupInvitationRevokedUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupInviteLinkAdminApprovalUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupInviteLinkDisabledUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupInviteLinkEnabledUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupInviteLinkResetUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupJoinRequestApprovalUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupJoinRequestCanceledUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupJoinRequestUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupMemberAddedUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupMemberJoinedByLinkUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupMemberJoinedUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupMemberLeftUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupMemberRemovedUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupMembershipAccessLevelChangeUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupNameUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupSelfInvitationRevokedUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupSequenceOfRequestsAndCancelsUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupUnknownInviteeUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupV2AccessLevel
import org.thoughtcrime.securesms.backup.v2.proto.SelfInvitedOtherUserToGroupUpdate
import org.thoughtcrime.securesms.backup.v2.proto.SelfInvitedToGroupUpdate
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil
import org.whispersystems.signalservice.api.push.ServiceId.Companion.parseOrNull
import org.whispersystems.signalservice.api.push.ServiceIds
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.LinkedList
import java.util.Optional
import java.util.stream.Collectors

/**
 * Object to help with the translation between DecryptedGroupV2Context group updates
 * and GroupChangeChatUpdates, which store the update messages as distinct messages rather
 * than diffs of the group state.
 */
object GroupsV2UpdateMessageConverter {

  @JvmStatic
  fun translateDecryptedChange(selfIds: ServiceIds, groupContext: DecryptedGroupV2Context): GroupChangeChatUpdate {
    if (groupContext.change != null && ((groupContext.groupState != null && groupContext.groupState.revision != 0) || groupContext.previousGroupState != null)) {
      return translateDecryptedChangeUpdate(selfIds, groupContext)
    } else {
      return translateDecryptedChangeNewGroup(selfIds, groupContext)
    }
  }

  @JvmStatic
  fun translateDecryptedChangeNewGroup(selfIds: ServiceIds, groupContext: DecryptedGroupV2Context): GroupChangeChatUpdate {
    var selfPending = Optional.empty<DecryptedPendingMember>()
    val decryptedGroupChange = groupContext.change
    val group = groupContext.groupState
    val updates: MutableList<GroupChangeChatUpdate.Update> = LinkedList()

    if (group != null) {
      selfPending = DecryptedGroupUtil.findPendingByServiceId(group.pendingMembers, selfIds.aci)
      if (selfPending.isEmpty() && selfIds.pni != null) {
        selfPending = DecryptedGroupUtil.findPendingByServiceId(group.pendingMembers, selfIds.pni)
      }
    }

    if (selfPending.isPresent) {
      updates.add(
        GroupChangeChatUpdate.Update(
          selfInvitedToGroupUpdate = SelfInvitedToGroupUpdate(inviterAci = selfPending.get().addedByAci)
        )
      )
      return GroupChangeChatUpdate(updates = updates)
    }

    if (decryptedGroupChange != null) {
      val foundingMemberUuid: ByteString = decryptedGroupChange.editorServiceIdBytes
      if (foundingMemberUuid.size > 0) {
        if (selfIds.matches(foundingMemberUuid)) {
          updates.add(
            GroupChangeChatUpdate.Update(
              groupCreationUpdate = GroupCreationUpdate(updaterAci = foundingMemberUuid)
            )
          )
        } else {
          updates.add(
            GroupChangeChatUpdate.Update(
              groupMemberAddedUpdate = GroupMemberAddedUpdate(updaterAci = foundingMemberUuid, newMemberAci = selfIds.aci.toByteString())
            )
          )
        }
        return GroupChangeChatUpdate(updates = updates)
      }
    }

    if (group != null && DecryptedGroupUtil.findMemberByAci(group.members, selfIds.aci).isPresent) {
      updates.add(GroupChangeChatUpdate.Update(groupMemberJoinedUpdate = GroupMemberJoinedUpdate(newMemberAci = selfIds.aci.toByteString())))
    }
    return GroupChangeChatUpdate(updates = updates)
  }

  @JvmStatic
  fun translateDecryptedChangeUpdate(selfIds: ServiceIds, groupContext: DecryptedGroupV2Context): GroupChangeChatUpdate {
    var previousGroupState = groupContext.previousGroupState
    val change = groupContext.change!!
    if (DecryptedGroup() == previousGroupState) {
      previousGroupState = null
    }
    val updates: MutableList<GroupChangeChatUpdate.Update> = LinkedList()
    var editorUnknown = change.editorServiceIdBytes.size == 0
    val editorServiceId = if (editorUnknown) null else parseOrNull(change.editorServiceIdBytes)
    if (editorServiceId == null || editorServiceId.isUnknown) {
      editorUnknown = true
    }
    translateMemberAdditions(change, editorUnknown, updates)
    translateModifyMemberRoles(change, editorUnknown, updates)
    translateInvitations(selfIds, change, editorUnknown, updates)
    translateRevokedInvitations(selfIds, change, editorUnknown, updates)
    translatePromotePending(selfIds, change, editorUnknown, updates)
    translateNewTitle(change, editorUnknown, updates)
    translateNewDescription(change, editorUnknown, updates)
    translateNewAvatar(change, editorUnknown, updates)
    translateNewTimer(change, editorUnknown, updates)
    translateNewAttributeAccess(change, editorUnknown, updates)
    translateNewMembershipAccess(change, editorUnknown, updates)
    translateNewGroupInviteLinkAccess(previousGroupState, change, editorUnknown, updates)
    translateRequestingMembers(selfIds, change, editorUnknown, updates)
    translateRequestingMemberApprovals(selfIds, change, editorUnknown, updates)
    translateRequestingMemberDeletes(selfIds, change, editorUnknown, updates)
    translateAnnouncementGroupChange(change, editorUnknown, updates)
    translatePromotePendingPniAci(selfIds, change, editorUnknown, updates)
    translateMemberRemovals(selfIds, change, editorUnknown, updates)
    if (updates.isEmpty()) {
      translateUnknownChange(change, editorUnknown, updates)
    }
    return GroupChangeChatUpdate(updates = updates)
  }

  @JvmStatic
  fun translateMemberAdditions(change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    for (member in change.newMembers) {
      if (!editorUnknown && member.aciBytes == change.editorServiceIdBytes) {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupMemberJoinedByLinkUpdate = GroupMemberJoinedByLinkUpdate(newMemberAci = member.aciBytes)
          )
        )
      } else {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupMemberAddedUpdate = GroupMemberAddedUpdate(
              updaterAci = if (editorUnknown) null else change.editorServiceIdBytes,
              newMemberAci = member.aciBytes,
              hadOpenInvitation = false
            )
          )
        )
      }
    }
  }

  @JvmStatic
  fun translateModifyMemberRoles(change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    for (roleChange in change.modifyMemberRoles) {
      updates.add(
        GroupChangeChatUpdate.Update(
          groupAdminStatusUpdate = GroupAdminStatusUpdate(
            updaterAci = if (editorUnknown) null else change.editorServiceIdBytes,
            memberAci = roleChange.aciBytes,
            wasAdminStatusGranted = roleChange.role == Member.Role.ADMINISTRATOR
          )
        )
      )
    }
  }

  @JvmStatic
  fun translateInvitations(selfIds: ServiceIds, change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    val editorIsYou = selfIds.matches(change.editorServiceIdBytes)

    var notYouInviteCount = 0
    for (invitee in change.newPendingMembers) {
      val newMemberIsYou = selfIds.matches(invitee.serviceIdBytes)
      if (newMemberIsYou) {
        updates.add(
          GroupChangeChatUpdate.Update(
            selfInvitedToGroupUpdate = SelfInvitedToGroupUpdate(
              inviterAci = if (editorUnknown) convertUnknownUUIDtoNull(invitee.addedByAci) else change.editorServiceIdBytes
            )
          )
        )
      } else {
        if (editorIsYou) {
          updates.add(GroupChangeChatUpdate.Update(selfInvitedOtherUserToGroupUpdate = SelfInvitedOtherUserToGroupUpdate(inviteeServiceId = invitee.serviceIdBytes)))
        } else {
          notYouInviteCount++
        }
      }
    }
    if (notYouInviteCount > 0) {
      updates.add(
        GroupChangeChatUpdate.Update(
          groupUnknownInviteeUpdate = GroupUnknownInviteeUpdate(
            inviterAci = if (editorUnknown) null else change.editorServiceIdBytes,
            inviteeCount = notYouInviteCount
          )
        )
      )
    }
  }

  @JvmStatic
  fun translateRevokedInvitations(selfIds: ServiceIds, change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    val editorAci = if (editorUnknown) null else change.editorServiceIdBytes

    val revokedInvitees = LinkedList<GroupInvitationRevokedUpdate.Invitee>()

    for (invitee in change.deletePendingMembers) {
      val decline = invitee.serviceIdBytes == editorAci
      if (decline) {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupInvitationDeclinedUpdate = GroupInvitationDeclinedUpdate(inviteeAci = invitee.serviceIdBytes)
          )
        )
      } else if (selfIds.matches(invitee.serviceIdBytes)) {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupSelfInvitationRevokedUpdate = GroupSelfInvitationRevokedUpdate(revokerAci = editorAci)
          )
        )
      } else {
        revokedInvitees.add(
          GroupInvitationRevokedUpdate.Invitee(
            inviteeAci = invitee.serviceIdBytes
          )
        )
      }
    }

    if (revokedInvitees.isNotEmpty()) {
      updates.add(
        GroupChangeChatUpdate.Update(
          groupInvitationRevokedUpdate = GroupInvitationRevokedUpdate(
            updaterAci = editorAci,
            invitees = revokedInvitees
          )
        )
      )
    }
  }

  @JvmStatic
  fun translatePromotePending(selfIds: ServiceIds, change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    val editorAci = if (editorUnknown) null else change.editorServiceIdBytes
    val editorIsYou = if (editorUnknown) false else selfIds.matches(editorAci)

    for (member in change.promotePendingMembers) {
      val newMemberIsYou: Boolean = selfIds.matches(member.aciBytes)
      if (editorIsYou) {
        if (newMemberIsYou) {
          updates.add(
            GroupChangeChatUpdate.Update(
              groupInvitationAcceptedUpdate = GroupInvitationAcceptedUpdate(
                inviterAci = null,
                newMemberAci = member.aciBytes
              )
            )
          )
        } else {
          updates.add(
            GroupChangeChatUpdate.Update(
              groupMemberAddedUpdate = GroupMemberAddedUpdate(
                updaterAci = editorAci,
                newMemberAci = member.aciBytes,
                hadOpenInvitation = true
              )
            )
          )
        }
      } else if (editorUnknown) {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupMemberJoinedUpdate = GroupMemberJoinedUpdate(
              newMemberAci = member.aciBytes
            )
          )
        )
      } else if (member.aciBytes == change.editorServiceIdBytes) {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupInvitationAcceptedUpdate = GroupInvitationAcceptedUpdate(
              inviterAci = null,
              newMemberAci = member.aciBytes
            )
          )
        )
      } else {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupMemberAddedUpdate = GroupMemberAddedUpdate(
              updaterAci = editorAci,
              newMemberAci = member.aciBytes,
              hadOpenInvitation = true
            )
          )
        )
      }
    }
  }

  @JvmStatic
  fun translateNewTitle(change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    if (change.newTitle != null) {
      val editorAci = if (editorUnknown) null else change.editorServiceIdBytes
      val newTitle = StringUtil.isolateBidi(change.newTitle?.value_)
      updates.add(
        GroupChangeChatUpdate.Update(
          groupNameUpdate = GroupNameUpdate(
            updaterAci = editorAci,
            newGroupName = newTitle
          )
        )
      )
    }
  }

  @JvmStatic
  fun translateNewDescription(change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    if (change.newDescription != null) {
      val editorAci = if (editorUnknown) null else change.editorServiceIdBytes
      updates.add(
        GroupChangeChatUpdate.Update(
          groupDescriptionUpdate = GroupDescriptionUpdate(
            updaterAci = editorAci,
            newDescription = change.newDescription?.value_
          )
        )
      )
    }
  }

  @JvmStatic
  fun translateNewAvatar(change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    if (change.newAvatar != null) {
      val editorAci = if (editorUnknown) null else change.editorServiceIdBytes
      updates.add(
        GroupChangeChatUpdate.Update(
          groupAvatarUpdate = GroupAvatarUpdate(
            updaterAci = editorAci,
            wasRemoved = change.newAvatar?.value_.isNullOrEmpty()
          )
        )
      )
    }
  }

  @JvmStatic
  fun translateNewTimer(change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    if (change.newTimer != null) {
      updates.add(
        GroupChangeChatUpdate.Update(
          groupExpirationTimerUpdate = GroupExpirationTimerUpdate(
            expiresInMs = (change.newTimer!!.duration * 1000L).toUInt().toLong(),
            updaterAci = if (editorUnknown) null else change.editorServiceIdBytes
          )
        )
      )
    }
  }

  @JvmStatic
  fun translateNewAttributeAccess(change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    if (change.newAttributeAccess != AccessControl.AccessRequired.UNKNOWN) {
      val editorAci = if (editorUnknown) null else change.editorServiceIdBytes
      updates.add(
        GroupChangeChatUpdate.Update(
          groupAttributesAccessLevelChangeUpdate = GroupAttributesAccessLevelChangeUpdate(
            updaterAci = editorAci,
            accessLevel = translateGv2AccessLevel(change.newAttributeAccess)
          )
        )
      )
    }
  }

  private fun translateGv2AccessLevel(accessRequired: AccessRequired): GroupV2AccessLevel {
    return when (accessRequired) {
      AccessRequired.ANY -> GroupV2AccessLevel.ANY
      AccessRequired.MEMBER -> GroupV2AccessLevel.MEMBER
      AccessRequired.ADMINISTRATOR -> GroupV2AccessLevel.ADMINISTRATOR
      AccessRequired.UNSATISFIABLE -> GroupV2AccessLevel.UNSATISFIABLE
      AccessRequired.UNKNOWN -> GroupV2AccessLevel.UNKNOWN
      else -> GroupV2AccessLevel.UNKNOWN
    }
  }

  @JvmStatic
  fun translateNewMembershipAccess(change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    if (change.newMemberAccess !== AccessRequired.UNKNOWN) {
      val editorAci = if (editorUnknown) null else change.editorServiceIdBytes
      updates.add(
        GroupChangeChatUpdate.Update(
          groupMembershipAccessLevelChangeUpdate = GroupMembershipAccessLevelChangeUpdate(
            updaterAci = editorAci,
            accessLevel = translateGv2AccessLevel(change.newMemberAccess)
          )
        )
      )
    }
  }

  @JvmStatic
  fun translateNewGroupInviteLinkAccess(previousGroupState: DecryptedGroup?, change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    var previousAccessControl: AccessRequired? = null

    if (previousGroupState?.accessControl != null) {
      previousAccessControl = previousGroupState.accessControl!!.addFromInviteLink
    }

    var groupLinkEnabled = false
    val editorAci = if (editorUnknown) null else change.editorServiceIdBytes

    when (change.newInviteLinkAccess) {
      AccessRequired.ANY -> {
        groupLinkEnabled = true
        updates.add(
          if (previousAccessControl == AccessControl.AccessRequired.ADMINISTRATOR) {
            GroupChangeChatUpdate.Update(
              groupInviteLinkAdminApprovalUpdate = GroupInviteLinkAdminApprovalUpdate(
                updaterAci = editorAci,
                linkRequiresAdminApproval = false
              )
            )
          } else {
            GroupChangeChatUpdate.Update(
              groupInviteLinkEnabledUpdate = GroupInviteLinkEnabledUpdate(
                updaterAci = editorAci,
                linkRequiresAdminApproval = false
              )
            )
          }
        )
      }
      AccessRequired.ADMINISTRATOR -> {
        groupLinkEnabled = true
        updates.add(
          if (previousAccessControl == AccessControl.AccessRequired.ANY) {
            GroupChangeChatUpdate.Update(
              groupInviteLinkAdminApprovalUpdate = GroupInviteLinkAdminApprovalUpdate(
                updaterAci = editorAci,
                linkRequiresAdminApproval = true
              )
            )
          } else {
            GroupChangeChatUpdate.Update(
              groupInviteLinkEnabledUpdate = GroupInviteLinkEnabledUpdate(
                updaterAci = editorAci,
                linkRequiresAdminApproval = true
              )
            )
          }
        )
      }
      AccessRequired.UNSATISFIABLE -> {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupInviteLinkDisabledUpdate = GroupInviteLinkDisabledUpdate(
              updaterAci = editorAci
            )
          )
        )
      }
      else -> {}
    }
    if (!groupLinkEnabled && change.newInviteLinkPassword.size > 0) {
      updates.add(
        GroupChangeChatUpdate.Update(
          groupInviteLinkResetUpdate = GroupInviteLinkResetUpdate(
            updaterAci = editorAci
          )
        )
      )
    }
  }

  @JvmStatic
  fun translateRequestingMembers(selfIds: ServiceIds, change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    val deleteRequestingUuids: Set<ByteString> = HashSet(change.deleteRequestingMembers)
    for (member in change.newRequestingMembers) {
      val requestingMemberIsYou = selfIds.matches(member.aciBytes)
      if (!requestingMemberIsYou && deleteRequestingUuids.contains(member.aciBytes)) {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupSequenceOfRequestsAndCancelsUpdate = GroupSequenceOfRequestsAndCancelsUpdate(
              requestorAci = member.aciBytes,
              count = change.deleteRequestingMembers.size
            )
          )
        )
      } else {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupJoinRequestUpdate = GroupJoinRequestUpdate(
              requestorAci = member.aciBytes
            )
          )
        )
      }
    }
  }

  @JvmStatic
  fun translateRequestingMemberApprovals(selfIds: ServiceIds, change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    val editorAci = if (editorUnknown) null else change.editorServiceIdBytes
    for (requestingMember in change.promoteRequestingMembers) {
      updates.add(
        GroupChangeChatUpdate.Update(
          groupJoinRequestApprovalUpdate = GroupJoinRequestApprovalUpdate(
            updaterAci = editorAci,
            requestorAci = requestingMember.aciBytes,
            wasApproved = true
          )
        )
      )
    }
  }

  @JvmStatic
  fun translateRequestingMemberDeletes(selfIds: ServiceIds, change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    val newRequestingUuids = change.newRequestingMembers.stream().map { m: DecryptedRequestingMember -> m.aciBytes }.collect(Collectors.toSet())

    val editorIsYou = selfIds.matches(change.editorServiceIdBytes)
    val editorAci = if (editorUnknown) null else change.editorServiceIdBytes
    for (requestingMember in change.deleteRequestingMembers) {
      if (newRequestingUuids.contains(requestingMember)) {
        continue
      }

      val requestingMemberIsYou = selfIds.matches(requestingMember)
      if ((requestingMemberIsYou && editorIsYou) || (change.editorServiceIdBytes == requestingMember)) {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupJoinRequestCanceledUpdate = GroupJoinRequestCanceledUpdate(
              requestorAci = requestingMember
            )
          )
        )
      } else {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupJoinRequestApprovalUpdate = GroupJoinRequestApprovalUpdate(
              requestorAci = requestingMember,
              updaterAci = editorAci,
              wasApproved = false
            )
          )
        )
      }
    }
  }

  @JvmStatic
  fun translateAnnouncementGroupChange(change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    if (change.newIsAnnouncementGroup == EnabledState.ENABLED || change.newIsAnnouncementGroup == EnabledState.DISABLED) {
      val editorAci = if (editorUnknown) null else change.editorServiceIdBytes
      updates.add(
        GroupChangeChatUpdate.Update(
          groupAnnouncementOnlyChangeUpdate = GroupAnnouncementOnlyChangeUpdate(
            updaterAci = editorAci,
            isAnnouncementOnly = change.newIsAnnouncementGroup == EnabledState.ENABLED
          )
        )
      )
    }
  }

  @JvmStatic
  fun translatePromotePendingPniAci(selfIds: ServiceIds, change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    val editorIsYou = selfIds.matches(change.editorServiceIdBytes)
    for (newMember in change.promotePendingPniAciMembers) {
      if (editorUnknown) {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupMemberJoinedUpdate = GroupMemberJoinedUpdate(
              newMemberAci = newMember.aciBytes
            )
          )
        )
      } else {
        if ((selfIds.matches(newMember.aciBytes) && editorIsYou) || newMember.aciBytes == change.editorServiceIdBytes) {
          updates.add(
            GroupChangeChatUpdate.Update(
              groupInvitationAcceptedUpdate = GroupInvitationAcceptedUpdate(
                inviterAci = null,
                newMemberAci = newMember.aciBytes
              )
            )
          )
        } else {
          updates.add(
            GroupChangeChatUpdate.Update(
              groupMemberAddedUpdate = GroupMemberAddedUpdate(
                newMemberAci = newMember.aciBytes,
                updaterAci = change.editorServiceIdBytes,
                hadOpenInvitation = true,
                inviterAci = null
              )
            )
          )
        }
      }
    }
  }

  @JvmStatic
  fun translateMemberRemovals(selfIds: ServiceIds, change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    val editorIsYou: Boolean = selfIds.matches(change.editorServiceIdBytes)
    for (member in change.deleteMembers) {
      val removedMemberIsYou: Boolean = selfIds.matches(member)
      if ((editorIsYou && removedMemberIsYou) || member == change.editorServiceIdBytes) {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupMemberLeftUpdate = GroupMemberLeftUpdate(aci = member)
          )
        )
      } else {
        updates.add(
          GroupChangeChatUpdate.Update(
            groupMemberRemovedUpdate = GroupMemberRemovedUpdate(
              removerAci = if (editorUnknown) null else change.editorServiceIdBytes,
              removedAci = member
            )
          )
        )
      }
    }
  }

  @JvmStatic
  fun translateUnknownChange(change: DecryptedGroupChange, editorUnknown: Boolean, updates: MutableList<GroupChangeChatUpdate.Update>) {
    updates.add(
      GroupChangeChatUpdate.Update(
        genericGroupUpdate = GenericGroupUpdate(
          updaterAci = if (editorUnknown) null else change.editorServiceIdBytes
        )
      )
    )
  }

  private fun convertUnknownUUIDtoNull(id: ByteString?): ByteString? {
    if (id.isNullOrEmpty()) return null
    val uuid = UuidUtil.fromByteStringOrUnknown(id)

    if (UuidUtil.UNKNOWN_UUID == uuid) return null
    return id
  }
}
