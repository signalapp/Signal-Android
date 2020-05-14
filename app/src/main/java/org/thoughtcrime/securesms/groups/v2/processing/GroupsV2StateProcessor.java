package org.thoughtcrime.securesms.groups.v2.processing;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupNotAMemberException;
import org.thoughtcrime.securesms.groups.GroupProtoUtil;
import org.thoughtcrime.securesms.groups.GroupsV2Authorization;
import org.thoughtcrime.securesms.groups.v2.ProfileKeySet;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.AvatarGroupsV2DownloadJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingGroupUpdateMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.IncomingGroupUpdateMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupHistoryEntry;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Advances a groups state to a specified revision.
 */
public final class GroupsV2StateProcessor {

  private static final String TAG = Log.tag(GroupsV2StateProcessor.class);

  public static final int LATEST = GroupStateMapper.LATEST;

  private final Context               context;
  private final JobManager            jobManager;
  private final RecipientDatabase     recipientDatabase;
  private final GroupDatabase         groupDatabase;
  private final GroupsV2Authorization groupsV2Authorization;
  private final GroupsV2Api           groupsV2Api;

  public GroupsV2StateProcessor(@NonNull Context context) {
    this.context               = context.getApplicationContext();
    this.jobManager            = ApplicationDependencies.getJobManager();
    this.groupsV2Authorization = ApplicationDependencies.getGroupsV2Authorization();
    this.groupsV2Api           = ApplicationDependencies.getSignalServiceAccountManager().getGroupsV2Api();
    this.recipientDatabase     = DatabaseFactory.getRecipientDatabase(context);
    this.groupDatabase         = DatabaseFactory.getGroupDatabase(context);
  }

  public StateProcessorForGroup forGroup(@NonNull GroupMasterKey groupMasterKey) {
    return new StateProcessorForGroup(groupMasterKey);
  }

  public enum GroupState {
    /**
     * The message revision was inconsistent with server revision, should ignore
     */
    INCONSISTENT,

    /**
     * The local group was successfully updated to be consistent with the message revision
     */
    GROUP_UPDATED,

    /**
     * The local group is already consistent with the message revision or is ahead of the message revision
     */
    GROUP_CONSISTENT_OR_AHEAD
  }

  public static class GroupUpdateResult {
              private final GroupState     groupState;
    @Nullable private       DecryptedGroup latestServer;

    GroupUpdateResult(@NonNull GroupState groupState, @Nullable DecryptedGroup latestServer) {
      this.groupState   = groupState;
      this.latestServer = latestServer;
    }

    public GroupState getGroupState() {
      return groupState;
    }

    public @Nullable DecryptedGroup getLatestServer() {
      return latestServer;
    }
  }

  public final class StateProcessorForGroup {
    private final GroupMasterKey    masterKey;
    private final GroupId.V2        groupId;
    private final GroupSecretParams groupSecretParams;

    private StateProcessorForGroup(@NonNull GroupMasterKey groupMasterKey) {
      this.masterKey         = groupMasterKey;
      this.groupId           = GroupId.v2(masterKey);
      this.groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
    }

    /**
     * Using network where required, will attempt to bring the local copy of the group up to the revision specified.
     *
     * @param revision use {@link #LATEST} to get latest.
     */
    @WorkerThread
    public GroupUpdateResult updateLocalGroupToRevision(final int revision,
                                                        final long timestamp)
        throws IOException, GroupNotAMemberException
    {
      if (localIsAtLeast(revision)) {
        return new GroupUpdateResult(GroupState.GROUP_CONSISTENT_OR_AHEAD, null);
      }

      GlobalGroupState inputGroupState;
      try {
        inputGroupState = queryServer();
      } catch (GroupNotAMemberException e) {
        Log.w(TAG, "Unable to query server for group " + groupId + " server says we're not in group, inserting leave message");
        insertGroupLeave();
        throw e;
      }
      AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(inputGroupState, revision);
      DecryptedGroup          newLocalState           = advanceGroupStateResult.getNewGlobalGroupState().getLocalState();

      if (newLocalState == null || newLocalState == inputGroupState.getLocalState()) {
        return new GroupUpdateResult(GroupState.GROUP_CONSISTENT_OR_AHEAD, null);
      }

      updateLocalDatabaseGroupState(inputGroupState, newLocalState);
      insertUpdateMessages(timestamp, advanceGroupStateResult.getProcessedLogEntries());
      persistLearnedProfileKeys(inputGroupState);

      GlobalGroupState remainingWork = advanceGroupStateResult.getNewGlobalGroupState();
      if (remainingWork.getHistory().size() > 0) {
        Log.i(TAG, String.format(Locale.US, "There are more versions on the server for this group, not applying at this time, V[%d..%d]", newLocalState.getVersion() + 1, remainingWork.getLatestVersionNumber()));
      }

      return new GroupUpdateResult(GroupState.GROUP_UPDATED, newLocalState);
    }

