package org.thoughtcrime.securesms.jobmanager;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.jobmanager.persistence.ConstraintSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.DependencySpec;
import org.thoughtcrime.securesms.jobmanager.persistence.FullSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.JobStorage;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Debouncer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the queue of jobs. This is the only class that should write to {@link JobStorage} to
 * ensure consistency.
 */
class JobController {

  private static final String TAG = JobController.class.getSimpleName();

  private final Application            application;
  private final JobStorage             jobStorage;
  private final JobInstantiator        jobInstantiator;
  private final ConstraintInstantiator constraintInstantiator;
  private final Data.Serializer        dataSerializer;
  private final DependencyInjector     dependencyInjector;
  private final Scheduler              scheduler;
  private final Debouncer              debouncer;
  private final Callback               callback;
  private final Set<String>            runningJobs;

  JobController(@NonNull Application application,
                @NonNull JobStorage jobStorage,
                @NonNull JobInstantiator jobInstantiator,
                @NonNull ConstraintInstantiator constraintInstantiator,
                @NonNull Data.Serializer dataSerializer,
                @NonNull DependencyInjector dependencyInjector,
                @NonNull Scheduler scheduler,
                @NonNull Debouncer debouncer,
                @NonNull Callback callback)
  {
    this.application            = application;
    this.jobStorage             = jobStorage;
    this.jobInstantiator        = jobInstantiator;
    this.constraintInstantiator = constraintInstantiator;
    this.dataSerializer         = dataSerializer;
    this.dependencyInjector     = dependencyInjector;
    this.scheduler              = scheduler;
    this.debouncer              = debouncer;
    this.callback               = callback;
    this.runningJobs            = new HashSet<>();
  }

  @WorkerThread
  synchronized void init() {
    jobStorage.init();
    jobStorage.updateAllJobsToBePending();
    notifyAll();
  }

  synchronized void wakeUp() {
    notifyAll();
  }

  @WorkerThread
  synchronized void submitNewJobChain(@NonNull List<List<Job>> chain) {
    chain = Stream.of(chain).filterNot(List::isEmpty).toList();

    if (chain.isEmpty()) {
      Log.w(TAG, "Tried to submit an empty job chain. Skipping.");
      return;
    }

    if (chainExceedsMaximumInstances(chain)) {
      Job solo = chain.get(0).get(0);
      Log.w(TAG, JobLogger.format(solo, "Already at the max instance count of " + solo.getParameters().getMaxInstances() + ". Skipping."));
      return;
    }

    insertJobChain(chain);
    scheduleJobs(chain.get(0));
    triggerOnSubmit(chain);
    notifyAll();
  }

  @WorkerThread
  synchronized void onRetry(@NonNull Job job) {
    int  nextRunAttempt     = job.getRunAttempt() + 1;
    long nextRunAttemptTime = calculateNextRunAttemptTime(System.currentTimeMillis(), nextRunAttempt, job.getParameters().getMaxBackoff());

    jobStorage.updateJobAfterRetry(job.getId(), false, nextRunAttempt, nextRunAttemptTime);

    List<Constraint> constraints = Stream.of(jobStorage.getConstraintSpecs(job.getId()))
                                         .map(ConstraintSpec::getFactoryKey)
                                         .map(constraintInstantiator::instantiate)
                                         .toList();


    long delay = Math.max(0, nextRunAttemptTime - System.currentTimeMillis());

    Log.i(TAG, JobLogger.format(job, "Scheduling a retry in " + delay + " ms."));
    scheduler.schedule(delay, constraints);

    notifyAll();
  }

  synchronized void onJobFinished(@NonNull Job job) {
    runningJobs.remove(job.getId());
  }

  @WorkerThread
  synchronized void onSuccess(@NonNull Job job) {
    jobStorage.deleteJob(job.getId());
    notifyAll();
  }

