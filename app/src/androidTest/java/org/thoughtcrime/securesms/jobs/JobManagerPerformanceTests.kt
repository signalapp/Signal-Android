/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.EventTimer
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.JobDatabase.Companion.getInstance
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobManager
import org.thoughtcrime.securesms.jobmanager.JobMigrator
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.util.concurrent.CountDownLatch
import kotlin.random.Random

@Ignore("This is just for testing performance, not correctness, and they can therefore take a long time. Run them manually when you need to.")
@RunWith(AndroidJUnit4::class)
class JobManagerPerformanceTests {

  companion object {
    val TAG = Log.tag(JobManagerPerformanceTests::class.java)
  }

  @Test
  fun testPerformance_singleQueue() {
    runTest("singleQueue", 2000) { TestJob(queue = "queue") }
  }

  @Test
  fun testPerformance_fourQueues() {
    runTest("fourQueues", 2000) { TestJob(queue = "queue-${Random.nextInt(1, 5)}") }
  }

  @Test
  fun testPerformance_noQueues() {
    runTest("noQueues", 2000) { TestJob(queue = null) }
  }

  private fun runTest(name: String, count: Int, jobCreator: () -> TestJob) {
    val context = AppDependencies.application
    val jobManager = testJobManager(context)

    jobManager.beginJobLoop()

    val eventTimer = EventTimer()

    val latch = CountDownLatch(count)
    var seenStart = false
    jobManager.addListener({ it.factoryKey == TestJob.KEY }) { _, state ->
      if (!seenStart && state == JobTracker.JobState.RUNNING) {
        // Adding the jobs can take a while (and runs on a background thread), so we want to reset the timer the first time we see a job run so the first job
        // doesn't have a skewed time
        eventTimer.reset()
        seenStart = true
      }
      if (state.isComplete) {
        eventTimer.emit("job")
        latch.countDown()
        if (latch.count % 100 == 0L) {
          Log.d(TAG, "[$name] Finished ${count - latch.count}/$count jobs")
        }
      }
    }

    Log.i(TAG, "[$name] Adding jobs...")
    jobManager.addAll((1..count).map { jobCreator() })

    Log.i(TAG, "[$name] Waiting for jobs to complete...")
    latch.await()
    Log.i(TAG, "[$name] Jobs complete!")
    Log.i(TAG, eventTimer.stop().summary)
  }

  private fun testJobManager(context: Application): JobManager {
    val config = JobManager.Configuration.Builder()
      .setJobFactories(
        JobManagerFactories.getJobFactories(context) + mapOf(
          TestJob.KEY to TestJob.Factory()
        )
      )
      .setConstraintFactories(JobManagerFactories.getConstraintFactories(context))
      .setConstraintObservers(JobManagerFactories.getConstraintObservers(context))
      .setJobStorage(FastJobStorage(getInstance(context)))
      .setJobMigrator(JobMigrator(TextSecurePreferences.getJobManagerVersion(context), JobManager.CURRENT_VERSION, JobManagerFactories.getJobMigrations(context)))
      .build()

    return JobManager(context, config)
  }

  private class TestJob(params: Parameters) : Job(params) {
    companion object {
      const val KEY = "test"
    }

    constructor(queue: String?) : this(Parameters.Builder().setQueue(queue).build())

    override fun serialize(): ByteArray? = null
    override fun getFactoryKey(): String = KEY
    override fun run(): Result = Result.success()
    override fun onFailure() = Unit

    class Factory : Job.Factory<TestJob> {
      override fun create(parameters: Parameters, serializedData: ByteArray?): TestJob {
        return TestJob(parameters)
      }
    }
  }
}
