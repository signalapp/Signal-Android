package org.thoughtcrime.securesms.jobmanager

import android.app.Application
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.thoughtcrime.securesms.jobmanager.persistence.DependencySpec
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec
import org.thoughtcrime.securesms.jobmanager.persistence.JobStorage
import org.thoughtcrime.securesms.util.Debouncer
import kotlin.time.Duration.Companion.seconds

@Ignore("When running tests in bulk, this causes the JVM to OOM, I think because we're creating lots of threads that don't get cleaned up, and I haven't figured out a nice way to fix it yet.")
class JobControllerTest {

  @MockK
  private lateinit var application: Application

  @MockK
  private lateinit var jobStorage: JobStorage

  @MockK
  private lateinit var jobInstantiator: JobInstantiator

  @MockK
  private lateinit var constraintInstantiator: ConstraintInstantiator

  @MockK
  private lateinit var jobTracker: JobTracker

  @MockK
  private lateinit var scheduler: Scheduler

  @MockK
  private lateinit var debouncer: Debouncer

  @MockK
  private lateinit var callback: JobController.Callback

  private lateinit var jobController: JobController

  companion object {
    private const val MIN_RUNNERS = 2
    private const val MAX_RUNNERS = 5
  }

  @Before
  fun setup() {
    MockKAnnotations.init(this, relaxed = true)

    // Mock default behavior
    every { jobStorage.updateAllJobsToBePending() } returns Unit
    every { debouncer.publish(any()) } returns Unit

    jobController = JobController(
      application,
      jobStorage,
      jobInstantiator,
      constraintInstantiator,
      jobTracker,
      scheduler,
      debouncer,
      callback,
      MIN_RUNNERS,
      MAX_RUNNERS,
      1.seconds.inWholeMilliseconds,
      emptyList()
    )
  }

  @Test
  fun `init updates all jobs to pending`() {
    // When
    jobController.init()

    // Then
    verify { jobStorage.updateAllJobsToBePending() }
  }

  @Test
  fun `submitNewJobChain inserts jobs and schedules them`() {
    // Given
    val testJob = createTestJob("test-job-1", "TestFactory")
    val chain = listOf(listOf(testJob))

    every { jobStorage.insertJobs(any()) } returns Unit
    every { scheduler.schedule(any(), any<List<Constraint>>()) } returns Unit

    // When
    jobController.submitNewJobChain(chain)

    // Then
    verify { jobStorage.insertJobs(any()) }
    verify { scheduler.schedule(0L, emptyList()) }
    verify { testJob.onSubmit() }
  }

  @Test
  fun `submitNewJobChain handles chain that exceeds maximum instances`() {
    // Given
    val testJob = createTestJob("test-job-1", "TestFactory") { params ->
      every { params.maxInstancesForFactory } returns 1
    }
    every { jobStorage.getJobCountForFactory("TestFactory") } returns 1

    val chain = listOf(listOf(testJob))

    // When
    jobController.submitNewJobChain(chain)

    // Then
    verify { jobTracker.onStateChange(testJob, JobTracker.JobState.IGNORED) }
    verify(exactly = 0) { jobStorage.insertJobs(any()) }
  }

  @Test
  fun `submitJobWithExistingDependencies handles failed dependencies`() {
    // Given
    val testJob = createTestJob("test-job-1", "TestFactory")
    val dependsOn = setOf("failed-job-id")

    every { jobTracker.haveAnyFailed(dependsOn) } returns true
    every { jobStorage.getJobSpec("failed-job-id") } returns null

    // When
    jobController.submitJobWithExistingDependencies(testJob, dependsOn, null)

    // Then
    verify { testJob.onFailure() }
    verify(exactly = 0) { jobStorage.insertJobs(any()) }
  }

  @Test
  fun `cancelJob handles unknown job`() {
    // Given
    every { jobStorage.getJobSpec("unknown-job") } returns null

    // When
    jobController.cancelJob("unknown-job")

    // Then - Should not crash
    verify(exactly = 0) { jobStorage.deleteJob(any()) }
  }

