package org.whispersystems.signalservice.api.groupsv2;

import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.NotarySignature;
import org.signal.libsignal.zkgroup.ServerPublicParams;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.auth.ClientZkAuthOperations;
import org.signal.libsignal.zkgroup.groups.ClientZkGroupCipher;
import org.signal.libsignal.zkgroup.groups.GroupIdentifier;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.libsignal.zkgroup.groups.ProfileKeyCiphertext;
import org.signal.libsignal.zkgroup.groups.UuidCiphertext;
import org.signal.libsignal.zkgroup.groupsend.GroupSendEndorsementsResponse;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialPresentation;
import org.signal.storageservice.storage.protos.groups.AccessControl;
import org.signal.storageservice.storage.protos.groups.MemberBanned;
import org.signal.storageservice.storage.protos.groups.Group;
import org.signal.storageservice.storage.protos.groups.GroupAttributeBlob;
import org.signal.storageservice.storage.protos.groups.GroupChange;
import org.signal.storageservice.storage.protos.groups.GroupJoinInfo;
import org.signal.storageservice.storage.protos.groups.Member;
import org.signal.storageservice.storage.protos.groups.MemberPendingProfileKey;
import org.signal.storageservice.storage.protos.groups.MemberPendingAdminApproval;
import org.signal.storageservice.storage.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.storage.protos.groups.local.DecryptedBannedMember;
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.storageservice.storage.protos.groups.local.DecryptedMember;
import org.signal.storageservice.storage.protos.groups.local.DecryptedModifyMemberLabel;
import org.signal.storageservice.storage.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.storage.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.storage.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.storage.protos.groups.local.DecryptedRequestingMember;
import org.signal.storageservice.storage.protos.groups.local.DecryptedString;
import org.signal.storageservice.storage.protos.groups.local.DecryptedTimer;
import org.signal.storageservice.storage.protos.groups.local.EnabledState;
import org.signal.core.models.ServiceId;
import org.signal.core.models.ServiceId.ACI;
import org.signal.core.models.ServiceId.PNI;
import org.signal.core.util.UuidUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import okio.ByteString;

/**
 * Contains operations to create, modify and validate groups and group changes.
 */
public final class GroupsV2Operations {

  private static final String TAG = GroupsV2Operations.class.getSimpleName();

  /** Used for undecryptable pending invites */
  public static final UUID UNKNOWN_UUID = UuidUtil.UNKNOWN_UUID;

  /** Highest change epoch this class knows now to decrypt */
  public static final int HIGHEST_KNOWN_EPOCH = 6;

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

    Group.Builder group = new Group.Builder()
                                   .version(0)
                                   .publicKey(ByteString.of(groupSecretParams.getPublicParams().serialize()))
                                   .title(groupOperations.encryptTitle(title))
                                   .disappearingMessagesTimer(groupOperations.encryptTimer(disappearingMessageTimerSeconds))
                                   .accessControl(new AccessControl.Builder()
                                                                   .attributes(AccessControl.AccessRequired.MEMBER)
                                                                   .members(AccessControl.AccessRequired.MEMBER)
                                                                   .build());

    List<Member>        groupMembers        = new ArrayList<>();
    List<MemberPendingProfileKey> groupPendingMembers = new ArrayList<>();

    groupMembers.add(groupOperations.member(self.requireExpiringProfileKeyCredential(), Member.Role.ADMINISTRATOR).build());

    for (GroupCandidate credential : members) {
      ExpiringProfileKeyCredential expiringProfileKeyCredential = credential.getExpiringProfileKeyCredential().orElse(null);

      if (expiringProfileKeyCredential != null) {
        groupMembers.add(groupOperations.member(expiringProfileKeyCredential, memberRole).build());
      } else {
        groupPendingMembers.add(groupOperations.invitee(credential.getServiceId(), memberRole).build());
      }
    }

    group.members(groupMembers)
         .membersPendingProfileKey(groupPendingMembers);

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

    public GroupOperations(GroupSecretParams groupSecretParams) {
      this.groupSecretParams   = groupSecretParams;
      this.clientZkGroupCipher = new ClientZkGroupCipher(groupSecretParams);
    }

    public GroupChange.Actions.Builder createModifyGroupTitle(final String title) {
      return new GroupChange.Actions.Builder().modifyTitle(new GroupChange.Actions.ModifyTitleAction.Builder().title(encryptTitle(title)).build());
    }

    public GroupChange.Actions.ModifyDescriptionAction.Builder createModifyGroupDescriptionAction(final String description) {
      return new GroupChange.Actions.ModifyDescriptionAction.Builder().description(encryptDescription(description));
    }

    public GroupChange.Actions.Builder createModifyGroupDescription(final String description) {
      return new GroupChange.Actions.Builder().modifyDescription(createModifyGroupDescriptionAction(description).build());
    }

    public GroupChange.Actions.Builder createModifyGroupMembershipChange(Set<GroupCandidate> membersToAdd, Set<ServiceId> bannedMembers, ACI selfAci) {
      final GroupOperations groupOperations = forGroup(groupSecretParams);

      Set<ServiceId> membersToUnban = membersToAdd.stream().map(GroupCandidate::getServiceId).filter(bannedMembers::contains).collect(Collectors.toSet());

      GroupChange.Actions.Builder actions = membersToUnban.isEmpty() ? new GroupChange.Actions.Builder()
                                                                     : createUnbanServiceIdsChange(membersToUnban);

      List<GroupChange.Actions.AddMemberAction>        addGroupMembers        = new ArrayList<>(actions.addMembers);
      List<GroupChange.Actions.AddMemberPendingProfileKeyAction> addGroupPendingMembers = new ArrayList<>(actions.addMembersPendingProfileKey);
      for (GroupCandidate credential : membersToAdd) {
        Member.Role                  newMemberRole                = Member.Role.DEFAULT;
        ExpiringProfileKeyCredential expiringProfileKeyCredential = credential.getExpiringProfileKeyCredential().orElse(null);

        if (expiringProfileKeyCredential != null) {
          addGroupMembers.add(new GroupChange.Actions.AddMemberAction.Builder().added(groupOperations.member(expiringProfileKeyCredential, newMemberRole).build()).build());
        } else {
          addGroupPendingMembers.add(new GroupChange.Actions.AddMemberPendingProfileKeyAction.Builder().added(groupOperations.invitee(credential.getServiceId(), newMemberRole)
                                                                                                                   .addedByUserId(encryptServiceId(selfAci))
                                                                                                                   .build())
                                                                                             .build());
        }
      }

      return actions.addMembers(addGroupMembers)
                    .addMembersPendingProfileKey(addGroupPendingMembers);
    }

