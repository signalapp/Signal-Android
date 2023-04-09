package org.thoughtcrime.securesms.jobmanager;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.impl.DefaultExecutorFactory;
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.JobStorage;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.FilteredExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Allows the scheduling of durable jobs that will be run as early as possible.
 */
public class JobManager implements ConstraintObserver.Notifier {

  private static final String TAG = Log.tag(JobManager.class);

  public static final int CURRENT_VERSION = 9;

  private final Application   application;
  private final Configuration configuration;
  private final Executor      executor;
  private final JobController jobController;
  private final JobTracker    jobTracker;

  @GuardedBy("emptyQueueListeners")
  private final Set<EmptyQueueListener> emptyQueueListeners = new CopyOnWriteArraySet<>();

  private volatile boolean initialized;

  public JobManager(@NonNull Application application, @NonNull Configuration configuration) {
    this.application   = application;
    this.configuration = configuration;
    this.executor      = new FilteredExecutor(configuration.getExecutorFactory().newSingleThreadExecutor("signal-JobManager"), ThreadUtil::isMainThread);
    this.jobTracker    = configuration.getJobTracker();
    this.jobController = new JobController(application,
                                           configuration.getJobStorage(),
                                           configuration.getJobInstantiator(),
                                           configuration.getConstraintFactories(),
                                           configuration.getJobTracker(),
                                           Build.VERSION.SDK_INT < 26 ? new AlarmManagerScheduler(application)
                                                                      : new CompositeScheduler(new InAppScheduler(this), new JobSchedulerScheduler(application)),
                                           new Debouncer(500),
                                           this::onEmptyQueue);

    executor.execute(() -> {
      synchronized (this) {
        JobStorage jobStorage = configuration.getJobStorage();
        jobStorage.init();

        int latestVersion = configuration.getJobMigrator().migrate(jobStorage);
        TextSecurePreferences.setJobManagerVersion(application, latestVersion);

        jobController.init();

        for (ConstraintObserver constraintObserver : configuration.getConstraintObservers()) {
          constraintObserver.register(this);
        }

        if (Build.VERSION.SDK_INT < 26) {
          application.startService(new Intent(application, KeepAliveService.class));
        }

        initialized = true;
        notifyAll();
      }
    });
  }

  /**
   * Begins the execution of jobs.
   */
  public void beginJobLoop() {
    runOnExecutor(()-> {
      int id = 0;

      for (int i = 0; i < configuration.getJobThreadCount(); i++) {
        new JobRunner(application, ++id, jobController, JobPredicate.NONE).start();
      }

      for (JobPredicate predicate : configuration.getReservedJobRunners()) {
        new JobRunner(application, ++id, jobController, predicate).start();
      }

      jobController.wakeUp();
    });
  }

  /**
   * Convenience method for {@link #addListener(JobTracker.JobFilter, JobTracker.JobListener)} that
   * takes in an ID to filter on.
   */
  public void addListener(@NonNull String id, @NonNull JobTracker.JobListener listener) {
    jobTracker.addListener(new JobIdFilter(id), listener);
  }

  /**
   * Add a listener to subscribe to job state updates. Listeners will be invoked on an arbitrary
   * background thread. You must eventually call {@link #removeListener(JobTracker.JobListener)} to avoid
   * memory leaks.
   */
  public void addListener(@NonNull JobTracker.JobFilter filter, @NonNull JobTracker.JobListener listener) {
    jobTracker.addListener(filter, listener);
  }

  /**
   * Unsubscribe the provided listener from all job updates.
   */
  public void removeListener(@NonNull JobTracker.JobListener listener) {
    jobTracker.removeListener(listener);
  }

  /**
   * Returns the state of the first Job that matches the provided filter. Note that there will always be races here, and the result you get back may not be
   * valid anymore by the time you get it. Use with caution.
   */
  public @Nullable JobTracker.JobState getFirstMatchingJobState(@NonNull JobTracker.JobFilter filter) {
    return jobTracker.getFirstMatchingJobState(filter);
  }

