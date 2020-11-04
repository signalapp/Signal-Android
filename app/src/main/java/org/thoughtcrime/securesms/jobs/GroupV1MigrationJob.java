package org.thoughtcrime.securesms.jobs;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupAlreadyExistsException;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupChangeFailedException;
import org.thoughtcrime.securesms.groups.GroupDoesNotExistException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupNotAMemberException;
import org.thoughtcrime.securesms.groups.MembershipNotSuitableForV2Exception;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.NoCredentialForRedemptionTimeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor.LATEST;

public class GroupV1MigrationJob extends BaseJob {

  private static final String TAG = Log.tag(GroupV1MigrationJob.class);

  public static final String KEY = "GroupV1MigrationJob";

  private static final String KEY_RECIPIENT_ID = "recipient_id";
  private static final String KEY_FORCED       = "forced";

  private static final int  ROUTINE_LIMIT     = 50;
  private static final long REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(3);

  private final RecipientId recipientId;
  private final boolean     forced;

  private GroupV1MigrationJob(@NonNull RecipientId recipientId, boolean forced) {
    this(updateParameters(new Parameters.Builder()
                                        .setQueue(recipientId.toQueueKey())
                                        .addConstraint(NetworkConstraint.KEY),
                          forced),
        recipientId,
        forced);
  }

  private static Parameters updateParameters(@NonNull Parameters.Builder builder, boolean forced) {
    if (forced) {
      return builder.setMaxAttempts(Parameters.UNLIMITED)
                    .setLifespan(TimeUnit.DAYS.toMillis(7))
                    .build();
    } else {
      return builder.setMaxAttempts(3)
                    .setLifespan(TimeUnit.MINUTES.toMillis(20))
                    .build();
    }
  }

  private GroupV1MigrationJob(@NonNull Parameters parameters, @NonNull RecipientId recipientId, boolean forced) {
    super(parameters);
    this.recipientId = recipientId;
    this.forced      = forced;
  }

  public static void enqueuePossibleAutoMigrate(@NonNull RecipientId recipientId) {
    SignalExecutors.BOUNDED.execute(() -> {
      if (Recipient.resolved(recipientId).isPushV1Group()) {
        ApplicationDependencies.getJobManager().add(new GroupV1MigrationJob(recipientId, false));
      }
    });
  }

  public static void enqueueRoutineMigrationsIfNecessary(@NonNull Application application) {
    if (!SignalStore.registrationValues().isRegistrationComplete() ||
        !TextSecurePreferences.isPushRegistered(application)       ||
        TextSecurePreferences.getLocalUuid(application) == null)
    {
      Log.i(TAG, "Registration not complete. Skipping.");
      return;
    }

    if (!FeatureFlags.groupsV1AutoMigration()) {
      Log.i(TAG, "Auto-migration disabled. Not proactively searching for groups.");
      return;
    }

    long timeSinceRefresh = System.currentTimeMillis() - SignalStore.misc().getLastProfileRefreshTime();
    if (timeSinceRefresh < REFRESH_INTERVAL) {
      Log.i(TAG, "Too soon to refresh. Did the last refresh " + timeSinceRefresh + " ms ago.");
      return;
    }

    SignalExecutors.BOUNDED.execute(() -> {
      JobManager         jobManager   = ApplicationDependencies.getJobManager();
      List<ThreadRecord> threads      = DatabaseFactory.getThreadDatabase(application).getRecentV1Groups(ROUTINE_LIMIT);
      Set<RecipientId>   needsRefresh = new HashSet<>();

      if (threads.size() > 0) {
        Log.d(TAG, "About to enqueue refreshes for " + threads.size() + " groups.");
      }

      for (ThreadRecord thread : threads) {
        jobManager.add(new GroupV1MigrationJob(thread.getRecipient().getId(), false));

        needsRefresh.addAll(Stream.of(thread.getRecipient().getParticipants())
                                  .filter(r -> r.getGroupsV2Capability() != Recipient.Capability.SUPPORTED ||
                                               r.getGroupsV1MigrationCapability() != Recipient.Capability.SUPPORTED)
                                  .map(Recipient::getId)
                                  .toList());
      }

      if (needsRefresh.size() > 0) {
        Log.w(TAG, "Enqueuing profile refreshes for " + needsRefresh.size() + " GV1 participants.");
        RetrieveProfileJob.enqueue(needsRefresh);
      }
    });
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_RECIPIENT_ID, recipientId.serialize())
                             .putBoolean(KEY_FORCED, forced)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws IOException, RetryLaterException {
    Recipient groupRecipient = Recipient.resolved(recipientId);
    Long      threadId       = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipientId);

