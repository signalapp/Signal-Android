package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.DisappearingMessagesTimer;
import org.signal.storageservice.protos.groups.Group;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.PendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.NotarySignature;
import org.signal.zkgroup.ServerPublicParams;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.auth.ClientZkAuthOperations;
import org.signal.zkgroup.groups.ClientZkGroupCipher;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.groups.ProfileKeyCiphertext;
import org.signal.zkgroup.groups.UuidCiphertext;
import org.signal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.signal.zkgroup.profiles.ProfileKeyCredentialPresentation;
import org.signal.zkgroup.util.UUIDUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Contains operations to create, modify and validate groups and group changes.
 */
public final class GroupsV2Operations {

  /** Used for undecryptable pending invites */
  public static final UUID UNKNOWN_UUID = new UUID(0, 0);

  private final ServerPublicParams        serverPublicParams;
  private final ClientZkProfileOperations clientZkProfileOperations;
  private final ClientZkAuthOperations    clientZkAuthOperations;
  private final SecureRandom              random;

  public GroupsV2Operations(ClientZkOperations clientZkOperations) {
    this.serverPublicParams        = clientZkOperations.getServerPublicParams();
    this.clientZkProfileOperations = clientZkOperations.getProfileOperations();
    this.clientZkAuthOperations    = clientZkOperations.getAuthOperations();
    this.random                    = new SecureRandom();
  }

  /**
   * Creates a new group with the title and avatar.
   *
   * @param self    You will be member 0 and the only admin.
   * @param members Members must not contain self. Members will be non-admin members of the group.
   */
  public NewGroup createNewGroup(final String title,
                                 final Optional<byte[]> avatar,
                                 final GroupCandidate self,
                                 final Set<GroupCandidate> members) {

    if (members.contains(self)) {
      throw new IllegalArgumentException("Members must not contain self");
    }

    final GroupSecretParams groupSecretParams = GroupSecretParams.generate(random);
    final GroupOperations   groupOperations   = forGroup(groupSecretParams);

    Group.Builder group = Group.newBuilder()
                               .setVersion(0)
                               .setPublicKey(ByteString.copyFrom(groupSecretParams.getPublicParams().serialize()))
                               .setTitle(groupOperations.encryptTitle(title))
                               .setDisappearingMessagesTimer(groupOperations.encryptTimer(0))
                               .setAccessControl(AccessControl.newBuilder()
                                                              .setAttributes(AccessControl.AccessRequired.MEMBER)
                                                              .setMembers(AccessControl.AccessRequired.MEMBER));

    group.addMembers(groupOperations.member(self.getProfileKeyCredential().get(), Member.Role.ADMINISTRATOR));

    for (GroupCandidate credential : members) {
      Member.Role          newMemberRole        = Member.Role.DEFAULT;
      ProfileKeyCredential profileKeyCredential = credential.getProfileKeyCredential().orNull();

      if (profileKeyCredential != null) {
        group.addMembers(groupOperations.member(profileKeyCredential, newMemberRole));
      } else {
        group.addPendingMembers(groupOperations.invitee(credential.getUuid(), newMemberRole));
      }
    }

    return new NewGroup(groupSecretParams, group.build(), avatar);
  }

  public GroupOperations forGroup(final GroupSecretParams groupSecretParams) {
    return new GroupOperations(groupSecretParams);
  }

  public ClientZkProfileOperations getProfileOperations() {
    return clientZkProfileOperations;
  }

  public ClientZkAuthOperations getAuthOperations() {
    return clientZkAuthOperations;
  }

  /**
   * Operations on a single group.
   */
  public final class GroupOperations {

    private final GroupSecretParams   groupSecretParams;
    private final ClientZkGroupCipher clientZkGroupCipher;

    private GroupOperations(GroupSecretParams groupSecretParams) {
      this.groupSecretParams   = groupSecretParams;
      this.clientZkGroupCipher = new ClientZkGroupCipher(groupSecretParams);
    }

