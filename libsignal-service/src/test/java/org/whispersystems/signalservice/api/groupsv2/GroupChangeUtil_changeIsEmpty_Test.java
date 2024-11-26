package org.whispersystems.signalservice.api.groupsv2;

import org.junit.Test;
import org.signal.storageservice.protos.groups.GroupChange;

import java.util.List;

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
                 25, maxFieldFound);
  }

  @Test
  public void empty_change_set() {
    assertTrue(GroupChangeUtil.changeIsEmpty(new GroupChange.Actions.Builder().build()));
  }

  @Test
  public void not_empty_with_add_member_field_3() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .addMembers(List.of(new GroupChange.Actions.AddMemberAction()))
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_delete_member_field_4() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .deleteMembers(List.of(new GroupChange.Actions.DeleteMemberAction()))
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_member_roles_field_5() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .modifyMemberRoles(List.of(new GroupChange.Actions.ModifyMemberRoleAction()))
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_profile_keys_field_6() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .modifyMemberProfileKeys(List.of(new GroupChange.Actions.ModifyMemberProfileKeyAction()))
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_add_pending_members_field_7() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .addPendingMembers(List.of(new GroupChange.Actions.AddPendingMemberAction()))
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_delete_pending_members_field_8() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .deletePendingMembers(List.of(new GroupChange.Actions.DeletePendingMemberAction()))
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_promote_delete_pending_members_field_9() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .promotePendingMembers(List.of(new GroupChange.Actions.PromotePendingMemberAction()))
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_title_field_10() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .modifyTitle(new GroupChange.Actions.ModifyTitleAction())
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_avatar_field_11() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .modifyAvatar(new GroupChange.Actions.ModifyAvatarAction())
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_disappearing_message_timer_field_12() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .modifyDisappearingMessagesTimer(new GroupChange.Actions.ModifyDisappearingMessagesTimerAction())
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_attributes_field_13() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .modifyAttributesAccess(new GroupChange.Actions.ModifyAttributesAccessControlAction())
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_member_access_field_14() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .modifyMemberAccess(new GroupChange.Actions.ModifyMembersAccessControlAction())
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_add_from_invite_link_field_15() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .modifyAddFromInviteLinkAccess(new GroupChange.Actions.ModifyAddFromInviteLinkAccessControlAction())
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_add_requesting_members_field_16() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .addRequestingMembers(List.of(new GroupChange.Actions.AddRequestingMemberAction()))
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_delete_requesting_members_field_17() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .deleteRequestingMembers(List.of(new GroupChange.Actions.DeleteRequestingMemberAction()))
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_promote_requesting_members_field_18() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .promoteRequestingMembers(List.of(new GroupChange.Actions.PromoteRequestingMemberAction()))
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_promote_requesting_members_field_19() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .modifyInviteLinkPassword(new GroupChange.Actions.ModifyInviteLinkPasswordAction())
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_description_field_20() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .modifyDescription(new GroupChange.Actions.ModifyDescriptionAction())
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_modify_description_field_21() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .modifyAnnouncementsOnly(new GroupChange.Actions.ModifyAnnouncementsOnlyAction())
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_add_banned_member_field_22() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .addBannedMembers(List.of(new GroupChange.Actions.AddBannedMemberAction()))
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_delete_banned_member_field_23() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .deleteBannedMembers(List.of(new GroupChange.Actions.DeleteBannedMemberAction()))
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }

  @Test
  public void not_empty_with_promote_pending_pni_aci_members_field_24() {
    GroupChange.Actions actions = new GroupChange.Actions.Builder()
        .promotePendingPniAciMembers(List.of(new GroupChange.Actions.PromotePendingPniAciMemberProfileKeyAction()))
        .build();

    assertFalse(GroupChangeUtil.changeIsEmpty(actions));
  }
}
