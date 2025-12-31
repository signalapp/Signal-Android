package org.whispersystems.signalservice.api.groupsv2

import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember
import org.signal.storageservice.protos.groups.local.EnabledState

internal class DecryptedGroupChangeActionsBuilderChangeSetModifier(private val result: DecryptedGroupChange.Builder) : ChangeSetModifier {
  override fun removeAddMembers(i: Int) {
    result.newMembers = result.newMembers.removeIndex(i)
  }

  override fun moveAddToPromote(i: Int) {
    val addMemberAction: DecryptedMember = result.newMembers[i]
    result.newMembers = result.newMembers.removeIndex(i)
    result.promotePendingMembers += addMemberAction
  }

  override fun removeDeleteMembers(i: Int) {
    result.deleteMembers = result.deleteMembers.removeIndex(i)
  }

  override fun removeModifyMemberRoles(i: Int) {
    result.modifyMemberRoles = result.modifyMemberRoles.removeIndex(i)
  }

  override fun removeModifyMemberProfileKeys(i: Int) {
    result.modifiedProfileKeys = result.modifiedProfileKeys.removeIndex(i)
  }

  override fun removeAddPendingMembers(i: Int) {
    result.newPendingMembers = result.newPendingMembers.removeIndex(i)
  }

  override fun removeDeletePendingMembers(i: Int) {
    result.deletePendingMembers = result.deletePendingMembers.removeIndex(i)
  }

  override fun removePromotePendingMembers(i: Int) {
    result.promotePendingMembers = result.promotePendingMembers.removeIndex(i)
  }

  override fun clearModifyTitle() {
    result.newTitle = null
  }

  override fun clearModifyAvatar() {
    result.newAvatar = null
  }

  override fun clearModifyDisappearingMessagesTimer() {
    result.newTimer = null
  }

  override fun clearModifyAttributesAccess() {
    result.newAttributeAccess = AccessControl.AccessRequired.UNKNOWN
  }

  override fun clearModifyMemberAccess() {
    result.newMemberAccess = AccessControl.AccessRequired.UNKNOWN
  }

  override fun clearModifyAddFromInviteLinkAccess() {
    result.newInviteLinkAccess = AccessControl.AccessRequired.UNKNOWN
  }

  override fun removeAddRequestingMembers(i: Int) {
    result.newRequestingMembers = result.newRequestingMembers.removeIndex(i)
  }

  override fun moveAddRequestingMembersToPromote(i: Int) {
    val addMemberAction: DecryptedRequestingMember = result.newRequestingMembers[i]
    result.newRequestingMembers = result.newRequestingMembers.removeIndex(i)

    val promote = DecryptedMember(
      aciBytes = addMemberAction.aciBytes,
      profileKey = addMemberAction.profileKey,
      role = Member.Role.DEFAULT
    )
    result.promotePendingMembers += promote
  }

  override fun removeDeleteRequestingMembers(i: Int) {
    result.deleteRequestingMembers = result.deleteRequestingMembers.removeIndex(i)
  }

  override fun removePromoteRequestingMembers(i: Int) {
    result.promoteRequestingMembers = result.promoteRequestingMembers.removeIndex(i)
  }

  override fun clearModifyDescription() {
    result.newDescription = null
  }

  override fun clearModifyAnnouncementsOnly() {
    result.newIsAnnouncementGroup = EnabledState.UNKNOWN
  }

  override fun removeAddBannedMembers(i: Int) {
    result.newBannedMembers = result.newBannedMembers.removeIndex(i)
  }

  override fun removeDeleteBannedMembers(i: Int) {
    result.deleteBannedMembers = result.deleteBannedMembers.removeIndex(i)
  }

  override fun removePromotePendingPniAciMembers(i: Int) {
    result.promotePendingPniAciMembers = result.promotePendingPniAciMembers.removeIndex(i)
  }

  private fun <T> List<T>.removeIndex(i: Int): List<T> {
    val modifiedList = this.toMutableList()
    modifiedList.removeAt(i)
    return modifiedList
  }
}
