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
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.admin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.approveAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.approveMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.demoteAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.member;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.newProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.pendingMemberRemoval;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.promoteAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.randomProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.requestingMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.withProfileKey;
import static org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber;

public final class GroupChangeReconstructTest {

  /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   * <p>
   * If we didn't, newly added fields would not be detected by {@link GroupChangeReconstruct#reconstructGroupChange}.
   */
  @Test
  public void ensure_GroupChangeReconstruct_knows_about_all_fields_of_DecryptedGroup() {
    int maxFieldFound = getMaxDeclaredFieldNumber(DecryptedGroup.class);

    assertEquals("GroupChangeReconstruct and its tests need updating to account for new fields on " + DecryptedGroup.class.getName(),
                 10, maxFieldFound);
  }

  @Test
  public void empty_to_empty() {
    DecryptedGroup from = DecryptedGroup.newBuilder().build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().build(), decryptedGroupChange);
  }

  @Test
  public void revision_set_to_the_target() {
    DecryptedGroup from = DecryptedGroup.newBuilder().setRevision(10).build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().setRevision(20).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(20, decryptedGroupChange.getRevision());
  }

  @Test
  public void title_change() {
    DecryptedGroup from = DecryptedGroup.newBuilder().setTitle("A").build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().setTitle("B").build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().setNewTitle(DecryptedString.newBuilder().setValue("B")).build(), decryptedGroupChange);
  }

  @Test
  public void avatar_change() {
    DecryptedGroup from = DecryptedGroup.newBuilder().setAvatar("A").build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().setAvatar("B").build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().setNewAvatar(DecryptedString.newBuilder().setValue("B")).build(), decryptedGroupChange);
  }

  @Test
  public void timer_change() {
    DecryptedGroup from = DecryptedGroup.newBuilder().setDisappearingMessagesTimer(DecryptedTimer.newBuilder().setDuration(100)).build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().setDisappearingMessagesTimer(DecryptedTimer.newBuilder().setDuration(200)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().setNewTimer(DecryptedTimer.newBuilder().setDuration(200)).build(), decryptedGroupChange);
  }

  @Test
  public void access_control_change_attributes() {
    DecryptedGroup from = DecryptedGroup.newBuilder().setAccessControl(AccessControl.newBuilder().setAttributes(AccessControl.AccessRequired.MEMBER)).build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().setAccessControl(AccessControl.newBuilder().setAttributes(AccessControl.AccessRequired.ADMINISTRATOR)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().setNewAttributeAccess(AccessControl.AccessRequired.ADMINISTRATOR).build(), decryptedGroupChange);
  }

  @Test
  public void access_control_change_membership() {
    DecryptedGroup from = DecryptedGroup.newBuilder().setAccessControl(AccessControl.newBuilder().setMembers(AccessControl.AccessRequired.ADMINISTRATOR)).build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().setAccessControl(AccessControl.newBuilder().setMembers(AccessControl.AccessRequired.MEMBER)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().setNewMemberAccess(AccessControl.AccessRequired.MEMBER).build(), decryptedGroupChange);
  }

  @Test
  public void access_control_change_membership_and_attributes() {
    DecryptedGroup from = DecryptedGroup.newBuilder().setAccessControl(AccessControl.newBuilder().setMembers(AccessControl.AccessRequired.MEMBER)
                                                     .setAttributes(AccessControl.AccessRequired.ADMINISTRATOR)).build();
    DecryptedGroup to   = DecryptedGroup.newBuilder().setAccessControl(AccessControl.newBuilder().setMembers(AccessControl.AccessRequired.ADMINISTRATOR)
                                                     .setAttributes(AccessControl.AccessRequired.MEMBER)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().setNewMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                   .setNewAttributeAccess(AccessControl.AccessRequired.MEMBER).build(), decryptedGroupChange);
  }

  @Test
  public void new_member() {
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuidNew)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addNewMembers(member(uuidNew)).build(), decryptedGroupChange);
  }

  @Test
  public void removed_member() {
    UUID           uuidOld = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuidOld)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addDeleteMembers(UuidUtil.toByteString(uuidOld)).build(), decryptedGroupChange);
  }

  @Test
  public void new_member_and_existing_member() {
    UUID           uuidOld = UUID.randomUUID();
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addMembers(member(uuidOld)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuidOld)).addMembers(member(uuidNew)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addNewMembers(member(uuidNew)).build(), decryptedGroupChange);
  }

  @Test
  public void removed_member_and_remaining_member() {
    UUID           uuidOld       = UUID.randomUUID();
    UUID           uuidRemaining = UUID.randomUUID();
    DecryptedGroup from          = DecryptedGroup.newBuilder().addMembers(member(uuidOld)).addMembers(member(uuidRemaining)).build();
    DecryptedGroup to            = DecryptedGroup.newBuilder().addMembers(member(uuidRemaining)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addDeleteMembers(UuidUtil.toByteString(uuidOld)).build(), decryptedGroupChange);
  }

  @Test
  public void new_member_by_invite() {
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addPendingMembers(pendingMember(uuidNew)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addMembers(member(uuidNew)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addPromotePendingMembers(member(uuidNew)).build(), decryptedGroupChange);
  }

  @Test
  public void uninvited_member_by_invite() {
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().addPendingMembers(pendingMember(uuidNew)).build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addDeletePendingMembers(pendingMemberRemoval(uuidNew)).build(), decryptedGroupChange);
  }

  @Test
  public void new_invite() {
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder().build();
    DecryptedGroup to      = DecryptedGroup.newBuilder().addPendingMembers(pendingMember(uuidNew)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addNewPendingMembers(pendingMember(uuidNew)).build(), decryptedGroupChange);
  }

  @Test
  public void to_admin() {
    UUID           uuid       = UUID.randomUUID();
    ProfileKey     profileKey = randomProfileKey();
    DecryptedGroup from       = DecryptedGroup.newBuilder().addMembers(withProfileKey(member(uuid), profileKey)).build();
    DecryptedGroup to         = DecryptedGroup.newBuilder().addMembers(withProfileKey(admin(uuid), profileKey)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addModifyMemberRoles(promoteAdmin(uuid)).build(), decryptedGroupChange);
  }

  @Test
  public void to_member() {
    UUID           uuid       = UUID.randomUUID();
    ProfileKey     profileKey = randomProfileKey();
    DecryptedGroup from       = DecryptedGroup.newBuilder().addMembers(withProfileKey(admin(uuid), profileKey)).build();
    DecryptedGroup to         = DecryptedGroup.newBuilder().addMembers(withProfileKey(member(uuid), profileKey)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addModifyMemberRoles(demoteAdmin(uuid)).build(), decryptedGroupChange);
  }

  @Test
  public void profile_key_change_member() {
    UUID           uuid        = UUID.randomUUID();
    ProfileKey     profileKey1 = randomProfileKey();
    ProfileKey     profileKey2 = randomProfileKey();
    DecryptedGroup from        = DecryptedGroup.newBuilder().addMembers(withProfileKey(admin(uuid),profileKey1)).build();
    DecryptedGroup to          = DecryptedGroup.newBuilder().addMembers(withProfileKey(admin(uuid),profileKey2)).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder().addModifiedProfileKeys(withProfileKey(admin(uuid),profileKey2)).build(), decryptedGroupChange);
  }

  @Test
  public void new_invite_access() {
    DecryptedGroup from = DecryptedGroup.newBuilder()
                                        .setAccessControl(AccessControl.newBuilder()
                                                                       .setAddFromInviteLink(AccessControl.AccessRequired.ADMINISTRATOR))
                                        .build();
    DecryptedGroup to   = DecryptedGroup.newBuilder()
                                        .setAccessControl(AccessControl.newBuilder()
                                                                       .setAddFromInviteLink(AccessControl.AccessRequired.UNSATISFIABLE))
                                        .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder()
                                     .setNewInviteLinkAccess(AccessControl.AccessRequired.UNSATISFIABLE)
                                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void new_requesting_members() {
    UUID           member1     = UUID.randomUUID();
    ProfileKey     profileKey1 = newProfileKey();
    DecryptedGroup from        = DecryptedGroup.newBuilder()
                                               .build();
    DecryptedGroup to          = DecryptedGroup.newBuilder()
                                               .addRequestingMembers(requestingMember(member1, profileKey1))
                                               .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder()
                                     .addNewRequestingMembers(requestingMember(member1, profileKey1))
                                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void new_requesting_members_ignores_existing_by_uuid() {
    UUID           member1     = UUID.randomUUID();
    UUID           member2     = UUID.randomUUID();
    ProfileKey     profileKey2 = newProfileKey();
    DecryptedGroup from        = DecryptedGroup.newBuilder()
                                               .addRequestingMembers(requestingMember(member1, newProfileKey()))
                                               .build();
    DecryptedGroup to          = DecryptedGroup.newBuilder()
                                               .addRequestingMembers(requestingMember(member1, newProfileKey()))
                                               .addRequestingMembers(requestingMember(member2, profileKey2))
                                               .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder()
                                     .addNewRequestingMembers(requestingMember(member2, profileKey2))
                                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void removed_requesting_members() {
    UUID           member1 = UUID.randomUUID();
    DecryptedGroup from    = DecryptedGroup.newBuilder()
                                           .addRequestingMembers(requestingMember(member1, newProfileKey()))
                                           .build();
    DecryptedGroup to      = DecryptedGroup.newBuilder()
                                           .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder()
                                     .addDeleteRequestingMembers(UuidUtil.toByteString(member1))
                                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void promote_requesting_members() {
    UUID           member1     = UUID.randomUUID();
    ProfileKey     profileKey1 = newProfileKey();
    UUID           member2     = UUID.randomUUID();
    ProfileKey     profileKey2 = newProfileKey();
    DecryptedGroup from        = DecryptedGroup.newBuilder()
                                               .addRequestingMembers(requestingMember(member1, profileKey1))
                                               .addRequestingMembers(requestingMember(member2, profileKey2))
                                               .build();
    DecryptedGroup to          = DecryptedGroup.newBuilder()
                                               .addMembers(member(member1, profileKey1))
                                               .addMembers(admin(member2, profileKey2))
                                               .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder()
                                     .addPromoteRequestingMembers(approveMember(member1))
                                     .addPromoteRequestingMembers(approveAdmin(member2))
                                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void new_invite_link_password() {
    ByteString     password1 = ByteString.copyFrom(Util.getSecretBytes(16));
    ByteString     password2 = ByteString.copyFrom(Util.getSecretBytes(16));
    DecryptedGroup from      = DecryptedGroup.newBuilder()
                                             .setInviteLinkPassword(password1)
                                             .build();
    DecryptedGroup to        = DecryptedGroup.newBuilder()
                                             .setInviteLinkPassword(password2)
                                             .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(DecryptedGroupChange.newBuilder()
                                     .setNewInviteLinkPassword(password2)
                                     .build(),
                 decryptedGroupChange);
  }
}