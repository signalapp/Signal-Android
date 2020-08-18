package org.whispersystems.signalservice.api.groupsv2;

import org.junit.Test;
import org.signal.storageservice.protos.groups.GroupChange;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber;

public final class GroupChangeUtil_changeIsEmpty_Test {

  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   * <p>
   * If we didn't, newly added fields would easily affect {@link GroupChangeUtil}'s ability to detect empty change states and resolve conflicts.
   */
  @Test
  public void ensure_GroupChangeUtil_knows_about_all_fields_of_GroupChange_Actions() {
    int maxFieldFound = getMaxDeclaredFieldNumber(GroupChange.Actions.class);

    assertEquals("GroupChangeUtil and its tests need updating to account for new fields on " + GroupChange.Actions.class.getName(),
                 19, maxFieldFound);
  }

  @Test
  public void empty_change_set() {
    assertTrue(GroupChangeUtil.changeIsEmpty(GroupChange.Actions.newBuilder().build()));
  }

  @Test
  public void not_empty_with_add_member_field_3() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .addAddMembers(GroupChange.Actions.AddMemberAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_delete_member_field_4() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .addDeleteMembers(GroupChange.Actions.DeleteMemberAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_member_roles_field_5() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .addModifyMemberRoles(GroupChange.Actions.ModifyMemberRoleAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_profile_keys_field_6() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .addModifyMemberProfileKeys(GroupChange.Actions.ModifyMemberProfileKeyAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_add_pending_members_field_7() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .addAddPendingMembers(GroupChange.Actions.AddPendingMemberAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_delete_pending_members_field_8() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .addDeletePendingMembers(GroupChange.Actions.DeletePendingMemberAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_promote_delete_pending_members_field_9() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .addPromotePendingMembers(GroupChange.Actions.PromotePendingMemberAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_title_field_10() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .setModifyTitle(GroupChange.Actions.ModifyTitleAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_avatar_field_11() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .setModifyAvatar(GroupChange.Actions.ModifyAvatarAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_disappearing_message_timer_field_12() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .setModifyDisappearingMessagesTimer(GroupChange.Actions.ModifyDisappearingMessagesTimerAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_attributes_field_13() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .setModifyAttributesAccess(GroupChange.Actions.ModifyAttributesAccessControlAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_member_access_field_14() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .setModifyMemberAccess(GroupChange.Actions.ModifyMembersAccessControlAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_add_from_invite_link_field_15() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .setModifyAddFromInviteLinkAccess(GroupChange.Actions.ModifyAddFromInviteLinkAccessControlAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_add_requesting_members_field_16() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .addAddRequestingMembers(GroupChange.Actions.AddRequestingMemberAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_delete_requesting_members_field_17() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .addDeleteRequestingMembers(GroupChange.Actions.DeleteRequestingMemberAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_promote_requesting_members_field_18() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .addPromoteRequestingMembers(GroupChange.Actions.PromoteRequestingMemberAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_promote_requesting_members_field_19() {
    GroupChange.Actions actions = GroupChange.Actions.newBuilder()
                                                     .setModifyInviteLinkPassword(GroupChange.Actions.ModifyInviteLinkPasswordAction.getDefaultInstance())
                                                     .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }
}