    public GroupChange.Actions.Builder createModifyGroupTitleAndMembershipChange(final Optional<String> title,
                                                                                 final Set<GroupCandidate> membersToAdd,
                                                                                 final Set<UUID> membersToRemove)
    {
      if (!Collections.disjoint(GroupCandidate.toUuidList(membersToAdd), membersToRemove)) {
        throw new IllegalArgumentException("Overlap between add and remove sets");
      }

      final GroupOperations groupOperations = forGroup(groupSecretParams);

      GroupChange.Actions.Builder actions = GroupChange.Actions.newBuilder();

      if (title.isPresent()) {
        actions.setModifyTitle(GroupChange.Actions.ModifyTitleAction.newBuilder()
                                                                    .setTitle(encryptTitle(title.get())));
      }

      for (GroupCandidate credential : membersToAdd) {
        Member.Role          newMemberRole        = Member.Role.DEFAULT;
        ProfileKeyCredential profileKeyCredential = credential.getProfileKeyCredential().orNull();

        if (profileKeyCredential != null) {
          actions.addAddMembers(GroupChange.Actions.AddMemberAction.newBuilder()
                                                                   .setAdded(groupOperations.member(profileKeyCredential, newMemberRole)));
        } else {
          actions.addAddPendingMembers(GroupChange.Actions.AddPendingMemberAction.newBuilder()
                                                                                 .setAdded(groupOperations.invitee(credential.getUuid(), newMemberRole)));
        }
      }

      for (UUID remove: membersToRemove) {
        actions.addDeleteMembers(GroupChange.Actions.DeleteMemberAction.newBuilder()
                                                                       .setDeletedUserId(encryptUuid(remove)));
      }

      return actions;
    }

    public GroupChange.Actions.Builder createModifyGroupTimerChange(int timerDurationSeconds) {
      return GroupChange.Actions
                        .newBuilder()
                        .setModifyDisappearingMessagesTimer(GroupChange.Actions.ModifyDisappearingMessagesTimerAction.newBuilder()
                                                                               .setTimer(encryptTimer(timerDurationSeconds)));
    }

    public GroupChange.Actions.Builder createUpdateProfileKeyCredentialChange(ProfileKeyCredential profileKeyCredential) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(random, groupSecretParams, profileKeyCredential);

      return GroupChange.Actions
                        .newBuilder()
                        .addModifyMemberProfileKeys(GroupChange.Actions.ModifyMemberProfileKeyAction.newBuilder()
                                                                       .setPresentation(ByteString.copyFrom(presentation.serialize())));
    }

    public GroupChange.Actions.Builder createAcceptInviteChange(ProfileKeyCredential profileKeyCredential) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(random, groupSecretParams, profileKeyCredential);

