package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.NotarySignature;
import org.signal.libsignal.zkgroup.ServerPublicParams;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.auth.ClientZkAuthOperations;
import org.signal.libsignal.zkgroup.groups.ClientZkGroupCipher;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.libsignal.zkgroup.groups.ProfileKeyCiphertext;
import org.signal.libsignal.zkgroup.groups.UuidCiphertext;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialPresentation;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.BannedMember;
import org.signal.storageservice.protos.groups.Group;
import org.signal.storageservice.protos.groups.GroupAttributeBlob;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupJoinInfo;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.PendingMember;
import org.signal.storageservice.protos.groups.RequestingMember;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember;
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
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Contains operations to create, modify and validate groups and group changes.
 */
public final class GroupsV2Operations {

  private static final String TAG = GroupsV2Operations.class.getSimpleName();

  /** Used for undecryptable pending invites */
  public static final UUID UNKNOWN_UUID = UuidUtil.UNKNOWN_UUID;

  /** Highest change epoch this class knows now to decrypt */
  public static final int HIGHEST_KNOWN_EPOCH = 5;

  private final ServerPublicParams        serverPublicParams;
  private final ClientZkProfileOperations clientZkProfileOperations;
  private final ClientZkAuthOperations    clientZkAuthOperations;
  private final int                       maxGroupSize;
  private final SecureRandom              random;

