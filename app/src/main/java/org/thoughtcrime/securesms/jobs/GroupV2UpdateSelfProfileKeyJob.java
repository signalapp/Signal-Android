package org.thoughtcrime.securesms.jobs;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupChangeFailedException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupInsufficientRightsException;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupNotAMemberException;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.DecryptionsDrainedConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.groupsv2.NoCredentialForRedemptionTimeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * When your profile key changes, this job can be used to update it on a single given group.
 * <p>
 * Your membership is confirmed first, so safe to run against any known {@link GroupId.V2}
 */
public final class GroupV2UpdateSelfProfileKeyJob extends BaseJob {

  public static final String KEY = "GroupV2UpdateSelfProfileKeyJob";

  private static final String QUEUE = "GroupV2UpdateSelfProfileKeyJob";

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(GroupV2UpdateSelfProfileKeyJob.class);

  private static final String KEY_GROUP_ID = "group_id";

  private final GroupId.V2 groupId;

  /**
   * Job will run regardless of how many times you enqueue it.
   */
  public static @NonNull GroupV2UpdateSelfProfileKeyJob withoutLimits(@NonNull GroupId.V2 groupId) {
    return new GroupV2UpdateSelfProfileKeyJob(new Parameters.Builder()
                                                            .addConstraint(NetworkConstraint.KEY)
                                                            .setLifespan(TimeUnit.DAYS.toMillis(1))
                                                            .setMaxAttempts(Parameters.UNLIMITED)
                                                            .setQueue(QUEUE)
                                                            .build(),
                                              groupId);
  }

  /**
   * Only one instance will be enqueued per group, and it won't run until after decryptions are
   * drained.
   */
  public static @NonNull GroupV2UpdateSelfProfileKeyJob withQueueLimits(@NonNull GroupId.V2 groupId) {
    return new GroupV2UpdateSelfProfileKeyJob(new Parameters.Builder()
                                                            .addConstraint(NetworkConstraint.KEY)
                                                            .addConstraint(DecryptionsDrainedConstraint.KEY)
                                                            .setLifespan(TimeUnit.DAYS.toMillis(1))
                                                            .setMaxAttempts(Parameters.UNLIMITED)
                                                            .setQueue(QUEUE + "_" + groupId.toString())
                                                            .setMaxInstancesForQueue(1)
                                                            .build(),
                                              groupId);
  }

  /**
   * Updates GV2 groups with the correct profile key if we find any that are out of date. Will run at most once per day.
   */
  @AnyThread
  public static void enqueueForGroupsIfNecessary() {
    if (!SignalStore.account().isRegistered() || SignalStore.account().getAci() == null || !Recipient.self().isRegistered()) {
      Log.w(TAG, "Not yet registered!");
      return;
    }

    byte[] rawProfileKey = Recipient.self().getProfileKey();

    if (rawProfileKey == null) {
      Log.w(TAG, "No profile key set!");
      return;
    }

    ByteString selfProfileKey = ByteString.copyFrom(rawProfileKey);

    long timeSinceLastCheck = System.currentTimeMillis() - SignalStore.misc().getLastGv2ProfileCheckTime();

    if (timeSinceLastCheck < TimeUnit.DAYS.toMillis(1)) {
      Log.d(TAG, "Too soon. Last check was " + timeSinceLastCheck + " ms ago.");
      return;
    }

    Log.i(TAG, "Running routine check.");

    SignalStore.misc().setLastGv2ProfileCheckTime(System.currentTimeMillis());

    SignalExecutors.BOUNDED.execute(() -> {
      boolean foundMismatch = false;

      for (GroupId.V2 id : SignalDatabase.groups().getAllGroupV2Ids()) {
        Optional<GroupRecord> group = SignalDatabase.groups().getGroup(id);
        if (!group.isPresent()) {
          Log.w(TAG, "Group " + group + " no longer exists?");
          continue;
        }

        ByteString      selfUuidBytes = UuidUtil.toByteString(Recipient.self().requireServiceId().uuid());
        DecryptedMember selfMember    = group.get().requireV2GroupProperties().getDecryptedGroup().getMembersList()
                                                                                                  .stream()
                                                                                                  .filter(m -> m.getUuid().equals(selfUuidBytes))
                                                                                                  .findFirst()
                                                                                                  .orElse(null);

        if (selfMember != null && !selfMember.getProfileKey().equals(selfProfileKey)) {
          Log.w(TAG, "Profile key mismatch for group " + id + " -- enqueueing job");
          foundMismatch = true;
          ApplicationDependencies.getJobManager().add(GroupV2UpdateSelfProfileKeyJob.withQueueLimits(id));
        }
      }

      if (!foundMismatch) {
        Log.i(TAG, "No mismatches found.");
      }
    });
  }


  private GroupV2UpdateSelfProfileKeyJob(@NonNull Parameters parameters, @NonNull GroupId.V2 groupId) {
    super(parameters);
    this.groupId = groupId;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_GROUP_ID, groupId.toString())
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun()
      throws IOException, GroupNotAMemberException, GroupChangeFailedException, GroupInsufficientRightsException, GroupChangeBusyException
  {
    Log.i(TAG, "Ensuring profile key up to date on group " + groupId);
    GroupManager.updateSelfProfileKeyInGroup(context, groupId);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException ||
           e instanceof NoCredentialForRedemptionTimeException||
           e instanceof GroupChangeBusyException;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<GroupV2UpdateSelfProfileKeyJob> {

    @Override
    public @NonNull GroupV2UpdateSelfProfileKeyJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new GroupV2UpdateSelfProfileKeyJob(parameters,
                                                GroupId.parseOrThrow(data.getString(KEY_GROUP_ID)).requireV2());
    }
  }
}
