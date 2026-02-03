/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:JvmName("DecryptedGroupExtensions")

package org.whispersystems.signalservice.api.groupsv2

import org.signal.core.models.ServiceId
import org.signal.core.models.ServiceId.ACI
import org.signal.storageservice.storage.protos.groups.AccessControl
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.storage.protos.groups.local.DecryptedMember
import org.signal.storageservice.storage.protos.groups.local.DecryptedModifyMemberLabel
import org.signal.storageservice.storage.protos.groups.local.DecryptedPendingMember
import org.signal.storageservice.storage.protos.groups.local.DecryptedRequestingMember
import org.signal.storageservice.storage.protos.groups.local.EnabledState
import java.util.Optional

fun Collection<DecryptedMember>.toAciListWithUnknowns(): List<ACI> {
  return DecryptedGroupUtil.toAciListWithUnknowns(this)
}

fun Collection<DecryptedMember>.toAciList(): List<ACI> {
  return DecryptedGroupUtil.toAciList(this)
}

fun Collection<DecryptedMember>.findMemberByAci(aci: ACI): Optional<DecryptedMember> {
  return DecryptedGroupUtil.findMemberByAci(this, aci)
}

fun Collection<DecryptedRequestingMember>.findRequestingByAci(aci: ACI): Optional<DecryptedRequestingMember> {
  return DecryptedGroupUtil.findRequestingByAci(this, aci)
}

fun Collection<DecryptedPendingMember>.findPendingByServiceId(serviceId: ServiceId): Optional<DecryptedPendingMember> {
  return DecryptedGroupUtil.findPendingByServiceId(this, serviceId)
}

@Throws(NotAbleToApplyGroupV2ChangeException::class)
fun DecryptedGroup.Builder.setModifyMemberLabelActions(
  actions: List<DecryptedModifyMemberLabel>
) {
  val updatedMembers = members.toMutableList()
  actions.forEach { action ->
    val modifiedMemberIndex = updatedMembers.indexOfFirst { it.aciBytes == action.aciBytes }
    if (modifiedMemberIndex < 0) {
      throw NotAbleToApplyGroupV2ChangeException()
    }

    updatedMembers[modifiedMemberIndex] = updatedMembers[modifiedMemberIndex]
      .newBuilder()
      .labelEmoji(action.labelEmoji)
      .labelString(action.labelString)
      .build()
  }

  members = updatedMembers
}

/**
 * Returns the group change fields that contain actual changes (value is not empty or default).
 */
fun DecryptedGroupChange.getChangedFields(): Set<GroupChangeField> {
  return buildSet {
    if (newIsAnnouncementGroup != EnabledState.UNKNOWN) add(GroupChangeField.ANNOUNCEMENT_GROUP)
    if (newAttributeAccess != AccessControl.AccessRequired.UNKNOWN) add(GroupChangeField.ATTRIBUTE_ACCESS)
    if (newAvatar != null) add(GroupChangeField.AVATAR)
    if (deleteBannedMembers.isNotEmpty()) add(GroupChangeField.BANNED_MEMBER_REMOVALS)
    if (newBannedMembers.isNotEmpty()) add(GroupChangeField.BANNED_MEMBERS)
    if (newDescription != null) add(GroupChangeField.DESCRIPTION)
    if (newInviteLinkAccess != AccessControl.AccessRequired.UNKNOWN) add(GroupChangeField.INVITE_LINK_ACCESS)
    if (newInviteLinkPassword.size != 0) add(GroupChangeField.INVITE_LINK_PASSWORD)
    if (newMemberAccess != AccessControl.AccessRequired.UNKNOWN) add(GroupChangeField.MEMBER_ACCESS)
    if (modifyMemberLabels.isNotEmpty()) add(GroupChangeField.MEMBER_LABELS)
    if (deleteMembers.isNotEmpty()) add(GroupChangeField.MEMBER_REMOVALS)
    if (modifyMemberRoles.isNotEmpty()) add(GroupChangeField.MEMBER_ROLES)
    if (newMembers.isNotEmpty()) add(GroupChangeField.MEMBERS)
    if (promotePendingMembers.isNotEmpty()) add(GroupChangeField.PENDING_MEMBER_PROMOTIONS)
    if (deletePendingMembers.isNotEmpty()) add(GroupChangeField.PENDING_MEMBER_REMOVALS)
    if (newPendingMembers.isNotEmpty()) add(GroupChangeField.PENDING_MEMBERS)
    if (promotePendingPniAciMembers.isNotEmpty()) add(GroupChangeField.PNI_ACI_PROMOTIONS)
    if (modifiedProfileKeys.isNotEmpty()) add(GroupChangeField.PROFILE_KEYS)
    if (promoteRequestingMembers.isNotEmpty()) add(GroupChangeField.REQUESTING_MEMBER_APPROVALS)
    if (deleteRequestingMembers.isNotEmpty()) add(GroupChangeField.REQUESTING_MEMBER_REMOVALS)
    if (newRequestingMembers.isNotEmpty()) add(GroupChangeField.REQUESTING_MEMBERS)
    if (newTimer != null) add(GroupChangeField.TIMER)
    if (newTitle != null) add(GroupChangeField.TITLE)
  }
}

/**
 * Returns true if the group change should not be announced to the group members.
 */
@JvmOverloads
fun DecryptedGroupChange.isSilent(
  changedFields: Set<GroupChangeField> = getChangedFields()
): Boolean {
  return GroupChangeField.silentChanges.containsAll(changedFields)
}

/**
 * Fields representing possible changes to a group state.
 * To add a new field, update the enum and add corresponding checks in getChangedFields().
 */
enum class GroupChangeField(val changeSilently: Boolean = false) {
  ANNOUNCEMENT_GROUP,
  ATTRIBUTE_ACCESS,
  AVATAR,
  BANNED_MEMBER_REMOVALS,
  BANNED_MEMBERS(changeSilently = true),
  DESCRIPTION,
  INVITE_LINK_ACCESS,
  INVITE_LINK_PASSWORD,
  MEMBER_ACCESS,
  MEMBER_LABELS(changeSilently = true),
  MEMBER_REMOVALS,
  MEMBER_ROLES,
  MEMBERS,
  PENDING_MEMBER_PROMOTIONS,
  PENDING_MEMBER_REMOVALS,
  PENDING_MEMBERS,
  PNI_ACI_PROMOTIONS,
  PROFILE_KEYS(changeSilently = true),
  REQUESTING_MEMBER_APPROVALS,
  REQUESTING_MEMBER_REMOVALS,
  REQUESTING_MEMBERS,
  TIMER,
  TITLE;

  companion object {
    val silentChanges = GroupChangeField.entries.filter { it.changeSilently }
  }
}
