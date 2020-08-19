package org.thoughtcrime.securesms.jobmanager.persistence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.List;

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
  int getJobInstanceCount(@NonNull String factoryKey);

  @WorkerThread
  void updateJobRunningState(@NonNull String id, boolean isRunning);

  @WorkerThread
  void updateJobAfterRetry(@NonNull String id, boolean isRunning, int runAttempt, long nextRunAttemptTime);

  @WorkerThread
  void updateAllJobsToBePending();

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
