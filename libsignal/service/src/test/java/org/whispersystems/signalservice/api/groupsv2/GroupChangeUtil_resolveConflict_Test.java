package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.DisappearingMessagesTimer;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.PendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class GroupChangeUtil_resolveConflict_Test {

  @Test
  public void empty_actions() {
    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(DecryptedGroup.newBuilder().build(),
                                                                          DecryptedGroupChange.newBuilder().build(),
                                                                          GroupChange.Actions.newBuilder().build())
                                                         .build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }

  @Test
  public void field_3__changes_to_add_existing_members_are_excluded() {
    UUID                 member1         = UUID.randomUUID();
    UUID                 member2         = UUID.randomUUID();
    UUID                 member3         = UUID.randomUUID();
    ProfileKey           profileKey2     = randomProfileKey();
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .addMembers(member(member1))
                                                         .addMembers(member(member3))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .addNewMembers(member(member1))
                                                               .addNewMembers(member(member2))
                                                               .addNewMembers(member(member3))
                                                               .build();
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .addAddMembers(GroupChange.Actions.AddMemberAction.newBuilder().setAdded(encryptedMember(member1, randomProfileKey())))
                                                              .addAddMembers(GroupChange.Actions.AddMemberAction.newBuilder().setAdded(encryptedMember(member2, profileKey2)))
                                                              .addAddMembers(GroupChange.Actions.AddMemberAction.newBuilder().setAdded(encryptedMember(member3, randomProfileKey())))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = GroupChange.Actions.newBuilder()
                                                      .addAddMembers(GroupChange.Actions.AddMemberAction.newBuilder().setAdded(encryptedMember(member2, profileKey2)))
                                                      .build();
    assertEquals(expected, resolvedActions);
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
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .addDeleteMembers(GroupChange.Actions.DeleteMemberAction.newBuilder().setDeletedUserId(encrypt(member1)))
                                                              .addDeleteMembers(GroupChange.Actions.DeleteMemberAction.newBuilder().setDeletedUserId(encrypt(member2)))
                                                              .addDeleteMembers(GroupChange.Actions.DeleteMemberAction.newBuilder().setDeletedUserId(encrypt(member3)))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = GroupChange.Actions.newBuilder()
                                                      .addDeleteMembers(GroupChange.Actions.DeleteMemberAction.newBuilder().setDeletedUserId(encrypt(member2)))
                                                      .build();
    assertEquals(expected, resolvedActions);
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
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .addModifyMemberRoles(GroupChange.Actions.ModifyMemberRoleAction.newBuilder().setUserId(encrypt(member1)).setRole(Member.Role.DEFAULT))
                                                              .addModifyMemberRoles(GroupChange.Actions.ModifyMemberRoleAction.newBuilder().setUserId(encrypt(member2)).setRole(Member.Role.ADMINISTRATOR))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertEquals(change, resolvedActions);
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
    GroupChange.Actions  change           = GroupChange.Actions.newBuilder()
                                                               .addModifyMemberRoles(GroupChange.Actions.ModifyMemberRoleAction.newBuilder().setUserId(encrypt(member1)).setRole(Member.Role.ADMINISTRATOR))
                                                               .addModifyMemberRoles(GroupChange.Actions.ModifyMemberRoleAction.newBuilder().setUserId(encrypt(member2)).setRole(Member.Role.ADMINISTRATOR))
                                                               .addModifyMemberRoles(GroupChange.Actions.ModifyMemberRoleAction.newBuilder().setUserId(encrypt(member3)).setRole(Member.Role.DEFAULT))
                                                               .addModifyMemberRoles(GroupChange.Actions.ModifyMemberRoleAction.newBuilder().setUserId(encrypt(memberNotInGroup)).setRole(Member.Role.ADMINISTRATOR))
                                                               .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = GroupChange.Actions.newBuilder()
                                                      .addModifyMemberRoles(GroupChange.Actions.ModifyMemberRoleAction.newBuilder().setUserId(encrypt(member2)).setRole(Member.Role.ADMINISTRATOR))
                                                      .build();
    assertEquals(expected, resolvedActions);
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
    GroupChange.Actions  change           = GroupChange.Actions.newBuilder()
                                                               .addModifyMemberProfileKeys(GroupChange.Actions.ModifyMemberProfileKeyAction.newBuilder().setPresentation(presentation(member1, profileKey1)))
                                                               .addModifyMemberProfileKeys(GroupChange.Actions.ModifyMemberProfileKeyAction.newBuilder().setPresentation(presentation(member2, profileKey2b)))
                                                               .addModifyMemberProfileKeys(GroupChange.Actions.ModifyMemberProfileKeyAction.newBuilder().setPresentation(presentation(member3, profileKey3)))
                                                               .addModifyMemberProfileKeys(GroupChange.Actions.ModifyMemberProfileKeyAction.newBuilder().setPresentation(presentation(memberNotInGroup, profileKey4)))
                                                               .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = GroupChange.Actions.newBuilder()
                                                      .addModifyMemberProfileKeys(GroupChange.Actions.ModifyMemberProfileKeyAction.newBuilder().setPresentation(presentation(member2, profileKey2b)))
                                                      .build();

    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_7__add_pending_members() {
    UUID                 member1         = UUID.randomUUID();
    UUID                 member2         = UUID.randomUUID();
    UUID                 member3         = UUID.randomUUID();
    ProfileKey           profileKey2     = randomProfileKey();
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .addMembers(member(member1))
                                                         .addPendingMembers(pendingMember(member3))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .addNewPendingMembers(pendingMember(member1))
                                                               .addNewPendingMembers(pendingMember(member2))
                                                               .addNewPendingMembers(pendingMember(member3))
                                                               .build();

    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .addAddPendingMembers(GroupChange.Actions.AddPendingMemberAction.newBuilder().setAdded(PendingMember.newBuilder().setMember(encryptedMember(member1, randomProfileKey()))))
                                                              .addAddPendingMembers(GroupChange.Actions.AddPendingMemberAction.newBuilder().setAdded(PendingMember.newBuilder().setMember(encryptedMember(member2, profileKey2))))
                                                              .addAddPendingMembers(GroupChange.Actions.AddPendingMemberAction.newBuilder().setAdded(PendingMember.newBuilder().setMember(encryptedMember(member3, randomProfileKey()))))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = GroupChange.Actions.newBuilder()
                                                      .addAddPendingMembers(GroupChange.Actions.AddPendingMemberAction.newBuilder().setAdded(PendingMember.newBuilder().setMember(encryptedMember(member2, profileKey2))))
                                                      .build();
    assertEquals(expected, resolvedActions);
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
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .addDeletePendingMembers(GroupChange.Actions.DeletePendingMemberAction.newBuilder().setDeletedUserId(encrypt(member1)))
                                                              .addDeletePendingMembers(GroupChange.Actions.DeletePendingMemberAction.newBuilder().setDeletedUserId(encrypt(member2)))
                                                              .addDeletePendingMembers(GroupChange.Actions.DeletePendingMemberAction.newBuilder().setDeletedUserId(encrypt(member3)))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = GroupChange.Actions.newBuilder()
                                                      .addDeletePendingMembers(GroupChange.Actions.DeletePendingMemberAction.newBuilder().setDeletedUserId(encrypt(member2)))
                                                      .build();
    assertEquals(expected, resolvedActions);
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
                                                               .addPromotePendingMembers(UuidUtil.toByteString(member1))
                                                               .addPromotePendingMembers(UuidUtil.toByteString(member2))
                                                               .addPromotePendingMembers(UuidUtil.toByteString(member3))
                                                               .build();
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .addPromotePendingMembers(GroupChange.Actions.PromotePendingMemberAction.newBuilder().setPresentation(presentation(member1, randomProfileKey())))
                                                              .addPromotePendingMembers(GroupChange.Actions.PromotePendingMemberAction.newBuilder().setPresentation(presentation(member2, profileKey2)))
                                                              .addPromotePendingMembers(GroupChange.Actions.PromotePendingMemberAction.newBuilder().setPresentation(presentation(member3, randomProfileKey())))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();


    GroupChange.Actions expected = GroupChange.Actions.newBuilder()
                                                      .addPromotePendingMembers(GroupChange.Actions.PromotePendingMemberAction.newBuilder().setPresentation(presentation(member2, profileKey2)))
                                                      .build();
    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_3_to_9__add_of_pending_member_converted_to_a_promote() {
    UUID                 member1         = UUID.randomUUID();
    ProfileKey           profileKey1     = randomProfileKey();
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .addPendingMembers(pendingMember(member1))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .addNewMembers(member(member1))
                                                               .build();

    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .addAddMembers(GroupChange.Actions.AddMemberAction.newBuilder().setAdded(encryptedMember(member1, profileKey1)))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = GroupChange.Actions.newBuilder()
                                                      .addPromotePendingMembers(GroupChange.Actions.PromotePendingMemberAction.newBuilder().setPresentation(presentation(member1, profileKey1)))
                                                      .build();
    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_10__title_change_is_preserved() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setTitle("Existing title")
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewTitle(DecryptedString.newBuilder().setValue("New title").build())
                                                               .build();
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .setModifyTitle(GroupChange.Actions.ModifyTitleAction.newBuilder().setTitle(ByteString.copyFrom("New title encrypted".getBytes())))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertEquals(change, resolvedActions);
  }

  @Test
  public void field_10__no_title_change_is_removed() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setTitle("Existing title")
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewTitle(DecryptedString.newBuilder().setValue("Existing title").build())
                                                               .build();
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .setModifyTitle(GroupChange.Actions.ModifyTitleAction.newBuilder().setTitle(ByteString.copyFrom("Existing title encrypted".getBytes())))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }

  @Test
  public void field_11__avatar_change_is_preserved() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setAvatar("Existing avatar")
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewAvatar(DecryptedString.newBuilder().setValue("New avatar").build())
                                                               .build();
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .setModifyAvatar(GroupChange.Actions.ModifyAvatarAction.newBuilder().setAvatar("New avatar possibly encrypted"))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertEquals(change, resolvedActions);
  }

  @Test
  public void field_11__no_avatar_change_is_removed() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setAvatar("Existing avatar")
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewAvatar(DecryptedString.newBuilder().setValue("Existing avatar").build())
                                                               .build();
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .setModifyAvatar(GroupChange.Actions.ModifyAvatarAction.newBuilder().setAvatar("Existing avatar possibly encrypted"))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }

  @Test
  public void field_12__timer_change_is_preserved() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setDisappearingMessagesTimer(DisappearingMessagesTimer.newBuilder().setDuration(123))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewTimer(DisappearingMessagesTimer.newBuilder().setDuration(456))
                                                               .build();
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .setModifyDisappearingMessagesTimer(GroupChange.Actions.ModifyDisappearingMessagesTimerAction.newBuilder().setTimer(ByteString.EMPTY))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertEquals(change, resolvedActions);
  }

  @Test
  public void field_12__no_timer_change_is_removed() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setDisappearingMessagesTimer(DisappearingMessagesTimer.newBuilder().setDuration(123))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewTimer(DisappearingMessagesTimer.newBuilder().setDuration(123))
                                                               .build();
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .setModifyDisappearingMessagesTimer(GroupChange.Actions.ModifyDisappearingMessagesTimerAction.newBuilder().setTimer(ByteString.EMPTY))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }

  @Test
  public void field_13__attribute_access_change_is_preserved() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setAccessControl(AccessControl.newBuilder().setAttributes(AccessControl.AccessRequired.ADMINISTRATOR))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewAttributeAccess(AccessControl.AccessRequired.MEMBER)
                                                               .build();
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .setModifyAttributesAccess(GroupChange.Actions.ModifyAttributesAccessControlAction.newBuilder().setAttributesAccess(AccessControl.AccessRequired.MEMBER))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertEquals(change, resolvedActions);
  }

  @Test
  public void field_13__no_attribute_access_change_is_removed() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setAccessControl(AccessControl.newBuilder().setAttributes(AccessControl.AccessRequired.ADMINISTRATOR))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewAttributeAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                               .build();
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .setModifyAttributesAccess(GroupChange.Actions.ModifyAttributesAccessControlAction.newBuilder().setAttributesAccess(AccessControl.AccessRequired.ADMINISTRATOR))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }

  @Test
  public void field_14__membership_access_change_is_preserved() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setAccessControl(AccessControl.newBuilder().setMembers(AccessControl.AccessRequired.ADMINISTRATOR))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewMemberAccess(AccessControl.AccessRequired.MEMBER)
                                                               .build();
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .setModifyMemberAccess(GroupChange.Actions.ModifyMembersAccessControlAction.newBuilder().setMembersAccess(AccessControl.AccessRequired.MEMBER))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertEquals(change, resolvedActions);
  }

  @Test
  public void field_14__no_membership_access_change_is_removed() {
    DecryptedGroup       groupState      = DecryptedGroup.newBuilder()
                                                         .setAccessControl(AccessControl.newBuilder().setMembers(AccessControl.AccessRequired.ADMINISTRATOR))
                                                         .build();
    DecryptedGroupChange decryptedChange = DecryptedGroupChange.newBuilder()
                                                               .setNewMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                               .build();
    GroupChange.Actions  change          = GroupChange.Actions.newBuilder()
                                                              .setModifyMemberAccess(GroupChange.Actions.ModifyMembersAccessControlAction.newBuilder().setMembersAccess(AccessControl.AccessRequired.ADMINISTRATOR))
                                                              .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }

  private static ProfileKey randomProfileKey() {
    byte[] contents = new byte[32];
    new SecureRandom().nextBytes(contents);
    try {
      return new ProfileKey(contents);
    } catch (InvalidInputException e) {
      throw new AssertionError();
    }
  }

  /**
   * Emulates encryption by creating a unique {@link ByteString} that won't equal a byte string created from the {@link UUID}.
   */
  private static ByteString encrypt(UUID uuid) {
    byte[] uuidBytes = UuidUtil.toByteArray(uuid);
    return ByteString.copyFrom(Arrays.copyOf(uuidBytes, uuidBytes.length + 1));
  }

  /**
   * Emulates a presentation by concatenating the uuid and profile key which makes it suitable for
   * equality assertions in these tests.
   */
  private static ByteString presentation(UUID uuid, ProfileKey profileKey) {
    byte[] uuidBytes       = UuidUtil.toByteArray(uuid);
    byte[] profileKeyBytes = profileKey.serialize();
    byte[] concat          = new byte[uuidBytes.length + profileKeyBytes.length];

    System.arraycopy(uuidBytes, 0, concat, 0, uuidBytes.length);
    System.arraycopy(profileKeyBytes, 0, concat, uuidBytes.length, profileKeyBytes.length);

    return ByteString.copyFrom(concat);
  }

  private static DecryptedModifyMemberRole promoteAdmin(UUID member) {
    return DecryptedModifyMemberRole.newBuilder()
                                    .setUuid(UuidUtil.toByteString(member))
                                    .setRole(Member.Role.ADMINISTRATOR)
                                    .build();
  }

  private static DecryptedModifyMemberRole demoteAdmin(UUID member) {
    return DecryptedModifyMemberRole.newBuilder()
                                    .setUuid(UuidUtil.toByteString(member))
                                    .setRole(Member.Role.DEFAULT)
                                    .build();
  }

  private Member encryptedMember(UUID uuid, ProfileKey profileKey) {
    return Member.newBuilder()
                 .setPresentation(presentation(uuid, profileKey))
                 .build();
  }

  private static DecryptedMember member(UUID uuid) {
    return DecryptedMember.newBuilder()
                          .setUuid(UuidUtil.toByteString(uuid))
                          .setRole(Member.Role.DEFAULT)
                          .build();
  }

  private static DecryptedPendingMemberRemoval pendingMemberRemoval(UUID uuid) {
    return DecryptedPendingMemberRemoval.newBuilder()
                                        .setUuid(UuidUtil.toByteString(uuid))
                                        .build();
  }

  private static DecryptedPendingMember pendingMember(UUID uuid) {
    return DecryptedPendingMember.newBuilder()
                                 .setUuid(UuidUtil.toByteString(uuid))
                                 .setRole(Member.Role.DEFAULT)
                                 .build();
  }

  private static DecryptedMember member(UUID uuid, ProfileKey profileKey) {
    return DecryptedMember.newBuilder()
                          .setUuid(UuidUtil.toByteString(uuid))
                          .setRole(Member.Role.DEFAULT)
                          .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                          .build();
  }

  private static DecryptedMember admin(UUID uuid) {
    return DecryptedMember.newBuilder()
                          .setUuid(UuidUtil.toByteString(uuid))
                          .setRole(Member.Role.ADMINISTRATOR)
                          .build();
  }
}