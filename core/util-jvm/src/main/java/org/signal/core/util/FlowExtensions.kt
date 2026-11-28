/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Throttles the flow so that at most one value is emitted every [timeout]. The latest value is always emitted.
 *
 * You can think of this like debouncing, but with "checkpoints" so that even if you have a constant stream of values,
 * you'll still get an emission every [timeout] (unlike debouncing, which will only emit once the stream settles down).
 * If the collector is slower than [timeout], emissions are spaced by the collector instead, still delivering the latest value.
 *
 * You can specify an optional [emitImmediately] function that will indicate whether an emission should skip throttling and
 * be emitted immediately. Like any other value, an immediate value is replaced by newer values while the collector is busy,
 * but whatever is latest at that point is emitted without waiting for the throttle window. Immediate emissions still start
 * a new window, so the next throttled value waits [timeout] after them. This lambda should be stateless.
 *
 * The upstream flow is never suspended by a slow collector.
 */
fun <T> Flow<T>.throttleLatest(timeout: Duration, emitImmediately: (T) -> Boolean = { false }): Flow<T> {
  val rootFlow = this
  return flow {
    coroutineScope {
      val pending = PendingValues<T>()
      val wakeup = Channel<Unit>(Channel.CONFLATED)

      // Collect upstream into a single slot so a slow collector never suspends the source
      launch {
        try {
          rootFlow.collect {
            pending.set(it, emitImmediately(it))
            wakeup.trySend(Unit)
          }
        } finally {
          pending.finish()
          wakeup.trySend(Unit)
        }
      }

      // The window job blocks throttled values until it finishes, then wakes the loop
      var window: Job? = null
      while (true) {
        val next = pending.take(throttledAllowed = window?.isActive != true)
        // Nothing ready yet, so stop if upstream is done or wait for a new value or the window to end
        if (next == null) {
          if (pending.isDrained) {
            break
          }
          wakeup.receive()
          continue
        }

        // Every emission starts a window, immediate values just don't wait for the current one.
        // Start it before emitting so it overlaps a slow collector instead of adding to it
        window?.cancel()
        window = launch {
          delay(timeout)
          wakeup.trySend(Unit)
        }

        // Suspends until the collector is done, so the next take always sees the newest value
        emit(next.value)
      }

      // Nothing left to emit, so don't let a trailing window hold the flow open
      window?.cancel()
    }
  }
}

private class PendingValue<T>(val value: T, val immediate: Boolean)

private class PendingValues<T> {
  private var pending: PendingValue<T>? = null
  private var finished = false

  val isDrained: Boolean
    @Synchronized get() = finished && pending == null

  @Synchronized
  fun set(value: T, immediate: Boolean) {
    // Newer values always replace the pending one, but an unemitted immediate keeps the replacement immediate
    pending = PendingValue(value, immediate || pending?.immediate == true)
  }

  @Synchronized
  fun take(throttledAllowed: Boolean): PendingValue<T>? {
    val next = pending
    if (next == null || (!next.immediate && !throttledAllowed)) {
      return null
    }
    pending = null
    return next
  }

  @Synchronized
  fun finish() {
    finished = true
  }
}
