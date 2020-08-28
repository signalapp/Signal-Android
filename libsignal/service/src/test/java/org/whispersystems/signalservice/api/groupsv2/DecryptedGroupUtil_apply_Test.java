package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.util.Util;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.admin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.asAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.asMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.member;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.newProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.randomProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.requestingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.withProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber;

public final class DecryptedGroupUtil_apply_Test {

  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   * <p>
   * If we didn't, newly added fields would not be applied by {@link DecryptedGroupUtil#apply}.
   */
  @Test
  public void ensure_DecryptedGroupUtil_knows_about_all_fields_of_DecryptedGroupChange() {
    int maxFieldFound = getMaxDeclaredFieldNumber(DecryptedGroupChange.class);

    assertEquals("DecryptedGroupUtil and its tests need updating to account for new fields on " + DecryptedGroupChange.class.getName(),
                 19, maxFieldFound);
  }

  @Test
  public void apply_revision() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(9)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(10)
                                                                           .build());

    assertEquals(10, newGroup.getRevision());
  }

  @Test
  public void apply_new_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .addMembers(member1)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .addNewMembers(member2)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .addMembers(member1)
                               .addMembers(member2)
                               .build(),
                 newGroup);
  }

  @Test
  public void apply_remove_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(13)
                                                                     .addMembers(member1)
                                                                     .addMembers(member2)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(14)
                                                                           .addDeleteMembers(member1.getUuid())
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(14)
                               .addMembers(member2)
                               .build(),
                 newGroup);
  }

  @Test
  public void apply_remove_members() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(13)
                                                                     .addMembers(member1)
                                                                     .addMembers(member2)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(14)
                                                                           .addDeleteMembers(member1.getUuid())
                                                                           .addDeleteMembers(member2.getUuid())
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(14)
                               .build(),
                 newGroup);
  }

  @Test
  public void apply_remove_members_not_found() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(13)
                                                                     .addMembers(member1)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(14)
                                                                           .addDeleteMembers(member2.getUuid())
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .addMembers(member1)
                               .setRevision(14)
                               .build(),
                 newGroup);
  }

  @Test
  public void apply_modify_member_role() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = admin(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(13)
                                                                     .addMembers(member1)
                                                                     .addMembers(member2)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(14)
                                                                           .addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder().setUuid(member1.getUuid()).setRole(Member.Role.ADMINISTRATOR))
                                                                           .addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder().setUuid(member2.getUuid()).setRole(Member.Role.DEFAULT))
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(14)
                               .addMembers(asAdmin(member1))
                               .addMembers(asMember(member2))
                               .build(),
                 newGroup);
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException.class)
  public void not_able_to_apply_modify_member_role_for_non_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                           .setRevision(13)
                                           .addMembers(member1)
                                           .build(),
                             DecryptedGroupChange.newBuilder()
                                                 .setRevision(14)
                                                 .addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder()
                                                                                                .setRole(Member.Role.ADMINISTRATOR)
                                                                                                .setUuid(member2.getUuid())
                                                                                                .build())
                                                 .build());
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException.class)
  public void not_able_to_apply_modify_member_role_for_no_role() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());

    DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                           .setRevision(13)
                                           .addMembers(member1)
                                           .build(),
                             DecryptedGroupChange.newBuilder()
                                                 .setRevision(14)
                                                 .addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder()
                                                                                                .setUuid(member1.getUuid())
                                                                                                .build())
                                                 .build());
  }

  @Test
  public void apply_modify_member_profile_keys() throws NotAbleToApplyGroupV2ChangeException {
    ProfileKey      profileKey1  = randomProfileKey();
    ProfileKey      profileKey2a = randomProfileKey();
    ProfileKey      profileKey2b = randomProfileKey();
    DecryptedMember member1      = member(UUID.randomUUID(), profileKey1);
    DecryptedMember member2a     = member(UUID.randomUUID(), profileKey2a);
    DecryptedMember member2b     = withProfileKey(member2a, profileKey2b);

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(13)
                                                                     .addMembers(member1)
                                                                     .addMembers(member2a)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(14)
                                                                           .addModifiedProfileKeys(member2b)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(14)
                               .addMembers(member1)
                               .addMembers(member2b)
                               .build(),
                 newGroup);
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException.class)
  public void cant_apply_modify_member_profile_keys_if_member_not_in_group() throws NotAbleToApplyGroupV2ChangeException {
    ProfileKey      profileKey1  = randomProfileKey();
    ProfileKey      profileKey2a = randomProfileKey();
    ProfileKey      profileKey2b = randomProfileKey();
    DecryptedMember member1      = member(UUID.randomUUID(), profileKey1);
    DecryptedMember member2a     = member(UUID.randomUUID(), profileKey2a);
    DecryptedMember member2b     = member(UUID.randomUUID(), profileKey2b);

    DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                           .setRevision(13)
                                           .addMembers(member1)
                                           .addMembers(member2a)
                                           .build(),
                             DecryptedGroupChange.newBuilder()
                                                 .setRevision(14)
                                                 .addModifiedProfileKeys(member2b)
                                                 .build());
  }

  @Test
  public void apply_modify_admin_profile_keys() throws NotAbleToApplyGroupV2ChangeException {
    UUID            adminUuid    = UUID.randomUUID();
    ProfileKey      profileKey1  = randomProfileKey();
    ProfileKey      profileKey2a = randomProfileKey();
    ProfileKey      profileKey2b = randomProfileKey();
    DecryptedMember member1      = member(UUID.randomUUID(), profileKey1);
    DecryptedMember admin2a      = admin(adminUuid, profileKey2a);

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(13)
                                                                     .addMembers(member1)
                                                                     .addMembers(admin2a)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(14)
                                                                           .addModifiedProfileKeys(DecryptedMember.newBuilder(DecryptedMember.newBuilder()
                                                                                                                                             .setUuid(UuidUtil.toByteString(adminUuid))
                                                                                                                                             .build())
                                                                                                                   .setProfileKey(ByteString.copyFrom(profileKey2b.serialize())))
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(14)
                               .addMembers(member1)
                               .addMembers(admin(adminUuid, profileKey2b))
                               .build(),
                 newGroup);
  }

  @Test
  public void apply_new_pending_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember        member1 = member(UUID.randomUUID());
    DecryptedPendingMember pending = pendingMember(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .addMembers(member1)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .addNewPendingMembers(pending)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .addMembers(member1)
                               .addPendingMembers(pending)
                               .build(),
                 newGroup);
  }

  @Test
  public void apply_new_pending_member_already_pending() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember        member1 = member(UUID.randomUUID());
    DecryptedPendingMember pending = pendingMember(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .addMembers(member1)
                                                                     .addPendingMembers(pending)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .addNewPendingMembers(pending)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .addMembers(member1)
                               .addPendingMembers(pending)
                               .build(),
                 newGroup);
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException.class)
  public void apply_new_pending_member_already_in_group() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember        member1  = member(UUID.randomUUID());
    UUID                   uuid2    = UUID.randomUUID();
    DecryptedMember        member2  = member(uuid2);
    DecryptedPendingMember pending2 = pendingMember(uuid2);

    DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                           .setRevision(10)
                                           .addMembers(member1)
                                           .addMembers(member2)
                                           .build(),
                             DecryptedGroupChange.newBuilder()
                                                 .setRevision(11)
                                                 .addNewPendingMembers(pending2)
                                                 .build());
  }

  @Test
  public void remove_pending_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember        member1     = member(UUID.randomUUID());
    UUID                   pendingUuid = UUID.randomUUID();
    DecryptedPendingMember pending     = pendingMember(pendingUuid);

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .addMembers(member1)
                                                                     .addPendingMembers(pending)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .addDeletePendingMembers(DecryptedPendingMemberRemoval.newBuilder()
                                                                                                                                 .setUuidCipherText(ProtoTestUtils.encrypt(pendingUuid))
                                                                                                                                 .build())
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .addMembers(member1)
                               .build(),
                 newGroup);
  }

  @Test
  public void cannot_remove_pending_member_if_not_in_group() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1     = member(UUID.randomUUID());
    UUID            pendingUuid = UUID.randomUUID();

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .addMembers(member1)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .addDeletePendingMembers(DecryptedPendingMemberRemoval.newBuilder()
                                                                                                                                 .setUuidCipherText(ProtoTestUtils.encrypt(pendingUuid))
                                                                                                                                 .build())
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .addMembers(member1)
                               .build(),
                  newGroup);
  }

  @Test
  public void promote_pending_member() throws NotAbleToApplyGroupV2ChangeException {
    ProfileKey             profileKey2  = randomProfileKey();
    DecryptedMember        member1      = member(UUID.randomUUID());
    UUID                   pending2Uuid = UUID.randomUUID();
    DecryptedPendingMember pending2     = pendingMember(pending2Uuid);
    DecryptedMember        member2      = member(pending2Uuid, profileKey2);

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .addMembers(member1)
                                                                     .addPendingMembers(pending2)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .addPromotePendingMembers(member2)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .addMembers(member1)
                               .addMembers(member2)
                               .build(),
                 newGroup);
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException.class)
  public void cannot_promote_pending_member_if_not_in_group() throws NotAbleToApplyGroupV2ChangeException {
    ProfileKey             profileKey2  = randomProfileKey();
    DecryptedMember        member1      = member(UUID.randomUUID());
    UUID                   pending2Uuid = UUID.randomUUID();
    DecryptedMember        member2      = withProfileKey(admin(pending2Uuid), profileKey2);

    DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                           .setRevision(10)
                                           .addMembers(member1)
                                           .build(),
                             DecryptedGroupChange.newBuilder()
                                                 .setRevision(11)
                                                 .addPromotePendingMembers(member2)
                                                 .build());
  }

  @Test
  public void skip_promote_pending_member_by_direct_add() throws NotAbleToApplyGroupV2ChangeException {
    ProfileKey             profileKey2  = randomProfileKey();
    ProfileKey             profileKey3  = randomProfileKey();
    DecryptedMember        member1      = member(UUID.randomUUID());
    UUID                   pending2Uuid = UUID.randomUUID();
    UUID                   pending3Uuid = UUID.randomUUID();
    UUID                   pending4Uuid = UUID.randomUUID();
    DecryptedPendingMember pending2     = pendingMember(pending2Uuid);
    DecryptedPendingMember pending3     = pendingMember(pending3Uuid);
    DecryptedPendingMember pending4     = pendingMember(pending4Uuid);
    DecryptedMember        member2      = member(pending2Uuid, profileKey2);
    DecryptedMember        member3      = member(pending3Uuid, profileKey3);

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .addMembers(member1)
                                                                     .addPendingMembers(pending2)
                                                                     .addPendingMembers(pending3)
                                                                     .addPendingMembers(pending4)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .addNewMembers(member2)
                                                                           .addNewMembers(member3)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .addMembers(member1)
                               .addMembers(member2)
                               .addMembers(member3)
                               .addPendingMembers(pending4)
                               .build(),
                 newGroup);
  }

  @Test
  public void skip_promote_requesting_member_by_direct_add() throws NotAbleToApplyGroupV2ChangeException {
    ProfileKey                profileKey2     = randomProfileKey();
    ProfileKey                profileKey3     = randomProfileKey();
    DecryptedMember           member1         = member(UUID.randomUUID());
    UUID                      requesting2Uuid = UUID.randomUUID();
    UUID                      requesting3Uuid = UUID.randomUUID();
    UUID                      requesting4Uuid = UUID.randomUUID();
    DecryptedRequestingMember requesting2     = requestingMember(requesting2Uuid);
    DecryptedRequestingMember requesting3     = requestingMember(requesting3Uuid);
    DecryptedRequestingMember requesting4     = requestingMember(requesting4Uuid);
    DecryptedMember           member2         = member(requesting2Uuid, profileKey2);
    DecryptedMember           member3         = member(requesting3Uuid, profileKey3);

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .addMembers(member1)
                                                                     .addRequestingMembers(requesting2)
                                                                     .addRequestingMembers(requesting3)
                                                                     .addRequestingMembers(requesting4)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .addNewMembers(member2)
                                                                           .addNewMembers(member3)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .addMembers(member1)
                               .addMembers(member2)
                               .addMembers(member3)
                               .addRequestingMembers(requesting4)
                               .build(),
                 newGroup);
  }

  @Test
  public void title() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .setTitle("Old title")
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .setNewTitle(DecryptedString.newBuilder().setValue("New title").build())
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .setTitle("New title")
                               .build(),
                 newGroup);
  }

  @Test
  public void avatar() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .setAvatar("https://cnd/oldavatar")
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .setNewAvatar(DecryptedString.newBuilder().setValue("https://cnd/newavatar").build())
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .setAvatar("https://cnd/newavatar")
                               .build(),
                 newGroup);
  }

  @Test
  public void timer() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .setDisappearingMessagesTimer(DecryptedTimer.newBuilder().setDuration(100))
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .setNewTimer(DecryptedTimer.newBuilder().setDuration(2000))
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .setDisappearingMessagesTimer(DecryptedTimer.newBuilder().setDuration(2000))
                               .build(),
                 newGroup);
  }

  @Test
  public void attribute_access() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .setAccessControl(AccessControl.newBuilder()
                                                                                                    .setAttributes(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                                                    .setMembers(AccessControl.AccessRequired.MEMBER)
                                                                                                    .build())
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .setNewAttributeAccess(AccessControl.AccessRequired.MEMBER)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .setAccessControl(AccessControl.newBuilder()
                                                              .setAttributes(AccessControl.AccessRequired.MEMBER)
                                                              .setMembers(AccessControl.AccessRequired.MEMBER)
                                                              .build())
                               .build(),
                 newGroup);
  }

  @Test
  public void membership_access() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .setAccessControl(AccessControl.newBuilder()
                                                                                                    .setAttributes(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                                                    .setMembers(AccessControl.AccessRequired.MEMBER)
                                                                                                    .build())
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .setNewMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .setAccessControl(AccessControl.newBuilder()
                                                              .setAttributes(AccessControl.AccessRequired.ADMINISTRATOR)
                                                              .setMembers(AccessControl.AccessRequired.ADMINISTRATOR)
                                                              .build())
                               .build(),
                 newGroup);
  }

  @Test
  public void change_both_access_levels() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .setAccessControl(AccessControl.newBuilder()
                                                                                                    .setAttributes(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                                                    .setMembers(AccessControl.AccessRequired.MEMBER)
                                                                                                    .build())
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .setNewAttributeAccess(AccessControl.AccessRequired.MEMBER)
                                                                           .setNewMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .setAccessControl(AccessControl.newBuilder()
                                                              .setAttributes(AccessControl.AccessRequired.MEMBER)
                                                              .setMembers(AccessControl.AccessRequired.ADMINISTRATOR)
                                                              .build())
                               .build(),
                 newGroup);
  }

  @Test
  public void invite_link_access() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .setAccessControl(AccessControl.newBuilder()
                                                                                                    .setAttributes(AccessControl.AccessRequired.MEMBER)
                                                                                                    .setMembers(AccessControl.AccessRequired.MEMBER)
                                                                                                    .setAddFromInviteLink(AccessControl.AccessRequired.UNSATISFIABLE)
                                                                                                    .build())
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .setNewInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .setAccessControl(AccessControl.newBuilder()
                                                              .setAttributes(AccessControl.AccessRequired.MEMBER)
                                                              .setMembers(AccessControl.AccessRequired.MEMBER)
                                                              .setAddFromInviteLink(AccessControl.AccessRequired.ADMINISTRATOR)
                                                              .build())
                               .build(),
                 newGroup);
  }

  @Test
  public void apply_new_requesting_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedRequestingMember member1 = requestingMember(UUID.randomUUID());
    DecryptedRequestingMember member2 = requestingMember(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .addRequestingMembers(member1)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .addNewRequestingMembers(member2)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .addRequestingMembers(member1)
                               .addRequestingMembers(member2)
                               .build(),
                 newGroup);
  }

  @Test
  public void apply_remove_requesting_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedRequestingMember member1 = requestingMember(UUID.randomUUID());
    DecryptedRequestingMember member2 = requestingMember(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(13)
                                                                     .addRequestingMembers(member1)
                                                                     .addRequestingMembers(member2)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(14)
                                                                           .addDeleteRequestingMembers(member1.getUuid())
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(14)
                               .addRequestingMembers(member2)
                               .build(),
                 newGroup);
  }

  @Test
  public void promote_requesting_member() throws NotAbleToApplyGroupV2ChangeException {
    UUID                      uuid1       = UUID.randomUUID();
    UUID                      uuid2       = UUID.randomUUID();
    UUID                      uuid3       = UUID.randomUUID();
    ProfileKey                profileKey1 = newProfileKey();
    ProfileKey                profileKey2 = newProfileKey();
    ProfileKey                profileKey3 = newProfileKey();
    DecryptedRequestingMember member1     = requestingMember(uuid1, profileKey1);
    DecryptedRequestingMember member2     = requestingMember(uuid2, profileKey2);
    DecryptedRequestingMember member3     = requestingMember(uuid3, profileKey3);

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(13)
                                                                     .addRequestingMembers(member1)
                                                                     .addRequestingMembers(member2)
                                                                     .addRequestingMembers(member3)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(14)
                                                                           .addPromoteRequestingMembers(DecryptedApproveMember.newBuilder()
                                                                                                                              .setRole(Member.Role.DEFAULT)
                                                                                                                              .setUuid(member1.getUuid()))
                                                                           .addPromoteRequestingMembers(DecryptedApproveMember.newBuilder()
                                                                                                                              .setRole(Member.Role.ADMINISTRATOR)
                                                                                                                              .setUuid(member2.getUuid()))
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(14)
                               .addMembers(member(uuid1, profileKey1))
                               .addMembers(admin(uuid2, profileKey2))
                               .addRequestingMembers(member3)
                               .build(),
                 newGroup);
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException.class)
  public void cannot_apply_promote_requesting_member_without_a_role() throws NotAbleToApplyGroupV2ChangeException {
    UUID                      uuid   = UUID.randomUUID();
    DecryptedRequestingMember member = requestingMember(uuid);

    DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                           .setRevision(13)
                                           .addRequestingMembers(member)
                                           .build(),
                             DecryptedGroupChange.newBuilder()
                                                 .setRevision(14)
                                                 .addPromoteRequestingMembers(DecryptedApproveMember.newBuilder()
                                                                                                    .setUuid(member.getUuid()))
                                                 .build());
  }

  @Test
  public void invite_link_password() throws NotAbleToApplyGroupV2ChangeException {
    ByteString password1 = ByteString.copyFrom(Util.getSecretBytes(16));
    ByteString password2 = ByteString.copyFrom(Util.getSecretBytes(16));

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .setInviteLinkPassword(password1)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .setNewInviteLinkPassword(password2)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .setInviteLinkPassword(password2)
                               .build(),
                 newGroup);
  }

  @Test
  public void invite_link_password_not_changed() throws NotAbleToApplyGroupV2ChangeException {
    ByteString password = ByteString.copyFrom(Util.getSecretBytes(16));

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setRevision(10)
                                                                     .setInviteLinkPassword(password)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setRevision(11)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setRevision(11)
                               .setInviteLinkPassword(password)
                               .build(),
                 newGroup);
  }

}
