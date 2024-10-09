package org.thoughtcrime.securesms.jobs

import androidx.annotation.VisibleForTesting
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.JobDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.persistence.ConstraintSpec
import org.thoughtcrime.securesms.jobmanager.persistence.DependencySpec
import org.thoughtcrime.securesms.jobmanager.persistence.FullSpec
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec
import org.thoughtcrime.securesms.jobmanager.persistence.JobStorage
import org.thoughtcrime.securesms.util.LRUCache
import java.util.TreeSet
import java.util.function.Predicate

class FastJobStorage(private val jobDatabase: JobDatabase) : JobStorage {

  companion object {
    private val TAG = Log.tag(FastJobStorage::class)
    private const val JOB_CACHE_LIMIT = 1000
    private const val DEBUG = false
  }

  /** We keep a trimmed down version of every job in memory. */
  private val minimalJobs: MutableList<MinimalJobSpec> = mutableListOf()

  /**
   * We keep a set of job specs in memory to facilitate fast retrieval. This is important because the most common job storage pattern is
   * [getNextEligibleJob], which needs to return full specs.
   */
  private val jobSpecCache: LRUCache<String, JobSpec> = LRUCache(JOB_CACHE_LIMIT)

  /**
   * We keep a set of constraints in memory, seeded by the same jobs in the [jobSpecCache]. It doesn't need to necessarily stay in sync with that cache, though.
   * The most important property to maintain is that if there's an entry in the map for a given jobId, we need to ensure we have _all_ of the constraints for
   * that job. Important for [getConstraintSpecs].
   */
  private val constraintsByJobId: LRUCache<String, MutableList<ConstraintSpec>> = LRUCache(JOB_CACHE_LIMIT)

  /** We keep every dependency in memory, since there aren't that many, and managing a limited subset would be very complicated. */
  private val dependenciesByJobId: MutableMap<String, MutableList<DependencySpec>> = hashMapOf()

  /** The list of jobs eligible to be returned from [getNextEligibleJob], kept sorted in the appropriate order. */
  private val eligibleJobs: TreeSet<MinimalJobSpec> = TreeSet(EligibleMinJobComparator)

  /** All migration-related jobs, kept in the appropriate order. */
  private val migrationJobs: TreeSet<MinimalJobSpec> = TreeSet(compareBy { it.createTime })

  /** We need a fast way to know what the "most eligible job" is for a given queue. This serves as a lookup table that speeds up the maintenance of [eligibleJobs]. */
  private val mostEligibleJobForQueue: MutableMap<String, MinimalJobSpec> = hashMapOf()

  @Synchronized
  override fun init() {
    val stopwatch = Stopwatch("init", decimalPlaces = 2)
    minimalJobs += jobDatabase.getAllMinimalJobSpecs()
    stopwatch.split("fetch-min-jobs")

    for (job in minimalJobs) {
      if (job.queueKey == Job.Parameters.MIGRATION_QUEUE_KEY) {
        migrationJobs += job
      } else {
        placeJobInEligibleList(job)
      }
    }
    stopwatch.split("sort-min-jobs")

    jobDatabase.getJobSpecs(JOB_CACHE_LIMIT).forEach {
      jobSpecCache[it.id] = it
    }
    stopwatch.split("fetch-full-jobs")

    for (constraintSpec in jobDatabase.getConstraintSpecsForJobs(jobSpecCache.keys)) {
      val jobConstraints: MutableList<ConstraintSpec> = constraintsByJobId.getOrPut(constraintSpec.jobSpecId) { mutableListOf() }
      jobConstraints += constraintSpec
    }
    stopwatch.split("fetch-constraints")

    for (dependencySpec in jobDatabase.getAllDependencySpecs().filterNot { it.hasCircularDependency() }) {
      val jobDependencies: MutableList<DependencySpec> = dependenciesByJobId.getOrPut(dependencySpec.jobId) { mutableListOf() }
      jobDependencies += dependencySpec
    }
    stopwatch.split("fetch-dependencies")

    stopwatch.stop(TAG)
  }

