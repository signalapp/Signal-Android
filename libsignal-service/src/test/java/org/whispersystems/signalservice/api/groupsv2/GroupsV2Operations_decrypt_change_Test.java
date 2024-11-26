package org.whispersystems.signalservice.api.groupsv2;

import org.junit.Before;
import org.junit.Test;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.groups.ClientZkGroupCipher;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.libsignal.zkgroup.groups.UuidCiphertext;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredentialResponse;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCommitment;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialPresentation;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialRequestContext;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.testutil.LibSignalLibraryUtil;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import okio.ByteString;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.whispersystems.signalservice.api.groupsv2.ProtobufTestUtils.getMaxDeclaredFieldNumber;

public final class GroupsV2Operations_decrypt_change_Test {

  private GroupSecretParams                  groupSecretParams;
  private GroupsV2Operations.GroupOperations groupOperations;
  private ClientZkOperations                 clientZkOperations;
  private TestZkGroupServer                  server;

  @Before
  public void setup() throws InvalidInputException {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS();

    server             = new TestZkGroupServer();
    groupSecretParams  = GroupSecretParams.deriveFromMasterKey(new GroupMasterKey(Util.getSecretBytes(32)));
    clientZkOperations = new ClientZkOperations(server.getServerPublicParams());
    groupOperations    = new GroupsV2Operations(clientZkOperations, 1000).forGroup(groupSecretParams);
  }

  @Test
  public void ensure_GroupV2Operations_decryptChange_knows_about_all_fields_of_DecryptedGroupChange() {
    int maxFieldFound = getMaxDeclaredFieldNumber(DecryptedGroupChange.class);

    assertEquals("GroupV2Operations#decryptChange and its tests need updating to account for new fields on " + DecryptedGroupChange.class.getName(),
                 24,
                 maxFieldFound);
  }

  @Test
  public void cannot_decrypt_change_with_epoch_higher_than_known() throws IOException, VerificationFailedException, InvalidGroupStateException {
    GroupChange change = new GroupChange.Builder()
        .changeEpoch(GroupsV2Operations.HIGHEST_KNOWN_EPOCH + 1)
        .build();

    Optional<DecryptedGroupChange> decryptedGroupChangeOptional = groupOperations.decryptChange(change, DecryptChangeVerificationMode.alreadyTrusted());

    assertFalse(decryptedGroupChangeOptional.isPresent());
  }

  @Test
  public void can_pass_revision_through_encrypt_and_decrypt_methods() {
    assertDecryption(new GroupChange.Actions.Builder()
                         .revision(1),
                     new DecryptedGroupChange.Builder()
                         .revision(1));
  }

  @Test
  public void can_decrypt_member_additions_field3() {
    ACI            self           = ACI.from(UUID.randomUUID());
    ACI            newMember      = ACI.from(UUID.randomUUID());
    ProfileKey     profileKey     = newProfileKey();
    GroupCandidate groupCandidate = groupCandidate(newMember, profileKey);

    assertDecryption(groupOperations.createModifyGroupMembershipChange(Collections.singleton(groupCandidate), Collections.emptySet(), self)
                                    .revision(10),
                     new DecryptedGroupChange.Builder()
                         .revision(10)
                         .newMembers(List.of(new DecryptedMember.Builder()
                                                 .role(Member.Role.DEFAULT)
                                                 .profileKey(ByteString.of(profileKey.serialize()))
                                                 .joinedAtRevision(10)
                                                 .aciBytes(newMember.toByteString())
                                                 .build())));
  }

