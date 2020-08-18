package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Before;
import org.junit.Test;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
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

public final class GroupsV2Operations_decrypt_change_Test {

  private GroupSecretParams                  groupSecretParams;
  private GroupsV2Operations.GroupOperations groupOperations;
  private ClientZkOperations                 clientZkOperations;
  private TestZkGroupServer                  server;

  @Before
  public void setup() throws InvalidInputException {
    ZkGroupLibraryUtil.assumeZkGroupSupportedOnOS();

    server             = new TestZkGroupServer();
    groupSecretParams  = GroupSecretParams.deriveFromMasterKey(new GroupMasterKey(Util.getSecretBytes(32)));
    clientZkOperations = new ClientZkOperations(server.getServerPublicParams());
    groupOperations    = new GroupsV2Operations(clientZkOperations).forGroup(groupSecretParams);
  }
  
  @Test
  public void cannot_decrypt_change_with_epoch_higher_than_known() throws InvalidProtocolBufferException, VerificationFailedException, InvalidGroupStateException {
    GroupChange change = GroupChange.newBuilder()
                                    .setChangeEpoch(GroupsV2Operations.HIGHEST_KNOWN_EPOCH + 1)
                                    .build();

    Optional<DecryptedGroupChange> decryptedGroupChangeOptional = groupOperations.decryptChange(change, false);

    assertFalse(decryptedGroupChangeOptional.isPresent());
  }

  @Test
  public void can_pass_revision_through_encrypt_and_decrypt_methods() {
    assertDecryption(GroupChange.Actions.newBuilder()
                                        .setRevision(1),
                     DecryptedGroupChange.newBuilder()
                                         .setRevision(1));
  }

  @Test
  public void can_decrypt_member_additions_field3() {
    UUID           self           = UUID.randomUUID();
    UUID           newMember      = UUID.randomUUID();
    ProfileKey     profileKey     = newProfileKey();
    GroupCandidate groupCandidate = groupCandidate(newMember, profileKey);

    assertDecryption(groupOperations.createModifyGroupMembershipChange(Collections.singleton(groupCandidate), self)
                                    .setRevision(10),
                     DecryptedGroupChange.newBuilder()
                                         .setRevision(10)
                                         .addNewMembers(DecryptedMember.newBuilder()
                                                                       .setRole(Member.Role.DEFAULT)
                                                                       .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                                                                       .setJoinedAtRevision(10)
                                                                       .setUuid(UuidUtil.toByteString(newMember))));
  }

  @Test
  public void can_decrypt_member_direct_join_field3() {
    UUID           newMember      = UUID.randomUUID();
    ProfileKey     profileKey     = newProfileKey();
    GroupCandidate groupCandidate = groupCandidate(newMember, profileKey);

    assertDecryption(groupOperations.createGroupJoinDirect(groupCandidate.getProfileKeyCredential().get())
                                    .setRevision(10),
                     DecryptedGroupChange.newBuilder()
                                         .setRevision(10)
                                         .addNewMembers(DecryptedMember.newBuilder()
                                                                       .setRole(Member.Role.DEFAULT)
                                                                       .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                                                                       .setJoinedAtRevision(10)
                                                                       .setUuid(UuidUtil.toByteString(newMember))));
  }

  @Test
  public void can_decrypt_member_additions_direct_to_admin_field3() {
    UUID           self           = UUID.randomUUID();
    UUID           newMember      = UUID.randomUUID();
    ProfileKey     profileKey     = newProfileKey();
    GroupCandidate groupCandidate = groupCandidate(newMember, profileKey);

    assertDecryption(groupOperations.createModifyGroupMembershipChange(Collections.singleton(groupCandidate), self)
                                    .setRevision(10),
                     DecryptedGroupChange.newBuilder()
                                         .setRevision(10)
                                         .addNewMembers(DecryptedMember.newBuilder()
                                                                       .setRole(Member.Role.DEFAULT)
                                                                       .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                                                                       .setJoinedAtRevision(10)
                                                                       .setUuid(UuidUtil.toByteString(newMember))));
  }

  @Test(expected = InvalidGroupStateException.class)
  public void cannot_decrypt_member_additions_with_bad_cipher_text_field3() throws InvalidProtocolBufferException, VerificationFailedException, InvalidGroupStateException {
    byte[]                      randomPresentation = Util.getSecretBytes(ProfileKeyCredentialPresentation.SIZE);
    GroupChange.Actions.Builder actions            = GroupChange.Actions.newBuilder();

    actions.addAddMembers(GroupChange.Actions.AddMemberAction.newBuilder()
                                                             .setAdded(Member.newBuilder().setRole(Member.Role.DEFAULT)
                                                             .setPresentation(ByteString.copyFrom(randomPresentation))));

    groupOperations.decryptChange(GroupChange.newBuilder().setActions(actions.build().toByteString()).build(), false);
  }

