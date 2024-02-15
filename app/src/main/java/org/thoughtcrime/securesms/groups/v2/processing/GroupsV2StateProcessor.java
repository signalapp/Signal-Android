package org.thoughtcrime.securesms.groups.v2.processing;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.GroupRecord;
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
import org.thoughtcrime.securesms.jobs.AvatarGroupsV2DownloadJob;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.jobs.LeaveGroupV2Job;
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mms.IncomingMessage;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupHistoryEntry;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupChangeReconstruct;
import org.whispersystems.signalservice.api.groupsv2.GroupHistoryPage;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.groupsv2.NotAbleToApplyGroupV2ChangeException;
import org.whispersystems.signalservice.api.groupsv2.PartialDecryptedGroup;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceIds;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.exceptions.GroupNotFoundException;
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Advances a groups state to a specified revision.
 */
public class GroupsV2StateProcessor {

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

  private final Context        context;
  private final RecipientTable recipientTable;
  private final GroupTable     groupDatabase;
  private final GroupsV2Authorization groupsV2Authorization;
  private final GroupsV2Api           groupsV2Api;

  public GroupsV2StateProcessor(@NonNull Context context) {
    this.context               = context.getApplicationContext();
    this.groupsV2Authorization = ApplicationDependencies.getGroupsV2Authorization();
    this.groupsV2Api           = ApplicationDependencies.getSignalServiceAccountManager().getGroupsV2Api();
    this.recipientTable        = SignalDatabase.recipients();
    this.groupDatabase         = SignalDatabase.groups();
  }

  public StateProcessorForGroup forGroup(@NonNull ServiceIds serviceIds, @NonNull GroupMasterKey groupMasterKey) {
    return forGroup(serviceIds, groupMasterKey, null);
  }

  public StateProcessorForGroup forGroup(@NonNull ServiceIds serviceIds, @NonNull GroupMasterKey groupMasterKey, @Nullable GroupSecretParams groupSecretParams) {
    if (groupSecretParams == null) {
      return new StateProcessorForGroup(serviceIds, context, groupDatabase, groupsV2Api, groupsV2Authorization, groupMasterKey, recipientTable);
    } else {
      return new StateProcessorForGroup(serviceIds, context, groupDatabase, groupsV2Api, groupsV2Authorization, groupMasterKey, groupSecretParams, recipientTable);
    }
  }

  public enum GroupState {
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
    private final DecryptedGroup latestServer;

    GroupUpdateResult(@NonNull GroupState groupState, @Nullable DecryptedGroup latestServer) {
      this.groupState   = groupState;
      this.latestServer = latestServer;
    }

    public @NonNull GroupState getGroupState() {
      return groupState;
    }

    public @Nullable DecryptedGroup getLatestServer() {
      return latestServer;
    }
  }

  public static final class StateProcessorForGroup {
    private final ServiceIds              serviceIds;
    private final GroupTable              groupDatabase;
    private final GroupsV2Api             groupsV2Api;
    private final GroupsV2Authorization   groupsV2Authorization;
    private final GroupMasterKey          masterKey;
    private final GroupId.V2              groupId;
    private final GroupSecretParams       groupSecretParams;
    private final ProfileAndMessageHelper profileAndMessageHelper;

    private StateProcessorForGroup(@NonNull ServiceIds serviceIds,
                                   @NonNull Context context,
                                   @NonNull GroupTable groupDatabase,
                                   @NonNull GroupsV2Api groupsV2Api,
                                   @NonNull GroupsV2Authorization groupsV2Authorization,
                                   @NonNull GroupMasterKey groupMasterKey,
                                   @NonNull RecipientTable recipientTable)
    {
      this(serviceIds, context, groupDatabase, groupsV2Api, groupsV2Authorization, groupMasterKey, GroupSecretParams.deriveFromMasterKey(groupMasterKey), recipientTable);
    }

    private StateProcessorForGroup(@NonNull ServiceIds serviceIds,
                                   @NonNull Context context,
                                   @NonNull GroupTable groupDatabase,
                                   @NonNull GroupsV2Api groupsV2Api,
                                   @NonNull GroupsV2Authorization groupsV2Authorization,
                                   @NonNull GroupMasterKey groupMasterKey,
                                   @NonNull GroupSecretParams groupSecretParams,
                                   @NonNull RecipientTable recipientTable)
    {
      this.serviceIds              = serviceIds;
      this.groupDatabase           = groupDatabase;
      this.groupsV2Api             = groupsV2Api;
      this.groupsV2Authorization   = groupsV2Authorization;
      this.masterKey               = groupMasterKey;
      this.groupSecretParams       = groupSecretParams;
      this.groupId                 = GroupId.v2(groupSecretParams.getPublicParams().getGroupIdentifier());
      this.profileAndMessageHelper = new ProfileAndMessageHelper(context, serviceIds.getAci(), groupMasterKey, groupId, recipientTable);
    }

