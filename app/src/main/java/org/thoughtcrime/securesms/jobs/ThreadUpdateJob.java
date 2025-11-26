package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ThreadUtil;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;

/**
 * A job that effectively debounces thread updates through a combination of having a max instance count
 * and sleeping at the end of the job to make sure it takes a minimum amount of time.
 */
public final class ThreadUpdateJob extends BaseJob {

  public static final String KEY = "ThreadUpdateJob";

  private static final String KEY_THREAD_ID = "thread_id";
  private static final String KEY_UNARCHIVE = "unarchive";

  private static final long DEBOUNCE_INTERVAL_WITH_BACKLOG = 3000;

  private final long    threadId;
  private final boolean unarchive;

  private ThreadUpdateJob(long threadId, boolean unarchive) {
    this(new Parameters.Builder()
                       .setQueue("ThreadUpdateJob_" + threadId)
                       .setMaxInstancesForQueue(2)
                       .build(),
         threadId,
         unarchive);
  }

  private ThreadUpdateJob(@NonNull Parameters  parameters, long threadId, boolean unarchive) {
    super(parameters);
    this.threadId  = threadId;
    this.unarchive = unarchive;
  }

  public static void enqueue(long threadId, boolean unarchive) {
    SignalDatabase.runPostSuccessfulTransaction(KEY + threadId, () -> {
      AppDependencies.getJobManager().add(new ThreadUpdateJob(threadId, unarchive));
    });
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putLong(KEY_THREAD_ID, threadId)
                                    .putBoolean(KEY_UNARCHIVE, unarchive)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    SignalDatabase.threads().update(threadId, unarchive, true);
    if (!AppDependencies.getIncomingMessageObserver().getDecryptionDrained()) {
      ThreadUtil.sleep(DEBOUNCE_INTERVAL_WITH_BACKLOG);
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<ThreadUpdateJob> {
    @Override
    public @NonNull ThreadUpdateJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);
      return new ThreadUpdateJob(parameters, data.getLong(KEY_THREAD_ID), data.getBooleanOrDefault(KEY_UNARCHIVE, true));
    }
  }
}
