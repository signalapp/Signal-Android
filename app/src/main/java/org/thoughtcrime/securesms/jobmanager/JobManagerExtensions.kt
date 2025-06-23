/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobmanager

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.thoughtcrime.securesms.dependencies.AppDependencies
import kotlin.time.Duration

/**
 * Runs the specified job synchronously. Beware: All normal dependencies are respected, meaning
 * you must take great care where you call this. It could take a very long time to complete!
 *
 * Coroutine friendly version of [JobManager.runSynchronously].
 *
 * @param timeout How long to wait for the job to complete before aborting and returning
 * @return If the job completed, the completion job state, otherwise null
 */
suspend fun JobManager.runJobBlocking(job: Job, timeout: Duration): JobTracker.JobState? {
  val jobState = callbackFlow {
    val listener = JobTracker.JobListener { _, jobState ->
      if (jobState.isComplete) {
        trySend(jobState)
      }
    }

    this@runJobBlocking.addListener(job.id, listener)
    this@runJobBlocking.add(job)

    awaitClose {
      this@runJobBlocking.removeListener(listener)
    }
  }

  return withTimeoutOrNull(timeout) {
    jobState
      .firstOrNull()
  }
}

/**
 * Runs the chain synchronously. Beware: All normal dependencies are respected, meaning
 * you must take great care where you call this. It could take a very long time to complete!
 *
 * Coroutine friendly version of [JobManager.Chain.enqueueAndBlockUntilCompletion].
 *
 * @param timeout How long to wait for the chain to complete before aborting and returning
 * @return If the job completed, the completion job state, otherwise null
 */
suspend fun JobManager.Chain.enqueueBlocking(timeout: Duration): JobTracker.JobState? {
  val jobState = callbackFlow {
    val listener = JobTracker.JobListener { _, jobState ->
      if (jobState.isComplete) {
        trySend(jobState)
      }
    }

    this@enqueueBlocking.enqueue(listener)

    awaitClose {
      AppDependencies.jobManager.removeListener(listener)
    }
  }

  return withTimeoutOrNull(timeout) {
    jobState
      .firstOrNull()
  }
}