  /**
   * Enqueues a single job to be run.
   */
  public void add(@NonNull Job job) {
    new Chain(this, Collections.singletonList(job)).enqueue();
  }

  /**
   * Enqueues a single job that depends on a collection of job ID's.
   */
  public void add(@NonNull Job job, @NonNull Collection<String> dependsOn) {
    jobTracker.onStateChange(job, JobTracker.JobState.PENDING);

    runOnExecutor(() -> {
      jobController.submitJobWithExistingDependencies(job, dependsOn, null);
      jobController.wakeUp();
    });
  }

  /**
   * Enqueues a single job that depends on a collection of job ID's, as well as any unfinished
   * items in the specified queue.
   */
  public void add(@NonNull Job job, @Nullable String dependsOnQueue) {
    jobTracker.onStateChange(job, JobTracker.JobState.PENDING);

    runOnExecutor(() -> {
      jobController.submitJobWithExistingDependencies(job, Collections.emptyList(), dependsOnQueue);
    });
  }

  /**
   * Enqueues a single job that depends on a collection of job ID's, as well as any unfinished
   * items in the specified queue.
   */
  public void add(@NonNull Job job, @NonNull Collection<String> dependsOn, @Nullable String dependsOnQueue) {
    jobTracker.onStateChange(job, JobTracker.JobState.PENDING);

    runOnExecutor(() -> {
      jobController.submitJobWithExistingDependencies(job, dependsOn, dependsOnQueue);
    });
  }

  public void addAll(@NonNull List<Job> jobs) {
    if (jobs.isEmpty()) {
      return;
    }

    for (Job job : jobs) {
      jobTracker.onStateChange(job, JobTracker.JobState.PENDING);
    }

    runOnExecutor(() -> {
      jobController.submitJobs(jobs);
    });
  }

  /**
   * Begins the creation of a job chain with a single job.
   * @see Chain
   */
  public Chain startChain(@NonNull Job job) {
    return new Chain(this, Collections.singletonList(job));
  }

  /**
   * Begins the creation of a job chain with a set of jobs that can be run in parallel.
   * @see Chain
   */
  public Chain startChain(@NonNull List<? extends Job> jobs) {
    return new Chain(this, jobs);
  }

  /**
   * Attempts to cancel a job. This is best-effort and may not actually prevent a job from
   * completing if it was already running. If this job is running, this can only stop jobs that
   * bother to check {@link Job#isCanceled()}.
   *
   * When a job is canceled, {@link Job#onFailure()} will be triggered at the earliest possible
   * moment. Just like a normal failure, all later jobs in the same chain will also be failed.
   */
  public void cancel(@NonNull String id) {
    runOnExecutor(() -> jobController.cancelJob(id));
  }

  /**
   * Cancels all jobs in the specified queue. See {@link #cancel(String)} for details.
   */
  public void cancelAllInQueue(@NonNull String queue) {
    runOnExecutor(() -> jobController.cancelAllInQueue(queue));
  }

  /**
   * Perform an arbitrary update on enqueued jobs. Will not apply to jobs that are already running.
   * You shouldn't use this if you can help it. You give yourself an opportunity to really screw
   * things up.
   */
  public void update(@NonNull JobUpdater updater) {
    runOnExecutor(() -> jobController.update(updater));
  }

  /**
   * Search through the list of pending jobs and find all that match a given predicate. Note that there will always be races here, and the result you get back
   * may not be valid anymore by the time you get it. Use with caution.
   */
  public @NonNull List<JobSpec> find(@NonNull Predicate<JobSpec> predicate) {
    waitUntilInitialized();
    return jobController.findJobs(predicate);
  }

