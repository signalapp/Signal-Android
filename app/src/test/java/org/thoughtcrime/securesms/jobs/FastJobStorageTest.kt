package org.thoughtcrime.securesms.jobs

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.thoughtcrime.securesms.assertIs
import org.thoughtcrime.securesms.database.JobDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.persistence.ConstraintSpec
import org.thoughtcrime.securesms.jobmanager.persistence.DependencySpec
import org.thoughtcrime.securesms.jobmanager.persistence.FullSpec
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec
import org.thoughtcrime.securesms.testutil.TestHelpers
import java.nio.charset.Charset

class FastJobStorageTest {
  @Test
  fun `init - all stored data available`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    DataSet1.assertJobsMatch(subject.allJobSpecs)
    DataSet1.assertConstraintsMatch(subject.allConstraintSpecs)
    DataSet1.assertDependenciesMatch(subject.allDependencySpecs)
  }

  @Test
  fun `init - removes circular dependencies`() {
    val subject = FastJobStorage(mockDatabase(DataSetCircularDependency.FULL_SPECS))
    subject.init()

    DataSetCircularDependency.assertJobsMatch(subject.allJobSpecs)
    DataSetCircularDependency.assertConstraintsMatch(subject.allConstraintSpecs)
    DataSetCircularDependency.assertDependenciesMatch(subject.allDependencySpecs)
  }

  @Test
  fun `insertJobs - writes to database`() {
    val database = mockDatabase()
    val subject = FastJobStorage(database)

    subject.insertJobs(DataSet1.FULL_SPECS)

    verify { database.insertJobs(DataSet1.FULL_SPECS) }
  }

  @Test
  fun `insertJobs - memory-only job does not write to database`() {
    val database = mockDatabase()
    val subject = FastJobStorage(database)

    subject.insertJobs(DataSetMemory.FULL_SPECS)

    verify(exactly = 0) { database.insertJobs(DataSet1.FULL_SPECS) }
  }

  @Test
  fun `insertJobs - data can be found`() {
    val subject = FastJobStorage(mockDatabase())
    subject.insertJobs(DataSet1.FULL_SPECS)
    DataSet1.assertJobsMatch(subject.allJobSpecs)
    DataSet1.assertConstraintsMatch(subject.allConstraintSpecs)
    DataSet1.assertDependenciesMatch(subject.allDependencySpecs)
  }

  @Test
  fun `insertJobs - individual job can be found`() {
    val subject = FastJobStorage(mockDatabase())
    subject.insertJobs(DataSet1.FULL_SPECS)

    subject.getJobSpec(DataSet1.JOB_1.id) assertIs DataSet1.JOB_1
    subject.getJobSpec(DataSet1.JOB_2.id) assertIs DataSet1.JOB_2
  }

  @Test
  fun `updateAllJobsToBePending - writes to database`() {
    val database = mockDatabase()
    val subject = FastJobStorage(database)
    subject.updateAllJobsToBePending()
    verify { database.updateAllJobsToBePending() }
  }

  @Test
  fun `updateAllJobsToBePending - all are pending`() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", isRunning = true), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2", isRunning = true), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(fullSpec1, fullSpec2)))
    subject.init()
    subject.updateAllJobsToBePending()

    subject.getJobSpec("1")!!.isRunning assertIs false
    subject.getJobSpec("2")!!.isRunning assertIs false
  }

  @Test
  fun `updateJobs - writes to database`() {
    val database = mockDatabase(DataSet1.FULL_SPECS)
    val jobs = listOf(jobSpec(id = "id1", factoryKey = "f1"))

    val subject = FastJobStorage(database)
    subject.init()
    subject.updateJobs(jobs)

    verify { database.updateJobs(jobs) }
  }

  @Test
  fun `updateJobs - memory-only job does not write to database`() {
    val database = mockDatabase(DataSetMemory.FULL_SPECS)
    val jobs = listOf(jobSpec(id = "id1", factoryKey = "f1"))

    val subject = FastJobStorage(database)
    subject.init()
    subject.updateJobs(jobs)

    verify(exactly = 0) { database.updateJobs(jobs) }
  }

  @Test
  fun `updateJobs - updates all fields`() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1"), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2"), emptyList(), emptyList())
    val fullSpec3 = FullSpec(jobSpec(id = "3", factoryKey = "f3"), emptyList(), emptyList())

    val update1 = jobSpec(
      id = "1",
      factoryKey = "g1",
      queueKey = "q1",
      createTime = 2,
      lastRunAttemptTime = 2,
      nextBackoffInterval = 2,
      runAttempt = 2,
      maxAttempts = 2,
      lifespan = 2,
      serializedData = "abc".toByteArray(),
      serializedInputData = null,
      isRunning = true,
      isMemoryOnly = false
    )
    val update2 = jobSpec(
      id = "2",
      factoryKey = "g2",
      queueKey = "q2",
      createTime = 3,
      lastRunAttemptTime = 3,
      nextBackoffInterval = 3,
      runAttempt = 3,
      maxAttempts = 3,
      lifespan = 3,
      serializedData = "def".toByteArray(),
      serializedInputData = "ghi".toByteArray(),
      isRunning = true,
      isMemoryOnly = false
    )

    val subject = FastJobStorage(mockDatabase(listOf(fullSpec1, fullSpec2, fullSpec3)))
    subject.init()
    subject.updateJobs(listOf(update1, update2))

    subject.getJobSpec("1") assertIs update1
    subject.getJobSpec("2") assertIs update2
    subject.getJobSpec("3") assertIs fullSpec3.jobSpec
  }

  @Test
  fun `markJobAsRunning - writes to database`() {
    val database = mockDatabase(DataSet1.FULL_SPECS)

    val subject = FastJobStorage(database)
    subject.init()

    subject.markJobAsRunning(id = "id1", currentTime = 42)

    verify { database.markJobAsRunning(id = "id1", currentTime = 42) }
  }

  @Test
  fun `markJobAsRunning - state updated`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    subject.markJobAsRunning(id = DataSet1.JOB_1.id, currentTime = 42)

    subject.getJobSpec(DataSet1.JOB_1.id)!!.isRunning assertIs true
    subject.getJobSpec(DataSet1.JOB_1.id)!!.lastRunAttemptTime assertIs 42
  }

  @Test
  fun `updateJobAfterRetry - writes to database`() {
    val database = mockDatabase(DataSet1.FULL_SPECS)

    val subject = FastJobStorage(database)
    subject.init()

    subject.updateJobAfterRetry(
      id = "id1",
      currentTime = 0,
      runAttempt = 1,
      nextBackoffInterval = 10,
      serializedData = "a".toByteArray()
    )

    verify { database.updateJobAfterRetry(id = "id1", currentTime = 0, runAttempt = 1, nextBackoffInterval = 10, serializedData = "a".toByteArray()) }
  }

  @Test
  fun `updateJobAfterRetry - memory-only job does not write to database`() {
    val database = mockDatabase(DataSetMemory.FULL_SPECS)

    val subject = FastJobStorage(database)
    subject.init()

    subject.updateJobAfterRetry(
      id = "id1",
      currentTime = 0,
      runAttempt = 1,
      nextBackoffInterval = 10,
      serializedData = "a".toByteArray()
    )

    verify(exactly = 0) { database.updateJobAfterRetry(id = "id1", currentTime = 0, runAttempt = 1, nextBackoffInterval = 10, serializedData = "a".toByteArray()) }
  }

  @Test
  fun `updateJobAfterRetry - state updated`() {
    val fullSpec = FullSpec(jobSpec(id = "1", factoryKey = "f1", isRunning = true), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(fullSpec)))
    subject.init()

    subject.updateJobAfterRetry(
      id = "1",
      currentTime = 3,
      runAttempt = 1,
      nextBackoffInterval = 10,
      serializedData = "a".toByteArray()
    )

    val job = subject.getJobSpec("1")
    check(job != null)
    job.isRunning assertIs false
    job.lastRunAttemptTime assertIs 3
    job.runAttempt assertIs 1
    job.nextBackoffInterval assertIs 10
    job.serializedData!!.toString(Charset.defaultCharset()) assertIs "a"
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - none when earlier item in queue is running`() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q", isRunning = true), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = "q"), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(fullSpec1, fullSpec2)))
    subject.init()

    subject.getPendingJobsWithNoDependenciesInCreatedOrder(1).size assertIs 0
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - none when all jobs are running`() {
    val fullSpec = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q", isRunning = true), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(fullSpec)))
    subject.init()

    subject.getPendingJobsWithNoDependenciesInCreatedOrder(10).size assertIs 0
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - none when next run time is after current time`() {
    val currentTime = 0L
    val fullSpec = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q", lastRunAttemptTime = 0, nextBackoffInterval = 10), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(fullSpec)))
    subject.init()

    subject.getPendingJobsWithNoDependenciesInCreatedOrder(currentTime).size assertIs 0
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - none when dependent on another job`() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", isRunning = true), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2"), emptyList(), listOf(DependencySpec("2", "1", false)))

    val subject = FastJobStorage(mockDatabase(listOf(fullSpec1, fullSpec2)))
    subject.init()

    subject.getPendingJobsWithNoDependenciesInCreatedOrder(0).size assertIs 0
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - single eligible job`() {
    val fullSpec = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q"), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(fullSpec)))
    subject.init()

    subject.getPendingJobsWithNoDependenciesInCreatedOrder(10).size assertIs 1
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - multiple eligible jobs`() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1"), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2"), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(fullSpec1, fullSpec2)))
    subject.init()

    subject.getPendingJobsWithNoDependenciesInCreatedOrder(10).size assertIs 2
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - single eligible job in mixed list`() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", isRunning = true), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2"), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(fullSpec1, fullSpec2)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 1
    jobs[0].id assertIs "2"
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - first item in queue`() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q"), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = "q"), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(fullSpec1, fullSpec2)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 1
    jobs[0].id assertIs "1"
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - first item in queue with priority`() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q", createTime = 1, priority = Job.Parameters.PRIORITY_LOW), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = "q", createTime = 2, priority = Job.Parameters.PRIORITY_HIGH), emptyList(), emptyList())
    val fullSpec3 = FullSpec(jobSpec(id = "3", factoryKey = "f3", queueKey = "q", createTime = 3, priority = Job.Parameters.PRIORITY_DEFAULT), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(fullSpec1, fullSpec2, fullSpec3)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 1
    jobs[0].id assertIs "2"
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - complex priority`() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q1", createTime = 1, priority = Job.Parameters.PRIORITY_LOW), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = "q1", createTime = 2, priority = Job.Parameters.PRIORITY_HIGH), emptyList(), emptyList())
    val fullSpec3 = FullSpec(jobSpec(id = "3", factoryKey = "f3", queueKey = "q2", createTime = 3, priority = Job.Parameters.PRIORITY_DEFAULT), emptyList(), emptyList())
    val fullSpec4 = FullSpec(jobSpec(id = "4", factoryKey = "f4", queueKey = "q2", createTime = 4, priority = Job.Parameters.PRIORITY_LOW), emptyList(), emptyList())
    val fullSpec5 = FullSpec(jobSpec(id = "5", factoryKey = "f5", queueKey = "q3", createTime = 5, priority = Job.Parameters.PRIORITY_DEFAULT), emptyList(), emptyList())
    val fullSpec6 = FullSpec(jobSpec(id = "6", factoryKey = "f6", queueKey = "q3", createTime = 6, priority = Job.Parameters.PRIORITY_HIGH), emptyList(), emptyList())
    val fullSpec7 = FullSpec(jobSpec(id = "7", factoryKey = "f7", queueKey = "q4", createTime = 7, priority = Job.Parameters.PRIORITY_LOW), emptyList(), emptyList())
    val fullSpec8 = FullSpec(jobSpec(id = "8", factoryKey = "f8", queueKey = null, createTime = 8, priority = Job.Parameters.PRIORITY_LOW), emptyList(), emptyList())
    val fullSpec9 = FullSpec(jobSpec(id = "9", factoryKey = "f9", queueKey = null, createTime = 9, priority = Job.Parameters.PRIORITY_DEFAULT), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(fullSpec1, fullSpec2, fullSpec3, fullSpec4, fullSpec5, fullSpec6, fullSpec7, fullSpec8, fullSpec9)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 6
    jobs[0].id assertIs "2"
    jobs[1].id assertIs "6"
    jobs[2].id assertIs "3"
    jobs[3].id assertIs "9"
    jobs[4].id assertIs "7"
    jobs[5].id assertIs "8"
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - lastRunAttemptTime in the future runs right away`() {
    val currentTime = 10L

    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q", lastRunAttemptTime = 100, nextBackoffInterval = 5), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(fullSpec1)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(currentTime)
    jobs.size assertIs 1
    jobs[0].id assertIs "1"
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - migration job takes precedence`() {
    val plainSpec = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q", createTime = 0), emptyList(), emptyList())
    val migrationSpec = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 5), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(plainSpec, migrationSpec)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 1
    jobs[0].id assertIs "2"
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - running migration blocks normal jobs`() {
    val plainSpec = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q", createTime = 0), emptyList(), emptyList())
    val migrationSpec = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 5, isRunning = true), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(plainSpec, migrationSpec)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 0
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - running migration blocks later migration jobs`() {
    val migrationSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 0, isRunning = true), emptyList(), emptyList())
    val migrationSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 5), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(migrationSpec1, migrationSpec2)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 0
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - only return first eligible migration job`() {
    val migrationSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 0), emptyList(), emptyList())
    val migrationSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 5), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(migrationSpec1, migrationSpec2)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 1
    jobs[0].id assertIs "1"
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - migration job that isn't scheduled to run yet blocks later migration jobs`() {
    val currentTime = 10L

    val migrationSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 0, lastRunAttemptTime = 0, nextBackoffInterval = 999), emptyList(), emptyList())
    val migrationSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 5, lastRunAttemptTime = 0, nextBackoffInterval = 0), emptyList(), emptyList())

    val subject = FastJobStorage(mockDatabase(listOf(migrationSpec1, migrationSpec2)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(currentTime)
    jobs.size assertIs 0
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - after deleted, no longer is in eligible list`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    var jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs true

    subject.deleteJobs(listOf("id1"))

    jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs false
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - after marked running, no longer is in eligible list`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    var jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs true

    subject.markJobAsRunning("id1", 1)

    jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs false
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - after updateJobAfterRetry to be invalid, no longer is in eligible list`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    var jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs true

    subject.updateJobAfterRetry("id1", 1, 1000, 1_000_000, null)

    jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs false
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - after invalid then marked pending, is in eligible list`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    subject.markJobAsRunning("id1", 1)
    var jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs false

    subject.updateAllJobsToBePending()

    jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.filter { it.id == DataSet1.JOB_1.id }.size assertIs 1 // The last run attempt time changes, so some fields will be different
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - after updateJobs to be invalid, no longer is in eligible list`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    var jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs true

    subject.updateJobs(listOf(DataSet1.JOB_1.copy(isRunning = true)))

    jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs false
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - newly-inserted higher-priority job in queue replaces old`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    var jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs true

    val higherPriorityJob = DataSet1.JOB_1.copy(id = "id-bigboi", priority = Job.Parameters.PRIORITY_HIGH)
    subject.insertJobs(listOf(FullSpec(jobSpec = higherPriorityJob, constraintSpecs = emptyList(), dependencySpecs = emptyList())))

    jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs false
    jobs.contains(higherPriorityJob) assertIs true
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - updating job to have a higher priority replaces lower priority in queue`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    val lowerPriorityJob = DataSet1.JOB_1.copy(id = "id-bigboi", priority = Job.Parameters.PRIORITY_LOW)
    subject.insertJobs(listOf(FullSpec(jobSpec = lowerPriorityJob, constraintSpecs = emptyList(), dependencySpecs = emptyList())))

    var jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs true
    jobs.contains(lowerPriorityJob) assertIs false

    val higherPriorityJob = lowerPriorityJob.copy(priority = Job.Parameters.PRIORITY_HIGH)
    subject.updateJobs(listOf(higherPriorityJob))

    jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs false
    jobs.contains(higherPriorityJob) assertIs true
  }

  @Test
  fun `getPendingJobsWithNoDependenciesInCreatedOrder - updating job to have an older createTime replaces newer in queue`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    val newerJob = DataSet1.JOB_1.copy(id = "id-bigboi", createTime = 1000)
    subject.insertJobs(listOf(FullSpec(jobSpec = newerJob, constraintSpecs = emptyList(), dependencySpecs = emptyList())))

    var jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs true
    jobs.contains(newerJob) assertIs false

    val olderJob = newerJob.copy(createTime = 0)
    subject.updateJobs(listOf(olderJob))

    jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(100)
    jobs.contains(DataSet1.JOB_1) assertIs false
    jobs.contains(olderJob) assertIs true
  }

  @Test
  fun `deleteJobs - writes to database`() {
    val database = mockDatabase(DataSet1.FULL_SPECS)
    val ids: List<String> = listOf("id1", "id2")

    val subject = FastJobStorage(database)
    subject.init()

    subject.deleteJobs(ids)

    verify { database.deleteJobs(ids) }
  }

  @Test
  fun `deleteJobs - memory-only job does not write to database`() {
    val database = mockDatabase(DataSetMemory.FULL_SPECS)
    val ids = listOf("id1")

    val subject = FastJobStorage(database)
    subject.init()

    subject.deleteJobs(ids)

    verify(exactly = 0) { database.deleteJobs(ids) }
  }

  @Test
  fun `deleteJobs - deletes all relevant pieces`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    subject.deleteJobs(listOf("id1"))

    val jobs = subject.allJobSpecs
    val constraints = subject.allConstraintSpecs
    val dependencies = subject.allDependencySpecs

    jobs.size assertIs 2
    jobs[0] assertIs DataSet1.JOB_2
    jobs[1] assertIs DataSet1.JOB_3
    constraints.size assertIs 1
    constraints[0] assertIs DataSet1.CONSTRAINT_2
    dependencies.size assertIs 1
    subject.getJobSpec("id1") assertIs null
  }

  @Test
  fun `getDependencySpecsThatDependOnJob - start of chain`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    val result = subject.getDependencySpecsThatDependOnJob("id1")
    result.size assertIs 2
    result[0] assertIs DataSet1.DEPENDENCY_2
    result[1] assertIs DataSet1.DEPENDENCY_3
  }

  @Test
  fun `getDependencySpecsThatDependOnJob - mid-chain`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    val result = subject.getDependencySpecsThatDependOnJob("id2")
    result.size assertIs 1
    result[0] assertIs DataSet1.DEPENDENCY_3
  }

  @Test
  fun `getDependencySpecsThatDependOnJob - end of chain`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    val result = subject.getDependencySpecsThatDependOnJob("id3")
    result.size assertIs 0
  }

  @Test
  fun `getJobsInQueue - empty`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    val result = subject.getJobsInQueue("x")
    result.size assertIs 0
  }

  @Test
  fun `getJobsInQueue - single job`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    val result = subject.getJobsInQueue("q1")
    result.size assertIs 1
    result[0].id assertIs "id1"
  }

  @Test
  fun `getJobCountForFactory - general`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    subject.getJobCountForFactory("f1") assertIs 1
    subject.getJobCountForFactory("does-not-exist") assertIs 0
  }

  @Test
  fun `getJobCountForFactoryAndQueue - general`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    subject.getJobCountForFactoryAndQueue("f1", "q1") assertIs 1
    subject.getJobCountForFactoryAndQueue("f2", "q1") assertIs 0
    subject.getJobCountForFactoryAndQueue("f1", "does-not-exist") assertIs 0
  }

  @Test
  fun `areQueuesEmpty - all non-empty`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    subject.areQueuesEmpty(TestHelpers.setOf("q1")) assertIs false
    subject.areQueuesEmpty(TestHelpers.setOf("q1", "q2")) assertIs false
  }

  @Test
  fun `areQueuesEmpty - mixed empty`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    subject.areQueuesEmpty(TestHelpers.setOf("q1", "q5")) assertIs false
  }

  @Test
  fun `areQueuesEmpty - queue does not exist`() {
    val subject = FastJobStorage(mockDatabase(DataSet1.FULL_SPECS))
    subject.init()

    subject.areQueuesEmpty(TestHelpers.setOf("q4")) assertIs true
    subject.areQueuesEmpty(TestHelpers.setOf("q4", "q5")) assertIs true
  }

  private fun mockDatabase(fullSpecs: List<FullSpec> = emptyList()): JobDatabase {
    val jobs = fullSpecs.map { it.jobSpec }.toMutableList()
    val constraints = fullSpecs.map { it.constraintSpecs }.flatten().toMutableList()
    val dependencies = fullSpecs.map { it.dependencySpecs }.flatten().toMutableList()

    val mock = mockk<JobDatabase>(relaxed = true)
    every { mock.getAllJobSpecs() } returns jobs
    every { mock.getAllMinimalJobSpecs() } returns jobs.map { it.toMinimalJobSpec() }
    every { mock.getOldestJobSpecs(any()) } answers { jobs.sortedBy { it.createTime }.take(firstArg()) }
    every { mock.getAllConstraintSpecs() } returns constraints
    every { mock.getAllDependencySpecs() } returns dependencies
    every { mock.getJobSpec(any()) } answers { jobs.first { it.id == firstArg() } }
    every { mock.insertJobs(any()) } answers {
      val inserts: List<FullSpec> = firstArg()
      for (insert in inserts) {
        jobs += insert.jobSpec
        constraints += insert.constraintSpecs
        dependencies += insert.dependencySpecs
      }
    }
    every { mock.deleteJobs(any()) } answers {
      val ids: List<String> = firstArg()
      jobs.removeIf { ids.contains(it.id) }
      constraints.removeIf { ids.contains(it.jobSpecId) }
      dependencies.removeIf { ids.contains(it.jobId) || ids.contains(it.dependsOnJobId) }
    }
    every { mock.updateJobs(any()) } answers {
      val updates: List<JobSpec> = firstArg()
      for (update in updates) {
        jobs.removeIf { it.id == update.id }
        jobs += update
      }
    }
    every { mock.updateAllJobsToBePending() } answers {
      val iterator = jobs.listIterator()
      while (iterator.hasNext()) {
        val job = iterator.next()
        iterator.set(job.copy(isRunning = false))
      }
    }
    every { mock.updateJobAfterRetry(any(), any(), any(), any(), any()) } answers {
      val id = args[0] as String
      val currentTime = args[1] as Long
      val runAttempt = args[2] as Int
      val nextBackoffInterval = args[3] as Long
      val serializedData = args[4] as ByteArray?

      val iterator = jobs.listIterator()
      while (iterator.hasNext()) {
        val job = iterator.next()
        if (job.id == id) {
          iterator.set(
            job.copy(
              isRunning = false,
              runAttempt = runAttempt,
              lastRunAttemptTime = currentTime,
              nextBackoffInterval = nextBackoffInterval,
              serializedData = serializedData
            )
          )
        }
      }
    }

    return mock
  }

  private fun jobSpec(
    id: String,
    factoryKey: String,
    queueKey: String? = null,
    createTime: Long = 1,
    lastRunAttemptTime: Long = 1,
    nextBackoffInterval: Long = 0,
    runAttempt: Int = 1,
    maxAttempts: Int = 1,
    lifespan: Long = 1,
    serializedData: ByteArray? = null,
    serializedInputData: ByteArray? = null,
    isRunning: Boolean = false,
    isMemoryOnly: Boolean = false,
    priority: Int = 0
  ): JobSpec {
    return JobSpec(
      id = id,
      factoryKey = factoryKey,
      queueKey = queueKey,
      createTime = createTime,
      lastRunAttemptTime = lastRunAttemptTime,
      nextBackoffInterval = nextBackoffInterval,
      runAttempt = runAttempt,
      maxAttempts = maxAttempts,
      lifespan = lifespan,
      serializedData = serializedData,
      serializedInputData = serializedInputData,
      isRunning = isRunning,
      isMemoryOnly = isMemoryOnly,
      priority = priority
    )
  }

  private object DataSet1 {
    val JOB_1 = JobSpec(
      id = "id1",
      factoryKey = "f1",
      queueKey = "q1",
      createTime = 1,
      lastRunAttemptTime = 2,
      nextBackoffInterval = 0,
      runAttempt = 3,
      maxAttempts = 4,
      lifespan = 5,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = false,
      priority = 0
    )
    val JOB_2 = JobSpec(
      id = "id2",
      factoryKey = "f2",
      queueKey = "q2",
      createTime = 1,
      lastRunAttemptTime = 2,
      nextBackoffInterval = 0,
      runAttempt = 3,
      maxAttempts = 4,
      lifespan = 5,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = false,
      priority = 0
    )
    val JOB_3 = JobSpec(
      id = "id3",
      factoryKey = "f3",
      queueKey = "q3",
      createTime = 1,
      lastRunAttemptTime = 2,
      nextBackoffInterval = 0,
      runAttempt = 3,
      maxAttempts = 4,
      lifespan = 5,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = false,
      priority = 0
    )

    val CONSTRAINT_1 = ConstraintSpec(jobSpecId = "id1", factoryKey = "f1", isMemoryOnly = false)
    val CONSTRAINT_2 = ConstraintSpec(jobSpecId = "id2", factoryKey = "f2", isMemoryOnly = false)

    val DEPENDENCY_2 = DependencySpec(jobId = "id2", dependsOnJobId = "id1", isMemoryOnly = false)
    val DEPENDENCY_3 = DependencySpec(jobId = "id3", dependsOnJobId = "id2", isMemoryOnly = false)

    val FULL_SPEC_1 = FullSpec(JOB_1, listOf(CONSTRAINT_1), emptyList())
    val FULL_SPEC_2 = FullSpec(JOB_2, listOf(CONSTRAINT_2), listOf(DEPENDENCY_2))
    val FULL_SPEC_3 = FullSpec(JOB_3, emptyList(), listOf(DEPENDENCY_3))
    val FULL_SPECS = listOf(FULL_SPEC_1, FULL_SPEC_2, FULL_SPEC_3)
    fun assertJobsMatch(jobs: List<JobSpec?>) {
      jobs.size assertIs 3
      jobs.contains(JOB_1) assertIs true
      jobs.contains(JOB_2) assertIs true
      jobs.contains(JOB_3) assertIs true
    }

    fun assertConstraintsMatch(constraints: List<ConstraintSpec?>) {
      constraints.size assertIs 2
      constraints.contains(CONSTRAINT_1) assertIs true
      constraints.contains(CONSTRAINT_2) assertIs true
    }

    fun assertDependenciesMatch(dependencies: List<DependencySpec?>) {
      dependencies.size assertIs 2
      dependencies.contains(DEPENDENCY_2) assertIs true
      dependencies.contains(DEPENDENCY_3) assertIs true
    }
  }

  private object DataSetMemory {
    val JOB_1 = JobSpec(
      id = "id1",
      factoryKey = "f1",
      queueKey = "q1",
      createTime = 1,
      lastRunAttemptTime = 2,
      nextBackoffInterval = 0,
      runAttempt = 3,
      maxAttempts = 4,
      lifespan = 5,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = true,
      priority = 0
    )
    val CONSTRAINT_1 = ConstraintSpec(jobSpecId = "id1", factoryKey = "f1", isMemoryOnly = true)
    val FULL_SPEC_1 = FullSpec(JOB_1, listOf(CONSTRAINT_1), emptyList())
    val FULL_SPECS = listOf(FULL_SPEC_1)
  }

  private object DataSetCircularDependency {
    val JOB_1 = JobSpec(
      id = "id1",
      factoryKey = "f1",
      queueKey = "q1",
      createTime = 1,
      lastRunAttemptTime = 2,
      nextBackoffInterval = 0,
      runAttempt = 3,
      maxAttempts = 4,
      lifespan = 5,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = false,
      priority = 0
    )
    val JOB_2 = JobSpec(
      id = "id2",
      factoryKey = "f2",
      queueKey = "q1",
      createTime = 2,
      lastRunAttemptTime = 2,
      nextBackoffInterval = 0,
      runAttempt = 3,
      maxAttempts = 4,
      lifespan = 5,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = false,
      priority = 0
    )
    val JOB_3 = JobSpec(
      id = "id3",
      factoryKey = "f3",
      queueKey = "q3",
      createTime = 3,
      lastRunAttemptTime = 2,
      nextBackoffInterval = 0,
      runAttempt = 3,
      maxAttempts = 4,
      lifespan = 5,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = false,
      priority = 0
    )

    val DEPENDENCY_1 = DependencySpec(jobId = "id1", dependsOnJobId = "id2", isMemoryOnly = false)
    val DEPENDENCY_3 = DependencySpec(jobId = "id3", dependsOnJobId = "id2", isMemoryOnly = false)

    val FULL_SPEC_1 = FullSpec(jobSpec = JOB_1, constraintSpecs = emptyList(), dependencySpecs = listOf(DEPENDENCY_1))
    val FULL_SPEC_2 = FullSpec(jobSpec = JOB_2, constraintSpecs = emptyList(), dependencySpecs = emptyList())
    val FULL_SPEC_3 = FullSpec(jobSpec = JOB_3, constraintSpecs = emptyList(), dependencySpecs = listOf(DEPENDENCY_3))
    val FULL_SPECS = listOf(FULL_SPEC_1, FULL_SPEC_2, FULL_SPEC_3)

    fun assertJobsMatch(jobs: List<JobSpec?>) {
      jobs.size assertIs 3
      jobs.contains(JOB_1) assertIs true
      jobs.contains(JOB_2) assertIs true
      jobs.contains(JOB_3) assertIs true
    }

    fun assertConstraintsMatch(constraints: List<ConstraintSpec?>) {
      constraints.size assertIs 0
    }

    fun assertDependenciesMatch(dependencies: List<DependencySpec?>) {
      dependencies.size assertIs 1
      dependencies.contains(DEPENDENCY_1) assertIs false
      dependencies.contains(DEPENDENCY_3) assertIs true
    }
  }
}
