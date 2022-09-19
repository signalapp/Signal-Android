package org.thoughtcrime.securesms.components

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import kotlin.reflect.KProperty

/**
 * ViewBinderDelegate which enforces the "best practices" for maintaining a reference to a view binding given by
 * Android official documentation.
 */
class ViewBinderDelegate<T : ViewBinding>(private val bindingFactory: (View) -> T) : DefaultLifecycleObserver {

  private var binding: T? = null

  operator fun getValue(thisRef: Fragment, property: KProperty<*>): T {
    if (binding == null) {
      thisRef.viewLifecycleOwner.lifecycle.addObserver(this@ViewBinderDelegate)
      binding = bindingFactory(thisRef.requireView())
    }

    return binding!!
  }

  override fun onDestroy(owner: LifecycleOwner) {
    binding = null
  }
}
