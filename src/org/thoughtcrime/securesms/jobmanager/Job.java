package org.thoughtcrime.securesms.jobmanager;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.logging.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A durable unit of work.
 *
 * Jobs have {@link Parameters} that describe the conditions upon when you'd like them to run, how
 * often they should be retried, and how long they should be retried for.
 *
 * Never rely on a specific instance of this class being run. It can be created and destroyed as the
 * job is retried. State that you want to save is persisted to a {@link Data} object in
 * {@link #serialize()}. Your job is then recreated using a {@link Factory} that you register in
 * {@link JobManager.Configuration.Builder#setJobFactories(Map)}, which is given the saved
 * {@link Data} bundle.
 */
public abstract class Job {

  private static final String TAG = Log.tag(Job.class);

  private final Parameters parameters;

  private String id;
  private int    runAttempt;
  private long   nextRunAttemptTime;

  protected Context context;

  public Job(@NonNull Parameters parameters) {
    this.parameters = parameters;
  }

  public final String getId() {
    return id;
  }

  public final @NonNull Parameters getParameters() {
    return parameters;
  }

  public final int getRunAttempt() {
    return runAttempt;
  }

  public final long getNextRunAttemptTime() {
    return nextRunAttemptTime;
  }

  /**
   * This is already called by {@link JobController} during job submission, but if you ever run a
   * job without submitting it to the {@link JobManager}, then you'll need to invoke this yourself.
   */
  public final void setContext(@NonNull Context context) {
    this.context = context;
  }

  /** Should only be invoked by {@link JobController} */
  final void setId(@NonNull String id) {
    this.id = id;
  }

  /** Should only be invoked by {@link JobController} */
  final void setRunAttempt(int runAttempt) {
    this.runAttempt = runAttempt;
  }

  /** Should only be invoked by {@link JobController} */
  final void setNextRunAttemptTime(long nextRunAttemptTime) {
    this.nextRunAttemptTime = nextRunAttemptTime;
  }

  @WorkerThread
  final void onSubmit() {
    Log.i(TAG, JobLogger.format(this, "onSubmit()"));
    onAdded();
  }

  /**
   * Called when the job is first submitted to the {@link JobManager}.
   */
  @WorkerThread
  public void onAdded() {
  }

  /**
   * Called after a job has run and its determined that a retry is required.
   */
  @WorkerThread
  public void onRetry() {
  }

  /**
   * Serialize your job state so that it can be recreated in the future.
   */
  public abstract @NonNull Data serialize();

  /**
   * Returns the key that can be used to find the relevant factory needed to create your job.
   */
  public abstract @NonNull String getFactoryKey();

  /**
   * Called to do your actual work.
   */
  @WorkerThread
  public abstract @NonNull Result run();

  /**
   * Called when your job has completely failed.
   */
  @WorkerThread
  public abstract void onCanceled();

  public interface Factory<T extends Job> {
    @NonNull T create(@NonNull Parameters parameters, @NonNull Data data);
  }

  public enum Result {
    SUCCESS, FAILURE, RETRY
  }

  public static final class Parameters {

    public static final int IMMORTAL  = -1;
    public static final int UNLIMITED = -1;

    private final long         createTime;
    private final long         lifespan;
    private final int          maxAttempts;
    private final long         maxBackoff;
    private final int          maxInstances;
    private final String       queue;
    private final List<String> constraintKeys;

    private Parameters(long createTime,
                       long lifespan,
                       int maxAttempts,
                       long maxBackoff,
                       int maxInstances,
                       @Nullable String queue,
                       @NonNull List<String> constraintKeys)
    {
      this.createTime     = createTime;
      this.lifespan       = lifespan;
      this.maxAttempts    = maxAttempts;
      this.maxBackoff     = maxBackoff;
      this.maxInstances   = maxInstances;
      this.queue          = queue;
      this.constraintKeys = constraintKeys;
    }

    public long getCreateTime() {
      return createTime;
    }

    public long getLifespan() {
      return lifespan;
    }

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public long getMaxBackoff() {
      return maxBackoff;
    }

    public int getMaxInstances() {
      return maxInstances;
    }

    public @Nullable String getQueue() {
      return queue;
    }

    public List<String> getConstraintKeys() {
      return constraintKeys;
    }


    public static final class Builder {

      private long         createTime     = System.currentTimeMillis();
      private long         maxBackoff     = TimeUnit.SECONDS.toMillis(30);
      private long         lifespan       = IMMORTAL;
      private int          maxAttempts    = 1;
      private int          maxInstances   = UNLIMITED;
      private String       queue          = null;
      private List<String> constraintKeys = new LinkedList<>();

      /** Should only be invoked by {@link JobController} */
      Builder setCreateTime(long createTime) {
        this.createTime = createTime;
        return this;
      }

      /**
       * Specify the amount of time this job is allowed to be retried. Defaults to {@link #IMMORTAL}.
       */
      public @NonNull Builder setLifespan(long lifespan) {
        this.lifespan = lifespan;
        return this;
      }

      /**
       * Specify the maximum number of times you want to attempt this job. Defaults to 1.
       */
      public @NonNull Builder setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
        return this;
      }

      /**
       * Specify the longest amount of time to wait between retries. No guarantees that this will
       * be respected on API >= 26.
       */
      public @NonNull Builder setMaxBackoff(long maxBackoff) {
        this.maxBackoff = maxBackoff;
        return this;
      }

      /**
       * Specify the maximum number of instances you'd want of this job at any given time. If
       * enqueueing this job would put it over that limit, it will be ignored.
       *
       * Duplicates are determined by two jobs having the same {@link Job#getFactoryKey()}.
       *
       * This property is ignored if the job is submitted as part of a {@link JobManager.Chain}.
       *
       * Defaults to {@link #UNLIMITED}.
       */
      public @NonNull Builder setMaxInstances(int maxInstances) {
        this.maxInstances = maxInstances;
        return this;
      }

      /**
       * Specify a string representing a queue. All jobs within the same queue are run in a
       * serialized fashion -- one after the other, in order of insertion. Failure of a job earlier
       * in the queue has no impact on the execution of jobs later in the queue.
       */
      public @NonNull Builder setQueue(@Nullable String queue) {
        this.queue = queue;
        return this;
      }

      /**
       * Add a constraint via the key that was used to register its factory in
       * {@link JobManager.Configuration)};
       */
      public @NonNull Builder addConstraint(@NonNull String constraintKey) {
        constraintKeys.add(constraintKey);
        return this;
      }

      /**
       * Set constraints via the key that was used to register its factory in
       * {@link JobManager.Configuration)};
       */
      public @NonNull Builder setConstraints(@NonNull List<String> constraintKeys) {
        this.constraintKeys.clear();
        this.constraintKeys.addAll(constraintKeys);
        return this;
      }

      public @NonNull Parameters build() {
        return new Parameters(createTime, lifespan, maxAttempts, maxBackoff, maxInstances, queue, constraintKeys);
      }
    }
  }
}
