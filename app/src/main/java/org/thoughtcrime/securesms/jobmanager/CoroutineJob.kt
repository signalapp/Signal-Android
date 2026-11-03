/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobmanager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/**
 * Perform a job utilizing operations that require coroutines. By default,
 * doRun is executed on the Default dispatcher.
 */
abstract class CoroutineJob(parameters: Parameters) : Job(parameters) {

  /** When true, canceling the job also cancels [doRun] at its next suspension point instead of only setting [isCanceled]. */
  protected open val cancelCoroutineOnJobCancel: Boolean = false

  @Volatile
  private var runningWork: Deferred<Result>? = null

  override fun run(): Result {
    if (!cancelCoroutineOnJobCancel) {
      return runBlocking {
        doRun()
      }
    }

    return runBlocking {
      val work = async(start = CoroutineStart.LAZY) { doRun() }
      runningWork = work

      // Covers a cancel that landed before runningWork was set, which onCanceled() could not see.
      if (isCanceled) {
        work.cancel()
      }

      try {
        work.await()
      } catch (e: CancellationException) {
        if (isCanceled) {
          Result.failure()
        } else {
          throw e
        }
      } finally {
        runningWork = null
      }
    }
  }

  override fun onCanceled() {
    runningWork?.cancel()
  }

  abstract suspend fun doRun(): Result
}
