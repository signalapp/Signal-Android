package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Group;
import org.signal.storageservice.protos.groups.GroupAttributeBlob;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupJoinInfo;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.PendingMember;
import org.signal.storageservice.protos.groups.RequestingMember;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
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
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Contains operations to create, modify and validate groups and group changes.
 */
public final class GroupsV2Operations {

  private static final String TAG = GroupsV2Operations.class.getSimpleName();

  /** Used for undecryptable pending invites */
  public static final UUID UNKNOWN_UUID = UuidUtil.UNKNOWN_UUID;

  /** Highest change epoch this class knows now to decrypt */
  public static final int HIGHEST_KNOWN_EPOCH = 1;

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
  public NewGroup createNewGroup(final GroupSecretParams groupSecretParams,
                                 final String title,
                                 final Optional<byte[]> avatar,
                                 final GroupCandidate self,
                                 final Set<GroupCandidate> members,
                                 final Member.Role memberRole,
                                 final int disappearingMessageTimerSeconds)
  {

    if (members.contains(self)) {
      throw new IllegalArgumentException("Members must not contain self");
    }

    final GroupOperations groupOperations = forGroup(groupSecretParams);

    Group.Builder group = Group.newBuilder()
                               .setRevision(0)
                               .setPublicKey(ByteString.copyFrom(groupSecretParams.getPublicParams().serialize()))
                               .setTitle(groupOperations.encryptTitle(title))
                               .setDisappearingMessagesTimer(groupOperations.encryptTimer(disappearingMessageTimerSeconds))
                               .setAccessControl(AccessControl.newBuilder()
                                                              .setAttributes(AccessControl.AccessRequired.MEMBER)
                                                              .setMembers(AccessControl.AccessRequired.MEMBER));

    group.addMembers(groupOperations.member(self.getProfileKeyCredential().get(), Member.Role.ADMINISTRATOR));

    for (GroupCandidate credential : members) {
      ProfileKeyCredential profileKeyCredential = credential.getProfileKeyCredential().orNull();

      if (profileKeyCredential != null) {
        group.addMembers(groupOperations.member(profileKeyCredential, memberRole));
      } else {
        group.addPendingMembers(groupOperations.invitee(credential.getUuid(), memberRole));
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

    public GroupChange.Actions.Builder createModifyGroupTitle(final String title) {
      return GroupChange.Actions.newBuilder().setModifyTitle(GroupChange.Actions.ModifyTitleAction
                                                                                .newBuilder()
                                                                                .setTitle(encryptTitle(title)));
    }

    public GroupChange.Actions.Builder createModifyGroupMembershipChange(Set<GroupCandidate> membersToAdd, UUID selfUuid) {
      final GroupOperations groupOperations = forGroup(groupSecretParams);

      GroupChange.Actions.Builder actions = GroupChange.Actions.newBuilder();

      for (GroupCandidate credential : membersToAdd) {
        Member.Role          newMemberRole        = Member.Role.DEFAULT;
        ProfileKeyCredential profileKeyCredential = credential.getProfileKeyCredential().orNull();

        if (profileKeyCredential != null) {
          actions.addAddMembers(GroupChange.Actions.AddMemberAction
                                                   .newBuilder()
                                                   .setAdded(groupOperations.member(profileKeyCredential, newMemberRole)));
        } else {
          actions.addAddPendingMembers(GroupChange.Actions.AddPendingMemberAction
                                                          .newBuilder()
                                                          .setAdded(groupOperations.invitee(credential.getUuid(), newMemberRole)
                                                                                   .setAddedByUserId(encryptUuid(selfUuid))));
        }
      }

      return actions;
    }

    public GroupChange.Actions.Builder createGroupJoinRequest(ProfileKeyCredential profileKeyCredential) {
      GroupOperations             groupOperations = forGroup(groupSecretParams);
      GroupChange.Actions.Builder actions         = GroupChange.Actions.newBuilder();

      actions.addAddRequestingMembers(GroupChange.Actions.AddRequestingMemberAction
                                                         .newBuilder()
                                                         .setAdded(groupOperations.requestingMember(profileKeyCredential)));

      return actions;
    }

    public GroupChange.Actions.Builder createGroupJoinDirect(ProfileKeyCredential profileKeyCredential) {
      GroupOperations             groupOperations = forGroup(groupSecretParams);
      GroupChange.Actions.Builder actions         = GroupChange.Actions.newBuilder();

      actions.addAddMembers(GroupChange.Actions.AddMemberAction
                                               .newBuilder()
                                               .setAdded(groupOperations.member(profileKeyCredential, Member.Role.DEFAULT)));

      return actions;
    }

    public GroupChange.Actions.Builder createRefuseGroupJoinRequest(Set<UUID> requestsToRemove) {
      GroupChange.Actions.Builder actions = GroupChange.Actions.newBuilder();

      for (UUID uuid : requestsToRemove) {
        actions.addDeleteRequestingMembers(GroupChange.Actions.DeleteRequestingMemberAction
                                                              .newBuilder()
                                                              .setDeletedUserId(encryptUuid(uuid)));
      }

      return actions;
    }

    public GroupChange.Actions.Builder createApproveGroupJoinRequest(Set<UUID> requestsToApprove) {
      GroupChange.Actions.Builder actions = GroupChange.Actions.newBuilder();

      for (UUID uuid : requestsToApprove) {
        actions.addPromoteRequestingMembers(GroupChange.Actions.PromoteRequestingMemberAction
                                                               .newBuilder()
                                                               .setRole(Member.Role.DEFAULT)
                                                               .setUserId(encryptUuid(uuid)));
      }

      return actions;
    }

    public GroupChange.Actions.Builder createRemoveMembersChange(final Set<UUID> membersToRemove) {
      GroupChange.Actions.Builder actions = GroupChange.Actions.newBuilder();

      for (UUID remove: membersToRemove) {
        actions.addDeleteMembers(GroupChange.Actions.DeleteMemberAction
                                                    .newBuilder()
                                                    .setDeletedUserId(encryptUuid(remove)));
      }

      return actions;
    }

    public GroupChange.Actions.Builder createLeaveAndPromoteMembersToAdmin(UUID self, List<UUID> membersToMakeAdmin) {
      GroupChange.Actions.Builder actions = createRemoveMembersChange(Collections.singleton(self));

      for (UUID member : membersToMakeAdmin) {
        actions.addModifyMemberRoles(GroupChange.Actions.ModifyMemberRoleAction
                                                        .newBuilder()
                                                        .setUserId(encryptUuid(member))
                                                        .setRole(Member.Role.ADMINISTRATOR));
      }

      return actions;
    }

    public GroupChange.Actions.Builder createModifyGroupTimerChange(int timerDurationSeconds) {
      return GroupChange.Actions
                        .newBuilder()
                        .setModifyDisappearingMessagesTimer(GroupChange.Actions.ModifyDisappearingMessagesTimerAction
                                                                               .newBuilder()
                                                                               .setTimer(encryptTimer(timerDurationSeconds)));
    }

    public GroupChange.Actions.Builder createUpdateProfileKeyCredentialChange(ProfileKeyCredential profileKeyCredential) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(random, groupSecretParams, profileKeyCredential);

      return GroupChange.Actions
                        .newBuilder()
                        .addModifyMemberProfileKeys(GroupChange.Actions.ModifyMemberProfileKeyAction
                                                                       .newBuilder()
                                                                       .setPresentation(ByteString.copyFrom(presentation.serialize())));
    }

    public GroupChange.Actions.Builder createAcceptInviteChange(ProfileKeyCredential profileKeyCredential) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(random, groupSecretParams, profileKeyCredential);

      return GroupChange.Actions
                        .newBuilder()
                        .addPromotePendingMembers(GroupChange.Actions.PromotePendingMemberAction
                                                                     .newBuilder()
                                                                     .setPresentation(ByteString.copyFrom(presentation.serialize())));
    }

    public GroupChange.Actions.Builder createRemoveInvitationChange(final Set<UuidCiphertext> uuidCipherTextsFromInvitesToRemove) {
      GroupChange.Actions.Builder builder = GroupChange.Actions
                                                       .newBuilder();

      for (UuidCiphertext uuidCipherText: uuidCipherTextsFromInvitesToRemove) {
        builder.addDeletePendingMembers(GroupChange.Actions.DeletePendingMemberAction
                                                           .newBuilder()
                                                           .setDeletedUserId(ByteString.copyFrom(uuidCipherText.serialize())));
      }

      return builder;
    }

    public GroupChange.Actions.Builder createModifyGroupLinkPasswordChange(byte[] groupLinkPassword) {
      return GroupChange.Actions
                        .newBuilder()
                        .setModifyInviteLinkPassword(GroupChange.Actions.ModifyInviteLinkPasswordAction
                                                                        .newBuilder()
                                                                        .setInviteLinkPassword(ByteString.copyFrom(groupLinkPassword)));
    }

    public GroupChange.Actions.Builder createModifyGroupLinkPasswordAndRightsChange(byte[] groupLinkPassword, AccessControl.AccessRequired newRights) {
      GroupChange.Actions.Builder change = createModifyGroupLinkPasswordChange(groupLinkPassword);

      return change.setModifyAddFromInviteLinkAccess(GroupChange.Actions.ModifyAddFromInviteLinkAccessControlAction
                                                                        .newBuilder()
                                                                        .setAddFromInviteLinkAccess(newRights));
    }

    public GroupChange.Actions.Builder createChangeJoinByLinkRights(AccessControl.AccessRequired newRights) {
      return GroupChange.Actions
                        .newBuilder()
                        .setModifyAddFromInviteLinkAccess(GroupChange.Actions.ModifyAddFromInviteLinkAccessControlAction
                                                                             .newBuilder()
                                                                             .setAddFromInviteLinkAccess(newRights));
    }

    public GroupChange.Actions.Builder createChangeMembershipRights(AccessControl.AccessRequired newRights) {
      return GroupChange.Actions
                        .newBuilder()
                        .setModifyMemberAccess(GroupChange.Actions.ModifyMembersAccessControlAction
                                                                  .newBuilder()
                                                                  .setMembersAccess(newRights));
    }

    public GroupChange.Actions.Builder createChangeAttributesRights(AccessControl.AccessRequired newRights) {
      return GroupChange.Actions
                        .newBuilder()
                        .setModifyAttributesAccess(GroupChange.Actions.ModifyAttributesAccessControlAction
                                                                      .newBuilder()
                                                                      .setAttributesAccess(newRights));
    }

    private Member.Builder member(ProfileKeyCredential credential, Member.Role role) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(new SecureRandom(), groupSecretParams, credential);

      return Member.newBuilder()
                   .setRole(role)
                   .setPresentation(ByteString.copyFrom(presentation.serialize()));
    }

