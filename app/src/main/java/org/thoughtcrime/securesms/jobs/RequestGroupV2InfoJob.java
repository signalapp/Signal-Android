package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.DecryptionsDrainedConstraint;

/**
 * Schedules a {@link RequestGroupV2InfoWorkerJob} to happen after message queues are drained.
 */
public final class RequestGroupV2InfoJob extends BaseJob {

  public static final String KEY = "RequestGroupV2InfoJob";

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(RequestGroupV2InfoJob.class);

  private static final String KEY_GROUP_ID    = "group_id";
  private static final String KEY_TO_REVISION = "to_revision";

  private final GroupId.V2 groupId;
  private final int        toRevision;

  /**
   * Get a particular group state revision for group after message queues are drained.
   */
  public RequestGroupV2InfoJob(@NonNull GroupId.V2 groupId, int toRevision) {
    this(new Parameters.Builder()
                       .setQueue("RequestGroupV2InfoSyncJob")
                       .addConstraint(DecryptionsDrainedConstraint.KEY)
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build(),
         groupId,
         toRevision);
  }

  /**
   * Get latest group state for group after message queues are drained.
   */
  public RequestGroupV2InfoJob(@NonNull GroupId.V2 groupId) {
    this(groupId, GroupsV2StateProcessor.LATEST);
  }

  private RequestGroupV2InfoJob(@NonNull Parameters parameters, @NonNull GroupId.V2 groupId, int toRevision) {
    super(parameters);

    this.groupId    = groupId;
    this.toRevision = toRevision;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_GROUP_ID, groupId.toString())
                                    .putInt(KEY_TO_REVISION, toRevision)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() {
    AppDependencies.getJobManager().add(new RequestGroupV2InfoWorkerJob(groupId, toRevision));
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<RequestGroupV2InfoJob> {

    @Override
    public @NonNull RequestGroupV2InfoJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);
      return new RequestGroupV2InfoJob(parameters,
                                       GroupId.parseOrThrow(data.getString(KEY_GROUP_ID)).requireV2(),
                                       data.getInt(KEY_TO_REVISION));
    }
  }
}
