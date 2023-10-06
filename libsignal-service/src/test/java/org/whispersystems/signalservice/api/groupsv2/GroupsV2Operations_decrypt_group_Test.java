package org.whispersystems.signalservice.api.groupsv2;

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

import java.util.List;
import java.util.UUID;

import okio.ByteString;

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
    Group group = new Group.Builder()
        .title(groupOperations.encryptTitle("Title!"))
        .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals("Title!", decryptedGroup.title);
  }

  @Test
  public void avatar_field_passed_through_3() throws VerificationFailedException, InvalidGroupStateException {
    Group group = new Group.Builder()
        .avatar("AvatarCdnKey")
        .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals("AvatarCdnKey", decryptedGroup.avatar);
  }

  @Test
  public void decrypt_message_timer_field_4() throws VerificationFailedException, InvalidGroupStateException {
    Group group = new Group.Builder()
        .disappearingMessagesTimer(groupOperations.encryptTimer(123))
        .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(123, decryptedGroup.disappearingMessagesTimer.duration);
  }

  @Test
  public void pass_through_access_control_field_5() throws VerificationFailedException, InvalidGroupStateException {
    AccessControl accessControl = new AccessControl.Builder()
        .members(AccessControl.AccessRequired.ADMINISTRATOR)
        .attributes(AccessControl.AccessRequired.MEMBER)
        .addFromInviteLink(AccessControl.AccessRequired.UNSATISFIABLE)
        .build();
    Group group = new Group.Builder()
        .accessControl(accessControl)
        .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(accessControl, decryptedGroup.accessControl);
  }

  @Test
  public void set_revision_field_6() throws VerificationFailedException, InvalidGroupStateException {
    Group group = new Group.Builder()
        .revision(99)
        .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(99, decryptedGroup.revision);
  }

  @Test
  public void decrypt_full_members_field_7() throws VerificationFailedException, InvalidGroupStateException {
    ACI        admin1           = ACI.from(UUID.randomUUID());
    ACI        member1          = ACI.from(UUID.randomUUID());
    ProfileKey adminProfileKey  = newProfileKey();
    ProfileKey memberProfileKey = newProfileKey();

    Group group = new Group.Builder()
        .members(List.of(new Member.Builder()
                             .role(Member.Role.ADMINISTRATOR)
                             .userId(groupOperations.encryptServiceId(admin1))
                             .joinedAtRevision(4)
                             .profileKey(encryptProfileKey(admin1, adminProfileKey))
                             .build(),
                         new Member.Builder()
                             .role(Member.Role.DEFAULT)
                             .userId(groupOperations.encryptServiceId(member1))
                             .joinedAtRevision(7)
                             .profileKey(encryptProfileKey(member1, memberProfileKey))
                             .build()))
        .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(new DecryptedGroup.Builder()
                     .members(List.of(new DecryptedMember.Builder()
                                          .joinedAtRevision(4)
                                          .aciBytes(admin1.toByteString())
                                          .role(Member.Role.ADMINISTRATOR)
                                          .profileKey(ByteString.of(adminProfileKey.serialize()))
                                          .build(),
                                      new DecryptedMember.Builder()
                                          .joinedAtRevision(7)
                                          .role(Member.Role.DEFAULT)
                                          .aciBytes(member1.toByteString())
                                          .profileKey(ByteString.of(memberProfileKey.serialize()))
                                          .build()))
                     .build().members,
                 decryptedGroup.members);
  }

  @Test
  public void decrypt_pending_members_field_8() throws VerificationFailedException, InvalidGroupStateException {
    ACI admin1   = ACI.from(UUID.randomUUID());
    ACI member1  = ACI.from(UUID.randomUUID());
    ACI member2  = ACI.from(UUID.randomUUID());
    ACI inviter1 = ACI.from(UUID.randomUUID());
    ACI inviter2 = ACI.from(UUID.randomUUID());

    Group group = new Group.Builder()
        .pendingMembers(List.of(new PendingMember.Builder()
                                    .addedByUserId(groupOperations.encryptServiceId(inviter1))
                                    .timestamp(100)
                                    .member(new Member.Builder()
                                                .role(Member.Role.ADMINISTRATOR)
                                                .userId(groupOperations.encryptServiceId(admin1))
                                                .build())
                                    .build(),
                                new PendingMember.Builder()
                                    .addedByUserId(groupOperations.encryptServiceId(inviter1))
                                    .timestamp(200)
                                    .member(new Member.Builder()
                                                .role(Member.Role.DEFAULT)
                                                .userId(groupOperations.encryptServiceId(member1))
                                                .build())
                                    .build(),
                                new PendingMember.Builder()
                                    .addedByUserId(groupOperations.encryptServiceId(inviter2))
                                    .timestamp(1500)
                                    .member(new Member.Builder()
                                                .userId(groupOperations.encryptServiceId(member2)).build())
                                    .build()))
        .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(new DecryptedGroup.Builder()
                     .pendingMembers(List.of(new DecryptedPendingMember.Builder()
                                                 .serviceIdBytes(admin1.toByteString())
                                                 .serviceIdCipherText(groupOperations.encryptServiceId(admin1))
                                                 .timestamp(100)
                                                 .addedByAci(inviter1.toByteString())
                                                 .role(Member.Role.ADMINISTRATOR)
                                                 .build(),
                                             new DecryptedPendingMember.Builder()
                                                 .serviceIdBytes(member1.toByteString())
                                                 .serviceIdCipherText(groupOperations.encryptServiceId(member1))
                                                 .timestamp(200)
                                                 .addedByAci(inviter1.toByteString())
                                                 .role(Member.Role.DEFAULT)
                                                 .build(),
                                             new DecryptedPendingMember.Builder()
                                                 .serviceIdBytes(member2.toByteString())
                                                 .serviceIdCipherText(groupOperations.encryptServiceId(member2))
                                                 .timestamp(1500)
                                                 .addedByAci(inviter2.toByteString())
                                                 .role(Member.Role.DEFAULT)
                                                 .build()))
                     .build()
                     .pendingMembers,
                 decryptedGroup.pendingMembers);
  }

  @Test
  public void decrypt_requesting_members_field_9() throws VerificationFailedException, InvalidGroupStateException {
    ACI        admin1           = ACI.from(UUID.randomUUID());
    ACI        member1          = ACI.from(UUID.randomUUID());
    ProfileKey adminProfileKey  = newProfileKey();
    ProfileKey memberProfileKey = newProfileKey();

    Group group = new Group.Builder()
        .requestingMembers(List.of(new RequestingMember.Builder()
                                       .userId(groupOperations.encryptServiceId(admin1))
                                       .profileKey(encryptProfileKey(admin1, adminProfileKey))
                                       .timestamp(5000)
                                       .build(),
                                   new RequestingMember.Builder()
                                       .userId(groupOperations.encryptServiceId(member1))
                                       .profileKey(encryptProfileKey(member1, memberProfileKey))
                                       .timestamp(15000)
                                       .build()))
        .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(new DecryptedGroup.Builder()
                     .requestingMembers(List.of(new DecryptedRequestingMember.Builder()
                                                    .aciBytes(admin1.toByteString())
                                                    .profileKey(ByteString.of(adminProfileKey.serialize()))
                                                    .timestamp(5000)
                                                    .build(),
                                                new DecryptedRequestingMember.Builder()
                                                    .aciBytes(member1.toByteString())
                                                    .profileKey(ByteString.of(memberProfileKey.serialize()))
                                                    .timestamp(15000)
                                                    .build()))
                     .build()
                     .requestingMembers,
                 decryptedGroup.requestingMembers);
  }

  @Test
  public void pass_through_group_link_password_field_10() throws VerificationFailedException, InvalidGroupStateException {
    ByteString password = ByteString.of(Util.getSecretBytes(16));
    Group group = new Group.Builder()
        .inviteLinkPassword(password)
        .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(password, decryptedGroup.inviteLinkPassword);
  }

  @Test
  public void decrypt_description_field_11() throws VerificationFailedException, InvalidGroupStateException {
    Group group = new Group.Builder()
        .description(groupOperations.encryptDescription("Description!"))
        .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals("Description!", decryptedGroup.description);
  }

  @Test
  public void decrypt_announcements_field_12() throws VerificationFailedException, InvalidGroupStateException {
    Group group = new Group.Builder()
        .announcementsOnly(true)
        .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(EnabledState.ENABLED, decryptedGroup.isAnnouncementGroup);
  }

  @Test
  public void decrypt_banned_members_field_13() throws VerificationFailedException, InvalidGroupStateException {
    ACI member1 = ACI.from(UUID.randomUUID());

    Group group = new Group.Builder()
        .bannedMembers(List.of(new BannedMember.Builder().userId(groupOperations.encryptServiceId(member1)).build()))
        .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(1, decryptedGroup.bannedMembers.size());
    assertEquals(new DecryptedBannedMember.Builder().serviceIdBytes(member1.toByteString()).build(), decryptedGroup.bannedMembers.get(0));
  }

  private ByteString encryptProfileKey(ACI aci, ProfileKey profileKey) {
    return ByteString.of(new ClientZkGroupCipher(groupSecretParams).encryptProfileKey(profileKey, aci.getLibSignalAci()).serialize());
  }

  private static ProfileKey newProfileKey() {
    try {
      return new ProfileKey(Util.getSecretBytes(32));
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }
}