  @Test
  public void can_decrypt_member_direct_join_field3() {
    ACI            newMember      = ACI.from(UUID.randomUUID());
    ProfileKey     profileKey     = newProfileKey();
    GroupCandidate groupCandidate = groupCandidate(newMember, profileKey);

    assertDecryption(groupOperations.createGroupJoinDirect(groupCandidate.getExpiringProfileKeyCredential().get())
                                    .revision(10),
                     new DecryptedGroupChange.Builder()
                         .revision(10)
                         .newMembers(List.of(new DecryptedMember.Builder()
                                                 .role(Member.Role.DEFAULT)
                                                 .profileKey(ByteString.of(profileKey.serialize()))
                                                 .joinedAtRevision(10)
                                                 .aciBytes(newMember.toByteString())
                                                 .build())));
  }

  @Test
  public void can_decrypt_member_additions_direct_to_admin_field3() {
    ACI            self           = ACI.from(UUID.randomUUID());
    ACI            newMember      = ACI.from(UUID.randomUUID());
    ProfileKey     profileKey     = newProfileKey();
    GroupCandidate groupCandidate = groupCandidate(newMember, profileKey);

    assertDecryption(groupOperations.createModifyGroupMembershipChange(Collections.singleton(groupCandidate), Collections.emptySet(), self)
                                    .revision(10),
                     new DecryptedGroupChange.Builder()
                         .revision(10)
                         .newMembers(List.of(new DecryptedMember.Builder()
                                                 .role(Member.Role.DEFAULT)
                                                 .profileKey(ByteString.of(profileKey.serialize()))
                                                 .joinedAtRevision(10)
                                                 .aciBytes(newMember.toByteString())
                                                 .build())));
  }

  @Test(expected = InvalidGroupStateException.class)
  public void cannot_decrypt_member_additions_with_bad_cipher_text_field3() throws IOException, VerificationFailedException, InvalidGroupStateException {
    byte[]                      randomPresentation = Util.getSecretBytes(5);
    GroupChange.Actions.Builder actions            = new GroupChange.Actions.Builder();

    actions.addMembers(List.of(new GroupChange.Actions.AddMemberAction.Builder().added(new Member.Builder().role(Member.Role.DEFAULT)
                                                                                                           .presentation(ByteString.of(randomPresentation)).build()).build()));

    groupOperations.decryptChange(new GroupChange.Builder().actions(actions.build().encodeByteString()).build(), DecryptChangeVerificationMode.alreadyTrusted());
  }

  @Test
  public void can_decrypt_member_removals_field4() {
    ACI oldMember = ACI.from(UUID.randomUUID());

    assertDecryption(groupOperations.createRemoveMembersChange(Collections.singleton(oldMember), false, Collections.emptyList())
                                    .revision(10),
                     new DecryptedGroupChange.Builder()
                         .revision(10)
                         .deleteMembers(List.of(oldMember.toByteString())));
  }

  @Test(expected = InvalidGroupStateException.class)
  public void cannot_decrypt_member_removals_with_bad_cipher_text_field4() throws IOException, VerificationFailedException, InvalidGroupStateException {
    byte[]                      randomPresentation = Util.getSecretBytes(5);
    GroupChange.Actions.Builder actions            = new GroupChange.Actions.Builder();

    actions.deleteMembers(List.of(new GroupChange.Actions.DeleteMemberAction.Builder().deletedUserId(ByteString.of(randomPresentation)).build()));

    groupOperations.decryptChange(new GroupChange.Builder().actions(actions.build().encodeByteString()).build(), DecryptChangeVerificationMode.alreadyTrusted());
  }

  @Test
  public void can_decrypt_modify_member_action_role_to_admin_field5() {
    ACI member = ACI.from(UUID.randomUUID());

    assertDecryption(groupOperations.createChangeMemberRole(member, Member.Role.ADMINISTRATOR),
                     new DecryptedGroupChange.Builder()
                         .modifyMemberRoles(List.of(new DecryptedModifyMemberRole.Builder()
                                                        .aciBytes(member.toByteString())
                                                        .role(Member.Role.ADMINISTRATOR)
                                                        .build())));
  }

