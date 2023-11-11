package org.whispersystems.signalservice.api.groupsv2;

import org.junit.Test;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.List;
import java.util.UUID;

import okio.ByteString;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.member;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMemberRemoval;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingPniAciMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.promoteAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.randomProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber;

public final class DecryptedGroupUtil_empty_Test {

  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   * <p>
   * If we didn't, newly added fields would easily affect {@link DecryptedGroupUtil}'s ability to detect non-empty change states.
   */
  @Test
  public void ensure_DecryptedGroupUtil_knows_about_all_fields_of_DecryptedGroupChange() {
    int maxFieldFound = getMaxDeclaredFieldNumber(DecryptedGroupChange.class);

    assertEquals("DecryptedGroupUtil and its tests need updating to account for new fields on " + DecryptedGroupChange.class.getName(),
                 24, maxFieldFound);
  }

  @Test
  public void empty_change_set() {
    assertTrue(DecryptedGroupUtil.changeIsEmpty(new DecryptedGroupChange.Builder().build()));
  }

  @Test
  public void not_empty_with_add_member_field_3() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newMembers(List.of(member(UUID.randomUUID())))
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_delete_member_field_4() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .deleteMembers(List.of(UuidUtil.toByteString(UUID.randomUUID())))
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_member_roles_field_5() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .modifyMemberRoles(List.of(promoteAdmin(UUID.randomUUID())))
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_profile_keys_field_6() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .modifiedProfileKeys(List.of(member(UUID.randomUUID(), randomProfileKey())))
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertTrue(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_add_pending_members_field_7() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newPendingMembers(List.of(pendingMember(UUID.randomUUID())))
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_delete_pending_members_field_8() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .deletePendingMembers(List.of(pendingMemberRemoval(UUID.randomUUID())))
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_promote_delete_pending_members_field_9() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .promotePendingMembers(List.of(member(UUID.randomUUID())))
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_title_field_10() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newTitle(new DecryptedString.Builder().value_("New title").build())
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_avatar_field_11() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newAvatar(new DecryptedString.Builder().value_("New Avatar").build())
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_disappearing_message_timer_field_12() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newTimer(new DecryptedTimer.Builder().duration(60).build())
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_attributes_field_13() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newAttributeAccess(AccessControl.AccessRequired.ADMINISTRATOR)
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_member_access_field_14() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newMemberAccess(AccessControl.AccessRequired.MEMBER)
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_add_from_invite_link_access_field_15() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_an_add_requesting_member_field_16() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newRequestingMembers(List.of(new DecryptedRequestingMember()))
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_a_delete_requesting_member_field_17() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .deleteRequestingMembers(List.of(ByteString.of(new byte[16])))
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_a_promote_requesting_member_field_18() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .promoteRequestingMembers(List.of(new DecryptedApproveMember()))
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_a_new_invite_link_password_19() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newInviteLinkPassword(ByteString.of(new byte[16]))
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_description_field_20() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newDescription(new DecryptedString.Builder().value_("New description").build())
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_announcement_field_21() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newIsAnnouncementGroup(EnabledState.ENABLED)
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_add_banned_member_field_22() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .newBannedMembers(List.of(new DecryptedBannedMember()))
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_delete_banned_member_field_23() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .deleteBannedMembers(List.of(new DecryptedBannedMember()))
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_promote_pending_pni_aci_members_field_24() {
    DecryptedGroupChange change = new DecryptedGroupChange.Builder()
        .promotePendingPniAciMembers(List.of(pendingPniAciMember(UUID.randomUUID(), UUID.randomUUID(), randomProfileKey())))
        .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }
}
