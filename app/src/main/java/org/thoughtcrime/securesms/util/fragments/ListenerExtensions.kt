package org.thoughtcrime.securesms.util.fragments

import androidx.fragment.app.Fragment
import java.lang.Exception

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

/**
 * Given an input type [T], find an instance of it first looking through all parents,
 * and then the activity.
 *
 * @return First instance found of type [T]
 * @throws ListenerNotFoundException with the hierarchy searched.
 *
 */
inline fun <reified T> Fragment.requireListener(): T {
  val hierarchy = mutableListOf<String>()
  try {
    var parent: Fragment? = parentFragment
    while (parent != null) {
      if (parent is T) {
        return parent
      }
      hierarchy.add(parent::class.java.name)
      parent = parent.parentFragment
    }

    return requireActivity() as T
  } catch (e: ClassCastException) {
    hierarchy.add(requireActivity()::class.java.name)
    throw ListenerNotFoundException(hierarchy, e)
  }
}

class ListenerNotFoundException(hierarchy: List<String>, cause: Throwable) : Exception(formatMessage(hierarchy), cause) {
  companion object {
    fun formatMessage(hierarchy: List<String>): String {
      return "Hierarchy Searched: \n${hierarchy.joinToString("\n")}"
    }
  }
}
