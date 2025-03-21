/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration

/**
 * Throttles the flow so that at most one value is emitted every [timeout]. The latest value is always emitted.
 *
 * You can think of this like debouncing, but with "checkpoints" so that even if you have a constant stream of values,
 * you'll still get an emission every [timeout] (unlike debouncing, which will only emit once the stream settles down).
 *
 * You can specify an optional [emitImmediately] function that will indicate whether an emission should skip throttling and
 * be emitted immediately. This lambda should be stateless, as it may be called multiple times for each item.
 */
fun <T> Flow<T>.throttleLatest(timeout: Duration, emitImmediately: (T) -> Boolean = { false }): Flow<T> {
  val rootFlow = this
  return channelFlow {
    rootFlow
      .onEach { if (emitImmediately(it)) send(it) }
      .filterNot { emitImmediately(it) }
      .conflate()
      .collect {
        send(it)
        delay(timeout)
      }
  }
}
