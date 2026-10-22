/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.eventbus

import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import org.signal.core.util.ThreadUtil

/**
 * Coalesces main-thread deliveries into a single pending looper message. A burst of events from background threads
 * results in one post to the main looper rather than one per event, which keeps contention on the main
 * [android.os.MessageQueue] lock low. Deliveries are drained in order, yielding back to the looper after [maxMillisPerPass].
 */
internal class MainThreadPoster @VisibleForTesting constructor(
  private val postToMain: (Runnable) -> Unit,
  private val uptimeMillis: () -> Long,
  private val maxMillisPerPass: Long
) {

  constructor() : this(ThreadUtil::postToMain, SystemClock::uptimeMillis, DEFAULT_MAX_MILLIS_PER_PASS)

  companion object {
    private const val DEFAULT_MAX_MILLIS_PER_PASS = 10L
  }

  private class PendingPost(val subscription: EventBus.Subscription, val event: Any)

  private val queue = ArrayDeque<PendingPost>()
  private var scheduled = false
  private val drainRunnable = Runnable { drain() }

  fun enqueue(subscription: EventBus.Subscription, event: Any) {
    synchronized(queue) {
      queue.addLast(PendingPost(subscription, event))
      if (!scheduled) {
        scheduled = true
        postToMain(drainRunnable)
      }
    }
  }

  private fun drain() {
    val start = uptimeMillis()
    try {
      while (uptimeMillis() - start < maxMillisPerPass) {
        val post = synchronized(queue) { queue.removeFirstOrNull() } ?: break
        post.subscription.invokeIfActive(post.event)
      }
    } finally {
      synchronized(queue) {
        scheduled = queue.isNotEmpty()
        if (scheduled) {
          postToMain(drainRunnable)
        }
      }
    }
  }
}