  @Test
  public void can_decrypt_modify_member_action_role_to_member_field5() {
    ACI member = ACI.from(UUID.randomUUID());

    assertDecryption(groupOperations.createChangeMemberRole(member, Member.Role.DEFAULT),
                     new DecryptedGroupChange.Builder()
                         .modifyMemberRoles(List.of(new DecryptedModifyMemberRole.Builder()
                                                        .aciBytes(member.toByteString())
                                                        .role(Member.Role.DEFAULT).build())));
  }

  @Test
  public void can_decrypt_modify_member_profile_key_action_field6() {
    ACI            self           = ACI.from(UUID.randomUUID());
    ProfileKey     profileKey     = newProfileKey();
    GroupCandidate groupCandidate = groupCandidate(self, profileKey);

    assertDecryption(groupOperations.createUpdateProfileKeyCredentialChange(groupCandidate.getExpiringProfileKeyCredential().get())
                                    .revision(10),
                     new DecryptedGroupChange.Builder()
                         .revision(10)
                         .modifiedProfileKeys(List.of(new DecryptedMember.Builder()
                                                          .role(Member.Role.UNKNOWN)
                                                          .joinedAtRevision(-1)
                                                          .profileKey(ByteString.of(profileKey.serialize()))
                                                          .aciBytes(self.toByteString())
                                                          .build())));
  }

  @Test
  public void can_decrypt_member_invitations_field7() {
    ACI            self           = ACI.from(UUID.randomUUID());
    ACI            newMember      = ACI.from(UUID.randomUUID());
    GroupCandidate groupCandidate = new GroupCandidate(newMember, Optional.empty());

    assertDecryption(groupOperations.createModifyGroupMembershipChange(Collections.singleton(groupCandidate), Collections.emptySet(), self)
                                    .revision(13),
                     new DecryptedGroupChange.Builder()
                         .revision(13)
                         .newPendingMembers(List.of(new DecryptedPendingMember.Builder()
                                                        .addedByAci(self.toByteString())
                                                        .serviceIdCipherText(groupOperations.encryptServiceId(newMember))
                                                        .role(Member.Role.DEFAULT)
                                                        .serviceIdBytes(newMember.toByteString())
                                                        .build())));
  }

  @Test
  public void can_decrypt_pending_member_removals_field8() throws InvalidInputException {
    ACI            oldMember      = ACI.from(UUID.randomUUID());
    UuidCiphertext uuidCiphertext = new UuidCiphertext(groupOperations.encryptServiceId(oldMember).toByteArray());

    assertDecryption(groupOperations.createRemoveInvitationChange(Collections.singleton(uuidCiphertext)),
                     new DecryptedGroupChange.Builder()
                         .deletePendingMembers(List.of(new DecryptedPendingMemberRemoval.Builder()
                                                           .serviceIdBytes(oldMember.toByteString())
                                                           .serviceIdCipherText(ByteString.of(uuidCiphertext.serialize()))
                                                           .build())));
  }

  @Test
  public void can_decrypt_pending_member_removals_with_bad_cipher_text_field8() {
    byte[] uuidCiphertext = Util.getSecretBytes(60);

    assertDecryption(new GroupChange.Actions.Builder()
                         .deletePendingMembers(List.of(new GroupChange.Actions.DeletePendingMemberAction.Builder()
                                                           .deletedUserId(ByteString.of(uuidCiphertext)).build())),
                     new DecryptedGroupChange.Builder()
                         .deletePendingMembers(List.of(new DecryptedPendingMemberRemoval.Builder()
                                                           .serviceIdBytes(UuidUtil.toByteString(UuidUtil.UNKNOWN_UUID))
                                                           .serviceIdCipherText(ByteString.of(uuidCiphertext))
                                                           .build())));
  }