    private void insertGroupLeave() {
      if (!groupDatabase.isActive(groupId)) {
        Log.w(TAG, "Group has already been left.");
        return;
      }

      Recipient      groupRecipient = Recipient.externalGroup(context, groupId);
      UUID           selfUuid       = Recipient.self().getUuid().get();
      DecryptedGroup decryptedGroup = groupDatabase.requireGroup(groupId)
                                                   .requireV2GroupProperties()
                                                   .getDecryptedGroup();

      DecryptedGroup       simulatedGroupState  = DecryptedGroupUtil.removeMember(decryptedGroup, selfUuid, decryptedGroup.getVersion() + 1);
      DecryptedGroupChange simulatedGroupChange = DecryptedGroupChange.newBuilder()
                                                                      .setEditor(UuidUtil.toByteString(UuidUtil.UNKNOWN_UUID))
                                                                      .setVersion(simulatedGroupState.getVersion())
                                                                      .addDeleteMembers(UuidUtil.toByteString(selfUuid))
                                                                      .build();

      DecryptedGroupV2Context    decryptedGroupV2Context = GroupProtoUtil.createDecryptedGroupV2Context(masterKey, simulatedGroupState, simulatedGroupChange);
      OutgoingGroupUpdateMessage leaveMessage            = new OutgoingGroupUpdateMessage(groupRecipient,
                                                                                          decryptedGroupV2Context,
                                                                                          null,
                                                                                          System.currentTimeMillis(),
                                                                                          0,
                                                                                          false,
                                                                                          null,
                                                                                          Collections.emptyList(),
                                                                                          Collections.emptyList());

      try {
        MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(context);
        long        threadId    = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
        long        id          = mmsDatabase.insertMessageOutbox(leaveMessage, threadId, false, null);
        mmsDatabase.markAsSent(id, true);
      } catch (MmsException e) {
        Log.w(TAG, "Failed to insert leave message.", e);
      }

      groupDatabase.setActive(groupId, false);
      groupDatabase.remove(groupId, Recipient.self().getId());
    }

    /**
     * @return true iff group exists locally and is at least the specified revision.
     */
    private boolean localIsAtLeast(int revision) {
      if (groupDatabase.isUnknownGroup(groupId) || revision == LATEST) {
        return false;
      }
      int dbRevision = groupDatabase.getGroup(groupId).get().requireV2GroupProperties().getGroupRevision();
      return revision <= dbRevision;
    }

    private void updateLocalDatabaseGroupState(@NonNull GlobalGroupState inputGroupState,
                                               @NonNull DecryptedGroup newLocalState)
    {
      boolean needsAvatarFetch;

      if (inputGroupState.getLocalState() == null) {
        groupDatabase.create(masterKey, newLocalState);
        needsAvatarFetch = !TextUtils.isEmpty(newLocalState.getAvatar());
      } else {
        groupDatabase.update(masterKey, newLocalState);
        needsAvatarFetch = !newLocalState.getAvatar().equals(inputGroupState.getLocalState().getAvatar());
      }

      if (needsAvatarFetch) {
        jobManager.add(new AvatarGroupsV2DownloadJob(groupId, newLocalState.getAvatar()));
      }

      final boolean fullMemberPostUpdate = GroupProtoUtil.isMember(Recipient.self().getUuid().get(), newLocalState.getMembersList());
      if (fullMemberPostUpdate) {
        recipientDatabase.setProfileSharing(Recipient.externalGroup(context, groupId).getId(), true);
      }
    }

    private void insertUpdateMessages(long timestamp, Collection<GroupLogEntry> processedLogEntries) {
      for (GroupLogEntry entry : processedLogEntries) {
        storeMessage(GroupProtoUtil.createDecryptedGroupV2Context(masterKey, entry.getGroup(), entry.getChange()), timestamp);
      }
    }