    @VisibleForTesting StateProcessorForGroup(@NonNull ServiceIds serviceIds,
                                              @NonNull GroupTable groupDatabase,
                                              @NonNull GroupsV2Api groupsV2Api,
                                              @NonNull GroupsV2Authorization groupsV2Authorization,
                                              @NonNull GroupMasterKey groupMasterKey,
                                              @NonNull ProfileAndMessageHelper profileAndMessageHelper)
    {
      this.serviceIds              = serviceIds;
      this.groupDatabase           = groupDatabase;
      this.groupsV2Api             = groupsV2Api;
      this.groupsV2Authorization   = groupsV2Authorization;
      this.masterKey               = groupMasterKey;
      this.groupSecretParams       = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
      this.groupId                 = GroupId.v2(groupSecretParams.getPublicParams().getGroupIdentifier());
      this.profileAndMessageHelper = profileAndMessageHelper;
    }

    @WorkerThread
    public GroupUpdateResult forceSanityUpdateFromServer(long timestamp)
        throws IOException, GroupNotAMemberException
    {
      Optional<GroupRecord> localRecord = groupDatabase.getGroup(groupId);
      DecryptedGroup        localState  = localRecord.map(g -> g.requireV2GroupProperties().getDecryptedGroup()).orElse(null);
      DecryptedGroup        serverState;

      if (localState == null) {
        info("No local state to force update");
        return new GroupUpdateResult(GroupState.GROUP_CONSISTENT_OR_AHEAD, null);
      }

      try {
        serverState = groupsV2Api.getGroup(groupSecretParams, groupsV2Authorization.getAuthorizationForToday(serviceIds, groupSecretParams));
      } catch (NotInGroupException | GroupNotFoundException e) {
        throw new GroupNotAMemberException(e);
      } catch (VerificationFailedException | InvalidGroupStateException e) {
        throw new IOException(e);
      }

      DecryptedGroupChange    decryptedGroupChange    = GroupChangeReconstruct.reconstructGroupChange(localState, serverState);
      GlobalGroupState        inputGroupState         = new GlobalGroupState(localState, Collections.singletonList(new ServerGroupLogEntry(serverState, decryptedGroupChange)));
      AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(inputGroupState, serverState.revision);
      DecryptedGroup          newLocalState           = advanceGroupStateResult.getNewGlobalGroupState().getLocalState();

      if (newLocalState == null || newLocalState == inputGroupState.getLocalState()) {
        info("Local state and server state are equal");
        return new GroupUpdateResult(GroupState.GROUP_CONSISTENT_OR_AHEAD, null);
      } else {
        info("Local state (revision: " + localState.revision + ") does not match server state (revision: " + serverState.revision + "), updating");
      }

      updateLocalDatabaseGroupState(inputGroupState, newLocalState);
      if (localState.revision == GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION) {
        info("Inserting single update message for restore placeholder");
        profileAndMessageHelper.insertUpdateMessages(timestamp, null, Collections.singleton(new LocalGroupLogEntry(newLocalState, null)), null);
      } else {
        info("Inserting force update messages");
        profileAndMessageHelper.insertUpdateMessages(timestamp, localState, advanceGroupStateResult.getProcessedLogEntries(), null);
      }
      profileAndMessageHelper.persistLearnedProfileKeys(inputGroupState);

      return new GroupUpdateResult(GroupState.GROUP_UPDATED, newLocalState);
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
      return updateLocalGroupToRevision(revision, timestamp, groupDatabase.getGroup(groupId), signedGroupChange, null);
    }