    if (threadId == null) {
      warn(TAG, "No thread found!");
      return;
    }

    if (!groupRecipient.isPushV1Group()) {
      warn(TAG, "Not a V1 group!");
      return;
    }

    if (groupRecipient.getParticipants().size() > FeatureFlags.groupLimits().getHardLimit()) {
      warn(TAG, "Too many members! Size: " + groupRecipient.getParticipants().size());
      return;
    }

    GroupId.V1     gv1Id        = groupRecipient.requireGroupId().requireV1();
    GroupId.V2     gv2Id        = gv1Id.deriveV2MigrationGroupId();
    GroupMasterKey gv2MasterKey = gv1Id.deriveV2MigrationMasterKey();
    boolean        newlyCreated = false;

    switch (GroupManager.v2GroupStatus(context, gv2MasterKey)) {
      case DOES_NOT_EXIST:
        log(TAG, "Group does not exist on the service.");

        if (!groupRecipient.isActiveGroup()) {
          warn(TAG, "Group is inactive! Can't migrate.");
          return;
        }

        if (!groupRecipient.isProfileSharing()) {
          warn(TAG, "Profile sharing is disabled! Can't migrate.");
          return;
        }

        if (!forced && SignalStore.internalValues().disableGv1AutoMigrateInitiation()) {
          warn(TAG, "Auto migration initiation has been disabled! Skipping.");
          return;
        }

        if (!forced && !FeatureFlags.groupsV1AutoMigration()) {
          warn(TAG, "Auto migration is not enabled! Skipping.");
          return;
        }

        if (forced && !FeatureFlags.groupsV1ManualMigration()) {
          warn(TAG, "Manual migration is not enabled! Skipping.");
          return;
        }

        RecipientUtil.ensureUuidsAreAvailable(context, groupRecipient.getParticipants());
        groupRecipient = groupRecipient.fresh();

        List<Recipient> registeredMembers = RecipientUtil.getEligibleForSending(groupRecipient.getParticipants());
        List<Recipient> possibleMembers   = forced ? getMigratableManualMigrationMembers(registeredMembers)
                                                   : getMigratableAutoMigrationMembers(registeredMembers);

        if (!forced && possibleMembers.size() != registeredMembers.size()) {
          warn(TAG, "Not allowed to invite or leave registered users behind in an auto-migration! Skipping.");
          return;
        }

        log(TAG, "Attempting to create group.");

        try {
          GroupManager.migrateGroupToServer(context, gv1Id, possibleMembers);
          newlyCreated = true;
          log(TAG, "Successfully created!");
        } catch (GroupChangeFailedException e) {
          warn(TAG, "Failed to migrate group. Retrying.", e);
          throw new RetryLaterException();
        } catch (MembershipNotSuitableForV2Exception e) {
          warn(TAG, "Failed to migrate job due to the membership not yet being suitable for GV2. Aborting.", e);
          return;
        } catch (GroupAlreadyExistsException e) {
          warn(TAG, "Someone else created the group while we were trying to do the same! It exists now. Continuing on.", e);
        }
        break;
      case NOT_A_MEMBER:
        warn(TAG, "The migrated group already exists, but we are not a member. Doing a local leave.");
        handleLeftBehind(context, gv1Id, groupRecipient, threadId);
        return;
      case FULL_OR_PENDING_MEMBER:
        warn(TAG, "The migrated group already exists, and we're in it. Continuing on.");
        break;
      default: throw new AssertionError();
    }

    log(TAG, "Migrating local group " + gv1Id + " to " + gv2Id);

    DecryptedGroup decryptedGroup = performLocalMigration(context, gv1Id, threadId, groupRecipient);

