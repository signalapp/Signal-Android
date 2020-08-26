package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.util.Util;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.admin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.approveMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.demoteAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.member;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMemberRemoval;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.promoteAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.randomProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.requestingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber;

public final class GroupChangeUtil_resolveConflict_decryptedOnly_Test {

  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   * <p>
   * If we didn't, newly added fields would not be resolved by {@link GroupChangeUtil#resolveConflict(DecryptedGroup, DecryptedGroupChange)}.
   */
  @Test
  public void ensure_resolveConflict_knows_about_all_fields_of_DecryptedGroupChange() {
    int maxFieldFound = getMaxDeclaredFieldNumber(DecryptedGroupChange.class);

    assertEquals("GroupChangeUtil#resolveConflict and its tests need updating to account for new fields on " + DecryptedGroupChange.class.getName(),
                 19, maxFieldFound);
  }

  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   * <p>
   * If we didn't, newly added fields would not be resolved by {@link GroupChangeUtil#resolveConflict(DecryptedGroup, DecryptedGroupChange)}.
   */
  @Test
  public void ensure_resolveConflict_knows_about_all_fields_of_DecryptedGroup() {
    int maxFieldFound = getMaxDeclaredFieldNumber(DecryptedGroup.class);

    assertEquals("GroupChangeUtil#resolveConflict and its tests need updating to account for new fields on " + DecryptedGroup.class.getName(),
                 10, maxFieldFound);
  }


  @Test
  public void field_3__changes_to_add_existing_members_are_excluded() {
    UUID                 member1         = UUID.randomUUID();
    UUID                 member2         = UUID.randomUUID();
    UUID                 member3         = UUID.randomUUID();
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .addMembers(member(member1))
                                                         .addMembers(member(member3))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .addNewMembers(member(member1))
                                                               .addNewMembers(member(member2))
                                                               .addNewMembers(member(member3))
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    DecryptedGroupChange expected = DecryptedGroupChange.newBuilder()
                                                               .addNewMembers(member(member2))
                                                               .build();
    assertEquals(expected, resolvedChanges);
  }

  @Test
  public void field_4__changes_to_remove_missing_members_are_excluded() {
    UUID                 member1         = UUID.randomUUID();
    UUID                 member2         = UUID.randomUUID();
    UUID                 member3         = UUID.randomUUID();
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .addMembers(member(member2))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .addDeleteMembers(UuidUtil.toByteString(member1))
                                                               .addDeleteMembers(UuidUtil.toByteString(member2))
                                                               .addDeleteMembers(UuidUtil.toByteString(member3))
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    DecryptedGroupChange expected = DecryptedGroupChange.newBuilder()
                                                        .addDeleteMembers(UuidUtil.toByteString(member2))
                                                        .build();

    assertEquals(expected, resolvedChanges);
  }

  @Test
  public void field_5__role_change_is_preserved() {
    UUID                 member1         = UUID.randomUUID();
    UUID                 member2         = UUID.randomUUID();
    UUID                 member3         = UUID.randomUUID();
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .addMembers(admin(member1))
                                                         .addMembers(member(member2))
                                                         .addMembers(member(member3))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .addModifyMemberRoles(demoteAdmin(member1))
                                                               .addModifyMemberRoles(promoteAdmin(member2))
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    assertEquals(decryptedChange, resolvedChanges);
  }

  @Test
  public void field_5__unnecessary_role_changes_removed() {
    UUID                 member1          = UUID.randomUUID();
    UUID                 member2          = UUID.randomUUID();
    UUID                 member3          = UUID.randomUUID();
    UUID                 memberNotInGroup = UUID.randomUUID();
    DecryptedGroup       groupState       = DecryptedGroup.newBuilder()
                                                          .addMembers(admin(member1))
                                                          .addMembers(member(member2))
                                                          .addMembers(member(member3))
                                                          .build();
    DecryptedGroupChange decryptedChange  = DecryptedGroupChange.newBuilder()
                                                                .addModifyMemberRoles(promoteAdmin(member1))
                                                                .addModifyMemberRoles(promoteAdmin(member2))
                                                                .addModifyMemberRoles(demoteAdmin(member3))
                                                                .addModifyMemberRoles(promoteAdmin(memberNotInGroup))
                                                                .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();


    DecryptedGroupChange expected  = DecryptedGroupChange.newBuilder()
                                                         .addModifyMemberRoles(promoteAdmin(member2))
                                                         .build();

    assertEquals(expected, resolvedChanges);
  }