    /**
     * Using network where required, will attempt to bring the local copy of the group up to the revision specified.
     *
     * @param revision use {@link #LATEST} to get latest.
     */
    @WorkerThread
    public GroupUpdateResult updateLocalGroupToRevision(final int revision,
                                                        final long timestamp,
                                                        @NonNull Optional<GroupRecord> localRecord,
                                                        @Nullable DecryptedGroupChange signedGroupChange,
                                                        @Nullable String serverGuid)
        throws IOException, GroupNotAMemberException
    {
      if (localIsAtLeast(localRecord, revision)) {
        return new GroupUpdateResult(GroupState.GROUP_CONSISTENT_OR_AHEAD, null);
      }

      GlobalGroupState inputGroupState = null;

      DecryptedGroup        localState  = localRecord.map(g -> g.requireV2GroupProperties().getDecryptedGroup()).orElse(null);

      if (signedGroupChange != null &&
          localState != null &&
          localState.revision + 1 == signedGroupChange.revision &&
          revision == signedGroupChange.revision)
      {
        if (notInGroupAndNotBeingAdded(localRecord, signedGroupChange) && notHavingInviteRevoked(signedGroupChange)) {
          warn("Ignoring P2P group change because we're not currently in the group and this change doesn't add us in. Falling back to a server fetch.");
        } else if (SignalStore.internalValues().gv2IgnoreP2PChanges()) {
          warn( "Ignoring P2P group change by setting");
        } else {
          try {
            info("Applying P2P group change");
            DecryptedGroup newState = DecryptedGroupUtil.apply(localState, signedGroupChange);

            inputGroupState = new GlobalGroupState(localState, Collections.singletonList(new ServerGroupLogEntry(newState, signedGroupChange)));
          } catch (NotAbleToApplyGroupV2ChangeException e) {
            warn( "Unable to apply P2P group change", e);
          }
        }
      }

      if (inputGroupState == null) {
        try {
          return updateLocalGroupFromServerPaged(revision, localState, timestamp, false, serverGuid);
        } catch (GroupNotAMemberException e) {
          if (localState != null && signedGroupChange != null) {
            try {
              if (notInGroupAndNotBeingAdded(localRecord, signedGroupChange)) {
                warn( "Server says we're not a member. Ignoring P2P group change because we're not currently in the group and this change doesn't add us in.");
              } else {
                info("Server says we're not a member. Applying P2P group change.");
                DecryptedGroup newState = DecryptedGroupUtil.applyWithoutRevisionCheck(localState, signedGroupChange);

                inputGroupState = new GlobalGroupState(localState, Collections.singletonList(new ServerGroupLogEntry(newState, signedGroupChange)));
              }
            } catch (NotAbleToApplyGroupV2ChangeException failed) {
              warn( "Unable to apply P2P group change when not a member", failed);
            }
          }

          if (inputGroupState == null) {
            if (localState != null && DecryptedGroupUtil.isPendingOrRequesting(localState, serviceIds)) {
              warn( "Unable to query server for group " + groupId + " server says we're not in group, but we think we are a pending or requesting member");
              throw new GroupNotAMemberException(e, true);
            } else {
              warn( "Unable to query server for group " + groupId + " server says we're not in group, inserting leave message");
              insertGroupLeave();
            }
            throw e;
          }
        }
      }

      AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(inputGroupState, revision);
      DecryptedGroup          newLocalState           = advanceGroupStateResult.getNewGlobalGroupState().getLocalState();

      if (newLocalState == null || newLocalState == inputGroupState.getLocalState()) {
        return new GroupUpdateResult(GroupState.GROUP_CONSISTENT_OR_AHEAD, null);
      }

      updateLocalDatabaseGroupState(inputGroupState, newLocalState);
      if (localState != null && localState.revision == GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION) {
        info("Inserting single update message for restore placeholder");
        profileAndMessageHelper.insertUpdateMessages(timestamp, null, Collections.singleton(new LocalGroupLogEntry(newLocalState, null)), null);
      } else {
        profileAndMessageHelper.insertUpdateMessages(timestamp, localState, advanceGroupStateResult.getProcessedLogEntries(), serverGuid);
      }
      profileAndMessageHelper.persistLearnedProfileKeys(inputGroupState);

      if (!signedGroupChange.promotePendingPniAciMembers.isEmpty()) {
        ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(false));
      }

      GlobalGroupState remainingWork = advanceGroupStateResult.getNewGlobalGroupState();
      if (remainingWork.getServerHistory().size() > 0) {
        info(String.format(Locale.US, "There are more revisions on the server for this group, scheduling for later, V[%d..%d]", newLocalState.revision + 1, remainingWork.getLatestRevisionNumber()));
        ApplicationDependencies.getJobManager().add(new RequestGroupV2InfoJob(groupId, remainingWork.getLatestRevisionNumber()));
      }

      return new GroupUpdateResult(GroupState.GROUP_UPDATED, newLocalState);
    }

