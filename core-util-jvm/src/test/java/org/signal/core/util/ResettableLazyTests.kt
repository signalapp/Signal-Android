/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch

class ResettableLazyTests {

  @Test
  fun `value only computed once`() {
    var counter = 0
    val lazy: Int by resettableLazy {
      counter++
    }

    assertEquals(0, lazy)
    assertEquals(0, lazy)
    assertEquals(0, lazy)
  }

  @Test
  fun `value recomputed after a reset`() {
    var counter = 0
    val _lazy = resettableLazy {
      counter++
    }
    val lazy by _lazy

    assertEquals(0, lazy)
    _lazy.reset()

    assertEquals(1, lazy)
    _lazy.reset()

    assertEquals(2, lazy)
  }

  @Test
  fun `isInitialized - general`() {
    val _lazy = resettableLazy { 1 }
    val lazy: Int by _lazy

    assertFalse(_lazy.isInitialized())

    val x = lazy + 1
    assertEquals(2, x)
    assertTrue(_lazy.isInitialized())

    _lazy.reset()
    assertFalse(_lazy.isInitialized())
  }

  /**
   * I've verified that without the synchronization inside of resettableLazy, this test usually fails.
   */
  @Test
  fun `ensure synchronization works`() {
    val numRounds = 100
    val numThreads = 5

    for (i in 1..numRounds) {
      var counter = 0
      val lazy: Int by resettableLazy {
        counter++
      }

      val latch = CountDownLatch(numThreads)

      for (j in 1..numThreads) {
        Thread {
          val x = lazy + 1
          latch.countDown()
        }.start()
      }

      latch.await()

      assertEquals(1, counter)
    }
  }
}
