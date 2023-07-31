package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Test;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.groups.ClientZkGroupCipher;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.BannedMember;
import org.signal.storageservice.protos.groups.Group;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.PendingMember;
import org.signal.storageservice.protos.groups.RequestingMember;
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.testutil.LibSignalLibraryUtil;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber;

public final class GroupsV2Operations_decrypt_group_Test {

  private GroupSecretParams                  groupSecretParams;
  private GroupsV2Operations.GroupOperations groupOperations;

  @Before
  public void setup() throws InvalidInputException {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS();

    TestZkGroupServer  server             = new TestZkGroupServer();
    ClientZkOperations clientZkOperations = new ClientZkOperations(server.getServerPublicParams());

    groupSecretParams = GroupSecretParams.deriveFromMasterKey(new GroupMasterKey(Util.getSecretBytes(32)));
    groupOperations   = new GroupsV2Operations(clientZkOperations, 1000).forGroup(groupSecretParams);
  }

    /**
   * Reflects over the generated protobuf class and ensures that no new fields have been added since we wrote this.
   * <p>
   * If we didn't, newly added fields would not be decrypted by {@link GroupsV2Operations.GroupOperations#decryptGroup}.
   */
  @Test
  public void ensure_GroupOperations_knows_about_all_fields_of_Group() {
    int maxFieldFound = getMaxDeclaredFieldNumber(Group.class);

    assertEquals("GroupOperations and its tests need updating to account for new fields on " + Group.class.getName(),
                 13, maxFieldFound);
  }
  
  @Test
  public void decrypt_title_field_2() throws VerificationFailedException, InvalidGroupStateException {
    Group group = Group.newBuilder()
                       .setTitle(groupOperations.encryptTitle("Title!"))
                       .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals("Title!", decryptedGroup.getTitle());
  }

  @Test
  public void avatar_field_passed_through_3() throws VerificationFailedException, InvalidGroupStateException {
    Group group = Group.newBuilder()
                       .setAvatar("AvatarCdnKey")
                       .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals("AvatarCdnKey", decryptedGroup.getAvatar());
  }

  @Test
  public void decrypt_message_timer_field_4() throws VerificationFailedException, InvalidGroupStateException {
    Group group = Group.newBuilder()
                       .setDisappearingMessagesTimer(groupOperations.encryptTimer(123))
                       .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(123, decryptedGroup.getDisappearingMessagesTimer().getDuration());
  }

  @Test
  public void pass_through_access_control_field_5() throws VerificationFailedException, InvalidGroupStateException {
    AccessControl accessControl = AccessControl.newBuilder()
                                               .setMembers(AccessControl.AccessRequired.ADMINISTRATOR)
                                               .setAttributes(AccessControl.AccessRequired.MEMBER)
                                               .setAddFromInviteLink(AccessControl.AccessRequired.UNSATISFIABLE)
                                               .build();
    Group group = Group.newBuilder()
                       .setAccessControl(accessControl)
                       .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(accessControl, decryptedGroup.getAccessControl());
  }

  @Test
  public void set_revision_field_6() throws VerificationFailedException, InvalidGroupStateException {
    Group group = Group.newBuilder()
                       .setRevision(99)
                       .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(99, decryptedGroup.getRevision());
  }

  @Test
  public void decrypt_full_members_field_7() throws VerificationFailedException, InvalidGroupStateException {
    ACI        admin1           = ACI.from(UUID.randomUUID());
    ACI        member1          = ACI.from(UUID.randomUUID());
    ProfileKey adminProfileKey  = newProfileKey();
    ProfileKey memberProfileKey = newProfileKey();

    Group group = Group.newBuilder()
                       .addMembers(Member.newBuilder()
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .setUserId(groupOperations.encryptServiceId(admin1))
                                         .setJoinedAtRevision(4)
                                         .setProfileKey(encryptProfileKey(admin1, adminProfileKey)))
                       .addMembers(Member.newBuilder()
                                         .setRole(Member.Role.DEFAULT)
                                         .setUserId(groupOperations.encryptServiceId(member1))
                                         .setJoinedAtRevision(7)
                                         .setProfileKey(encryptProfileKey(member1, memberProfileKey)))
                       .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(DecryptedGroup.newBuilder()
                               .addMembers(DecryptedMember.newBuilder()
                                                          .setJoinedAtRevision(4)
                                                          .setUuid(admin1.toByteString())
                                                          .setRole(Member.Role.ADMINISTRATOR)
                                                          .setProfileKey(ByteString.copyFrom(adminProfileKey.serialize())))
                               .addMembers(DecryptedMember.newBuilder()
                                                          .setJoinedAtRevision(7)
                                                          .setRole(Member.Role.DEFAULT)
                                                          .setUuid(member1.toByteString())
                                                          .setProfileKey(ByteString.copyFrom(memberProfileKey.serialize())))
                               .build().getMembersList(),
                 decryptedGroup.getMembersList());
  }