  @Test
  public void field_6__profile_key_changes() {
    UUID                 member1          = UUID.randomUUID();
    UUID                 member2          = UUID.randomUUID();
    UUID                 member3          = UUID.randomUUID();
    UUID                 memberNotInGroup = UUID.randomUUID();
    ProfileKey           profileKey1      = randomProfileKey();
    ProfileKey           profileKey2      = randomProfileKey();
    ProfileKey           profileKey3      = randomProfileKey();
    ProfileKey           profileKey4      = randomProfileKey();
    ProfileKey           profileKey2b     = randomProfileKey();
    DecryptedGroup       groupState       = DecryptedGroup.newBuilder()
                                                          .addMembers(member(member1, profileKey1))
                                                          .addMembers(member(member2, profileKey2))
                                                          .addMembers(member(member3, profileKey3))
                                                          .build();
    DecryptedGroupChange decryptedChange  = DecryptedGroupChange.newBuilder()
                                                                .addModifiedProfileKeys(member(member1, profileKey1))
                                                                .addModifiedProfileKeys(member(member2, profileKey2b))
                                                                .addModifiedProfileKeys(member(member3, profileKey3))
                                                                .addModifiedProfileKeys(member(memberNotInGroup, profileKey4))
                                                                .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    DecryptedGroupChange expected  = DecryptedGroupChange.newBuilder()
                                                         .addModifiedProfileKeys(member(member2, profileKey2b))
                                                         .build();

    assertEquals(expected, resolvedChanges);
  }

  @Test
  public void field_7__add_pending_members() {
    UUID                 member1         = UUID.randomUUID();
    UUID                 member2         = UUID.randomUUID();
    UUID                 member3         = UUID.randomUUID();
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .addMembers(member(member1))
                                                         .addPendingMembers(pendingMember(member3))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .addNewPendingMembers(pendingMember(member1))
                                                               .addNewPendingMembers(pendingMember(member2))
                                                               .addNewPendingMembers(pendingMember(member3))
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    DecryptedGroupChange expected = DecryptedGroupChange.newBuilder()
                                                        .addNewPendingMembers(pendingMember(member2))
                                                        .build();

    assertEquals(expected, resolvedChanges);
  }

  @Test
  public void field_8__delete_pending_members() {
    UUID                 member1         = UUID.randomUUID();
    UUID                 member2         = UUID.randomUUID();
    UUID                 member3         = UUID.randomUUID();
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .addMembers(member(member1))
                                                         .addPendingMembers(pendingMember(member2))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .addDeletePendingMembers(pendingMemberRemoval(member1))
                                                               .addDeletePendingMembers(pendingMemberRemoval(member2))
                                                               .addDeletePendingMembers(pendingMemberRemoval(member3))
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    DecryptedGroupChange expected = DecryptedGroupChange.newBuilder()
                                                        .addDeletePendingMembers(pendingMemberRemoval(member2))
                                                        .build();

    assertEquals(expected, resolvedChanges);
  }

  @Test
  public void field_9__promote_pending_members() {
    UUID                 member1         = UUID.randomUUID();
    UUID                 member2         = UUID.randomUUID();
    UUID                 member3         = UUID.randomUUID();
    ProfileKey           profileKey2     = randomProfileKey();
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .addMembers(member(member1))
                                                         .addPendingMembers(pendingMember(member2))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .addPromotePendingMembers(member(member1))
                                                               .addPromotePendingMembers(member(member2, profileKey2))
                                                               .addPromotePendingMembers(member(member3))
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    DecryptedGroupChange expected = DecryptedGroupChange.newBuilder()
                                                        .addPromotePendingMembers(member(member2, profileKey2))
                                                        .build();
    assertEquals(expected, resolvedChanges);
  }