  @Synchronized
  override fun insertJobs(fullSpecs: List<FullSpec>) {
    val stopwatch = debugStopwatch("insert")
    val durable: List<FullSpec> = fullSpecs.filterNot { it.isMemoryOnly }

    if (durable.isNotEmpty()) {
      jobDatabase.insertJobs(durable)
    }
    stopwatch?.split("db")

    for (fullSpec in fullSpecs) {
      val minimalJobSpec = fullSpec.jobSpec.toMinimalJobSpec()
      minimalJobs += minimalJobSpec
      jobSpecCache[fullSpec.jobSpec.id] = fullSpec.jobSpec

      if (fullSpec.jobSpec.queueKey == Job.Parameters.MIGRATION_QUEUE_KEY) {
        migrationJobs += minimalJobSpec
      } else {
        placeJobInEligibleList(minimalJobSpec)
      }

      constraintsByJobId[fullSpec.jobSpec.id] = fullSpec.constraintSpecs.toMutableList()
      dependenciesByJobId[fullSpec.jobSpec.id] = fullSpec.dependencySpecs.toMutableList()
    }
    stopwatch?.split("cache")
    stopwatch?.stop(TAG)
  }

  @Synchronized
  override fun getJobSpec(id: String): JobSpec? {
    return minimalJobs.firstOrNull { it.id == id }?.toJobSpec()
  }

  @Synchronized
  override fun getAllMatchingFilter(predicate: Predicate<JobSpec>): List<JobSpec> {
    return jobDatabase.getAllMatchingFilter(predicate)
  }

  @Synchronized
  override fun getNextEligibleJob(currentTime: Long, filter: (MinimalJobSpec) -> Boolean): JobSpec? {
    val stopwatch = debugStopwatch("get-pending")
    val migrationJob: MinimalJobSpec? = migrationJobs.firstOrNull()

    return if (migrationJob != null && !migrationJob.isRunning && migrationJob.hasEligibleRunTime(currentTime)) {
      migrationJob.toJobSpec()
    } else if (migrationJob != null) {
      null
    } else {
      eligibleJobs
        .asSequence()
        .filter { job ->
          // Filter out all jobs with unmet dependencies
          dependenciesByJobId[job.id].isNullOrEmpty()
        }
        .filterNot { it.isRunning }
        .filter { job -> job.hasEligibleRunTime(currentTime) }
        .firstOrNull(filter)
        ?.toJobSpec()
    }.also {
      stopwatch?.stop(TAG)
    }
  }

  @Synchronized
  override fun getJobsInQueue(queue: String): List<JobSpec> {
    return minimalJobs
      .filter { it.queueKey == queue }
      .mapNotNull { it.toJobSpec() }
  }

  @Synchronized
  override fun getJobCountForFactory(factoryKey: String): Int {
    return minimalJobs
      .filter { it.factoryKey == factoryKey }
      .size
  }

  @Synchronized
  override fun getJobCountForFactoryAndQueue(factoryKey: String, queueKey: String): Int {
    return minimalJobs
      .filter { it.factoryKey == factoryKey && it.queueKey == queueKey }
      .size
  }

  @Synchronized
  override fun areQueuesEmpty(queueKeys: Set<String>): Boolean {
    return minimalJobs.none { it.queueKey != null && queueKeys.contains(it.queueKey) }
  }

  @Synchronized
  override fun markJobAsRunning(id: String, currentTime: Long) {
    val job: JobSpec? = getJobSpec(id)
    if (job == null || !job.isMemoryOnly) {
      jobDatabase.markJobAsRunning(id, currentTime)
      // Don't need to update jobSpecCache because all changed fields are in the min spec
    }

    updateCachedJobSpecs(
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

      // Note: Serialized data and run attempt are the only JobSpec-specific fields that need to be updated -- the rest are in MinimalJobSpec and will be
      //       updated below.
      val cached = jobSpecCache[id]
      if (cached != null) {
        jobSpecCache[id] = cached.copy(
          serializedData = serializedData,
          runAttempt = runAttempt
        )
      }
    }

    updateCachedJobSpecs(
      filter = { it.id == id },
      transformer = { jobSpec ->
        jobSpec.copy(
          isRunning = false,
          lastRunAttemptTime = currentTime,
          nextBackoffInterval = nextBackoffInterval
        )
      },
      singleUpdate = true
    )
  }