  @Test
  fun `pullNextEligibleJobForExecution with timeout returns null when no jobs available`() {
    // Given
    every { jobStorage.getNextEligibleJob(any(), any()) } returns null

    // When
    val result = jobController.pullNextEligibleJobForExecution({ true }, "runner", 100)

    // Then
    assertThat(result).isNull()
  }

  @Test
  fun `pullNextEligibleJobForExecution marks job as running and tracks it`() {
    // Given
    val jobSpec = createJobSpec("test-job-1", "TestFactory")
    val testJob = createTestJob("test-job-1", "TestFactory")

    every { jobStorage.getNextEligibleJob(any(), any()) } returns jobSpec
    every { jobStorage.getConstraintSpecs("test-job-1") } returns emptyList()
    every { jobInstantiator.instantiate("TestFactory", any(), any()) } returns testJob
    every { jobStorage.markJobAsRunning("test-job-1", any()) } returns Unit

    // When
    val result = jobController.pullNextEligibleJobForExecution({ true }, "runner", 0)

    // Then
    assertThat(result).isEqualTo(testJob)
    verify { jobStorage.markJobAsRunning("test-job-1", any()) }
    verify { jobTracker.onStateChange(testJob, JobTracker.JobState.RUNNING) }
  }

  @Test
  fun `onSuccess deletes job and updates tracker`() {
    // Given
    val testJob = createTestJob("test-job-1", "TestFactory")
    every { jobStorage.getDependencySpecsThatDependOnJob("test-job-1") } returns emptyList()
    every { jobStorage.deleteJob("test-job-1") } returns Unit

    // When
    jobController.onSuccess(testJob, null)

    // Then
    verify { jobStorage.deleteJob("test-job-1") }
    verify { jobTracker.onStateChange(testJob, JobTracker.JobState.SUCCESS) }
  }

  @Test
  fun `onSuccess with output data updates dependent jobs`() {
    // Given
    val testJob = createTestJob("test-job-1", "TestFactory")
    val outputData = "test-output".toByteArray()
    val dependentSpec = DependencySpec("dependent-job", "test-job-1", false)
    val dependentJobSpec = createJobSpec("dependent-job", "DependentFactory")

    every { jobStorage.getDependencySpecsThatDependOnJob("test-job-1") } returns listOf(dependentSpec)
    every { jobStorage.getJobSpec("dependent-job") } returns dependentJobSpec
    every { jobStorage.updateJobs(any()) } returns Unit
    every { jobStorage.deleteJob("test-job-1") } returns Unit

    // When
    jobController.onSuccess(testJob, outputData)

    // Then
    verify { jobStorage.updateJobs(any()) }
    verify { jobStorage.deleteJob("test-job-1") }
  }

  @Test
  fun `onFailure deletes job and all dependents`() {
    // Given
    val testJob = createTestJob("test-job-1", "TestFactory")
    val dependentSpec = DependencySpec("dependent-job", "test-job-1", false)
    val dependentJobSpec = createJobSpec("dependent-job", "DependentFactory")
    val dependentJob = createTestJob("dependent-job", "DependentFactory")

    every { jobStorage.getDependencySpecsThatDependOnJob("test-job-1") } returns listOf(dependentSpec)
    every { jobStorage.getJobSpec("dependent-job") } returns dependentJobSpec
    every { jobStorage.getConstraintSpecs("dependent-job") } returns emptyList()
    every { jobInstantiator.instantiate("DependentFactory", any(), any()) } returns dependentJob
    every { jobStorage.deleteJobs(any()) } returns Unit

    // When
    val dependents = jobController.onFailure(testJob)

    // Then
    assertThat(dependents).hasSize(1)
    assertThat(dependents[0]).isEqualTo(dependentJob)
    verify { jobStorage.deleteJobs(listOf("test-job-1", "dependent-job")) }
    verify { jobTracker.onStateChange(testJob, JobTracker.JobState.FAILURE) }
    verify { jobTracker.onStateChange(dependentJob, JobTracker.JobState.FAILURE) }
  }

