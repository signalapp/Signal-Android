package org.thoughtcrime.securesms.jobs

import org.thoughtcrime.securesms.database.JobDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.persistence.ConstraintSpec
import org.thoughtcrime.securesms.jobmanager.persistence.DependencySpec
import org.thoughtcrime.securesms.jobmanager.persistence.FullSpec
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec
import org.thoughtcrime.securesms.jobmanager.persistence.JobStorage
import java.util.TreeSet

class FastJobStorage(private val jobDatabase: JobDatabase) : JobStorage {

  // TODO [job] We need a new jobspec that has no data (for space efficiency), and ideally no other random stuff that we don't need for filtering

  private val jobs: MutableList<JobSpec> = mutableListOf()
  private val constraintsByJobId: MutableMap<String, MutableList<ConstraintSpec>> = mutableMapOf()
  private val dependenciesByJobId: MutableMap<String, MutableList<DependencySpec>> = mutableMapOf()

  private val eligibleJobs: TreeSet<JobSpec> = TreeSet(EligibleJobComparator)
  private val migrationJobs: TreeSet<JobSpec> = TreeSet(compareBy { it.createTime })
  private val mostEligibleJobForQueue: MutableMap<String, JobSpec> = hashMapOf()

  @Synchronized
  override fun init() {
    jobs += jobDatabase.getAllJobSpecs()

    for (job in jobs) {
      if (job.queueKey == Job.Parameters.MIGRATION_QUEUE_KEY) {
        migrationJobs += job
      } else {
        // TODO [job] Because we're using a TreeSet, this operation becomes n*log(n). Ideal complexity for a sort, but think more about whether a bulk sort would be better.
        placeJobInEligibleList(job)
      }
    }

    for (constraintSpec in jobDatabase.getAllConstraintSpecs()) {
      val jobConstraints: MutableList<ConstraintSpec> = constraintsByJobId.getOrPut(constraintSpec.jobSpecId) { mutableListOf() }
      jobConstraints += constraintSpec
    }

    for (dependencySpec in jobDatabase.getAllDependencySpecs().filterNot { it.hasCircularDependency() }) {
      val jobDependencies: MutableList<DependencySpec> = dependenciesByJobId.getOrPut(dependencySpec.jobId) { mutableListOf() }
      jobDependencies += dependencySpec
    }
  }

  @Synchronized
  override fun insertJobs(fullSpecs: List<FullSpec>) {
    val durable: List<FullSpec> = fullSpecs.filterNot { it.isMemoryOnly }

    if (durable.isNotEmpty()) {
      jobDatabase.insertJobs(durable)
    }

    for (fullSpec in fullSpecs) {
      jobs += fullSpec.jobSpec

      if (fullSpec.jobSpec.queueKey == Job.Parameters.MIGRATION_QUEUE_KEY) {
        migrationJobs += fullSpec.jobSpec
      } else {
        placeJobInEligibleList(fullSpec.jobSpec)
      }

      constraintsByJobId[fullSpec.jobSpec.id] = fullSpec.constraintSpecs.toMutableList()
      dependenciesByJobId[fullSpec.jobSpec.id] = fullSpec.dependencySpecs.toMutableList()
    }
  }

  @Synchronized
  override fun getJobSpec(id: String): JobSpec? {
    return jobs.firstOrNull { it.id == id }
  }

  @Synchronized
  override fun getAllJobSpecs(): List<JobSpec> {
    // TODO [job] this will have to change
    return ArrayList(jobs)
  }

  @Synchronized
  override fun getPendingJobsWithNoDependenciesInCreatedOrder(currentTime: Long): List<JobSpec> {
    val migrationJob: JobSpec? = migrationJobs.firstOrNull()

    return if (migrationJob != null && !migrationJob.isRunning && migrationJob.hasEligibleRunTime(currentTime)) {
      listOf(migrationJob)
    } else if (migrationJob != null) {
      emptyList()
    } else {
      eligibleJobs
        .asSequence()
        .filter { job ->
          // Filter out all jobs with unmet dependencies
          dependenciesByJobId[job.id].isNullOrEmpty()
        }
        .filterNot { it.isRunning }
        .filter { job -> job.hasEligibleRunTime(currentTime) }
        .toList()

      // Note: The priority sort at the end is safe because it's stable. That means that within jobs with the same priority, they will still be sorted by createTime.
    }
  }

  @Synchronized
  override fun getJobsInQueue(queue: String): List<JobSpec> {
    return jobs.filter { it.queueKey == queue }
  }

  @Synchronized
  override fun getJobCountForFactory(factoryKey: String): Int {
    return jobs
      .filter { it.factoryKey == factoryKey }
      .size
  }

