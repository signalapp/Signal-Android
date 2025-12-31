package org.whispersystems.signalservice.api.groupsv2

import org.signal.storageservice.protos.groups.GroupChange
import org.signal.storageservice.protos.groups.GroupChange.Actions.AddMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.AddRequestingMemberAction

internal class GroupChangeActionsBuilderChangeSetModifier(private val result: GroupChange.Actions.Builder) : ChangeSetModifier {
  override fun removeAddMembers(i: Int) {
    result.addMembers = result.addMembers.removeIndex(i)
  }

  override fun moveAddToPromote(i: Int) {
    val addMemberAction: AddMemberAction = result.addMembers[i]
    result.addMembers = result.addMembers.removeIndex(i)
    result.promotePendingMembers += GroupChange.Actions.PromotePendingMemberAction.Builder().presentation(addMemberAction.added!!.presentation).build()
  }

  override fun removeDeleteMembers(i: Int) {
    result.deleteMembers = result.deleteMembers.removeIndex(i)
  }

  override fun removeModifyMemberRoles(i: Int) {
    result.modifyMemberRoles = result.modifyMemberRoles.removeIndex(i)
  }

  override fun removeModifyMemberProfileKeys(i: Int) {
    result.modifyMemberProfileKeys = result.modifyMemberProfileKeys.removeIndex(i)
  }

  override fun removeAddPendingMembers(i: Int) {
    result.addPendingMembers = result.addPendingMembers.removeIndex(i)
  }

  override fun removeDeletePendingMembers(i: Int) {
    result.deletePendingMembers = result.deletePendingMembers.removeIndex(i)
  }

  override fun removePromotePendingMembers(i: Int) {
    result.promotePendingMembers = result.promotePendingMembers.removeIndex(i)
  }

  override fun clearModifyTitle() {
    result.modifyTitle = null
  }

  override fun clearModifyAvatar() {
    result.modifyAvatar = null
  }

  override fun clearModifyDisappearingMessagesTimer() {
    result.modifyDisappearingMessagesTimer = null
  }

  override fun clearModifyAttributesAccess() {
    result.modifyAttributesAccess = null
  }

  override fun clearModifyMemberAccess() {
    result.modifyMemberAccess = null
  }

  override fun clearModifyAddFromInviteLinkAccess() {
    result.modifyAddFromInviteLinkAccess = null
  }

  override fun removeAddRequestingMembers(i: Int) {
    result.addRequestingMembers = result.addRequestingMembers.removeIndex(i)
  }

  override fun moveAddRequestingMembersToPromote(i: Int) {
    val addMemberAction: AddRequestingMemberAction = result.addRequestingMembers[i]
    result.addRequestingMembers = result.addRequestingMembers.removeIndex(i)
    result.promotePendingMembers += GroupChange.Actions.PromotePendingMemberAction.Builder().presentation(addMemberAction.added!!.presentation).build()
  }

  override fun removeDeleteRequestingMembers(i: Int) {
    result.deleteRequestingMembers = result.deleteRequestingMembers.removeIndex(i)
  }

  override fun removePromoteRequestingMembers(i: Int) {
    result.promoteRequestingMembers = result.promoteRequestingMembers.removeIndex(i)
  }

  override fun clearModifyDescription() {
    result.modifyDescription = null
  }

  override fun clearModifyAnnouncementsOnly() {
    result.modifyAnnouncementsOnly = null
  }

  override fun removeAddBannedMembers(i: Int) {
    result.addBannedMembers = result.addBannedMembers.removeIndex(i)
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
