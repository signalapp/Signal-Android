/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.eventbus

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RestartableSubscriptionTest {

  private class Event(val value: Int)

  private val bus = EventBus()

  @Test
  fun `start - repeated calls subscribe once`() {
    val received = mutableListOf<Int>()
    val subscription = RestartableSubscription { bus.subscribe<Event> { received += it.value } }

    subscription.start()
    subscription.start()
    bus.post(Event(1))

    assertThat(received).containsExactly(1)
  }

  @Test
  fun `stop - no delivery after stop, and stop is idempotent`() {
    val received = mutableListOf<Int>()
    val subscription = RestartableSubscription { bus.subscribe<Event> { received += it.value } }

    subscription.stop()
    subscription.start()
    bus.post(Event(1))
    subscription.stop()
    subscription.stop()
    bus.post(Event(2))

    assertThat(received).containsExactly(1)
  }

  @Test
  fun `start - can restart after stop`() {
    val received = mutableListOf<Int>()
    val subscription = RestartableSubscription { bus.subscribe<Event> { received += it.value } }

    subscription.start()
    subscription.stop()
    bus.post(Event(1))
    subscription.start()
    bus.post(Event(2))

    assertThat(received).containsExactly(2)
  }

  @Test
  fun `stop - honored when called from a sticky handler during start`() {
    val received = mutableListOf<Int>()
    lateinit var subscription: RestartableSubscription
    subscription = RestartableSubscription {
      bus.subscribeSticky<Event> {
        received += it.value
        subscription.stop()
      }
    }

    bus.postSticky(Event(1))
    subscription.start()
    bus.post(Event(2))

    assertThat(received).containsExactly(1)
  }

  @Test
  fun `bindTo - subscribed only while at least the given state`() {
    val received = mutableListOf<Int>()
    val owner = TestLifecycleOwner()
    RestartableSubscription { bus.subscribe<Event> { received += it.value } }.bindTo(owner, Lifecycle.State.RESUMED)

    owner.registry.currentState = Lifecycle.State.STARTED
    bus.post(Event(1))
    owner.registry.currentState = Lifecycle.State.RESUMED
    bus.post(Event(2))
    owner.registry.currentState = Lifecycle.State.STARTED
    bus.post(Event(3))
    owner.registry.currentState = Lifecycle.State.RESUMED
    bus.post(Event(4))

    assertThat(received).containsExactly(2, 4)
  }

  @Test
  fun `bindTo - stops when destroyed`() {
    val received = mutableListOf<Int>()
    val owner = TestLifecycleOwner()
    RestartableSubscription { bus.subscribe<Event> { received += it.value } }.bindTo(owner, Lifecycle.State.CREATED)

    owner.registry.currentState = Lifecycle.State.CREATED
    owner.registry.currentState = Lifecycle.State.DESTROYED
    bus.post(Event(1))

    assertThat(received).isEmpty()
  }

  private class TestLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this)
    override val lifecycle: Lifecycle = registry
  }
}
