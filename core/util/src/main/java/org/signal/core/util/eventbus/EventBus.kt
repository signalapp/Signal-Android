/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.eventbus

import org.signal.core.util.ThreadUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A minimal publish/subscribe event bus. Subscriptions are explicit function calls rather than annotated methods,
 * so there is no reflection and subscribers need no R8 keep rules.
 *
 * Events are matched by type, including supertypes: a subscriber for `Any` receives every event.
 */
class EventBus {

  companion object {
    @JvmStatic
    val default: EventBus = EventBus()
  }

  private val subscriptions = CopyOnWriteArrayList<Subscription>()
  private val stickyEvents = ConcurrentHashMap<Class<*>, Any>()

  /**
   * Subscribes [handler] to events of type [eventClass] until the returned [Subscription] is unsubscribed.
   */
  @JvmOverloads
  fun <T : Any> subscribe(eventClass: Class<T>, threadMode: ThreadMode = ThreadMode.POSTING, handler: EventHandler<T>): Subscription {
    return subscribe(eventClass, threadMode, sticky = false, handler)
  }

  inline fun <reified T : Any> subscribe(threadMode: ThreadMode = ThreadMode.POSTING, handler: EventHandler<T>): Subscription {
    return subscribe(T::class.java, threadMode, handler)
  }

  /**
   * Like [subscribe], but the most recent sticky event of a matching type (if any) is delivered immediately.
   */
  @JvmOverloads
  fun <T : Any> subscribeSticky(eventClass: Class<T>, threadMode: ThreadMode = ThreadMode.POSTING, handler: EventHandler<T>): Subscription {
    return subscribe(eventClass, threadMode, sticky = true, handler)
  }

  inline fun <reified T : Any> subscribeSticky(threadMode: ThreadMode = ThreadMode.POSTING, handler: EventHandler<T>): Subscription {
    return subscribeSticky(T::class.java, threadMode, handler)
  }

  private fun <T : Any> subscribe(eventClass: Class<T>, threadMode: ThreadMode, sticky: Boolean, handler: EventHandler<T>): Subscription {
    val subscription = Subscription(this, eventClass, threadMode, handler)
    subscriptions += subscription

    if (sticky) {
      stickyEvents.values.filter { eventClass.isInstance(it) }.forEach { subscription.deliver(it) }
    }

    return subscription
  }

  fun post(event: Any) {
    for (subscription in subscriptions) {
      if (subscription.eventClass.isInstance(event)) {
        subscription.deliver(event)
      }
    }
  }

  /** Posts [event] and retains it so that future sticky subscribers of its type receive it on subscribe. */
  fun postSticky(event: Any) {
    stickyEvents[event.javaClass] = event
    post(event)
  }

  fun <T : Any> getStickyEvent(eventClass: Class<T>): T? {
    return eventClass.cast(stickyEvents[eventClass])
  }

  inline fun <reified T : Any> getStickyEvent(): T? {
    return getStickyEvent(T::class.java)
  }

  fun <T : Any> removeStickyEvent(eventClass: Class<T>): T? {
    return eventClass.cast(stickyEvents.remove(eventClass))
  }

  inline fun <reified T : Any> removeStickyEvent(): T? {
    return removeStickyEvent(T::class.java)
  }

  /** Removes [event] only if it is the currently retained sticky event of its type. */
  fun removeStickyEvent(event: Any): Boolean {
    return stickyEvents.remove(event.javaClass, event)
  }

  fun removeAllStickyEvents() {
    stickyEvents.clear()
  }

  class Subscription internal constructor(
    private val bus: EventBus,
    internal val eventClass: Class<*>,
    private val threadMode: ThreadMode,
    private val handler: EventHandler<*>
  ) {
    @Volatile
    private var active = true

    fun unsubscribe() {
      active = false
      bus.subscriptions.remove(this)
    }

    internal fun deliver(event: Any) {
      when (threadMode) {
        ThreadMode.POSTING -> invoke(event)
        ThreadMode.MAIN -> {
          if (ThreadUtil.isMainThread()) {
            invoke(event)
          } else {
            ThreadUtil.postToMain {
              if (active) {
                invoke(event)
              }
            }
          }
        }
      }
    }

    @Suppress("UNCHECKED_CAST")
    private fun invoke(event: Any) {
      (handler as EventHandler<Any>).onEvent(event)
    }
  }
}
