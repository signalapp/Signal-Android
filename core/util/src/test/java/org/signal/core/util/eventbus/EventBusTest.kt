/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.eventbus

import android.app.Application
import android.os.Looper
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class EventBusTest {

  private open class Event(val value: Int)
  private class SubEvent(value: Int) : Event(value)
  private class OtherEvent

  private val bus = EventBus()

  @Test
  fun `post - delivers to matching subscribers only`() {
    val received = mutableListOf<Int>()
    bus.subscribe<Event> { received += it.value }
    bus.subscribe<OtherEvent> { received += -1 }

    bus.post(Event(1))
    bus.post(Event(2))

    assertThat(received).containsExactly(1, 2)
  }

  @Test
  fun `post - delivers subtypes to supertype subscribers`() {
    val received = mutableListOf<Int>()
    bus.subscribe<Event> { received += it.value }

    bus.post(SubEvent(7))

    assertThat(received).containsExactly(7)
  }

  @Test
  fun `post - no delivery after unsubscribe`() {
    val received = mutableListOf<Int>()
    val subscription = bus.subscribe<Event> { received += it.value }

    bus.post(Event(1))
    subscription.unsubscribe()
    bus.post(Event(2))

    assertThat(received).containsExactly(1)
  }

  @Test
  fun `post - unsubscribing inside a handler does not break delivery to others`() {
    val received = mutableListOf<String>()
    lateinit var first: EventBus.Subscription
    first = bus.subscribe<Event> {
      received += "first"
      first.unsubscribe()
    }
    bus.subscribe<Event> { received += "second" }

    bus.post(Event(1))
    bus.post(Event(2))

    assertThat(received).containsExactly("first", "second", "second")
  }

  @Test
  fun `postSticky - delivered to later sticky subscribers but not to non-sticky ones`() {
    bus.postSticky(Event(5))

    var sticky: Int? = null
    var nonSticky: Int? = null
    bus.subscribeSticky<Event> { sticky = it.value }
    bus.subscribe<Event> { nonSticky = it.value }

    assertThat(sticky).isEqualTo(5)
    assertThat(nonSticky).isNull()
  }

  @Test
  fun `postSticky - only the most recent event of a type is retained`() {
    bus.postSticky(Event(1))
    bus.postSticky(Event(2))

    assertThat(bus.getStickyEvent<Event>()!!.value).isEqualTo(2)
  }

  @Test
  fun `removeStickyEvent - by class`() {
    bus.postSticky(Event(1))

    assertThat(bus.removeStickyEvent<Event>()!!.value).isEqualTo(1)
    assertThat(bus.getStickyEvent<Event>()).isNull()
  }

  @Test
  fun `removeStickyEvent - by instance only removes that instance`() {
    val stale = Event(1)
    val current = Event(2)
    bus.postSticky(stale)
    bus.postSticky(current)

    assertThat(bus.removeStickyEvent(stale)).isFalse()
    assertThat(bus.getStickyEvent<Event>()).isEqualTo(current)
    assertThat(bus.removeStickyEvent(current)).isTrue()
    assertThat(bus.getStickyEvent<Event>()).isNull()
  }

  @Test
  fun `removeAllStickyEvents - clears everything`() {
    bus.postSticky(Event(1))
    bus.postSticky(OtherEvent())

    bus.removeAllStickyEvents()

    assertThat(bus.getStickyEvent<Event>()).isNull()
    assertThat(bus.getStickyEvent<OtherEvent>()).isNull()
  }

  @Test
  fun `MAIN - posted from main thread is delivered synchronously`() {
    val received = mutableListOf<Int>()
    bus.subscribe<Event>(threadMode = ThreadMode.MAIN) { received += it.value }

    bus.post(Event(1))

    assertThat(received).containsExactly(1)
  }

  @Test
  fun `MAIN - posted from a background thread is delivered on the main looper`() {
    val received = mutableListOf<Int>()
    bus.subscribe<Event>(threadMode = ThreadMode.MAIN) {
      received += it.value
      assertThat(Looper.myLooper()).isEqualTo(Looper.getMainLooper())
    }

    postFromBackgroundThread(Event(1))

    assertThat(received).isEmpty()
    shadowOf(Looper.getMainLooper()).idle()
    assertThat(received).containsExactly(1)
  }

  @Test
  fun `MAIN - unsubscribing before the main looper runs drops the event`() {
    val received = mutableListOf<Int>()
    val subscription = bus.subscribe<Event>(threadMode = ThreadMode.MAIN) { received += it.value }

    postFromBackgroundThread(Event(1))
    subscription.unsubscribe()
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(received).isEmpty()
  }

  @Test
  fun `POSTING - delivered on the posting thread`() {
    var deliveryThread: Thread? = null
    bus.subscribe<Event> { deliveryThread = Thread.currentThread() }

    val thread = Thread { bus.post(Event(1)) }
    thread.start()
    thread.join()

    assertThat(deliveryThread).isEqualTo(thread)
  }

  private fun postFromBackgroundThread(event: Any) {
    val latch = CountDownLatch(1)
    Thread {
      bus.post(event)
      latch.countDown()
    }.start()
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
  }
}
