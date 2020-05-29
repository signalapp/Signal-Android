package org.thoughtcrime.securesms.groups;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.groups.UuidCiphertext;
import org.signal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.v2.GroupCandidateHelper;
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.OutgoingGroupUpdateMessage;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupCandidate;
import org.whispersystems.signalservice.api.groupsv2.GroupChangeUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.ConflictException;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.exceptions.GroupPatchNotAcceptedException;
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class GroupManagerV2 {

  private static final String TAG = Log.tag(GroupManagerV2.class);

  private final Context                   context;
  private final GroupDatabase             groupDatabase;
  private final GroupsV2Api               groupsV2Api;
  private final GroupsV2Operations        groupsV2Operations;
  private final GroupsV2Authorization     authorization;
  private final GroupsV2StateProcessor    groupsV2StateProcessor;
  private final UUID                      selfUuid;
  private final GroupCandidateHelper      groupCandidateHelper;
  private final GroupsV2CapabilityChecker capabilityChecker;

  GroupManagerV2(@NonNull Context context) {
    this.context                = context;
    this.groupDatabase          = DatabaseFactory.getGroupDatabase(context);
    this.groupsV2Api            = ApplicationDependencies.getSignalServiceAccountManager().getGroupsV2Api();
    this.groupsV2Operations     = ApplicationDependencies.getGroupsV2Operations();
    this.authorization          = ApplicationDependencies.getGroupsV2Authorization();
    this.groupsV2StateProcessor = ApplicationDependencies.getGroupsV2StateProcessor();
    this.selfUuid               = Recipient.self().getUuid().get();
    this.groupCandidateHelper   = new GroupCandidateHelper(context);
    this.capabilityChecker      = new GroupsV2CapabilityChecker();
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
  GroupUpdater updater(@NonNull GroupMasterKey groupId) throws GroupChangeBusyException {
    return new GroupUpdater(groupId, GroupsV2ProcessingLock.acquireGroupProcessingLock());
  }

  class GroupCreator implements Closeable {

    private final Closeable lock;

    GroupCreator(@NonNull Closeable lock) {
      this.lock = lock;
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult createGroup(@NonNull Collection<RecipientId> members,
                                                        @Nullable String name,
                                                        @Nullable byte[] avatar)
        throws GroupChangeFailedException, IOException, MembershipNotSuitableForV2Exception
    {
      if (!capabilityChecker.allAndSelfSupportGroupsV2AndUuid(members)) {
        throw new MembershipNotSuitableForV2Exception("At least one potential new member does not support GV2 or UUID capabilities");
      }

      GroupCandidate      self       = groupCandidateHelper.recipientIdToCandidate(Recipient.self().getId());
      Set<GroupCandidate> candidates = new HashSet<>(groupCandidateHelper.recipientIdsToCandidates(members));

      if (!self.hasProfileKeyCredential()) {
        Log.w(TAG, "Cannot create a V2 group as self does not have a versioned profile");
        throw new MembershipNotSuitableForV2Exception("Cannot create a V2 group as self does not have a versioned profile");
      }

      GroupsV2Operations.NewGroup newGroup = groupsV2Operations.createNewGroup(name,
                                                                               Optional.fromNullable(avatar),
                                                                               self,
                                                                               candidates);

      GroupSecretParams groupSecretParams = newGroup.getGroupSecretParams();
      GroupMasterKey    masterKey         = groupSecretParams.getMasterKey();

      try {
        groupsV2Api.putNewGroup(newGroup, authorization.getAuthorizationForToday(Recipient.self().requireUuid(), groupSecretParams));

        DecryptedGroup decryptedGroup = groupsV2Api.getGroup(groupSecretParams, ApplicationDependencies.getGroupsV2Authorization().getAuthorizationForToday(Recipient.self().requireUuid(), groupSecretParams));
        if (decryptedGroup == null) {
          throw new GroupChangeFailedException();
        }

        GroupId.V2  groupId          = groupDatabase.create(masterKey, decryptedGroup);
        RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
        Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);

        AvatarHelper.setAvatar(context, groupRecipientId, avatar != null ? new ByteArrayInputStream(avatar) : null);
        groupDatabase.onAvatarUpdated(groupId, avatar != null);
        DatabaseFactory.getRecipientDatabase(context).setProfileSharing(groupRecipient.getId(), true);

        return sendGroupUpdate(masterKey, decryptedGroup, null, null);
      } catch (VerificationFailedException | InvalidGroupStateException e) {
        throw new GroupChangeFailedException(e);
      }
    }

    @Override
    public void close() throws IOException {
      lock.close();
    }
  }

  class GroupEditor implements Closeable {

    private final Closeable                          lock;
    private final GroupId.V2                         groupId;
    private final GroupMasterKey                     groupMasterKey;
    private final GroupSecretParams                  groupSecretParams;
    private final GroupsV2Operations.GroupOperations groupOperations;

    GroupEditor(@NonNull GroupId.V2 groupId, @NonNull Closeable lock) {
      GroupDatabase.GroupRecord       groupRecord       = groupDatabase.requireGroup(groupId);
      GroupDatabase.V2GroupProperties v2GroupProperties = groupRecord.requireV2GroupProperties();

      this.lock              = lock;
      this.groupId           = groupId;
      this.groupMasterKey    = v2GroupProperties.getGroupMasterKey();
      this.groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
      this.groupOperations   = groupsV2Operations.forGroup(groupSecretParams);
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult addMembers(@NonNull Collection<RecipientId> newMembers)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, MembershipNotSuitableForV2Exception
    {
      if (!capabilityChecker.allSupportGroupsV2AndUuid(newMembers)) {
        throw new MembershipNotSuitableForV2Exception("At least one potential new member does not support GV2 or UUID capabilities");
      }

      Set<GroupCandidate> groupCandidates = groupCandidateHelper.recipientIdsToCandidates(new HashSet<>(newMembers));
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
        GroupChange.Actions.Builder change = groupOperations.createModifyGroupTitleAndMembershipChange(Optional.fromNullable(title), Collections.emptySet(), Collections.emptySet());

        if (avatarChanged) {
          String cdnKey = avatarBytes != null ? groupsV2Api.uploadAvatar(avatarBytes, groupSecretParams, authorization.getAuthorizationForToday(selfUuid, groupSecretParams))
                                              : "";
          change.setModifyAvatar(GroupChange.Actions.ModifyAvatarAction.newBuilder()
                                                    .setAvatar(cdnKey));
        }

        GroupManager.GroupActionResult groupActionResult = commitChangeWithConflictResolution(change);

        if (avatarChanged) {
          AvatarHelper.setAvatar(context, Recipient.externalGroup(context, groupId).getId(), avatarBytes != null ? new ByteArrayInputStream(avatarBytes) : null);
          groupDatabase.onAvatarUpdated(groupId, avatarBytes != null);
        }

        return groupActionResult;
      } catch (VerificationFailedException e) {
        throw new GroupChangeFailedException(e);
      }
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult cancelInvites(@NonNull Collection<UuidCiphertext> uuidCipherTexts)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      return commitChangeWithConflictResolution(groupOperations.createRemoveInvitationChange(new HashSet<>(uuidCipherTexts)));
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
      return ejectMember(Recipient.self().getId());
    }

    @WorkerThread
    @NonNull GroupManager.GroupActionResult ejectMember(@NonNull RecipientId recipientId)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      Recipient recipient = Recipient.resolved(recipientId);

      return commitChangeWithConflictResolution(groupOperations.createRemoveMembersChange(Collections.singleton(recipient.getUuid().get())));
    }

    @WorkerThread
    @Nullable GroupManager.GroupActionResult updateSelfProfileKeyInGroup()
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      ProfileKey                profileKey  = ProfileKeyUtil.getSelfProfileKey();
      DecryptedGroup            group       = groupDatabase.requireGroup(groupId).requireV2GroupProperties().getDecryptedGroup();
      Optional<DecryptedMember> selfInGroup = DecryptedGroupUtil.findMemberByUuid(group.getMembersList(), selfUuid);

      if (!selfInGroup.isPresent()) {
        Log.w(TAG, "Self not in group");
        return null;
      }

      if (Arrays.equals(profileKey.serialize(), selfInGroup.get().getProfileKey().toByteArray())) {
        Log.i(TAG, "Own Profile Key is already up to date in group " + groupId);
        return null;
      }

      GroupCandidate groupCandidate = groupCandidateHelper.recipientIdToCandidate(Recipient.self().getId());

      if (!groupCandidate.hasProfileKeyCredential()) {
        Log.w(TAG, "No credential available");
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
            Recipient groupRecipient = Recipient.externalGroup(context, groupId);
            long      threadId       = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);

            return new GroupManager.GroupActionResult(groupRecipient, threadId);
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
      final GroupDatabase.GroupRecord       groupRecord       = groupDatabase.requireGroup(groupId);
      final GroupDatabase.V2GroupProperties v2GroupProperties = groupRecord.requireV2GroupProperties();
      final int                             nextRevision      = v2GroupProperties.getGroupRevision() + 1;
      final GroupChange.Actions             changeActions     = change.setRevision(nextRevision).build();
      final DecryptedGroupChange            decryptedChange;
      final DecryptedGroup                  decryptedGroupState;

      try {
        decryptedChange     = groupOperations.decryptChange(changeActions, selfUuid);
        decryptedGroupState = DecryptedGroupUtil.apply(v2GroupProperties.getDecryptedGroup(), decryptedChange);
      } catch (VerificationFailedException | InvalidGroupStateException | DecryptedGroupUtil.NotAbleToApplyChangeException e) {
        Log.w(TAG, e);
        throw new IOException(e);
      }

      GroupChange signedGroupChange = commitToServer(changeActions);
      groupDatabase.update(groupId, decryptedGroupState);

      return sendGroupUpdate(groupMasterKey, decryptedGroupState, decryptedChange, signedGroupChange);
    }

    private GroupChange commitToServer(GroupChange.Actions change)
        throws GroupNotAMemberException, GroupChangeFailedException, IOException, GroupInsufficientRightsException
    {
      try {
        return groupsV2Api.patchGroup(change, authorization.getAuthorizationForToday(selfUuid, groupSecretParams));
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

    @Override
    public void close() throws IOException {
      lock.close();
    }
  }

  class GroupUpdater implements Closeable {

    private final Closeable      lock;
    private final GroupMasterKey groupMasterKey;

    GroupUpdater(@NonNull GroupMasterKey groupMasterKey, @NonNull Closeable lock) {
      this.lock           = lock;
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
          return groupOperations.decryptChange(GroupChange.parseFrom(signedGroupChange), true);
        } catch (VerificationFailedException | InvalidGroupStateException | InvalidProtocolBufferException e) {
          Log.w(TAG, "Unable to verify supplied group change", e);
        }
      }

      return null;
    }

    @Override
    public void close() throws IOException {
      lock.close();
    }
  }

  private @NonNull GroupManager.GroupActionResult sendGroupUpdate(@NonNull GroupMasterKey masterKey,
                                                                  @NonNull DecryptedGroup decryptedGroup,
                                                                  @Nullable DecryptedGroupChange plainGroupChange,
                                                                  @Nullable GroupChange signedGroupChange)
  {
    GroupId.V2                groupId                 = GroupId.v2(masterKey);
    Recipient                 groupRecipient          = Recipient.externalGroup(context, groupId);
    DecryptedGroupV2Context   decryptedGroupV2Context = GroupProtoUtil.createDecryptedGroupV2Context(masterKey, decryptedGroup, plainGroupChange, signedGroupChange);
    OutgoingGroupUpdateMessage outgoingMessage        = new OutgoingGroupUpdateMessage(groupRecipient,
                                                                                       decryptedGroupV2Context,
                                                                                       null,
                                                                                       System.currentTimeMillis(),
                                                                                       0,
                                                                                       false,
                                                                                       null,
                                                                                       Collections.emptyList(),
                                                                                       Collections.emptyList());

    long threadId = MessageSender.send(context, outgoingMessage, -1, false, null);

    return new GroupManager.GroupActionResult(groupRecipient, threadId);
  }

  private static @NonNull AccessControl.AccessRequired rightsToAccessControl(@NonNull GroupAccessControl rights) {
    switch (rights){
      case ALL_MEMBERS:
        return AccessControl.AccessRequired.MEMBER;
      case ONLY_ADMINS:
        return AccessControl.AccessRequired.ADMINISTRATOR;
      default:
      throw new AssertionError();
    }
  }
}