    public GroupChange.Actions.Builder createGroupJoinRequest(ExpiringProfileKeyCredential expiringProfileKeyCredential) {
      GroupOperations             groupOperations = forGroup(groupSecretParams);
      GroupChange.Actions.Builder actions         = new GroupChange.Actions.Builder();

      actions.addMembersPendingAdminApproval = Collections.singletonList(new GroupChange.Actions.AddMemberPendingAdminApprovalAction.Builder().added(groupOperations.requestingMember(expiringProfileKeyCredential).build()).build());

      return actions;
    }

    public GroupChange.Actions.Builder createGroupJoinDirect(ExpiringProfileKeyCredential expiringProfileKeyCredential) {
      GroupOperations             groupOperations = forGroup(groupSecretParams);
      GroupChange.Actions.Builder actions         = new GroupChange.Actions.Builder();

      actions.addMembers = Collections.singletonList(new GroupChange.Actions.AddMemberAction.Builder().added(groupOperations.member(expiringProfileKeyCredential, Member.Role.DEFAULT).build()).build());

      return actions;
    }

    public GroupChange.Actions.Builder createRefuseGroupJoinRequest(Set<? extends ServiceId> requestsToRemove, boolean alsoBan, List<DecryptedBannedMember> bannedMembers) {
      GroupChange.Actions.Builder actions = alsoBan ? createBanServiceIdsChange(requestsToRemove, false, bannedMembers)
                                                    : new GroupChange.Actions.Builder();

      List<GroupChange.Actions.DeleteMemberPendingAdminApprovalAction> DeleteMemberPendingAdminApprovalActions = new ArrayList<>(actions.deleteMembersPendingAdminApproval);
      for (ServiceId serviceId : requestsToRemove) {
        if (serviceId instanceof ACI) {
          DeleteMemberPendingAdminApprovalActions.add(new GroupChange.Actions.DeleteMemberPendingAdminApprovalAction.Builder().deletedUserId(encryptServiceId(serviceId)).build());
        }
      }
      return actions.deleteMembersPendingAdminApproval(DeleteMemberPendingAdminApprovalActions);
    }

    public GroupChange.Actions.Builder createApproveGroupJoinRequest(Set<UUID> requestsToApprove) {
      GroupChange.Actions.Builder actions = new GroupChange.Actions.Builder();

      actions.promoteMembersPendingAdminApproval = requestsToApprove.stream()
                                                          .map(uuid -> new GroupChange.Actions.PromoteMemberPendingAdminApprovalAction.Builder().role(Member.Role.DEFAULT)
                                                                                                                                      .userId(encryptServiceId(ACI.from(uuid)))
                                                                                                                                      .build())
                                                          .collect(Collectors.toList());

      return actions;
    }

    public GroupChange.Actions.Builder createRemoveMembersChange(final Set<ACI> membersToRemove, boolean alsoBan, List<DecryptedBannedMember> bannedMembers) {
      GroupChange.Actions.Builder actions = alsoBan ? createBanServiceIdsChange(membersToRemove, false, bannedMembers)
                                                    : new GroupChange.Actions.Builder();

      List<GroupChange.Actions.DeleteMemberAction> deleteMemberActions = new ArrayList<>(actions.deleteMembers);
      for (ACI remove: membersToRemove) {
        deleteMemberActions.add(new GroupChange.Actions.DeleteMemberAction.Builder().deletedUserId(encryptServiceId(remove)).build());
      }
      return actions.deleteMembers(deleteMemberActions);
    }

    public GroupChange.Actions.Builder createLeaveAndPromoteMembersToAdmin(ACI self, List<UUID> membersToMakeAdmin) {
      GroupChange.Actions.Builder actions = createRemoveMembersChange(Collections.singleton(self), false, Collections.emptyList());

      List<GroupChange.Actions.ModifyMemberRoleAction> modifyMemberRoleActions = new ArrayList<>(actions.modifyMemberRoles);
      for (UUID member : membersToMakeAdmin) {
        modifyMemberRoleActions.add(new GroupChange.Actions.ModifyMemberRoleAction.Builder().userId(encryptServiceId(ACI.from(member))).role(Member.Role.ADMINISTRATOR).build());
      }
      return actions.modifyMemberRoles(modifyMemberRoleActions);
    }

    public GroupChange.Actions.Builder createModifyGroupTimerChange(int timerDurationSeconds) {
      return new GroupChange.Actions.Builder()
          .modifyDisappearingMessageTimer(new GroupChange.Actions.ModifyDisappearingMessageTimerAction.Builder().timer(encryptTimer(timerDurationSeconds)).build());
    }

    public GroupChange.Actions.Builder createUpdateProfileKeyCredentialChange(ExpiringProfileKeyCredential expiringProfileKeyCredential) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(random, groupSecretParams, expiringProfileKeyCredential);

