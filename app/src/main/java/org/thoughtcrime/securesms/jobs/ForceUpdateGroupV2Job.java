package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.DecryptionsDrainedConstraint;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Schedules a {@link ForceUpdateGroupV2WorkerJob} to happen after message queues are drained.
 */
public final class ForceUpdateGroupV2Job extends BaseJob {

  public static final String KEY = "ForceUpdateGroupV2Job";

  private static final long   FORCE_UPDATE_INTERVAL = TimeUnit.DAYS.toMillis(7);
  private static final String KEY_GROUP_ID          = "group_id";

  private final GroupId.V2 groupId;

  public static void enqueueIfNecessary(@NonNull GroupId.V2 groupId) {
    SignalExecutors.BOUNDED.execute(() -> {
      Optional<GroupRecord> group = SignalDatabase.groups().getGroup(groupId);
      if (group.isPresent() &&
          group.get().isV2Group() &&
          group.get().getLastForceUpdateTimestamp() + FORCE_UPDATE_INTERVAL < System.currentTimeMillis()
      ) {
        AppDependencies.getJobManager().add(new ForceUpdateGroupV2Job(groupId));
      }
    });
  }

  private ForceUpdateGroupV2Job(@NonNull GroupId.V2 groupId) {
    this(new Parameters.Builder().setQueue("ForceUpdateGroupV2Job_" + groupId)
                                 .setMaxInstancesForQueue(1)
                                 .addConstraint(DecryptionsDrainedConstraint.KEY)
                                 .setMaxAttempts(Parameters.UNLIMITED)
                                 .build(),
         groupId);
  }

  private ForceUpdateGroupV2Job(@NonNull Parameters parameters, @NonNull GroupId.V2 groupId) {
    super(parameters);
    this.groupId = groupId;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_GROUP_ID, groupId.toString()).serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() {
    AppDependencies.getJobManager().add(new ForceUpdateGroupV2WorkerJob(groupId));
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<ForceUpdateGroupV2Job> {

    @Override
    public @NonNull ForceUpdateGroupV2Job create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);
      return new ForceUpdateGroupV2Job(parameters, GroupId.parseOrThrow(data.getString(KEY_GROUP_ID)).requireV2());
    }
  }
}
