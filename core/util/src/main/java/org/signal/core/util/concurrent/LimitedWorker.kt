/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.concurrent

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore

object LimitedWorker {

  /**
   * Call [worker] on a thread from [executor] for each element in [input] using only up to [maxThreads] concurrently.
   *
   * This method will block until all work is completed. There is no guarantee that the same threads
   * will be used but that only up to [maxThreads] will be actively doing work.
   */
  @JvmStatic
  fun <T> execute(executor: ExecutorService, maxThreads: Int, input: Collection<T>, worker: (T) -> Unit) {
    val doneWorkLatch = CountDownLatch(input.size)
    val semaphore = Semaphore(maxThreads)

    for (work in input) {
      semaphore.acquire()
      executor.execute {
        worker(work)
        semaphore.release()
        doneWorkLatch.countDown()
      }
    }

    doneWorkLatch.await()
  }
}
