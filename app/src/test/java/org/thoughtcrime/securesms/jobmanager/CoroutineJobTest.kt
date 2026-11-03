/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobmanager

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CoroutineJobTest {

  private val executor = Executors.newSingleThreadExecutor()

  @Test
  fun `canceling a running job cancels doRun when enabled`() {
    val started = CountDownLatch(1)
    val job = TestJob(cancelCoroutineOnJobCancel = true) {
      started.countDown()
      awaitCancellation()
    }

    val result = executor.submit<Job.Result> { job.run() }
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
    job.cancel()

    assertThat(result.get(5, TimeUnit.SECONDS).isFailure).isTrue()
  }

  @Test
  fun `a job canceled before it runs never starts doRun when enabled`() {
    var ran = false
    val job = TestJob(cancelCoroutineOnJobCancel = true) {
      ran = true
      Job.Result.success()
    }

    job.cancel()

    assertThat(job.run().isFailure).isTrue()
    assertThat(ran).isFalse()
  }

  @Test
  fun `canceling only sets the flag when disabled`() {
    val started = CountDownLatch(1)
    val release = CompletableDeferred<Unit>()
    val job = TestJob(cancelCoroutineOnJobCancel = false) {
      started.countDown()
      release.await()
      Job.Result.success()
    }

    val result = executor.submit<Job.Result> { job.run() }
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
    job.cancel()
    release.complete(Unit)

    assertThat(result.get(5, TimeUnit.SECONDS).isSuccess).isTrue()
    assertThat(job.isCanceled).isTrue()
  }

  private class TestJob(
    override val cancelCoroutineOnJobCancel: Boolean,
    private val work: suspend () -> Job.Result
  ) : CoroutineJob(Parameters.Builder().build()) {
    override suspend fun doRun(): Job.Result = work()
    override fun serialize(): ByteArray? = null
    override fun getFactoryKey(): String = "TestJob"
    override fun onFailure() = Unit
  }
}