    private void persistLearnedProfileKeys(@NonNull GlobalGroupState globalGroupState) {
      final ProfileKeySet profileKeys = new ProfileKeySet();

      for (GroupLogEntry entry : globalGroupState.getHistory()) {
        profileKeys.addKeysFromGroupState(entry.getGroup(), DecryptedGroupUtil.editorUuid(entry.getChange()));
      }

      Collection<RecipientId> updated = recipientDatabase.persistProfileKeySet(profileKeys);

      if (!updated.isEmpty()) {
        Log.i(TAG, String.format(Locale.US, "Learned %d new profile keys, scheduling profile retrievals", updated.size()));
        for (RecipientId recipient : updated) {
          ApplicationDependencies.getJobManager().add(RetrieveProfileJob.forRecipient(recipient));
        }
      }
    }

    private GlobalGroupState queryServer()
        throws IOException, GroupNotAMemberException
    {
      DecryptedGroup      latestServerGroup;
      List<GroupLogEntry> history;
      UUID                selfUuid   = Recipient.self().getUuid().get();
      DecryptedGroup      localState = groupDatabase.getGroup(groupId)
                                                    .transform(g -> g.requireV2GroupProperties().getDecryptedGroup())
                                                    .orNull();

      try {
        latestServerGroup = groupsV2Api.getGroup(groupSecretParams, groupsV2Authorization.getAuthorizationForToday(selfUuid, groupSecretParams));
      } catch (NotInGroupException e) {
        throw new GroupNotAMemberException(e);
      } catch (VerificationFailedException | InvalidGroupStateException e) {
        throw new IOException(e);
      }

      int versionWeWereAdded = GroupProtoUtil.findVersionWeWereAdded(latestServerGroup, selfUuid);
      int logsNeededFrom     = localState != null ? Math.max(localState.getVersion(), versionWeWereAdded) : versionWeWereAdded;

      if (GroupProtoUtil.isMember(selfUuid, latestServerGroup.getMembersList())) {
        history = getFullMemberHistory(selfUuid, logsNeededFrom);
      } else {
        history = Collections.singletonList(new GroupLogEntry(latestServerGroup, null));
      }

      return new GlobalGroupState(localState, history);
    }

    private List<GroupLogEntry> getFullMemberHistory(@NonNull UUID selfUuid, int logsNeededFrom) throws IOException {
      try {
        Collection<DecryptedGroupHistoryEntry> groupStatesFromRevision = groupsV2Api.getGroupHistory(groupSecretParams, logsNeededFrom, groupsV2Authorization.getAuthorizationForToday(selfUuid, groupSecretParams));
        ArrayList<GroupLogEntry>               history                 = new ArrayList<>(groupStatesFromRevision.size());

        for (DecryptedGroupHistoryEntry entry : groupStatesFromRevision) {
          history.add(new GroupLogEntry(entry.getGroup(), entry.getChange()));
        }

        return history;
      } catch (InvalidGroupStateException | VerificationFailedException e) {
        throw new IOException(e);
      }
    }

    private void storeMessage(@NonNull DecryptedGroupV2Context decryptedGroupV2Context, long timestamp) {
      UUID editor = DecryptedGroupUtil.editorUuid(decryptedGroupV2Context.getChange());
      boolean outgoing = Recipient.self().getUuid().get().equals(editor);

      if (outgoing) {
        try {
          MmsDatabase                mmsDatabase     = DatabaseFactory.getMmsDatabase(context);
          RecipientId                recipientId     = recipientDatabase.getOrInsertFromGroupId(groupId);
          Recipient                  recipient       = Recipient.resolved(recipientId);
          OutgoingGroupUpdateMessage outgoingMessage = new OutgoingGroupUpdateMessage(recipient, decryptedGroupV2Context, null, timestamp, 0, false, null, Collections.emptyList(), Collections.emptyList());
          long                       threadId        = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
          long                       messageId       = mmsDatabase.insertMessageOutbox(outgoingMessage, threadId, false, null);

          mmsDatabase.markAsSent(messageId, true);
        } catch (MmsException e) {
          Log.w(TAG, e);
        }
      } else {
        SmsDatabase                smsDatabase  = DatabaseFactory.getSmsDatabase(context);
        RecipientId                sender       = Recipient.externalPush(context, editor, null).getId();
        IncomingTextMessage        incoming     = new IncomingTextMessage(sender, -1, timestamp, timestamp, "", Optional.of(groupId), 0, false);
        IncomingGroupUpdateMessage groupMessage = new IncomingGroupUpdateMessage(incoming, decryptedGroupV2Context);

        smsDatabase.insertMessageInbox(groupMessage);
      }
    }
  }
}
