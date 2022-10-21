package org.thoughtcrime.securesms.components

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import kotlin.reflect.KProperty

/**
 * ViewBinderDelegate which enforces the "best practices" for maintaining a reference to a view binding given by
 * Android official documentation.
 */
open class ViewBinderDelegate<T : ViewBinding>(
  private val bindingFactory: (View) -> T,
  private val onBindingWillBeDestroyed: (T) -> Unit = {}
) : DefaultLifecycleObserver {

  private var binding: T? = null

  operator fun getValue(thisRef: Fragment, property: KProperty<*>): T {
    if (binding == null) {
      if (!thisRef.viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
        error("Invalid state to create a binding.")
      }

      thisRef.viewLifecycleOwner.lifecycle.addObserver(this@ViewBinderDelegate)
      binding = bindingFactory(thisRef.requireView())
    }

    return binding!!
  }

  override fun onDestroy(owner: LifecycleOwner) {
    if (binding != null) {
      onBindingWillBeDestroyed(binding!!)
    }

    binding = null
  }
}