  @Synchronized
  override fun getJobCountForFactoryAndQueue(factoryKey: String, queueKey: String): Int {
    return jobs
      .filter { it.factoryKey == factoryKey && it.queueKey == queueKey }
      .size
  }

  @Synchronized
  override fun areQueuesEmpty(queueKeys: Set<String>): Boolean {
    return jobs.none { it.queueKey != null && queueKeys.contains(it.queueKey) }
  }

  @Synchronized
  override fun markJobAsRunning(id: String, currentTime: Long) {
    val job: JobSpec? = getJobSpec(id)
    if (job == null || !job.isMemoryOnly) {
      jobDatabase.markJobAsRunning(id, currentTime)
    }

    updateJobsInMemory(
      filter = { it.id == id },
      transformer = { jobSpec ->
        jobSpec.copy(
          isRunning = true,
          lastRunAttemptTime = currentTime
        )
      },
      singleUpdate = true
    )
  }

  @Synchronized
  override fun updateJobAfterRetry(id: String, currentTime: Long, runAttempt: Int, nextBackoffInterval: Long, serializedData: ByteArray?) {
    val job = getJobSpec(id)
    if (job == null || !job.isMemoryOnly) {
      jobDatabase.updateJobAfterRetry(id, currentTime, runAttempt, nextBackoffInterval, serializedData)
    }

    updateJobsInMemory(
      filter = { it.id == id },
      transformer = { jobSpec ->
        jobSpec.copy(
          isRunning = false,
          runAttempt = runAttempt,
          lastRunAttemptTime = currentTime,
          nextBackoffInterval = nextBackoffInterval,
          serializedData = serializedData
        )
      },
      singleUpdate = true
    )
  }

  private fun updateJobsInMemory(filter: (JobSpec) -> Boolean, transformer: (JobSpec) -> JobSpec, singleUpdate: Boolean = false) {
    val iterator = jobs.listIterator()

    while (iterator.hasNext()) {
      val current = iterator.next()

      if (filter(current)) {
        val updated = transformer(current)
        iterator.set(updated)
        replaceJobInEligibleList(current, updated)

        if (singleUpdate) {
          return
        }
      }
    }
  }

  @Synchronized
  override fun updateAllJobsToBePending() {
    jobDatabase.updateAllJobsToBePending()

    updateJobsInMemory(
      filter = { it.isRunning },
      transformer = { jobSpec ->
        jobSpec.copy(
          isRunning = false
        )
      }
    )
  }

  @Synchronized
  override fun updateJobs(jobSpecs: List<JobSpec>) {
    val durable: List<JobSpec> = jobSpecs
      .filter { updatedJob ->
        val found = getJobSpec(updatedJob.id)
        found != null && !found.isMemoryOnly
      }

    if (durable.isNotEmpty()) {
      jobDatabase.updateJobs(durable)
    }

    val updatesById: Map<String, JobSpec> = jobSpecs.associateBy { it.id }

    updateJobsInMemory(
      filter = { updatesById.containsKey(it.id) },
      transformer = { updatesById.getValue(it.id) }
    )
  }

  @Synchronized
  override fun deleteJob(jobId: String) {
    deleteJobs(listOf(jobId))
  }

  @Synchronized
  override fun deleteJobs(jobIds: List<String>) {
    val jobsToDelete: Set<JobSpec> = jobIds
      .mapNotNull { getJobSpec(it) }
      .toSet()
    val durableJobIdsToDelete: List<String> = jobsToDelete
      .filterNot { it.isMemoryOnly }
      .map { it.id }

    if (durableJobIdsToDelete.isNotEmpty()) {
      jobDatabase.deleteJobs(durableJobIdsToDelete)
    }

    val deleteIds: Set<String> = jobIds.toSet()
    jobs.removeIf { deleteIds.contains(it.id) }
    eligibleJobs.removeAll(jobsToDelete)
    migrationJobs.removeAll(jobsToDelete)

    for (jobId in jobIds) {
      constraintsByJobId.remove(jobId)
      dependenciesByJobId.remove(jobId)

      for (dependencyList in dependenciesByJobId.values) {
        val iter = dependencyList.iterator()

        while (iter.hasNext()) {
          if (iter.next().dependsOnJobId == jobId) {
            iter.remove()
          }
        }
      }
    }
  }

  @Synchronized
  override fun getConstraintSpecs(jobId: String): List<ConstraintSpec> {
    return constraintsByJobId.getOrElse(jobId) { listOf() }
  }

  @Synchronized
  override fun getAllConstraintSpecs(): List<ConstraintSpec> {
    return constraintsByJobId.values.flatten()
  }

