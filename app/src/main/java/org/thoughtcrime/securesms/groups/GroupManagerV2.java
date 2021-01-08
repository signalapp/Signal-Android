package org.thoughtcrime.securesms.groups;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupExternalCredential;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.ClientZkGroupCipher;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.groups.UuidCiphertext;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.v2.GroupCandidateHelper;
import org.thoughtcrime.securesms.groups.v2.GroupInviteLinkUrl;
import org.thoughtcrime.securesms.groups.v2.GroupLinkPassword;
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor;
import org.thoughtcrime.securesms.jobs.ProfileUploadJob;
import org.thoughtcrime.securesms.jobs.PushGroupSilentUpdateSendJob;
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mms.OutgoingGroupUpdateMessage;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupCandidate;
import org.whispersystems.signalservice.api.groupsv2.GroupChangeReconstruct;
import org.whispersystems.signalservice.api.groupsv2.GroupChangeUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.groupsv2.NotAbleToApplyGroupV2ChangeException;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.ConflictException;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.exceptions.GroupExistsException;
import org.whispersystems.signalservice.internal.push.exceptions.GroupPatchNotAcceptedException;
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class GroupManagerV2 {

  private static final String TAG = Log.tag(GroupManagerV2.class);

  private final Context                context;
  private final GroupDatabase          groupDatabase;
  private final GroupsV2Api            groupsV2Api;
  private final GroupsV2Operations     groupsV2Operations;
  private final GroupsV2Authorization  authorization;
  private final GroupsV2StateProcessor groupsV2StateProcessor;
  private final UUID                   selfUuid;
  private final GroupCandidateHelper   groupCandidateHelper;

  GroupManagerV2(@NonNull Context context) {
    this.context                = context;
    this.groupDatabase          = DatabaseFactory.getGroupDatabase(context);
    this.groupsV2Api            = ApplicationDependencies.getSignalServiceAccountManager().getGroupsV2Api();
    this.groupsV2Operations     = ApplicationDependencies.getGroupsV2Operations();
    this.authorization          = ApplicationDependencies.getGroupsV2Authorization();
    this.groupsV2StateProcessor = ApplicationDependencies.getGroupsV2StateProcessor();
    this.selfUuid               = Recipient.self().getUuid().get();
    this.groupCandidateHelper   = new GroupCandidateHelper(context);
  }

  @NonNull DecryptedGroupJoinInfo getGroupJoinInfoFromServer(@NonNull GroupMasterKey groupMasterKey, @Nullable GroupLinkPassword password)
      throws IOException, VerificationFailedException, GroupLinkNotActiveException
  {
    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);

    return groupsV2Api.getGroupJoinInfo(groupSecretParams,
                                        Optional.fromNullable(password).transform(GroupLinkPassword::serialize),
                                        authorization.getAuthorizationForToday(Recipient.self().requireUuid(), groupSecretParams));
  }

  @WorkerThread
  @NonNull GroupExternalCredential getGroupExternalCredential(@NonNull GroupId.V2 groupId)
      throws IOException, VerificationFailedException
  {
    GroupMasterKey groupMasterKey = DatabaseFactory.getGroupDatabase(context)
                                                   .requireGroup(groupId)
                                                   .requireV2GroupProperties()
                                                   .getGroupMasterKey();

    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);

    return groupsV2Api.getGroupExternalCredential(authorization.getAuthorizationForToday(Recipient.self().requireUuid(), groupSecretParams));
  }

  @WorkerThread
  @NonNull Map<UUID, UuidCiphertext> getUuidCipherTexts(@NonNull GroupId.V2 groupId) {
    GroupDatabase.GroupRecord groupRecord         = DatabaseFactory.getGroupDatabase(context).requireGroup(groupId);
    GroupMasterKey            groupMasterKey      = groupRecord.requireV2GroupProperties().getGroupMasterKey();
    ClientZkGroupCipher       clientZkGroupCipher = new ClientZkGroupCipher(GroupSecretParams.deriveFromMasterKey(groupMasterKey));
    List<Recipient>           recipients          = Recipient.resolvedList(groupRecord.getMembers());

    Map<UUID, UuidCiphertext> uuidCipherTexts = new HashMap<>();
    for (Recipient recipient : recipients) {
      uuidCipherTexts.put(recipient.requireUuid(), clientZkGroupCipher.encryptUuid(recipient.requireUuid()));
    }

    return uuidCipherTexts;
  }

  @WorkerThread
  GroupCreator create() throws GroupChangeBusyException {
    return new GroupCreator(GroupsV2ProcessingLock.acquireGroupProcessingLock());
  }

  @WorkerThread
  GroupEditor edit(@NonNull GroupId.V2 groupId) throws GroupChangeBusyException {
    return new GroupEditor(groupId, GroupsV2ProcessingLock.acquireGroupProcessingLock());
  }

  @WorkerThread
  GroupJoiner join(@NonNull GroupMasterKey groupMasterKey, @NonNull GroupLinkPassword password) throws GroupChangeBusyException {
    return new GroupJoiner(groupMasterKey, password, GroupsV2ProcessingLock.acquireGroupProcessingLock());
  }

  @WorkerThread
  GroupJoiner cancelRequest(@NonNull GroupId.V2 groupId) throws GroupChangeBusyException {
    GroupMasterKey groupMasterKey = DatabaseFactory.getGroupDatabase(context)
                                                   .requireGroup(groupId)
                                                   .requireV2GroupProperties()
                                                   .getGroupMasterKey();

    return new GroupJoiner(groupMasterKey, null, GroupsV2ProcessingLock.acquireGroupProcessingLock());
  }

  @WorkerThread
  GroupUpdater updater(@NonNull GroupMasterKey groupId) throws GroupChangeBusyException {
    return new GroupUpdater(groupId, GroupsV2ProcessingLock.acquireGroupProcessingLock());
  }

  @WorkerThread
  void groupServerQuery(@NonNull GroupMasterKey groupMasterKey)
      throws GroupNotAMemberException, IOException, GroupDoesNotExistException
  {
    new GroupsV2StateProcessor(context).forGroup(groupMasterKey)
                                       .getCurrentGroupStateFromServer();
  }

  @WorkerThread
  @NonNull DecryptedGroup addedGroupVersion(@NonNull GroupMasterKey groupMasterKey)
      throws GroupNotAMemberException, IOException, GroupDoesNotExistException
  {
    GroupsV2StateProcessor.StateProcessorForGroup stateProcessorForGroup = new GroupsV2StateProcessor(context).forGroup(groupMasterKey);
    DecryptedGroup                                latest                 = stateProcessorForGroup.getCurrentGroupStateFromServer();

    if (latest.getRevision() == 0) {
      return latest;
    }

    Optional<DecryptedMember> selfInFullMemberList = DecryptedGroupUtil.findMemberByUuid(latest.getMembersList(), Recipient.self().requireUuid());

    if (!selfInFullMemberList.isPresent()) {
      return latest;
    }

    DecryptedGroup joinedVersion = stateProcessorForGroup.getSpecificVersionFromServer(selfInFullMemberList.get().getJoinedAtRevision());

    if (joinedVersion != null) {
      return joinedVersion;
    } else {
      Log.w(TAG, "Unable to retreive exact version joined at, using latest");
      return latest;
    }
  }

  @WorkerThread
  void migrateGroupOnToServer(@NonNull GroupId.V1 groupIdV1, @NonNull Collection<Recipient> members)
      throws IOException, MembershipNotSuitableForV2Exception, GroupAlreadyExistsException, GroupChangeFailedException
  {
      GroupMasterKey            groupMasterKey    = groupIdV1.deriveV2MigrationMasterKey();
      GroupSecretParams         groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
      GroupDatabase.GroupRecord groupRecord       = groupDatabase.requireGroup(groupIdV1);
      String                    name              = groupRecord.getTitle();
      byte[]                    avatar            = groupRecord.hasAvatar() ? AvatarHelper.getAvatarBytes(context, groupRecord.getRecipientId()) : null;
      int                       messageTimer      = Recipient.resolved(groupRecord.getRecipientId()).getExpireMessages();
      Set<RecipientId>          memberIds         = Stream.of(members)
                                                          .map(Recipient::getId)
                                                          .filterNot(m -> m.equals(Recipient.self().getId()))
                                                          .collect(Collectors.toSet());

      createGroupOnServer(groupSecretParams, name, avatar, memberIds, Member.Role.ADMINISTRATOR, messageTimer);
  }

  @WorkerThread
  void sendNoopGroupUpdate(@NonNull GroupMasterKey masterKey, @NonNull DecryptedGroup currentState) {
    sendGroupUpdate(masterKey, new GroupMutation(currentState, DecryptedGroupChange.newBuilder().build(), currentState), null);
  }


  final class GroupCreator extends LockOwner {

    GroupCreator(@NonNull Closeable lock) {
      super(lock);
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult createGroup(@NonNull Collection<RecipientId> members,
                                                        @Nullable String name,
                                                        @Nullable byte[] avatar)
        throws GroupChangeFailedException, IOException, MembershipNotSuitableForV2Exception
    {
      return createGroup(name, avatar, members);
    }

    @WorkerThread
    private @NonNull GroupManager.GroupActionResult createGroup(@Nullable String name,
                                                                @Nullable byte[] avatar,
                                                                @NonNull Collection<RecipientId> members)
        throws GroupChangeFailedException, IOException, MembershipNotSuitableForV2Exception
    {
      GroupSecretParams groupSecretParams = GroupSecretParams.generate();
      DecryptedGroup    decryptedGroup;

      try {
        decryptedGroup = createGroupOnServer(groupSecretParams, name, avatar, members, Member.Role.DEFAULT, 0);
      } catch (GroupAlreadyExistsException e) {
        throw new GroupChangeFailedException(e);
      }

      GroupMasterKey masterKey        = groupSecretParams.getMasterKey();
      GroupId.V2     groupId          = groupDatabase.create(masterKey, decryptedGroup);
      RecipientId    groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
      Recipient      groupRecipient   = Recipient.resolved(groupRecipientId);

      AvatarHelper.setAvatar(context, groupRecipientId, avatar != null ? new ByteArrayInputStream(avatar) : null);
      groupDatabase.onAvatarUpdated(groupId, avatar != null);
      DatabaseFactory.getRecipientDatabase(context).setProfileSharing(groupRecipient.getId(), true);

      DecryptedGroupChange groupChange = DecryptedGroupChange.newBuilder(GroupChangeReconstruct.reconstructGroupChange(DecryptedGroup.newBuilder().build(), decryptedGroup))
                                                             .setEditor(UuidUtil.toByteString(selfUuid))
                                                             .build();

      RecipientAndThread recipientAndThread = sendGroupUpdate(masterKey, new GroupMutation(null, groupChange, decryptedGroup), null);

      return new GroupManager.GroupActionResult(recipientAndThread.groupRecipient,
                                                recipientAndThread.threadId,
                                                decryptedGroup.getMembersCount() - 1,
                                                getPendingMemberRecipientIds(decryptedGroup.getPendingMembersList()));
    }
  }

  final class GroupEditor extends LockOwner {

    private final GroupId.V2                         groupId;
    private final GroupMasterKey                     groupMasterKey;
    private final GroupSecretParams                  groupSecretParams;
    private final GroupsV2Operations.GroupOperations groupOperations;

    GroupEditor(@NonNull GroupId.V2 groupId, @NonNull Closeable lock) {
      super(lock);

      GroupDatabase.GroupRecord       groupRecord       = groupDatabase.requireGroup(groupId);
      GroupDatabase.V2GroupProperties v2GroupProperties = groupRecord.requireV2GroupProperties();

      this.groupId           = groupId;
      this.groupMasterKey    = v2GroupProperties.getGroupMasterKey();
      this.groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
      this.groupOperations   = groupsV2Operations.forGroup(groupSecretParams);
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult addMembers(@NonNull Collection<RecipientId> newMembers)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, MembershipNotSuitableForV2Exception
    {
      if (!GroupsV2CapabilityChecker.allHaveUuidAndSupportGroupsV2(newMembers)) {
        throw new MembershipNotSuitableForV2Exception("At least one potential new member does not support GV2 or UUID capabilities");
      }

      Set<GroupCandidate> groupCandidates = groupCandidateHelper.recipientIdsToCandidates(new HashSet<>(newMembers));

      if (SignalStore.internalValues().gv2ForceInvites()) {
        groupCandidates = GroupCandidate.withoutProfileKeyCredentials(groupCandidates);
      }

      return commitChangeWithConflictResolution(groupOperations.createModifyGroupMembershipChange(groupCandidates, selfUuid));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult updateGroupTimer(int expirationTime)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      return commitChangeWithConflictResolution(groupOperations.createModifyGroupTimerChange(expirationTime));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult updateAttributesRights(@NonNull GroupAccessControl newRights)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      return commitChangeWithConflictResolution(groupOperations.createChangeAttributesRights(rightsToAccessControl(newRights)));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult updateMembershipRights(@NonNull GroupAccessControl newRights)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      return commitChangeWithConflictResolution(groupOperations.createChangeMembershipRights(rightsToAccessControl(newRights)));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult updateGroupTitleAndAvatar(@Nullable String title, @Nullable byte[] avatarBytes, boolean avatarChanged)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      try {
        GroupChange.Actions.Builder change = title != null ? groupOperations.createModifyGroupTitle(title)
                                                           : GroupChange.Actions.newBuilder();

        if (avatarChanged) {
          String cdnKey = avatarBytes != null ? groupsV2Api.uploadAvatar(avatarBytes, groupSecretParams, authorization.getAuthorizationForToday(selfUuid, groupSecretParams))
                                              : "";
          change.setModifyAvatar(GroupChange.Actions.ModifyAvatarAction.newBuilder()
                                                    .setAvatar(cdnKey));
        }

        GroupManager.GroupActionResult groupActionResult = commitChangeWithConflictResolution(change);

        if (avatarChanged) {
          AvatarHelper.setAvatar(context, Recipient.externalGroupExact(context, groupId).getId(), avatarBytes != null ? new ByteArrayInputStream(avatarBytes) : null);
          groupDatabase.onAvatarUpdated(groupId, avatarBytes != null);
        }

        return groupActionResult;
      } catch (VerificationFailedException e) {
        throw new GroupChangeFailedException(e);
      }
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult revokeInvites(@NonNull Collection<UuidCiphertext> uuidCipherTexts)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      return commitChangeWithConflictResolution(groupOperations.createRemoveInvitationChange(new HashSet<>(uuidCipherTexts)));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult approveRequests(@NonNull Collection<RecipientId> recipientIds)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      Set<UUID> uuids = Stream.of(recipientIds)
                              .map(r -> Recipient.resolved(r).getUuid().get())
                              .collect(Collectors.toSet());

      return commitChangeWithConflictResolution(groupOperations.createApproveGroupJoinRequest(uuids));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult denyRequests(@NonNull Collection<RecipientId> recipientIds)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      Set<UUID> uuids = Stream.of(recipientIds)
                              .map(r -> Recipient.resolved(r).getUuid().get())
                              .collect(Collectors.toSet());

      return commitChangeWithConflictResolution(groupOperations.createRefuseGroupJoinRequest(uuids));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult setMemberAdmin(@NonNull RecipientId recipientId,
                                                           boolean admin)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      Recipient recipient = Recipient.resolved(recipientId);
      return commitChangeWithConflictResolution(groupOperations.createChangeMemberRole(recipient.getUuid().get(), admin ? Member.Role.ADMINISTRATOR : Member.Role.DEFAULT));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult leaveGroup()
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      Recipient                        self               = Recipient.self();
      GroupDatabase.GroupRecord        groupRecord        = groupDatabase.getGroup(groupId).get();
      List<DecryptedPendingMember>     pendingMembersList = groupRecord.requireV2GroupProperties().getDecryptedGroup().getPendingMembersList();
      Optional<DecryptedPendingMember> selfPendingMember  = DecryptedGroupUtil.findPendingByUuid(pendingMembersList, selfUuid);

      if (selfPendingMember.isPresent()) {
        try {
          return revokeInvites(Collections.singleton(new UuidCiphertext(selfPendingMember.get().getUuidCipherText().toByteArray())));
        } catch (InvalidInputException e) {
          throw new AssertionError(e);
        }
      } else {
        return ejectMember(self.getId());
      }
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult ejectMember(@NonNull RecipientId recipientId)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      Recipient recipient = Recipient.resolved(recipientId);

      return commitChangeWithConflictResolution(groupOperations.createRemoveMembersChange(Collections.singleton(recipient.getUuid().get())));
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult addMemberAdminsAndLeaveGroup(Collection<RecipientId> newAdmins)
        throws GroupChangeFailedException, GroupNotAMemberException, GroupInsufficientRightsException, IOException
    {
      Recipient  self               = Recipient.self();
      List<UUID> newAdminRecipients = Stream.of(newAdmins).map(id -> Recipient.resolved(id).getUuid().get()).toList();

      return commitChangeWithConflictResolution(groupOperations.createLeaveAndPromoteMembersToAdmin(self.getUuid().get(),
                                                                                                    newAdminRecipients));
    }

    @WorkerThread
    @Nullable GroupManager.GroupActionResult updateSelfProfileKeyInGroup()
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      ProfileKey                profileKey  = ProfileKeyUtil.getSelfProfileKey();
      DecryptedGroup            group       = groupDatabase.requireGroup(groupId).requireV2GroupProperties().getDecryptedGroup();
      Optional<DecryptedMember> selfInGroup = DecryptedGroupUtil.findMemberByUuid(group.getMembersList(), selfUuid);

      if (!selfInGroup.isPresent()) {
        Log.w(TAG, "Self not in group " + groupId);
        return null;
      }

      if (Arrays.equals(profileKey.serialize(), selfInGroup.get().getProfileKey().toByteArray())) {
        Log.i(TAG, "Own Profile Key is already up to date in group " + groupId);
        return null;
      } else {
        Log.i(TAG, "Profile Key does not match that in group " + groupId);
      }

      GroupCandidate groupCandidate = groupCandidateHelper.recipientIdToCandidate(Recipient.self().getId());

      if (!groupCandidate.hasProfileKeyCredential()) {
        Log.w(TAG, "No credential available, repairing");
        ApplicationDependencies.getJobManager().add(new ProfileUploadJob());
        return null;
      }

      return commitChangeWithConflictResolution(groupOperations.createUpdateProfileKeyCredentialChange(groupCandidate.getProfileKeyCredential().get()));
    }

    @WorkerThread
    @Nullable GroupManager.GroupActionResult acceptInvite()
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      DecryptedGroup            group       = groupDatabase.requireGroup(groupId).requireV2GroupProperties().getDecryptedGroup();
      Optional<DecryptedMember> selfInGroup = DecryptedGroupUtil.findMemberByUuid(group.getMembersList(), Recipient.self().getUuid().get());

      if (selfInGroup.isPresent()) {
        Log.w(TAG, "Self already in group");
        return null;
      }

      GroupCandidate groupCandidate = groupCandidateHelper.recipientIdToCandidate(Recipient.self().getId());

      if (!groupCandidate.hasProfileKeyCredential()) {
        Log.w(TAG, "No credential available");
        return null;
      }

      return commitChangeWithConflictResolution(groupOperations.createAcceptInviteChange(groupCandidate.getProfileKeyCredential().get()));
    }

    @WorkerThread
    public GroupManager.GroupActionResult cycleGroupLinkPassword()
        throws GroupChangeFailedException, GroupNotAMemberException, GroupInsufficientRightsException, IOException
    {
      return commitChangeWithConflictResolution(groupOperations.createModifyGroupLinkPasswordChange(GroupLinkPassword.createNew().serialize()));
    }

    @WorkerThread
    public @Nullable GroupInviteLinkUrl setJoinByGroupLinkState(@NonNull GroupManager.GroupLinkState state)
        throws GroupChangeFailedException, GroupNotAMemberException, GroupInsufficientRightsException, IOException
    {
      AccessControl.AccessRequired access;

      switch (state) {
        case DISABLED             : access = AccessControl.AccessRequired.UNSATISFIABLE; break;
        case ENABLED              : access = AccessControl.AccessRequired.ANY;           break;
        case ENABLED_WITH_APPROVAL: access = AccessControl.AccessRequired.ADMINISTRATOR; break;
        default:                    throw new AssertionError();
      }

      GroupChange.Actions.Builder change = groupOperations.createChangeJoinByLinkRights(access);

      if (state != GroupManager.GroupLinkState.DISABLED) {
        DecryptedGroup group = groupDatabase.requireGroup(groupId).requireV2GroupProperties().getDecryptedGroup();

        if (group.getInviteLinkPassword().isEmpty()) {
          Log.d(TAG, "First time enabling group links for group and password empty, generating");
          change = groupOperations.createModifyGroupLinkPasswordAndRightsChange(GroupLinkPassword.createNew().serialize(), access);
        }
      }

      commitChangeWithConflictResolution(change);

      if (state != GroupManager.GroupLinkState.DISABLED) {
        GroupDatabase.V2GroupProperties v2GroupProperties = groupDatabase.requireGroup(groupId).requireV2GroupProperties();
        GroupMasterKey                  groupMasterKey    = v2GroupProperties.getGroupMasterKey();
        DecryptedGroup                  decryptedGroup    = v2GroupProperties.getDecryptedGroup();

        return GroupInviteLinkUrl.forGroup(groupMasterKey, decryptedGroup);
      } else {
        return null;
      }
    }

    private @NonNull GroupManager.GroupActionResult commitChangeWithConflictResolution(@NonNull GroupChange.Actions.Builder change)
        throws GroupChangeFailedException, GroupNotAMemberException, GroupInsufficientRightsException, IOException
    {
      change.setSourceUuid(UuidUtil.toByteString(Recipient.self().getUuid().get()));

      for (int attempt = 0; attempt < 5; attempt++) {
        try {
          return commitChange(change);
        } catch (GroupPatchNotAcceptedException e) {
          throw new GroupChangeFailedException(e);
        } catch (ConflictException e) {
          Log.w(TAG, "Invalid group patch or conflict", e);

          change = resolveConflict(change);

          if (GroupChangeUtil.changeIsEmpty(change.build())) {
            Log.i(TAG, "Change is empty after conflict resolution");
            Recipient groupRecipient = Recipient.externalGroupExact(context, groupId);
            long      threadId       = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);

            return new GroupManager.GroupActionResult(groupRecipient, threadId, 0, Collections.emptyList());
          }
        }
      }

      throw new GroupChangeFailedException("Unable to apply change to group after conflicts");
    }

    private GroupChange.Actions.Builder resolveConflict(@NonNull GroupChange.Actions.Builder change)
          throws IOException, GroupNotAMemberException, GroupChangeFailedException
    {
      GroupsV2StateProcessor.GroupUpdateResult groupUpdateResult = groupsV2StateProcessor.forGroup(groupMasterKey)
                                                                                         .updateLocalGroupToRevision(GroupsV2StateProcessor.LATEST, System.currentTimeMillis(), null);

      if (groupUpdateResult.getGroupState() != GroupsV2StateProcessor.GroupState.GROUP_UPDATED || groupUpdateResult.getLatestServer() == null) {
        throw new GroupChangeFailedException();
      }

      Log.w(TAG, "Group has been updated");
      try {
        GroupChange.Actions changeActions = change.build();

        return GroupChangeUtil.resolveConflict(groupUpdateResult.getLatestServer(),
                                               groupOperations.decryptChange(changeActions, selfUuid),
                                               changeActions);
      } catch (VerificationFailedException | InvalidGroupStateException ex) {
        throw new GroupChangeFailedException(ex);
      }
    }

    private GroupManager.GroupActionResult commitChange(@NonNull GroupChange.Actions.Builder change)
        throws GroupNotAMemberException, GroupChangeFailedException, IOException, GroupInsufficientRightsException
    {
      final GroupDatabase.GroupRecord       groupRecord         = groupDatabase.requireGroup(groupId);
      final GroupDatabase.V2GroupProperties v2GroupProperties   = groupRecord.requireV2GroupProperties();
      final int                             nextRevision        = v2GroupProperties.getGroupRevision() + 1;
      final GroupChange.Actions             changeActions       = change.setRevision(nextRevision).build();
      final DecryptedGroupChange            decryptedChange;
      final DecryptedGroup                  decryptedGroupState;
      final DecryptedGroup                  previousGroupState;

      try {
        previousGroupState  = v2GroupProperties.getDecryptedGroup();
        decryptedChange     = groupOperations.decryptChange(changeActions, selfUuid);
        decryptedGroupState = DecryptedGroupUtil.apply(previousGroupState, decryptedChange);
      } catch (VerificationFailedException | InvalidGroupStateException | NotAbleToApplyGroupV2ChangeException e) {
        Log.w(TAG, e);
        throw new IOException(e);
      }

      GroupChange signedGroupChange = commitToServer(changeActions);
      groupDatabase.update(groupId, decryptedGroupState);

      GroupMutation      groupMutation      = new GroupMutation(previousGroupState, decryptedChange, decryptedGroupState);
      RecipientAndThread recipientAndThread = sendGroupUpdate(groupMasterKey, groupMutation, signedGroupChange);
      int                newMembersCount    = decryptedChange.getNewMembersCount();
      List<RecipientId>  newPendingMembers  = getPendingMemberRecipientIds(decryptedChange.getNewPendingMembersList());

      return new GroupManager.GroupActionResult(recipientAndThread.groupRecipient, recipientAndThread.threadId, newMembersCount, newPendingMembers);
    }

    private @NonNull GroupChange commitToServer(@NonNull GroupChange.Actions change)
        throws GroupNotAMemberException, GroupChangeFailedException, IOException, GroupInsufficientRightsException
    {
      try {
        return groupsV2Api.patchGroup(change, authorization.getAuthorizationForToday(selfUuid, groupSecretParams), Optional.absent());
      } catch (NotInGroupException e) {
        Log.w(TAG, e);
        throw new GroupNotAMemberException(e);
      } catch (AuthorizationFailedException e) {
        Log.w(TAG, e);
        throw new GroupInsufficientRightsException(e);
      } catch (VerificationFailedException e) {
        Log.w(TAG, e);
        throw new GroupChangeFailedException(e);
      }
    }
  }

  final class GroupUpdater extends LockOwner {

    private final GroupMasterKey groupMasterKey;

    GroupUpdater(@NonNull GroupMasterKey groupMasterKey, @NonNull Closeable lock) {
      super(lock);

      this.groupMasterKey = groupMasterKey;
    }

    @WorkerThread
    void updateLocalToServerRevision(int revision, long timestamp, @Nullable byte[] signedGroupChange)
        throws IOException, GroupNotAMemberException
    {
      new GroupsV2StateProcessor(context).forGroup(groupMasterKey)
                                         .updateLocalGroupToRevision(revision, timestamp, getDecryptedGroupChange(signedGroupChange));
    }

    private DecryptedGroupChange getDecryptedGroupChange(@Nullable byte[] signedGroupChange) {
      if (signedGroupChange != null) {
        GroupsV2Operations.GroupOperations groupOperations = groupsV2Operations.forGroup(GroupSecretParams.deriveFromMasterKey(groupMasterKey));

        try {
          return groupOperations.decryptChange(GroupChange.parseFrom(signedGroupChange), true)
                                .orNull();
        } catch (VerificationFailedException | InvalidGroupStateException | InvalidProtocolBufferException e) {
          Log.w(TAG, "Unable to verify supplied group change", e);
        }
      }

      return null;
    }
  }

  @WorkerThread
  private @NonNull DecryptedGroup createGroupOnServer(@NonNull GroupSecretParams groupSecretParams,
                                                      @Nullable String name,
                                                      @Nullable byte[] avatar,
                                                      @NonNull Collection<RecipientId> members,
                                                      @NonNull Member.Role memberRole,
                                                      int disappearingMessageTimerSeconds)
      throws GroupChangeFailedException, IOException, MembershipNotSuitableForV2Exception, GroupAlreadyExistsException
  {
    if (!GroupsV2CapabilityChecker.allAndSelfHaveUuidAndSupportGroupsV2(members)) {
      throw new MembershipNotSuitableForV2Exception("At least one potential new member does not support GV2 capability or we don't have their UUID");
    }

    GroupCandidate      self       = groupCandidateHelper.recipientIdToCandidate(Recipient.self().getId());
    Set<GroupCandidate> candidates = new HashSet<>(groupCandidateHelper.recipientIdsToCandidates(members));

    if (SignalStore.internalValues().gv2ForceInvites()) {
      Log.w(TAG, "Forcing GV2 invites due to internal setting");
      candidates = GroupCandidate.withoutProfileKeyCredentials(candidates);
    }

    if (!self.hasProfileKeyCredential()) {
      Log.w(TAG, "Cannot create a V2 group as self does not have a versioned profile");
      throw new MembershipNotSuitableForV2Exception("Cannot create a V2 group as self does not have a versioned profile");
    }

    GroupsV2Operations.NewGroup newGroup = groupsV2Operations.createNewGroup(groupSecretParams,
                                                                             name,
                                                                             Optional.fromNullable(avatar),
                                                                             self,
                                                                             candidates,
                                                                             memberRole,
                                                                             disappearingMessageTimerSeconds);

    try {
      groupsV2Api.putNewGroup(newGroup, authorization.getAuthorizationForToday(Recipient.self().requireUuid(), groupSecretParams));

      DecryptedGroup decryptedGroup = groupsV2Api.getGroup(groupSecretParams, ApplicationDependencies.getGroupsV2Authorization().getAuthorizationForToday(Recipient.self().requireUuid(), groupSecretParams));
      if (decryptedGroup == null) {
        throw new GroupChangeFailedException();
      }

      return decryptedGroup;
    } catch (VerificationFailedException | InvalidGroupStateException e) {
      throw new GroupChangeFailedException(e);
    } catch (GroupExistsException e) {
      throw new GroupAlreadyExistsException(e);
    }
  }

  final class GroupJoiner extends LockOwner {
    private final GroupId.V2                         groupId;
    private final GroupLinkPassword                  password;
    private final GroupSecretParams                  groupSecretParams;
    private final GroupsV2Operations.GroupOperations groupOperations;
    private final GroupMasterKey                     groupMasterKey;

    public GroupJoiner(@NonNull GroupMasterKey groupMasterKey,
                       @Nullable GroupLinkPassword password,
                       @NonNull Closeable lock)
    {
      super(lock);

      this.groupId           = GroupId.v2(groupMasterKey);
      this.password          = password;
      this.groupMasterKey    = groupMasterKey;
      this.groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
      this.groupOperations   = groupsV2Operations.forGroup(groupSecretParams);
    }

    @WorkerThread
    public GroupManager.GroupActionResult joinGroup(@NonNull DecryptedGroupJoinInfo joinInfo,
                                                    @Nullable byte[] avatar)
        throws GroupChangeFailedException, IOException, MembershipNotSuitableForV2Exception, GroupLinkNotActiveException
    {
      boolean requestToJoin  = joinInfo.getAddFromInviteLink() == AccessControl.AccessRequired.ADMINISTRATOR;
      boolean alreadyAMember = false;

      if (requestToJoin) {
        Log.i(TAG, "Requesting to join " + groupId);
      } else {
        Log.i(TAG, "Joining " + groupId);
      }

      GroupChange          signedGroupChange = null;
      DecryptedGroupChange decryptedChange   = null;
      try {
        signedGroupChange = joinGroupOnServer(requestToJoin, joinInfo.getRevision());

        if (requestToJoin) {
          Log.i(TAG, String.format("Successfully requested to join %s on server", groupId));
        } else {
          Log.i(TAG, String.format("Successfully added self to %s on server", groupId));
        }

        decryptedChange = decryptChange(signedGroupChange);
      } catch (GroupJoinAlreadyAMemberException e) {
        Log.i(TAG, "Server reports that we are already a member of " + groupId);
        alreadyAMember = true;
      }

      Optional<GroupDatabase.GroupRecord> unmigratedV1Group = groupDatabase.getGroupV1ByExpectedV2(groupId);

      if (unmigratedV1Group.isPresent()) {
        Log.i(TAG, "Group link was for a migrated V1 group we know about! Migrating it and using that as the base.");
        GroupsV1MigrationUtil.performLocalMigration(context, unmigratedV1Group.get().getId().requireV1());
      }

      DecryptedGroup decryptedGroup = createPlaceholderGroup(joinInfo, requestToJoin);

      Optional<GroupDatabase.GroupRecord> group = groupDatabase.getGroup(groupId);

      if (group.isPresent()) {
        Log.i(TAG, "Group already present locally");

        DecryptedGroup currentGroupState = group.get()
                                                .requireV2GroupProperties()
                                                .getDecryptedGroup();

        DecryptedGroup updatedGroup = currentGroupState;

        try {
          if (decryptedChange != null) {
            updatedGroup = DecryptedGroupUtil.applyWithoutRevisionCheck(updatedGroup, decryptedChange);
          }
          updatedGroup = resetRevision(updatedGroup, currentGroupState.getRevision());
        } catch (NotAbleToApplyGroupV2ChangeException e) {
          Log.w(TAG, e);
          updatedGroup = decryptedGroup;
        }

        groupDatabase.update(groupId, updatedGroup);
      } else {
        groupDatabase.create(groupMasterKey, decryptedGroup);
        Log.i(TAG, "Created local group with placeholder");
      }

      RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
      Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);

      AvatarHelper.setAvatar(context, groupRecipientId, avatar != null ? new ByteArrayInputStream(avatar) : null);
      groupDatabase.onAvatarUpdated(groupId, avatar != null);
      DatabaseFactory.getRecipientDatabase(context).setProfileSharing(groupRecipientId, true);

      if (alreadyAMember) {
        Log.i(TAG, "Already a member of the group");

        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
        long           threadId       = threadDatabase.getOrCreateValidThreadId(groupRecipient, -1);

        return new GroupManager.GroupActionResult(groupRecipient,
                                                  threadId,
                                                  0,
                                                  Collections.emptyList());
      } else if (requestToJoin) {
        Log.i(TAG, "Requested to join, cannot send update");

        RecipientAndThread recipientAndThread = sendGroupUpdate(groupMasterKey, new GroupMutation(null, decryptedChange, decryptedGroup), signedGroupChange);

        return new GroupManager.GroupActionResult(groupRecipient,
                                                  recipientAndThread.threadId,
                                                  0,
                                                  Collections.emptyList());
      } else {
        Log.i(TAG, "Joined group on server, fetching group state and sending update");

        return fetchGroupStateAndSendUpdate(groupRecipient, decryptedGroup, decryptedChange, signedGroupChange);
      }
    }

    private GroupManager.GroupActionResult fetchGroupStateAndSendUpdate(@NonNull Recipient groupRecipient,
                                                                        @NonNull DecryptedGroup decryptedGroup,
                                                                        @NonNull DecryptedGroupChange decryptedChange,
                                                                        @NonNull GroupChange signedGroupChange)
        throws GroupChangeFailedException, IOException
    {
      try {
        new GroupsV2StateProcessor(context).forGroup(groupMasterKey)
                                           .updateLocalGroupToRevision(decryptedChange.getRevision(),
                                                                       System.currentTimeMillis(),
                                                                       decryptedChange);

        RecipientAndThread recipientAndThread = sendGroupUpdate(groupMasterKey, new GroupMutation(null, decryptedChange, decryptedGroup), signedGroupChange);

        return new GroupManager.GroupActionResult(groupRecipient,
                                                  recipientAndThread.threadId,
                                                  1,
                                                  Collections.emptyList());
      } catch (GroupNotAMemberException e) {
        Log.w(TAG, "Despite adding self to group, server says we are not a member, scheduling refresh of group info " + groupId, e);

        ApplicationDependencies.getJobManager()
                               .add(new RequestGroupV2InfoJob(groupId));

        throw new GroupChangeFailedException(e);
      } catch (IOException e) {
        Log.w(TAG, "Group data fetch failed, scheduling refresh of group info " + groupId, e);

        ApplicationDependencies.getJobManager()
                               .add(new RequestGroupV2InfoJob(groupId));

        throw e;
      }
    }

    private @NonNull DecryptedGroupChange decryptChange(@NonNull GroupChange signedGroupChange)
        throws GroupChangeFailedException
    {
      try {
        return groupOperations.decryptChange(signedGroupChange, false).get();
      } catch (VerificationFailedException | InvalidGroupStateException | InvalidProtocolBufferException e) {
        Log.w(TAG, e);
        throw new GroupChangeFailedException(e);
      }
    }

    /**
     * Creates a local group from what we know before joining.
     * <p>
     * Creates as a {@link GroupsV2StateProcessor#PLACEHOLDER_REVISION} so that we know not do do a
     * full diff against this group once we learn more about this group as that would create a large
     * update message.
     */
    private DecryptedGroup createPlaceholderGroup(@NonNull DecryptedGroupJoinInfo joinInfo, boolean requestToJoin) {
      DecryptedGroup.Builder group = DecryptedGroup.newBuilder()
                                                   .setTitle(joinInfo.getTitle())
                                                   .setAvatar(joinInfo.getAvatar())
                                                   .setRevision(GroupsV2StateProcessor.PLACEHOLDER_REVISION);

      Recipient  self       = Recipient.self();
      ByteString selfUuid   = UuidUtil.toByteString(self.requireUuid());
      ByteString profileKey = ByteString.copyFrom(Objects.requireNonNull(self.getProfileKey()));

      if (requestToJoin) {
        group.addRequestingMembers(DecryptedRequestingMember.newBuilder()
                                                            .setUuid(selfUuid)
                                                            .setProfileKey(profileKey));
      } else {
        group.addMembers(DecryptedMember.newBuilder()
                                        .setUuid(selfUuid)
                                        .setProfileKey(profileKey));
      }

      return group.build();
    }

    private @NonNull GroupChange joinGroupOnServer(boolean requestToJoin, int currentRevision)
        throws GroupChangeFailedException, IOException, MembershipNotSuitableForV2Exception, GroupLinkNotActiveException, GroupJoinAlreadyAMemberException
    {
      if (!GroupsV2CapabilityChecker.allAndSelfHaveUuidAndSupportGroupsV2(Collections.singleton(Recipient.self().getId()))) {
        throw new MembershipNotSuitableForV2Exception("Self does not support GV2 or UUID capabilities");
      }

      GroupCandidate self = groupCandidateHelper.recipientIdToCandidate(Recipient.self().getId());

      if (!self.hasProfileKeyCredential()) {
        throw new MembershipNotSuitableForV2Exception("No profile key credential for self");
      }

      ProfileKeyCredential profileKeyCredential = self.getProfileKeyCredential().get();

      GroupChange.Actions.Builder change = requestToJoin ? groupOperations.createGroupJoinRequest(profileKeyCredential)
                                                         : groupOperations.createGroupJoinDirect(profileKeyCredential);

      change.setSourceUuid(UuidUtil.toByteString(Recipient.self().getUuid().get()));

      return commitJoinChangeWithConflictResolution(currentRevision, change);
    }

    private @NonNull GroupChange commitJoinChangeWithConflictResolution(int currentRevision, @NonNull GroupChange.Actions.Builder change)
        throws GroupChangeFailedException, IOException, GroupLinkNotActiveException, GroupJoinAlreadyAMemberException
    {
      for (int attempt = 0; attempt < 5; attempt++) {
        try {
          GroupChange.Actions changeActions = change.setRevision(currentRevision + 1)
                                                    .build();

          Log.i(TAG, "Trying to join group at V" + changeActions.getRevision());
          GroupChange signedGroupChange = commitJoinToServer(changeActions);

          Log.i(TAG, "Successfully joined group at V" + changeActions.getRevision());
          return signedGroupChange;
        } catch (GroupPatchNotAcceptedException e) {
          Log.w(TAG, "Patch not accepted", e);

          try {
            if (alreadyPendingAdminApproval() || testGroupMembership()) {
              throw new GroupJoinAlreadyAMemberException(e);
            } else {
              throw new GroupChangeFailedException(e);
            }
          } catch (VerificationFailedException | InvalidGroupStateException ex) {
            throw new GroupChangeFailedException(ex);
          }
        } catch (ConflictException e) {
          Log.w(TAG, "Revision conflict", e);

          currentRevision = getCurrentGroupRevisionFromServer();
        }
      }

      throw new GroupChangeFailedException("Unable to join group after conflicts");
    }

    private @NonNull GroupChange commitJoinToServer(@NonNull GroupChange.Actions change)
      throws GroupChangeFailedException, IOException, GroupLinkNotActiveException
    {
      try {
        return groupsV2Api.patchGroup(change, authorization.getAuthorizationForToday(selfUuid, groupSecretParams), Optional.fromNullable(password).transform(GroupLinkPassword::serialize));
      } catch (NotInGroupException | VerificationFailedException e) {
        Log.w(TAG, e);
        throw new GroupChangeFailedException(e);
      } catch (AuthorizationFailedException e) {
        Log.w(TAG, e);
        throw new GroupLinkNotActiveException(e);
      }
    }

    private int getCurrentGroupRevisionFromServer()
        throws IOException, GroupLinkNotActiveException, GroupChangeFailedException
    {
      try {
        int currentRevision = getGroupJoinInfoFromServer(groupMasterKey, password).getRevision();

        Log.i(TAG, "Server now on V" + currentRevision);

        return currentRevision;
      } catch (VerificationFailedException ex) {
        throw new GroupChangeFailedException(ex);
      }
    }

    private boolean alreadyPendingAdminApproval()
        throws IOException, GroupLinkNotActiveException, GroupChangeFailedException
    {
      try {
        boolean pendingAdminApproval = getGroupJoinInfoFromServer(groupMasterKey, password).getPendingAdminApproval();

        if (pendingAdminApproval) {
          Log.i(TAG, "User is already pending admin approval");
        }

        return pendingAdminApproval;
      } catch (VerificationFailedException ex) {
        throw new GroupChangeFailedException(ex);
      }
    }

    private boolean testGroupMembership()
      throws IOException, VerificationFailedException, InvalidGroupStateException
    {
      try {
        groupsV2Api.getGroup(groupSecretParams, authorization.getAuthorizationForToday(Recipient.self().requireUuid(), groupSecretParams));
        return true;
      } catch (NotInGroupException ex) {
        return false;
      }
    }

    @WorkerThread
    void cancelJoinRequest()
        throws GroupChangeFailedException, IOException
    {
      Set<UUID> uuids = Collections.singleton(Recipient.self().getUuid().get());

      GroupChange signedGroupChange;
      try {
        signedGroupChange = commitCancelChangeWithConflictResolution(groupOperations.createRefuseGroupJoinRequest(uuids));
      } catch (GroupLinkNotActiveException e) {
        Log.d(TAG, "Unexpected unable to leave group due to group link off");
        throw new GroupChangeFailedException(e);
      }

      DecryptedGroup decryptedGroup = groupDatabase.requireGroup(groupId).requireV2GroupProperties().getDecryptedGroup();

      try {
        DecryptedGroupChange decryptedChange = groupOperations.decryptChange(signedGroupChange, false).get();
        DecryptedGroup       newGroup        = DecryptedGroupUtil.applyWithoutRevisionCheck(decryptedGroup, decryptedChange);

        groupDatabase.update(groupId, resetRevision(newGroup, decryptedGroup.getRevision()));

        sendGroupUpdate(groupMasterKey, new GroupMutation(decryptedGroup, decryptedChange, newGroup), signedGroupChange);
      } catch (VerificationFailedException | InvalidGroupStateException | NotAbleToApplyGroupV2ChangeException e) {
        throw new GroupChangeFailedException(e);
      }
    }

    private DecryptedGroup resetRevision(DecryptedGroup newGroup, int revision) {
      return DecryptedGroup.newBuilder(newGroup)
                           .setRevision(revision)
                           .build();
    }

    private @NonNull GroupChange commitCancelChangeWithConflictResolution(@NonNull GroupChange.Actions.Builder change)
        throws GroupChangeFailedException, IOException, GroupLinkNotActiveException
    {
      int currentRevision = getCurrentGroupRevisionFromServer();

      for (int attempt = 0; attempt < 5; attempt++) {
        try {
          GroupChange.Actions changeActions = change.setRevision(currentRevision + 1)
                                                    .build();

          Log.i(TAG, "Trying to cancel request group at V" + changeActions.getRevision());
          GroupChange signedGroupChange = commitJoinToServer(changeActions);

          Log.i(TAG, "Successfully cancelled group join at V" + changeActions.getRevision());
          return signedGroupChange;
        } catch (GroupPatchNotAcceptedException e) {
          throw new GroupChangeFailedException(e);
        } catch (ConflictException e) {
          Log.w(TAG, "Revision conflict", e);

          currentRevision = getCurrentGroupRevisionFromServer();
        }
      }

      throw new GroupChangeFailedException("Unable to cancel group join request after conflicts");
    }
}

  private abstract static class LockOwner implements Closeable {
    final Closeable lock;

    LockOwner(@NonNull Closeable lock) {
      this.lock = lock;
    }

    @Override
    public void close() throws IOException {
      lock.close();
    }
  }

  private @NonNull RecipientAndThread sendGroupUpdate(@NonNull GroupMasterKey masterKey,
                                                      @NonNull GroupMutation groupMutation,
                                                      @Nullable GroupChange signedGroupChange)
  {
    GroupId.V2                groupId                 = GroupId.v2(masterKey);
    Recipient                 groupRecipient          = Recipient.externalGroupExact(context, groupId);
    DecryptedGroupV2Context   decryptedGroupV2Context = GroupProtoUtil.createDecryptedGroupV2Context(masterKey, groupMutation, signedGroupChange);
    OutgoingGroupUpdateMessage outgoingMessage        = new OutgoingGroupUpdateMessage(groupRecipient,
                                                                                       decryptedGroupV2Context,
                                                                                       null,
                                                                                       System.currentTimeMillis(),
                                                                                       0,
                                                                                       false,
                                                                                       null,
                                                                                       Collections.emptyList(),
                                                                                       Collections.emptyList(),
                                                                                       Collections.emptyList());


    DecryptedGroupChange plainGroupChange = groupMutation.getGroupChange();

    if (plainGroupChange != null && DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(plainGroupChange)) {
      ApplicationDependencies.getJobManager().add(PushGroupSilentUpdateSendJob.create(context, groupId, groupMutation.getNewGroupState(), outgoingMessage));
      return new RecipientAndThread(groupRecipient, -1);
    } else {
      long threadId = MessageSender.send(context, outgoingMessage, -1, false, null);
      return new RecipientAndThread(groupRecipient, threadId);
    }
  }

  private static @NonNull List<RecipientId> getPendingMemberRecipientIds(@NonNull List<DecryptedPendingMember> newPendingMembersList) {
    return Stream.of(DecryptedGroupUtil.pendingToUuidList(newPendingMembersList))
                 .map(uuid-> RecipientId.from(uuid,null))
                 .toList();
  }

  private static @NonNull AccessControl.AccessRequired rightsToAccessControl(@NonNull GroupAccessControl rights) {
    switch (rights){
      case ALL_MEMBERS:
        return AccessControl.AccessRequired.MEMBER;
      case ONLY_ADMINS:
        return AccessControl.AccessRequired.ADMINISTRATOR;
      case NO_ONE:
        return AccessControl.AccessRequired.UNSATISFIABLE;
      default:
      throw new AssertionError();
    }
  }

  static class RecipientAndThread {
    private final Recipient groupRecipient;
    private final long      threadId;

    RecipientAndThread(@NonNull Recipient groupRecipient, long threadId) {
      this.groupRecipient = groupRecipient;
      this.threadId       = threadId;
    }
  }
}
