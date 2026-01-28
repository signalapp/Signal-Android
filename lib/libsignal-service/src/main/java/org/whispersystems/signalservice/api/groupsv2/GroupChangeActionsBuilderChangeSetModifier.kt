package org.whispersystems.signalservice.api.groupsv2

import org.signal.storageservice.storage.protos.groups.GroupChange
import org.signal.storageservice.storage.protos.groups.GroupChange.Actions.AddMemberAction
import org.signal.storageservice.storage.protos.groups.GroupChange.Actions.AddMemberPendingAdminApprovalAction

internal class GroupChangeActionsBuilderChangeSetModifier(private val result: GroupChange.Actions.Builder) : ChangeSetModifier {
  override fun removeAddMembers(i: Int) {
    result.addMembers = result.addMembers.removeIndex(i)
  }

  override fun moveAddToPromote(i: Int) {
    val addMemberAction: AddMemberAction = result.addMembers[i]
    result.addMembers = result.addMembers.removeIndex(i)
    result.promoteMembersPendingProfileKey += GroupChange.Actions.PromoteMemberPendingProfileKeyAction.Builder().presentation(addMemberAction.added!!.presentation).build()
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
    result.addMembersPendingProfileKey = result.addMembersPendingProfileKey.removeIndex(i)
  }

  override fun removeDeletePendingMembers(i: Int) {
    result.deleteMembersPendingProfileKey = result.deleteMembersPendingProfileKey.removeIndex(i)
  }

  override fun removePromotePendingMembers(i: Int) {
    result.promoteMembersPendingProfileKey = result.promoteMembersPendingProfileKey.removeIndex(i)
  }

  override fun clearModifyTitle() {
    result.modifyTitle = null
  }

  override fun clearModifyAvatar() {
    result.modifyAvatar = null
  }

  override fun clearModifyDisappearingMessagesTimer() {
    result.modifyDisappearingMessageTimer= null
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
    result.addMembersPendingAdminApproval = result.addMembersPendingAdminApproval.removeIndex(i)
  }

  override fun moveAddRequestingMembersToPromote(i: Int) {
    val addMemberAction: AddMemberPendingAdminApprovalAction = result.addMembersPendingAdminApproval[i]
    result.addMembersPendingAdminApproval = result.addMembersPendingAdminApproval.removeIndex(i)
    result.promoteMembersPendingProfileKey += GroupChange.Actions.PromoteMemberPendingProfileKeyAction.Builder().presentation(addMemberAction.added!!.presentation).build()
  }

  override fun removeDeleteRequestingMembers(i: Int) {
    result.deleteMembersPendingAdminApproval = result.deleteMembersPendingAdminApproval.removeIndex(i)
  }

  override fun removePromoteRequestingMembers(i: Int) {
    result.promoteMembersPendingAdminApproval = result.promoteMembersPendingAdminApproval.removeIndex(i)
  }

  override fun clearModifyDescription() {
    result.modifyDescription = null
  }

  override fun clearModifyAnnouncementsOnly() {
    result.modify_announcements_only = null
  }

  override fun removeAddBannedMembers(i: Int) {
    result.add_members_banned = result.add_members_banned.removeIndex(i)
  }

  override fun removeDeleteBannedMembers(i: Int) {
    result.delete_members_banned = result.delete_members_banned.removeIndex(i)
  }

  override fun removePromotePendingPniAciMembers(i: Int) {
    result.promote_members_pending_pni_aci_profile_key = result.promote_members_pending_pni_aci_profile_key.removeIndex(i)
  }

  override fun removeModifyMemberLabels(i: Int) {
    result.modifyMemberLabel = result.modifyMemberLabel.removeIndex(i)
  }

  private fun <T> List<T>.removeIndex(i: Int): List<T> {
    val modifiedList = this.toMutableList()
    modifiedList.removeAt(i)
    return modifiedList
  }
}