  @Test
  public void can_decrypt_promote_pending_member_field9() {
    ACI            newMember      = ACI.from(UUID.randomUUID());
    ProfileKey     profileKey     = newProfileKey();
    GroupCandidate groupCandidate = groupCandidate(newMember, profileKey);

    assertDecryption(groupOperations.createAcceptInviteChange(groupCandidate.getExpiringProfileKeyCredential().get()),
                     new DecryptedGroupChange.Builder()
                         .promotePendingMembers(List.of(new DecryptedMember.Builder()
                                                            .aciBytes(newMember.toByteString())
                                                            .role(Member.Role.DEFAULT)
                                                            .profileKey(ByteString.of(profileKey.serialize()))
                                                            .joinedAtRevision(-1)
                                                            .build())));
  }

  @Test
  public void can_decrypt_title_field_10() {
    assertDecryption(groupOperations.createModifyGroupTitle("New title"),
                     new DecryptedGroupChange.Builder()
                         .newTitle(new DecryptedString.Builder().value_("New title").build()));
  }

  @Test
  public void can_decrypt_avatar_key_field_11() {
    assertDecryption(new GroupChange.Actions.Builder()
                         .modifyAvatar(new GroupChange.Actions.ModifyAvatarAction.Builder().avatar("New avatar").build()),
                     new DecryptedGroupChange.Builder()
                         .newAvatar(new DecryptedString.Builder().value_("New avatar").build()));
  }

  @Test
  public void can_decrypt_timer_value_field_12() {
    assertDecryption(groupOperations.createModifyGroupTimerChange(100),
                     new DecryptedGroupChange.Builder()
                         .newTimer(new DecryptedTimer.Builder().duration(100).build()));
  }

  @Test
  public void can_pass_through_new_attribute_access_rights_field_13() {
    assertDecryption(groupOperations.createChangeAttributesRights(AccessControl.AccessRequired.MEMBER),
                     new DecryptedGroupChange.Builder()
                         .newAttributeAccess(AccessControl.AccessRequired.MEMBER));
  }

  @Test
  public void can_pass_through_new_membership_rights_field_14() {
    assertDecryption(groupOperations.createChangeMembershipRights(AccessControl.AccessRequired.ADMINISTRATOR),
                     new DecryptedGroupChange.Builder()
                         .newMemberAccess(AccessControl.AccessRequired.ADMINISTRATOR));
  }

  @Test
  public void can_pass_through_new_add_by_invite_link_rights_field_15() {
    assertDecryption(groupOperations.createChangeJoinByLinkRights(AccessControl.AccessRequired.ADMINISTRATOR),
                     new DecryptedGroupChange.Builder()
                         .newInviteLinkAccess(AccessControl.AccessRequired.ADMINISTRATOR));
  }

  @Test
  public void can_pass_through_new_add_by_invite_link_rights_field_15_unsatisfiable() {
    assertDecryption(groupOperations.createChangeJoinByLinkRights(AccessControl.AccessRequired.UNSATISFIABLE),
                     new DecryptedGroupChange.Builder()
                         .newInviteLinkAccess(AccessControl.AccessRequired.UNSATISFIABLE));
  }

  @Test
  public void can_decrypt_member_requests_field16() {
    ACI            newRequestingMember = ACI.from(UUID.randomUUID());
    ProfileKey     profileKey          = newProfileKey();
    GroupCandidate groupCandidate      = groupCandidate(newRequestingMember, profileKey);

    assertDecryption(groupOperations.createGroupJoinRequest(groupCandidate.getExpiringProfileKeyCredential().get())
                                    .revision(10),
                     new DecryptedGroupChange.Builder()
                         .revision(10)
                         .newRequestingMembers(List.of(new DecryptedRequestingMember.Builder()
                                                           .aciBytes(newRequestingMember.toByteString())
                                                           .profileKey(ByteString.of(profileKey.serialize()))
                                                           .build())));
  }

