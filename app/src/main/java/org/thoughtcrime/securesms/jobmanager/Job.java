package org.thoughtcrime.securesms.jobmanager;

import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil;
import org.thoughtcrime.securesms.util.RemoteConfig;

import java.lang.annotation.Retention;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * A durable unit of work.
 *
 * Jobs have {@link Parameters} that describe the conditions upon when you'd like them to run, how
 * often they should be retried, and how long they should be retried for.
 *
 * Never rely on a specific instance of this class being run. It can be created and destroyed as the
 * job is retried. State that you want to save is persisted to a {@link JsonJobData} object in
 * {@link #serialize()}. Your job is then recreated using a {@link Factory} that you register in
 * {@link JobManager.Configuration.Builder#setJobFactories(Map)}, which is given the saved
 * {@link JsonJobData} bundle.
 */
public abstract class Job {

  private static final String TAG = Log.tag(Job.class);

  private final Parameters parameters;

  private int  runAttempt;
  private long lastRunAttemptTime;
  private long nextBackoffInterval;

  private volatile boolean canceled;

  protected Context context;

  public Job(@NonNull Parameters parameters) {
    this.parameters = parameters;
  }

  public final @NonNull String getId() {
    return parameters.getId();
  }

  public final @NonNull Parameters getParameters() {
    return parameters;
  }

  public final int getRunAttempt() {
    return runAttempt;
  }

  public final long getLastRunAttemptTime() {
    return lastRunAttemptTime;
  }

  public final long getNextBackoffInterval() {
    return nextBackoffInterval;
  }

  public final @Nullable byte[] getInputData() {
    return parameters.getInputData();
  }

  public final @NonNull byte[] requireInputData() {
    return Objects.requireNonNull(parameters.getInputData());
  }

  /**
   * This is already called by {@link JobController} during job submission, but if you ever run a
   * job without submitting it to the {@link JobManager}, then you'll need to invoke this yourself.
   */
  public final void setContext(@NonNull Context context) {
    this.context = context;
  }

  /** Should only be invoked by {@link JobController} */
  final void setRunAttempt(int runAttempt) {
    this.runAttempt = runAttempt;
  }

  /** Should only be invoked by {@link JobController} */
  final void setLastRunAttemptTime(long lastRunAttemptTime) {
    this.lastRunAttemptTime = lastRunAttemptTime;
  }

  /** Should only be invoked by {@link JobController} */
  final void setNextBackoffInterval(long nextBackoffInterval) {
    this.nextBackoffInterval = nextBackoffInterval;
  }

  /** Should only be invoked by {@link JobController} */
  final void cancel() {
    this.canceled = true;
  }

  /** Provides a default exponential backoff given the current run attempt. */
  protected final long defaultBackoff() {
    return BackoffUtil.exponentialBackoff(runAttempt + 1, RemoteConfig.getDefaultMaxBackoff());
  }


  @WorkerThread
  final void onSubmit() {
    Log.i(TAG, JobLogger.format(this, "onSubmit()"));
    onAdded();
  }

  /**
   * @return True if your job has been marked as canceled while it was running, otherwise false.
   *         If a job sees that it has been canceled, it should make a best-effort attempt at
   *         stopping it's work. This job will have {@link #onFailure()} called after {@link #run()}
   *         has finished.
   */
  public final boolean isCanceled() {
    return canceled;
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
  public abstract @Nullable byte[] serialize();

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
   * Called when your job has completely failed and will not be run again.
   */
  @WorkerThread
  public abstract void onFailure();

  public interface Factory<T extends Job> {
    @NonNull T create(@NonNull Parameters parameters, @Nullable byte[] serializedData);
  }

  public static final class Result {

    private static final int INVALID_BACKOFF = -1;

    private static final Result SUCCESS_NO_DATA = new Result(ResultType.SUCCESS, null, null, INVALID_BACKOFF);
    private static final Result FAILURE         = new Result(ResultType.FAILURE, null, null, INVALID_BACKOFF);

    private final ResultType       resultType;
    private final RuntimeException runtimeException;
    private final byte[]           outputData;
    private final long             backoffInterval;

    private Result(@NonNull ResultType resultType, @Nullable RuntimeException runtimeException, @Nullable byte[] outputData, long backoffInterval) {
      this.resultType       = resultType;
      this.runtimeException = runtimeException;
      this.outputData       = outputData;
      this.backoffInterval  = backoffInterval;
    }

    /** Job completed successfully. */
    public static Result success() {
      return SUCCESS_NO_DATA;
    }

    /** Job completed successfully and wants to provide some output data. */
    public static Result success(@Nullable byte[] outputData) {
      return new Result(ResultType.SUCCESS, null, outputData, INVALID_BACKOFF);
    }

    /**
     * Job did not complete successfully, but it can be retried later.
     * @param backoffInterval How long to wait before retrying
     */
    public static Result retry(long backoffInterval) {
      return new Result(ResultType.RETRY, null, null, backoffInterval);
    }

    /** Job did not complete successfully and should not be tried again. Dependent jobs will also be failed.*/
    public static Result failure() {
      return FAILURE;
    }

    /** Same as {@link #failure()}, except the app should also crash with the provided exception. */
    public static Result fatalFailure(@NonNull RuntimeException runtimeException) {
      return new Result(ResultType.FAILURE, runtimeException, null, INVALID_BACKOFF);
    }

    public boolean isSuccess() {
      return resultType == ResultType.SUCCESS;
    }

    public boolean isRetry() {
      return resultType == ResultType.RETRY;
    }

    public boolean isFailure() {
      return resultType == ResultType.FAILURE;
    }

    @Nullable RuntimeException getException() {
      return runtimeException;
    }

    @Nullable byte[] getOutputData() {
      return outputData;
    }

    long getBackoffInterval() {
      return backoffInterval;
    }

    @Override
    public @NonNull String toString() {
      switch (resultType) {
        case SUCCESS:
        case RETRY:
          return resultType.toString();
        case FAILURE:
          if (runtimeException == null) {
            return resultType.toString();
          } else {
            return "FATAL_FAILURE";
          }
      }

      return "UNKNOWN?";
    }

    private enum ResultType {
      SUCCESS, FAILURE, RETRY
    }
  }

  public static final class Parameters {

    public static final String MIGRATION_QUEUE_KEY = "MIGRATION";
    public static final long   IMMORTAL            = -1;
    public static final int    UNLIMITED           = -1;

    @Retention(SOURCE)
    @IntDef({ PRIORITY_DEFAULT, PRIORITY_LOW, PRIORITY_HIGH})
    public @interface Priority{}
    public static final int PRIORITY_DEFAULT = 0;
    public static final int PRIORITY_HIGH = 1;
    public static final int PRIORITY_LOW = -1;

    private final String       id;
    private final long         createTime;
    private final long         lifespan;
    private final int          maxAttempts;
    private final int          maxInstancesForFactory;
    private final int          maxInstancesForQueue;
    private final String       queue;
    private final List<String> constraintKeys;
    private final byte[]       inputData;
    private final boolean      memoryOnly;
    private final int          globalPriority;
    private final int          queuePriority;

    private Parameters(@NonNull String id,
                       long createTime,
                       long lifespan,
                       int maxAttempts,
                       int maxInstancesForFactory,
                       int maxInstancesForQueue,
                       @Nullable String queue,
                       @NonNull List<String> constraintKeys,
                       @Nullable byte[] inputData,
                       boolean memoryOnly,
                       int globalPriority,
                       int queuePriority)
    {
      this.id                     = id;
      this.createTime             = createTime;
      this.lifespan               = lifespan;
      this.maxAttempts            = maxAttempts;
      this.maxInstancesForFactory = maxInstancesForFactory;
      this.maxInstancesForQueue   = maxInstancesForQueue;
      this.queue                  = queue;
      this.constraintKeys         = constraintKeys;
      this.inputData              = inputData;
      this.memoryOnly             = memoryOnly;
      this.globalPriority         = globalPriority;
      this.queuePriority          = queuePriority;
    }

    @NonNull String getId() {
      return id;
    }

    long getCreateTime() {
      return createTime;
    }

    long getLifespan() {
      return lifespan;
    }

    int getMaxAttempts() {
      return maxAttempts;
    }

    int getMaxInstancesForFactory() {
      return maxInstancesForFactory;
    }

    int getMaxInstancesForQueue() {
      return maxInstancesForQueue;
    }

    public @Nullable String getQueue() {
      return queue;
    }

    @NonNull List<String> getConstraintKeys() {
      return constraintKeys;
    }

    @Nullable byte[] getInputData() {
      return inputData;
    }

    boolean isMemoryOnly() {
      return memoryOnly;
    }

    int getGlobalPriority() {
      return globalPriority;
    }

    int getQueuePriority() {
      return queuePriority;
    }

    public Builder toBuilder() {
      return new Builder(id, createTime, lifespan, maxAttempts, maxInstancesForFactory, maxInstancesForQueue, queue, constraintKeys, inputData, memoryOnly, globalPriority, queuePriority);
    }


    public static final class Builder {
      private String       id;
      private long         createTime;
      private long         lifespan;
      private int          maxAttempts;
      private int          maxInstancesForFactory;
      private int          maxInstancesForQueue;
      private String       queue;
      private List<String> constraintKeys;
      private byte[]       inputData;
      private boolean      memoryOnly;
      private int          globalPriority;
      private int          queuePriority;

      public Builder() {
        this(UUID.randomUUID().toString());
      }

      Builder(@NonNull String id) {
        this(id, System.currentTimeMillis(), IMMORTAL, 1, UNLIMITED, UNLIMITED, null, new LinkedList<>(), null, false, Parameters.PRIORITY_DEFAULT, Parameters.PRIORITY_DEFAULT);
      }

      private Builder(@NonNull String id,
                      long createTime,
                      long lifespan,
                      int maxAttempts,
                      int maxInstancesForFactory,
                      int maxInstancesForQueue,
                      @Nullable String queue,
                      @NonNull List<String> constraintKeys,
                      @Nullable byte[] inputData,
                      boolean memoryOnly,
                      int globalPriority,
                      int queuePriority)
      {
        this.id                     = id;
        this.createTime             = createTime;
        this.lifespan               = lifespan;
        this.maxAttempts            = maxAttempts;
        this.maxInstancesForFactory = maxInstancesForFactory;
        this.maxInstancesForQueue   = maxInstancesForQueue;
        this.queue                  = queue;
        this.constraintKeys         = constraintKeys;
        this.inputData              = inputData;
        this.memoryOnly             = memoryOnly;
        this.globalPriority         = globalPriority;
        this.queuePriority          = queuePriority;
      }

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
       * Specify the maximum number of instances you'd want of this job at any given time, as
       * determined by the job's factory key. If enqueueing this job would put it over that limit,
       * it will be ignored.
       *
       * This property is ignored if the job is submitted as part of a {@link JobManager.Chain}.
       *
       * Defaults to {@link #UNLIMITED}.
       */
      public @NonNull Builder setMaxInstancesForFactory(int maxInstancesForFactory) {
        this.maxInstancesForFactory = maxInstancesForFactory;
        return this;
      }

      /**
       * Specify the maximum number of instances you'd want of this job at any given time, as
       * determined by the job's factory key and queue key. If enqueueing this job would put it over
       * that limit, it will be ignored.
       *
       * This property is ignored if the job is submitted as part of a {@link JobManager.Chain}, or
       * if the job has no queue key.
       *
       * Defaults to {@link #UNLIMITED}.
       */
      public @NonNull Builder setMaxInstancesForQueue(int maxInstancesForQueue) {
        this.maxInstancesForQueue = maxInstancesForQueue;
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

      /**
       * Specify whether or not you want this job to only live in memory. If true, this job will
       * *not* survive application death. This defaults to false, and should be used with care.
       *
       * Defaults to false.
       */
      public @NonNull Builder setMemoryOnly(boolean memoryOnly) {
        this.memoryOnly = memoryOnly;
        return this;
      }

      /**
       * Sets the job's global priority. Higher numbers are higher priority. Use the constants {@link Parameters#PRIORITY_HIGH}, {@link Parameters#PRIORITY_LOW},
       * and {@link Parameters#PRIORITY_DEFAULT}. Defaults to {@link Parameters#PRIORITY_DEFAULT}.
       *
       * Priority determines the order jobs are run. In general, higher priority jobs run first. When deciding which job to run within a queue, we will always
       * run the oldest job that has the highest priority. For example, if the highest priority in the queue is {@link Parameters#PRIORITY_DEFAULT}, then we'll
       * run the oldest job with that priority, ignoring lower-priority jobs.
       *
       * Given all of the jobs that are eligible in each queue, we will do the same sort again to determine which job to run next. We will run the oldest job
       * that has the highest priority among those eligible to be run.
       *
       * This creates the property that the only time a low-priority job will be run is if all other higher-priority jobs have been run already. Be considerate
       * of this, as it provides the potential for lower-priority jobs to be extremely delayed if higher-priority jobs are being consistently enqueued at the
       * same time.
       */
      public @NonNull Builder setGlobalPriority(@Priority int priority) {
        this.globalPriority = priority;
        return this;
      }

      /**
       * Sets the job's queue priority. Higher numbers are higher priority. Use the constants {@link Parameters#PRIORITY_HIGH}, {@link Parameters#PRIORITY_LOW},
       * and {@link Parameters#PRIORITY_DEFAULT}. Defaults to {@link Parameters#PRIORITY_DEFAULT}.
       *
       * Queue priority determines the order jobs are run within a queue. It's a secondary attribute to {@link #setGlobalPriority(int)}. When deciding which job
       * to run within a queue, if two jobs have equal global priorities, we will always run the oldest job that has the highest queue priority. For example,
       * if the highest queue priority in the queue is {@link Parameters#PRIORITY_DEFAULT} (and all global priorities are equal), then we'll run the oldest job
       * with that queue priority, ignoring lower-priority jobs.
       *
       * Outside of picking the "most eligible job" within a queue, the queue priority is not used. It is ignored when choosing which job to run amongst
       * multiple queues. If you'd like to influence that, see {@link #setGlobalPriority(int)}.
       */
      public @NonNull Builder setQueuePriority(@Priority int priority) {
        this.queuePriority = priority;
        return this;
      }

      /**
       * Sets the input data that will be made available to the job when it is run.
       * Should only be set by {@link JobController}.
       */
      @NonNull Builder setInputData(@Nullable byte[] inputData) {
        this.inputData = inputData;
        return this;
      }

      public @NonNull Parameters build() {
        return new Parameters(id, createTime, lifespan, maxAttempts, maxInstancesForFactory, maxInstancesForQueue, queue, constraintKeys, inputData, memoryOnly, globalPriority, queuePriority);
      }
    }
  }
}