  @Test
  public void field_3_to_9__add_of_pending_member_converted_to_a_promote() {
    UUID                 member1         = UUID.randomUUID();
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .addPendingMembers(pendingMember(member1))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .addNewMembers(member(member1))
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    DecryptedGroupChange expected = DecryptedGroupChange.newBuilder()
                                                        .addPromotePendingMembers(member(member1))
                                                        .build();

    assertEquals(expected, resolvedChanges);
  }

  @Test
  public void field_10__title_change_is_preserved() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setTitle("Existing title")
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewTitle(DecryptedString.newBuilder().setValue("New title").build())
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    assertEquals(decryptedChange, resolvedChanges);
  }

  @Test
  public void field_10__no_title_change_is_removed() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setTitle("Existing title")
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewTitle(DecryptedString.newBuilder().setValue("Existing title").build())
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    assertTrue(DecryptedGroupUtil.changeIsEmpty(resolvedChanges));
  }

  @Test
  public void field_11__avatar_change_is_preserved() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setAvatar("Existing avatar")
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewAvatar(DecryptedString.newBuilder().setValue("New avatar").build())
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    assertEquals(decryptedChange, resolvedChanges);
  }

  @Test
  public void field_11__no_avatar_change_is_removed() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setAvatar("Existing avatar")
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewAvatar(DecryptedString.newBuilder().setValue("Existing avatar").build())
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    assertTrue(DecryptedGroupUtil.changeIsEmpty(resolvedChanges));
  }

  @Test
  public void field_12__timer_change_is_preserved() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setDisappearingMessagesTimer(DecryptedTimer.newBuilder().setDuration(123))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewTimer(DecryptedTimer.newBuilder().setDuration(456))
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    assertEquals(decryptedChange, resolvedChanges);
  }

  @Test
  public void field_12__no_timer_change_is_removed() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setDisappearingMessagesTimer(DecryptedTimer.newBuilder().setDuration(123))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewTimer(DecryptedTimer.newBuilder().setDuration(123))
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    assertTrue(DecryptedGroupUtil.changeIsEmpty(resolvedChanges));
  }

  @Test
  public void field_13__attribute_access_change_is_preserved() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setAccessControl(AccessControl.newBuilder().setAttributes(AccessControl.AccessRequired.ADMINISTRATOR))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewAttributeAccess(AccessControl.AccessRequired.MEMBER)
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    assertEquals(decryptedChange, resolvedChanges);
  }

  @Test
  public void field_13__no_attribute_access_change_is_removed() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setAccessControl(AccessControl.newBuilder().setAttributes(AccessControl.AccessRequired.ADMINISTRATOR))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewAttributeAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    assertTrue(DecryptedGroupUtil.changeIsEmpty(resolvedChanges));
  }

  @Test
  public void field_14__membership_access_change_is_preserved() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setAccessControl(AccessControl.newBuilder().setMembers(AccessControl.AccessRequired.ADMINISTRATOR))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewMemberAccess(AccessControl.AccessRequired.MEMBER)
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    assertEquals(decryptedChange, resolvedChanges);
  }

  @Test
  public void field_14__no_membership_access_change_is_removed() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setAccessControl(AccessControl.newBuilder().setMembers(AccessControl.AccessRequired.ADMINISTRATOR))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    assertTrue(DecryptedGroupUtil.changeIsEmpty(resolvedChanges));
  }

  @Test
  public void field_15__no_membership_access_change_is_removed() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setAccessControl(AccessControl.newBuilder().setAddFromInviteLink(AccessControl.AccessRequired.ADMINISTRATOR))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    assertTrue(DecryptedGroupUtil.changeIsEmpty(resolvedChanges));
  }

  @Test
  public void field_16__changes_to_add_requesting_members_when_full_members_are_removed() {
    UUID                 member1         = UUID.randomUUID();
    UUID                 member2         = UUID.randomUUID();
    UUID                 member3         = UUID.randomUUID();
    ProfileKey           profileKey2     = randomProfileKey();
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .addMembers(member(member1))
                                                         .addMembers(member(member3))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .addNewRequestingMembers(requestingMember(member1))
                                                               .addNewRequestingMembers(requestingMember(member2, profileKey2))
                                                               .addNewRequestingMembers(requestingMember(member3))
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    DecryptedGroupChange expected = DecryptedGroupChange.newBuilder()
                                                        .addNewRequestingMembers(requestingMember(member2, profileKey2))
                                                        .build();

    assertEquals(expected, resolvedChanges);
  }

  @Test
  public void field_16__changes_to_add_requesting_members_when_pending_are_promoted() {
    UUID                 member1         = UUID.randomUUID();
    UUID                 member2         = UUID.randomUUID();
    UUID                 member3         = UUID.randomUUID();
    ProfileKey           profileKey1     = randomProfileKey();
    ProfileKey           profileKey2     = randomProfileKey();
    ProfileKey           profileKey3     = randomProfileKey();
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .addPendingMembers(pendingMember(member1))
                                                         .addPendingMembers(pendingMember(member3))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .addNewRequestingMembers(requestingMember(member1, profileKey1))
                                                               .addNewRequestingMembers(requestingMember(member2, profileKey2))
                                                               .addNewRequestingMembers(requestingMember(member3, profileKey3))
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    DecryptedGroupChange expected = DecryptedGroupChange.newBuilder()
                                                        .addPromotePendingMembers(member(member1, profileKey1))
                                                        .addNewRequestingMembers(requestingMember(member2, profileKey2))
                                                        .addPromotePendingMembers(member(member3, profileKey3))
                                                        .build();

    assertEquals(expected, resolvedChanges);
  }

  @Test
  public void field_17__changes_to_remove_missing_requesting_members_are_excluded() {
    UUID                 member1         = UUID.randomUUID();
    UUID                 member2         = UUID.randomUUID();
    UUID                 member3         = UUID.randomUUID();
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .addRequestingMembers(requestingMember(member2))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .addDeleteRequestingMembers(UuidUtil.toByteString(member1))
                                                               .addDeleteRequestingMembers(UuidUtil.toByteString(member2))
                                                               .addDeleteRequestingMembers(UuidUtil.toByteString(member3))
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    DecryptedGroupChange expected = DecryptedGroupChange.newBuilder()
                                                        .addDeleteRequestingMembers(UuidUtil.toByteString(member2))
                                                        .build();

    assertEquals(expected, resolvedChanges);
  }

  @Test
  public void field_18__promote_requesting_members() {
    UUID                 member1         = UUID.randomUUID();
    UUID                 member2         = UUID.randomUUID();
    UUID                 member3         = UUID.randomUUID();
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .addMembers(member(member1))
                                                         .addRequestingMembers(requestingMember(member2))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .addPromoteRequestingMembers(approveMember(member1))
                                                               .addPromoteRequestingMembers(approveMember(member2))
                                                               .addPromoteRequestingMembers(approveMember(member3))
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    DecryptedGroupChange expected = DecryptedGroupChange.newBuilder()
                                                        .addPromoteRequestingMembers(approveMember(member2))
                                                        .build();

    assertEquals(expected, resolvedChanges);
  }

  @Test
  public void field_19__password_change_is_kept() {
    ByteString           password1       = ByteString.copyFrom(Util.getSecretBytes(16));
    ByteString           password2       = ByteString.copyFrom(Util.getSecretBytes(16));
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setInviteLinkPassword(password1)
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewInviteLinkPassword(password2)
                                                               .build();

    DecryptedGroupChange resolvedChanges = GroupChangeUtil.resolveConflict(groupState, decryptedChange).build();

    assertEquals(decryptedChange, resolvedChanges);
  }
}