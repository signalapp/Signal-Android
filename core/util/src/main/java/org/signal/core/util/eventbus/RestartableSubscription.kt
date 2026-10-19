/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.eventbus

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Wraps an [EventBus] subscription so it can be started and stopped any number of times. Both are no-ops if already
 * in the requested state, and stopping from within a handler that runs during [start] (e.g. for a sticky event) is honored.
 */
class RestartableSubscription(private val subscribe: () -> EventBus.Subscription) {

  private var active = false
  private var subscription: EventBus.Subscription? = null

  fun start() {
    if (active) {
      return
    }

    active = true
    val newSubscription = subscribe()
    if (active) {
      subscription = newSubscription
    } else {
      newSubscription.unsubscribe()
    }
  }

  fun stop() {
    active = false
    subscription?.unsubscribe()
    subscription = null
  }

  /**
   * Keeps this subscribed while [lifecycleOwner] is at least [minState], and stops it for good once the lifecycle is destroyed.
   */
  fun bindTo(lifecycleOwner: LifecycleOwner, minState: Lifecycle.State): RestartableSubscription {
    lifecycleOwner.lifecycle.addObserver(
      object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
          val state = source.lifecycle.currentState
          if (state.isAtLeast(minState)) {
            start()
          } else {
            stop()
          }

          if (state == Lifecycle.State.DESTROYED) {
            source.lifecycle.removeObserver(this)
          }
        }
      }
    )
    return this
  }
}
