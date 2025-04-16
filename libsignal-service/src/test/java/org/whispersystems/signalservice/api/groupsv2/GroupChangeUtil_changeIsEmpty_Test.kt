package org.whispersystems.signalservice.api.groupsv2

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.signal.storageservice.protos.groups.GroupChange
import org.signal.storageservice.protos.groups.GroupChange.Actions.AddBannedMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.AddMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.AddPendingMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.AddRequestingMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.DeleteBannedMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.DeleteMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.DeletePendingMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.DeleteRequestingMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.ModifyAddFromInviteLinkAccessControlAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.ModifyAnnouncementsOnlyAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.ModifyAttributesAccessControlAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.ModifyAvatarAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.ModifyDescriptionAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.ModifyDisappearingMessagesTimerAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.ModifyInviteLinkPasswordAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.ModifyMemberProfileKeyAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.ModifyMemberRoleAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.ModifyMembersAccessControlAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.ModifyTitleAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.PromotePendingMemberAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.PromotePendingPniAciMemberProfileKeyAction
import org.signal.storageservice.protos.groups.GroupChange.Actions.PromoteRequestingMemberAction

class GroupChangeUtil_changeIsEmpty_Test {
  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   *
   *
   * If we didn't, newly added fields would easily affect [GroupChangeUtil]'s ability to detect empty change states and resolve conflicts.
   */
  @Test
  fun ensure_GroupChangeUtil_knows_about_all_fields_of_GroupChange_Actions() {
    val maxFieldFound = ProtobufTestUtils.getMaxDeclaredFieldNumber(GroupChange.Actions::class.java)

    assertThat(
      actual = maxFieldFound,
      name = "GroupChangeUtil and its tests need updating to account for new fields on " + GroupChange.Actions::class.java.name
    ).isEqualTo(25)
  }

  @Test
  fun empty_change_set() {
    assertThat(GroupChangeUtil.changeIsEmpty(GroupChange.Actions.Builder().build())).isTrue()
  }

  @Test
  fun not_empty_with_add_member_field_3() {
    val actions = GroupChange.Actions.Builder()
      .addMembers(listOf(AddMemberAction()))
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_delete_member_field_4() {
    val actions = GroupChange.Actions.Builder()
      .deleteMembers(listOf(DeleteMemberAction()))
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_modify_member_roles_field_5() {
    val actions = GroupChange.Actions.Builder()
      .modifyMemberRoles(listOf(ModifyMemberRoleAction()))
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_modify_profile_keys_field_6() {
    val actions = GroupChange.Actions.Builder()
      .modifyMemberProfileKeys(listOf(ModifyMemberProfileKeyAction()))
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_add_pending_members_field_7() {
    val actions = GroupChange.Actions.Builder()
      .addPendingMembers(listOf(AddPendingMemberAction()))
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_delete_pending_members_field_8() {
    val actions = GroupChange.Actions.Builder()
      .deletePendingMembers(listOf(DeletePendingMemberAction()))
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_promote_delete_pending_members_field_9() {
    val actions = GroupChange.Actions.Builder()
      .promotePendingMembers(listOf(PromotePendingMemberAction()))
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_modify_title_field_10() {
    val actions = GroupChange.Actions.Builder()
      .modifyTitle(ModifyTitleAction())
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_modify_avatar_field_11() {
    val actions = GroupChange.Actions.Builder()
      .modifyAvatar(ModifyAvatarAction())
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_modify_disappearing_message_timer_field_12() {
    val actions = GroupChange.Actions.Builder()
      .modifyDisappearingMessagesTimer(ModifyDisappearingMessagesTimerAction())
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_modify_attributes_field_13() {
    val actions = GroupChange.Actions.Builder()
      .modifyAttributesAccess(ModifyAttributesAccessControlAction())
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_modify_member_access_field_14() {
    val actions = GroupChange.Actions.Builder()
      .modifyMemberAccess(ModifyMembersAccessControlAction())
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_modify_add_from_invite_link_field_15() {
    val actions = GroupChange.Actions.Builder()
      .modifyAddFromInviteLinkAccess(ModifyAddFromInviteLinkAccessControlAction())
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_add_requesting_members_field_16() {
    val actions = GroupChange.Actions.Builder()
      .addRequestingMembers(listOf(AddRequestingMemberAction()))
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_delete_requesting_members_field_17() {
    val actions = GroupChange.Actions.Builder()
      .deleteRequestingMembers(listOf(DeleteRequestingMemberAction()))
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_promote_requesting_members_field_18() {
    val actions = GroupChange.Actions.Builder()
      .promoteRequestingMembers(listOf(PromoteRequestingMemberAction()))
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_promote_requesting_members_field_19() {
    val actions = GroupChange.Actions.Builder()
      .modifyInviteLinkPassword(ModifyInviteLinkPasswordAction())
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_modify_description_field_20() {
    val actions = GroupChange.Actions.Builder()
      .modifyDescription(ModifyDescriptionAction())
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_modify_description_field_21() {
    val actions = GroupChange.Actions.Builder()
      .modifyAnnouncementsOnly(ModifyAnnouncementsOnlyAction())
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_add_banned_member_field_22() {
    val actions = GroupChange.Actions.Builder()
      .addBannedMembers(listOf(AddBannedMemberAction()))
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_delete_banned_member_field_23() {
    val actions = GroupChange.Actions.Builder()
      .deleteBannedMembers(listOf(DeleteBannedMemberAction()))
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }

  @Test
  fun not_empty_with_promote_pending_pni_aci_members_field_24() {
    val actions = GroupChange.Actions.Builder()
      .promotePendingPniAciMembers(listOf(PromotePendingPniAciMemberProfileKeyAction()))
      .build()

    assertThat(GroupChangeUtil.changeIsEmpty(actions)).isFalse()
  }
}