      return new GroupChange.Actions.Builder().modifyMemberProfileKeys(Collections.singletonList(
          new GroupChange.Actions.ModifyMemberProfileKeyAction.Builder()
              .presentation(ByteString.of(presentation.serialize()))
              .build()
      ));
    }

    public GroupChange.Actions.Builder createAcceptInviteChange(ExpiringProfileKeyCredential credential) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(random, groupSecretParams, credential);

      return new GroupChange.Actions.Builder().promoteMembersPendingProfileKey(Collections.singletonList(
          new GroupChange.Actions.PromoteMemberPendingProfileKeyAction.Builder()
              .presentation(ByteString.of(presentation.serialize()))
              .build()
      ));
    }

    public GroupChange.Actions.Builder createAcceptPniInviteChange(ExpiringProfileKeyCredential credential) {
      ByteString presentation = ByteString.of(clientZkProfileOperations.createProfileKeyCredentialPresentation(random, groupSecretParams, credential).serialize());

      return new GroupChange.Actions.Builder().promote_members_pending_pni_aci_profile_key(Collections.singletonList(
          new GroupChange.Actions.PromoteMemberPendingPniAciProfileKeyAction.Builder().presentation(presentation).build()
      ));
    }

    public GroupChange.Actions.Builder createRemoveInvitationChange(final Set<UuidCiphertext> uuidCipherTextsFromInvitesToRemove) {
      GroupChange.Actions.Builder builder = new GroupChange.Actions.Builder();

      builder.deleteMembersPendingProfileKey = uuidCipherTextsFromInvitesToRemove.stream()
                                                                       .map(uuidCipherText -> new GroupChange.Actions.DeleteMemberPendingProfileKeyAction.Builder().deletedUserId(ByteString.of(uuidCipherText.serialize()))
                                                                                                                                                         .build())
                                                                       .collect(Collectors.toList());

      return builder;
    }

    public GroupChange.Actions.Builder createModifyGroupLinkPasswordChange(byte[] groupLinkPassword) {
      return new GroupChange.Actions.Builder().modifyInviteLinkPassword(
          new GroupChange.Actions.ModifyInviteLinkPasswordAction.Builder().inviteLinkPassword(ByteString.of(groupLinkPassword)).build()
      );
    }

    public GroupChange.Actions.Builder createModifyGroupLinkPasswordAndRightsChange(byte[] groupLinkPassword, AccessControl.AccessRequired newRights) {
      GroupChange.Actions.Builder change = createModifyGroupLinkPasswordChange(groupLinkPassword);

      return change.modifyAddFromInviteLinkAccess(new GroupChange.Actions.ModifyAddFromInviteLinkAccessControlAction.Builder().addFromInviteLinkAccess(newRights).build());
    }

    public GroupChange.Actions.Builder createChangeJoinByLinkRights(AccessControl.AccessRequired newRights) {
      return new GroupChange.Actions.Builder().modifyAddFromInviteLinkAccess(new GroupChange.Actions.ModifyAddFromInviteLinkAccessControlAction.Builder().addFromInviteLinkAccess(newRights).build());
    }

    public GroupChange.Actions.Builder createChangeMembershipRights(AccessControl.AccessRequired newRights) {
      return new GroupChange.Actions.Builder().modifyMemberAccess(
          new GroupChange.Actions.ModifyMembersAccessControlAction.Builder().membersAccess(newRights).build()
      );
    }

    public GroupChange.Actions.Builder createChangeAttributesRights(AccessControl.AccessRequired newRights) {
      return new GroupChange.Actions.Builder().modifyAttributesAccess(
          new GroupChange.Actions.ModifyAttributesAccessControlAction.Builder().attributesAccess(newRights).build()
      );
    }

    public GroupChange.Actions.Builder createAnnouncementGroupChange(boolean isAnnouncementGroup) {
      return new GroupChange.Actions.Builder().modify_announcements_only(
          new GroupChange.Actions.ModifyAnnouncementsOnlyAction.Builder().announcements_only(isAnnouncementGroup).build()
      );
    }

    /** Note that this can only ban ACIs. */
    public GroupChange.Actions.Builder createBanServiceIdsChange(Set<? extends ServiceId> banServiceIds, boolean rejectJoinRequest, List<DecryptedBannedMember> bannedMembersList) {
      GroupChange.Actions.Builder builder = rejectJoinRequest ? createRefuseGroupJoinRequest(banServiceIds, false, Collections.emptyList())
                                                              : new GroupChange.Actions.Builder();

      int spacesToFree = bannedMembersList.size() + banServiceIds.size() - maxGroupSize;
      if (spacesToFree > 0) {
        List<ByteString> unban = bannedMembersList.stream()
                                                  .sorted(Comparator.comparingLong(m -> m.timestamp))
                                                  .limit(spacesToFree)
                                                  .map(m -> m.serviceIdBytes)
                                                  .collect(Collectors.toList());

        List<GroupChange.Actions.DeleteMemberBannedAction> deleteBannedMemberActions = new ArrayList<>(builder.delete_members_banned);
        for (ByteString serviceIdBinary : unban) {
          deleteBannedMemberActions.add(new GroupChange.Actions.DeleteMemberBannedAction.Builder().deletedUserId(encryptServiceId(ServiceId.parseOrThrow(serviceIdBinary.toByteArray()))).build());
        }
        builder.delete_members_banned(deleteBannedMemberActions);
      }

      List<GroupChange.Actions.AddMemberBannedAction> AddMemberBannedActions = new ArrayList<>(builder.add_members_banned);
      for (ServiceId banServiceId : banServiceIds) {
        AddMemberBannedActions.add(new GroupChange.Actions.AddMemberBannedAction.Builder().added(new MemberBanned.Builder().userId(encryptServiceId(banServiceId)).build()).build());
      }
      builder.add_members_banned(AddMemberBannedActions);

      return builder;
    }

    public GroupChange.Actions.Builder createUnbanServiceIdsChange(Set<ServiceId> serviceIds) {
      GroupChange.Actions.Builder builder = new GroupChange.Actions.Builder();

      builder.delete_members_banned = serviceIds.stream()
                                              .map(serviceId -> new GroupChange.Actions.DeleteMemberBannedAction.Builder().deletedUserId(encryptServiceId(serviceId)).build())
                                              .collect(Collectors.toList());

      return builder;
    }

    public GroupChange.Actions.Builder replaceAddMembers(GroupChange.Actions.Builder change, List<GroupCandidate> candidates) throws InvalidInputException {
      if (change.addMembers.size() != candidates.size()) {
        throw new InvalidInputException("Replacement candidates not same size as original add");
      }

      List<GroupChange.Actions.AddMemberAction> addMemberActions = new ArrayList<>(change.addMembers);
      for (int i = 0; i < addMemberActions.size(); i++) {
        GroupChange.Actions.AddMemberAction original  = addMemberActions.get(i);
        GroupCandidate                      candidate = candidates.get(i);

        ExpiringProfileKeyCredential expiringProfileKeyCredential = candidate.getExpiringProfileKeyCredential().orElse(null);

        if (expiringProfileKeyCredential == null) {
          throw new InvalidInputException("Replacement candidate missing credential");
        }

        addMemberActions.set(i, new GroupChange.Actions.AddMemberAction.Builder().added(member(expiringProfileKeyCredential, original.added.role).build()).build());
      }

      return change.addMembers(addMemberActions);
    }

    private Member.Builder member(ExpiringProfileKeyCredential credential, Member.Role role) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(new SecureRandom(), groupSecretParams, credential);

      return new Member.Builder().role(role)
                                 .presentation(ByteString.of(presentation.serialize()));
    }

    private MemberPendingAdminApproval.Builder requestingMember(ExpiringProfileKeyCredential credential) {
      ProfileKeyCredentialPresentation presentation = clientZkProfileOperations.createProfileKeyCredentialPresentation(new SecureRandom(), groupSecretParams, credential);

      return new MemberPendingAdminApproval.Builder().presentation(ByteString.of(presentation.serialize()));
    }

    public MemberPendingProfileKey.Builder invitee(ServiceId serviceId, Member.Role role) {
      UuidCiphertext uuidCiphertext = clientZkGroupCipher.encrypt(serviceId.getLibSignalServiceId());

      Member member = new Member.Builder().role(role)
                                          .userId(ByteString.of(uuidCiphertext.serialize()))
                                          .build();

      return new MemberPendingProfileKey.Builder().member(member);
    }

    public @Nonnull DecryptedGroupResponse decryptGroup(@Nonnull Group group, @Nonnull byte[] groupSendEndorsementsBytes)
        throws VerificationFailedException, InvalidGroupStateException, InvalidInputException
    {
      DecryptedGroup                decryptedGroup                = decryptGroup(group);
      GroupSendEndorsementsResponse groupSendEndorsementsResponse = groupSendEndorsementsBytes.length > 0 ? new GroupSendEndorsementsResponse(groupSendEndorsementsBytes) : null;

      return new DecryptedGroupResponse(decryptedGroup, groupSendEndorsementsResponse);
    }

    public DecryptedGroup decryptGroup(Group group)
        throws VerificationFailedException, InvalidGroupStateException
    {
      List<Member>                     membersList                = group.members;
      List<MemberPendingProfileKey>    pendingMembersList         = group.membersPendingProfileKey;
      List<MemberPendingAdminApproval> requestingMembersList      = group.membersPendingAdminApproval;
      List<DecryptedMember>            decryptedMembers           = new ArrayList<>(membersList.size());
      List<DecryptedPendingMember>     decryptedPendingMembers    = new ArrayList<>(pendingMembersList.size());
      List<DecryptedRequestingMember>  decryptedRequestingMembers = new ArrayList<>(requestingMembersList.size());
      List<DecryptedBannedMember>      decryptedBannedMembers     = new ArrayList<>(group.members_banned.size());

      for (Member member : membersList) {
        try {
          decryptedMembers.add(decryptMember(member).build());
        } catch (InvalidInputException e) {
          throw new InvalidGroupStateException(e);
        }
      }

      for (MemberPendingProfileKey member : pendingMembersList) {
        decryptedPendingMembers.add(decryptMember(member));
      }

      for (MemberPendingAdminApproval member : requestingMembersList) {
        decryptedRequestingMembers.add(decryptRequestingMember(member));
      }

      for (MemberBanned member : group.members_banned) {
        decryptedBannedMembers.add(new DecryptedBannedMember.Builder().serviceIdBytes(decryptServiceIdToBinary(member.userId)).timestamp(member.timestamp).build());
      }

      return new DecryptedGroup.Builder()
                               .title(decryptTitle(group.title))
                               .description(decryptDescription(group.description))
                               .isAnnouncementGroup(group.announcements_only ? EnabledState.ENABLED : EnabledState.DISABLED)
                               .avatar(group.avatarUrl)
                               .accessControl(group.accessControl)
                               .revision(group.version)
                               .members(decryptedMembers)
                               .pendingMembers(decryptedPendingMembers)
                               .requestingMembers(decryptedRequestingMembers)
                               .disappearingMessagesTimer(new DecryptedTimer.Builder().duration(decryptDisappearingMessagesTimer(group.disappearingMessagesTimer)).build())
                               .inviteLinkPassword(group.inviteLinkPassword)
                               .bannedMembers(decryptedBannedMembers)
                               .build();
    }

    /**
     * @param verification You might want to avoid verification if you already know it's correct, or you are not going to pass to other clients.
     *                     <p>
     *                     Also, if you know it's version 0, do not verify because changes for version 0 are not signed, but should be empty.
     *
     * @return {@link Optional#empty()} if the epoch for the change is higher that this code can decrypt.
     */
    public Optional<DecryptedGroupChange> decryptChange(GroupChange groupChange, @Nonnull DecryptChangeVerificationMode verification)
        throws IOException, VerificationFailedException, InvalidGroupStateException
    {
      if (groupChange.changeEpoch > HIGHEST_KNOWN_EPOCH) {
        Log.w(TAG, String.format(Locale.US, "Ignoring change from Epoch %d. Highest known Epoch is %d", groupChange.changeEpoch, HIGHEST_KNOWN_EPOCH));
        return Optional.empty();
      }

      GroupChange.Actions actions = verification.verify() ? getVerifiedActions(groupChange) : getActions(groupChange);

      if (verification.verify()) {
        GroupIdentifier groupId = verification.groupId();
        if (groupId == null || !Arrays.equals(groupId.serialize(), actions.group_id.toByteArray())) {
           throw new VerificationFailedException("Invalid group id");
        }
      }

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
      DecryptedGroupChange.Builder builder = new DecryptedGroupChange.Builder();
      ServiceId                    editorServiceId;

      // Field 1
      if (source != null) {
        editorServiceId = source;
      } else {
        editorServiceId = decryptServiceId(actions.sourceUserId);
      }
      builder.editorServiceIdBytes(editorServiceId.toByteString());

      // Field 2
      builder.revision(actions.version);

      // Field 3
      List<DecryptedMember> newMembers = new ArrayList<>(actions.addMembers.size());
      for (GroupChange.Actions.AddMemberAction addMemberAction : actions.addMembers) {
        try {
          newMembers.add(decryptMember(addMemberAction.added).joinedAtRevision(actions.version).build());
        } catch (InvalidInputException e) {
          throw new InvalidGroupStateException(e);
        }
      }
      builder.newMembers(newMembers);

      // Field 4
      List<ByteString> deleteMembers = new ArrayList<>(actions.deleteMembers.size());
      for (GroupChange.Actions.DeleteMemberAction deleteMemberAction : actions.deleteMembers) {
        deleteMembers.add(decryptAciToBinary(deleteMemberAction.deletedUserId));
      }
      builder.deleteMembers(deleteMembers);

      // Field 5
      List<DecryptedModifyMemberRole> modifyMemberRoles = new ArrayList<>(actions.modifyMemberRoles.size());
      for (GroupChange.Actions.ModifyMemberRoleAction modifyMemberRoleAction : actions.modifyMemberRoles) {
        modifyMemberRoles.add(new DecryptedModifyMemberRole.Builder().role(modifyMemberRoleAction.role)
                                                                     .aciBytes(decryptAciToBinary(modifyMemberRoleAction.userId))
                                                                     .build());
      }
      builder.modifyMemberRoles(modifyMemberRoles);

      // Field 6
      List<DecryptedMember> modifiedProfileKeys = new ArrayList<>(actions.modifyMemberProfileKeys.size());
      for (GroupChange.Actions.ModifyMemberProfileKeyAction modifyMemberProfileKeyAction : actions.modifyMemberProfileKeys) {
        try {
          ACI        aci;
          ProfileKey profileKey;

          if (modifyMemberProfileKeyAction.user_id.size() == 0 || modifyMemberProfileKeyAction.profile_key.size() == 0) {
            ProfileKeyCredentialPresentation presentation = new ProfileKeyCredentialPresentation(modifyMemberProfileKeyAction.presentation.toByteArray());
            aci        = decryptAci(ByteString.of(presentation.getUuidCiphertext().serialize()));
            profileKey = decryptProfileKey(ByteString.of(presentation.getProfileKeyCiphertext().serialize()), aci);
          } else {
            aci        = decryptAci(modifyMemberProfileKeyAction.user_id);
            profileKey = decryptProfileKey(modifyMemberProfileKeyAction.profile_key, aci);
          }

          modifiedProfileKeys.add(new DecryptedMember.Builder()
                                                     .role(Member.Role.UNKNOWN)
                                                     .joinedAtRevision(-1)
                                                     .aciBytes(aci.toByteString())
                                                     .profileKey(ByteString.of(profileKey.serialize()))
                                                     .build());
        } catch (InvalidInputException e) {
          throw new InvalidGroupStateException(e);
        }
      }
      builder.modifiedProfileKeys(modifiedProfileKeys);

      // Field 7
      List<DecryptedPendingMember> newPendingMembers = new ArrayList<>(actions.addMembersPendingProfileKey.size());
      for (GroupChange.Actions.AddMemberPendingProfileKeyAction AddMemberPendingProfileKeyAction : actions.addMembersPendingProfileKey) {
        MemberPendingProfileKey added               = AddMemberPendingProfileKeyAction.added;
        Member        member              = added.member;
        ByteString    serviceIdCipherText = member.userId;
        ServiceId     serviceId           = decryptServiceIdOrUnknown(serviceIdCipherText);

        newPendingMembers.add(new DecryptedPendingMember.Builder()
                                                        .serviceIdBytes(serviceId.toByteString())
                                                        .serviceIdCipherText(serviceIdCipherText)
                                                        .role(member.role)
                                                        .addedByAci(decryptAciToBinary(added.addedByUserId))
                                                        .timestamp(added.timestamp)
                                                        .build());
      }
      builder.newPendingMembers(newPendingMembers);

      // Field 8
      List<DecryptedPendingMemberRemoval> deletePendingMembers = new ArrayList<>(actions.deleteMembersPendingProfileKey.size());
      for (GroupChange.Actions.DeleteMemberPendingProfileKeyAction DeleteMemberPendingProfileKeyAction : actions.deleteMembersPendingProfileKey) {
        ByteString serviceIdCipherText = DeleteMemberPendingProfileKeyAction.deletedUserId;
        ServiceId  serviceId           = decryptServiceIdOrUnknown(serviceIdCipherText);

        deletePendingMembers.add(new DecryptedPendingMemberRemoval.Builder()
                                                                  .serviceIdBytes(serviceId.toByteString())
                                                                  .serviceIdCipherText(serviceIdCipherText)
                                                                  .build());
      }
      builder.deletePendingMembers(deletePendingMembers);

      // Field 9
      List<DecryptedMember> promotePendingMembers = new ArrayList<>(actions.promoteMembersPendingProfileKey.size());
      for (GroupChange.Actions.PromoteMemberPendingProfileKeyAction PromoteMemberPendingProfileKeyAction : actions.promoteMembersPendingProfileKey) {
        try {
          ACI        aci;
          ProfileKey profileKey;

          if (PromoteMemberPendingProfileKeyAction.user_id.size() == 0 || PromoteMemberPendingProfileKeyAction.profile_key.size() == 0) {
            ProfileKeyCredentialPresentation presentation = new ProfileKeyCredentialPresentation(PromoteMemberPendingProfileKeyAction.presentation.toByteArray());
            aci        = decryptAci(ByteString.of(presentation.getUuidCiphertext().serialize()));
            profileKey = decryptProfileKey(ByteString.of(presentation.getProfileKeyCiphertext().serialize()), aci);
          } else {
            aci        = decryptAci(PromoteMemberPendingProfileKeyAction.user_id);
            profileKey = decryptProfileKey(PromoteMemberPendingProfileKeyAction.profile_key, aci);
          }

          promotePendingMembers.add(new DecryptedMember.Builder()
                                                       .joinedAtRevision(-1)
                                                       .role(Member.Role.DEFAULT)
                                                       .aciBytes(aci.toByteString())
                                                       .profileKey(ByteString.of(profileKey.serialize()))
                                                       .build());
        } catch (InvalidInputException e) {
          throw new InvalidGroupStateException(e);
        }
      }
      builder.promotePendingMembers(promotePendingMembers);

      // Field 10
      if (actions.modifyTitle != null) {
        builder.newTitle(new DecryptedString.Builder().value_(decryptTitle(actions.modifyTitle.title)).build());
      }

      // Field 11
      if (actions.modifyAvatar != null) {
        builder.newAvatar(new DecryptedString.Builder().value_(actions.modifyAvatar.avatar).build());
      }

      // Field 12
      if (actions.modifyDisappearingMessageTimer != null) {
        int duration = decryptDisappearingMessagesTimer(actions.modifyDisappearingMessageTimer.timer);
        builder.newTimer(new DecryptedTimer.Builder().duration(duration).build());
      }

      // Field 13
      if (actions.modifyAttributesAccess != null) {
        builder.newAttributeAccess(actions.modifyAttributesAccess.attributesAccess);
      }

      // Field 14
      if (actions.modifyMemberAccess != null) {
        builder.newMemberAccess(actions.modifyMemberAccess.membersAccess);
      }

      // Field 15
      if (actions.modifyAddFromInviteLinkAccess != null) {
        builder.newInviteLinkAccess(actions.modifyAddFromInviteLinkAccess.addFromInviteLinkAccess);
      }

      // Field 16
      List<DecryptedRequestingMember> newRequestingMembers = new ArrayList<>(actions.addMembersPendingAdminApproval.size());
      for (GroupChange.Actions.AddMemberPendingAdminApprovalAction request : actions.addMembersPendingAdminApproval) {
        newRequestingMembers.add(decryptRequestingMember(request.added));
      }
      builder.newRequestingMembers(newRequestingMembers);

      // Field 17
      List<ByteString> deleteRequestingMembers = new ArrayList<>(actions.deleteMembersPendingAdminApproval.size());
      for (GroupChange.Actions.DeleteMemberPendingAdminApprovalAction delete : actions.deleteMembersPendingAdminApproval) {
        deleteRequestingMembers.add(decryptServiceIdToBinary(delete.deletedUserId));
      }
      builder.deleteRequestingMembers(deleteRequestingMembers);

      // Field 18
      List<DecryptedApproveMember> promoteRequestingMembers = new ArrayList<>(actions.promoteMembersPendingAdminApproval.size());
      for (GroupChange.Actions.PromoteMemberPendingAdminApprovalAction promote : actions.promoteMembersPendingAdminApproval) {
        promoteRequestingMembers.add(new DecryptedApproveMember.Builder().role(promote.role).aciBytes(decryptAciToBinary(promote.userId)).build());
      }
      builder.promoteRequestingMembers(promoteRequestingMembers);

      // Field 19
      if (actions.modifyInviteLinkPassword != null) {
        builder.newInviteLinkPassword(actions.modifyInviteLinkPassword.inviteLinkPassword);
      }

      // Field 20
      if (actions.modifyDescription != null) {
        builder.newDescription(new DecryptedString.Builder().value_(decryptDescription(actions.modifyDescription.description)).build());
      }

      // Field 21
      if (actions.modify_announcements_only != null) {
        builder.newIsAnnouncementGroup(actions.modify_announcements_only.announcements_only ? EnabledState.ENABLED : EnabledState.DISABLED);
      }

      // Field 22
      List<DecryptedBannedMember> newBannedMembers = new ArrayList<>(actions.add_members_banned.size());
      for (GroupChange.Actions.AddMemberBannedAction action : actions.add_members_banned) {
        newBannedMembers.add(new DecryptedBannedMember.Builder().serviceIdBytes(decryptServiceIdToBinary(action.added.userId)).timestamp(action.added.timestamp).build());
      }
      builder.newBannedMembers(newBannedMembers);

      // Field 23
      List<DecryptedBannedMember> deleteBannedMembers = new ArrayList<>(actions.delete_members_banned.size());
      for (GroupChange.Actions.DeleteMemberBannedAction action : actions.delete_members_banned) {
        deleteBannedMembers.add(new DecryptedBannedMember.Builder().serviceIdBytes(decryptServiceIdToBinary(action.deletedUserId)).build());
      }
      builder.deleteBannedMembers(deleteBannedMembers);

      // Field 24
      List<DecryptedMember> promotePendingPniAciMembers = new ArrayList<>(actions.promote_members_pending_pni_aci_profile_key.size());
      for (GroupChange.Actions.PromoteMemberPendingPniAciProfileKeyAction promotePendingPniAciMemberAction : actions.promote_members_pending_pni_aci_profile_key) {
        ACI        aci        = decryptAci(promotePendingPniAciMemberAction.user_id);
        ServiceId  pni        = decryptServiceId(promotePendingPniAciMemberAction.pni);
        ProfileKey profileKey = decryptProfileKey(promotePendingPniAciMemberAction.profile_key, aci);

        if (!(pni instanceof PNI)) {
          throw new InvalidGroupStateException();
        }

        builder.editorServiceIdBytes(aci.toByteString());
        promotePendingPniAciMembers.add(new DecryptedMember.Builder()
                                                           .aciBytes(aci.toByteString())
                                                           .role(Member.Role.DEFAULT)
                                                           .profileKey(ByteString.of(profileKey.serialize()))
                                                           .joinedAtRevision(actions.version)
                                                           .pniBytes(pni.toByteString())
                                                           .build());
      }
      builder.promotePendingPniAciMembers(promotePendingPniAciMembers);

      // Field 26
      List<DecryptedModifyMemberLabel> modifyMemberLabels = new ArrayList<>(actions.modifyMemberLabel.size());
      for (GroupChange.Actions.ModifyMemberLabelAction action : actions.modifyMemberLabel) {
        modifyMemberLabels.add(
            new DecryptedModifyMemberLabel.Builder()
                .aciBytes(decryptAciToBinary(action.userId))
                .labelEmoji(Objects.requireNonNullElse(decryptString(action.labelEmoji), ""))
                .labelString(Objects.requireNonNullElse(decryptString(action.labelString), ""))
                .build()
        );
      }
      builder.modifyMemberLabel(modifyMemberLabels);

      if (editorServiceId instanceof ServiceId.PNI) {
        if (actions.addMembers.size() == 1 && builder.newMembers.size() == 1) {
          GroupChange.Actions.AddMemberAction addMemberAction = actions.addMembers.get(0);
          DecryptedMember                     decryptedMember = builder.newMembers.get(0);

          if (addMemberAction.joinFromInviteLink) {
            Log.d(TAG, "Replacing PNI editor with ACI for buggy join from invite link");
            builder.editorServiceIdBytes(decryptedMember.aciBytes);
          } else {
            Log.w(TAG, "Unable to replace PNI editor with ACI for add member update");
            builder.editorServiceIdBytes(ByteString.EMPTY);
          }
        } else if (actions.deleteMembersPendingProfileKey.isEmpty() && actions.promote_members_pending_pni_aci_profile_key.isEmpty()) {
          Log.w(TAG, "Received group change with PNI editor for a non-PNI editor eligible update, clearing editor");
          builder.editorServiceIdBytes(ByteString.EMPTY);
        }
      }

      return builder.build();
    }

    public DecryptedGroupJoinInfo decryptGroupJoinInfo(GroupJoinInfo joinInfo) {
      return new DecryptedGroupJoinInfo.Builder()
                                       .title(decryptTitle(joinInfo.title))
                                       .avatar(joinInfo.avatar)
                                       .memberCount(joinInfo.memberCount)
                                       .addFromInviteLink(joinInfo.addFromInviteLink)
                                       .revision(joinInfo.version)
                                       .pendingAdminApproval(joinInfo.pendingAdminApproval)
                                       .description(decryptDescription(joinInfo.description))
                                       .build();
    }

    private DecryptedMember.Builder decryptMember(Member member)
        throws InvalidGroupStateException, VerificationFailedException, InvalidInputException
    {
      String labelEmoji  = Objects.requireNonNullElse(decryptString(member.labelEmoji), "");
      String labelString = Objects.requireNonNullElse(decryptString(member.labelString), "");

      if (member.presentation.size() == 0) {
        ACI aci = decryptAci(member.userId);

        return new DecryptedMember.Builder()
            .aciBytes(aci.toByteString())
            .joinedAtRevision(member.joinedAtVersion)
            .profileKey(decryptProfileKeyToByteString(member.profileKey, aci))
            .role(member.role)
            .labelEmoji(labelEmoji)
            .labelString(labelString);
      } else {
        ProfileKeyCredentialPresentation profileKeyCredentialPresentation = new ProfileKeyCredentialPresentation(member.presentation.toByteArray());

        ServiceId serviceId = ServiceId.fromLibSignal(clientZkGroupCipher.decrypt(profileKeyCredentialPresentation.getUuidCiphertext()));
        if (!(serviceId instanceof ACI)) {
          throw new InvalidGroupStateException();
        }
        ACI aci = (ACI) serviceId;

        ProfileKey profileKey = clientZkGroupCipher.decryptProfileKey(profileKeyCredentialPresentation.getProfileKeyCiphertext(), aci.getLibSignalAci());

        return new DecryptedMember.Builder()
            .aciBytes(aci.toByteString())
            .joinedAtRevision(member.joinedAtVersion)
            .profileKey(ByteString.of(profileKey.serialize()))
            .role(member.role)
            .labelEmoji(labelEmoji)
            .labelString(labelString);
      }
    }

    private DecryptedPendingMember decryptMember(MemberPendingProfileKey member)
        throws InvalidGroupStateException, VerificationFailedException
    {
      ByteString userIdCipherText = member.member.userId;
      ServiceId  serviceId        = decryptServiceIdOrUnknown(userIdCipherText);
      ACI        addedBy          = decryptAci(member.addedByUserId);

      Member.Role role = member.member.role;

      if (role != Member.Role.ADMINISTRATOR && role != Member.Role.DEFAULT) {
        role = Member.Role.DEFAULT;
      }

      return new DecryptedPendingMember.Builder()
                                       .serviceIdBytes(serviceId.toByteString())
                                       .serviceIdCipherText(userIdCipherText)
                                       .addedByAci(addedBy.toByteString())
                                       .role(role)
                                       .timestamp(member.timestamp)
                                       .build();
    }

    private DecryptedRequestingMember decryptRequestingMember(MemberPendingAdminApproval member)
        throws InvalidGroupStateException, VerificationFailedException
    {
      if (member.presentation.size() == 0) {
        ACI aci = decryptAci(member.userId);

        return new DecryptedRequestingMember.Builder()
                                            .aciBytes(aci.toByteString())
                                            .profileKey(decryptProfileKeyToByteString(member.profileKey, aci))
                                            .timestamp(member.timestamp)
                                            .build();
      } else {
        ProfileKeyCredentialPresentation profileKeyCredentialPresentation;
        try {
          profileKeyCredentialPresentation = new ProfileKeyCredentialPresentation(member.presentation.toByteArray());
        } catch (InvalidInputException e) {
          throw new InvalidGroupStateException(e);
        }

        ServiceId serviceId = ServiceId.fromLibSignal(clientZkGroupCipher.decrypt(profileKeyCredentialPresentation.getUuidCiphertext()));
        if (!(serviceId instanceof ACI)) {
          throw new InvalidGroupStateException();
        }
        ACI aci = (ACI) serviceId;

        ProfileKey profileKey = clientZkGroupCipher.decryptProfileKey(profileKeyCredentialPresentation.getProfileKeyCiphertext(), aci.getLibSignalAci());

        return new DecryptedRequestingMember.Builder()
                                            .aciBytes(aci.toByteString())
                                            .profileKey(ByteString.of(profileKey.serialize()))
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
      return ByteString.of(decryptProfileKey(profileKey, aci).serialize());
    }

    private ByteString decryptServiceIdToBinary(ByteString userId) throws InvalidGroupStateException, VerificationFailedException {
      return decryptServiceId(userId).toByteString();
    }

    private ByteString decryptAciToBinary(ByteString userId) throws InvalidGroupStateException, VerificationFailedException {
      return decryptAci(userId).toByteString();
    }

    // Visible for Testing
    public ByteString encryptServiceId(ServiceId serviceId) {
      return ByteString.of(clientZkGroupCipher.encrypt(serviceId.getLibSignalServiceId()).serialize());
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
        return (ACI) result;
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

    private ACI decryptAciOrUnknown(ByteString userId) {
      try {
        ServiceId result = ServiceId.fromLibSignal(clientZkGroupCipher.decrypt(new UuidCiphertext(userId.toByteArray())));
        if (result instanceof ACI) {
          return (ACI) result;
        } else {
          return ACI.UNKNOWN;
        }
      } catch (InvalidInputException | VerificationFailedException e) {
        return ACI.UNKNOWN;
      }
    }

    ByteString encryptTitle(String title) {
      try {
        GroupAttributeBlob blob = new GroupAttributeBlob.Builder().title(title).build();

        return ByteString.of(clientZkGroupCipher.encryptBlob(blob.encode()));
      } catch (VerificationFailedException e) {
        throw new AssertionError(e);
      }
    }

    private String decryptTitle(ByteString cipherText) {
      String title = decryptBlob(cipherText).title;
      return title != null ? title.trim() : "";
    }

    ByteString encryptDescription(String description) {
      try {
        GroupAttributeBlob blob = new GroupAttributeBlob.Builder().descriptionText(description).build();

        return ByteString.of(clientZkGroupCipher.encryptBlob(blob.encode()));
      } catch (VerificationFailedException e) {
        throw new AssertionError(e);
      }
    }

    private String decryptDescription(ByteString cipherText) {
      String description = decryptBlob(cipherText).descriptionText;
      return description != null ? description.trim() : "";
    }

    private int decryptDisappearingMessagesTimer(ByteString encryptedTimerMessage) {
      Integer disappearingMessagesDuration = decryptBlob(encryptedTimerMessage).disappearingMessagesDuration;
      return disappearingMessagesDuration != null ? disappearingMessagesDuration : 0;
    }

    public byte[] decryptAvatar(byte[] bytes) {
      return decryptBlob(bytes).avatar.toByteArray();
    }

    private GroupAttributeBlob decryptBlob(ByteString blob) {
      return decryptBlob(blob.toByteArray());
    }

    private GroupAttributeBlob decryptBlob(byte[] bytes) {
      // TODO GV2: Minimum field length checking should be responsibility of clientZkGroupCipher#decryptBlob
      if (bytes == null || bytes.length == 0) {
        return new GroupAttributeBlob();
      }
      if (bytes.length < 29) {
        Log.w(TAG, "Bad encrypted blob length");
        return new GroupAttributeBlob();
      }
      try {
        return GroupAttributeBlob.ADAPTER.decode(clientZkGroupCipher.decryptBlob(bytes));
      } catch (IOException | VerificationFailedException e) {
        Log.w(TAG, "Bad encrypted blob");
        return new GroupAttributeBlob();
      }
    }

    ByteString encryptTimer(int timerDurationSeconds) {
       try {
         GroupAttributeBlob timer = new GroupAttributeBlob.Builder()
                                                          .disappearingMessagesDuration(timerDurationSeconds)
                                                          .build();
         return ByteString.of(clientZkGroupCipher.encryptBlob(timer.encode()));
       } catch (VerificationFailedException e) {
         throw new AssertionError(e);
       }
    }

    /**
     * Encrypts a string as raw UTF-8 bytes for member-specific attributes.
     */
    private ByteString encryptString(@Nullable String value) {
      if (value == null || value.isEmpty()) {
        return ByteString.EMPTY;
      }

      try {
        return ByteString.of(clientZkGroupCipher.encryptBlob(value.getBytes(StandardCharsets.UTF_8)));
      } catch (VerificationFailedException e) {
        throw new AssertionError(e);
      }
    }

    /**
     * Decrypts a string from raw UTF-8 bytes for member-specific attributes.
     */
    @Nullable
    private String decryptString(@Nullable ByteString cipherText) throws VerificationFailedException {
      if (cipherText == null || cipherText.size() == 0) {
        return null;
      }

      byte[] decryptedBytes = clientZkGroupCipher.decryptBlob(cipherText.toByteArray());
      return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * Verifies signature and parses actions on a group change.
     */
    private GroupChange.Actions getVerifiedActions(GroupChange groupChange)
        throws VerificationFailedException, IOException
    {
      byte[] actionsByteArray = groupChange.actions.toByteArray();

      NotarySignature signature;
      try {
        signature = new NotarySignature(groupChange.serverSignature.toByteArray());
      } catch (InvalidInputException e) {
        Log.w(TAG, "Invalid input while verifying group change", e);
        throw new VerificationFailedException();
      }

      serverPublicParams.verifySignature(actionsByteArray, signature);

      return GroupChange.Actions.ADAPTER.decode(actionsByteArray);
    }

    /**
     * Parses actions on a group change without verification.
     */
    private GroupChange.Actions getActions(GroupChange groupChange)
        throws IOException
    {
      return GroupChange.Actions.ADAPTER.decode(groupChange.actions);
    }

    public GroupChange.Actions.Builder createChangeMemberRole(ACI memberAci, Member.Role role) {
      return new GroupChange.Actions.Builder().modifyMemberRoles(Collections.singletonList(
          new GroupChange.Actions.ModifyMemberRoleAction.Builder().userId(encryptServiceId(memberAci)).role(role).build()
      ));
    }

    public GroupChange.Actions.Builder createChangeMemberLabel(@Nonnull ACI memberAci, @Nonnull String labelString, @Nullable String labelEmoji) {
      return new GroupChange.Actions.Builder().modifyMemberLabel(
          Collections.singletonList(
              new GroupChange.Actions.ModifyMemberLabelAction.Builder()
                  .userId(encryptServiceId(memberAci))
                  .labelEmoji(encryptString(labelEmoji))
                  .labelString(encryptString(labelString))
                  .build()
          )
      );
    }

    public List<ServiceId> decryptAddMembers(List<GroupChange.Actions.AddMemberAction> addMembers) throws InvalidGroupStateException, InvalidInputException, VerificationFailedException {
      List<ServiceId> ids = new ArrayList<>(addMembers.size());
      for (GroupChange.Actions.AddMemberAction addMember : addMembers) {
        if (addMember.added.presentation.size() == 0) {
          ids.add(decryptAci(addMember.added.userId));
        } else {
          ProfileKeyCredentialPresentation profileKeyCredentialPresentation = new ProfileKeyCredentialPresentation(addMember.added.presentation.toByteArray());

          ids.add(ServiceId.fromLibSignal(clientZkGroupCipher.decrypt(profileKeyCredentialPresentation.getUuidCiphertext())));
        }
      }
      return ids;
    }

    public @Nullable ReceivedGroupSendEndorsements receiveGroupSendEndorsements(@Nonnull ACI selfAci,
                                                                                @Nonnull DecryptedGroup decryptedGroup,
                                                                                @Nullable ByteString groupSendEndorsementsResponse)
    {
      if (groupSendEndorsementsResponse != null && groupSendEndorsementsResponse.size() > 0) {
        try {
          return receiveGroupSendEndorsements(selfAci, decryptedGroup, new GroupSendEndorsementsResponse(groupSendEndorsementsResponse.toByteArray()));
        } catch (InvalidInputException e) {
          Log.w(TAG, "Unable to parse send endorsements response", e);
        }
      }

      return null;
    }

    public @Nullable ReceivedGroupSendEndorsements receiveGroupSendEndorsements(@Nonnull ACI selfAci,
                                                                                @Nonnull DecryptedGroup decryptedGroup,
                                                                                @Nullable GroupSendEndorsementsResponse groupSendEndorsementsResponse)
    {
      if (groupSendEndorsementsResponse == null) {
        return null;
      }

      List<ACI> members = decryptedGroup.members.stream().map(m -> ACI.parseOrThrow(m.aciBytes)).collect(Collectors.toList());

      if (!members.contains(selfAci)) {
        Log.w(TAG, "Attempting to receive endorsements for group state we aren't in, aborting");
        return null;
      }

      GroupSendEndorsementsResponse.ReceivedEndorsements endorsements = null;
      try {
        endorsements = groupSendEndorsementsResponse.receive(
            members.stream().map(ACI::getLibSignalAci).collect(Collectors.toList()),
            selfAci.getLibSignalAci(),
            groupSecretParams,
            serverPublicParams
        );
      } catch (VerificationFailedException e) {
        Log.w(TAG, "Unable to receive send endorsements for group", e);
      }

      return endorsements != null ? new ReceivedGroupSendEndorsements(groupSendEndorsementsResponse.getExpiration(), members, endorsements)
                                  : null;
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
