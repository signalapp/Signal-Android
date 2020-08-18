package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.member;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMemberRemoval;
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
                 19, maxFieldFound);
  }

  @Test
  public void empty_change_set() {
    assertTrue(DecryptedGroupUtil.changeIsEmpty(DecryptedGroupChange.newBuilder().build()));
  }

  @Test
  public void not_empty_with_add_member_field_3() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .addNewMembers(member(UUID.randomUUID()))
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_delete_member_field_4() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .addDeleteMembers(UuidUtil.toByteString(UUID.randomUUID()))
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_member_roles_field_5() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .addModifyMemberRoles(promoteAdmin(UUID.randomUUID()))
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_profile_keys_field_6() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .addModifiedProfileKeys(member(UUID.randomUUID(), randomProfileKey()))
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertTrue(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_add_pending_members_field_7() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .addNewPendingMembers(pendingMember(UUID.randomUUID()))
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_delete_pending_members_field_8() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .addDeletePendingMembers(pendingMemberRemoval(UUID.randomUUID()))
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_promote_delete_pending_members_field_9() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .addPromotePendingMembers(member(UUID.randomUUID()))
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_title_field_10() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .setNewTitle(DecryptedString.newBuilder().setValue("New title"))
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_avatar_field_11() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .setNewAvatar(DecryptedString.newBuilder().setValue("New Avatar"))
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_disappearing_message_timer_field_12() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .setNewTimer(DecryptedTimer.newBuilder().setDuration(60))
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_attributes_field_13() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .setNewAttributeAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_member_access_field_14() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .setNewMemberAccess(AccessControl.AccessRequired.MEMBER)
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_modify_add_from_invite_link_access_field_15() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .setNewInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_an_add_requesting_member_field_16() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .addNewRequestingMembers(DecryptedRequestingMember.getDefaultInstance())
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_a_delete_requesting_member_field_17() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .addDeleteRequestingMembers(ByteString.copyFrom(new byte[16]))
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

  @Test
  public void not_empty_with_a_promote_requesting_member_field_18() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .addPromoteRequestingMembers(DecryptedApproveMember.getDefaultInstance())
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }

    @Test
  public void not_empty_with_a_new_invite_link_password_19() {
    DecryptedGroupChange change = DecryptedGroupChange.newBuilder()
                                                      .setNewInviteLinkPassword(ByteString.copyFrom(new byte[16]))
                                                      .build();

    assertFalse(DecryptedGroupUtil.changeIsEmpty(change));
    assertFalse(DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(change));
  }
}