  public GroupsV2Operations(ClientZkOperations clientZkOperations, int maxGroupSize) {
    this.serverPublicParams        = clientZkOperations.getServerPublicParams();
    this.clientZkProfileOperations = clientZkOperations.getProfileOperations();
    this.clientZkAuthOperations    = clientZkOperations.getAuthOperations();
    this.maxGroupSize              = maxGroupSize;
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

    group.addMembers(groupOperations.member(self.requireExpiringProfileKeyCredential(), Member.Role.ADMINISTRATOR));

    for (GroupCandidate credential : members) {
      ExpiringProfileKeyCredential expiringProfileKeyCredential = credential.getExpiringProfileKeyCredential().orElse(null);

      if (expiringProfileKeyCredential != null) {
        group.addMembers(groupOperations.member(expiringProfileKeyCredential, memberRole));
      } else {
        group.addPendingMembers(groupOperations.invitee(credential.getServiceId(), memberRole));
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

    public GroupChange.Actions.ModifyDescriptionAction.Builder createModifyGroupDescriptionAction(final String description) {
      return GroupChange.Actions.ModifyDescriptionAction.newBuilder().setDescription(encryptDescription(description));
    }

    public GroupChange.Actions.Builder createModifyGroupDescription(final String description) {
      return GroupChange.Actions.newBuilder().setModifyDescription(createModifyGroupDescriptionAction(description));
    }

    public GroupChange.Actions.Builder createModifyGroupMembershipChange(Set<GroupCandidate> membersToAdd, Set<ServiceId> bannedMembers, ACI selfAci) {
      final GroupOperations groupOperations = forGroup(groupSecretParams);

      Set<ServiceId> membersToUnban = membersToAdd.stream().map(GroupCandidate::getServiceId).filter(bannedMembers::contains).collect(Collectors.toSet());

      GroupChange.Actions.Builder actions = membersToUnban.isEmpty() ? GroupChange.Actions.newBuilder()
                                                                     : createUnbanServiceIdsChange(membersToUnban);

      for (GroupCandidate credential : membersToAdd) {
        Member.Role                  newMemberRole                = Member.Role.DEFAULT;
        ExpiringProfileKeyCredential expiringProfileKeyCredential = credential.getExpiringProfileKeyCredential().orElse(null);

        if (expiringProfileKeyCredential != null) {
          actions.addAddMembers(GroupChange.Actions.AddMemberAction
                                                   .newBuilder()
                                                   .setAdded(groupOperations.member(expiringProfileKeyCredential, newMemberRole)));
        } else {
          actions.addAddPendingMembers(GroupChange.Actions.AddPendingMemberAction
                                                          .newBuilder()
                                                          .setAdded(groupOperations.invitee(credential.getServiceId(), newMemberRole)
                                                                                   .setAddedByUserId(encryptServiceId(selfAci))));
        }
      }

      return actions;
    }

    public GroupChange.Actions.Builder createGroupJoinRequest(ExpiringProfileKeyCredential expiringProfileKeyCredential) {
      GroupOperations             groupOperations = forGroup(groupSecretParams);
      GroupChange.Actions.Builder actions         = GroupChange.Actions.newBuilder();

      actions.addAddRequestingMembers(GroupChange.Actions.AddRequestingMemberAction
                                                         .newBuilder()
                                                         .setAdded(groupOperations.requestingMember(expiringProfileKeyCredential)));

      return actions;
    }

    public GroupChange.Actions.Builder createGroupJoinDirect(ExpiringProfileKeyCredential expiringProfileKeyCredential) {
      GroupOperations             groupOperations = forGroup(groupSecretParams);
      GroupChange.Actions.Builder actions         = GroupChange.Actions.newBuilder();

      actions.addAddMembers(GroupChange.Actions.AddMemberAction
                                               .newBuilder()
                                               .setAdded(groupOperations.member(expiringProfileKeyCredential, Member.Role.DEFAULT)));

      return actions;
    }

    public GroupChange.Actions.Builder createRefuseGroupJoinRequest(Set<UUID> requestsToRemove, boolean alsoBan, List<DecryptedBannedMember> bannedMembers) {
      GroupChange.Actions.Builder actions = alsoBan ? createBanUuidsChange(requestsToRemove, false, bannedMembers)
                                                    : GroupChange.Actions.newBuilder();

      for (UUID uuid : requestsToRemove) {
        actions.addDeleteRequestingMembers(GroupChange.Actions.DeleteRequestingMemberAction
                                                              .newBuilder()
                                                              .setDeletedUserId(encryptServiceId(ACI.from(uuid))));
      }

      return actions;
    }

    public GroupChange.Actions.Builder createApproveGroupJoinRequest(Set<UUID> requestsToApprove) {
      GroupChange.Actions.Builder actions = GroupChange.Actions.newBuilder();

      for (UUID uuid : requestsToApprove) {
        actions.addPromoteRequestingMembers(GroupChange.Actions.PromoteRequestingMemberAction
                                                               .newBuilder()
                                                               .setRole(Member.Role.DEFAULT)
                                                               .setUserId(encryptServiceId(ACI.from(uuid))));
      }

      return actions;
    }

    public GroupChange.Actions.Builder createRemoveMembersChange(final Set<UUID> membersToRemove, boolean alsoBan, List<DecryptedBannedMember> bannedMembers) {
      GroupChange.Actions.Builder actions = alsoBan ? createBanUuidsChange(membersToRemove, false, bannedMembers)
                                                    : GroupChange.Actions.newBuilder();

      for (UUID remove: membersToRemove) {
        actions.addDeleteMembers(GroupChange.Actions.DeleteMemberAction
                                                    .newBuilder()
                                                    .setDeletedUserId(encryptServiceId(ACI.from(remove))));
      }

      return actions;
    }

    public GroupChange.Actions.Builder createLeaveAndPromoteMembersToAdmin(UUID self, List<UUID> membersToMakeAdmin) {
      GroupChange.Actions.Builder actions = createRemoveMembersChange(Collections.singleton(self), false, Collections.emptyList());

      for (UUID member : membersToMakeAdmin) {
        actions.addModifyMemberRoles(GroupChange.Actions.ModifyMemberRoleAction
                                                        .newBuilder()
                                                        .setUserId(encryptServiceId(ACI.from(member)))
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

    public GroupChange.Actions.Builder createUpdateProfileKeyCredentialChange(ExpiringProfileKeyCredential expiringProfileKeyCredential) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(random, groupSecretParams, expiringProfileKeyCredential);

      return GroupChange.Actions
                        .newBuilder()
                        .addModifyMemberProfileKeys(GroupChange.Actions.ModifyMemberProfileKeyAction
                                                                       .newBuilder()
                                                                       .setPresentation(ByteString.copyFrom(presentation.serialize())));
    }

    public GroupChange.Actions.Builder createAcceptInviteChange(ExpiringProfileKeyCredential credential) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(random, groupSecretParams, credential);

      return GroupChange.Actions.newBuilder()
                                .addPromotePendingMembers(GroupChange.Actions.PromotePendingMemberAction.newBuilder()
                                                                                                        .setPresentation(ByteString.copyFrom(presentation.serialize())));
    }

    public GroupChange.Actions.Builder createAcceptPniInviteChange(ExpiringProfileKeyCredential credential) {
      ByteString presentation = ByteString.copyFrom(clientZkProfileOperations.createProfileKeyCredentialPresentation(random, groupSecretParams, credential).serialize());

      return GroupChange.Actions.newBuilder()
                                .addPromotePendingPniAciMembers(GroupChange.Actions.PromotePendingPniAciMemberProfileKeyAction.newBuilder()
                                                                                                                              .setPresentation(presentation));
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

    public GroupChange.Actions.Builder createAnnouncementGroupChange(boolean isAnnouncementGroup) {
      return GroupChange.Actions
                        .newBuilder()
                        .setModifyAnnouncementsOnly(GroupChange.Actions.ModifyAnnouncementsOnlyAction
                                                                       .newBuilder()
                                                                       .setAnnouncementsOnly(isAnnouncementGroup));
    }

    /** Note that this can only ban ACIs. */
    public GroupChange.Actions.Builder createBanUuidsChange(Set<UUID> banUuids, boolean rejectJoinRequest, List<DecryptedBannedMember> bannedMembersList) {
      GroupChange.Actions.Builder builder = rejectJoinRequest ? createRefuseGroupJoinRequest(banUuids, false, Collections.emptyList())
                                                              : GroupChange.Actions.newBuilder();

      int spacesToFree = bannedMembersList.size() + banUuids.size() - maxGroupSize;
      if (spacesToFree > 0) {
        List<ByteString> unban = bannedMembersList.stream()
                                                  .sorted(Comparator.comparingLong(DecryptedBannedMember::getTimestamp))
                                                  .limit(spacesToFree)
                                                  .map(DecryptedBannedMember::getServiceIdBinary)
                                                  .collect(Collectors.toList());

        for (ByteString serviceIdBinary : unban) {
          builder.addDeleteBannedMembers(GroupChange.Actions.DeleteBannedMemberAction.newBuilder().setDeletedUserId(encryptServiceId(ServiceId.parseOrThrow(serviceIdBinary.toByteArray()))));
        }
      }

      for (UUID uuid : banUuids) {
        builder.addAddBannedMembers(GroupChange.Actions.AddBannedMemberAction.newBuilder().setAdded(BannedMember.newBuilder().setUserId(encryptServiceId(ACI.from(uuid))).build()));
      }

      return builder;
    }

    public GroupChange.Actions.Builder createUnbanServiceIdsChange(Set<ServiceId> serviceIds) {
      GroupChange.Actions.Builder builder = GroupChange.Actions.newBuilder();

      for (ServiceId serviceId : serviceIds) {
        builder.addDeleteBannedMembers(GroupChange.Actions.DeleteBannedMemberAction.newBuilder().setDeletedUserId(encryptServiceId(serviceId)).build());
      }

      return builder;
    }

    public GroupChange.Actions.Builder replaceAddMembers(GroupChange.Actions.Builder change, List<GroupCandidate> candidates) throws InvalidInputException {
      if (change.getAddMembersCount() != candidates.size()) {
        throw new InvalidInputException("Replacement candidates not same size as original add");
      }

      for (int i = 0; i < change.getAddMembersCount(); i++) {
        GroupChange.Actions.AddMemberAction original  = change.getAddMembers(i);
        GroupCandidate                      candidate = candidates.get(i);

        ExpiringProfileKeyCredential expiringProfileKeyCredential = candidate.getExpiringProfileKeyCredential().orElse(null);

        if (expiringProfileKeyCredential == null) {
          throw new InvalidInputException("Replacement candidate missing credential");
        }

        change.setAddMembers(i,
                             GroupChange.Actions.AddMemberAction.newBuilder()
                                                                .setAdded(member(expiringProfileKeyCredential, original.getAdded().getRole())));
      }

      return change;
    }

    private Member.Builder member(ExpiringProfileKeyCredential credential, Member.Role role) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(new SecureRandom(), groupSecretParams, credential);

      return Member.newBuilder()
                   .setRole(role)
                   .setPresentation(ByteString.copyFrom(presentation.serialize()));
    }

    private RequestingMember.Builder requestingMember(ExpiringProfileKeyCredential credential) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(new SecureRandom(), groupSecretParams, credential);

      return RequestingMember.newBuilder()
                             .setPresentation(ByteString.copyFrom(presentation.serialize()));
    }

    public PendingMember.Builder invitee(ServiceId serviceId, Member.Role role) {
      UuidCiphertext uuidCiphertext = clientZkGroupCipher.encrypt(serviceId.getLibSignalServiceId());

      Member member = Member.newBuilder()
                            .setRole(role)
                            .setUserId(ByteString.copyFrom(uuidCiphertext.serialize()))
                            .build();

      return PendingMember.newBuilder()
                          .setMember(member);
    }

    public PartialDecryptedGroup partialDecryptGroup(Group group)
        throws VerificationFailedException, InvalidGroupStateException
    {
      List<Member>                 membersList             = group.getMembersList();
      List<PendingMember>          pendingMembersList      = group.getPendingMembersList();
      List<DecryptedMember>        decryptedMembers        = new ArrayList<>(membersList.size());
      List<DecryptedPendingMember> decryptedPendingMembers = new ArrayList<>(pendingMembersList.size());

      for (Member member : membersList) {
        ACI memberAci = decryptAci(member.getUserId());
        decryptedMembers.add(DecryptedMember.newBuilder()
                                            .setUuid(memberAci.toByteString())
                                            .setJoinedAtRevision(member.getJoinedAtRevision())
                                            .build());
      }

      for (PendingMember member : pendingMembersList) {
        ServiceId pendingMemberServiceId = decryptServiceIdOrUnknown(member.getMember().getUserId());
        decryptedPendingMembers.add(DecryptedPendingMember.newBuilder()
                                                          .setServiceIdBinary(pendingMemberServiceId.toByteString())
                                                          .build());
      }

      DecryptedGroup decryptedGroup = DecryptedGroup.newBuilder()
                                                    .setRevision(group.getRevision())
                                                    .addAllMembers(decryptedMembers)
                                                    .addAllPendingMembers(decryptedPendingMembers)
                                                    .build();

      return new PartialDecryptedGroup(group, decryptedGroup, GroupsV2Operations.this, groupSecretParams);
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
      List<DecryptedBannedMember>     decryptedBannedMembers     = new ArrayList<>(group.getBannedMembersCount());

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

      for (BannedMember member : group.getBannedMembersList()) {
        decryptedBannedMembers.add(DecryptedBannedMember.newBuilder().setServiceIdBinary(decryptServiceIdToBinary(member.getUserId())).setTimestamp(member.getTimestamp()).build());
      }

      return DecryptedGroup.newBuilder()
                           .setTitle(decryptTitle(group.getTitle()))
                           .setDescription(decryptDescription(group.getDescription()))
                           .setIsAnnouncementGroup(group.getAnnouncementsOnly() ? EnabledState.ENABLED : EnabledState.DISABLED)
                           .setAvatar(group.getAvatar())
                           .setAccessControl(group.getAccessControl())
                           .setRevision(group.getRevision())
                           .addAllMembers(decryptedMembers)
                           .addAllPendingMembers(decryptedPendingMembers)
                           .addAllRequestingMembers(decryptedRequestingMembers)
                           .setDisappearingMessagesTimer(DecryptedTimer.newBuilder().setDuration(decryptDisappearingMessagesTimer(group.getDisappearingMessagesTimer())))
                           .setInviteLinkPassword(group.getInviteLinkPassword())
                           .addAllBannedMembers(decryptedBannedMembers)
                           .build();
    }

    /**
     * @param verifySignature You might want to avoid verification if you already know it's correct, or you
     *                        are not going to pass to other clients.
     *                        <p>
     *                        Also, if you know it's version 0, do not verify because changes for version 0
     *                        are not signed, but should be empty.
     * @return {@link Optional#empty()} if the epoch for the change is higher that this code can decrypt.
     */
    public Optional<DecryptedGroupChange> decryptChange(GroupChange groupChange, boolean verifySignature)
        throws InvalidProtocolBufferException, VerificationFailedException, InvalidGroupStateException
    {
      if (groupChange.getChangeEpoch() > HIGHEST_KNOWN_EPOCH) {
        Log.w(TAG, String.format(Locale.US, "Ignoring change from Epoch %d. Highest known Epoch is %d", groupChange.getChangeEpoch(), HIGHEST_KNOWN_EPOCH));
        return Optional.empty();
      }

      GroupChange.Actions actions = verifySignature ? getVerifiedActions(groupChange) : getActions(groupChange);

      return Optional.of(decryptChange(actions));
    }

    public DecryptedGroupChange decryptChange(GroupChange.Actions actions)
        throws VerificationFailedException, InvalidGroupStateException
    {
      return decryptChange(actions, null);
    }

    public DecryptedGroupChange decryptChange(GroupChange.Actions actions, ServiceId source)
        throws VerificationFailedException, InvalidGroupStateException
    {
      DecryptedGroupChange.Builder builder = DecryptedGroupChange.newBuilder();

      // Field 1
      if (source != null) {
        builder.setEditor(source.toByteString());
      } else {
        builder.setEditor(decryptServiceIdToBinary(actions.getSourceUuid()));
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
        builder.addDeleteMembers(decryptServiceIdToBinary(deleteMemberAction.getDeletedUserId()));
      }

      // Field 5
      for (GroupChange.Actions.ModifyMemberRoleAction modifyMemberRoleAction : actions.getModifyMemberRolesList()) {
        builder.addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder()
               .setRole(modifyMemberRoleAction.getRole())
               .setUuid(decryptServiceIdToBinary(modifyMemberRoleAction.getUserId())));
      }

      // Field 6
      for (GroupChange.Actions.ModifyMemberProfileKeyAction modifyMemberProfileKeyAction : actions.getModifyMemberProfileKeysList()) {
        try {
          ACI        aci;
          ProfileKey profileKey;

          if (modifyMemberProfileKeyAction.getUserId().isEmpty() || modifyMemberProfileKeyAction.getProfileKey().isEmpty()) {
            ProfileKeyCredentialPresentation presentation = new ProfileKeyCredentialPresentation(modifyMemberProfileKeyAction.getPresentation().toByteArray());
            aci        = decryptAci(ByteString.copyFrom(presentation.getUuidCiphertext().serialize()));
            profileKey = decryptProfileKey(ByteString.copyFrom(presentation.getProfileKeyCiphertext().serialize()), aci);
          } else {
            aci        = decryptAci(modifyMemberProfileKeyAction.getUserId());
            profileKey = decryptProfileKey(modifyMemberProfileKeyAction.getProfileKey(), aci);
          }

          builder.addModifiedProfileKeys(DecryptedMember.newBuilder()
                                                        .setRole(Member.Role.UNKNOWN)
                                                        .setJoinedAtRevision(-1)
                                                        .setUuid(aci.toByteString())
                                                        .setProfileKey(ByteString.copyFrom(profileKey.serialize())));
        } catch (InvalidInputException e) {
          throw new InvalidGroupStateException(e);
        }
      }

      // Field 7
      for (GroupChange.Actions.AddPendingMemberAction addPendingMemberAction : actions.getAddPendingMembersList()) {
        PendingMember added          = addPendingMemberAction.getAdded();
        Member        member         = added.getMember();
        ByteString    uuidCipherText = member.getUserId();
        ServiceId     serviceId      = decryptServiceIdOrUnknown(uuidCipherText);

        builder.addNewPendingMembers(DecryptedPendingMember.newBuilder()
                                                           .setServiceIdBinary(serviceId.toByteString())
                                                           .setUuidCipherText(uuidCipherText)
                                                           .setRole(member.getRole())
                                                           .setAddedByUuid(decryptServiceIdToBinary(added.getAddedByUserId()))
                                                           .setTimestamp(added.getTimestamp()));
      }

      // Field 8
      for (GroupChange.Actions.DeletePendingMemberAction deletePendingMemberAction : actions.getDeletePendingMembersList()) {
        ByteString uuidCipherText = deletePendingMemberAction.getDeletedUserId();
        ServiceId  serviceId      = decryptServiceIdOrUnknown(uuidCipherText);

        builder.addDeletePendingMembers(DecryptedPendingMemberRemoval.newBuilder()
                                                                     .setServiceIdBinary(serviceId.toByteString())
                                                                     .setUuidCipherText(uuidCipherText));
      }

      // Field 9
      for (GroupChange.Actions.PromotePendingMemberAction promotePendingMemberAction : actions.getPromotePendingMembersList()) {
        try {
          ACI        aci;
          ProfileKey profileKey;

          if (promotePendingMemberAction.getUserId().isEmpty() || promotePendingMemberAction.getProfileKey().isEmpty()) {
            ProfileKeyCredentialPresentation presentation = new ProfileKeyCredentialPresentation(promotePendingMemberAction.getPresentation().toByteArray());
            aci        = decryptAci(ByteString.copyFrom(presentation.getUuidCiphertext().serialize()));
            profileKey = decryptProfileKey(ByteString.copyFrom(presentation.getProfileKeyCiphertext().serialize()), aci);
          } else {
            aci        = decryptAci(promotePendingMemberAction.getUserId());
            profileKey = decryptProfileKey(promotePendingMemberAction.getProfileKey(), aci);
          }

          builder.addPromotePendingMembers(DecryptedMember.newBuilder()
                                                          .setJoinedAtRevision(-1)
                                                          .setRole(Member.Role.DEFAULT)
                                                          .setUuid(aci.toByteString())
                                                          .setProfileKey(ByteString.copyFrom(profileKey.serialize())));
        } catch (InvalidInputException e) {
          throw new InvalidGroupStateException(e);
        }
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
        builder.addDeleteRequestingMembers(decryptServiceIdToBinary(delete.getDeletedUserId()));
      }

      // Field 18
      for (GroupChange.Actions.PromoteRequestingMemberAction promote : actions.getPromoteRequestingMembersList()) {
        builder.addPromoteRequestingMembers(DecryptedApproveMember.newBuilder().setRole(promote.getRole()).setUuid(decryptServiceIdToBinary(promote.getUserId())));
      }

      // Field 19
      if (actions.hasModifyInviteLinkPassword()) {
        builder.setNewInviteLinkPassword(actions.getModifyInviteLinkPassword().getInviteLinkPassword());
      }

      // Field 20
      if (actions.hasModifyDescription()) {
        builder.setNewDescription(DecryptedString.newBuilder().setValue(decryptDescription(actions.getModifyDescription().getDescription())));
      }

      // Field 21
      if (actions.hasModifyAnnouncementsOnly()) {
        builder.setNewIsAnnouncementGroup(actions.getModifyAnnouncementsOnly().getAnnouncementsOnly() ? EnabledState.ENABLED : EnabledState.DISABLED);
      }

      // Field 22
      for (GroupChange.Actions.AddBannedMemberAction action : actions.getAddBannedMembersList()) {
        builder.addNewBannedMembers(DecryptedBannedMember.newBuilder().setServiceIdBinary(decryptServiceIdToBinary(action.getAdded().getUserId())).setTimestamp(action.getAdded().getTimestamp()).build());
      }

      // Field 23
      for (GroupChange.Actions.DeleteBannedMemberAction action : actions.getDeleteBannedMembersList()) {
        builder.addDeleteBannedMembers(DecryptedBannedMember.newBuilder().setServiceIdBinary(decryptServiceIdToBinary(action.getDeletedUserId())).build());
      }

      // Field 24
      for (GroupChange.Actions.PromotePendingPniAciMemberProfileKeyAction promotePendingPniAciMemberAction : actions.getPromotePendingPniAciMembersList()) {
        ACI        aci        = decryptAci(promotePendingPniAciMemberAction.getUserId());
        ServiceId  pni        = decryptServiceId(promotePendingPniAciMemberAction.getPni());
        ProfileKey profileKey = decryptProfileKey(promotePendingPniAciMemberAction.getProfileKey(), aci);

        if (!(pni instanceof PNI)) {
          throw new InvalidGroupStateException();
        }

        builder.setEditor(aci.toByteString())
               .addPromotePendingPniAciMembers(DecryptedMember.newBuilder()
                                                              .setUuid(aci.toByteString())
                                                              .setRole(Member.Role.DEFAULT)
                                                              .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                                                              .setJoinedAtRevision(actions.getRevision())
                                                              .setPni(pni.toByteString()));
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
                                   .setDescription(decryptDescription(joinInfo.getDescription()))
                                   .build();
    }

    private DecryptedMember.Builder decryptMember(Member member)
        throws InvalidGroupStateException, VerificationFailedException, InvalidInputException
    {
      if (member.getPresentation().isEmpty()) {
        ACI aci = decryptAci(member.getUserId());

        return DecryptedMember.newBuilder()
                              .setUuid(aci.toByteString())
                              .setJoinedAtRevision(member.getJoinedAtRevision())
                              .setProfileKey(decryptProfileKeyToByteString(member.getProfileKey(), aci))
                              .setRole(member.getRole());
      } else {
        ProfileKeyCredentialPresentation profileKeyCredentialPresentation = new ProfileKeyCredentialPresentation(member.getPresentation().toByteArray());

        ServiceId serviceId = ServiceId.fromLibSignal(clientZkGroupCipher.decrypt(profileKeyCredentialPresentation.getUuidCiphertext()));
        if (!(serviceId instanceof ACI)) {
          throw new InvalidGroupStateException();
        }
        ACI serviceIdAsAci  = (ACI)serviceId;

        ProfileKey profileKey = clientZkGroupCipher.decryptProfileKey(profileKeyCredentialPresentation.getProfileKeyCiphertext(), serviceIdAsAci.getLibSignalAci());

        return DecryptedMember.newBuilder()
                              .setUuid(serviceIdAsAci.toByteString())
                              .setJoinedAtRevision(member.getJoinedAtRevision())
                              .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                              .setRole(member.getRole());
      }
    }

    private DecryptedPendingMember decryptMember(PendingMember member)
        throws InvalidGroupStateException, VerificationFailedException
    {
      ByteString userIdCipherText = member.getMember().getUserId();
      ServiceId  serviceId        = decryptServiceIdOrUnknown(userIdCipherText);
      ACI        addedBy          = decryptAci(member.getAddedByUserId());

      Member.Role role = member.getMember().getRole();

      if (role != Member.Role.ADMINISTRATOR && role != Member.Role.DEFAULT) {
        role = Member.Role.DEFAULT;
      }

      return DecryptedPendingMember.newBuilder()
                                   .setServiceIdBinary(serviceId.toByteString())
                                   .setUuidCipherText(userIdCipherText)
                                   .setAddedByUuid(addedBy.toByteString())
                                   .setRole(role)
                                   .setTimestamp(member.getTimestamp())
                                   .build();
    }

    private DecryptedRequestingMember decryptRequestingMember(RequestingMember member)
        throws InvalidGroupStateException, VerificationFailedException
    {
      if (member.getPresentation().isEmpty()) {
        ACI aci = decryptAci(member.getUserId());

        return DecryptedRequestingMember.newBuilder()
                                        .setUuid(aci.toByteString())
                                        .setProfileKey(decryptProfileKeyToByteString(member.getProfileKey(), aci))
                                        .setTimestamp(member.getTimestamp())
                                        .build();
      } else {
        ProfileKeyCredentialPresentation profileKeyCredentialPresentation;
        try {
          profileKeyCredentialPresentation = new ProfileKeyCredentialPresentation(member.getPresentation().toByteArray());
        } catch (InvalidInputException e) {
          throw new InvalidGroupStateException(e);
        }

        ServiceId serviceId = ServiceId.fromLibSignal(clientZkGroupCipher.decrypt(profileKeyCredentialPresentation.getUuidCiphertext()));
        if (!(serviceId instanceof ACI)) {
          throw new InvalidGroupStateException();
        }
        ACI serviceIdAsAci = (ACI)serviceId;

        ProfileKey profileKey = clientZkGroupCipher.decryptProfileKey(profileKeyCredentialPresentation.getProfileKeyCiphertext(), serviceIdAsAci.getLibSignalAci());

        return DecryptedRequestingMember.newBuilder()
                                        .setUuid(serviceIdAsAci.toByteString())
                                        .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                                        .build();
      }
    }

    private ProfileKey decryptProfileKey(ByteString profileKey, ACI aci) throws VerificationFailedException, InvalidGroupStateException {
      try {
        ProfileKeyCiphertext profileKeyCiphertext = new ProfileKeyCiphertext(profileKey.toByteArray());
        return clientZkGroupCipher.decryptProfileKey(profileKeyCiphertext, aci.getLibSignalAci());
      } catch (InvalidInputException e) {
        throw new InvalidGroupStateException(e);
      }
    }

    private ByteString decryptProfileKeyToByteString(ByteString profileKey, ACI aci) throws VerificationFailedException, InvalidGroupStateException {
      return ByteString.copyFrom(decryptProfileKey(profileKey, aci).serialize());
    }

    private ByteString decryptServiceIdToBinary(ByteString userId) throws InvalidGroupStateException, VerificationFailedException {
      return decryptServiceId(userId).toByteString();
    }

    // Visible for Testing
    public ByteString encryptServiceId(ServiceId serviceId) {
      return ByteString.copyFrom(clientZkGroupCipher.encrypt(serviceId.getLibSignalServiceId()).serialize());
    }

    private ServiceId decryptServiceId(ByteString userId) throws InvalidGroupStateException, VerificationFailedException {
      try {
        return ServiceId.fromLibSignal(clientZkGroupCipher.decrypt(new UuidCiphertext(userId.toByteArray())));
      } catch (InvalidInputException e) {
        throw new InvalidGroupStateException(e);
      }
    }

    private ACI decryptAci(ByteString userId) throws InvalidGroupStateException, VerificationFailedException {
      ServiceId result = decryptServiceId(userId);
      if (result instanceof ACI) {
        return (ACI)result;
      }
      throw new InvalidGroupStateException();
    }

    /**
     * Attempts to decrypt a UUID, but will return an ACI of {@link #UNKNOWN_UUID} if it cannot.
     */
    private ServiceId decryptServiceIdOrUnknown(ByteString userId) {
      try {
        return ServiceId.fromLibSignal(clientZkGroupCipher.decrypt(new UuidCiphertext(userId.toByteArray())));
      } catch (InvalidInputException | VerificationFailedException e) {
        return ACI.UNKNOWN;
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

    ByteString encryptDescription(String description) {
      try {
        GroupAttributeBlob blob = GroupAttributeBlob.newBuilder().setDescription(description).build();

        return ByteString.copyFrom(clientZkGroupCipher.encryptBlob(blob.toByteArray()));
      } catch (VerificationFailedException e) {
        throw new AssertionError(e);
      }
    }

    private String decryptDescription(ByteString cipherText) {
      return decryptBlob(cipherText).getDescription().trim();
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
        Log.w(TAG, "Invalid input while verifying group change", e);
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

    public GroupChange.Actions.Builder createChangeMemberRole(ACI memberAci, Member.Role role) {
      return GroupChange.Actions.newBuilder()
                                .addModifyMemberRoles(GroupChange.Actions.ModifyMemberRoleAction.newBuilder()
                                                                         .setUserId(encryptServiceId(memberAci))
                                                                         .setRole(role));
    }

    public List<ServiceId> decryptAddMembers(List<GroupChange.Actions.AddMemberAction> addMembers) throws InvalidInputException, VerificationFailedException {
      List<ServiceId> ids = new ArrayList<>(addMembers.size());
      for (int i = 0; i < addMembers.size(); i++) {
        GroupChange.Actions.AddMemberAction addMember                        = addMembers.get(i);
        ProfileKeyCredentialPresentation    profileKeyCredentialPresentation = new ProfileKeyCredentialPresentation(addMember.getAdded().getPresentation().toByteArray());

        ids.add(ServiceId.fromLibSignal(clientZkGroupCipher.decrypt(profileKeyCredentialPresentation.getUuidCiphertext())));
      }
      return ids;
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
