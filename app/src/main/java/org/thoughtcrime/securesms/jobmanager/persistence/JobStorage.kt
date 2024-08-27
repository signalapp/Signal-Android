package org.thoughtcrime.securesms.jobmanager.persistence

import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.jobs.MinimalJobSpec
import java.util.function.Predicate

interface JobStorage {
  @WorkerThread
  fun init()

  @WorkerThread
  fun insertJobs(fullSpecs: List<FullSpec>)

  @WorkerThread
  fun getJobSpec(id: String): JobSpec?

  @WorkerThread
  fun getAllMatchingFilter(predicate: Predicate<JobSpec>): List<JobSpec>

  @WorkerThread
  fun getNextEligibleJob(currentTime: Long, filter: (MinimalJobSpec) -> Boolean): JobSpec?

  @WorkerThread
  fun getJobsInQueue(queue: String): List<JobSpec>

  @WorkerThread
  fun getJobCountForFactory(factoryKey: String): Int

  @WorkerThread
  fun getJobCountForFactoryAndQueue(factoryKey: String, queueKey: String): Int

  @WorkerThread
  fun areQueuesEmpty(queueKeys: Set<String>): Boolean

  @WorkerThread
  fun markJobAsRunning(id: String, currentTime: Long)

  @WorkerThread
  fun updateJobAfterRetry(id: String, currentTime: Long, runAttempt: Int, nextBackoffInterval: Long, serializedData: ByteArray?)

  @WorkerThread
  fun updateAllJobsToBePending()

  @WorkerThread
  fun updateJobs(jobSpecs: List<JobSpec>)

  @WorkerThread
  fun transformJobs(transformer: (JobSpec) -> JobSpec)

  @WorkerThread
  fun deleteJob(id: String)

  @WorkerThread
  fun deleteJobs(ids: List<String>)

  @WorkerThread
  fun getConstraintSpecs(jobId: String): List<ConstraintSpec>

  @WorkerThread
  fun getDependencySpecsThatDependOnJob(jobSpecId: String): List<DependencySpec>

  @WorkerThread
  fun debugGetJobSpecs(limit: Int): List<JobSpec>

  @WorkerThread
  fun debugGetConstraintSpecs(limit: Int): List<ConstraintSpec>

  @WorkerThread
  fun debugGetAllDependencySpecs(): List<DependencySpec>
}
