package org.whispersystems.signalservice.api.groupsv2;

import org.junit.Test;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.BannedMember;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.PendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.util.Util;

import java.util.List;
import java.util.UUID;

import okio.ByteString;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.admin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.approveMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.bannedMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.demoteAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.encrypt;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.encryptedMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.encryptedRequestingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.member;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMemberRemoval;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingPniAciMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.presentation;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.promoteAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.randomProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.requestingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber;

public final class GroupChangeUtil_resolveConflict_Test {

  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   * <p>
   * If we didn't, newly added fields would not be resolved by {@link GroupChangeUtil#resolveConflict(DecryptedGroup, DecryptedGroupChange, GroupChange.Actions)}.
   */
  @Test
  public void ensure_resolveConflict_knows_about_all_fields_of_DecryptedGroupChange() {
    int maxFieldFound = getMaxDeclaredFieldNumber(DecryptedGroupChange.class);

    assertEquals("GroupChangeUtil#resolveConflict and its tests need updating to account for new fields on " + DecryptedGroupChange.class.getName(),
                 24, maxFieldFound);
  }

  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   * <p>
   * If we didn't, newly added fields would not be resolved by {@link GroupChangeUtil#resolveConflict(DecryptedGroup, DecryptedGroupChange, GroupChange.Actions)}.
   */
  @Test
  public void ensure_resolveConflict_knows_about_all_fields_of_GroupChange() {
    int maxFieldFound = getMaxDeclaredFieldNumber(DecryptedGroupChange.class);

    assertEquals("GroupChangeUtil#resolveConflict and its tests need updating to account for new fields on " + GroupChange.class.getName(),
                 24, maxFieldFound);
  }

  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   * <p>
   * If we didn't, newly added fields would not be resolved by {@link GroupChangeUtil#resolveConflict(DecryptedGroup, DecryptedGroupChange, GroupChange.Actions)}.
   */
  @Test
  public void ensure_resolveConflict_knows_about_all_fields_of_DecryptedGroup() {
    int maxFieldFound = getMaxDeclaredFieldNumber(DecryptedGroup.class, ProtobufTestUtils.IGNORED_DECRYPTED_GROUP_TAGS);

    assertEquals("GroupChangeUtil#resolveConflict and its tests need updating to account for new fields on " + DecryptedGroup.class.getName(),
                 13, maxFieldFound);
  }


  @Test
  public void empty_actions() {
    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(new DecryptedGroup.Builder().build(),
                                                                          new DecryptedGroupChange.Builder().build(),
                                                                          new GroupChange.Actions.Builder().build())
                                                         .build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }

