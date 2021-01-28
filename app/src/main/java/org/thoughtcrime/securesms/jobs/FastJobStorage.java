package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.JobDatabase;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.persistence.ConstraintSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.DependencySpec;
import org.thoughtcrime.securesms.jobmanager.persistence.FullSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.JobStorage;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class FastJobStorage implements JobStorage {

  private static final String TAG = Log.tag(FastJobStorage.class);

  private final JobDatabase jobDatabase;

  private final List<JobSpec>                     jobs;
  private final Map<String, List<ConstraintSpec>> constraintsByJobId;
  private final Map<String, List<DependencySpec>> dependenciesByJobId;

  public FastJobStorage(@NonNull JobDatabase jobDatabase) {
    this.jobDatabase         = jobDatabase;
    this.jobs                = new ArrayList<>();
    this.constraintsByJobId  = new HashMap<>();
    this.dependenciesByJobId = new HashMap<>();
  }

  @Override
  public synchronized void init() {
    List<JobSpec>        jobSpecs        = jobDatabase.getAllJobSpecs();
    List<ConstraintSpec> constraintSpecs = jobDatabase.getAllConstraintSpecs();
    List<DependencySpec> dependencySpecs = jobDatabase.getAllDependencySpecs();

    jobs.addAll(jobSpecs);

    for (ConstraintSpec constraintSpec: constraintSpecs) {
      List<ConstraintSpec> jobConstraints = Util.getOrDefault(constraintsByJobId, constraintSpec.getJobSpecId(), new LinkedList<>());
      jobConstraints.add(constraintSpec);
      constraintsByJobId.put(constraintSpec.getJobSpecId(), jobConstraints);
    }

    for (DependencySpec dependencySpec : dependencySpecs) {
      List<DependencySpec> jobDependencies = Util.getOrDefault(dependenciesByJobId, dependencySpec.getJobId(), new LinkedList<>());
      jobDependencies.add(dependencySpec);
      dependenciesByJobId.put(dependencySpec.getJobId(), jobDependencies);
    }
  }

  @Override
  public synchronized void insertJobs(@NonNull List<FullSpec> fullSpecs) {
    List<FullSpec> durable = Stream.of(fullSpecs).filterNot(FullSpec::isMemoryOnly).toList();
    if (durable.size() > 0) {
      jobDatabase.insertJobs(durable);
    }

    for (FullSpec fullSpec : fullSpecs) {
      jobs.add(fullSpec.getJobSpec());
      constraintsByJobId.put(fullSpec.getJobSpec().getId(), fullSpec.getConstraintSpecs());
      dependenciesByJobId.put(fullSpec.getJobSpec().getId(), fullSpec.getDependencySpecs());
    }
  }

  @Override
  public synchronized @Nullable JobSpec getJobSpec(@NonNull String id) {
    for (JobSpec jobSpec : jobs) {
      if (jobSpec.getId().equals(id)) {
        return jobSpec;
      }
    }
    return null;
  }

  @Override
  public synchronized @NonNull List<JobSpec> getAllJobSpecs() {
    return new ArrayList<>(jobs);
  }

  @Override
  public synchronized @NonNull List<JobSpec> getPendingJobsWithNoDependenciesInCreatedOrder(long currentTime) {
    Optional<JobSpec> migrationJob = getMigrationJob();

    if (migrationJob.isPresent() && !migrationJob.get().isRunning() && migrationJob.get().getNextRunAttemptTime() <= currentTime) {
      return Collections.singletonList(migrationJob.get());
    } else if (migrationJob.isPresent()) {
      return Collections.emptyList();
    } else {
      return Stream.of(jobs)
                   .groupBy(jobSpec -> {
                     String queueKey = jobSpec.getQueueKey();
                     if (queueKey != null) {
                       return queueKey;
                     } else {
                       return jobSpec.getId();
                     }
                   })
                   .map(byQueueKey ->
                     Stream.of(byQueueKey.getValue()).sorted((j1, j2) -> Long.compare(j1.getCreateTime(), j2.getCreateTime()))
                           .findFirst()
                           .orElse(null)
                   )
                   .withoutNulls()
                   .filter(j -> {
                     List<DependencySpec> dependencies = dependenciesByJobId.get(j.getId());
                     return dependencies == null || dependencies.isEmpty();
                   })
                   .filterNot(JobSpec::isRunning)
                   .filter(j -> j.getNextRunAttemptTime() <= currentTime)
                   .sorted((j1, j2) -> Long.compare(j1.getCreateTime(), j2.getCreateTime()))
                   .toList();
    }
  }

  @Override
  public synchronized @NonNull List<JobSpec> getJobsInQueue(@NonNull String queue) {
    return Stream.of(jobs)
                 .filter(j -> queue.equals(j.getQueueKey()))
                 .sorted((j1, j2) -> Long.compare(j1.getCreateTime(), j2.getCreateTime()))
                 .toList();
  }

  private Optional<JobSpec> getMigrationJob() {
    return Optional.fromNullable(Stream.of(jobs)
                                       .filter(j -> Job.Parameters.MIGRATION_QUEUE_KEY.equals(j.getQueueKey()))
                                       .filter(this::firstInQueue)
                                       .findFirst()
                                       .orElse(null));
  }

  private boolean firstInQueue(@NonNull JobSpec job) {
    if (job.getQueueKey() == null) {
      return true;
    }

    return Stream.of(jobs)
                 .filter(j -> Util.equals(j.getQueueKey(), job.getQueueKey()))
                 .sorted((j1, j2) -> Long.compare(j1.getCreateTime(), j2.getCreateTime()))
                 .toList()
                 .get(0)
                 .equals(job);
  }

  @Override
  public synchronized int getJobCountForFactory(@NonNull String factoryKey) {
    return (int) Stream.of(jobs)
                       .filter(j -> j.getFactoryKey().equals(factoryKey))
                       .count();
  }

  @Override
  public synchronized int getJobCountForQueue(@NonNull String queueKey) {
    return (int) Stream.of(jobs)
                       .filter(j -> queueKey.equals(j.getQueueKey()))
                       .count();
  }

  @Override
  public synchronized void updateJobRunningState(@NonNull String id, boolean isRunning) {
    JobSpec job = getJobById(id);
    if (job == null || !job.isMemoryOnly()) {
      jobDatabase.updateJobRunningState(id, isRunning);
    }

    ListIterator<JobSpec> iter = jobs.listIterator();

    while (iter.hasNext()) {
      JobSpec existing = iter.next();
      if (existing.getId().equals(id)) {
        JobSpec updated = new JobSpec(existing.getId(),
                                      existing.getFactoryKey(),
                                      existing.getQueueKey(),
                                      existing.getCreateTime(),
                                      existing.getNextRunAttemptTime(),
                                      existing.getRunAttempt(),
                                      existing.getMaxAttempts(),
                                      existing.getMaxBackoff(),
                                      existing.getLifespan(),
                                      existing.getSerializedData(),
                                      existing.getSerializedInputData(),
                                      isRunning,
                                      existing.isMemoryOnly());
        iter.set(updated);
      }
    }
  }

  @Override
  public synchronized void updateJobAfterRetry(@NonNull String id, boolean isRunning, int runAttempt, long nextRunAttemptTime, @NonNull String serializedData) {
    JobSpec job = getJobById(id);
    if (job == null || !job.isMemoryOnly()) {
      jobDatabase.updateJobAfterRetry(id, isRunning, runAttempt, nextRunAttemptTime, serializedData);
    }

    ListIterator<JobSpec> iter = jobs.listIterator();

    while (iter.hasNext()) {
      JobSpec existing = iter.next();
      if (existing.getId().equals(id)) {
        JobSpec updated = new JobSpec(existing.getId(),
                                      existing.getFactoryKey(),
                                      existing.getQueueKey(),
                                      existing.getCreateTime(),
                                      nextRunAttemptTime,
                                      runAttempt,
                                      existing.getMaxAttempts(),
                                      existing.getMaxBackoff(),
                                      existing.getLifespan(),
                                      serializedData,
                                      existing.getSerializedInputData(),
                                      isRunning,
                                      existing.isMemoryOnly());
        iter.set(updated);
      }
    }
  }

  @Override
  public synchronized void updateAllJobsToBePending() {
    jobDatabase.updateAllJobsToBePending();

    ListIterator<JobSpec> iter = jobs.listIterator();

    while (iter.hasNext()) {
      JobSpec existing = iter.next();
      JobSpec updated  = new JobSpec(existing.getId(),
                                     existing.getFactoryKey(),
                                     existing.getQueueKey(),
                                     existing.getCreateTime(),
                                     existing.getNextRunAttemptTime(),
                                     existing.getRunAttempt(),
                                     existing.getMaxAttempts(),
                                     existing.getMaxBackoff(),
                                     existing.getLifespan(),
                                     existing.getSerializedData(),
                                     existing.getSerializedInputData(),
                                     false,
                                     existing.isMemoryOnly());
      iter.set(updated);
    }
  }

  @Override
  public void updateJobs(@NonNull List<JobSpec> jobSpecs) {
    List<JobSpec> durable = new ArrayList<>(jobSpecs.size());
    for (JobSpec update : jobSpecs) {
      JobSpec found = getJobById(update.getId());
      if (found == null || !found.isMemoryOnly()) {
        durable.add(update);
      }
    }

    if (durable.size() > 0) {
      jobDatabase.updateJobs(durable);
    }

    Map<String, JobSpec>  updates = Stream.of(jobSpecs).collect(Collectors.toMap(JobSpec::getId));
    ListIterator<JobSpec> iter    = jobs.listIterator();

    while (iter.hasNext()) {
      JobSpec existing = iter.next();
      JobSpec update   = updates.get(existing.getId());

      if (update != null) {
        iter.set(update);
      }
    }
  }

  @Override
  public synchronized void deleteJob(@NonNull String jobId) {
    deleteJobs(Collections.singletonList(jobId));
  }

  @Override
  public synchronized void deleteJobs(@NonNull List<String> jobIds) {
    List<String> durableIds = new ArrayList<>(jobIds.size());
    for (String id : jobIds) {
      JobSpec job = getJobById(id);
      if (job == null || !job.isMemoryOnly()) {
        durableIds.add(id);
      }
    }

    if (durableIds.size() > 0) {
      jobDatabase.deleteJobs(durableIds);
    }

    Set<String> deleteIds = new HashSet<>(jobIds);

    Iterator<JobSpec> jobIter = jobs.iterator();
    while (jobIter.hasNext()) {
      if (deleteIds.contains(jobIter.next().getId())) {
        jobIter.remove();
      }
    }

    for (String jobId : jobIds) {
      constraintsByJobId.remove(jobId);
      dependenciesByJobId.remove(jobId);

      for (Map.Entry<String, List<DependencySpec>> entry : dependenciesByJobId.entrySet()) {
        Iterator<DependencySpec> depedencyIter = entry.getValue().iterator();

        while (depedencyIter.hasNext()) {
          if (depedencyIter.next().getDependsOnJobId().equals(jobId)) {
            depedencyIter.remove();
          }
        }
      }
    }
  }

  @Override
  public synchronized @NonNull List<ConstraintSpec> getConstraintSpecs(@NonNull String jobId) {
    return Util.getOrDefault(constraintsByJobId, jobId, new LinkedList<>());
  }

  @Override
  public synchronized @NonNull List<ConstraintSpec> getAllConstraintSpecs() {
    return Stream.of(constraintsByJobId)
                 .map(Map.Entry::getValue)
                 .flatMap(Stream::of)
                 .toList();
  }

  @Override
  public synchronized @NonNull List<DependencySpec> getDependencySpecsThatDependOnJob(@NonNull String jobSpecId) {
    List<DependencySpec> layer = getSingleLayerOfDependencySpecsThatDependOnJob(jobSpecId);
    List<DependencySpec> all   = new ArrayList<>(layer);

    Set<String> activeJobIds;

    do {
      activeJobIds = Stream.of(layer).map(DependencySpec::getJobId).collect(Collectors.toSet());
      layer.clear();

      for (String activeJobId : activeJobIds) {
        layer.addAll(getSingleLayerOfDependencySpecsThatDependOnJob(activeJobId));
      }

      all.addAll(layer);
    } while (!layer.isEmpty());

    return all;
  }

  private @NonNull List<DependencySpec> getSingleLayerOfDependencySpecsThatDependOnJob(@NonNull String jobSpecId) {
    return Stream.of(dependenciesByJobId.entrySet())
                 .map(Map.Entry::getValue)
                 .flatMap(Stream::of)
                 .filter(j -> j.getDependsOnJobId().equals(jobSpecId))
                 .toList();
  }

  @Override
  public @NonNull List<DependencySpec> getAllDependencySpecs() {
    return Stream.of(dependenciesByJobId)
                 .map(Map.Entry::getValue)
                 .flatMap(Stream::of)
                 .toList();
  }

  private JobSpec getJobById(@NonNull String id) {
    for (JobSpec job : jobs) {
      if (job.getId().equals(id)) {
        return job;
      }
    }
    Log.w(TAG, "Was looking for job with ID JOB::" + id + ", but it doesn't exist in memory!");
    return null;
  }
}
