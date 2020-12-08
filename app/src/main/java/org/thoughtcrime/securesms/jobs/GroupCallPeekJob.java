package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.DecryptionsDrainedConstraint;
import org.thoughtcrime.securesms.recipients.RecipientId;

/**
 * Allows the enqueueing of one peek operation per group while the web socket is not drained.
 */
public final class GroupCallPeekJob extends BaseJob {

  public static final String KEY = "GroupCallPeekJob";

  private static final String QUEUE = "__GroupCallPeekJob__";

  private static final String KEY_GROUP_RECIPIENT_ID = "group_recipient_id";

  @NonNull private final RecipientId groupRecipientId;

  public static void enqueue(@NonNull RecipientId groupRecipientId) {
    JobManager         jobManager = ApplicationDependencies.getJobManager();
    String             queue      = QUEUE + groupRecipientId.serialize();
    Parameters.Builder parameters = new Parameters.Builder()
                                                  .setQueue(queue)
                                                  .addConstraint(DecryptionsDrainedConstraint.KEY);

    jobManager.cancelAllInQueue(queue);

    jobManager.add(new GroupCallPeekJob(parameters.build(), groupRecipientId));
  }

  private GroupCallPeekJob(@NonNull Parameters parameters,
                           @NonNull RecipientId groupRecipientId)
  {
    super(parameters);
    this.groupRecipientId = groupRecipientId;
  }

  @Override
  protected void onRun() {
    ApplicationDependencies.getJobManager().add(new GroupCallPeekWorkerJob(groupRecipientId));
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder()
                   .putString(KEY_GROUP_RECIPIENT_ID, groupRecipientId.serialize())
                   .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<GroupCallPeekJob> {

    @Override
    public @NonNull GroupCallPeekJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new GroupCallPeekJob(parameters, RecipientId.from(data.getString(KEY_GROUP_RECIPIENT_ID)));
    }
  }
}
