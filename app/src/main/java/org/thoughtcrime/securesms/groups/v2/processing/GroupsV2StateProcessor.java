package org.thoughtcrime.securesms.groups.v2.processing;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupDoesNotExistException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupMutation;
import org.thoughtcrime.securesms.groups.GroupNotAMemberException;
import org.thoughtcrime.securesms.groups.GroupProtoUtil;
import org.thoughtcrime.securesms.groups.GroupsV2Authorization;
import org.thoughtcrime.securesms.groups.v2.ProfileKeySet;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.AvatarGroupsV2DownloadJob;
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
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
import org.whispersystems.signalservice.api.groupsv2.NotAbleToApplyGroupV2ChangeException;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.exceptions.GroupNotFoundException;
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Advances a groups state to a specified revision.
 */
public final class GroupsV2StateProcessor {

  private static final String TAG = Log.tag(GroupsV2StateProcessor.class);

  public static final int LATEST = GroupStateMapper.LATEST;

  /**
   * Used to mark a group state as a placeholder when there is partial knowledge (title and avater)
   * gathered from a group join link.
   */
  public static final int PLACEHOLDER_REVISION = GroupStateMapper.PLACEHOLDER_REVISION;

  /**
   * Used to mark a group state as a placeholder when you have no knowledge at all of the group
   * e.g. from a group master key from a storage service restore.
   */
  public static final int RESTORE_PLACEHOLDER_REVISION = GroupStateMapper.RESTORE_PLACEHOLDER_REVISION;

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
    this.recipientDatabase     = SignalDatabase.recipients();
    this.groupDatabase         = SignalDatabase.groups();
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
    @Nullable private final DecryptedGroup latestServer;

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
                                                        final long timestamp,
                                                        @Nullable DecryptedGroupChange signedGroupChange)
        throws IOException, GroupNotAMemberException
    {
      if (localIsAtLeast(revision)) {
        return new GroupUpdateResult(GroupState.GROUP_CONSISTENT_OR_AHEAD, null);
      }

      GlobalGroupState inputGroupState = null;

      DecryptedGroup localState = groupDatabase.getGroup(groupId)
                                               .transform(g -> g.requireV2GroupProperties().getDecryptedGroup())
                                               .orNull();

      if (signedGroupChange != null                                       &&
          localState != null                                              &&
          localState.getRevision() + 1 == signedGroupChange.getRevision() &&
          revision == signedGroupChange.getRevision())
      {
        if (SignalStore.internalValues().gv2IgnoreP2PChanges()) {
          Log.w(TAG, "Ignoring P2P group change by setting");
        } else {
          try {
            Log.i(TAG, "Applying P2P group change");
            DecryptedGroup newState = DecryptedGroupUtil.apply(localState, signedGroupChange);

            inputGroupState = new GlobalGroupState(localState, Collections.singletonList(new ServerGroupLogEntry(newState, signedGroupChange)));
          } catch (NotAbleToApplyGroupV2ChangeException e) {
            Log.w(TAG, "Unable to apply P2P group change", e);
          }
        }
      }

      if (inputGroupState == null) {
        try {
          boolean latestRevisionOnly = revision == LATEST && (localState == null || localState.getRevision() == GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION);
          inputGroupState = queryServer(localState, latestRevisionOnly);
        } catch (GroupNotAMemberException e) {
          if (localState != null && signedGroupChange != null) {
            try {
              Log.i(TAG, "Applying P2P group change when not a member");
              DecryptedGroup newState = DecryptedGroupUtil.applyWithoutRevisionCheck(localState, signedGroupChange);

              inputGroupState = new GlobalGroupState(localState, Collections.singletonList(new ServerGroupLogEntry(newState, signedGroupChange)));
            } catch (NotAbleToApplyGroupV2ChangeException failed) {
              Log.w(TAG, "Unable to apply P2P group change when not a member", failed);
            }
          }

          if (inputGroupState == null) {
            if (localState != null && DecryptedGroupUtil.isPendingOrRequesting(localState, Recipient.self().requireAci().uuid())) {
              Log.w(TAG, "Unable to query server for group " + groupId + " server says we're not in group, but we think we are a pending or requesting member");
            } else {
              Log.w(TAG, "Unable to query server for group " + groupId + " server says we're not in group, inserting leave message");
              insertGroupLeave();
            }
            throw e;
          }
        }
      } else {
        Log.i(TAG, "Saved server query for group change");
      }

      AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(inputGroupState, revision);
      DecryptedGroup          newLocalState           = advanceGroupStateResult.getNewGlobalGroupState().getLocalState();

      if (newLocalState == null || newLocalState == inputGroupState.getLocalState()) {
        return new GroupUpdateResult(GroupState.GROUP_CONSISTENT_OR_AHEAD, null);
      }

      updateLocalDatabaseGroupState(inputGroupState, newLocalState);
      determineProfileSharing(inputGroupState, newLocalState);
      if (localState != null && localState.getRevision() == GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION) {
        Log.i(TAG, "Inserting single update message for restore placeholder");
        insertUpdateMessages(timestamp, null, Collections.singleton(new LocalGroupLogEntry(newLocalState, null)));
      } else {
        insertUpdateMessages(timestamp, localState, advanceGroupStateResult.getProcessedLogEntries());
      }
      persistLearnedProfileKeys(inputGroupState);

      GlobalGroupState remainingWork = advanceGroupStateResult.getNewGlobalGroupState();
      if (remainingWork.getServerHistory().size() > 0) {
        Log.i(TAG, String.format(Locale.US, "There are more revisions on the server for this group, scheduling for later, V[%d..%d]", newLocalState.getRevision() + 1, remainingWork.getLatestRevisionNumber()));
        ApplicationDependencies.getJobManager().add(new RequestGroupV2InfoJob(groupId, remainingWork.getLatestRevisionNumber()));
      }

      return new GroupUpdateResult(GroupState.GROUP_UPDATED, newLocalState);
    }

    @WorkerThread
    public @NonNull DecryptedGroup getCurrentGroupStateFromServer()
        throws IOException, GroupNotAMemberException, GroupDoesNotExistException
    {
      try {
        return groupsV2Api.getGroup(groupSecretParams, groupsV2Authorization.getAuthorizationForToday(Recipient.self().requireAci(), groupSecretParams));
      } catch (GroupNotFoundException e) {
        throw new GroupDoesNotExistException(e);
      } catch (NotInGroupException e) {
        throw new GroupNotAMemberException(e);
      } catch (VerificationFailedException | InvalidGroupStateException e) {
        throw new IOException(e);
      }
    }

    @WorkerThread
    public @Nullable DecryptedGroup getSpecificVersionFromServer(int revision)
        throws IOException, GroupNotAMemberException, GroupDoesNotExistException
    {
      try {
        return groupsV2Api.getGroupHistory(groupSecretParams, revision, groupsV2Authorization.getAuthorizationForToday(Recipient.self().requireAci(), groupSecretParams))
                          .get(0)
                          .getGroup()
                          .orNull();
      } catch (GroupNotFoundException e) {
        throw new GroupDoesNotExistException(e);
      } catch (NotInGroupException e) {
        throw new GroupNotAMemberException(e);
      } catch (VerificationFailedException | InvalidGroupStateException e) {
        throw new IOException(e);
      }
    }

    private void insertGroupLeave() {
      if (!groupDatabase.isActive(groupId)) {
        Log.w(TAG, "Group has already been left.");
        return;
      }

      Recipient      groupRecipient = Recipient.externalGroupExact(context, groupId);
      UUID           selfUuid       = Recipient.self().requireAci().uuid();
      DecryptedGroup decryptedGroup = groupDatabase.requireGroup(groupId)
                                                   .requireV2GroupProperties()
                                                   .getDecryptedGroup();

      DecryptedGroup       simulatedGroupState  = DecryptedGroupUtil.removeMember(decryptedGroup, selfUuid, decryptedGroup.getRevision() + 1);
      DecryptedGroupChange simulatedGroupChange = DecryptedGroupChange.newBuilder()
                                                                      .setEditor(UuidUtil.toByteString(UuidUtil.UNKNOWN_UUID))
                                                                      .setRevision(simulatedGroupState.getRevision())
                                                                      .addDeleteMembers(UuidUtil.toByteString(selfUuid))
                                                                      .build();

      DecryptedGroupV2Context    decryptedGroupV2Context = GroupProtoUtil.createDecryptedGroupV2Context(masterKey, new GroupMutation(decryptedGroup, simulatedGroupChange, simulatedGroupState), null);
      OutgoingGroupUpdateMessage leaveMessage            = new OutgoingGroupUpdateMessage(groupRecipient,
                                                                                          decryptedGroupV2Context,
                                                                                          null,
                                                                                          System.currentTimeMillis(),
                                                                                          0,
                                                                                          false,
                                                                                          null,
                                                                                          Collections.emptyList(),
                                                                                          Collections.emptyList(),
                                                                                          Collections.emptyList());

      try {
        MessageDatabase mmsDatabase    = SignalDatabase.mms();
        ThreadDatabase  threadDatabase = SignalDatabase.threads();
        long            threadId       = threadDatabase.getOrCreateThreadIdFor(groupRecipient);
        long            id             = mmsDatabase.insertMessageOutbox(leaveMessage, threadId, false, null);
        mmsDatabase.markAsSent(id, true);
        threadDatabase.update(threadId, false, false);
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

      determineProfileSharing(inputGroupState, newLocalState);
    }

    private void determineProfileSharing(@NonNull GlobalGroupState inputGroupState,
                                         @NonNull DecryptedGroup newLocalState)
    {
      if (inputGroupState.getLocalState() != null) {
        boolean wasAMemberAlready = DecryptedGroupUtil.findMemberByUuid(inputGroupState.getLocalState().getMembersList(), Recipient.self().requireAci().uuid()).isPresent();

        if (wasAMemberAlready) {
          Log.i(TAG, "Skipping profile sharing detection as was already a full member before update");
          return;
        }
      }

      Optional<DecryptedMember> selfAsMemberOptional = DecryptedGroupUtil.findMemberByUuid(newLocalState.getMembersList(), Recipient.self().requireAci().uuid());

      if (selfAsMemberOptional.isPresent()) {
        DecryptedMember     selfAsMember     = selfAsMemberOptional.get();
        int                 revisionJoinedAt = selfAsMember.getJoinedAtRevision();

        Optional<Recipient> addedByOptional  = Stream.of(inputGroupState.getServerHistory())
                                                     .map(ServerGroupLogEntry::getChange)
                                                     .filter(c -> c != null && c.getRevision() == revisionJoinedAt)
                                                     .findFirst()
                                                     .map(c -> Optional.fromNullable(UuidUtil.fromByteStringOrNull(c.getEditor()))
                                                                       .transform(a -> Recipient.externalPush(context, ACI.fromByteStringOrNull(c.getEditor()), null, false)))
                                                     .orElse(Optional.absent());

        if (addedByOptional.isPresent()) {
          Recipient addedBy = addedByOptional.get();

          Log.i(TAG, String.format("Added as a full member of %s by %s", groupId, addedBy.getId()));

          if (addedBy.isSystemContact() || addedBy.isProfileSharing()) {
            Log.i(TAG, "Group 'adder' is trusted. contact: " + addedBy.isSystemContact() + ", profileSharing: " + addedBy.isProfileSharing());
            Log.i(TAG, "Added to a group and auto-enabling profile sharing");
            recipientDatabase.setProfileSharing(Recipient.externalGroupExact(context, groupId).getId(), true);
          } else {
            Log.i(TAG, "Added to a group, but not enabling profile sharing, as 'adder' is not trusted");
          }
        } else {
          Log.w(TAG, "Could not find founding member during gv2 create. Not enabling profile sharing.");
        }
      } else {
        Log.i(TAG, String.format("Added to %s, but not enabling profile sharing as not a fullMember.", groupId));
      }
    }

    private void insertUpdateMessages(long timestamp,
                                      @Nullable DecryptedGroup previousGroupState,
                                      Collection<LocalGroupLogEntry> processedLogEntries)
    {
      for (LocalGroupLogEntry entry : processedLogEntries) {
        if (entry.getChange() != null && DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(entry.getChange()) && !DecryptedGroupUtil.changeIsEmpty(entry.getChange())) {
          Log.d(TAG, "Skipping profile key changes only update message");
        } else {
          if (entry.getChange() != null && DecryptedGroupUtil.changeIsEmpty(entry.getChange()) && previousGroupState != null) {
            Log.w(TAG, "Empty group update message seen. Not inserting.");
          } else {
            storeMessage(GroupProtoUtil.createDecryptedGroupV2Context(masterKey, new GroupMutation(previousGroupState, entry.getChange(), entry.getGroup()), null), timestamp);
            timestamp++;
          }
        }
        previousGroupState = entry.getGroup();
      }
    }

    private void persistLearnedProfileKeys(@NonNull GlobalGroupState globalGroupState) {
      final ProfileKeySet profileKeys = new ProfileKeySet();

      for (ServerGroupLogEntry entry : globalGroupState.getServerHistory()) {
        if (entry.getGroup() != null) {
          profileKeys.addKeysFromGroupState(entry.getGroup());
        }
        if (entry.getChange() != null) {
          profileKeys.addKeysFromGroupChange(entry.getChange());
        }
      }

      Set<RecipientId> updated = recipientDatabase.persistProfileKeySet(profileKeys);

      if (!updated.isEmpty()) {
        Log.i(TAG, String.format(Locale.US, "Learned %d new profile keys, fetching profiles", updated.size()));

        for (Job job : RetrieveProfileJob.forRecipients(updated)) {
          jobManager.runSynchronously(job, 5000);
        }
      }
    }

    private @NonNull GlobalGroupState queryServer(@Nullable DecryptedGroup localState, boolean latestOnly)
        throws IOException, GroupNotAMemberException
    {
      ACI                       selfAci = Recipient.self().requireAci();
      DecryptedGroup            latestServerGroup;
      List<ServerGroupLogEntry> history;

      try {
        latestServerGroup = groupsV2Api.getGroup(groupSecretParams, groupsV2Authorization.getAuthorizationForToday(selfAci, groupSecretParams));
      } catch (NotInGroupException | GroupNotFoundException e) {
        throw new GroupNotAMemberException(e);
      } catch (VerificationFailedException | InvalidGroupStateException e) {
        throw new IOException(e);
      }

      if (latestOnly || !GroupProtoUtil.isMember(selfAci.uuid(), latestServerGroup.getMembersList())) {
        history = Collections.singletonList(new ServerGroupLogEntry(latestServerGroup, null));
      } else {
        int revisionWeWereAdded = GroupProtoUtil.findRevisionWeWereAdded(latestServerGroup, selfAci.uuid());
        int logsNeededFrom      = localState != null ? Math.max(localState.getRevision(), revisionWeWereAdded) : revisionWeWereAdded;

        history = getFullMemberHistory(selfAci, logsNeededFrom);
      }

      return new GlobalGroupState(localState, history);
    }

    private List<ServerGroupLogEntry> getFullMemberHistory(@NonNull ACI selfAci, int logsNeededFromRevision) throws IOException {
      try {
        Collection<DecryptedGroupHistoryEntry> groupStatesFromRevision = groupsV2Api.getGroupHistory(groupSecretParams, logsNeededFromRevision, groupsV2Authorization.getAuthorizationForToday(selfAci, groupSecretParams));
        ArrayList<ServerGroupLogEntry>         history                 = new ArrayList<>(groupStatesFromRevision.size());
        boolean                                ignoreServerChanges     = SignalStore.internalValues().gv2IgnoreServerChanges();

        if (ignoreServerChanges) {
          Log.w(TAG, "Server change logs are ignored by setting");
        }

        for (DecryptedGroupHistoryEntry entry : groupStatesFromRevision) {
          DecryptedGroup       group  = entry.getGroup().orNull();
          DecryptedGroupChange change = ignoreServerChanges ? null : entry.getChange().orNull();

          if (group != null || change != null) {
            history.add(new ServerGroupLogEntry(group, change));
          }
        }

        return history;
      } catch (InvalidGroupStateException | VerificationFailedException e) {
        throw new IOException(e);
      }
    }

    private void storeMessage(@NonNull DecryptedGroupV2Context decryptedGroupV2Context, long timestamp) {
      Optional<ACI> editor = getEditor(decryptedGroupV2Context).transform(ACI::from);

      boolean outgoing = !editor.isPresent() || Recipient.self().requireAci().equals(editor.get());

      if (outgoing) {
        try {
          MessageDatabase            mmsDatabase     = SignalDatabase.mms();
          ThreadDatabase             threadDatabase  = SignalDatabase.threads();
          RecipientId                recipientId     = recipientDatabase.getOrInsertFromGroupId(groupId);
          Recipient                  recipient       = Recipient.resolved(recipientId);
          OutgoingGroupUpdateMessage outgoingMessage = new OutgoingGroupUpdateMessage(recipient, decryptedGroupV2Context, null, timestamp, 0, false, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
          long                       threadId        = threadDatabase.getOrCreateThreadIdFor(recipient);
          long                       messageId       = mmsDatabase.insertMessageOutbox(outgoingMessage, threadId, false, null);

          mmsDatabase.markAsSent(messageId, true);
          threadDatabase.update(threadId, false, false);
        } catch (MmsException e) {
          Log.w(TAG, e);
        }
      } else {
        MessageDatabase                        smsDatabase  = SignalDatabase.sms();
        RecipientId                            sender       = RecipientId.from(editor.get(), null);
        IncomingTextMessage                    incoming     = new IncomingTextMessage(sender, -1, timestamp, timestamp, timestamp, "", Optional.of(groupId), 0, false, null);
        IncomingGroupUpdateMessage             groupMessage = new IncomingGroupUpdateMessage(incoming, decryptedGroupV2Context);
        Optional<MessageDatabase.InsertResult> insertResult = smsDatabase.insertMessageInbox(groupMessage);

        if (insertResult.isPresent()) {
          SignalDatabase.threads().update(insertResult.get().getThreadId(), false, false);
        } else {
          Log.w(TAG, "Could not insert update message");
        }
      }
    }

    private Optional<UUID> getEditor(@NonNull DecryptedGroupV2Context decryptedGroupV2Context) {
      DecryptedGroupChange change       = decryptedGroupV2Context.getChange();
      Optional<UUID>       changeEditor = DecryptedGroupUtil.editorUuid(change);
      if (changeEditor.isPresent()) {
        return changeEditor;
      } else {
        Optional<DecryptedPendingMember> pendingByUuid = DecryptedGroupUtil.findPendingByUuid(decryptedGroupV2Context.getGroupState().getPendingMembersList(), Recipient.self().requireAci().uuid());
        if (pendingByUuid.isPresent()) {
          return Optional.fromNullable(UuidUtil.fromByteStringOrNull(pendingByUuid.get().getAddedByUuid()));
        }
      }
      return Optional.absent();
    }
  }
}
