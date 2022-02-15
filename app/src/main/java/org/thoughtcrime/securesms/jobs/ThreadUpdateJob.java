package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.ThreadUtil;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;

public final class ThreadUpdateJob extends BaseJob {

  public static final String KEY = "ThreadUpdateJob";

  private static final String KEY_THREAD_ID = "thread_id";

  private final long threadId;

  private ThreadUpdateJob(long threadId) {
    this(new Parameters.Builder()
                       .setQueue("ThreadUpdateJob_" + threadId)
                       .setMaxInstancesForQueue(2)
                       .build(),
         threadId);
  }

  private ThreadUpdateJob(@NonNull Parameters  parameters, long threadId) {
    super(parameters);
    this.threadId = threadId;
  }

  public static void enqueue(long threadId) {
    ApplicationDependencies.getJobManager().add(new ThreadUpdateJob(threadId));
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_THREAD_ID, threadId).build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    SignalDatabase.threads().update(threadId, true);
    ThreadUtil.sleep(1000);
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
    public @NonNull ThreadUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new ThreadUpdateJob(parameters, data.getLong(KEY_THREAD_ID));
    }
  }
}
