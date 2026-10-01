/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

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
      .throttleLatest(53.milliseconds)
      .toList()

    assertEquals(listOf(1, 6, 11, 16, 22, 27, 30), output)
  }

  @Test
  fun `throttleLatest - slow collector receives latest value instead of a backlog`() = runTest {
    val testFlow = flow {
      for (i in 1..30) {
        emit(i)
        delay(10)
      }
    }

    val output = testFlow
      .throttleLatest(53.milliseconds)
      .onEach { delay(205) }
      .toList()

    assertEquals(listOf(1, 21, 30), output)
  }

  @Test
  fun `throttleLatest - respects skipThrottle`() = runTest {
    val testFlow = flow {
      for (i in 1..30) {
        emit(i)
        delay(10)
      }
    }

    val output = testFlow
      .throttleLatest(53.milliseconds) { it in setOf(2, 3, 4, 26, 27, 28) }
      .toList()

    assertEquals(listOf(1, 2, 3, 4, 9, 14, 19, 25, 26, 27, 28, 30), output)
  }

  @Test
  fun `throttleLatest - slow collector never suspends the upstream`() = runTest {
    val source = MutableSharedFlow<Int>()
    val received = mutableListOf<Int>()

    val collector = launch {
      source
        .throttleLatest(50.milliseconds) { it % 2 == 0 }
        .collect {
          delay(1000)
          received += it
        }
    }
    source.subscriptionCount.first { it > 0 }

    val start = testScheduler.currentTime
    for (i in 1..200) {
      source.emit(i)
    }

    assertEquals(start, testScheduler.currentTime)

    advanceTimeBy(1.minutes * 10)
    collector.cancel()

    assertEquals(200, received.last())
    assertTrue(received.size <= 2)
  }

  @Test
  fun `throttleLatest - final immediate value is emitted after a stall`() = runTest {
    val testFlow = flow {
      emit(1)
      emit(2)
      delay(5)
      emit(3)
      emit(100)
    }

    val output = testFlow
      .throttleLatest(10.milliseconds) { it >= 100 }
      .onEach { delay(200) }
      .toList()

    assertEquals(listOf(2, 100), output)
  }

  @Test
  fun `throttleLatest - throttled value older than an emitted immediate value is dropped`() = runTest {
    val testFlow = flow {
      emit(1)
      delay(10)
      emit(2)
      delay(10)
      emit(3)
    }

    val output = testFlow
      .throttleLatest(100.milliseconds) { it == 3 }
      .toList()

    assertEquals(listOf(1, 3), output)
  }

  @Test
  fun `throttleLatest - only the latest of a same-instant burst is emitted`() = runTest {
    val testFlow = flow {
      emit(1)
      emit(2)
      emit(3)
      emit(4)
      delay(5)
      emit(5)
    }

    val output = testFlow
      .throttleLatest(10.milliseconds) { it in setOf(2, 3, 4) }
      .onEach { delay(100) }
      .toList()

    assertEquals(listOf(4, 5), output)
  }

  @Test
  fun `throttleLatest - throttled value replacing a pending immediate value skips the window`() = runTest {
    val testFlow = flow {
      emit(1)
      delay(5)
      emit(100)
      emit(3)
    }

    val output = testFlow
      .throttleLatest(1000.milliseconds) { it >= 100 }
      .onEach { delay(200) }
      .toList()

    assertEquals(listOf(1, 3), output)
    assertEquals(400, testScheduler.currentTime)
  }

  @Test
  fun `throttleLatest - throttled value after a replaced immediate value is still throttled`() = runTest {
    val emittedAt = mutableMapOf<String, Long>()
    val testFlow = flow {
      delay(450)
      emit("immediate-1")
      delay(10)
      emit("immediate-2")
      delay(10)
      emit("p")
      delay(90)
      emit("p2")
    }

    val output = testFlow
      .throttleLatest(500.milliseconds) { it.startsWith("immediate") }
      .onEach {
        emittedAt[it] = testScheduler.currentTime
        delay(100)
      }
      .toList()

    assertEquals(listOf("immediate-1", "p", "p2"), output)
    assertEquals(550L, emittedAt["p"])
    assertTrue(emittedAt.getValue("p2") - emittedAt.getValue("p") >= 500)
  }
}