  @Test
  public void can_decrypt_member_requests_refusals_field17() {
    ACI newRequestingMember = ACI.from(UUID.randomUUID());

    assertDecryption(groupOperations.createRefuseGroupJoinRequest(Collections.singleton(newRequestingMember), true, Collections.emptyList())
                                    .revision(10),
                     new DecryptedGroupChange.Builder()
                         .revision(10)
                         .deleteRequestingMembers(List.of(newRequestingMember.toByteString()))
                         .newBannedMembers(List.of(new DecryptedBannedMember.Builder().serviceIdBytes(newRequestingMember.toByteString()).build())));
  }

  @Test
  public void can_decrypt_promote_requesting_members_field18() {
    UUID newRequestingMember = UUID.randomUUID();

    assertDecryption(groupOperations.createApproveGroupJoinRequest(Collections.singleton(newRequestingMember))
                                    .revision(15),
                     new DecryptedGroupChange.Builder()
                         .revision(15)
                         .promoteRequestingMembers(List.of(new DecryptedApproveMember.Builder()
                                                               .role(Member.Role.DEFAULT)
                                                               .aciBytes(UuidUtil.toByteString(newRequestingMember))
                                                               .build())));
  }

  @Test
  public void can_pass_through_new_invite_link_password_field19() {
    byte[] newPassword = Util.getSecretBytes(16);

    assertDecryption(new GroupChange.Actions.Builder()
                         .modifyInviteLinkPassword(new GroupChange.Actions.ModifyInviteLinkPasswordAction.Builder()
                                                       .inviteLinkPassword(ByteString.of(newPassword))
                                                       .build()),
                     new DecryptedGroupChange.Builder()
                         .newInviteLinkPassword(ByteString.of(newPassword)));
  }

  @Test
  public void can_pass_through_new_description_field20() {
    assertDecryption(groupOperations.createModifyGroupDescription("New Description"),
                     new DecryptedGroupChange.Builder()
                         .newDescription(new DecryptedString.Builder().value_("New Description").build()));
  }

  @Test
  public void can_pass_through_new_announcment_only_field21() {
    assertDecryption(new GroupChange.Actions.Builder()
                         .modifyAnnouncementsOnly(new GroupChange.Actions.ModifyAnnouncementsOnlyAction.Builder()
                                                      .announcementsOnly(true)
                                                      .build()),
                     new DecryptedGroupChange.Builder()
                         .newIsAnnouncementGroup(EnabledState.ENABLED));
  }

  @Test
  public void can_decrypt_member_bans_field22() {
    ACI ban = ACI.from(UUID.randomUUID());

    assertDecryption(groupOperations.createBanServiceIdsChange(Collections.singleton(ban), false, Collections.emptyList())
                                    .revision(13),
                     new DecryptedGroupChange.Builder()
                         .revision(13)
                         .newBannedMembers(List.of(new DecryptedBannedMember.Builder()
                                                       .serviceIdBytes(ban.toByteString())
                                                       .build())));
  }

  @Test
  public void can_decrypt_banned_member_removals_field23() {
    ACI ban = ACI.from(UUID.randomUUID());

    assertDecryption(groupOperations.createUnbanServiceIdsChange(Collections.singleton(ban))
                                    .revision(13),
                     new DecryptedGroupChange.Builder()
                         .revision(13)
                         .deleteBannedMembers(List.of(new DecryptedBannedMember.Builder()
                                                          .serviceIdBytes(ban.toByteString()).build())));
  }