  @Test
  public void decrypt_pending_members_field_8() throws VerificationFailedException, InvalidGroupStateException {
    ACI admin1   = ACI.from(UUID.randomUUID());
    ACI member1  = ACI.from(UUID.randomUUID());
    ACI member2  = ACI.from(UUID.randomUUID());
    ACI inviter1 = ACI.from(UUID.randomUUID());
    ACI inviter2 = ACI.from(UUID.randomUUID());

    Group group = Group.newBuilder()
                       .addPendingMembers(PendingMember.newBuilder()
                                                       .setAddedByUserId(groupOperations.encryptServiceId(inviter1))
                                                       .setTimestamp(100)
                                                       .setMember(Member.newBuilder()
                                                                        .setRole(Member.Role.ADMINISTRATOR)
                                                                        .setUserId(groupOperations.encryptServiceId(admin1))))
                       .addPendingMembers(PendingMember.newBuilder()
                                                       .setAddedByUserId(groupOperations.encryptServiceId(inviter1))
                                                       .setTimestamp(200)
                                                       .setMember(Member.newBuilder()
                                                                        .setRole(Member.Role.DEFAULT)
                                                                        .setUserId(groupOperations.encryptServiceId(member1))))
                       .addPendingMembers(PendingMember.newBuilder()
                                                       .setAddedByUserId(groupOperations.encryptServiceId(inviter2))
                                                       .setTimestamp(1500)
                                                       .setMember(Member.newBuilder()
                                                                        .setUserId(groupOperations.encryptServiceId(member2))))
                       .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(DecryptedGroup.newBuilder()
                               .addPendingMembers(DecryptedPendingMember.newBuilder()
                                                                        .setServiceIdBinary(admin1.toByteString())
                                                                        .setUuidCipherText(groupOperations.encryptServiceId(admin1))
                                                                        .setTimestamp(100)
                                                                        .setAddedByUuid(inviter1.toByteString())
                                                                        .setRole(Member.Role.ADMINISTRATOR))
                               .addPendingMembers(DecryptedPendingMember.newBuilder()
                                                                        .setServiceIdBinary(member1.toByteString())
                                                                        .setUuidCipherText(groupOperations.encryptServiceId(member1))
                                                                        .setTimestamp(200)
                                                                        .setAddedByUuid(inviter1.toByteString())
                                                                        .setRole(Member.Role.DEFAULT))
                               .addPendingMembers(DecryptedPendingMember.newBuilder()
                                                                        .setServiceIdBinary(member2.toByteString())
                                                                        .setUuidCipherText(groupOperations.encryptServiceId(member2))
                                                                        .setTimestamp(1500)
                                                                        .setAddedByUuid(inviter2.toByteString())
                                                                        .setRole(Member.Role.DEFAULT))
                               .build().getPendingMembersList(),
                 decryptedGroup.getPendingMembersList());
  }

  @Test
  public void decrypt_requesting_members_field_9() throws VerificationFailedException, InvalidGroupStateException {
    ACI        admin1           = ACI.from(UUID.randomUUID());
    ACI        member1          = ACI.from(UUID.randomUUID());
    ProfileKey adminProfileKey  = newProfileKey();
    ProfileKey memberProfileKey = newProfileKey();

    Group group = Group.newBuilder()
                       .addRequestingMembers(RequestingMember.newBuilder()
                                                             .setUserId(groupOperations.encryptServiceId(admin1))
                                                             .setProfileKey(encryptProfileKey(admin1, adminProfileKey))
                                                             .setTimestamp(5000))
                       .addRequestingMembers(RequestingMember.newBuilder()
                                                             .setUserId(groupOperations.encryptServiceId(member1))
                                                             .setProfileKey(encryptProfileKey(member1, memberProfileKey))
                                                             .setTimestamp(15000))
                       .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(DecryptedGroup.newBuilder()
                               .addRequestingMembers(DecryptedRequestingMember.newBuilder()
                                                                              .setUuid(admin1.toByteString())
                                                                              .setProfileKey(ByteString.copyFrom(adminProfileKey.serialize()))
                                                                              .setTimestamp(5000))
                               .addRequestingMembers(DecryptedRequestingMember.newBuilder()
                                                                              .setUuid(member1.toByteString())
                                                                              .setProfileKey(ByteString.copyFrom(memberProfileKey.serialize()))
                                                                              .setTimestamp(15000))
                               .build().getRequestingMembersList(),
                 decryptedGroup.getRequestingMembersList());
  }

  @Test
  public void pass_through_group_link_password_field_10() throws VerificationFailedException, InvalidGroupStateException {
    ByteString password = ByteString.copyFrom(Util.getSecretBytes(16));
    Group      group    = Group.newBuilder()
                               .setInviteLinkPassword(password)
                               .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(password, decryptedGroup.getInviteLinkPassword());
  }

  @Test
  public void decrypt_description_field_11() throws VerificationFailedException, InvalidGroupStateException {
    Group group = Group.newBuilder()
                       .setDescription(groupOperations.encryptDescription("Description!"))
                       .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals("Description!", decryptedGroup.getDescription());
  }

  @Test
  public void decrypt_announcements_field_12() throws VerificationFailedException, InvalidGroupStateException {
    Group group = Group.newBuilder()
                       .setAnnouncementsOnly(true)
                       .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(EnabledState.ENABLED, decryptedGroup.getIsAnnouncementGroup());
  }

  @Test
  public void decrypt_banned_members_field_13() throws VerificationFailedException, InvalidGroupStateException {
    ACI member1 = ACI.from(UUID.randomUUID());

    Group group = Group.newBuilder()
                       .addBannedMembers(BannedMember.newBuilder().setUserId(groupOperations.encryptServiceId(member1)))
                       .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(1, decryptedGroup.getBannedMembersCount());
    assertEquals(DecryptedBannedMember.newBuilder().setServiceIdBinary(member1.toByteString()).build(), decryptedGroup.getBannedMembers(0));
  }

  private ByteString encryptProfileKey(ACI aci, ProfileKey profileKey) {
    return ByteString.copyFrom(new ClientZkGroupCipher(groupSecretParams).encryptProfileKey(profileKey, aci.getLibSignalAci()).serialize());
  }

  private static ProfileKey newProfileKey() {
    try {
      return new ProfileKey(Util.getSecretBytes(32));
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }
}