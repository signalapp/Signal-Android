package org.whispersystems.signalservice.api.groupsv2;

import org.junit.Test;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.util.Util;

import java.util.List;
import java.util.UUID;

import okio.ByteString;

import static org.junit.Assert.assertEquals;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.admin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.asAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.asMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.bannedMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.member;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.newProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingPniAciMember;
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
                 24, maxFieldFound);
  }

  @Test
  public void apply_revision() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(9)
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(10)
                                                           .build());

    assertEquals(10, newGroup.revision);
  }

  @Test
  public void apply_new_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .members(List.of(member1))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newMembers(List.of(member2))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .members(List.of(member1, member2))
                     .build(),
                 newGroup);
  }

  @Test
  public void apply_new_member_already_in_the_group() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .members(List.of(member1, member2))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newMembers(List.of(member2))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .members(List.of(member1, member2))
                     .build(),
                 newGroup);
  }

  @Test
  public void apply_new_member_already_in_the_group_by_uuid() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1     = member(UUID.randomUUID());
    UUID            member2Uuid = UUID.randomUUID();
    DecryptedMember member2a    = member(member2Uuid, newProfileKey());
    DecryptedMember member2b    = member(member2Uuid, newProfileKey());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .members(List.of(member1, member2a))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newMembers(List.of(member2b))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .members(List.of(member1, member2b))
                     .build(),
                 newGroup);
  }

  @Test
  public void apply_remove_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(13)
                                                           .members(List.of(member1, member2))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(14)
                                                           .deleteMembers(List.of(member1.aciBytes))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(14)
                     .members(List.of(member2))
                     .build(),
                 newGroup);
  }

  @Test
  public void apply_remove_members() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(13)
                                                           .members(List.of(member1, member2))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(14)
                                                           .deleteMembers(List.of(member1.aciBytes, member2.aciBytes))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(14)
                     .build(),
                 newGroup);
  }

  @Test
  public void apply_remove_members_not_found() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(13)
                                                           .members(List.of(member1))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(14)
                                                           .deleteMembers(List.of(member2.aciBytes))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .members(List.of(member1))
                     .revision(14)
                     .build(),
                 newGroup);
  }

  @Test
  public void apply_modify_member_role() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = admin(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(13)
                                                           .members(List.of(member1, member2))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(14)
                                                           .modifyMemberRoles(List.of(new DecryptedModifyMemberRole.Builder().aciBytes(member1.aciBytes).role(Member.Role.ADMINISTRATOR).build(),
                                                                                      new DecryptedModifyMemberRole.Builder().aciBytes(member2.aciBytes).role(Member.Role.DEFAULT).build()))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(14)
                     .members(List.of(asAdmin(member1), asMember(member2)))
                     .build(),
                 newGroup);
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException.class)
  public void not_able_to_apply_modify_member_role_for_non_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                 .revision(13)
                                 .members(List.of(member1))
                                 .build(),
                             new DecryptedGroupChange.Builder()
                                 .revision(14)
                                 .modifyMemberRoles(List.of(new DecryptedModifyMemberRole.Builder()
                                                                .role(Member.Role.ADMINISTRATOR)
                                                                .aciBytes(member2.aciBytes)
                                                                .build()))
                                 .build());
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException.class)
  public void not_able_to_apply_modify_member_role_for_no_role() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());

    DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                 .revision(13)
                                 .members(List.of(member1))
                                 .build(),
                             new DecryptedGroupChange.Builder()
                                 .revision(14)
                                 .modifyMemberRoles(List.of(new DecryptedModifyMemberRole.Builder()
                                                                .aciBytes(member1.aciBytes)
                                                                .build()))
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

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(13)
                                                           .members(List.of(member1, member2a))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(14)
                                                           .modifiedProfileKeys(List.of(member2b))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(14)
                     .members(List.of(member1, member2b))
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

    DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                 .revision(13)
                                 .members(List.of(member1, member2a))
                                 .build(),
                             new DecryptedGroupChange.Builder()
                                 .revision(14)
                                 .modifiedProfileKeys(List.of(member2b))
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

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(13)
                                                           .members(List.of(member1, admin2a))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(14)
                                                           .modifiedProfileKeys(List.of(new DecryptedMember.Builder()
                                                                                            .aciBytes(UuidUtil.toByteString(adminUuid))
                                                                                            .build()
                                                                                            .newBuilder()
                                                                                            .profileKey(ByteString.of(profileKey2b.serialize()))
                                                                                            .build()))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(14)
                     .members(List.of(member1, admin(adminUuid, profileKey2b)))
                     .build(),
                 newGroup);
  }

  @Test
  public void apply_new_pending_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember        member1 = member(UUID.randomUUID());
    DecryptedPendingMember pending = pendingMember(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .members(List.of(member1))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newPendingMembers(List.of(pending))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .members(List.of(member1))
                     .pendingMembers(List.of(pending))
                     .build(),
                 newGroup);
  }

  @Test
  public void apply_new_pending_member_already_pending() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember        member1 = member(UUID.randomUUID());
    DecryptedPendingMember pending = pendingMember(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .members(List.of(member1))
                                                           .pendingMembers(List.of(pending))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newPendingMembers(List.of(pending))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .members(List.of(member1))
                     .pendingMembers(List.of(pending))
                     .build(),
                 newGroup);
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException.class)
  public void apply_new_pending_member_already_in_group() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember        member1  = member(UUID.randomUUID());
    UUID                   uuid2    = UUID.randomUUID();
    DecryptedMember        member2  = member(uuid2);
    DecryptedPendingMember pending2 = pendingMember(uuid2);

    DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                 .revision(10)
                                 .members(List.of(member1, member2))
                                 .build(),
                             new DecryptedGroupChange.Builder()
                                 .revision(11)
                                 .newPendingMembers(List.of(pending2))
                                 .build());
  }

  @Test
  public void remove_pending_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember        member1     = member(UUID.randomUUID());
    UUID                   pendingUuid = UUID.randomUUID();
    DecryptedPendingMember pending     = pendingMember(pendingUuid);

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .members(List.of(member1))
                                                           .pendingMembers(List.of(pending))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .deletePendingMembers(List.of(new DecryptedPendingMemberRemoval.Builder()
                                                                                             .serviceIdCipherText(ProtoTestUtils.encrypt(pendingUuid))
                                                                                             .build()))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .members(List.of(member1))
                     .build(),
                 newGroup);
  }

  @Test
  public void cannot_remove_pending_member_if_not_in_group() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember member1     = member(UUID.randomUUID());
    UUID            pendingUuid = UUID.randomUUID();

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .members(List.of(member1))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .deletePendingMembers(List.of(new DecryptedPendingMemberRemoval.Builder()
                                                                                             .serviceIdCipherText(ProtoTestUtils.encrypt(pendingUuid))
                                                                                             .build()))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .members(List.of(member1))
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

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .members(List.of(member1))
                                                           .pendingMembers(List.of(pending2))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .promotePendingMembers(List.of(member2))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .members(List.of(member1, member2))
                     .build(),
                 newGroup);
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException.class)
  public void cannot_promote_pending_member_if_not_in_group() throws NotAbleToApplyGroupV2ChangeException {
    ProfileKey      profileKey2  = randomProfileKey();
    DecryptedMember member1      = member(UUID.randomUUID());
    UUID            pending2Uuid = UUID.randomUUID();
    DecryptedMember member2      = withProfileKey(admin(pending2Uuid), profileKey2);

    DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                 .revision(10)
                                 .members(List.of(member1))
                                 .build(),
                             new DecryptedGroupChange.Builder()
                                 .revision(11)
                                 .promotePendingMembers(List.of(member2))
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

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .members(List.of(member1))
                                                           .pendingMembers(List.of(pending2, pending3, pending4))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newMembers(List.of(member2, member3))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .members(List.of(member1, member2, member3))
                     .pendingMembers(List.of(pending4))
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

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .members(List.of(member1))
                                                           .requestingMembers(List.of(requesting2, requesting3, requesting4))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newMembers(List.of(member2, member3))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .members(List.of(member1, member2, member3))
                     .requestingMembers(List.of(requesting4))
                     .build(),
                 newGroup);
  }

  @Test
  public void title() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .title("Old title")
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newTitle(new DecryptedString.Builder().value_("New title").build())
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .title("New title")
                     .build(),
                 newGroup);
  }

  @Test
  public void description() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .description("Old description")
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newDescription(new DecryptedString.Builder().value_("New Description").build())
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .description("New Description")
                     .build(),
                 newGroup);
  }

  @Test
  public void isAnnouncementGroup() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .isAnnouncementGroup(EnabledState.DISABLED)
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newIsAnnouncementGroup(EnabledState.ENABLED)
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .isAnnouncementGroup(EnabledState.ENABLED)
                     .build(),
                 newGroup);
  }

  @Test
  public void avatar() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .avatar("https://cnd/oldavatar")
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newAvatar(new DecryptedString.Builder().value_("https://cnd/newavatar").build())
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .avatar("https://cnd/newavatar")
                     .build(),
                 newGroup);
  }

  @Test
  public void timer() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .disappearingMessagesTimer(new DecryptedTimer.Builder().duration(100).build())
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newTimer(new DecryptedTimer.Builder().duration(2000).build())
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .disappearingMessagesTimer(new DecryptedTimer.Builder().duration(2000).build())
                     .build(),
                 newGroup);
  }

  @Test
  public void attribute_access() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .accessControl(new AccessControl.Builder()
                                                                              .attributes(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                              .members(AccessControl.AccessRequired.MEMBER)
                                                                              .build())
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newAttributeAccess(AccessControl.AccessRequired.MEMBER)
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .accessControl(new AccessControl.Builder()
                                        .attributes(AccessControl.AccessRequired.MEMBER)
                                        .members(AccessControl.AccessRequired.MEMBER)
                                        .build())
                     .build(),
                 newGroup);
  }

  @Test
  public void membership_access() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .accessControl(new AccessControl.Builder()
                                                                              .attributes(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                              .members(AccessControl.AccessRequired.MEMBER)
                                                                              .build())
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .accessControl(new AccessControl.Builder()
                                        .attributes(AccessControl.AccessRequired.ADMINISTRATOR)
                                        .members(AccessControl.AccessRequired.ADMINISTRATOR)
                                        .build())
                     .build(),
                 newGroup);
  }

  @Test
  public void change_both_access_levels() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .accessControl(new AccessControl.Builder()
                                                                              .attributes(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                              .members(AccessControl.AccessRequired.MEMBER)
                                                                              .build())
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newAttributeAccess(AccessControl.AccessRequired.MEMBER)
                                                           .newMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .accessControl(new AccessControl.Builder()
                                        .attributes(AccessControl.AccessRequired.MEMBER)
                                        .members(AccessControl.AccessRequired.ADMINISTRATOR)
                                        .build())
                     .build(),
                 newGroup);
  }

  @Test
  public void invite_link_access() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .accessControl(new AccessControl.Builder()
                                                                              .attributes(AccessControl.AccessRequired.MEMBER)
                                                                              .members(AccessControl.AccessRequired.MEMBER)
                                                                              .addFromInviteLink(AccessControl.AccessRequired.UNSATISFIABLE)
                                                                              .build())
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .accessControl(new AccessControl.Builder()
                                        .attributes(AccessControl.AccessRequired.MEMBER)
                                        .members(AccessControl.AccessRequired.MEMBER)
                                        .addFromInviteLink(AccessControl.AccessRequired.ADMINISTRATOR)
                                        .build())
                     .build(),
                 newGroup);
  }

  @Test
  public void apply_new_requesting_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedRequestingMember member1 = requestingMember(UUID.randomUUID());
    DecryptedRequestingMember member2 = requestingMember(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .requestingMembers(List.of(member1))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newRequestingMembers(List.of(member2))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .requestingMembers(List.of(member1, member2))
                     .build(),
                 newGroup);
  }

  @Test
  public void apply_remove_requesting_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedRequestingMember member1 = requestingMember(UUID.randomUUID());
    DecryptedRequestingMember member2 = requestingMember(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(13)
                                                           .requestingMembers(List.of(member1, member2))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(14)
                                                           .deleteRequestingMembers(List.of(member1.aciBytes))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(14)
                     .requestingMembers(List.of(member2))
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

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(13)
                                                           .requestingMembers(List.of(member1, member2, member3))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(14)
                                                           .promoteRequestingMembers(List.of(new DecryptedApproveMember.Builder()
                                                                                                 .role(Member.Role.DEFAULT)
                                                                                                 .aciBytes(member1.aciBytes)
                                                                                                 .build(),
                                                                                             new DecryptedApproveMember.Builder()
                                                                                                 .role(Member.Role.ADMINISTRATOR)
                                                                                                 .aciBytes(member2.aciBytes)
                                                                                                 .build()))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(14)
                     .members(List.of(member(uuid1, profileKey1), admin(uuid2, profileKey2)))
                     .requestingMembers(List.of(member3))
                     .build(),
                 newGroup);
  }

  @Test(expected = NotAbleToApplyGroupV2ChangeException.class)
  public void cannot_apply_promote_requesting_member_without_a_role() throws NotAbleToApplyGroupV2ChangeException {
    UUID                      uuid   = UUID.randomUUID();
    DecryptedRequestingMember member = requestingMember(uuid);

    DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                 .revision(13)
                                 .requestingMembers(List.of(member))
                                 .build(),
                             new DecryptedGroupChange.Builder()
                                 .revision(14)
                                 .promoteRequestingMembers(List.of(new DecryptedApproveMember.Builder().aciBytes(member.aciBytes).build()))
                                 .build());
  }

  @Test
  public void invite_link_password() throws NotAbleToApplyGroupV2ChangeException {
    ByteString password1 = ByteString.of(Util.getSecretBytes(16));
    ByteString password2 = ByteString.of(Util.getSecretBytes(16));

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .inviteLinkPassword(password1)
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newInviteLinkPassword(password2)
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .inviteLinkPassword(password2)
                     .build(),
                 newGroup);
  }

  @Test
  public void invite_link_password_not_changed() throws NotAbleToApplyGroupV2ChangeException {
    ByteString password = ByteString.of(Util.getSecretBytes(16));

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .inviteLinkPassword(password)
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .inviteLinkPassword(password)
                     .build(),
                 newGroup);
  }


  @Test
  public void apply_new_banned_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember       member1 = member(UUID.randomUUID());
    DecryptedBannedMember banned  = bannedMember(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .members(List.of(member1))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newBannedMembers(List.of(banned))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .members(List.of(member1))
                     .bannedMembers(List.of(banned))
                     .build(),
                 newGroup);
  }

  @Test
  public void apply_new_banned_member_already_banned() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember       member1 = member(UUID.randomUUID());
    DecryptedBannedMember banned  = bannedMember(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .members(List.of(member1))
                                                           .bannedMembers(List.of(banned))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .newBannedMembers(List.of(banned))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .members(List.of(member1))
                     .bannedMembers(List.of(banned))
                     .build(),
                 newGroup);
  }

  @Test
  public void remove_banned_member() throws NotAbleToApplyGroupV2ChangeException {
    DecryptedMember       member1    = member(UUID.randomUUID());
    UUID                  bannedUuid = UUID.randomUUID();
    DecryptedBannedMember banned     = bannedMember(bannedUuid);

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .members(List.of(member1))
                                                           .bannedMembers(List.of(banned))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .deleteBannedMembers(List.of(new DecryptedBannedMember.Builder()
                                                                                            .serviceIdBytes(UuidUtil.toByteString(bannedUuid))
                                                                                            .build()))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .members(List.of(member1))
                     .build(),
                 newGroup);
  }

  @Test
  public void promote_pending_member_pni_aci() throws NotAbleToApplyGroupV2ChangeException {
    ProfileKey             profileKey2 = randomProfileKey();
    DecryptedMember        member1     = member(UUID.randomUUID());
    UUID                   pending2Aci = UUID.randomUUID();
    UUID                   pending2Pni = UUID.randomUUID();
    DecryptedPendingMember pending2    = pendingMember(pending2Pni);
    DecryptedMember        member2     = pendingPniAciMember(pending2Aci, pending2Pni, profileKey2);

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(new DecryptedGroup.Builder()
                                                           .revision(10)
                                                           .members(List.of(member1))
                                                           .pendingMembers(List.of(pending2))
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(11)
                                                           .promotePendingPniAciMembers(List.of(member2))
                                                           .build());

    assertEquals(new DecryptedGroup.Builder()
                     .revision(11)
                     .members(List.of(member1, member2))
                     .build(),
                 newGroup);
  }
}