    private boolean notInGroupAndNotBeingAdded(@NonNull Optional<GroupRecord> localRecord, @NonNull DecryptedGroupChange signedGroupChange) {
      boolean currentlyInGroup = localRecord.isPresent() && localRecord.get().isActive();

      boolean addedAsMember = signedGroupChange.newMembers
                                               .stream()
                                               .map(m -> m.aciBytes)
                                               .map(ACI::parseOrNull)
                                               .filter(Objects::nonNull)
                                               .anyMatch(serviceIds::matches);

      boolean addedAsPendingMember = signedGroupChange.newPendingMembers
                                                      .stream()
                                                      .map(m -> m.serviceIdBytes)
                                                      .anyMatch(serviceIds::matches);

      boolean addedAsRequestingMember = signedGroupChange.newRequestingMembers
                                                         .stream()
                                                         .map(m -> m.aciBytes)
                                                         .map(ACI::parseOrNull)
                                                         .filter(Objects::nonNull)
                                                         .anyMatch(serviceIds::matches);

      return !currentlyInGroup && !addedAsMember && !addedAsPendingMember && !addedAsRequestingMember;
    }

    private boolean notHavingInviteRevoked(@NonNull DecryptedGroupChange signedGroupChange) {
      boolean havingInviteRevoked = signedGroupChange.deletePendingMembers
                                                     .stream()
                                                     .map(m -> m.serviceIdBytes)
                                                     .anyMatch(serviceIds::matches);

      return !havingInviteRevoked;
    }

    /**
     * Using network, attempt to bring the local copy of the group up to the revision specified via paging.
     */
    private GroupUpdateResult updateLocalGroupFromServerPaged(int revision, DecryptedGroup localState, long timestamp, boolean forceIncludeFirst, @Nullable String serverGuid) throws IOException, GroupNotAMemberException {
      boolean latestRevisionOnly = revision == LATEST && (localState == null || localState.revision == GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION);

      info("Paging from server revision: " + (revision == LATEST ? "latest" : revision) + ", latestOnly: " + latestRevisionOnly);

      PartialDecryptedGroup latestServerGroup;
      GlobalGroupState      inputGroupState;

      try {
        latestServerGroup = groupsV2Api.getPartialDecryptedGroup(groupSecretParams, groupsV2Authorization.getAuthorizationForToday(serviceIds, groupSecretParams));
      } catch (NotInGroupException | GroupNotFoundException e) {
        throw new GroupNotAMemberException(e);
      } catch (VerificationFailedException | InvalidGroupStateException e) {
        throw new IOException(e);
      }

      if (localState != null && localState.revision >= latestServerGroup.getRevision() && GroupProtoUtil.isMember(serviceIds.getAci(), localState.members)) {
        info("Local state is at or later than server");
        return new GroupUpdateResult(GroupState.GROUP_CONSISTENT_OR_AHEAD, null);
      }

      if (latestRevisionOnly || !GroupProtoUtil.isMember(serviceIds.getAci(), latestServerGroup.getMembersList())) {
        info("Latest revision or not a member, use latest only");
        inputGroupState = new GlobalGroupState(localState, Collections.singletonList(new ServerGroupLogEntry(latestServerGroup.getFullyDecryptedGroup(), null)));
      } else {
        int revisionWeWereAdded = GroupProtoUtil.findRevisionWeWereAdded(latestServerGroup, serviceIds.getAci());
        int logsNeededFrom      = localState != null ? Math.max(localState.revision, revisionWeWereAdded) : revisionWeWereAdded;

        boolean includeFirstState = forceIncludeFirst ||
                                    localState == null ||
                                    localState.revision < 0 ||
                                    localState.revision == revisionWeWereAdded ||
                                    !GroupProtoUtil.isMember(serviceIds.getAci(), localState.members) ||
                                    (revision == LATEST && localState.revision + 1 < latestServerGroup.getRevision());

        info("Requesting from server currentRevision: " + (localState != null ? localState.revision : "null") +
             " logsNeededFrom: " + logsNeededFrom +
             " includeFirstState: " + includeFirstState +
             " forceIncludeFirst: " + forceIncludeFirst);
        inputGroupState = getFullMemberHistoryPage(localState, logsNeededFrom, includeFirstState);
      }

      ProfileKeySet    profileKeys           = new ProfileKeySet();
      DecryptedGroup   finalState            = localState;
      GlobalGroupState finalGlobalGroupState = inputGroupState;
      boolean          performCdsLookup      = false;

      boolean hasMore = true;

      while (hasMore) {
        AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(inputGroupState, revision);
        DecryptedGroup          newLocalState           = advanceGroupStateResult.getNewGlobalGroupState().getLocalState();
        info("Advanced group to revision: " + (newLocalState != null ? newLocalState.revision : "null"));

        if (newLocalState != null && !inputGroupState.hasMore() && !forceIncludeFirst) {
          int newLocalRevision = newLocalState.revision;
          int requestRevision  = (revision == LATEST) ? latestServerGroup.getRevision() : revision;
          if (newLocalRevision < requestRevision) {
            warn( "Paging again with force first snapshot enabled due to error processing changes. New local revision [" + newLocalRevision + "] hasn't reached our desired level [" + requestRevision + "]");
            return updateLocalGroupFromServerPaged(revision, localState, timestamp, true, serverGuid);
          }
        }

        if (newLocalState == null || newLocalState == inputGroupState.getLocalState()) {
          return new GroupUpdateResult(GroupState.GROUP_CONSISTENT_OR_AHEAD, null);
        }

        updateLocalDatabaseGroupState(inputGroupState, newLocalState);

        if (localState == null || localState.revision != GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION) {
          timestamp = profileAndMessageHelper.insertUpdateMessages(timestamp, localState, advanceGroupStateResult.getProcessedLogEntries(), serverGuid);
        }

        for (ServerGroupLogEntry entry : inputGroupState.getServerHistory()) {
          if (entry.getGroup() != null) {
            profileKeys.addKeysFromGroupState(entry.getGroup());
          }

          if (entry.getChange() != null) {
            profileKeys.addKeysFromGroupChange(entry.getChange());

            if (!entry.getChange().promotePendingPniAciMembers.isEmpty()) {
              performCdsLookup = true;
            }
          }
        }

        finalState            = newLocalState;
        finalGlobalGroupState = advanceGroupStateResult.getNewGlobalGroupState();
        hasMore               = inputGroupState.hasMore();

        if (hasMore) {
          info("Request next page from server revision: " + finalState.revision + " nextPageRevision: " + inputGroupState.getNextPageRevision());
          inputGroupState = getFullMemberHistoryPage(finalState, inputGroupState.getNextPageRevision(), false);
        }
      }

      if (localState != null && localState.revision == GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION) {
        info("Inserting single update message for restore placeholder");
        profileAndMessageHelper.insertUpdateMessages(timestamp, null, Collections.singleton(new LocalGroupLogEntry(finalState, null)), serverGuid);
      }

      profileAndMessageHelper.persistLearnedProfileKeys(profileKeys);

      if (performCdsLookup) {
        ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(false));
      }

