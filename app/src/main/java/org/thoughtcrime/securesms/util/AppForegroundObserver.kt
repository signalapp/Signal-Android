package org.thoughtcrime.securesms.util

import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.signal.core.util.ThreadUtil
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.Volatile

/**
 * A wrapper around [ProcessLifecycleOwner] that allows for safely adding/removing observers
 * on multiple threads.
 */
object AppForegroundObserver {
  private val listeners: MutableSet<Listener> = CopyOnWriteArraySet()

  @Volatile
  private var isInitialized: Boolean = false

  @Volatile
  private var isForegrounded: Boolean = false

  @MainThread
  @JvmStatic
  fun begin() {
    ThreadUtil.assertMainThread()

    ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onStart(owner: LifecycleOwner) {
        onForeground()
      }

      override fun onStop(owner: LifecycleOwner) {
        onBackground()
      }
    })

    isInitialized = true
  }

  /**
   * Adds a listener to be notified of when the app moves between the background and the foreground.
   * To mimic the behavior of subscribing to [ProcessLifecycleOwner], this listener will be
   * immediately notified of the foreground state if we've experienced a foreground/background event
   * already.
   */
  @AnyThread
  @JvmStatic
  fun addListener(listener: Listener) {
    listeners.add(listener)

    if (isInitialized) {
      if (isForegrounded) {
        listener.onForeground()
      } else {
        listener.onBackground()
      }
    }
  }

  @AnyThread
  @JvmStatic
  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }

  @JvmStatic
  fun isForegrounded(): Boolean {
    return isInitialized && isForegrounded
  }

  @MainThread
  private fun onForeground() {
    isForegrounded = true

    for (listener in listeners) {
      listener.onForeground()
    }
  }

  @MainThread
  private fun onBackground() {
    isForegrounded = false

    for (listener in listeners) {
      listener.onBackground()
    }
  }

  interface Listener {
    fun onForeground() {}
    fun onBackground() {}
  }
}
