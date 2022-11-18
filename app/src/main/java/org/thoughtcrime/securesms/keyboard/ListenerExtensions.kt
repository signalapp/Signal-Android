package org.thoughtcrime.securesms.keyboard

import androidx.fragment.app.Fragment

/**
 * Given an input type [T], find an instance of it first looking through all
 * parents, and then the activity.
 *
 * @return First instance found of type [T] or null
 */
inline fun <reified T> Fragment.findListener(): T? {
  var parent: Fragment? = parentFragment
  while (parent != null) {
    if (parent is T) {
      return parent
    }
    parent = parent.parentFragment
  }

  return requireActivity() as? T
}