  @Test
  fun `onRetry updates job with backoff and schedules retry`() {
    // Given
    val testJob = createTestJob("test-job-1", "TestFactory")
    val backoffInterval = 5000L

    every { jobStorage.updateJobAfterRetry(any(), any(), any(), any(), any()) } returns Unit
    every { jobStorage.getConstraintSpecs("test-job-1") } returns emptyList()
    every { scheduler.schedule(backoffInterval, emptyList()) } returns Unit

    // When
    jobController.onRetry(testJob, backoffInterval)

    // Then
    verify { jobStorage.updateJobAfterRetry("test-job-1", any(), 1, backoffInterval, any()) }
    verify { jobTracker.onStateChange(testJob, JobTracker.JobState.PENDING) }
    verify { scheduler.schedule(backoffInterval, emptyList()) }
  }

  @Test
  fun `submitJobs filters out jobs that exceed maximum instances`() {
    // Given
    val validJob = createTestJob("valid-job", "ValidFactory") { params ->
      every { params.maxInstancesForFactory } returns 5
      every { params.maxInstancesForQueue } returns Job.Parameters.UNLIMITED
    }
    val invalidJob = createTestJob("invalid-job", "InvalidFactory") { params ->
      every { params.maxInstancesForFactory } returns 1
      every { params.maxInstancesForQueue } returns Job.Parameters.UNLIMITED
    }

    every { jobStorage.getJobCountForFactory("ValidFactory") } returns 2
    every { jobStorage.getJobCountForFactory("InvalidFactory") } returns 1

    every { jobStorage.insertJobs(any()) } returns Unit
    every { scheduler.schedule(any(), any<List<Constraint>>()) } returns Unit

    // When
    jobController.submitJobs(listOf(validJob, invalidJob))

    // Then
    verify { jobTracker.onStateChange(invalidJob, JobTracker.JobState.IGNORED) }
    verify { jobStorage.insertJobs(any()) }
    verify { validJob.onSubmit() }
    verify(exactly = 0) { invalidJob.onSubmit() }
  }

  @Test
  fun `submitJobs handles empty list after filtering`() {
    // Given
    val invalidJob = createTestJob("invalid-job", "InvalidFactory") { params ->
      every { params.maxInstancesForFactory } returns 1
    }
    every { jobStorage.getJobCountForFactory("InvalidFactory") } returns 1

    // When
    jobController.submitJobs(listOf(invalidJob))

    // Then
    verify(exactly = 0) { jobStorage.insertJobs(any()) }
    verify(exactly = 0) { scheduler.schedule(any(), any<List<Constraint>>()) }
  }

  @Test
  fun `pullNextEligibleJobForExecution publishes empty callback when no running jobs`() {
    // Given
    every { jobStorage.getNextEligibleJob(any(), any()) } returns null

    // When
    jobController.pullNextEligibleJobForExecution({ true }, "runner", 10)

    // Then
    verify { debouncer.publish(any()) }
  }

  @Test
  fun `findJobs returns filtered results from storage`() {
    // Given
    val predicate: (JobSpec) -> Boolean = { it.factoryKey == "TestFactory" }
    val jobSpecs = listOf(
      createJobSpec("job-1", "TestFactory"),
      createJobSpec("job-2", "OtherFactory")
    )

    every { jobStorage.getAllMatchingFilter(any()) } returns jobSpecs.filter(predicate)

    // When
    val result = jobController.findJobs(predicate)

    // Then
    assertThat(result).hasSize(1)
    assertThat(result[0].id).isEqualTo("job-1")
    verify { jobStorage.getAllMatchingFilter(any()) }
  }

  @Test
  fun `cancelJob handles job not found gracefully`() {
    // Given
    val nonExistentJobId = "non-existent-job"
    every { jobStorage.getJobSpec(nonExistentJobId) } returns null

    // When
    jobController.cancelJob(nonExistentJobId)

    // Then
    verify(exactly = 0) { jobStorage.deleteJob(any()) }
    verify(exactly = 0) { jobStorage.deleteJobs(any()) }
  }

