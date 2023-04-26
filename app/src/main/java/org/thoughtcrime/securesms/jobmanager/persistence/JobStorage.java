package org.thoughtcrime.securesms.jobmanager.persistence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.List;
import java.util.Set;

public interface JobStorage {

  @WorkerThread
  void init();

  @WorkerThread
  void insertJobs(@NonNull List<FullSpec> fullSpecs);

  @WorkerThread
  @Nullable JobSpec getJobSpec(@NonNull String id);

  @WorkerThread
  @NonNull List<JobSpec> getAllJobSpecs();

  @WorkerThread
  @NonNull List<JobSpec> getPendingJobsWithNoDependenciesInCreatedOrder(long currentTime);

  @WorkerThread
  @NonNull List<JobSpec> getJobsInQueue(@NonNull String queue);

  @WorkerThread
  int getJobCountForFactory(@NonNull String factoryKey);

  @WorkerThread
  int getJobCountForFactoryAndQueue(@NonNull String factoryKey, @NonNull String queueKey);

  @WorkerThread
  boolean areQueuesEmpty(@NonNull Set<String> queueKeys);

  @WorkerThread
  void updateJobRunningState(@NonNull String id, boolean isRunning);

  @WorkerThread
  void updateJobAfterRetry(@NonNull String id, boolean isRunning, int runAttempt, long nextRunAttemptTime, @Nullable byte[] serializedData);

  @WorkerThread
  void updateAllJobsToBePending();

  @WorkerThread
  void updateJobs(@NonNull List<JobSpec> jobSpecs);

  @WorkerThread
  void deleteJob(@NonNull String id);

  @WorkerThread
  void deleteJobs(@NonNull List<String> ids);

  @WorkerThread
  @NonNull List<ConstraintSpec> getConstraintSpecs(@NonNull String jobId);

  @WorkerThread
  @NonNull List<ConstraintSpec> getAllConstraintSpecs();

  @WorkerThread
  @NonNull List<DependencySpec> getDependencySpecsThatDependOnJob(@NonNull String jobSpecId);

  @WorkerThread
  @NonNull List<DependencySpec> getAllDependencySpecs();
}
