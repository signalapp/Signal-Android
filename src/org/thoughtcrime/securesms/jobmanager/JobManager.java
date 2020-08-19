package org.thoughtcrime.securesms.jobmanager;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.impl.DefaultExecutorFactory;
import org.thoughtcrime.securesms.jobmanager.impl.JsonDataSerializer;
import org.thoughtcrime.securesms.jobmanager.migration.WorkManagerMigrator;
import org.thoughtcrime.securesms.jobmanager.persistence.JobStorage;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Debouncer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Allows the scheduling of durable jobs that will be run as early as possible.
 */
public class JobManager implements ConstraintObserver.Notifier {

  private static final String TAG = JobManager.class.getSimpleName();

  private final ExecutorService executor;
  private final JobController   jobController;
  private final JobRunner[]     jobRunners;

  private final Set<EmptyQueueListener> emptyQueueListeners = new CopyOnWriteArraySet<>();

  public JobManager(@NonNull Application application, @NonNull Configuration configuration) {
    this.executor      = configuration.getExecutorFactory().newSingleThreadExecutor("JobManager");
    this.jobRunners    = new JobRunner[configuration.getJobThreadCount()];
    this.jobController = new JobController(application,
                                           configuration.getJobStorage(),
                                           configuration.getJobInstantiator(),
                                           configuration.getConstraintFactories(),
                                           configuration.getDataSerializer(),
                                           configuration.getDependencyInjector(),
                                           Build.VERSION.SDK_INT < 26 ? new AlarmManagerScheduler(application)
                                                                      : new CompositeScheduler(new InAppScheduler(this), new JobSchedulerScheduler(application)),
                                           new Debouncer(500),
                                           this::onEmptyQueue);

    executor.execute(() -> {
      if (WorkManagerMigrator.needsMigration(application)) {
        Log.i(TAG, "Detected an old WorkManager database. Migrating.");
        WorkManagerMigrator.migrate(application, configuration.getJobStorage(), configuration.getDataSerializer());
      }

      jobController.init();

      for (int i = 0; i < jobRunners.length; i++) {
        jobRunners[i] = new JobRunner(application, i + 1, jobController);
        jobRunners[i].start();
      }

      for (ConstraintObserver constraintObserver : configuration.getConstraintObservers()) {
        constraintObserver.register(this);
      }

      if (Build.VERSION.SDK_INT < 26) {
        application.startService(new Intent(application, KeepAliveService.class));
      }

      wakeUp();
    });
  }