  @Test
  public void can_decrypt_promote_pending_pni_aci_member_field24() {
    ACI        memberAci  = ACI.from(UUID.randomUUID());
    PNI        memberPni  = PNI.from(UUID.randomUUID());
    ProfileKey profileKey = newProfileKey();

    GroupChange.Actions.Builder builder = new GroupChange.Actions.Builder()
        .sourceServiceId(groupOperations.encryptServiceId(memberPni))
        .revision(5)
        .promotePendingPniAciMembers(List.of(new GroupChange.Actions.PromotePendingPniAciMemberProfileKeyAction.Builder()
                                                 .userId(groupOperations.encryptServiceId(memberAci))
                                                 .pni(groupOperations.encryptServiceId(memberPni))
                                                 .profileKey(encryptProfileKey(memberAci, profileKey))
                                                 .build()));

    assertDecryptionWithEditorSet(builder,
                                  new DecryptedGroupChange.Builder()
                                      .editorServiceIdBytes(memberAci.toByteString())
                                      .revision(5)
                                      .promotePendingPniAciMembers(List.of(new DecryptedMember.Builder()
                                                                               .aciBytes(memberAci.toByteString())
                                                                               .pniBytes(memberPni.toByteString())
                                                                               .role(Member.Role.DEFAULT)
                                                                               .profileKey(ByteString.of(profileKey.serialize()))
                                                                               .joinedAtRevision(5)
                                                                               .build())));
  }

  private static ProfileKey newProfileKey() {
    try {
      return new ProfileKey(Util.getSecretBytes(32));
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }

  private ByteString encryptProfileKey(ACI aci, ProfileKey profileKey) {
    return ByteString.of(new ClientZkGroupCipher(groupSecretParams).encryptProfileKey(profileKey, aci.getLibSignalAci()).serialize());
  }

  static GroupCandidate groupCandidate(UUID uuid) {
    return new GroupCandidate(ACI.from(uuid), Optional.empty());
  }

  GroupCandidate groupCandidate(ACI aci, ProfileKey profileKey) {
    try {
      ClientZkProfileOperations            profileOperations                    = clientZkOperations.getProfileOperations();
      ProfileKeyCommitment                 commitment                           = profileKey.getCommitment(aci.getLibSignalAci());
      ProfileKeyCredentialRequestContext   requestContext                       = profileOperations.createProfileKeyCredentialRequestContext(aci.getLibSignalAci(), profileKey);
      ProfileKeyCredentialRequest          request                              = requestContext.getRequest();
      ExpiringProfileKeyCredentialResponse expiringProfileKeyCredentialResponse = server.getExpiringProfileKeyCredentialResponse(request, aci, commitment, Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS));
      ExpiringProfileKeyCredential         profileKeyCredential                 = profileOperations.receiveExpiringProfileKeyCredential(requestContext, expiringProfileKeyCredentialResponse);
      GroupCandidate                       groupCandidate                       = new GroupCandidate(aci, Optional.of(profileKeyCredential));

      ProfileKeyCredentialPresentation presentation = profileOperations.createProfileKeyCredentialPresentation(groupSecretParams, profileKeyCredential);
      server.assertProfileKeyCredentialPresentation(groupSecretParams.getPublicParams(), presentation, Instant.now());

      return groupCandidate;
    } catch (VerificationFailedException e) {
      throw new AssertionError(e);
    }
  }

  void assertDecryption(GroupChange.Actions.Builder inputChange,
                        DecryptedGroupChange.Builder expectedDecrypted)
  {
    ACI editor = ACI.from(UUID.randomUUID());
    assertDecryptionWithEditorSet(inputChange.sourceServiceId(groupOperations.encryptServiceId(editor)), expectedDecrypted.editorServiceIdBytes(editor.toByteString()));
  }

  void assertDecryptionWithEditorSet(GroupChange.Actions.Builder inputChange,
                                     DecryptedGroupChange.Builder expectedDecrypted)
  {
    GroupChange.Actions actions = inputChange.build();

    GroupChange change = new GroupChange.Builder()
        .actions(actions.encodeByteString())
        .build();

    DecryptedGroupChange decryptedGroupChange = decrypt(change);

    assertEquals(expectedDecrypted.build(),
                 decryptedGroupChange);
  }

  private DecryptedGroupChange decrypt(GroupChange build) {
    try {
      return groupOperations.decryptChange(build, DecryptChangeVerificationMode.alreadyTrusted()).get();
    } catch (IOException | VerificationFailedException | InvalidGroupStateException e) {
      throw new AssertionError(e);
    }
  }

}