    if (newlyCreated && decryptedGroup != null && !SignalStore.internalValues().disableGv1AutoMigrateNotification()) {
      GroupManager.sendNoopUpdate(context, gv2MasterKey, decryptedGroup);
    }
  }

  public static void performLocalMigration(@NonNull Context context, @NonNull GroupId.V1 gv1Id) throws IOException {
    Recipient recipient = Recipient.externalGroupExact(context, gv1Id);
    long      threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);

    performLocalMigration(context, gv1Id, threadId, recipient);
  }

  private static @Nullable DecryptedGroup performLocalMigration(@NonNull Context context, @NonNull GroupId.V1 gv1Id, long threadId, @NonNull Recipient groupRecipient) throws IOException {
    DecryptedGroup decryptedGroup;
    try {
      decryptedGroup = GroupManager.addedGroupVersion(context, gv1Id.deriveV2MigrationMasterKey());
    } catch (GroupDoesNotExistException e) {
      throw new IOException("[Local] The group should exist already!");
    } catch (GroupNotAMemberException e) {
      Log.w(TAG, "[Local] We are not in the group. Doing a local leave.");
      handleLeftBehind(context, gv1Id, groupRecipient, threadId);
      return null;
    }

    List<RecipientId> pendingRecipients = Stream.of(DecryptedGroupUtil.pendingToUuidList(decryptedGroup.getPendingMembersList()))
                                                .map(uuid -> Recipient.externalPush(context, uuid, null, false))
                                                .filterNot(Recipient::isSelf)
                                                .map(Recipient::getId)
                                                .toList();

    Log.i(TAG, "[Local] Migrating group over to the version we were added to: V" + decryptedGroup.getRevision());
    DatabaseFactory.getGroupDatabase(context).migrateToV2(gv1Id, decryptedGroup);
    DatabaseFactory.getSmsDatabase(context).insertGroupV1MigrationEvents(groupRecipient.getId(), threadId, pendingRecipients);

    Log.i(TAG, "[Local] Applying all changes since V" + decryptedGroup.getRevision());
    try {
      GroupManager.updateGroupFromServer(context, gv1Id.deriveV2MigrationMasterKey(), LATEST, System.currentTimeMillis(), null);
    } catch (GroupChangeBusyException | GroupNotAMemberException e) {
      Log.w(TAG, e);
    }

    return decryptedGroup;
  }

  private static void handleLeftBehind(@NonNull Context context, @NonNull GroupId.V1 gv1Id, @NonNull Recipient groupRecipient, long threadId) {
    DatabaseFactory.getGroupDatabase(context).setActive(gv1Id, false);

    OutgoingMediaMessage leaveMessage = GroupUtil.createGroupV1LeaveMessage(gv1Id, groupRecipient);
    try {
      long id = DatabaseFactory.getMmsDatabase(context).insertMessageOutbox(leaveMessage, threadId, false, null);
      DatabaseFactory.getMmsDatabase(context).markAsSent(id, true);
    } catch (MmsException e) {
      Log.w(TAG, "Failed to insert group leave message!", e);
    }
  }

  /**
   * In addition to meeting traditional requirements, you must also have a profile key for a member
   * to consider them migratable in an auto-migration.
   */
  private static @NonNull List<Recipient> getMigratableAutoMigrationMembers(@NonNull List<Recipient> registeredMembers) {
    return Stream.of(getMigratableManualMigrationMembers(registeredMembers))
                 .filter(r -> r.getProfileKey() != null)
                 .toList();
  }

  /**
   * You can only migrate users that have the required capabilities.
   */
  private static @NonNull List<Recipient> getMigratableManualMigrationMembers(@NonNull List<Recipient> registeredMembers) {
    return Stream.of(registeredMembers)
                 .filter(r -> r.getGroupsV2Capability() == Recipient.Capability.SUPPORTED &&
                              r.getGroupsV1MigrationCapability() == Recipient.Capability.SUPPORTED)
                 .toList();
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException                   ||
           e instanceof NoCredentialForRedemptionTimeException ||
           e instanceof GroupChangeBusyException               ||
           e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<GroupV1MigrationJob> {
    @Override
    public @NonNull GroupV1MigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new GroupV1MigrationJob(parameters,
                                     RecipientId.from(data.getString(KEY_RECIPIENT_ID)),
                                     data.getBoolean(KEY_FORCED));
    }
  }
}
