/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.eventbus

import android.app.Application
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MainThreadPosterTest {

  private class Event(val value: Int)

  private val bus = EventBus()
  private val posted = mutableListOf<Runnable>()
  private var now = 0L
  private val poster = MainThreadPoster(postToMain = { posted += it }, uptimeMillis = { now }, maxMillisPerPass = 10)

  private fun runNextPost() {
    posted.removeAt(0).run()
  }

  @Test
  fun `enqueue - a burst of events schedules a single post`() {
    val received = mutableListOf<Int>()
    val subscription = bus.subscribe<Event>(threadMode = ThreadMode.MAIN) { received += it.value }

    repeat(100) { poster.enqueue(subscription, Event(it)) }

    assertThat(posted).hasSize(1)
    runNextPost()
    assertThat(received).isEqualTo((0 until 100).toList())
    assertThat(posted).isEmpty()
  }

  @Test
  fun `enqueue - after the queue drains a new event schedules a new post`() {
    val received = mutableListOf<Int>()
    val subscription = bus.subscribe<Event>(threadMode = ThreadMode.MAIN) { received += it.value }

    poster.enqueue(subscription, Event(1))
    runNextPost()
    poster.enqueue(subscription, Event(2))

    assertThat(posted).hasSize(1)
    runNextPost()
    assertThat(received).containsExactly(1, 2)
  }

  @Test
  fun `drain - yields to the looper once the time budget is spent and resumes in order`() {
    val received = mutableListOf<Int>()
    val subscription = bus.subscribe<Event>(threadMode = ThreadMode.MAIN) {
      received += it.value
      now += 4
    }

    repeat(5) { poster.enqueue(subscription, Event(it)) }

    runNextPost()
    assertThat(received).containsExactly(0, 1, 2)
    assertThat(posted).hasSize(1)

    runNextPost()
    assertThat(received).containsExactly(0, 1, 2, 3, 4)
    assertThat(posted).isEmpty()
  }

  @Test
  fun `drain - events enqueued while draining are delivered without an extra post`() {
    val received = mutableListOf<Int>()
    lateinit var subscription: EventBus.Subscription
    subscription = bus.subscribe<Event>(threadMode = ThreadMode.MAIN) {
      received += it.value
      if (it.value == 1) {
        poster.enqueue(subscription, Event(2))
      }
    }

    poster.enqueue(subscription, Event(1))
    runNextPost()

    assertThat(received).containsExactly(1, 2)
    assertThat(posted).isEmpty()
  }

  @Test
  fun `drain - a throwing handler does not stall later deliveries`() {
    val received = mutableListOf<Int>()
    val subscription = bus.subscribe<Event>(threadMode = ThreadMode.MAIN) {
      if (it.value == 1) {
        throw IllegalStateException("boom")
      }
      received += it.value
    }

    poster.enqueue(subscription, Event(1))
    poster.enqueue(subscription, Event(2))

    assertFailure { runNextPost() }.hasClass(IllegalStateException::class)

    assertThat(posted).hasSize(1)
    runNextPost()
    assertThat(received).containsExactly(2)
  }

  @Test
  fun `drain - unsubscribed subscriptions are skipped`() {
    val received = mutableListOf<Int>()
    val subscription = bus.subscribe<Event>(threadMode = ThreadMode.MAIN) { received += it.value }

    poster.enqueue(subscription, Event(1))
    subscription.unsubscribe()
    runNextPost()

    assertThat(received).isEmpty()
  }
}