  @Test
  public void can_decrypt_member_removals_field4() {
    UUID oldMember = UUID.randomUUID();

    assertDecryption(groupOperations.createRemoveMembersChange(Collections.singleton(oldMember))
                                    .setRevision(10),
                     DecryptedGroupChange.newBuilder()
                                         .setRevision(10)
                                         .addDeleteMembers(UuidUtil.toByteString(oldMember)));
  }

  @Test(expected = InvalidGroupStateException.class)
  public void cannot_decrypt_member_removals_with_bad_cipher_text_field4() throws InvalidProtocolBufferException, VerificationFailedException, InvalidGroupStateException {
    byte[]                      randomPresentation = Util.getSecretBytes(UuidCiphertext.SIZE);
    GroupChange.Actions.Builder actions            = GroupChange.Actions.newBuilder();

    actions.addDeleteMembers(GroupChange.Actions.DeleteMemberAction.newBuilder()
                                                                   .setDeletedUserId(ByteString.copyFrom(randomPresentation)));

    groupOperations.decryptChange(GroupChange.newBuilder().setActions(actions.build().toByteString()).build(), false);
  }

  @Test
  public void can_decrypt_modify_member_action_role_to_admin_field5() {
    UUID member = UUID.randomUUID();

    assertDecryption(groupOperations.createChangeMemberRole(member, Member.Role.ADMINISTRATOR),
                     DecryptedGroupChange.newBuilder()
                                         .addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder()
                                                                                        .setUuid(UuidUtil.toByteString(member))
                                                                                        .setRole(Member.Role.ADMINISTRATOR)));
  }

  @Test
  public void can_decrypt_modify_member_action_role_to_member_field5() {
    UUID member = UUID.randomUUID();

    assertDecryption(groupOperations.createChangeMemberRole(member, Member.Role.DEFAULT),
                     DecryptedGroupChange.newBuilder()
                                         .addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder()
                                                                                        .setUuid(UuidUtil.toByteString(member))
                                                                                        .setRole(Member.Role.DEFAULT)));
  }

  @Test
  public void can_decrypt_modify_member_profile_key_action_field6() {
    UUID           self           = UUID.randomUUID();
    ProfileKey     profileKey     = newProfileKey();
    GroupCandidate groupCandidate = groupCandidate(self, profileKey);

    assertDecryption(groupOperations.createUpdateProfileKeyCredentialChange(groupCandidate.getProfileKeyCredential().get())
                                    .setRevision(10),
                     DecryptedGroupChange.newBuilder()
                                         .setRevision(10)
                                         .addModifiedProfileKeys(DecryptedMember.newBuilder()
                                                                                .setRole(Member.Role.UNKNOWN)
                                                                                .setJoinedAtRevision(-1)
                                                                                .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                                                                                .setUuid(UuidUtil.toByteString(self))));
  }

  @Test
  public void can_decrypt_member_invitations_field7() {
    UUID           self           = UUID.randomUUID();
    UUID           newMember      = UUID.randomUUID();
    GroupCandidate groupCandidate = groupCandidate(newMember);

    assertDecryption(groupOperations.createModifyGroupMembershipChange(Collections.singleton(groupCandidate), self)
                                    .setRevision(13),
                     DecryptedGroupChange.newBuilder()
                                         .setRevision(13)
                                         .addNewPendingMembers(DecryptedPendingMember.newBuilder()
                                                                                     .setAddedByUuid(UuidUtil.toByteString(self))
                                                                                     .setUuidCipherText(groupOperations.encryptUuid(newMember))
                                                                                     .setRole(Member.Role.DEFAULT)
                                                                                     .setUuid(UuidUtil.toByteString(newMember))));
  }
  
  @Test
  public void can_decrypt_pending_member_removals_field8() throws InvalidInputException {
    UUID           oldMember      = UUID.randomUUID();
    UuidCiphertext uuidCiphertext = new UuidCiphertext(groupOperations.encryptUuid(oldMember).toByteArray());

    assertDecryption(groupOperations.createRemoveInvitationChange(Collections.singleton(uuidCiphertext)),
                     DecryptedGroupChange.newBuilder()
                                         .addDeletePendingMembers(DecryptedPendingMemberRemoval.newBuilder()
                                                                                               .setUuid(UuidUtil.toByteString(oldMember))
                                                                                               .setUuidCipherText(ByteString.copyFrom(uuidCiphertext.serialize()))));
  }

  @Test
  public void can_decrypt_pending_member_removals_with_bad_cipher_text_field8() {
    byte[] uuidCiphertext = Util.getSecretBytes(60);

    assertDecryption(GroupChange.Actions
                                .newBuilder()
                                .addDeletePendingMembers(GroupChange.Actions.DeletePendingMemberAction.newBuilder()
                                                                    .setDeletedUserId(ByteString.copyFrom(uuidCiphertext))),
                     DecryptedGroupChange.newBuilder()
                                         .addDeletePendingMembers(DecryptedPendingMemberRemoval.newBuilder()
                                                                                               .setUuid(UuidUtil.toByteString(UuidUtil.UNKNOWN_UUID))
                                                                                               .setUuidCipherText(ByteString.copyFrom(uuidCiphertext))));
  }

  @Test
  public void can_decrypt_promote_pending_member_field9() {
    UUID           newMember      = UUID.randomUUID();
    ProfileKey     profileKey     = newProfileKey();
    GroupCandidate groupCandidate = groupCandidate(newMember, profileKey);

    assertDecryption(groupOperations.createAcceptInviteChange(groupCandidate.getProfileKeyCredential().get()),
                     DecryptedGroupChange.newBuilder()
                                         .addPromotePendingMembers(DecryptedMember.newBuilder()
                                                                                  .setUuid(UuidUtil.toByteString(newMember))
                                                                                  .setRole(Member.Role.DEFAULT)
                                                                                  .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                                                                                  .setJoinedAtRevision(-1)));
  }

  @Test
  public void can_decrypt_title_field_10() {
    assertDecryption(groupOperations.createModifyGroupTitle("New title"),
                     DecryptedGroupChange.newBuilder()
                                         .setNewTitle(DecryptedString.newBuilder().setValue("New title")));
  }

  @Test
  public void can_decrypt_avatar_key_field_11() {
    assertDecryption(GroupChange.Actions.newBuilder()
                                        .setModifyAvatar(GroupChange.Actions.ModifyAvatarAction.newBuilder().setAvatar("New avatar")),
                     DecryptedGroupChange.newBuilder()
                                         .setNewAvatar(DecryptedString.newBuilder().setValue("New avatar")));
  }

  @Test
  public void can_decrypt_timer_value_field_12() {
    assertDecryption(groupOperations.createModifyGroupTimerChange(100),
                     DecryptedGroupChange.newBuilder()
                                         .setNewTimer(DecryptedTimer.newBuilder().setDuration(100)));
  }

  @Test
  public void can_pass_through_new_attribute_access_rights_field_13() {
    assertDecryption(groupOperations.createChangeAttributesRights(AccessControl.AccessRequired.MEMBER),
                     DecryptedGroupChange.newBuilder()
                                         .setNewAttributeAccess(AccessControl.AccessRequired.MEMBER));
  }

  @Test
  public void can_pass_through_new_membership_rights_field_14() {
    assertDecryption(groupOperations.createChangeMembershipRights(AccessControl.AccessRequired.ADMINISTRATOR),
                     DecryptedGroupChange.newBuilder()
                                         .setNewMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR));
  }

  @Test
  public void can_pass_through_new_add_by_invite_link_rights_field_15() {
    assertDecryption(groupOperations.createChangeJoinByLinkRights(AccessControl.AccessRequired.ADMINISTRATOR),
                     DecryptedGroupChange.newBuilder()
                                         .setNewInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR));
  }

  @Test
  public void can_pass_through_new_add_by_invite_link_rights_field_15_unsatisfiable() {
    assertDecryption(groupOperations.createChangeJoinByLinkRights(AccessControl.AccessRequired.UNSATISFIABLE),
                     DecryptedGroupChange.newBuilder()
                                         .setNewInviteLinkAccess(AccessControl.AccessRequired.UNSATISFIABLE));
  }

  @Test
  public void can_decrypt_member_requests_field16() {
    UUID           newRequestingMember = UUID.randomUUID();
    ProfileKey     profileKey          = newProfileKey();
    GroupCandidate groupCandidate      = groupCandidate(newRequestingMember, profileKey);

    assertDecryption(groupOperations.createGroupJoinRequest(groupCandidate.getProfileKeyCredential().get())
                                    .setRevision(10),
                     DecryptedGroupChange.newBuilder()
                                         .setRevision(10)
                                         .addNewRequestingMembers(DecryptedRequestingMember.newBuilder()
                                                                                           .setUuid(UuidUtil.toByteString(newRequestingMember))
                                                                                           .setProfileKey(ByteString.copyFrom(profileKey.serialize()))));
  }

  @Test
  public void can_decrypt_member_requests_refusals_field17() {
    UUID newRequestingMember = UUID.randomUUID();

    assertDecryption(groupOperations.createRefuseGroupJoinRequest(Collections.singleton(newRequestingMember))
                                    .setRevision(10),
                     DecryptedGroupChange.newBuilder()
                                         .setRevision(10)
                                         .addDeleteRequestingMembers(UuidUtil.toByteString(newRequestingMember)));
  }

  @Test
  public void can_decrypt_promote_requesting_members_field18() {
    UUID newRequestingMember = UUID.randomUUID();

    assertDecryption(groupOperations.createApproveGroupJoinRequest(Collections.singleton(newRequestingMember))
                                    .setRevision(15),
                     DecryptedGroupChange.newBuilder()
                                         .setRevision(15)
                                         .addPromoteRequestingMembers(DecryptedApproveMember.newBuilder()
                                                                                            .setRole(Member.Role.DEFAULT)
                                                                                            .setUuid(UuidUtil.toByteString(newRequestingMember))));
  }

  @Test
  public void can_pass_through_new_invite_link_password_field19() {
    byte[] newPassword = Util.getSecretBytes(16);

    assertDecryption(GroupChange.Actions.newBuilder()
                                        .setModifyInviteLinkPassword(GroupChange.Actions.ModifyInviteLinkPasswordAction.newBuilder()
                                                                                        .setInviteLinkPassword(ByteString.copyFrom(newPassword))),
                      DecryptedGroupChange.newBuilder()
                                          .setNewInviteLinkPassword(ByteString.copyFrom(newPassword)));
  }

  private static ProfileKey newProfileKey() {
    try {
      return new ProfileKey(Util.getSecretBytes(32));
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }

  static GroupCandidate groupCandidate(UUID uuid) {
    return new GroupCandidate(uuid, Optional.absent());
  }

  GroupCandidate groupCandidate(UUID uuid, ProfileKey profileKey) {
    try {
      ClientZkProfileOperations          profileOperations            = clientZkOperations.getProfileOperations();
      ProfileKeyCommitment               commitment                   = profileKey.getCommitment(uuid);
      ProfileKeyCredentialRequestContext requestContext               = profileOperations.createProfileKeyCredentialRequestContext(uuid, profileKey);
      ProfileKeyCredentialRequest        request                      = requestContext.getRequest();
      ProfileKeyCredentialResponse       profileKeyCredentialResponse = server.getProfileKeyCredentialResponse(request, uuid, commitment);
      ProfileKeyCredential               profileKeyCredential         = profileOperations.receiveProfileKeyCredential(requestContext, profileKeyCredentialResponse);
      GroupCandidate                     groupCandidate               = new GroupCandidate(uuid, Optional.of(profileKeyCredential));

      ProfileKeyCredentialPresentation presentation = profileOperations.createProfileKeyCredentialPresentation(groupSecretParams, profileKeyCredential);
      server.assertProfileKeyCredentialPresentation(groupSecretParams.getPublicParams(), presentation);

      return groupCandidate;
    } catch (VerificationFailedException e) {
      throw new AssertionError(e);
    }
  }

  void assertDecryption(GroupChange.Actions.Builder inputChange,
                        DecryptedGroupChange.Builder expectedDecrypted)
  {
    UUID                editor  = UUID.randomUUID();
    GroupChange.Actions actions = inputChange.setSourceUuid(groupOperations.encryptUuid(editor))
                                             .build();

    GroupChange change = GroupChange.newBuilder()
                                    .setActions(actions.toByteString())
                                    .build();

    DecryptedGroupChange decryptedGroupChange = decrypt(change);

    assertEquals(expectedDecrypted.setEditor(UuidUtil.toByteString(editor))
                                  .build(),
                 decryptedGroupChange);
  }

  private DecryptedGroupChange decrypt(GroupChange build) {
    try {
      return groupOperations.decryptChange(build, false).get();
    } catch (InvalidProtocolBufferException | VerificationFailedException | InvalidGroupStateException e) {
     throw new AssertionError(e);
    }
  }

}