  /**
   * @return The list of all dependent jobs that should also be failed.
   */
  @WorkerThread
  synchronized @NonNull List<Job> onFailure(@NonNull Job job) {
    List<Job> dependents = Stream.of(jobStorage.getDependencySpecsThatDependOnJob(job.getId()))
                                 .map(DependencySpec::getJobId)
                                 .map(jobStorage::getJobSpec)
                                 .withoutNulls()
                                 .map(jobSpec -> {
                                   List<ConstraintSpec> constraintSpecs = jobStorage.getConstraintSpecs(jobSpec.getId());
                                   return createJob(jobSpec, constraintSpecs);
                                 })
                                 .toList();

    List<Job> all = new ArrayList<>(dependents.size() + 1);
    all.add(job);
    all.addAll(dependents);

    jobStorage.deleteJobs(Stream.of(all).map(Job::getId).toList());

    return dependents;
  }

  /**
   * Retrieves the next job that is eligible for execution. To be 'eligible' means that the job:
   *  - Has no dependencies
   *  - Has no unmet constraints
   *
   * This method will block until a job is available.
   * When the job returned from this method has been run, you must call {@link #onJobFinished(Job)}.
   */
  @WorkerThread
  synchronized @NonNull Job pullNextEligibleJobForExecution() {
    try {
      Job job;

      while ((job = getNextEligibleJobForExecution()) == null) {
        if (runningJobs.isEmpty()) {
          debouncer.publish(callback::onEmpty);
        }

        wait();
      }

      jobStorage.updateJobRunningState(job.getId(), true);
      runningJobs.add(job.getId());

      return job;
    } catch (InterruptedException e) {
      Log.e(TAG, "Interrupted.");
      throw new AssertionError(e);
    }
  }

  /**
   * Retrieves a string representing the state of the job queue. Intended for debugging.
   */
  @WorkerThread
  synchronized @NonNull String getDebugInfo() {
    List<JobSpec>        jobs         = jobStorage.getAllJobSpecs();
    List<ConstraintSpec> constraints  = jobStorage.getAllConstraintSpecs();
    List<DependencySpec> dependencies = jobStorage.getAllDependencySpecs();

    StringBuilder info = new StringBuilder();

    info.append("-- Jobs\n");
    if (!jobs.isEmpty()) {
      Stream.of(jobs).forEach(j -> info.append(j.toString()).append('\n'));
    } else {
      info.append("None\n");
    }

    info.append("\n-- Constraints\n");
    if (!constraints.isEmpty()) {
      Stream.of(constraints).forEach(c -> info.append(c.toString()).append('\n'));
    } else {
      info.append("None\n");
    }

    info.append("\n-- Dependencies\n");
    if (!dependencies.isEmpty()) {
      Stream.of(dependencies).forEach(d -> info.append(d.toString()).append('\n'));
    } else {
      info.append("None\n");
    }

    return info.toString();
  }

  @WorkerThread
  private boolean chainExceedsMaximumInstances(@NonNull List<List<Job>> chain) {
    if (chain.size() == 1 && chain.get(0).size() == 1) {
      Job solo = chain.get(0).get(0);

      if (solo.getParameters().getMaxInstances() != Job.Parameters.UNLIMITED &&
          jobStorage.getJobInstanceCount(solo.getFactoryKey()) >= solo.getParameters().getMaxInstances())
      {
        return true;
      }
    }
    return false;
  }

  @WorkerThread
  private void triggerOnSubmit(@NonNull List<List<Job>> chain) {
    Stream.of(chain)
          .forEach(list -> Stream.of(list).forEach(job -> {
            job.setContext(application);
            job.onSubmit();
          }));
  }

  @WorkerThread
  private void insertJobChain(@NonNull List<List<Job>> chain) {
    List<FullSpec> fullSpecs = new LinkedList<>();
    List<Job>      dependsOn = Collections.emptyList();

    for (List<Job> jobList : chain) {
      for (Job job : jobList) {
        fullSpecs.add(buildFullSpec(job, dependsOn));
      }
      dependsOn = jobList;
    }

    jobStorage.insertJobs(fullSpecs);
  }