  @Test
  fun `cancelAllInQueue cancels all jobs in specified queue`() {
    // Given
    val queueName = "test-queue"
    val job1Spec = createJobSpec("job-1", "Factory1")
    val job2Spec = createJobSpec("job-2", "Factory2")
    val job1 = createTestJob("job-1", "Factory1")
    val job2 = createTestJob("job-2", "Factory2")

    every { jobStorage.getJobsInQueue(queueName) } returns listOf(job1Spec, job2Spec)
    every { jobStorage.getJobSpec("job-1") } returns job1Spec
    every { jobStorage.getJobSpec("job-2") } returns job2Spec
    every { jobStorage.getConstraintSpecs(any()) } returns emptyList()
    every { jobInstantiator.instantiate("Factory1", any(), any()) } returns job1
    every { jobInstantiator.instantiate("Factory2", any(), any()) } returns job2
    every { jobStorage.getDependencySpecsThatDependOnJob(any()) } returns emptyList()
    every { jobStorage.deleteJob(any()) } returns Unit
    every { jobStorage.deleteJobs(any()) } returns Unit

    // When
    jobController.cancelAllInQueue(queueName)

    // Then
    verify { job1.cancel() }
    verify { job2.cancel() }
    verify { job1.onFailure() }
    verify { job2.onFailure() }
  }

  @Test
  fun `onFailure handles cascading failures correctly`() {
    // Given - Create a chain: job1 -> job2 -> job3
    val job1 = createTestJob("job-1", "Factory1")
    val job2Spec = createJobSpec("job-2", "Factory2")
    val job3Spec = createJobSpec("job-3", "Factory3")
    val job2 = createTestJob("job-2", "Factory2")
    val job3 = createTestJob("job-3", "Factory3")

    val job2Dependency = DependencySpec("job-2", "job-1", false)
    val job3Dependency = DependencySpec("job-3", "job-2", false)

    every { jobStorage.getDependencySpecsThatDependOnJob("job-1") } returns listOf(job2Dependency)
    every { jobStorage.getDependencySpecsThatDependOnJob("job-2") } returns listOf(job3Dependency)
    every { jobStorage.getDependencySpecsThatDependOnJob("job-3") } returns emptyList()

    every { jobStorage.getJobSpec("job-2") } returns job2Spec
    every { jobStorage.getJobSpec("job-3") } returns job3Spec
    every { jobStorage.getConstraintSpecs(any()) } returns emptyList()
    every { jobInstantiator.instantiate("Factory2", any(), any()) } returns job2
    every { jobInstantiator.instantiate("Factory3", any(), any()) } returns job3

    every { jobStorage.deleteJobs(any()) } returns Unit

    // When
    val dependents = jobController.onFailure(job1)

    // Then
    assertThat(dependents).hasSize(1)
    assertThat(dependents[0]).isEqualTo(job2)
    verify { jobStorage.deleteJobs(listOf("job-1", "job-2")) }
    verify { jobTracker.onStateChange(job1, JobTracker.JobState.FAILURE) }
    verify { jobTracker.onStateChange(job2, JobTracker.JobState.FAILURE) }
  }

  @Test
  fun `onFailure handles null job specs in dependency chain`() {
    // Given
    val job1 = createTestJob("job-1", "Factory1")
    val job2Dependency = DependencySpec("job-2", "job-1", false)
    val job3Dependency = DependencySpec("job-3", "job-1", false)

    every { jobStorage.getDependencySpecsThatDependOnJob("job-1") } returns listOf(job2Dependency, job3Dependency)
    every { jobStorage.getJobSpec("job-2") } returns null // Job was already deleted
    every { jobStorage.getJobSpec("job-3") } returns createJobSpec("job-3", "Factory3")
    every { jobStorage.getConstraintSpecs("job-3") } returns emptyList()
    every { jobInstantiator.instantiate("Factory3", any(), any()) } returns createTestJob("job-3", "Factory3")
    every { jobStorage.deleteJobs(any()) } returns Unit

    // When
    val dependents = jobController.onFailure(job1)

    // Then - Should handle null job spec gracefully
    assertThat(dependents).hasSize(1)
    assertThat(dependents[0].id).isEqualTo("job-3")
  }

