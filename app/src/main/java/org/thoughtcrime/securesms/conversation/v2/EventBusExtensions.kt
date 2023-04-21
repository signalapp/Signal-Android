package org.thoughtcrime.securesms.conversation.v2

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.logging.Log

/**
 * Set up a lifecycle aware register/deregister for the lifecycleowner.
 */
fun EventBus.registerForLifecycle(subscriber: Any, lifecycleOwner: LifecycleOwner) {
  val registration = LifecycleAwareRegistration(subscriber, this)
  lifecycleOwner.lifecycle.addObserver(registration)
}

private class LifecycleAwareRegistration(
  private val subscriber: Any,
  private val bus: EventBus
) : DefaultLifecycleObserver {

  companion object {
    private val TAG = Log.tag(LifecycleAwareRegistration::class.java)
  }

  override fun onResume(owner: LifecycleOwner) {
    Log.d(TAG, "Registering owner.")
    bus.register(subscriber)
  }

  override fun onPause(owner: LifecycleOwner) {
    Log.d(TAG, "Unregistering owner.")
    bus.unregister(subscriber)
  }
}