      return GroupChange.Actions
                        .newBuilder()
                        .addPromotePendingMembers(GroupChange.Actions.PromotePendingMemberAction.newBuilder()
                                                                                                .setPresentation(ByteString.copyFrom(presentation.serialize())));
    }

    public GroupChange.Actions.Builder createRemoveInvitationChange(final Set<UuidCiphertext> uuidCipherTextsFromInvitesToRemove) {
      GroupChange.Actions.Builder builder = GroupChange.Actions
                                                       .newBuilder();

      for (UuidCiphertext uuidCipherText: uuidCipherTextsFromInvitesToRemove) {
        builder.addDeletePendingMembers(GroupChange.Actions.DeletePendingMemberAction.newBuilder()
                                                                                     .setDeletedUserId(ByteString.copyFrom(uuidCipherText.serialize())));
      }

      return builder;
    }

    private Member.Builder member(ProfileKeyCredential credential, Member.Role role) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(new SecureRandom(), groupSecretParams, credential);

      return Member.newBuilder().setRole(role)
                                .setPresentation(ByteString.copyFrom(presentation.serialize()));
    }

    public PendingMember.Builder invitee(UUID uuid, Member.Role role) {
      UuidCiphertext uuidCiphertext = clientZkGroupCipher.encryptUuid(uuid);

      Member member = Member.newBuilder().setRole(role)
                                         .setUserId(ByteString.copyFrom(uuidCiphertext.serialize()))
                                         .build();

      return PendingMember.newBuilder().setMember(member);
    }

    public DecryptedGroup decryptGroup(Group group)
      throws VerificationFailedException, InvalidGroupStateException, InvalidProtocolBufferException
    {
      List<Member>                 membersList             = group.getMembersList();
      List<PendingMember>          pendingMembersList      = group.getPendingMembersList();
      List<DecryptedMember>        decryptedMembers        = new ArrayList<>(membersList.size());
      List<DecryptedPendingMember> decryptedPendingMembers = new ArrayList<>(pendingMembersList.size());

      for (Member member : membersList) {
        decryptedMembers.add(decryptMember(member));
      }

      for (PendingMember member : pendingMembersList) {
        decryptedPendingMembers.add(decryptMember(member));
      }

      DecryptedGroup.Builder builder = DecryptedGroup.newBuilder()
                                                     .setTitle(decryptTitle(group.getTitle()))
                                                     .setAvatar(group.getAvatar())
                                                     .setAccessControl(group.getAccessControl())
                                                     .setVersion(group.getVersion())
                                                     .addAllMembers(decryptedMembers)
                                                     .addAllPendingMembers(decryptedPendingMembers);

      DisappearingMessagesTimer messagesTimer = decryptDisappearingMessagesTimer(group.getDisappearingMessagesTimer());
      if (messagesTimer != null) {
        builder.setDisappearingMessagesTimer(messagesTimer);
      }

      return builder.build();
    }

    /**
     * @param verify You might want to avoid verification if you already know it's correct, or you
     *               are not going to pass to other clients.
     *               <p>
     *               Also, if you know it's version 0, do not verify because changes for version 0
     *               are not signed, but should be empty.
     */
    public DecryptedGroupChange decryptChange(GroupChange groupChange, boolean verify)
      throws InvalidProtocolBufferException, VerificationFailedException, InvalidGroupStateException
    {
      GroupChange.Actions actions = verify ? getVerifiedActions(groupChange) : getActions(groupChange);

      return decryptChange(actions);
    }

    public DecryptedGroupChange decryptChange(GroupChange.Actions actions)
      throws InvalidProtocolBufferException, VerificationFailedException, InvalidGroupStateException
    {
      DecryptedGroupChange.Builder builder = DecryptedGroupChange.newBuilder();

      // Field 1
      builder.setEditor(decryptUuidToByteString(actions.getSourceUuid()));

      // Field 2
      builder.setVersion(actions.getVersion());

      // Field 3
      for (GroupChange.Actions.AddMemberAction addMemberAction : actions.getAddMembersList()) {
        UUID uuid = decryptUuid(addMemberAction.getAdded().getUserId());
        builder.addNewMembers(DecryptedMember.newBuilder()
                                             .setUuid(ByteString.copyFrom(UUIDUtil.serialize(uuid)))
                                             .setProfileKey(decryptProfileKeyToByteString(addMemberAction.getAdded().getProfileKey(), uuid)));
      }

      // Field 4
      for (GroupChange.Actions.DeleteMemberAction deleteMemberAction : actions.getDeleteMembersList()) {
        builder.addDeleteMembers(decryptUuidToByteString(deleteMemberAction.getDeletedUserId()));
      }

      // Field 5
      for (GroupChange.Actions.ModifyMemberRoleAction modifyMemberRoleAction : actions.getModifyMemberRolesList()) {
        builder.addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder()
               .setRole(modifyMemberRoleAction.getRole())
               .setUuid(decryptUuidToByteString(modifyMemberRoleAction.getUserId())));
      }

      // Field 6
      for (GroupChange.Actions.ModifyMemberProfileKeyAction modifyMemberProfileKeyAction : actions.getModifyMemberProfileKeysList()) {
        try {
          ProfileKeyCredentialPresentation presentation = new ProfileKeyCredentialPresentation(modifyMemberProfileKeyAction.getPresentation().toByteArray());
          presentation.getProfileKeyCiphertext();

          UUID uuid = decryptUuid(ByteString.copyFrom(presentation.getUuidCiphertext().serialize()));
          builder.addModifiedProfileKeys(DecryptedMember.newBuilder()
                                                        .setRole(Member.Role.UNKNOWN)
                                                        .setJoinedAtVersion(-1)
                                                        .setUuid(ByteString.copyFrom(UUIDUtil.serialize(uuid)))
                                                        .setProfileKey(ByteString.copyFrom(decryptProfileKey(ByteString.copyFrom(presentation.getProfileKeyCiphertext().serialize()), uuid).serialize())));
        } catch (InvalidInputException e) {
          throw new InvalidGroupStateException(e);
        }
      }

      // Field 7
      for (GroupChange.Actions.AddPendingMemberAction addPendingMemberAction:actions.getAddPendingMembersList()) {
        PendingMember added          = addPendingMemberAction.getAdded();
        Member        member         = added.getMember();
        ByteString    uuidCipherText = member.getUserId();
        UUID          uuid           = decryptUuidOrUnknown(uuidCipherText);

        builder.addNewPendingMembers(DecryptedPendingMember.newBuilder()
                                                           .setUuid(ByteString.copyFrom(UUIDUtil.serialize(uuid)))
                                                           .setUuidCipherText(uuidCipherText)
                                                           .setRole(member.getRole())
                                                           .setAddedByUuid(decryptUuidToByteString(added.getAddedByUserId()))
                                                           .setTimestamp(added.getTimestamp()));
      }

      // Field 8
      for (GroupChange.Actions.DeletePendingMemberAction deletePendingMemberAction : actions.getDeletePendingMembersList()) {
        ByteString uuidCipherText = deletePendingMemberAction.getDeletedUserId();
        UUID       uuid           = decryptUuidOrUnknown(uuidCipherText);

        builder.addDeletePendingMembers(DecryptedPendingMemberRemoval.newBuilder()
                                                                     .setUuid(ByteString.copyFrom(UUIDUtil.serialize(uuid)))
                                                                     .setUuidCipherText(uuidCipherText));
      }

      // Field 9
      for (GroupChange.Actions.PromotePendingMemberAction promotePendingMemberAction : actions.getPromotePendingMembersList()) {
        ProfileKeyCredentialPresentation profileKeyCredentialPresentation;
        try {
          profileKeyCredentialPresentation = new ProfileKeyCredentialPresentation(promotePendingMemberAction.getPresentation().toByteArray());
        } catch (InvalidInputException e) {
          throw new InvalidGroupStateException(e);
        }
        UUID       uuid  = clientZkGroupCipher.decryptUuid(profileKeyCredentialPresentation.getUuidCiphertext());
        builder.addPromotePendingMembers(UuidUtil.toByteString(uuid));
      }

      // Field 10
      if (actions.hasModifyTitle()) {
        builder.setNewTitle(DecryptedString.newBuilder().setValue(decryptTitle(actions.getModifyTitle().getTitle())));
      }

      // Field 11
      if (actions.hasModifyAvatar()) {
        builder.setNewAvatar(DecryptedString.newBuilder().setValue(actions.getModifyAvatar().getAvatar()));
      }

      // Field 12
      if (actions.hasModifyDisappearingMessagesTimer()) {
        int duration = decryptDisappearingMessagesTimer(actions.getModifyDisappearingMessagesTimer().getTimer()).getDuration();
        builder.setNewTimer(DisappearingMessagesTimer.newBuilder().setDuration(duration));
      }

      // Field 13
      if (actions.hasModifyAttributesAccess()) {
        builder.setNewAttributeAccess(actions.getModifyAttributesAccess().getAttributesAccess());
      }

      // Field 14
      if (actions.hasModifyMemberAccess()) {
        builder.setNewMemberAccess(actions.getModifyMemberAccess().getMembersAccess());
      }

      return builder.build();
    }

    private DecryptedMember decryptMember(Member member)
      throws InvalidGroupStateException, VerificationFailedException
    {
      ByteString userId = member.getUserId();
      UUID       uuid   = decryptUuid(userId);

      return DecryptedMember.newBuilder()
                            .setUuid(ByteString.copyFrom(UUIDUtil.serialize(uuid)))
                            .setProfileKey(decryptProfileKeyToByteString(member.getProfileKey(), uuid))
                            .setRole(member.getRole())
                            .build();
    }

    private DecryptedPendingMember decryptMember(PendingMember member)
      throws InvalidGroupStateException, VerificationFailedException
    {
      ByteString userIdCipherText = member.getMember().getUserId();
      UUID       uuid             = decryptUuidOrUnknown(userIdCipherText);
      UUID       addedBy          = decryptUuid(member.getAddedByUserId());

      return DecryptedPendingMember.newBuilder()
                                   .setUuid(ByteString.copyFrom(UUIDUtil.serialize(uuid)))
                                   .setUuidCipherText(userIdCipherText)
                                   .setAddedByUuid(ByteString.copyFrom(UUIDUtil.serialize(addedBy)))
                                   .setRole(member.getMember().getRole())
                                   .build();
    }

    private ProfileKey decryptProfileKey(ByteString profileKey, UUID uuid) throws VerificationFailedException, InvalidGroupStateException {
      try {
        ProfileKeyCiphertext profileKeyCiphertext = new ProfileKeyCiphertext(profileKey.toByteArray());
        return clientZkGroupCipher.decryptProfileKey(profileKeyCiphertext, uuid);
      } catch (InvalidInputException e) {
        throw new InvalidGroupStateException(e);
      }
    }

    private ByteString decryptProfileKeyToByteString(ByteString profileKey, UUID uuid) throws VerificationFailedException, InvalidGroupStateException {
      return ByteString.copyFrom(decryptProfileKey(profileKey, uuid).serialize());
    }

    private ByteString decryptUuidToByteString(ByteString userId) throws InvalidGroupStateException, VerificationFailedException {
      return ByteString.copyFrom(UUIDUtil.serialize(decryptUuid(userId)));
    }

    private ByteString encryptUuid(UUID uuid) {
      return ByteString.copyFrom(clientZkGroupCipher.encryptUuid(uuid).serialize());
    }

    private UUID decryptUuid(ByteString userId) throws InvalidGroupStateException, VerificationFailedException {
      try {
        return clientZkGroupCipher.decryptUuid(new UuidCiphertext(userId.toByteArray()));
      } catch (InvalidInputException e) {
        throw new InvalidGroupStateException(e);
      }
    }

    /**
     * Attempts to decrypt a UUID, but will return {@link #UNKNOWN_UUID} if it cannot.
     */
    private UUID decryptUuidOrUnknown(ByteString userId) {
      try {
        return clientZkGroupCipher.decryptUuid(new UuidCiphertext(userId.toByteArray()));
      } catch (InvalidInputException | VerificationFailedException e) {
        return UNKNOWN_UUID;
      }
    }

    private ByteString encryptTitle(String title) {
      try {
        return ByteString.copyFrom(clientZkGroupCipher.encryptBlob((title == null ? "" : title).getBytes(StandardCharsets.UTF_8)));
      } catch (VerificationFailedException e) {
        throw new AssertionError(e);
      }
    }

    private String decryptTitle(ByteString cipherText) throws VerificationFailedException {
      return new String(decryptBlob(cipherText), StandardCharsets.UTF_8);
    }

    private DisappearingMessagesTimer decryptDisappearingMessagesTimer(ByteString encryptedTimerMessage)
      throws VerificationFailedException, InvalidProtocolBufferException
    {
      return DisappearingMessagesTimer.parseFrom(decryptBlob(encryptedTimerMessage));
    }

    private byte[] decryptBlob(ByteString blob) throws VerificationFailedException {
      return decryptBlob(blob.toByteArray());
    }

    public byte[] decryptAvatar(byte[] bytes) throws VerificationFailedException {
      return decryptBlob(bytes);
    }

    private byte[] decryptBlob(byte[] bytes) throws VerificationFailedException {
      // TODO GV2: Minimum field length checking should be responsibility of clientZkGroupCipher#decryptBlob
      if (bytes == null) return null;
      if (bytes.length == 0) return bytes;
      if (bytes.length < 28) throw new VerificationFailedException();
      return clientZkGroupCipher.decryptBlob(bytes);
    }

    private ByteString encryptTimer(int timerDurationSeconds) {
       try {
         DisappearingMessagesTimer timer = DisappearingMessagesTimer.newBuilder()
                                                                    .setDuration(timerDurationSeconds)
                                                                    .build();
         return ByteString.copyFrom(clientZkGroupCipher.encryptBlob(timer.toByteArray()));
       } catch (VerificationFailedException e) {
         throw new AssertionError(e);
       }
    }

    /**
     * Verifies signature and parses actions on a group change.
     */
    private GroupChange.Actions getVerifiedActions(GroupChange groupChange)
      throws VerificationFailedException, InvalidProtocolBufferException
    {
      byte[] actionsByteArray = groupChange.getActions().toByteArray();

      NotarySignature signature;
      try {
        signature = new NotarySignature(groupChange.getServerSignature().toByteArray());
      } catch (InvalidInputException e) {
        throw new VerificationFailedException();
      }

      serverPublicParams.verifySignature(actionsByteArray, signature);

      return GroupChange.Actions.parseFrom(actionsByteArray);
    }

    /**
     * Parses actions on a group change without verification.
     */
    private GroupChange.Actions getActions(GroupChange groupChange)
      throws InvalidProtocolBufferException
    {
      return GroupChange.Actions.parseFrom(groupChange.getActions());
    }

    public GroupChange.Actions.Builder createChangeMembershipRights(AccessControl.AccessRequired newRights) {
      return GroupChange.Actions.newBuilder()
                                .setModifyMemberAccess(GroupChange.Actions.ModifyMembersAccessControlAction.newBuilder()
                                                                                                           .setMembersAccess(newRights));
    }

    public GroupChange.Actions.Builder createChangeAttributesRights(AccessControl.AccessRequired newRights) {
      return GroupChange.Actions.newBuilder()
                                .setModifyAttributesAccess(GroupChange.Actions.ModifyAttributesAccessControlAction.newBuilder()
                                                                                                                  .setAttributesAccess(newRights));
    }
  }

  public static class NewGroup {
    private final GroupSecretParams groupSecretParams;
    private final Group             newGroupMessage;
    private final Optional<byte[]>  avatar;

    private NewGroup(GroupSecretParams groupSecretParams, Group newGroupMessage, Optional<byte[]> avatar) {
      this.groupSecretParams = groupSecretParams;
      this.newGroupMessage   = newGroupMessage;
      this.avatar            = avatar;
    }

    public GroupSecretParams getGroupSecretParams() {
      return groupSecretParams;
    }

    public Group getNewGroupMessage() {
      return newGroupMessage;
    }

    public Optional<byte[]> getAvatar() {
      return avatar;
    }
  }
}
