package org.thoughtcrime.securesms.jobs

import org.junit.Test
import org.mockito.Mockito
import org.thoughtcrime.securesms.assertIs
import org.thoughtcrime.securesms.database.JobDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.persistence.ConstraintSpec
import org.thoughtcrime.securesms.jobmanager.persistence.DependencySpec
import org.thoughtcrime.securesms.jobmanager.persistence.FullSpec
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec
import org.thoughtcrime.securesms.testutil.TestHelpers
import java.nio.charset.Charset
import java.util.Arrays

class FastJobStorageTest {
  @Test
  fun init_allStoredDataAvailable() {
    val subject = FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS))
    subject.init()

    DataSet1.assertJobsMatch(subject.allJobSpecs)
    DataSet1.assertConstraintsMatch(subject.allConstraintSpecs)
    DataSet1.assertDependenciesMatch(subject.allDependencySpecs)
  }

  @Test
  fun init_removesCircularDependencies() {
    val subject = FastJobStorage(fixedDataDatabase(DataSetCircularDependency.FULL_SPECS))
    subject.init()

    DataSetCircularDependency.assertJobsMatch(subject.allJobSpecs)
    DataSetCircularDependency.assertConstraintsMatch(subject.allConstraintSpecs)
    DataSetCircularDependency.assertDependenciesMatch(subject.allDependencySpecs)
  }

  @Test
  fun insertJobs_writesToDatabase() {
    val database = noopDatabase()
    val subject = FastJobStorage(database)

    subject.insertJobs(DataSet1.FULL_SPECS)

    Mockito.verify(database).insertJobs(DataSet1.FULL_SPECS)
  }

  @Test
  fun insertJobs_memoryOnlyJob_doesNotWriteToDatabase() {
    val database = noopDatabase()
    val subject = FastJobStorage(database)

    subject.insertJobs(DataSetMemory.FULL_SPECS)

    Mockito.verify(database, Mockito.times(0)).insertJobs(DataSet1.FULL_SPECS)
  }

  @Test
  fun insertJobs_dataCanBeFound() {
    val subject = FastJobStorage(noopDatabase())
    subject.insertJobs(DataSet1.FULL_SPECS)
    DataSet1.assertJobsMatch(subject.allJobSpecs)
    DataSet1.assertConstraintsMatch(subject.allConstraintSpecs)
    DataSet1.assertDependenciesMatch(subject.allDependencySpecs)
  }

  @Test
  fun insertJobs_individualJobCanBeFound() {
    val subject = FastJobStorage(noopDatabase())
    subject.insertJobs(DataSet1.FULL_SPECS)

    subject.getJobSpec(DataSet1.JOB_1.id) assertIs DataSet1.JOB_1
    subject.getJobSpec(DataSet1.JOB_2.id) assertIs DataSet1.JOB_2
  }

  @Test
  fun updateAllJobsToBePending_writesToDatabase() {
    val database = noopDatabase()
    val subject = FastJobStorage(database)
    subject.updateAllJobsToBePending()
    Mockito.verify(database).updateAllJobsToBePending()
  }

  @Test
  fun updateAllJobsToBePending_allArePending() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", isRunning = true), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2", isRunning = true), emptyList(), emptyList())

    val subject = FastJobStorage(fixedDataDatabase(Arrays.asList(fullSpec1, fullSpec2)))
    subject.init()
    subject.updateAllJobsToBePending()

    subject.getJobSpec("1")!!.isRunning assertIs false
    subject.getJobSpec("2")!!.isRunning assertIs false
  }

  @Test
  fun updateJobs_writesToDatabase() {
    val database = fixedDataDatabase(DataSet1.FULL_SPECS)
    val jobs = listOf(jobSpec(id = "id1", factoryKey = "f1"))

    val subject = FastJobStorage(database)
    subject.init()
    subject.updateJobs(jobs)

    Mockito.verify(database).updateJobs(jobs)
  }

  @Test
  fun updateJobs_memoryOnly_doesNotWriteToDatabase() {
    val database = fixedDataDatabase(DataSetMemory.FULL_SPECS)
    val jobs = listOf(jobSpec(id = "id1", factoryKey = "f1"))

    val subject = FastJobStorage(database)
    subject.init()
    subject.updateJobs(jobs)

    Mockito.verify(database, Mockito.times(0)).updateJobs(jobs)
  }

  @Test
  fun updateJobs_updatesAllFields() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1"), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2"), emptyList(), emptyList())
    val fullSpec3 = FullSpec(jobSpec(id = "3", factoryKey = "f3"), emptyList(), emptyList())

    val update1 = jobSpec(
      id = "1",
      factoryKey = "g1",
      queueKey = "q1",
      createTime = 2,
      nextRunAttemptTime = 2,
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
      nextRunAttemptTime = 3,
      runAttempt = 3,
      maxAttempts = 3,
      lifespan = 3,
      serializedData = "def".toByteArray(),
      serializedInputData = "ghi".toByteArray(),
      isRunning = true,
      isMemoryOnly = false
    )

    val subject = FastJobStorage(fixedDataDatabase(listOf(fullSpec1, fullSpec2, fullSpec3)))
    subject.init()
    subject.updateJobs(listOf(update1, update2))

    subject.getJobSpec("1") assertIs update1
    subject.getJobSpec("2") assertIs update2
    subject.getJobSpec("3") assertIs fullSpec3.jobSpec
  }

  @Test
  fun updateJobRunningState_writesToDatabase() {
    val database = fixedDataDatabase(DataSet1.FULL_SPECS)

    val subject = FastJobStorage(database)
    subject.init()

    subject.updateJobRunningState(id = "id1", isRunning = true)

    Mockito.verify(database).updateJobRunningState("id1", true)
  }

  @Test
  fun updateJobRunningState_stateUpdated() {
    val subject = FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS))
    subject.init()

    subject.updateJobRunningState(id = DataSet1.JOB_1.id, isRunning = true)
    subject.getJobSpec(DataSet1.JOB_1.id)!!.isRunning assertIs true

    subject.updateJobRunningState(id = DataSet1.JOB_1.id, isRunning = false)
    subject.getJobSpec(DataSet1.JOB_1.id)!!.isRunning assertIs false
  }

  @Test
  fun updateJobAfterRetry_writesToDatabase() {
    val database = fixedDataDatabase(DataSet1.FULL_SPECS)

    val subject = FastJobStorage(database)
    subject.init()

    subject.updateJobAfterRetry(
      id = "id1",
      isRunning = true,
      runAttempt = 1,
      nextRunAttemptTime = 10,
      serializedData = "a".toByteArray()
    )

    Mockito.verify(database).updateJobAfterRetry("id1", true, 1, 10, "a".toByteArray())
  }

  @Test
  fun updateJobAfterRetry_memoryOnly_doesNotWriteToDatabase() {
    val database = fixedDataDatabase(DataSetMemory.FULL_SPECS)

    val subject = FastJobStorage(database)
    subject.init()

    subject.updateJobAfterRetry(
      id = "id1",
      isRunning = true,
      runAttempt = 1,
      nextRunAttemptTime = 10,
      serializedData = "a".toByteArray()
    )

    Mockito.verify(database, Mockito.times(0)).updateJobAfterRetry("id1", true, 1, 10, "a".toByteArray())
  }

  @Test
  fun updateJobAfterRetry_stateUpdated() {
    val fullSpec = FullSpec(jobSpec(id = "1", factoryKey = "f1", isRunning = true), emptyList(), emptyList())

    val subject = FastJobStorage(fixedDataDatabase(listOf(fullSpec)))
    subject.init()

    subject.updateJobAfterRetry("1", false, 1, 10, "a".toByteArray())

    val job = subject.getJobSpec("1")
    check(job != null)
    job.isRunning assertIs false
    job.runAttempt assertIs 1
    job.nextRunAttemptTime assertIs 10
    job.serializedData!!.toString(Charset.defaultCharset()) assertIs "a"
  }

  @Test
  fun getPendingJobsWithNoDependenciesInCreatedOrder_noneWhenEarlierItemInQueueInRunning() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q", isRunning = true), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = "q"), emptyList(), emptyList())

    val subject = FastJobStorage(fixedDataDatabase(listOf(fullSpec1, fullSpec2)))
    subject.init()

    subject.getPendingJobsWithNoDependenciesInCreatedOrder(1).size assertIs 0
  }

  @Test
  fun getPendingJobsWithNoDependenciesInCreatedOrder_noneWhenAllJobsAreRunning() {
    val fullSpec = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q", isRunning = true), emptyList(), emptyList())

    val subject = FastJobStorage(fixedDataDatabase(listOf(fullSpec)))
    subject.init()

    subject.getPendingJobsWithNoDependenciesInCreatedOrder(10).size assertIs 0
  }

  @Test
  fun getPendingJobsWithNoDependenciesInCreatedOrder_noneWhenNextRunTimeIsAfterCurrentTime() {
    val fullSpec = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q", nextRunAttemptTime = 10), emptyList(), emptyList())

    val subject = FastJobStorage(fixedDataDatabase(listOf(fullSpec)))
    subject.init()

    subject.getPendingJobsWithNoDependenciesInCreatedOrder(0).size assertIs 0
  }

  @Test
  fun getPendingJobsWithNoDependenciesInCreatedOrder_noneWhenDependentOnAnotherJob() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", isRunning = true), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2"), emptyList(), listOf(DependencySpec("2", "1", false)))

    val subject = FastJobStorage(fixedDataDatabase(listOf(fullSpec1, fullSpec2)))
    subject.init()

    subject.getPendingJobsWithNoDependenciesInCreatedOrder(0).size assertIs 0
  }

  @Test
  fun getPendingJobsWithNoDependenciesInCreatedOrder_singleEligibleJob() {
    val fullSpec = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q"), emptyList(), emptyList())

    val subject = FastJobStorage(fixedDataDatabase(listOf(fullSpec)))
    subject.init()

    subject.getPendingJobsWithNoDependenciesInCreatedOrder(10).size assertIs 1
  }

  @Test
  fun getPendingJobsWithNoDependenciesInCreatedOrder_multipleEligibleJobs() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1"), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2"), emptyList(), emptyList())

    val subject = FastJobStorage(fixedDataDatabase(listOf(fullSpec1, fullSpec2)))
    subject.init()

    subject.getPendingJobsWithNoDependenciesInCreatedOrder(10).size assertIs 2
  }

  @Test
  fun getPendingJobsWithNoDependenciesInCreatedOrder_singleEligibleJobInMixedList() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", isRunning = true), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2"), emptyList(), emptyList())

    val subject = FastJobStorage(fixedDataDatabase(listOf(fullSpec1, fullSpec2)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 1
    jobs[0].id assertIs "2"
  }

  @Test
  fun getPendingJobsWithNoDependenciesInCreatedOrder_firstItemInQueue() {
    val fullSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q"), emptyList(), emptyList())
    val fullSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = "q"), emptyList(), emptyList())

    val subject = FastJobStorage(fixedDataDatabase(listOf(fullSpec1, fullSpec2)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 1
    jobs[0].id assertIs "1"
  }

  @Test
  fun getPendingJobsWithNoDependenciesInCreatedOrder_migrationJobTakesPrecedence() {
    val plainSpec = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q", createTime = 0), emptyList(), emptyList())
    val migrationSpec = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 5), emptyList(), emptyList())

    val subject = FastJobStorage(fixedDataDatabase(listOf(plainSpec, migrationSpec)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 1
    jobs[0].id assertIs "2"
  }

  @Test
  fun getPendingJobsWithNoDependenciesInCreatedOrder_runningMigrationBlocksNormalJobs() {
    val plainSpec = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = "q", createTime = 0), emptyList(), emptyList())
    val migrationSpec = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 5, isRunning = true), emptyList(), emptyList())

    val subject = FastJobStorage(fixedDataDatabase(listOf(plainSpec, migrationSpec)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 0
  }

  @Test
  fun getPendingJobsWithNoDependenciesInCreatedOrder_runningMigrationBlocksLaterMigrationJobs() {
    val migrationSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 0, isRunning = true), emptyList(), emptyList())
    val migrationSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 5), emptyList(), emptyList())

    val subject = FastJobStorage(fixedDataDatabase(listOf(migrationSpec1, migrationSpec2)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 0
  }

  @Test
  fun getPendingJobsWithNoDependenciesInCreatedOrder_onlyReturnFirstEligibleMigrationJob() {
    val migrationSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 0), emptyList(), emptyList())
    val migrationSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 5), emptyList(), emptyList())

    val subject = FastJobStorage(fixedDataDatabase(listOf(migrationSpec1, migrationSpec2)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 1
    jobs[0].id assertIs "1"
  }

  @Test
  fun getPendingJobsWithNoDependenciesInCreatedOrder_onlyMigrationJobWithAppropriateNextRunTime() {
    val migrationSpec1 = FullSpec(jobSpec(id = "1", factoryKey = "f1", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 0, nextRunAttemptTime = 999), emptyList(), emptyList())
    val migrationSpec2 = FullSpec(jobSpec(id = "2", factoryKey = "f2", queueKey = Job.Parameters.MIGRATION_QUEUE_KEY, createTime = 5, nextRunAttemptTime = 0), emptyList(), emptyList())

    val subject = FastJobStorage(fixedDataDatabase(listOf(migrationSpec1, migrationSpec2)))
    subject.init()

    val jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10)
    jobs.size assertIs 0
  }

  @Test
  fun deleteJobs_writesToDatabase() {
    val database = fixedDataDatabase(DataSet1.FULL_SPECS)
    val ids: List<String> = listOf("id1", "id2")

    val subject = FastJobStorage(database)
    subject.init()

    subject.deleteJobs(ids)

    Mockito.verify(database).deleteJobs(ids)
  }

  @Test
  fun deleteJobs_memoryOnly_doesNotWriteToDatabase() {
    val database = fixedDataDatabase(DataSetMemory.FULL_SPECS)
    val ids = listOf("id1")

    val subject = FastJobStorage(database)
    subject.init()

    subject.deleteJobs(ids)

    Mockito.verify(database, Mockito.times(0)).deleteJobs(ids)
  }

  @Test
  fun deleteJobs_deletesAllRelevantPieces() {
    val subject = FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS))
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
  }

  @Test
  fun getDependencySpecsThatDependOnJob_startOfChain() {
    val subject = FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS))
    subject.init()

    val result = subject.getDependencySpecsThatDependOnJob("id1")
    result.size assertIs 2
    result[0] assertIs DataSet1.DEPENDENCY_2
    result[1] assertIs DataSet1.DEPENDENCY_3
  }

  @Test
  fun getDependencySpecsThatDependOnJob_midChain() {
    val subject = FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS))
    subject.init()

    val result = subject.getDependencySpecsThatDependOnJob("id2")
    result.size assertIs 1
    result[0] assertIs DataSet1.DEPENDENCY_3
  }

  @Test
  fun getDependencySpecsThatDependOnJob_endOfChain() {
    val subject = FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS))
    subject.init()

    val result = subject.getDependencySpecsThatDependOnJob("id3")
    result.size assertIs 0
  }

  @Test
  fun getJobsInQueue_empty() {
    val subject = FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS))
    subject.init()

    val result = subject.getJobsInQueue("x")
    result.size assertIs 0
  }

  @Test
  fun getJobsInQueue_singleJob() {
    val subject = FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS))
    subject.init()

    val result = subject.getJobsInQueue("q1")
    result.size assertIs 1
    result[0].id assertIs "id1"
  }

  @Test
  fun getJobCountForFactory_general() {
    val subject = FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS))
    subject.init()

    subject.getJobCountForFactory("f1") assertIs 1
    subject.getJobCountForFactory("does-not-exist") assertIs 0
  }

  @Test
  fun getJobCountForFactoryAndQueue_general() {
    val subject = FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS))
    subject.init()

    subject.getJobCountForFactoryAndQueue("f1", "q1") assertIs 1
    subject.getJobCountForFactoryAndQueue("f2", "q1") assertIs 0
    subject.getJobCountForFactoryAndQueue("f1", "does-not-exist") assertIs 0
  }

  @Test
  fun areQueuesEmpty_allNonEmpty() {
    val subject = FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS))
    subject.init()

    subject.areQueuesEmpty(TestHelpers.setOf("q1")) assertIs false
    subject.areQueuesEmpty(TestHelpers.setOf("q1", "q2")) assertIs false
  }

  @Test
  fun areQueuesEmpty_mixedEmpty() {
    val subject = FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS))
    subject.init()

    subject.areQueuesEmpty(TestHelpers.setOf("q1", "q5")) assertIs false
  }

  @Test
  fun areQueuesEmpty_queueDoesNotExist() {
    val subject = FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS))
    subject.init()

    subject.areQueuesEmpty(TestHelpers.setOf("q4")) assertIs true
    subject.areQueuesEmpty(TestHelpers.setOf("q4", "q5")) assertIs true
  }

  private fun noopDatabase(): JobDatabase {
    val database = Mockito.mock(JobDatabase::class.java)
    Mockito.`when`(database.getAllJobSpecs()).thenReturn(emptyList())
    Mockito.`when`(database.getAllConstraintSpecs()).thenReturn(emptyList())
    Mockito.`when`(database.getAllDependencySpecs()).thenReturn(emptyList())
    return database
  }

  private fun fixedDataDatabase(fullSpecs: List<FullSpec>): JobDatabase {
    val database = Mockito.mock(JobDatabase::class.java)
    Mockito.`when`(database.getAllJobSpecs()).thenReturn(fullSpecs.map { it.jobSpec })
    Mockito.`when`(database.getAllConstraintSpecs()).thenReturn(fullSpecs.map { it.constraintSpecs }.flatten())
    Mockito.`when`(database.getAllDependencySpecs()).thenReturn(fullSpecs.map { it.dependencySpecs }.flatten())
    return database
  }

  private fun jobSpec(
    id: String,
    factoryKey: String,
    queueKey: String? = null,
    createTime: Long = 1,
    nextRunAttemptTime: Long = 1,
    runAttempt: Int = 1,
    maxAttempts: Int = 1,
    lifespan: Long = 1,
    serializedData: ByteArray? = null,
    serializedInputData: ByteArray? = null,
    isRunning: Boolean = false,
    isMemoryOnly: Boolean = false
  ): JobSpec {
    return JobSpec(
      id = id,
      factoryKey = factoryKey,
      queueKey = queueKey,
      createTime = createTime,
      nextRunAttemptTime = nextRunAttemptTime,
      runAttempt = runAttempt,
      maxAttempts = maxAttempts,
      lifespan = lifespan,
      serializedData = serializedData,
      serializedInputData = serializedInputData,
      isRunning = isRunning,
      isMemoryOnly = isMemoryOnly
    )
  }

  private object DataSet1 {
    val JOB_1 = JobSpec(
      id = "id1",
      factoryKey = "f1",
      queueKey = "q1",
      createTime = 1,
      nextRunAttemptTime = 2,
      runAttempt = 3,
      maxAttempts = 4,
      lifespan = 5,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = false
    )
    val JOB_2 = JobSpec(
      id = "id2",
      factoryKey = "f2",
      queueKey = "q2",
      createTime = 1,
      nextRunAttemptTime = 2,
      runAttempt = 3,
      maxAttempts = 4,
      lifespan = 5,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = false
    )
    val JOB_3 = JobSpec(
      id = "id3",
      factoryKey = "f3",
      queueKey = "q3",
      createTime = 1,
      nextRunAttemptTime = 2,
      runAttempt = 3,
      maxAttempts = 4,
      lifespan = 5,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = false
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
      nextRunAttemptTime = 2,
      runAttempt = 3,
      maxAttempts = 4,
      lifespan = 5,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = true
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
      nextRunAttemptTime = 2,
      runAttempt = 3,
      maxAttempts = 4,
      lifespan = 5,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = false
    )
    val JOB_2 = JobSpec(
      id = "id2",
      factoryKey = "f2",
      queueKey = "q1",
      createTime = 2,
      nextRunAttemptTime = 2,
      runAttempt = 3,
      maxAttempts = 4,
      lifespan = 5,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = false
    )
    val JOB_3 = JobSpec(
      id = "id3",
      factoryKey = "f3",
      queueKey = "q3",
      createTime = 3,
      nextRunAttemptTime = 2,
      runAttempt = 3,
      maxAttempts = 4,
      lifespan = 5,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = false
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