  @Synchronized
  override fun updateAllJobsToBePending() {
    jobDatabase.updateAllJobsToBePending()
    // Don't need to update jobSpecCache because all changed fields are in the min spec

    updateCachedJobSpecs(
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

    val updatesById: Map<String, MinimalJobSpec> = jobSpecs
      .map { it.toMinimalJobSpec() }
      .associateBy { it.id }

    updateCachedJobSpecs(
      filter = { updatesById.containsKey(it.id) },
      transformer = { updatesById.getValue(it.id) }
    )

    for (update in jobSpecs) {
      jobSpecCache[update.id] = update
    }
  }

  @Synchronized
  override fun transformJobs(transformer: (JobSpec) -> JobSpec) {
    val updated = jobDatabase.transformJobs(transformer)
    for (update in updated) {
      jobSpecCache[update.id] = update
    }

    val iterator = minimalJobs.listIterator()
    while (iterator.hasNext()) {
      val current = iterator.next()
      val updatedJob = updated.firstOrNull { it.id == current.id }

      if (updatedJob != null) {
        iterator.set(updatedJob.toMinimalJobSpec())
        replaceJobInEligibleList(current, updatedJob.toMinimalJobSpec())
      }
    }
  }

  @Synchronized
  override fun deleteJob(id: String) {
    deleteJobs(listOf(id))
  }

  @Synchronized
  override fun deleteJobs(ids: List<String>) {
    val jobsToDelete: Set<MinimalJobSpec> = ids
      .mapNotNull { id ->
        minimalJobs.firstOrNull { it.id == id }
      }
      .toSet()

    val durableJobIdsToDelete: List<String> = jobsToDelete
      .filterNot { it.isMemoryOnly }
      .map { it.id }

    val affectedQueues: Set<String> = jobsToDelete.mapNotNull { it.queueKey }.toSet()

    if (durableJobIdsToDelete.isNotEmpty()) {
      jobDatabase.deleteJobs(durableJobIdsToDelete)
    }

    val deleteIds: Set<String> = ids.toSet()
    minimalJobs.removeIf { deleteIds.contains(it.id) }
    jobSpecCache.keys.removeAll(deleteIds)
    eligibleJobs.removeIf { deleteIds.contains(it.id) }
    migrationJobs.removeIf { deleteIds.contains(it.id) }

    mostEligibleJobForQueue.keys.removeAll(affectedQueues)

    for (queue in affectedQueues) {
      jobDatabase.getMostEligibleJobInQueue(queue)?.let {
        jobSpecCache[it.id] = it
        placeJobInEligibleList(it.toMinimalJobSpec())
      }
    }

    for (jobId in ids) {
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
    return constraintsByJobId.getOrPut(jobId) {
      jobDatabase.getConstraintSpecsForJobs(listOf(jobId)).toMutableList()
    }
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
  override fun debugGetJobSpecs(limit: Int): List<JobSpec> {
    return jobDatabase.getJobSpecs(limit)
  }

  @Synchronized
  override fun debugGetConstraintSpecs(limit: Int): List<ConstraintSpec> {
    return jobDatabase.getConstraintSpecs(limit)
  }

  @Synchronized
  override fun debugGetAllDependencySpecs(): List<DependencySpec> {
    return dependenciesByJobId.values.flatten()
  }

  private fun updateCachedJobSpecs(filter: (MinimalJobSpec) -> Boolean, transformer: (MinimalJobSpec) -> MinimalJobSpec, singleUpdate: Boolean = false) {
    val iterator = minimalJobs.listIterator()

    while (iterator.hasNext()) {
      val current = iterator.next()

      if (filter(current)) {
        val updated = transformer(current)
        iterator.set(updated)
        replaceJobInEligibleList(current, updated)

        jobSpecCache.remove(current.id)?.let { currentJobSpec ->
          val updatedJobSpec = currentJobSpec.copy(
            id = updated.id,
            factoryKey = updated.factoryKey,
            queueKey = updated.queueKey,
            createTime = updated.createTime,
            lastRunAttemptTime = updated.lastRunAttemptTime,
            nextBackoffInterval = updated.nextBackoffInterval,
            globalPriority = updated.globalPriority,
            isRunning = updated.isRunning,
            isMemoryOnly = updated.isMemoryOnly
          )
          jobSpecCache[updatedJobSpec.id] = updatedJobSpec

          if (singleUpdate) {
            return
          }
        }
      }
    }
  }

  /**
   * Heart of a lot of the in-memory job management. Will ensure that we have an up-to-date list of eligible jobs in sorted order.
   */
  private fun placeJobInEligibleList(jobCandidate: MinimalJobSpec) {
    val existingJobInQueue = jobCandidate.queueKey?.let { mostEligibleJobForQueue[it] }
    if (existingJobInQueue != null) {
      if (jobCandidate.globalPriority < existingJobInQueue.globalPriority) {
        return
      }

      if (jobCandidate.globalPriority == existingJobInQueue.globalPriority) {
        if (jobCandidate.queuePriority < existingJobInQueue.queuePriority) {
          return
        }

        if (jobCandidate.queuePriority == existingJobInQueue.queuePriority && jobCandidate.createTime >= existingJobInQueue.createTime) {
          return
        }
      }
    }

    // At this point, we know that the job candidate has a higher global priority, higher queue priority, or their priorities are the same but with an older creation time.
    // That means we know it's now the most eligible job in its queue.

    jobCandidate.queueKey?.let { queueKey ->
      eligibleJobs.removeIf { it.id == existingJobInQueue?.id }
      mostEligibleJobForQueue[queueKey] = jobCandidate
    }

    // At this point, anything queue-related has been handled. We just need to insert this job in the correct spot in the list.
    // Thankfully, we're using a TreeSet, so sorting is automatic.

    eligibleJobs += jobCandidate
  }

  /**
   * Replaces a job in the eligible list with an updated version of the job.
   */
  private fun replaceJobInEligibleList(current: MinimalJobSpec?, updated: MinimalJobSpec?) {
    if (current == null || updated == null) {
      return
    }

    if (updated.queueKey == Job.Parameters.MIGRATION_QUEUE_KEY) {
      migrationJobs.removeIf { it.id == current.id }
      migrationJobs += updated
    } else {
      eligibleJobs.removeIf { current.id == it.id }
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
  private fun MinimalJobSpec.hasEligibleRunTime(currentTime: Long): Boolean {
    return this.lastRunAttemptTime > currentTime || (this.lastRunAttemptTime + this.nextBackoffInterval) < currentTime
  }

  private fun getSingleLayerOfDependencySpecsThatDependOnJob(jobSpecId: String): List<DependencySpec> {
    return dependenciesByJobId
      .values
      .flatten()
      .filter { it.dependsOnJobId == jobSpecId }
  }

  /**
   * Converts a [MinimalJobSpec] to a [JobSpec]. We prefer using the cache, but if it's not found, we'll hit the database.
   * We consider this a "recent access" and will cache it for future use.
   */
  private fun MinimalJobSpec.toJobSpec(): JobSpec? {
    return jobSpecCache.getOrPut(this.id) {
      jobDatabase.getJobSpec(this.id) ?: return null
    }
  }

  private object EligibleMinJobComparator : Comparator<MinimalJobSpec> {
    override fun compare(o1: MinimalJobSpec, o2: MinimalJobSpec): Int {
      // We want to sort by priority descending, then createTime ascending.
      // This is for determining which job to run across multiple queues, so queue priority is not considered.

      // CAUTION: This is used by a TreeSet, so it must be consistent with equals.
      //          If this compare function says two objects are equal, then only one will be allowed in the set!
      //          This is why the last step is to compare the IDs.
      return when {
        o1.globalPriority > o2.globalPriority -> -1
        o1.globalPriority < o2.globalPriority -> 1
        o1.createTime < o2.createTime -> -1
        o1.createTime > o2.createTime -> 1
        else -> o1.id.compareTo(o2.id)
      }
    }
  }

  /**
   * Identical to [EligibleMinJobComparator], but for full jobs.
   */
  private object EligibleFullJobComparator : Comparator<JobSpec> {
    override fun compare(o1: JobSpec, o2: JobSpec): Int {
      return when {
        o1.globalPriority > o2.globalPriority -> -1
        o1.globalPriority < o2.globalPriority -> 1
        o1.createTime < o2.createTime -> -1
        o1.createTime > o2.createTime -> 1
        else -> o1.id.compareTo(o2.id)
      }
    }
  }

  private fun debugStopwatch(label: String): Stopwatch? {
    return if (DEBUG) Stopwatch(label, decimalPlaces = 2) else null
  }
}

/**
 * Converts a [JobSpec] to a [MinimalJobSpec], which is just a matter of trimming off unnecessary properties.
 */
@VisibleForTesting
fun JobSpec.toMinimalJobSpec(): MinimalJobSpec {
  return MinimalJobSpec(
    id = this.id,
    factoryKey = this.factoryKey,
    queueKey = this.queueKey,
    createTime = this.createTime,
    lastRunAttemptTime = this.lastRunAttemptTime,
    nextBackoffInterval = this.nextBackoffInterval,
    globalPriority = this.globalPriority,
    queuePriority = this.queuePriority,
    isRunning = this.isRunning,
    isMemoryOnly = this.isMemoryOnly
  )
}
