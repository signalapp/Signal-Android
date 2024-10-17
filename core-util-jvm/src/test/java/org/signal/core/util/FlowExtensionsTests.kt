/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class FlowExtensionsTests {

  @Test
  fun `throttleLatest - always emits first value`() = runTest {
    val testFlow = flow {
      delay(10)
      emit(1)
    }

    val output = testFlow
      .throttleLatest(100.milliseconds)
      .toList()

    assertEquals(listOf(1), output)
  }

  @Test
  fun `throttleLatest - always emits last value`() = runTest {
    val testFlow = flow {
      delay(10)
      emit(1)
      delay(30)
      emit(2)
    }

    val output = testFlow
      .throttleLatest(20.milliseconds)
      .toList()

    assertEquals(listOf(1, 2), output)
  }

  @Test
  fun `throttleLatest - skips intermediate values`() = runTest {
    val testFlow = flow {
      for (i in 1..30) {
        emit(i)
        delay(10)
      }
    }

    val output = testFlow
      .throttleLatest(50.milliseconds)
      .toList()

    assertEquals(listOf(1, 5, 10, 15, 20, 25, 30), output)
  }
}