  @Test
  fun `startJobRunners - creates minimum number of runners, even with no eligible jobs`() {
    // Given
    every { jobStorage.getEligibleJobCount(any()) } returns 0

    // When
    jobController.startJobRunners()

    // Then
    assertThat(jobController.activeGeneralRunners.size).isEqualTo(MIN_RUNNERS)
  }

  @Test
  fun `startJobRunners - creates runners to satisfy demand`() {
    // Given
    every { jobStorage.getEligibleJobCount(any()) } returns MAX_RUNNERS

    // When
    jobController.startJobRunners()

    // Then
    assertThat(jobController.activeGeneralRunners.size).isEqualTo(MAX_RUNNERS)
  }

  @Test
  fun `startJobRunners - does not exceed max runners`() {
    // Given
    every { jobStorage.getEligibleJobCount(any()) } returns MAX_RUNNERS * 2

    // When
    jobController.startJobRunners()

    // Then
    assertThat(jobController.activeGeneralRunners.size).isEqualTo(MAX_RUNNERS)
  }

  @Test
  fun `maybeScaleUpRunners - creates runners to satisfy demand`() {
    // When
    jobController.runnersStarted.set(true)
    jobController.maybeScaleUpRunners { MAX_RUNNERS }

    // Then
    assertThat(jobController.activeGeneralRunners.size).isEqualTo(MAX_RUNNERS)
  }

  @Test
  fun `maybeScaleUpRunners - does not exceed max runners`() {
    // When
    jobController.runnersStarted.set(true)
    jobController.maybeScaleUpRunners { MAX_RUNNERS * 2 }

    // Then
    assertThat(jobController.activeGeneralRunners.size).isEqualTo(MAX_RUNNERS)
  }

  @Test
  fun `onRunnerTerminated - decrements active runners`() {
    // Given
    every { jobStorage.getEligibleJobCount(any()) } returns MAX_RUNNERS
    jobController.startJobRunners()

    // When
    jobController.onRunnerTerminated(jobController.activeGeneralRunners.first())

    // Then
    assertThat(jobController.activeGeneralRunners.size).isEqualTo(MAX_RUNNERS - 1)
  }

  /**
   * @param parameterConfig Allows you to mock out specific fields on the [Job.Parameters].
   */
  private fun createTestJob(
    id: String,
    factoryKey: String,
    parameterConfig: ((Job.Parameters) -> Unit)? = null
  ): Job {
    val job = mockk<Job>(relaxed = true)
    every { job.id } returns id
    every { job.factoryKey } returns factoryKey
    every { job.runAttempt } returns 0
    every { job.serialize() } returns null
    every { job.parameters } returns createJobParameters(id, parameterConfig)
    return job
  }

  private fun createJobParameters(
    id: String,
    config: ((Job.Parameters) -> Unit)? = null
  ): Job.Parameters {
    val params = mockk<Job.Parameters>(relaxed = true)
    every { params.id } returns id
    every { params.maxInstancesForFactory } returns Job.Parameters.UNLIMITED
    every { params.maxInstancesForQueue } returns Job.Parameters.UNLIMITED
    every { params.queue } returns null
    every { params.constraintKeys } returns emptyList()
    every { params.initialDelay } returns 0L
    config?.invoke(params)
    return params
  }

  private fun createJobSpec(id: String, factoryKey: String): JobSpec {
    return JobSpec(
      id = id,
      factoryKey = factoryKey,
      queueKey = null,
      createTime = System.currentTimeMillis(),
      lastRunAttemptTime = 0L,
      nextBackoffInterval = 0L,
      runAttempt = 0,
      maxAttempts = 3,
      lifespan = -1L,
      serializedData = null,
      serializedInputData = null,
      isRunning = false,
      isMemoryOnly = false,
      globalPriority = 0,
      queuePriority = 0,
      initialDelay = 0L
    )
  }
}
