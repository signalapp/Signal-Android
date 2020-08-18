package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Before;
import org.junit.Test;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Group;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.PendingMember;
import org.signal.storageservice.protos.groups.RequestingMember;
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
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.ClientZkGroupCipher;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.groups.UuidCiphertext;
import org.signal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCommitment;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.signal.zkgroup.profiles.ProfileKeyCredentialPresentation;
import org.signal.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.signal.zkgroup.profiles.ProfileKeyCredentialRequestContext;
import org.signal.zkgroup.profiles.ProfileKeyCredentialResponse;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.testutil.ZkGroupLibraryUtil;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber;

public final class GroupsV2Operations_decrypt_group_Test {

  private GroupSecretParams                  groupSecretParams;
  private GroupsV2Operations.GroupOperations groupOperations;

  @Before
  public void setup() throws InvalidInputException {
    ZkGroupLibraryUtil.assumeZkGroupSupportedOnOS();

    TestZkGroupServer  server             = new TestZkGroupServer();
    ClientZkOperations clientZkOperations = new ClientZkOperations(server.getServerPublicParams());

    groupSecretParams = GroupSecretParams.deriveFromMasterKey(new GroupMasterKey(Util.getSecretBytes(32)));
    groupOperations   = new GroupsV2Operations(clientZkOperations).forGroup(groupSecretParams);
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
                 10, maxFieldFound);
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
    UUID       admin1           = UUID.randomUUID();
    UUID       member1          = UUID.randomUUID();
    ProfileKey adminProfileKey  = newProfileKey();
    ProfileKey memberProfileKey = newProfileKey();

    Group group = Group.newBuilder()
                       .addMembers(Member.newBuilder()
                                         .setRole(Member.Role.ADMINISTRATOR)
                                         .setUserId(groupOperations.encryptUuid(admin1))
                                         .setJoinedAtRevision(4)
                                         .setProfileKey(encryptProfileKey(admin1, adminProfileKey)))
                       .addMembers(Member.newBuilder()
                                         .setRole(Member.Role.DEFAULT)
                                         .setUserId(groupOperations.encryptUuid(member1))
                                         .setJoinedAtRevision(7)
                                         .setProfileKey(encryptProfileKey(member1, memberProfileKey)))
                       .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(DecryptedGroup.newBuilder()
                               .addMembers(DecryptedMember.newBuilder()
                                                          .setJoinedAtRevision(4)
                                                          .setUuid(UuidUtil.toByteString(admin1))
                                                          .setRole(Member.Role.ADMINISTRATOR)
                                                          .setProfileKey(ByteString.copyFrom(adminProfileKey.serialize())))
                               .addMembers(DecryptedMember.newBuilder()
                                                          .setJoinedAtRevision(7)
                                                          .setRole(Member.Role.DEFAULT)
                                                          .setUuid(UuidUtil.toByteString(member1))
                                                          .setProfileKey(ByteString.copyFrom(memberProfileKey.serialize())))
                               .build().getMembersList(),
                 decryptedGroup.getMembersList());
  }

  @Test
  public void decrypt_pending_members_field_8() throws VerificationFailedException, InvalidGroupStateException {
    UUID admin1   = UUID.randomUUID();
    UUID member1  = UUID.randomUUID();
    UUID member2  = UUID.randomUUID();
    UUID inviter1 = UUID.randomUUID();
    UUID inviter2 = UUID.randomUUID();

    Group group = Group.newBuilder()
                       .addPendingMembers(PendingMember.newBuilder()
                                                       .setAddedByUserId(groupOperations.encryptUuid(inviter1))
                                                       .setTimestamp(100)
                                                       .setMember(Member.newBuilder()
                                                                        .setRole(Member.Role.ADMINISTRATOR)
                                                                        .setUserId(groupOperations.encryptUuid(admin1))))
                       .addPendingMembers(PendingMember.newBuilder()
                                                       .setAddedByUserId(groupOperations.encryptUuid(inviter1))
                                                       .setTimestamp(200)
                                                       .setMember(Member.newBuilder()
                                                                        .setRole(Member.Role.DEFAULT)
                                                                        .setUserId(groupOperations.encryptUuid(member1))))
                       .addPendingMembers(PendingMember.newBuilder()
                                                       .setAddedByUserId(groupOperations.encryptUuid(inviter2))
                                                       .setTimestamp(1500)
                                                       .setMember(Member.newBuilder()
                                                                        .setUserId(groupOperations.encryptUuid(member2))))
                       .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(DecryptedGroup.newBuilder()
                               .addPendingMembers(DecryptedPendingMember.newBuilder()
                                                                        .setUuid(UuidUtil.toByteString(admin1))
                                                                        .setUuidCipherText(groupOperations.encryptUuid(admin1))
                                                                        .setTimestamp(100)
                                                                        .setAddedByUuid(UuidUtil.toByteString(inviter1))
                                                                        .setRole(Member.Role.ADMINISTRATOR))
                               .addPendingMembers(DecryptedPendingMember.newBuilder()
                                                                        .setUuid(UuidUtil.toByteString(member1))
                                                                        .setUuidCipherText(groupOperations.encryptUuid(member1))
                                                                        .setTimestamp(200)
                                                                        .setAddedByUuid(UuidUtil.toByteString(inviter1))
                                                                        .setRole(Member.Role.DEFAULT))
                               .addPendingMembers(DecryptedPendingMember.newBuilder()
                                                                        .setUuid(UuidUtil.toByteString(member2))
                                                                        .setUuidCipherText(groupOperations.encryptUuid(member2))
                                                                        .setTimestamp(1500)
                                                                        .setAddedByUuid(UuidUtil.toByteString(inviter2))
                                                                        .setRole(Member.Role.DEFAULT))
                               .build().getPendingMembersList(),
                 decryptedGroup.getPendingMembersList());
  }

  @Test
  public void decrypt_requesting_members_field_9() throws VerificationFailedException, InvalidGroupStateException {
    UUID       admin1           = UUID.randomUUID();
    UUID       member1          = UUID.randomUUID();
    ProfileKey adminProfileKey  = newProfileKey();
    ProfileKey memberProfileKey = newProfileKey();

    Group group = Group.newBuilder()
                       .addRequestingMembers(RequestingMember.newBuilder()
                                                             .setUserId(groupOperations.encryptUuid(admin1))
                                                             .setProfileKey(encryptProfileKey(admin1, adminProfileKey))
                                                             .setTimestamp(5000))
                       .addRequestingMembers(RequestingMember.newBuilder()
                                                             .setUserId(groupOperations.encryptUuid(member1))
                                                             .setProfileKey(encryptProfileKey(member1, memberProfileKey))
                                                             .setTimestamp(15000))
                       .build();

    DecryptedGroup decryptedGroup = groupOperations.decryptGroup(group);

    assertEquals(DecryptedGroup.newBuilder()
                               .addRequestingMembers(DecryptedRequestingMember.newBuilder()
                                                                              .setUuid(UuidUtil.toByteString(admin1))
                                                                              .setProfileKey(ByteString.copyFrom(adminProfileKey.serialize()))
                                                                              .setTimestamp(5000))
                               .addRequestingMembers(DecryptedRequestingMember.newBuilder()
                                                                              .setUuid(UuidUtil.toByteString(member1))
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

  private ByteString encryptProfileKey(UUID uuid, ProfileKey profileKey) {
    return ByteString.copyFrom(new ClientZkGroupCipher(groupSecretParams).encryptProfileKey(profileKey, uuid).serialize());
  }

  private static ProfileKey newProfileKey() {
    try {
      return new ProfileKey(Util.getSecretBytes(32));
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }
}