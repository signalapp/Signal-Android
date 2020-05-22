package org.whispersystems.signalservice.api.groupsv2;

import org.junit.Test;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.signal.zkgroup.profiles.ProfileKey;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.admin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.asAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.asMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.member;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.randomProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.withProfileKey;

public final class DecryptedGroupUtil_apply_Test {

  @Test
  public void apply_version() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(9)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(10)
                                                                           .build());

    assertEquals(10, newGroup.getVersion());
  }

  @Test
  public void apply_new_member() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(10)
                                                                     .addMembers(member1)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(11)
                                                                           .addNewMembers(member2)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(11)
                               .addMembers(member1)
                               .addMembers(member2)
                               .build(),
                 newGroup);
  }

  @Test
  public void apply_remove_member() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(13)
                                                                     .addMembers(member1)
                                                                     .addMembers(member2)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(14)
                                                                           .addDeleteMembers(member1.getUuid())
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(14)
                               .addMembers(member2)
                               .build(),
                 newGroup);
  }

  @Test
  public void apply_remove_members() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(13)
                                                                     .addMembers(member1)
                                                                     .addMembers(member2)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(14)
                                                                           .addDeleteMembers(member1.getUuid())
                                                                           .addDeleteMembers(member2.getUuid())
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(14)
                               .build(),
                 newGroup);
  }

  @Test(expected = DecryptedGroupUtil.NotAbleToApplyChangeException.class)
  public void apply_remove_members_not_found() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = member(UUID.randomUUID());

    DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                           .setVersion(13)
                                           .addMembers(member1)
                                           .build(),
                             DecryptedGroupChange.newBuilder()
                                                 .setVersion(14)
                                                 .addDeleteMembers(member2.getUuid())
                                                 .build());
  }

  @Test
  public void apply_modify_member_role() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    DecryptedMember member1 = member(UUID.randomUUID());
    DecryptedMember member2 = admin(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(13)
                                                                     .addMembers(member1)
                                                                     .addMembers(member2)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(14)
                                                                           .addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder().setUuid(member1.getUuid()).setRole(Member.Role.ADMINISTRATOR))
                                                                           .addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder().setUuid(member2.getUuid()).setRole(Member.Role.DEFAULT))
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(14)
                               .addMembers(asAdmin(member1))
                               .addMembers(asMember(member2))
                               .build(),
                 newGroup);
  }

  @Test
  public void apply_modify_member_profile_keys() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    ProfileKey      profileKey1  = randomProfileKey();
    ProfileKey      profileKey2a = randomProfileKey();
    ProfileKey      profileKey2b = randomProfileKey();
    DecryptedMember member1      = member(UUID.randomUUID(), profileKey1);
    DecryptedMember member2a     = member(UUID.randomUUID(), profileKey2a);
    DecryptedMember member2b     = withProfileKey(member2a, profileKey2b);

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(13)
                                                                     .addMembers(member1)
                                                                     .addMembers(member2a)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(14)
                                                                           .addModifiedProfileKeys(member2b)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(14)
                               .addMembers(member1)
                               .addMembers(member2b)
                               .build(),
                 newGroup);
  }

  @Test
  public void apply_new_pending_member() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    DecryptedMember        member1 = member(UUID.randomUUID());
    DecryptedPendingMember pending = pendingMember(UUID.randomUUID());

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(10)
                                                                     .addMembers(member1)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(11)
                                                                           .addNewPendingMembers(pending)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(11)
                               .addMembers(member1)
                               .addPendingMembers(pending)
                               .build(),
                 newGroup);
  }

  @Test
  public void remove_pending_member() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    DecryptedMember        member1     = member(UUID.randomUUID());
    UUID                   pendingUuid = UUID.randomUUID();
    DecryptedPendingMember pending     = pendingMember(pendingUuid);

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(10)
                                                                     .addMembers(member1)
                                                                     .addPendingMembers(pending)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(11)
                                                                           .addDeletePendingMembers(DecryptedPendingMemberRemoval.newBuilder()
                                                                                                                                 .setUuidCipherText(ProtoTestUtils.encrypt(pendingUuid))
                                                                                                                                 .build())
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(11)
                               .addMembers(member1)
                               .build(),
                 newGroup);
  }

  @Test
  public void promote_pending_member() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    ProfileKey             profileKey2  = randomProfileKey();
    DecryptedMember        member1      = member(UUID.randomUUID());
    UUID                   pending2Uuid = UUID.randomUUID();
    DecryptedPendingMember pending2     = pendingMember(pending2Uuid);
    DecryptedMember        member2      = member(pending2Uuid, profileKey2);

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(10)
                                                                     .addMembers(member1)
                                                                     .addPendingMembers(pending2)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(11)
                                                                           .addPromotePendingMembers(member2)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(11)
                               .addMembers(member1)
                               .addMembers(member2)
                               .build(),
                 newGroup);
  }

  @Test
  public void promote_direct_to_admin() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    ProfileKey             profileKey2  = randomProfileKey();
    DecryptedMember        member1      = member(UUID.randomUUID());
    UUID                   pending2Uuid = UUID.randomUUID();
    DecryptedPendingMember pending2     = pendingMember(pending2Uuid);
    DecryptedMember        member2      = withProfileKey(admin(pending2Uuid), profileKey2);

    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(10)
                                                                     .addMembers(member1)
                                                                     .addPendingMembers(pending2)
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(11)
                                                                           .addPromotePendingMembers(member2)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(11)
                               .addMembers(member1)
                               .addMembers(member2)
                               .build(),
                 newGroup);
  }

  @Test
  public void title() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(10)
                                                                     .setTitle("Old title")
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(11)
                                                                           .setNewTitle(DecryptedString.newBuilder().setValue("New title").build())
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(11)
                               .setTitle("New title")
                               .build(),
                 newGroup);
  }

  @Test
  public void avatar() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(10)
                                                                     .setAvatar("https://cnd/oldavatar")
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(11)
                                                                           .setNewAvatar(DecryptedString.newBuilder().setValue("https://cnd/newavatar").build())
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(11)
                               .setAvatar("https://cnd/newavatar")
                               .build(),
                 newGroup);
  }

  @Test
  public void timer() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(10)
                                                                     .setDisappearingMessagesTimer(DecryptedTimer.newBuilder().setDuration(100))
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(11)
                                                                           .setNewTimer(DecryptedTimer.newBuilder().setDuration(2000))
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(11)
                               .setDisappearingMessagesTimer(DecryptedTimer.newBuilder().setDuration(2000))
                               .build(),
                 newGroup);
  }

  @Test
  public void attribute_access() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(10)
                                                                     .setAccessControl(AccessControl.newBuilder()
                                                                                                    .setAttributes(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                                                    .setMembers(AccessControl.AccessRequired.MEMBER)
                                                                                                    .build())
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(11)
                                                                           .setNewAttributeAccess(AccessControl.AccessRequired.MEMBER)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(11)
                               .setAccessControl(AccessControl.newBuilder()
                                                              .setAttributes(AccessControl.AccessRequired.MEMBER)
                                                              .setMembers(AccessControl.AccessRequired.MEMBER)
                                                              .build())
                               .build(),
                 newGroup);
  }

  @Test
  public void membership_access() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(10)
                                                                     .setAccessControl(AccessControl.newBuilder()
                                                                                                    .setAttributes(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                                                    .setMembers(AccessControl.AccessRequired.MEMBER)
                                                                                                    .build())
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(11)
                                                                           .setNewMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(11)
                               .setAccessControl(AccessControl.newBuilder()
                                                              .setAttributes(AccessControl.AccessRequired.ADMINISTRATOR)
                                                              .setMembers(AccessControl.AccessRequired.ADMINISTRATOR)
                                                              .build())
                               .build(),
                 newGroup);
  }

  @Test
  public void change_both_access_levels() throws DecryptedGroupUtil.NotAbleToApplyChangeException {
    DecryptedGroup newGroup = DecryptedGroupUtil.apply(DecryptedGroup.newBuilder()
                                                                     .setVersion(10)
                                                                     .setAccessControl(AccessControl.newBuilder()
                                                                                                    .setAttributes(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                                                    .setMembers(AccessControl.AccessRequired.MEMBER)
                                                                                                    .build())
                                                                     .build(),
                                                       DecryptedGroupChange.newBuilder()
                                                                           .setVersion(11)
                                                                           .setNewAttributeAccess(AccessControl.AccessRequired.MEMBER)
                                                                           .setNewMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                           .build());

    assertEquals(DecryptedGroup.newBuilder()
                               .setVersion(11)
                               .setAccessControl(AccessControl.newBuilder()
                                                              .setAttributes(AccessControl.AccessRequired.MEMBER)
                                                              .setMembers(AccessControl.AccessRequired.ADMINISTRATOR)
                                                              .build())
                               .build(),
                 newGroup);
  }
}