  @Test
  public void field_3__changes_to_add_existing_members_are_excluded() {
    UUID                 member1         = UUID.randomUUID();
    UUID                 member2         = UUID.randomUUID();
    UUID                 member3         = UUID.randomUUID();
    ProfileKey           profileKey2     = randomProfileKey();
    DecryptedGroup       groupState      = new DecryptedGroup.Builder().members(List.of(member(member1), member(member3))).build();
    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder().newMembers(List.of(member(member1), member(member2), member(member3))).build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .addMembers(List.of(new GroupChange.Actions.AddMemberAction.Builder().added(encryptedMember(member1, randomProfileKey())).build(),
                            new GroupChange.Actions.AddMemberAction.Builder().added(encryptedMember(member2, profileKey2)).build(),
                            new GroupChange.Actions.AddMemberAction.Builder().added(encryptedMember(member3, randomProfileKey())).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .addMembers(List.of(new GroupChange.Actions.AddMemberAction.Builder().added(encryptedMember(member2, profileKey2)).build()))
        .build();
    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_4__changes_to_remove_missing_members_are_excluded() {
    UUID member1 = UUID.randomUUID();
    UUID member2 = UUID.randomUUID();
    UUID member3 = UUID.randomUUID();

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .members(List.of(member(member2)))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .deleteMembers(List.of(UuidUtil.toByteString(member1), UuidUtil.toByteString(member2), UuidUtil.toByteString(member3)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .deleteMembers(List.of(new GroupChange.Actions.DeleteMemberAction.Builder().deletedUserId(encrypt(member1)).build(),
                               new GroupChange.Actions.DeleteMemberAction.Builder().deletedUserId(encrypt(member2)).build(),
                               new GroupChange.Actions.DeleteMemberAction.Builder().deletedUserId(encrypt(member3)).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .deleteMembers(List.of(new GroupChange.Actions.DeleteMemberAction.Builder().deletedUserId(encrypt(member2)).build()))
        .build();
    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_5__role_change_is_preserved() {
    UUID member1 = UUID.randomUUID();
    UUID member2 = UUID.randomUUID();
    UUID member3 = UUID.randomUUID();

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .members(List.of(admin(member1), member(member2), member(member3)))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .modifyMemberRoles(List.of(demoteAdmin(member1), promoteAdmin(member2)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyMemberRoles(List.of(new GroupChange.Actions.ModifyMemberRoleAction.Builder().userId(encrypt(member1)).role(Member.Role.DEFAULT).build(),
                                   new GroupChange.Actions.ModifyMemberRoleAction.Builder().userId(encrypt(member2)).role(Member.Role.ADMINISTRATOR).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertEquals(change, resolvedActions);
  }

  @Test
  public void field_5__unnecessary_role_changes_removed() {
    UUID member1          = UUID.randomUUID();
    UUID member2          = UUID.randomUUID();
    UUID member3          = UUID.randomUUID();
    UUID memberNotInGroup = UUID.randomUUID();

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .members(List.of(admin(member1), member(member2), member(member3)))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .modifyMemberRoles(List.of(promoteAdmin(member1), promoteAdmin(member2), demoteAdmin(member3), promoteAdmin(memberNotInGroup)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyMemberRoles(List.of(new GroupChange.Actions.ModifyMemberRoleAction.Builder().userId(encrypt(member1)).role(Member.Role.ADMINISTRATOR).build(),
                                   new GroupChange.Actions.ModifyMemberRoleAction.Builder().userId(encrypt(member2)).role(Member.Role.ADMINISTRATOR).build(),
                                   new GroupChange.Actions.ModifyMemberRoleAction.Builder().userId(encrypt(member3)).role(Member.Role.DEFAULT).build(),
                                   new GroupChange.Actions.ModifyMemberRoleAction.Builder().userId(encrypt(memberNotInGroup)).role(Member.Role.ADMINISTRATOR).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .modifyMemberRoles(List.of(new GroupChange.Actions.ModifyMemberRoleAction.Builder().userId(encrypt(member2)).role(Member.Role.ADMINISTRATOR).build()))
        .build();
    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_6__profile_key_changes() {
    UUID       member1          = UUID.randomUUID();
    UUID       member2          = UUID.randomUUID();
    UUID       member3          = UUID.randomUUID();
    UUID       memberNotInGroup = UUID.randomUUID();
    ProfileKey profileKey1      = randomProfileKey();
    ProfileKey profileKey2      = randomProfileKey();
    ProfileKey profileKey3      = randomProfileKey();
    ProfileKey profileKey4      = randomProfileKey();
    ProfileKey profileKey2b     = randomProfileKey();

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .members(List.of(member(member1, profileKey1), member(member2, profileKey2), member(member3, profileKey3)))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .modifiedProfileKeys(List.of(member(member1, profileKey1), member(member2, profileKey2b), member(member3, profileKey3), member(memberNotInGroup, profileKey4)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyMemberProfileKeys(List.of(new GroupChange.Actions.ModifyMemberProfileKeyAction.Builder().presentation(presentation(member1, profileKey1)).build(),
                                         new GroupChange.Actions.ModifyMemberProfileKeyAction.Builder().presentation(presentation(member2, profileKey2b)).build(),
                                         new GroupChange.Actions.ModifyMemberProfileKeyAction.Builder().presentation(presentation(member3, profileKey3)).build(),
                                         new GroupChange.Actions.ModifyMemberProfileKeyAction.Builder().presentation(presentation(memberNotInGroup, profileKey4)).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .modifyMemberProfileKeys(List.of(new GroupChange.Actions.ModifyMemberProfileKeyAction.Builder().presentation(presentation(member2, profileKey2b)).build()))
        .build();

    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_7__add_pending_members() {
    UUID       member1     = UUID.randomUUID();
    UUID       member2     = UUID.randomUUID();
    UUID       member3     = UUID.randomUUID();
    ProfileKey profileKey2 = randomProfileKey();

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .members(List.of(member(member1)))
        .pendingMembers(List.of(pendingMember(member3)))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newPendingMembers(List.of(pendingMember(member1), pendingMember(member2), pendingMember(member3)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .addPendingMembers(List.of(new GroupChange.Actions.AddPendingMemberAction.Builder().added(new PendingMember.Builder().member(encryptedMember(member1, randomProfileKey())).build()).build(),
                                   new GroupChange.Actions.AddPendingMemberAction.Builder().added(new PendingMember.Builder().member(encryptedMember(member2, profileKey2)).build()).build(),
                                   new GroupChange.Actions.AddPendingMemberAction.Builder().added(new PendingMember.Builder().member(encryptedMember(member3, randomProfileKey())).build()).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .addPendingMembers(List.of(new GroupChange.Actions.AddPendingMemberAction.Builder().added(new PendingMember.Builder().member(encryptedMember(member2, profileKey2)).build()).build()))
        .build();

    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_8__delete_pending_members() {
    UUID member1 = UUID.randomUUID();
    UUID member2 = UUID.randomUUID();
    UUID member3 = UUID.randomUUID();

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .members(List.of(member(member1)))
        .pendingMembers(List.of(pendingMember(member2)))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .deletePendingMembers(List.of(pendingMemberRemoval(member1), pendingMemberRemoval(member2), pendingMemberRemoval(member3)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .deletePendingMembers(List.of(new GroupChange.Actions.DeletePendingMemberAction.Builder().deletedUserId(encrypt(member1)).build(),
                                      new GroupChange.Actions.DeletePendingMemberAction.Builder().deletedUserId(encrypt(member2)).build(),
                                      new GroupChange.Actions.DeletePendingMemberAction.Builder().deletedUserId(encrypt(member3)).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .deletePendingMembers(List.of(new GroupChange.Actions.DeletePendingMemberAction.Builder().deletedUserId(encrypt(member2)).build()))
        .build();

    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_9__promote_pending_members() {
    UUID       member1     = UUID.randomUUID();
    UUID       member2     = UUID.randomUUID();
    UUID       member3     = UUID.randomUUID();
    ProfileKey profileKey2 = randomProfileKey();

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .members(List.of(member(member1)))
        .pendingMembers(List.of(pendingMember(member2)))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .promotePendingMembers(List.of(member(member1), member(member2), member(member3)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .promotePendingMembers(List.of(new GroupChange.Actions.PromotePendingMemberAction.Builder().presentation(presentation(member1, randomProfileKey())).build(),
                                       new GroupChange.Actions.PromotePendingMemberAction.Builder().presentation(presentation(member2, profileKey2)).build(),
                                       new GroupChange.Actions.PromotePendingMemberAction.Builder().presentation(presentation(member3, randomProfileKey())).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();


    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .promotePendingMembers(List.of(new GroupChange.Actions.PromotePendingMemberAction.Builder().presentation(presentation(member2, profileKey2)).build()))
        .build();

    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_3_to_9__add_of_pending_member_converted_to_a_promote() {
    UUID       member1     = UUID.randomUUID();
    ProfileKey profileKey1 = randomProfileKey();

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .pendingMembers(List.of(pendingMember(member1)))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newMembers(List.of(member(member1)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .addMembers(List.of(new GroupChange.Actions.AddMemberAction.Builder().added(encryptedMember(member1, profileKey1)).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .promotePendingMembers(List.of(new GroupChange.Actions.PromotePendingMemberAction.Builder().presentation(presentation(member1, profileKey1)).build()))
        .build();

    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_10__title_change_is_preserved() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .title("Existing title")
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newTitle(new DecryptedString.Builder().value_("New title").build())
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyTitle(new GroupChange.Actions.ModifyTitleAction.Builder().title(ByteString.of("New title encrypted".getBytes())).build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertEquals(change, resolvedActions);
  }

  @Test
  public void field_10__no_title_change_is_removed() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .title("Existing title")
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newTitle(new DecryptedString.Builder().value_("Existing title").build())
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyTitle(new GroupChange.Actions.ModifyTitleAction.Builder().title(ByteString.of("Existing title encrypted".getBytes())).build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }

  @Test
  public void field_11__avatar_change_is_preserved() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .avatar("Existing avatar")
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newAvatar(new DecryptedString.Builder().value_("New avatar").build())
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyAvatar(new GroupChange.Actions.ModifyAvatarAction.Builder().avatar("New avatar possibly encrypted").build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertEquals(change, resolvedActions);
  }

  @Test
  public void field_11__no_avatar_change_is_removed() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .avatar("Existing avatar")
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newAvatar(new DecryptedString.Builder().value_("Existing avatar").build())
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyAvatar(new GroupChange.Actions.ModifyAvatarAction.Builder().avatar("Existing avatar possibly encrypted").build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }

  @Test
  public void field_12__timer_change_is_preserved() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .disappearingMessagesTimer(new DecryptedTimer.Builder().duration(123).build())
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newTimer(new DecryptedTimer.Builder().duration(456).build())
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyDisappearingMessagesTimer(new GroupChange.Actions.ModifyDisappearingMessagesTimerAction.Builder().timer(ByteString.EMPTY).build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertEquals(change, resolvedActions);
  }

  @Test
  public void field_12__no_timer_change_is_removed() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .disappearingMessagesTimer(new DecryptedTimer.Builder().duration(123).build())
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newTimer(new DecryptedTimer.Builder().duration(123).build())
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyDisappearingMessagesTimer(new GroupChange.Actions.ModifyDisappearingMessagesTimerAction.Builder().timer(ByteString.EMPTY).build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }

  @Test
  public void field_13__attribute_access_change_is_preserved() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .accessControl(new AccessControl.Builder().attributes(AccessControl.AccessRequired.ADMINISTRATOR).build())
        .build();
    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newAttributeAccess(AccessControl.AccessRequired.MEMBER)
        .build();
    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyAttributesAccess(new GroupChange.Actions.ModifyAttributesAccessControlAction.Builder().attributesAccess(AccessControl.AccessRequired.MEMBER).build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertEquals(change, resolvedActions);
  }

  @Test
  public void field_13__no_attribute_access_change_is_removed() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .accessControl(new AccessControl.Builder().attributes(AccessControl.AccessRequired.ADMINISTRATOR).build())
        .build();
    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newAttributeAccess(AccessControl.AccessRequired.ADMINISTRATOR)
        .build();
    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyAttributesAccess(new GroupChange.Actions.ModifyAttributesAccessControlAction.Builder().attributesAccess(AccessControl.AccessRequired.ADMINISTRATOR).build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }

  @Test
  public void field_14__membership_access_change_is_preserved() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .accessControl(new AccessControl.Builder().members(AccessControl.AccessRequired.ADMINISTRATOR).build())
        .build();
    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newMemberAccess(AccessControl.AccessRequired.MEMBER)
        .build();
    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyMemberAccess(new GroupChange.Actions.ModifyMembersAccessControlAction.Builder().membersAccess(AccessControl.AccessRequired.MEMBER).build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertEquals(change, resolvedActions);
  }

  @Test
  public void field_14__no_membership_access_change_is_removed() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .accessControl(new AccessControl.Builder().members(AccessControl.AccessRequired.ADMINISTRATOR).build())
        .build();
    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
        .build();
    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyMemberAccess(new GroupChange.Actions.ModifyMembersAccessControlAction.Builder().membersAccess(AccessControl.AccessRequired.ADMINISTRATOR).build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }

  @Test
  public void field_15__no_membership_access_change_is_removed() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .accessControl(new AccessControl.Builder().addFromInviteLink(AccessControl.AccessRequired.ADMINISTRATOR).build())
        .build();
    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
        .build();
    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyAddFromInviteLinkAccess(new GroupChange.Actions.ModifyAddFromInviteLinkAccessControlAction.Builder().addFromInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR).build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }

  @Test
  public void field_16__changes_to_add_requesting_members_when_full_members_are_removed() {
    UUID       member1     = UUID.randomUUID();
    UUID       member2     = UUID.randomUUID();
    UUID       member3     = UUID.randomUUID();
    ProfileKey profileKey2 = randomProfileKey();

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .members(List.of(member(member1), member(member3)))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newRequestingMembers(List.of(requestingMember(member1), requestingMember(member2), requestingMember(member3)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .addRequestingMembers(List.of(new GroupChange.Actions.AddRequestingMemberAction.Builder().added(encryptedRequestingMember(member1, randomProfileKey())).build(),
                                      new GroupChange.Actions.AddRequestingMemberAction.Builder().added(encryptedRequestingMember(member2, profileKey2)).build(),
                                      new GroupChange.Actions.AddRequestingMemberAction.Builder().added(encryptedRequestingMember(member3, randomProfileKey())).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .addRequestingMembers(List.of(new GroupChange.Actions.AddRequestingMemberAction.Builder().added(encryptedRequestingMember(member2, profileKey2)).build()))
        .build();

    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_16__changes_to_add_requesting_members_when_pending_are_promoted() {
    UUID       member1     = UUID.randomUUID();
    UUID       member2     = UUID.randomUUID();
    UUID       member3     = UUID.randomUUID();
    ProfileKey profileKey1 = randomProfileKey();
    ProfileKey profileKey2 = randomProfileKey();
    ProfileKey profileKey3 = randomProfileKey();

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .pendingMembers(List.of(pendingMember(member1), pendingMember(member3)))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newRequestingMembers(List.of(requestingMember(member1, profileKey1), requestingMember(member2, profileKey2), requestingMember(member3, profileKey3)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .addRequestingMembers(List.of(new GroupChange.Actions.AddRequestingMemberAction.Builder().added(encryptedRequestingMember(member1, profileKey1)).build(),
                                      new GroupChange.Actions.AddRequestingMemberAction.Builder().added(encryptedRequestingMember(member2, profileKey2)).build(),
                                      new GroupChange.Actions.AddRequestingMemberAction.Builder().added(encryptedRequestingMember(member3, profileKey3)).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .promotePendingMembers(List.of(new GroupChange.Actions.PromotePendingMemberAction.Builder().presentation(presentation(member3, profileKey3)).build(),
                                       new GroupChange.Actions.PromotePendingMemberAction.Builder().presentation(presentation(member1, profileKey1)).build()))
        .addRequestingMembers(List.of(new GroupChange.Actions.AddRequestingMemberAction.Builder().added(encryptedRequestingMember(member2, profileKey2)).build()))
        .build();

    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_17__changes_to_remove_missing_requesting_members_are_excluded() {
    UUID member1 = UUID.randomUUID();
    UUID member2 = UUID.randomUUID();
    UUID member3 = UUID.randomUUID();

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .requestingMembers(List.of(requestingMember(member2)))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .deleteRequestingMembers(List.of(UuidUtil.toByteString(member1), UuidUtil.toByteString(member2), UuidUtil.toByteString(member3)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .deleteRequestingMembers(List.of(new GroupChange.Actions.DeleteRequestingMemberAction.Builder().deletedUserId(encrypt(member1)).build(),
                                         new GroupChange.Actions.DeleteRequestingMemberAction.Builder().deletedUserId(encrypt(member2)).build(),
                                         new GroupChange.Actions.DeleteRequestingMemberAction.Builder().deletedUserId(encrypt(member3)).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .deleteRequestingMembers(List.of(new GroupChange.Actions.DeleteRequestingMemberAction.Builder().deletedUserId(encrypt(member2)).build()))
        .build();

    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_18__promote_requesting_members() {
    UUID member1 = UUID.randomUUID();
    UUID member2 = UUID.randomUUID();
    UUID member3 = UUID.randomUUID();

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .members(List.of(member(member1)))
        .requestingMembers(List.of(requestingMember(member2)))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .promoteRequestingMembers(List.of(approveMember(member1), approveMember(member2), approveMember(member3)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .promoteRequestingMembers(List.of(new GroupChange.Actions.PromoteRequestingMemberAction.Builder().role(Member.Role.DEFAULT).userId(UuidUtil.toByteString(member1)).build(),
                                          new GroupChange.Actions.PromoteRequestingMemberAction.Builder().role(Member.Role.DEFAULT).userId(UuidUtil.toByteString(member2)).build(),
                                          new GroupChange.Actions.PromoteRequestingMemberAction.Builder().role(Member.Role.DEFAULT).userId(UuidUtil.toByteString(member3)).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .promoteRequestingMembers(List.of(new GroupChange.Actions.PromoteRequestingMemberAction.Builder().role(Member.Role.DEFAULT).userId(UuidUtil.toByteString(member2)).build()))
        .build();

    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_19__password_change_is_kept() {
    ByteString password1 = ByteString.of(Util.getSecretBytes(16));
    ByteString password2 = ByteString.of(Util.getSecretBytes(16));
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .inviteLinkPassword(password1)
        .build();
    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newInviteLinkPassword(password2)
        .build();
    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyInviteLinkPassword(new GroupChange.Actions.ModifyInviteLinkPasswordAction.Builder().inviteLinkPassword(password2).build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .modifyInviteLinkPassword(new GroupChange.Actions.ModifyInviteLinkPasswordAction.Builder().inviteLinkPassword(password2).build())
        .build();
    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_20__description_change_is_preserved() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .description("Existing title")
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newDescription(new DecryptedString.Builder().value_("New title").build())
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyDescription(new GroupChange.Actions.ModifyDescriptionAction.Builder().description(ByteString.of("New title encrypted".getBytes())).build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertEquals(change, resolvedActions);
  }

  @Test
  public void field_20__no_description_change_is_removed() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .description("Existing title")
        .build();
    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newDescription(new DecryptedString.Builder().value_("Existing title").build())
        .build();
    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyDescription(new GroupChange.Actions.ModifyDescriptionAction.Builder().description(ByteString.of("Existing title encrypted".getBytes())).build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }

  @Test
  public void field_21__announcement_change_is_preserved() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .isAnnouncementGroup(EnabledState.DISABLED)
        .build();
    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newIsAnnouncementGroup(EnabledState.ENABLED)
        .build();
    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyAnnouncementsOnly(new GroupChange.Actions.ModifyAnnouncementsOnlyAction.Builder().announcementsOnly(true).build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertEquals(change, resolvedActions);
  }

  @Test
  public void field_21__announcement_change_is_removed() {
    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .isAnnouncementGroup(EnabledState.ENABLED)
        .build();
    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newIsAnnouncementGroup(EnabledState.ENABLED)
        .build();
    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .modifyAnnouncementsOnly(new GroupChange.Actions.ModifyAnnouncementsOnlyAction.Builder().announcementsOnly(true).build())
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    assertTrue(GroupChangeUtil.changeIsEmpty(resolvedActions));
  }


  @Test
  public void field_22__add_banned_members() {
    UUID member1 = UUID.randomUUID();
    UUID member2 = UUID.randomUUID();
    UUID member3 = UUID.randomUUID();

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .members(List.of(member(member1)))
        .bannedMembers(List.of(bannedMember(member3)))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .newBannedMembers(List.of(bannedMember(member1), bannedMember(member2), bannedMember(member3)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .addBannedMembers(List.of(new GroupChange.Actions.AddBannedMemberAction.Builder().added(new BannedMember.Builder().userId(encrypt(member1)).build()).build(),
                                  new GroupChange.Actions.AddBannedMemberAction.Builder().added(new BannedMember.Builder().userId(encrypt(member2)).build()).build(),
                                  new GroupChange.Actions.AddBannedMemberAction.Builder().added(new BannedMember.Builder().userId(encrypt(member3)).build()).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .addBannedMembers(List.of(new GroupChange.Actions.AddBannedMemberAction.Builder().added(new BannedMember.Builder().userId(encrypt(member1)).build()).build(),
                                  new GroupChange.Actions.AddBannedMemberAction.Builder().added(new BannedMember.Builder().userId(encrypt(member2)).build()).build()))
        .build();

    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_23__delete_banned_members() {
    UUID member1 = UUID.randomUUID();
    UUID member2 = UUID.randomUUID();
    UUID member3 = UUID.randomUUID();

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .members(List.of(member(member1)))
        .bannedMembers(List.of(bannedMember(member2)))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .deleteBannedMembers(List.of(bannedMember(member1), bannedMember(member2), bannedMember(member3)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .deleteBannedMembers(List.of(new GroupChange.Actions.DeleteBannedMemberAction.Builder().deletedUserId(encrypt(member1)).build(),
                                     new GroupChange.Actions.DeleteBannedMemberAction.Builder().deletedUserId(encrypt(member2)).build(),
                                     new GroupChange.Actions.DeleteBannedMemberAction.Builder().deletedUserId(encrypt(member3)).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .deleteBannedMembers(List.of(new GroupChange.Actions.DeleteBannedMemberAction.Builder().deletedUserId(encrypt(member2)).build()))
        .build();

    assertEquals(expected, resolvedActions);
  }

  @Test
  public void field_24__promote_pending_members() {
    DecryptedMember member1 = pendingPniAciMember(UUID.randomUUID(), UUID.randomUUID(), randomProfileKey());
    DecryptedMember member2 = pendingPniAciMember(UUID.randomUUID(), UUID.randomUUID(), randomProfileKey());

    DecryptedGroup groupState = new DecryptedGroup.Builder()
        .members(List.of(member(UuidUtil.fromByteString(member1.aciBytes))))
        .build();

    DecryptedGroupChange decryptedChange = new DecryptedGroupChange.Builder()
        .promotePendingPniAciMembers(List.of(pendingPniAciMember(member1.aciBytes, member1.pniBytes, member1.profileKey),
                                             pendingPniAciMember(member2.aciBytes, member2.pniBytes, member2.profileKey)))
        .build();

    GroupChange.Actions change = new GroupChange.Actions.Builder()
        .promotePendingPniAciMembers(List.of(new GroupChange.Actions.PromotePendingPniAciMemberProfileKeyAction.Builder().presentation(presentation(member1.pniBytes, member1.profileKey)).build(),
                                             new GroupChange.Actions.PromotePendingPniAciMemberProfileKeyAction.Builder().presentation(presentation(member2.pniBytes, member2.profileKey)).build()))
        .build();

    GroupChange.Actions resolvedActions = GroupChangeUtil.resolveConflict(groupState, decryptedChange, change).build();

    GroupChange.Actions expected = new GroupChange.Actions.Builder()
        .promotePendingPniAciMembers(List.of(new GroupChange.Actions.PromotePendingPniAciMemberProfileKeyAction.Builder().presentation(presentation(member2.pniBytes, member2.profileKey)).build()))
        .build();
    assertEquals(expected, resolvedActions);
  }
}