  @Synchronized
  override fun getDependencySpecsThatDependOnJob(jobSpecId: String): List<DependencySpec> {
    val all: MutableList<DependencySpec> = mutableListOf()

    var dependencyLayer: List<DependencySpec> = getSingleLayerOfDependencySpecsThatDependOnJob(jobSpecId)

    while (dependencyLayer.isNotEmpty()) {
      all += dependencyLayer

      dependencyLayer = dependencyLayer
        .map { getSingleLayerOfDependencySpecsThatDependOnJob(it.jobId) }
        .flatten()
    }

    return all
  }

  @Synchronized
  override fun getAllDependencySpecs(): List<DependencySpec> {
    return dependenciesByJobId.values.flatten()
  }

  private fun placeJobInEligibleList(job: JobSpec) {
    var jobToPlace: JobSpec? = job

    if (job.queueKey != null) {
      val existingJobInQueue = mostEligibleJobForQueue[job.queueKey]
      if (existingJobInQueue != null) {
        // We only want a single job from each queue. It should be the oldest job with the highest priority.
        if (job.priority > existingJobInQueue.priority || (job.priority == existingJobInQueue.priority && job.createTime < existingJobInQueue.createTime)) {
          mostEligibleJobForQueue[job.queueKey] = job
          eligibleJobs.remove(existingJobInQueue)
        } else {
          // There's a more eligible job in the queue already, so no need to put it in the eligible list
          jobToPlace = null
        }
      }
    }

    if (jobToPlace == null) {
      return
    }

    jobToPlace.queueKey?.let { queueKey ->
      mostEligibleJobForQueue[queueKey] = job
    }

    // At this point, anything queue-related has been handled. We just need to insert this job in the correct spot in the list.
    // Thankfully, we're using a TreeSet, so sorting is automatic.

    eligibleJobs += jobToPlace
  }

  private fun replaceJobInEligibleList(current: JobSpec?, updated: JobSpec?) {
    if (current == null || updated == null) {
      return
    }

    if (updated.queueKey == Job.Parameters.MIGRATION_QUEUE_KEY) {
      migrationJobs.remove(current)
      migrationJobs += updated
    } else {
      eligibleJobs.remove(current)
      current.queueKey?.let { queueKey ->
        if (mostEligibleJobForQueue[queueKey] == current) {
          mostEligibleJobForQueue.remove(queueKey)
        }
      }
      placeJobInEligibleList(updated)
    }
  }

  /**
   * Note that this is currently only checking a specific kind of circular dependency -- ones that are
   * created between dependencies and queues.
   *
   * More specifically, dependencies where one job depends on another job in the same queue that was
   * scheduled *after* it. These dependencies will never resolve. Under normal circumstances these
   * won't occur, but *could* occur if the user changed their clock (either purposefully or automatically).
   *
   * Rather than go through and delete them from the database, removing them from memory at load time
   * serves the same effect and doesn't require new write methods. This should also be very rare.
   */
  private fun DependencySpec.hasCircularDependency(): Boolean {
    val job = getJobSpec(this.jobId)
    val dependsOnJob = getJobSpec(this.dependsOnJobId)

    if (job == null || dependsOnJob == null) {
      return false
    }

    if (job.queueKey == null || dependsOnJob.queueKey == null) {
      return false
    }

    if (job.queueKey != dependsOnJob.queueKey) {
      return false
    }

    return dependsOnJob.createTime > job.createTime
  }

  /**
   * Whether or not the job's eligible to be run based off of it's [Job.nextBackoffInterval] and other properties.
   */
  private fun JobSpec.hasEligibleRunTime(currentTime: Long): Boolean {
    return this.lastRunAttemptTime > currentTime || (this.lastRunAttemptTime + this.nextBackoffInterval) < currentTime
  }

  private fun getSingleLayerOfDependencySpecsThatDependOnJob(jobSpecId: String): List<DependencySpec> {
    return dependenciesByJobId
      .values
      .flatten()
      .filter { it.dependsOnJobId == jobSpecId }
  }

  private object EligibleJobComparator : Comparator<JobSpec> {
    override fun compare(o1: JobSpec, o2: JobSpec): Int {
      // We want to sort by priority descending, then createTime ascending

      // CAUTION: This is used by a TreeSet, so it must be consistent with equals.
      //          If this compare function says two objects are equal, then only one will be allowed in the set!
      return when {
        o1.priority > o2.priority -> -1
        o1.priority < o2.priority -> 1
        o1.createTime < o2.createTime -> -1
        o1.createTime > o2.createTime -> 1
        else -> o1.id.compareTo(o2.id)
      }
    }
  }

  private data class MinimalJobSpec(
    val id: String,
    val factoryKey: String,
    val queueKey: String?,
    val createTime: Long,
    val priority: Int,
    val isRunning: Boolean,
    val isMemoryOnly: Boolean
  )
}