  /**
   * Runs the specified job synchronously. Beware: All normal dependencies are respected, meaning
   * you must take great care where you call this. It could take a very long time to complete!
   *
   * @return If the job completed, this will contain its completion state. If it timed out or
   *         otherwise didn't complete, this will be absent.
   */
  @WorkerThread
  public Optional<JobTracker.JobState> runSynchronously(@NonNull Job job, long timeout) {
    CountDownLatch                       latch       = new CountDownLatch(1);
    AtomicReference<JobTracker.JobState> resultState = new AtomicReference<>();

    addListener(job.getId(), new JobTracker.JobListener() {
      @Override
      public void onStateChanged(@NonNull Job job, @NonNull JobTracker.JobState jobState) {
        if (jobState.isComplete()) {
          removeListener(this);
          resultState.set(jobState);
          latch.countDown();
        }
      }
    });

    add(job);

    try {
      if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
        return Optional.empty();
      }
    } catch (InterruptedException e) {
      Log.w(TAG, "Interrupted during runSynchronously()", e);
      return Optional.empty();
    }

    return Optional.ofNullable(resultState.get());
  }

  /**
   * Retrieves a string representing the state of the job queue. Intended for debugging.
   */
  @WorkerThread
  public @NonNull String getDebugInfo() {
    AtomicReference<String> result = new AtomicReference<>();
    CountDownLatch          latch  = new CountDownLatch(1);

    runOnExecutor(() -> {
      result.set(jobController.getDebugInfo());
      latch.countDown();
    });

    try {
      boolean finished = latch.await(10, TimeUnit.SECONDS);
      if (finished) {
        return result.get();
      } else {
        return "Timed out waiting for Job info.";
      }
    } catch (InterruptedException e) {
      Log.w(TAG, "Failed to retrieve Job info.", e);
      return "Failed to retrieve Job info.";
    }
  }

  /**
   * Adds a listener that will be notified when the job queue has been drained.
   */
  void addOnEmptyQueueListener(@NonNull EmptyQueueListener listener) {
    runOnExecutor(() -> {
      synchronized (emptyQueueListeners) {
        emptyQueueListeners.add(listener);
      }
    });
  }

  /**
   * Removes a listener that was added via {@link #addOnEmptyQueueListener(EmptyQueueListener)}.
   */
  void removeOnEmptyQueueListener(@NonNull EmptyQueueListener listener) {
    runOnExecutor(() -> {
      synchronized (emptyQueueListeners) {
        emptyQueueListeners.remove(listener);
      }
    });
  }

  @Override
  public void onConstraintMet(@NonNull String reason) {
    Log.i(TAG, "onConstraintMet(" + reason + ")");
    wakeUp();
  }

  /**
   * Blocks until all pending operations are finished.
   */
  @WorkerThread
  public void flush() {
    CountDownLatch latch = new CountDownLatch(1);

    runOnExecutor(latch::countDown);

    try {
      latch.await();
      Log.i(TAG, "Successfully flushed.");
    } catch (InterruptedException e) {
      Log.w(TAG, "Failed to finish flushing.", e);
    }
  }

  /**
   * Can tell you if a queue is empty at the time of invocation. It is worth noting that the state
   * of the queue could change immediately after this method returns due to a call on some other
   * thread, and you should take that into consideration when using the result. If you want
   * something to happen within a queue, the safest course of action will always be to create a
   * job and place it in that queue.
   *
   * @return True if requested queue is empty at the time of invocation, otherwise false.
   */
  @WorkerThread
  public boolean isQueueEmpty(@NonNull String queueKey) {
    return areQueuesEmpty(Collections.singleton(queueKey));
  }

  /**
   * See {@link #isQueueEmpty(String)}
   *
   * @return True if *all* requested queues are empty at the time of invocation, otherwise false.
   */
  @WorkerThread
  public boolean areQueuesEmpty(@NonNull Set<String> queueKeys) {
    waitUntilInitialized();
    return jobController.areQueuesEmpty(queueKeys);
  }

  /**
   * Pokes the system to take another pass at the job queue.
   */
  void wakeUp() {
    runOnExecutor(jobController::wakeUp);
  }

  private void enqueueChain(@NonNull Chain chain) {
    for (List<Job> jobList : chain.getJobListChain()) {
      for (Job job : jobList) {
        jobTracker.onStateChange(job, JobTracker.JobState.PENDING);
      }
    }

    runOnExecutor(() -> {
      jobController.submitNewJobChain(chain.getJobListChain());
      jobController.wakeUp();
    });
  }

  private void onEmptyQueue() {
    runOnExecutor(() -> {
      synchronized (emptyQueueListeners) {
        for (EmptyQueueListener listener : emptyQueueListeners) {
          listener.onQueueEmpty();
        }
      }
    });
  }

  /**
   * Anything that you want to ensure happens off of the main thread and after initialization, run
   * it through here.
   */
  private void runOnExecutor(@NonNull Runnable runnable) {
    executor.execute(() -> {
      waitUntilInitialized();
      runnable.run();
    });
  }

  private void waitUntilInitialized() {
    if (!initialized) {
      Log.i(TAG, "Waiting for initialization...");
      synchronized (this) {
        while (!initialized) {
          Util.wait(this, 0);
        }
      }
      Log.i(TAG, "Initialization complete.");
    }
  }


  public interface EmptyQueueListener {
    void onQueueEmpty();
  }

  public static class JobIdFilter implements JobTracker.JobFilter {
    private final String id;

    public JobIdFilter(@NonNull String id) {
      this.id = id;
    }

    @Override
    public boolean matches(@NonNull Job job) {
      return id.equals(job.getId());
    }
  }

  /**
   * Allows enqueuing work that depends on each other. Jobs that appear later in the chain will
   * only run after all jobs earlier in the chain have been completed. If a job fails, all jobs
   * that occur later in the chain will also be failed.
   */
  public static class Chain {

    private final JobManager jobManager;
    private final List<List<Job>> jobs;

    @VisibleForTesting
    public Chain(@NonNull JobManager jobManager, @NonNull List<? extends Job> jobs) {
      this.jobManager = jobManager;
      this.jobs       = new LinkedList<>();

      this.jobs.add(new ArrayList<>(jobs));
    }

    public Chain then(@NonNull Job job) {
      return then(Collections.singletonList(job));
    }

    public Chain then(@NonNull List<? extends Job> jobs) {
      if (!jobs.isEmpty()) {
        this.jobs.add(new ArrayList<>(jobs));
      }
      return this;
    }

    public void enqueue() {
      jobManager.enqueueChain(this);
    }

    public void enqueue(@NonNull JobTracker.JobListener listener) {
      List<Job> lastChain          = jobs.get(jobs.size() - 1);
      Job       lastJobInLastChain = lastChain.get(lastChain.size() - 1);

      jobManager.addListener(lastJobInLastChain.getId(), listener);
      enqueue();
    }

    public Optional<JobTracker.JobState> enqueueAndBlockUntilCompletion(long timeout) {
      CountDownLatch                       latch       = new CountDownLatch(1);
      AtomicReference<JobTracker.JobState> resultState = new AtomicReference<>();
      JobTracker.JobListener               listener    = new JobTracker.JobListener() {
        @Override
        public void onStateChanged(@NonNull Job job, @NonNull JobTracker.JobState jobState) {
          if (jobState.isComplete()) {
            jobManager.removeListener(this);
            resultState.set(jobState);
            latch.countDown();
          }
        }
      };

      enqueue(listener);

      try {
        if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
          return Optional.empty();
        }
      } catch (InterruptedException e) {
        Log.w(TAG, "Interrupted during enqueueSynchronously()", e);
        return Optional.empty();
      }

      return Optional.ofNullable(resultState.get());
    }

    @VisibleForTesting
    public List<List<Job>> getJobListChain() {
      return jobs;
    }
  }

  public static class Configuration {

    private final ExecutorFactory          executorFactory;
    private final int                      jobThreadCount;
    private final JobInstantiator          jobInstantiator;
    private final ConstraintInstantiator   constraintInstantiator;
    private final List<ConstraintObserver> constraintObservers;
    private final JobStorage               jobStorage;
    private final JobMigrator              jobMigrator;
    private final JobTracker               jobTracker;
    private final List<JobPredicate>       reservedJobRunners;

    private Configuration(int jobThreadCount,
                          @NonNull ExecutorFactory executorFactory,
                          @NonNull JobInstantiator jobInstantiator,
                          @NonNull ConstraintInstantiator constraintInstantiator,
                          @NonNull List<ConstraintObserver> constraintObservers,
                          @NonNull JobStorage jobStorage,
                          @NonNull JobMigrator jobMigrator,
                          @NonNull JobTracker jobTracker,
                          @NonNull List<JobPredicate> reservedJobRunners)
    {
      this.executorFactory        = executorFactory;
      this.jobThreadCount         = jobThreadCount;
      this.jobInstantiator        = jobInstantiator;
      this.constraintInstantiator = constraintInstantiator;
      this.constraintObservers    = new ArrayList<>(constraintObservers);
      this.jobStorage             = jobStorage;
      this.jobMigrator            = jobMigrator;
      this.jobTracker             = jobTracker;
      this.reservedJobRunners     = new ArrayList<>(reservedJobRunners);
    }

    int getJobThreadCount() {
      return jobThreadCount;
    }

    @NonNull ExecutorFactory getExecutorFactory() {
      return executorFactory;
    }

    @NonNull JobInstantiator getJobInstantiator() {
      return jobInstantiator;
    }

    @NonNull
    ConstraintInstantiator getConstraintFactories() {
      return constraintInstantiator;
    }

    @NonNull List<ConstraintObserver> getConstraintObservers() {
      return constraintObservers;
    }

    @NonNull JobStorage getJobStorage() {
      return jobStorage;
    }

    @NonNull JobMigrator getJobMigrator() {
      return jobMigrator;
    }

    @NonNull JobTracker getJobTracker() {
      return jobTracker;
    }

    @NonNull List<JobPredicate> getReservedJobRunners() {
      return reservedJobRunners;
    }

    public static class Builder {

      private ExecutorFactory                 executorFactory     = new DefaultExecutorFactory();
      private int                             jobThreadCount      = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 1, 4));
      private Map<String, Job.Factory>        jobFactories        = new HashMap<>();
      private Map<String, Constraint.Factory> constraintFactories = new HashMap<>();
      private List<ConstraintObserver>        constraintObservers = new ArrayList<>();
      private JobStorage                      jobStorage          = null;
      private JobMigrator                     jobMigrator         = null;
      private JobTracker                      jobTracker          = new JobTracker();
      private List<JobPredicate>              reservedJobRunners  = new ArrayList<>();

      public @NonNull Builder setJobThreadCount(int jobThreadCount) {
        this.jobThreadCount = jobThreadCount;
        return this;
      }

      public @NonNull Builder addReservedJobRunner(@NonNull JobPredicate predicate) {
        this.reservedJobRunners.add(predicate);
        return this;
      }

      public @NonNull Builder setExecutorFactory(@NonNull ExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
        return this;
      }

      public @NonNull Builder setJobFactories(@NonNull Map<String, Job.Factory> jobFactories) {
        this.jobFactories = jobFactories;
        return this;
      }

      public @NonNull Builder setConstraintFactories(@NonNull Map<String, Constraint.Factory> constraintFactories) {
        this.constraintFactories = constraintFactories;
        return this;
      }

      public @NonNull Builder setConstraintObservers(@NonNull List<ConstraintObserver> constraintObservers) {
        this.constraintObservers = constraintObservers;
        return this;
      }

      public @NonNull Builder setJobStorage(@NonNull JobStorage jobStorage) {
        this.jobStorage = jobStorage;
        return this;
      }

      public @NonNull Builder setJobMigrator(@NonNull JobMigrator jobMigrator) {
        this.jobMigrator = jobMigrator;
        return this;
      }

      public @NonNull Configuration build() {
        return new Configuration(jobThreadCount,
                                 executorFactory,
                                 new JobInstantiator(jobFactories),
                                 new ConstraintInstantiator(constraintFactories),
                                 new ArrayList<>(constraintObservers),
                                 jobStorage,
                                 jobMigrator,
                                 jobTracker,
                                 reservedJobRunners);
      }
    }
  }
}