  @WorkerThread
  private @NonNull FullSpec buildFullSpec(@NonNull Job job, @NonNull List<Job> dependsOn) {
    String id = UUID.randomUUID().toString();

    job.setId(id);
    job.setRunAttempt(0);

    JobSpec jobSpec = new JobSpec(job.getId(),
                                  job.getFactoryKey(),
                                  job.getParameters().getQueue(),
                                  job.getParameters().getCreateTime(),
                                  job.getNextRunAttemptTime(),
                                  job.getRunAttempt(),
                                  job.getParameters().getMaxAttempts(),
                                  job.getParameters().getMaxBackoff(),
                                  job.getParameters().getLifespan(),
                                  job.getParameters().getMaxInstances(),
                                  dataSerializer.serialize(job.serialize()),
                                  false);

    List<ConstraintSpec> constraintSpecs = Stream.of(job.getParameters().getConstraintKeys())
                                                 .map(key -> new ConstraintSpec(jobSpec.getId(), key))
                                                 .toList();

    List<DependencySpec> dependencySpecs = Stream.of(dependsOn)
                                                 .map(depends -> new DependencySpec(job.getId(), depends.getId()))
                                                 .toList();

    return new FullSpec(jobSpec, constraintSpecs, dependencySpecs);
  }

  @WorkerThread
  private void scheduleJobs(@NonNull List<Job> jobs) {
    for (Job job : jobs) {
      List<Constraint> constraints = Stream.of(job.getParameters().getConstraintKeys())
                                           .map(key -> new ConstraintSpec(job.getId(), key))
                                           .map(ConstraintSpec::getFactoryKey)
                                           .map(constraintInstantiator::instantiate)
                                           .toList();

      scheduler.schedule(0, constraints);
    }
  }

  @WorkerThread
  private @Nullable Job getNextEligibleJobForExecution() {
    List<JobSpec> jobSpecs = jobStorage.getPendingJobsWithNoDependenciesInCreatedOrder(System.currentTimeMillis());

    for (JobSpec jobSpec : jobSpecs) {
      List<ConstraintSpec> constraintSpecs = jobStorage.getConstraintSpecs(jobSpec.getId());
      List<Constraint>     constraints     = Stream.of(constraintSpecs)
                                                   .map(ConstraintSpec::getFactoryKey)
                                                   .map(constraintInstantiator::instantiate)
                                                   .toList();

      if (Stream.of(constraints).allMatch(Constraint::isMet)) {
        return createJob(jobSpec, constraintSpecs);
      }
    }

    return null;
  }

  private @NonNull Job createJob(@NonNull JobSpec jobSpec, @NonNull List<ConstraintSpec> constraintSpecs) {
    Job.Parameters parameters = buildJobParameters(jobSpec, constraintSpecs);
    Data           data       = dataSerializer.deserialize(jobSpec.getSerializedData());
    Job            job        = jobInstantiator.instantiate(jobSpec.getFactoryKey(), parameters, data);

    job.setId(jobSpec.getId());
    job.setRunAttempt(jobSpec.getRunAttempt());
    job.setNextRunAttemptTime(jobSpec.getNextRunAttemptTime());
    job.setContext(application);

    dependencyInjector.injectDependencies(job);

    return job;
  }

  private @NonNull Job.Parameters buildJobParameters(@NonNull JobSpec jobSpec, @NonNull List<ConstraintSpec> constraintSpecs) {
    return new Job.Parameters.Builder()
                  .setCreateTime(jobSpec.getCreateTime())
                  .setLifespan(jobSpec.getLifespan())
                  .setMaxAttempts(jobSpec.getMaxAttempts())
                  .setQueue(jobSpec.getQueueKey())
                  .setConstraints(Stream.of(constraintSpecs).map(ConstraintSpec::getFactoryKey).toList())
                  .build();
  }

  private long calculateNextRunAttemptTime(long currentTime, int nextAttempt, long maxBackoff) {
    int  boundedAttempt     = Math.min(nextAttempt, 30);
    long exponentialBackoff = (long) Math.pow(2, boundedAttempt) * 1000;
    long actualBackoff      = Math.min(exponentialBackoff, maxBackoff);

    return currentTime + actualBackoff;
  }

  interface Callback {
    void onEmpty();
  }
}