    private RequestingMember.Builder requestingMember(ProfileKeyCredential credential) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(new SecureRandom(), groupSecretParams, credential);

      return RequestingMember.newBuilder()
                             .setPresentation(ByteString.copyFrom(presentation.serialize()));
    }

    public PendingMember.Builder invitee(UUID uuid, Member.Role role) {
      UuidCiphertext uuidCiphertext = clientZkGroupCipher.encryptUuid(uuid);

      Member member = Member.newBuilder()
                            .setRole(role)
                            .setUserId(ByteString.copyFrom(uuidCiphertext.serialize()))
                            .build();

      return PendingMember.newBuilder()
                          .setMember(member);
    }

    public DecryptedGroup decryptGroup(Group group)
        throws VerificationFailedException, InvalidGroupStateException
    {
      List<Member>                    membersList                = group.getMembersList();
      List<PendingMember>             pendingMembersList         = group.getPendingMembersList();
      List<RequestingMember>          requestingMembersList      = group.getRequestingMembersList();
      List<DecryptedMember>           decryptedMembers           = new ArrayList<>(membersList.size());
      List<DecryptedPendingMember>    decryptedPendingMembers    = new ArrayList<>(pendingMembersList.size());
      List<DecryptedRequestingMember> decryptedRequestingMembers = new ArrayList<>(requestingMembersList.size());

      for (Member member : membersList) {
        try {
          decryptedMembers.add(decryptMember(member).build());
        } catch (InvalidInputException e) {
          throw new InvalidGroupStateException(e);
        }
      }

      for (PendingMember member : pendingMembersList) {
        decryptedPendingMembers.add(decryptMember(member));
      }

      for (RequestingMember member : requestingMembersList) {
        decryptedRequestingMembers.add(decryptRequestingMember(member));
      }

      return DecryptedGroup.newBuilder()
                           .setTitle(decryptTitle(group.getTitle()))
                           .setAvatar(group.getAvatar())
                           .setAccessControl(group.getAccessControl())
                           .setRevision(group.getRevision())
                           .addAllMembers(decryptedMembers)
                           .addAllPendingMembers(decryptedPendingMembers)
                           .addAllRequestingMembers(decryptedRequestingMembers)
                           .setDisappearingMessagesTimer(DecryptedTimer.newBuilder().setDuration(decryptDisappearingMessagesTimer(group.getDisappearingMessagesTimer())))
                           .setInviteLinkPassword(group.getInviteLinkPassword())
                           .build();
    }

    /**
     * @param verifySignature You might want to avoid verification if you already know it's correct, or you
     *                        are not going to pass to other clients.
     *                        <p>
     *                        Also, if you know it's version 0, do not verify because changes for version 0
     *                        are not signed, but should be empty.
     * @return {@link Optional#absent} if the epoch for the change is higher that this code can decrypt.
     */
    public Optional<DecryptedGroupChange> decryptChange(GroupChange groupChange, boolean verifySignature)
        throws InvalidProtocolBufferException, VerificationFailedException, InvalidGroupStateException
    {
      if (groupChange.getChangeEpoch() > HIGHEST_KNOWN_EPOCH) {
        Log.w(TAG, String.format(Locale.US, "Ignoring change from Epoch %d. Highest known Epoch is %d", groupChange.getChangeEpoch(), HIGHEST_KNOWN_EPOCH));
        return Optional.absent();
      }

      GroupChange.Actions actions = verifySignature ? getVerifiedActions(groupChange) : getActions(groupChange);

      return Optional.of(decryptChange(actions));
    }

    public DecryptedGroupChange decryptChange(GroupChange.Actions actions)
        throws VerificationFailedException, InvalidGroupStateException
    {
      return decryptChange(actions, null);
    }

    public DecryptedGroupChange decryptChange(GroupChange.Actions actions, UUID source)
        throws VerificationFailedException, InvalidGroupStateException
    {
      DecryptedGroupChange.Builder builder = DecryptedGroupChange.newBuilder();

      // Field 1
      if (source != null) {
        builder.setEditor(UuidUtil.toByteString(source));
      } else {
        builder.setEditor(decryptUuidToByteString(actions.getSourceUuid()));
      }

      // Field 2
      builder.setRevision(actions.getRevision());

      // Field 3
      for (GroupChange.Actions.AddMemberAction addMemberAction : actions.getAddMembersList()) {
        try {
          builder.addNewMembers(decryptMember(addMemberAction.getAdded()).setJoinedAtRevision(actions.getRevision()));
        } catch (InvalidInputException e) {
          throw new InvalidGroupStateException(e);
        }
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
                                                        .setJoinedAtRevision(-1)
                                                        .setUuid(UuidUtil.toByteString(uuid))
                                                        .setProfileKey(ByteString.copyFrom(decryptProfileKey(ByteString.copyFrom(presentation.getProfileKeyCiphertext().serialize()), uuid).serialize())));
        } catch (InvalidInputException e) {
          throw new InvalidGroupStateException(e);
        }
      }

      // Field 7
      for (GroupChange.Actions.AddPendingMemberAction addPendingMemberAction : actions.getAddPendingMembersList()) {
        PendingMember added          = addPendingMemberAction.getAdded();
        Member        member         = added.getMember();
        ByteString    uuidCipherText = member.getUserId();
        UUID          uuid           = decryptUuidOrUnknown(uuidCipherText);

        builder.addNewPendingMembers(DecryptedPendingMember.newBuilder()
                                                           .setUuid(UuidUtil.toByteString(uuid))
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
                                                                     .setUuid(UuidUtil.toByteString(uuid))
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
        UUID       uuid       = clientZkGroupCipher.decryptUuid(profileKeyCredentialPresentation.getUuidCiphertext());
        ProfileKey profileKey = clientZkGroupCipher.decryptProfileKey(profileKeyCredentialPresentation.getProfileKeyCiphertext(), uuid);
        builder.addPromotePendingMembers(DecryptedMember.newBuilder()
                                                        .setJoinedAtRevision(-1)
                                                        .setRole(Member.Role.DEFAULT)
                                                        .setUuid(UuidUtil.toByteString(uuid))
                                                        .setProfileKey(ByteString.copyFrom(profileKey.serialize())));
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
        int duration = decryptDisappearingMessagesTimer(actions.getModifyDisappearingMessagesTimer().getTimer());
        builder.setNewTimer(DecryptedTimer.newBuilder().setDuration(duration));
      }

      // Field 13
      if (actions.hasModifyAttributesAccess()) {
        builder.setNewAttributeAccess(actions.getModifyAttributesAccess().getAttributesAccess());
      }

      // Field 14
      if (actions.hasModifyMemberAccess()) {
        builder.setNewMemberAccess(actions.getModifyMemberAccess().getMembersAccess());
      }

      // Field 15
      if (actions.hasModifyAddFromInviteLinkAccess()) {
        builder.setNewInviteLinkAccess(actions.getModifyAddFromInviteLinkAccess().getAddFromInviteLinkAccess());
      }

      // Field 16
      for (GroupChange.Actions.AddRequestingMemberAction request : actions.getAddRequestingMembersList()) {
        builder.addNewRequestingMembers(decryptRequestingMember(request.getAdded()));
      }

      // Field 17
      for (GroupChange.Actions.DeleteRequestingMemberAction delete : actions.getDeleteRequestingMembersList()) {
        builder.addDeleteRequestingMembers(decryptUuidToByteString(delete.getDeletedUserId()));
      }

      // Field 18
      for (GroupChange.Actions.PromoteRequestingMemberAction promote : actions.getPromoteRequestingMembersList()) {
        builder.addPromoteRequestingMembers(DecryptedApproveMember.newBuilder().setRole(promote.getRole()).setUuid(decryptUuidToByteString(promote.getUserId())));
      }

      // Field 19
      if (actions.hasModifyInviteLinkPassword()) {
        builder.setNewInviteLinkPassword(actions.getModifyInviteLinkPassword().getInviteLinkPassword());
      }

      return builder.build();
    }

    public DecryptedGroupJoinInfo decryptGroupJoinInfo(GroupJoinInfo joinInfo) {
      return DecryptedGroupJoinInfo.newBuilder()
                                   .setTitle(decryptTitle(joinInfo.getTitle()))
                                   .setAvatar(joinInfo.getAvatar())
                                   .setMemberCount(joinInfo.getMemberCount())
                                   .setAddFromInviteLink(joinInfo.getAddFromInviteLink())
                                   .setRevision(joinInfo.getRevision())
                                   .setPendingAdminApproval(joinInfo.getPendingAdminApproval())
                                   .build();
    }

    private DecryptedMember.Builder decryptMember(Member member)
        throws InvalidGroupStateException, VerificationFailedException, InvalidInputException
    {
      if (member.getPresentation().isEmpty()) {
        UUID uuid = decryptUuid(member.getUserId());

        return DecryptedMember.newBuilder()
                              .setUuid(UuidUtil.toByteString(uuid))
                              .setJoinedAtRevision(member.getJoinedAtRevision())
                              .setProfileKey(decryptProfileKeyToByteString(member.getProfileKey(), uuid))
                              .setRole(member.getRole());
      } else {
        ProfileKeyCredentialPresentation profileKeyCredentialPresentation = new ProfileKeyCredentialPresentation(member.getPresentation().toByteArray());
        UUID                             uuid                             = clientZkGroupCipher.decryptUuid(profileKeyCredentialPresentation.getUuidCiphertext());
        ProfileKey                       profileKey                       = clientZkGroupCipher.decryptProfileKey(profileKeyCredentialPresentation.getProfileKeyCiphertext(), uuid);

        return DecryptedMember.newBuilder()
                              .setUuid(UuidUtil.toByteString(uuid))
                              .setJoinedAtRevision(member.getJoinedAtRevision())
                              .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                              .setRole(member.getRole());
      }
    }

    private DecryptedPendingMember decryptMember(PendingMember member)
        throws InvalidGroupStateException, VerificationFailedException
    {
      ByteString userIdCipherText = member.getMember().getUserId();
      UUID       uuid             = decryptUuidOrUnknown(userIdCipherText);
      UUID       addedBy          = decryptUuid(member.getAddedByUserId());

      Member.Role role = member.getMember().getRole();

      if (role != Member.Role.ADMINISTRATOR && role != Member.Role.DEFAULT) {
        role = Member.Role.DEFAULT;
      }

      return DecryptedPendingMember.newBuilder()
                                   .setUuid(UuidUtil.toByteString(uuid))
                                   .setUuidCipherText(userIdCipherText)
                                   .setAddedByUuid(ByteString.copyFrom(UUIDUtil.serialize(addedBy)))
                                   .setRole(role)
                                   .setTimestamp(member.getTimestamp())
                                   .build();
    }

    private DecryptedRequestingMember decryptRequestingMember(RequestingMember member)
        throws InvalidGroupStateException, VerificationFailedException
    {
      if (member.getPresentation().isEmpty()) {
        UUID uuid = decryptUuid(member.getUserId());

        return DecryptedRequestingMember.newBuilder()
                                        .setUuid(UuidUtil.toByteString(uuid))
                                        .setProfileKey(decryptProfileKeyToByteString(member.getProfileKey(), uuid))
                                        .setTimestamp(member.getTimestamp())
                                        .build();
      } else {
        ProfileKeyCredentialPresentation profileKeyCredentialPresentation;
        try {
          profileKeyCredentialPresentation = new ProfileKeyCredentialPresentation(member.getPresentation().toByteArray());
        } catch (InvalidInputException e) {
          throw new InvalidGroupStateException(e);
        }

        UUID       uuid       = clientZkGroupCipher.decryptUuid(profileKeyCredentialPresentation.getUuidCiphertext());
        ProfileKey profileKey = clientZkGroupCipher.decryptProfileKey(profileKeyCredentialPresentation.getProfileKeyCiphertext(), uuid);

        return DecryptedRequestingMember.newBuilder()
                                        .setUuid(UuidUtil.toByteString(uuid))
                                        .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                                        .build();
      }
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

    ByteString encryptUuid(UUID uuid) {
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

    ByteString encryptTitle(String title) {
      try {
        GroupAttributeBlob blob = GroupAttributeBlob.newBuilder().setTitle(title).build();

        return ByteString.copyFrom(clientZkGroupCipher.encryptBlob(blob.toByteArray()));
      } catch (VerificationFailedException e) {
        throw new AssertionError(e);
      }
    }

    private String decryptTitle(ByteString cipherText) {
      return decryptBlob(cipherText).getTitle().trim();
    }

    private int decryptDisappearingMessagesTimer(ByteString encryptedTimerMessage) {
      return decryptBlob(encryptedTimerMessage).getDisappearingMessagesDuration();
    }

    public byte[] decryptAvatar(byte[] bytes) {
      return decryptBlob(bytes).getAvatar().toByteArray();
    }

    private GroupAttributeBlob decryptBlob(ByteString blob) {
      return decryptBlob(blob.toByteArray());
    }

    private GroupAttributeBlob decryptBlob(byte[] bytes) {
      // TODO GV2: Minimum field length checking should be responsibility of clientZkGroupCipher#decryptBlob
      if (bytes == null || bytes.length == 0) {
        return GroupAttributeBlob.getDefaultInstance();
      }
      if (bytes.length < 29) {
        Log.w(TAG, "Bad encrypted blob length");
        return GroupAttributeBlob.getDefaultInstance();
      }
      try {
        return GroupAttributeBlob.parseFrom(clientZkGroupCipher.decryptBlob(bytes));
      } catch (InvalidProtocolBufferException | VerificationFailedException e) {
        Log.w(TAG, "Bad encrypted blob");
        return GroupAttributeBlob.getDefaultInstance();
      }
    }

    ByteString encryptTimer(int timerDurationSeconds) {
       try {
         GroupAttributeBlob timer = GroupAttributeBlob.newBuilder()
                                                      .setDisappearingMessagesDuration(timerDurationSeconds)
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

    public GroupChange.Actions.Builder createChangeMemberRole(UUID uuid, Member.Role role) {
      return GroupChange.Actions.newBuilder()
                                .addModifyMemberRoles(GroupChange.Actions.ModifyMemberRoleAction.newBuilder()
                                                                         .setUserId(encryptUuid(uuid))
                                                                         .setRole(role));
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