      if (finalGlobalGroupState.getServerHistory().size() > 0) {
        info(String.format(Locale.US, "There are more revisions on the server for this group, scheduling for later, V[%d..%d]", finalState.revision + 1, finalGlobalGroupState.getLatestRevisionNumber()));
        ApplicationDependencies.getJobManager().add(new RequestGroupV2InfoJob(groupId, finalGlobalGroupState.getLatestRevisionNumber()));
      }

      return new GroupUpdateResult(GroupState.GROUP_UPDATED, finalState);
    }

    @WorkerThread
    public @NonNull DecryptedGroup getCurrentGroupStateFromServer()
        throws IOException, GroupNotAMemberException, GroupDoesNotExistException
    {
      try {
        return groupsV2Api.getGroup(groupSecretParams, groupsV2Authorization.getAuthorizationForToday(serviceIds, groupSecretParams));
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
        return groupsV2Api.getGroupHistoryPage(groupSecretParams, revision, groupsV2Authorization.getAuthorizationForToday(serviceIds, groupSecretParams), true)
                          .getResults()
                          .get(0)
                          .getGroup()
                          .orElse(null);
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
        warn("Group has already been left.");
        return;
      }

      Recipient groupRecipient = Recipient.externalGroupExact(groupId);

      DecryptedGroup decryptedGroup = groupDatabase.requireGroup(groupId)
                                                   .requireV2GroupProperties()
                                                   .getDecryptedGroup();

      DecryptedGroup simulatedGroupState = DecryptedGroupUtil.removeMember(decryptedGroup, serviceIds.getAci(), decryptedGroup.revision + 1);

      DecryptedGroupChange simulatedGroupChange = new DecryptedGroupChange.Builder()
                                                                          .editorServiceIdBytes(ACI.UNKNOWN.toByteString())
                                                                          .revision(simulatedGroupState.revision)
                                                                          .deleteMembers(Collections.singletonList(serviceIds.getAci().toByteString()))
                                                                          .build();

      DecryptedGroupV2Context decryptedGroupV2Context = GroupProtoUtil.createDecryptedGroupV2Context(masterKey, new GroupMutation(decryptedGroup, simulatedGroupChange, simulatedGroupState), null);
      OutgoingMessage         leaveMessage            = OutgoingMessage.groupUpdateMessage(groupRecipient, decryptedGroupV2Context, System.currentTimeMillis());

      try {
        MessageTable mmsDatabase = SignalDatabase.messages();
        ThreadTable  threadTable = SignalDatabase.threads();
        long         threadId    = threadTable.getOrCreateThreadIdFor(groupRecipient);
        long         id          = mmsDatabase.insertMessageOutbox(leaveMessage, threadId, false, null);
        mmsDatabase.markAsSent(id, true);
        threadTable.update(threadId, false, false);
      } catch (MmsException e) {
        warn( "Failed to insert leave message.", e);
      }

      groupDatabase.setActive(groupId, false);
      groupDatabase.remove(groupId, Recipient.self().getId());
    }

    /**
     * @return true iff group exists locally and is at least the specified revision.
     */
    private boolean localIsAtLeast(Optional<GroupRecord> localRecord, int revision) {
      if (revision == LATEST || localRecord.isEmpty() || groupDatabase.isUnknownGroup(localRecord)) {
        return false;
      }
      int dbRevision = localRecord.get().requireV2GroupProperties().getGroupRevision();
      return revision <= dbRevision;
    }

    private void updateLocalDatabaseGroupState(@NonNull GlobalGroupState inputGroupState,
                                               @NonNull DecryptedGroup newLocalState)
    {
      boolean needsAvatarFetch;

      if (inputGroupState.getLocalState() == null) {
        GroupId.V2 groupId = groupDatabase.create(masterKey, newLocalState);
        if (groupId == null) {
          Log.w(TAG, "Group create failed, trying to update");
          groupDatabase.update(masterKey, newLocalState);
        }
        needsAvatarFetch = !TextUtils.isEmpty(newLocalState.avatar);
      } else {
        groupDatabase.update(masterKey, newLocalState);
        needsAvatarFetch = !newLocalState.avatar.equals(inputGroupState.getLocalState().avatar);
      }

      if (needsAvatarFetch) {
        ApplicationDependencies.getJobManager().add(new AvatarGroupsV2DownloadJob(groupId, newLocalState.avatar));
      }

      profileAndMessageHelper.determineProfileSharing(inputGroupState, newLocalState);
    }

    private GlobalGroupState getFullMemberHistoryPage(DecryptedGroup localState, int logsNeededFromRevision, boolean includeFirstState) throws IOException {
      try {
        GroupHistoryPage               groupHistoryPage    = groupsV2Api.getGroupHistoryPage(groupSecretParams, logsNeededFromRevision, groupsV2Authorization.getAuthorizationForToday(serviceIds, groupSecretParams), includeFirstState);
        ArrayList<ServerGroupLogEntry> history             = new ArrayList<>(groupHistoryPage.getResults().size());
        boolean                        ignoreServerChanges = SignalStore.internalValues().gv2IgnoreServerChanges();

        if (ignoreServerChanges) {
          warn( "Server change logs are ignored by setting");
        }

        for (DecryptedGroupHistoryEntry entry : groupHistoryPage.getResults()) {
          DecryptedGroup       group  = entry.getGroup().orElse(null);
          DecryptedGroupChange change = ignoreServerChanges ? null : entry.getChange().orElse(null);

          if (group != null || change != null) {
            history.add(new ServerGroupLogEntry(group, change));
          }
        }

        return new GlobalGroupState(localState, history, groupHistoryPage.getPagingData());
      } catch (InvalidGroupStateException | VerificationFailedException e) {
        throw new IOException(e);
      }
    }

    private void info(String message) {
      info(message, null);
    }

    private void info(String message, Throwable t) {
      Log.i(TAG, "[" + groupId.toString() + "] " + message, t);
    }

    private void warn(String message) {
      warn(message, null);
    }

    private void warn(String message, Throwable e) {
      Log.w(TAG, "[" + groupId.toString() + "] " + message, e);
    }
  }

  @VisibleForTesting
  static class ProfileAndMessageHelper {

    private final Context        context;
    private final ACI            aci;
    private final GroupId.V2     groupId;
    private final RecipientTable recipientTable;

    @VisibleForTesting
    GroupMasterKey masterKey;

    ProfileAndMessageHelper(@NonNull Context context, @NonNull ACI aci, @NonNull GroupMasterKey masterKey, @NonNull GroupId.V2 groupId, @NonNull RecipientTable recipientTable) {
      this.context        = context;
      this.aci            = aci;
      this.masterKey      = masterKey;
      this.groupId        = groupId;
      this.recipientTable = recipientTable;
    }

    void determineProfileSharing(@NonNull GlobalGroupState inputGroupState, @NonNull DecryptedGroup newLocalState) {
      if (inputGroupState.getLocalState() != null) {
        boolean wasAMemberAlready = DecryptedGroupUtil.findMemberByAci(inputGroupState.getLocalState().members, aci).isPresent();

        if (wasAMemberAlready) {
          return;
        }
      }

      Optional<DecryptedMember>        selfAsMemberOptional  = DecryptedGroupUtil.findMemberByAci(newLocalState.members, aci);
      Optional<DecryptedPendingMember> selfAsPendingOptional = DecryptedGroupUtil.findPendingByServiceId(newLocalState.pendingMembers, aci);

      if (selfAsMemberOptional.isPresent()) {
        DecryptedMember selfAsMember     = selfAsMemberOptional.get();
        int             revisionJoinedAt = selfAsMember.joinedAtRevision;

        Optional<Recipient> addedByOptional = Stream.of(inputGroupState.getServerHistory())
                                                    .map(ServerGroupLogEntry::getChange)
                                                    .filter(c -> c != null && c.revision == revisionJoinedAt)
                                                    .findFirst()
                                                    .map(c -> Optional.ofNullable(ServiceId.parseOrNull(c.editorServiceIdBytes))
                                                                      .map(Recipient::externalPush))
                                                    .orElse(Optional.empty());

        if (addedByOptional.isPresent()) {
          Recipient addedBy = addedByOptional.get();

          Log.i(TAG, String.format("Added as a full member of %s by %s", groupId, addedBy.getId()));

          if (addedBy.isBlocked() && (inputGroupState.getLocalState() == null || !DecryptedGroupUtil.isRequesting(inputGroupState.getLocalState(), aci))) {
            Log.i(TAG, "Added by a blocked user. Leaving group.");
            ApplicationDependencies.getJobManager().add(new LeaveGroupV2Job(groupId));
            //noinspection UnnecessaryReturnStatement
            return;
          } else if (addedBy.isSystemContact() || addedBy.isProfileSharing()) {
            Log.i(TAG, "Group 'adder' is trusted. contact: " + addedBy.isSystemContact() + ", profileSharing: " + addedBy.isProfileSharing());
            Log.i(TAG, "Added to a group and auto-enabling profile sharing");
            recipientTable.setProfileSharing(Recipient.externalGroupExact(groupId).getId(), true);
          } else {
            Log.i(TAG, "Added to a group, but not enabling profile sharing, as 'adder' is not trusted");
          }
        } else {
          Log.w(TAG, "Could not find founding member during gv2 create. Not enabling profile sharing.");
        }
      } else if (selfAsPendingOptional.isPresent()) {
        Optional<Recipient> addedBy = selfAsPendingOptional.flatMap(adder -> Optional.ofNullable(UuidUtil.fromByteStringOrNull(adder.addedByAci))
                                                                                     .map(uuid -> Recipient.externalPush(ACI.from(uuid))));

        if (addedBy.isPresent() && addedBy.get().isBlocked()) {
          Log.i(TAG, String.format("Added to group %s by a blocked user %s. Leaving group.", groupId, addedBy.get().getId()));
          ApplicationDependencies.getJobManager().add(new LeaveGroupV2Job(groupId));
          //noinspection UnnecessaryReturnStatement
          return;
        } else {
          Log.i(TAG, String.format("Added to %s, but not enabling profile sharing as we are a pending member.", groupId));
        }
      } else {
        Log.i(TAG, String.format("Added to %s, but not enabling profile sharing as not a fullMember.", groupId));
      }
    }

    long insertUpdateMessages(long timestamp,
                              @Nullable DecryptedGroup previousGroupState,
                              Collection<LocalGroupLogEntry> processedLogEntries,
                              @Nullable String serverGuid)
    {
      for (LocalGroupLogEntry entry : processedLogEntries) {
        if (entry.getChange() != null && DecryptedGroupUtil.changeIsEmptyExceptForProfileKeyChanges(entry.getChange()) && !DecryptedGroupUtil.changeIsEmpty(entry.getChange())) {
          Log.d(TAG, "Skipping profile key changes only update message");
        } else if (entry.getChange() != null && DecryptedGroupUtil.changeIsEmptyExceptForBanChangesAndOptionalProfileKeyChanges(entry.getChange())) {
          Log.d(TAG, "Skipping ban changes only update message");
        } else {
          if (entry.getChange() != null && DecryptedGroupUtil.changeIsEmpty(entry.getChange()) && previousGroupState != null) {
            Log.w(TAG, "Empty group update message seen. Not inserting.");
          } else {
            storeMessage(GroupProtoUtil.createDecryptedGroupV2Context(masterKey, new GroupMutation(previousGroupState, entry.getChange(), entry.getGroup()), null), timestamp, serverGuid);
            timestamp++;
          }
        }
        previousGroupState = entry.getGroup();
      }
      return timestamp;
    }

    void persistLearnedProfileKeys(@NonNull GlobalGroupState globalGroupState) {
      final ProfileKeySet profileKeys = new ProfileKeySet();

      for (ServerGroupLogEntry entry : globalGroupState.getServerHistory()) {
        if (entry.getGroup() != null) {
          profileKeys.addKeysFromGroupState(entry.getGroup());
        }
        if (entry.getChange() != null) {
          profileKeys.addKeysFromGroupChange(entry.getChange());
        }
      }

      persistLearnedProfileKeys(profileKeys);
    }

    void persistLearnedProfileKeys(@NonNull ProfileKeySet profileKeys) {
      Set<RecipientId> updated = recipientTable.persistProfileKeySet(profileKeys);

      if (!updated.isEmpty()) {
        Log.i(TAG, String.format(Locale.US, "Learned %d new profile keys, fetching profiles", updated.size()));

        for (Job job : RetrieveProfileJob.forRecipients(updated)) {
          ApplicationDependencies.getJobManager().runSynchronously(job, 5000);
        }
      }
    }

    void storeMessage(@NonNull DecryptedGroupV2Context decryptedGroupV2Context, long timestamp, @Nullable String serverGuid) {
      Optional<ServiceId> editor = getEditor(decryptedGroupV2Context);

      boolean outgoing = !editor.isPresent() || aci.equals(editor.get());

      if (outgoing) {
        try {
          MessageTable    mmsDatabase     = SignalDatabase.messages();
          ThreadTable     threadTable     = SignalDatabase.threads();
          RecipientId     recipientId     = recipientTable.getOrInsertFromGroupId(groupId);
          Recipient       recipient       = Recipient.resolved(recipientId);
          OutgoingMessage outgoingMessage = OutgoingMessage.groupUpdateMessage(recipient, decryptedGroupV2Context, timestamp);
          long            threadId        = threadTable.getOrCreateThreadIdFor(recipient);
          long            messageId       = mmsDatabase.insertMessageOutbox(outgoingMessage, threadId, false, null);

          mmsDatabase.markAsSent(messageId, true);
          threadTable.update(threadId, false, false);
        } catch (MmsException e) {
          Log.w(TAG, "Failed to insert outgoing update message!", e);
        }
      } else {
        try {
          MessageTable                        smsDatabase  = SignalDatabase.messages();
          RecipientId                         sender       = RecipientId.from(editor.get());
          IncomingMessage                     groupMessage = IncomingMessage.groupUpdate(sender, timestamp, groupId, decryptedGroupV2Context, serverGuid);
          Optional<MessageTable.InsertResult> insertResult = smsDatabase.insertMessageInbox(groupMessage);

          if (insertResult.isPresent()) {
            SignalDatabase.threads().update(insertResult.get().getThreadId(), false, false);
          } else {
            Log.w(TAG, "Could not insert update message");
          }
        } catch (MmsException e) {
          Log.w(TAG, "Failed to insert incoming update message!", e);
        }
      }
    }

    private Optional<ServiceId> getEditor(@NonNull DecryptedGroupV2Context decryptedGroupV2Context) {
      DecryptedGroupChange change       = decryptedGroupV2Context.change;
      Optional<ServiceId>  changeEditor = DecryptedGroupUtil.editorServiceId(change);
      if (changeEditor.isPresent()) {
        return changeEditor;
      } else {
        Optional<DecryptedPendingMember> pending = DecryptedGroupUtil.findPendingByServiceId(decryptedGroupV2Context.groupState.pendingMembers, aci);
        if (pending.isPresent()) {
          return Optional.ofNullable(ACI.parseOrNull(pending.get().addedByAci));
        }
      }
      return Optional.empty();
    }
  }
}
