package org.thoughtcrime.securesms.groups;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupChangeUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Authorization;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.ConflictException;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
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

  GroupManagerV2(@NonNull Context context) {
    this.context                = context;
    this.groupDatabase          = DatabaseFactory.getGroupDatabase(context);
    this.groupsV2Api            = ApplicationDependencies.getSignalServiceAccountManager().getGroupsV2Api();
    this.groupsV2Operations     = ApplicationDependencies.getGroupsV2Operations();
    this.authorization          = ApplicationDependencies.getGroupsV2Authorization();
    this.groupsV2StateProcessor = ApplicationDependencies.getGroupsV2StateProcessor();
    this.selfUuid               = Recipient.self().getUuid().get();
  }

  @WorkerThread
  GroupEditor edit(@NonNull GroupId.V2 groupId) throws GroupChangeBusyException {
    return new GroupEditor(groupId, GroupsV2ProcessingLock.acquireGroupProcessingLock());
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
    @NonNull GroupManager.GroupActionResult updateGroupTimer(int expirationTime)
        throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException
    {
      return commitChangeWithConflictResolution(groupOperations.createModifyGroupTimerChange(expirationTime));
    }

    void updateLocalToServerVersion(int version)
        throws IOException, GroupNotAMemberException
    {
        new GroupsV2StateProcessor(context).forGroup(groupMasterKey)
                                           .updateLocalGroupToRevision(version, System.currentTimeMillis());
    }

    private GroupManager.GroupActionResult commitChangeWithConflictResolution(GroupChange.Actions.Builder change)
        throws GroupChangeFailedException, GroupNotAMemberException, GroupInsufficientRightsException, IOException
    {
      change.setSourceUuid(UuidUtil.toByteString(Recipient.self().getUuid().get()));

      for (int attempt = 0; attempt < 5; attempt++) {
        try {
          return commitChange(change);
        } catch (ConflictException e) {
          Log.w(TAG, "Conflict on group");
          GroupsV2StateProcessor.GroupUpdateResult groupUpdateResult = groupsV2StateProcessor.forGroup(groupMasterKey)
                                                                                              .updateLocalGroupToRevision(GroupsV2StateProcessor.LATEST, System.currentTimeMillis());

          if (groupUpdateResult.getGroupState() != GroupsV2StateProcessor.GroupState.GROUP_UPDATED || groupUpdateResult.getLatestServer() == null) {
            throw new GroupChangeFailedException();
          }

          Log.w(TAG, "Group has been updated");
          try {
            change = GroupChangeUtil.resolveConflict(groupUpdateResult.getLatestServer(),
                                                     groupOperations.decryptChange(change.build(), selfUuid),
                                                     change.build());
          } catch (VerificationFailedException | InvalidGroupStateException ex) {
            throw new GroupChangeFailedException(ex);
          }
        }
      }

      throw new GroupChangeFailedException("Unable to apply change to group after conflicts");
    }

    private GroupManager.GroupActionResult commitChange(GroupChange.Actions.Builder change)
        throws GroupNotAMemberException, GroupChangeFailedException, IOException, GroupInsufficientRightsException
    {
      final GroupDatabase.GroupRecord       groupRecord       = groupDatabase.requireGroup(groupId);
      final GroupDatabase.V2GroupProperties v2GroupProperties = groupRecord.requireV2GroupProperties();
      final int                             nextRevision      = v2GroupProperties.getGroupRevision() + 1;
      final GroupChange.Actions             changeActions     = change.setVersion(nextRevision).build();
      final DecryptedGroupChange            decryptedChange;
      final DecryptedGroup                  decryptedGroupState;

      try {
        decryptedChange     = groupOperations.decryptChange(changeActions, selfUuid);
        decryptedGroupState = DecryptedGroupUtil.apply(v2GroupProperties.getDecryptedGroup(), decryptedChange);
      } catch (VerificationFailedException | InvalidGroupStateException | DecryptedGroupUtil.NotAbleToApplyChangeException e) {
        Log.w(TAG, e);
        throw new IOException(e);
      }

      commitToServer(changeActions);
      groupDatabase.update(groupId, decryptedGroupState);

      return sendGroupUpdate(groupMasterKey, decryptedGroupState, decryptedChange);
    }

    private void commitToServer(GroupChange.Actions change)
        throws GroupNotAMemberException, GroupChangeFailedException, IOException, GroupInsufficientRightsException
    {
      try {
        groupsV2Api.patchGroup(change, groupSecretParams, authorization);
      } catch (NotInGroupException e) {
        Log.w(TAG, e);
        throw new GroupNotAMemberException(e);
      } catch (AuthorizationFailedException e) {
        Log.w(TAG, e);
        throw new GroupInsufficientRightsException(e);
      } catch (VerificationFailedException | InvalidGroupStateException e) {
        Log.w(TAG, e);
        throw new GroupChangeFailedException(e);
      }
    }

    private @NonNull GroupManager.GroupActionResult sendGroupUpdate(@NonNull GroupMasterKey masterKey,
                                                                    @NonNull DecryptedGroup decryptedGroup,
                                                                    @Nullable DecryptedGroupChange plainGroupChange)
    {
      RecipientId               groupRecipientId        = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
      Recipient                 groupRecipient          = Recipient.resolved(groupRecipientId);
      DecryptedGroupV2Context   decryptedGroupV2Context = GroupProtoUtil.createDecryptedGroupV2Context(masterKey, decryptedGroup, plainGroupChange);
      OutgoingGroupMediaMessage outgoingMessage         = new OutgoingGroupMediaMessage(groupRecipient,
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

    @Override
    public void close() throws IOException {
      lock.close();
    }
  }
}