  /**
   * Enqueues a single job to be run.
   */
  public void add(@NonNull Job job) {
    new Chain(this, Collections.singletonList(job)).enqueue();
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
   * Retrieves a string representing the state of the job queue. Intended for debugging.
   */
  public @NonNull String getDebugInfo() {
    Future<String> result = executor.submit(jobController::getDebugInfo);
    try {
      return result.get();
    } catch (ExecutionException | InterruptedException e) {
      Log.w(TAG, "Failed to retrieve Job info.", e);
      return "Failed to retrieve Job info.";
    }
  }

  /**
   * Adds a listener to that will be notified when the job queue has been drained.
   */
  void addOnEmptyQueueListener(@NonNull EmptyQueueListener listener) {
    executor.execute(() -> {
      emptyQueueListeners.add(listener);
    });
  }

  /**
   * Removes a listener that was added via {@link #addOnEmptyQueueListener(EmptyQueueListener)}.
   */
  void removeOnEmptyQueueListener(@NonNull EmptyQueueListener listener) {
    executor.execute(() -> {
      emptyQueueListeners.remove(listener);
    });
  }

  @Override
  public void onConstraintMet(@NonNull String reason) {
    Log.i(TAG, "onConstraintMet(" + reason + ")");
    wakeUp();
  }

  /**
   * Pokes the system to take another pass at the job queue.
   */
  void wakeUp() {
    executor.execute(jobController::wakeUp);
  }

  private void enqueueChain(@NonNull Chain chain) {
    executor.execute(() -> {
      jobController.submitNewJobChain(chain.getJobListChain());
      wakeUp();
    });
  }

  private void onEmptyQueue() {
    executor.execute(() -> {
      for (EmptyQueueListener listener : emptyQueueListeners) {
        listener.onQueueEmpty();
      }
    });
  }

  public interface EmptyQueueListener {
    void onQueueEmpty();
  }

  /**
   * Allows enqueuing work that depends on each other. Jobs that appear later in the chain will
   * only run after all jobs earlier in the chain have been completed. If a job fails, all jobs
   * that occur later in the chain will also be failed.
   */
  public static class Chain {

    private final JobManager jobManager;
    private final List<List<Job>> jobs;

    private Chain(@NonNull JobManager jobManager, @NonNull List<? extends Job> jobs) {
      this.jobManager = jobManager;
      this.jobs       = new LinkedList<>();

      this.jobs.add(new ArrayList<>(jobs));
    }

    public Chain then(@NonNull Job job) {
      return then(Collections.singletonList(job));
    }

    public Chain then(@NonNull List<Job> jobs) {
      if (!jobs.isEmpty()) {
        this.jobs.add(new ArrayList<>(jobs));
      }
      return this;
    }

    public void enqueue() {
      jobManager.enqueueChain(this);
    }

    private List<List<Job>> getJobListChain() {
      return jobs;
    }
  }

  public static class Configuration {

    private final ExecutorFactory          executorFactory;
    private final int                      jobThreadCount;
    private final JobInstantiator          jobInstantiator;
    private final ConstraintInstantiator   constraintInstantiator;
    private final List<ConstraintObserver> constraintObservers;
    private final Data.Serializer          dataSerializer;
    private final JobStorage               jobStorage;
    private final DependencyInjector       dependencyInjector;

    private Configuration(int jobThreadCount,
                          @NonNull ExecutorFactory executorFactory,
                          @NonNull JobInstantiator jobInstantiator,
                          @NonNull ConstraintInstantiator constraintInstantiator,
                          @NonNull List<ConstraintObserver> constraintObservers,
                          @NonNull Data.Serializer dataSerializer,
                          @NonNull JobStorage jobStorage,
                          @NonNull DependencyInjector dependencyInjector)
    {
      this.executorFactory        = executorFactory;
      this.jobThreadCount         = jobThreadCount;
      this.jobInstantiator        = jobInstantiator;
      this.constraintInstantiator = constraintInstantiator;
      this.constraintObservers    = constraintObservers;
      this.dataSerializer         = dataSerializer;
      this.jobStorage             = jobStorage;
      this.dependencyInjector     = dependencyInjector;
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

    @NonNull Data.Serializer getDataSerializer() {
      return dataSerializer;
    }

    @NonNull JobStorage getJobStorage() {
      return jobStorage;
    }

    @NonNull DependencyInjector getDependencyInjector() {
      return dependencyInjector;
    }

    public static class Builder {

      private ExecutorFactory                 executorFactory     = new DefaultExecutorFactory();
      private int                             jobThreadCount      = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 1, 4));
      private Map<String, Job.Factory>        jobFactories        = new HashMap<>();
      private Map<String, Constraint.Factory> constraintFactories = new HashMap<>();
      private List<ConstraintObserver>        constraintObservers = new ArrayList<>();
      private Data.Serializer                 dataSerializer      = new JsonDataSerializer();
      private JobStorage                      jobStorage          = null;
      private DependencyInjector              dependencyInjector  = o -> { /*noop*/ };

      public @NonNull Builder setJobThreadCount(int jobThreadCount) {
        this.jobThreadCount = jobThreadCount;
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

      public @NonNull Builder setDataSerializer(@NonNull Data.Serializer dataSerializer) {
        this.dataSerializer = dataSerializer;
        return this;
      }

      public @NonNull Builder setJobStorage(@NonNull JobStorage jobStorage) {
        this.jobStorage = jobStorage;
        return this;
      }

      public @NonNull Builder setDependencyInjector(@NonNull DependencyInjector dependencyInjector) {
        this.dependencyInjector = dependencyInjector;
        return this;
      }

      public @NonNull Configuration build() {
        return new Configuration(jobThreadCount,
                                 executorFactory,
                                 new JobInstantiator(jobFactories),
                                 new ConstraintInstantiator(constraintFactories),
                                 new ArrayList<>(constraintObservers),
                                 dataSerializer,
                                 jobStorage,
                                 dependencyInjector);
      }
    }
  }
}
