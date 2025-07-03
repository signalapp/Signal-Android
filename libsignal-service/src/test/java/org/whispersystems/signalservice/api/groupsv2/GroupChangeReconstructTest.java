package org.whispersystems.signalservice.api.groupsv2;

import org.junit.Test;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
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
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.approveAdmin;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.approveMember;
import static org.whispersystems.signalservice.api.groupsv2.ProtoTestUtils.bannedMember;
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
    int maxFieldFound = getMaxDeclaredFieldNumber(DecryptedGroup.class, ProtobufTestUtils.IGNORED_DECRYPTED_GROUP_TAGS);

    assertEquals("GroupChangeReconstruct and its tests need updating to account for new fields on " + DecryptedGroup.class.getName(),
                 13, maxFieldFound);
  }

  @Test
  public void empty_to_empty() {
    DecryptedGroup from = new DecryptedGroup.Builder().build();
    DecryptedGroup to   = new DecryptedGroup.Builder().build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().build(), decryptedGroupChange);
  }

  @Test
  public void revision_set_to_the_target() {
    DecryptedGroup from = new DecryptedGroup.Builder().revision(10).build();
    DecryptedGroup to   = new DecryptedGroup.Builder().revision(20).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(20, decryptedGroupChange.revision);
  }

  @Test
  public void title_change() {
    DecryptedGroup from = new DecryptedGroup.Builder().title("A").build();
    DecryptedGroup to   = new DecryptedGroup.Builder().title("B").build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().newTitle(new DecryptedString.Builder().value_("B").build()).build(), decryptedGroupChange);
  }

  @Test
  public void description_change() {
    DecryptedGroup from = new DecryptedGroup.Builder().description("A").build();
    DecryptedGroup to   = new DecryptedGroup.Builder().description("B").build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().newDescription(new DecryptedString.Builder().value_("B").build()).build(), decryptedGroupChange);
  }

  @Test
  public void announcement_group_change() {
    DecryptedGroup from = new DecryptedGroup.Builder().isAnnouncementGroup(EnabledState.DISABLED).build();
    DecryptedGroup to   = new DecryptedGroup.Builder().isAnnouncementGroup(EnabledState.ENABLED).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().newIsAnnouncementGroup(EnabledState.ENABLED).build(), decryptedGroupChange);
  }

  @Test
  public void avatar_change() {
    DecryptedGroup from = new DecryptedGroup.Builder().avatar("A").build();
    DecryptedGroup to   = new DecryptedGroup.Builder().avatar("B").build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().newAvatar(new DecryptedString.Builder().value_("B").build()).build(), decryptedGroupChange);
  }

  @Test
  public void timer_change() {
    DecryptedGroup from = new DecryptedGroup.Builder().disappearingMessagesTimer(new DecryptedTimer.Builder().duration(100).build()).build();
    DecryptedGroup to   = new DecryptedGroup.Builder().disappearingMessagesTimer(new DecryptedTimer.Builder().duration(200).build()).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().newTimer(new DecryptedTimer.Builder().duration(200).build()).build(), decryptedGroupChange);
  }

  @Test
  public void access_control_change_attributes() {
    DecryptedGroup from = new DecryptedGroup.Builder().accessControl(new AccessControl.Builder().attributes(AccessControl.AccessRequired.MEMBER).build()).build();
    DecryptedGroup to   = new DecryptedGroup.Builder().accessControl(new AccessControl.Builder().attributes(AccessControl.AccessRequired.ADMINISTRATOR).build()).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().newAttributeAccess(AccessControl.AccessRequired.ADMINISTRATOR).build(), decryptedGroupChange);
  }

  @Test
  public void access_control_change_membership() {
    DecryptedGroup from = new DecryptedGroup.Builder().accessControl(new AccessControl.Builder().members(AccessControl.AccessRequired.ADMINISTRATOR).build()).build();
    DecryptedGroup to   = new DecryptedGroup.Builder().accessControl(new AccessControl.Builder().members(AccessControl.AccessRequired.MEMBER).build()).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().newMemberAccess(AccessControl.AccessRequired.MEMBER).build(), decryptedGroupChange);
  }

  @Test
  public void access_control_change_membership_and_attributes() {
    DecryptedGroup from = new DecryptedGroup.Builder().accessControl(new AccessControl.Builder().members(AccessControl.AccessRequired.MEMBER)
                                                                                                .attributes(AccessControl.AccessRequired.ADMINISTRATOR).build()).build();
    DecryptedGroup to = new DecryptedGroup.Builder().accessControl(new AccessControl.Builder().members(AccessControl.AccessRequired.ADMINISTRATOR)
                                                                                              .attributes(AccessControl.AccessRequired.MEMBER).build()).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().newMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR)
                                                   .newAttributeAccess(AccessControl.AccessRequired.MEMBER).build(), decryptedGroupChange);
  }

  @Test
  public void new_member() {
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = new DecryptedGroup.Builder().build();
    DecryptedGroup to      = new DecryptedGroup.Builder().members(List.of(member(uuidNew))).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().newMembers(List.of(member(uuidNew))).build(), decryptedGroupChange);
  }

  @Test
  public void removed_member() {
    UUID           uuidOld = UUID.randomUUID();
    DecryptedGroup from    = new DecryptedGroup.Builder().members(List.of(member(uuidOld))).build();
    DecryptedGroup to      = new DecryptedGroup.Builder().build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().deleteMembers(List.of(UuidUtil.toByteString(uuidOld))).build(), decryptedGroupChange);
  }

  @Test
  public void new_member_and_existing_member() {
    UUID           uuidOld = UUID.randomUUID();
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = new DecryptedGroup.Builder().members(List.of(member(uuidOld))).build();
    DecryptedGroup to      = new DecryptedGroup.Builder().members(List.of(member(uuidOld), member(uuidNew))).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().newMembers(List.of(member(uuidNew))).build(), decryptedGroupChange);
  }

  @Test
  public void removed_member_and_remaining_member() {
    UUID           uuidOld       = UUID.randomUUID();
    UUID           uuidRemaining = UUID.randomUUID();
    DecryptedGroup from          = new DecryptedGroup.Builder().members(List.of(member(uuidOld), member(uuidRemaining))).build();
    DecryptedGroup to            = new DecryptedGroup.Builder().members(List.of(member(uuidRemaining))).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().deleteMembers(List.of(UuidUtil.toByteString(uuidOld))).build(), decryptedGroupChange);
  }

  @Test
  public void new_member_by_invite() {
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = new DecryptedGroup.Builder().pendingMembers(List.of(pendingMember(uuidNew))).build();
    DecryptedGroup to      = new DecryptedGroup.Builder().members(List.of(member(uuidNew))).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().promotePendingMembers(List.of(member(uuidNew))).build(), decryptedGroupChange);
  }

  @Test
  public void uninvited_member_by_invite() {
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = new DecryptedGroup.Builder().pendingMembers(List.of(pendingMember(uuidNew))).build();
    DecryptedGroup to      = new DecryptedGroup.Builder().build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().deletePendingMembers(List.of(pendingMemberRemoval(uuidNew))).build(), decryptedGroupChange);
  }

  @Test
  public void new_invite() {
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = new DecryptedGroup.Builder().build();
    DecryptedGroup to      = new DecryptedGroup.Builder().pendingMembers(List.of(pendingMember(uuidNew))).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().newPendingMembers(List.of(pendingMember(uuidNew))).build(), decryptedGroupChange);
  }

  @Test
  public void to_admin() {
    UUID           uuid       = UUID.randomUUID();
    ProfileKey     profileKey = randomProfileKey();
    DecryptedGroup from       = new DecryptedGroup.Builder().members(List.of(withProfileKey(member(uuid), profileKey))).build();
    DecryptedGroup to         = new DecryptedGroup.Builder().members(List.of(withProfileKey(admin(uuid), profileKey))).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().modifyMemberRoles(List.of(promoteAdmin(uuid))).build(), decryptedGroupChange);
  }

  @Test
  public void to_member() {
    UUID           uuid       = UUID.randomUUID();
    ProfileKey     profileKey = randomProfileKey();
    DecryptedGroup from       = new DecryptedGroup.Builder().members(List.of(withProfileKey(admin(uuid), profileKey))).build();
    DecryptedGroup to         = new DecryptedGroup.Builder().members(List.of(withProfileKey(member(uuid), profileKey))).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().modifyMemberRoles(List.of(demoteAdmin(uuid))).build(), decryptedGroupChange);
  }

  @Test
  public void profile_key_change_member() {
    UUID           uuid        = UUID.randomUUID();
    ProfileKey     profileKey1 = randomProfileKey();
    ProfileKey     profileKey2 = randomProfileKey();
    DecryptedGroup from        = new DecryptedGroup.Builder().members(List.of(withProfileKey(admin(uuid), profileKey1))).build();
    DecryptedGroup to          = new DecryptedGroup.Builder().members(List.of(withProfileKey(admin(uuid), profileKey2))).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().modifiedProfileKeys(List.of(withProfileKey(admin(uuid), profileKey2))).build(), decryptedGroupChange);
  }

  @Test
  public void new_invite_access() {
    DecryptedGroup from = new DecryptedGroup.Builder()
        .accessControl(new AccessControl.Builder()
                           .addFromInviteLink(AccessControl.AccessRequired.ADMINISTRATOR)
                           .build())
        .build();
    DecryptedGroup to = new DecryptedGroup.Builder()
        .accessControl(new AccessControl.Builder()
                           .addFromInviteLink(AccessControl.AccessRequired.UNSATISFIABLE)
                           .build())
        .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder()
                     .newInviteLinkAccess(AccessControl.AccessRequired.UNSATISFIABLE)
                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void new_requesting_members() {
    UUID       member1     = UUID.randomUUID();
    ProfileKey profileKey1 = newProfileKey();
    DecryptedGroup from = new DecryptedGroup.Builder()
        .build();
    DecryptedGroup to = new DecryptedGroup.Builder()
        .requestingMembers(List.of(requestingMember(member1, profileKey1)))
        .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder()
                     .newRequestingMembers(List.of(requestingMember(member1, profileKey1)))
                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void new_requesting_members_ignores_existing_by_uuid() {
    UUID       member1     = UUID.randomUUID();
    UUID       member2     = UUID.randomUUID();
    ProfileKey profileKey2 = newProfileKey();

    DecryptedGroup from = new DecryptedGroup.Builder()
        .requestingMembers(List.of(requestingMember(member1, newProfileKey())))
        .build();

    DecryptedGroup to = new DecryptedGroup.Builder()
        .requestingMembers(List.of(requestingMember(member1, newProfileKey()), requestingMember(member2, profileKey2)))
        .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder()
                     .newRequestingMembers(List.of(requestingMember(member2, profileKey2)))
                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void removed_requesting_members() {
    UUID member1 = UUID.randomUUID();
    DecryptedGroup from = new DecryptedGroup.Builder()
        .requestingMembers(List.of(requestingMember(member1, newProfileKey())))
        .build();
    DecryptedGroup to = new DecryptedGroup.Builder()
        .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder()
                     .deleteRequestingMembers(List.of(UuidUtil.toByteString(member1)))
                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void promote_requesting_members() {
    UUID       member1     = UUID.randomUUID();
    ProfileKey profileKey1 = newProfileKey();
    UUID       member2     = UUID.randomUUID();
    ProfileKey profileKey2 = newProfileKey();
    DecryptedGroup from = new DecryptedGroup.Builder()
        .requestingMembers(List.of(requestingMember(member1, profileKey1)))
        .requestingMembers(List.of(requestingMember(member2, profileKey2)))
        .build();
    DecryptedGroup to = new DecryptedGroup.Builder()
        .members(List.of(member(member1, profileKey1)))
        .members(List.of(admin(member2, profileKey2)))
        .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder()
                     .promoteRequestingMembers(List.of(approveMember(member1)))
                     .promoteRequestingMembers(List.of(approveAdmin(member2)))
                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void new_invite_link_password() {
    ByteString password1 = ByteString.of(Util.getSecretBytes(16));
    ByteString password2 = ByteString.of(Util.getSecretBytes(16));
    DecryptedGroup from = new DecryptedGroup.Builder()
        .inviteLinkPassword(password1)
        .build();
    DecryptedGroup to = new DecryptedGroup.Builder()
        .inviteLinkPassword(password2)
        .build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder()
                     .newInviteLinkPassword(password2)
                     .build(),
                 decryptedGroupChange);
  }

  @Test
  public void new_banned_member() {
    UUID           uuidNew = UUID.randomUUID();
    DecryptedGroup from    = new DecryptedGroup.Builder().build();
    DecryptedGroup to      = new DecryptedGroup.Builder().bannedMembers(List.of(bannedMember(uuidNew))).build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().newBannedMembers(List.of(bannedMember(uuidNew))).build(), decryptedGroupChange);
  }

  @Test
  public void removed_banned_member() {
    UUID           uuidOld = UUID.randomUUID();
    DecryptedGroup from    = new DecryptedGroup.Builder().bannedMembers(List.of(bannedMember(uuidOld))).build();
    DecryptedGroup to      = new DecryptedGroup.Builder().build();

    DecryptedGroupChange decryptedGroupChange = GroupChangeReconstruct.reconstructGroupChange(from, to);

    assertEquals(new DecryptedGroupChange.Builder().deleteBannedMembers(List.of(bannedMember(uuidOld))).build(), decryptedGroupChange);
  }
}