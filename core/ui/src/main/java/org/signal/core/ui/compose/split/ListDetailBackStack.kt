/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Describes a location that would sit in the "List" portion of a list-detail scaffold.
 */
interface ListNavKey : NavKey

/**
 * Describes a location that would sit in the "Detail" portion of a list-detail scaffold.
 */
interface DetailNavKey : NavKey {
  val isContentRoot: Boolean
}

/**
 * A backstack for a list-detail scaffold. Allows us to decorate the type with a bunch of helpful extensions.
 */
typealias ListDetailBackStack = NavBackStack<NavKey>

/**
 * Index of the list location currently being displayed. Everything above it is detail content.
 */
@PublishedApi
internal val ListDetailBackStack.listIndex: Int
  get() = indexOfLast { it is ListNavKey }

/**
 * The list location currently being displayed, which is the last one on the stack, as [L]. Throws if the
 * displayed list is not an [L].
 */
inline fun <reified L : ListNavKey> ListDetailBackStack.listLocation(): L {
  return this[listIndex] as L
}

/**
 * The detail content displayed above the current list as [D], or null when the list is showing on its own.
 */
inline fun <reified D : DetailNavKey> ListDetailBackStack.detailLocation(): D? {
  return lastOrNull() as? D
}

/**
 * Whether detail content is displayed above the current list.
 */
val ListDetailBackStack.hasDetail: Boolean
  get() = listIndex < lastIndex

/**
 * Pushes [location] into the place its kind belongs
 */
fun ListDetailBackStack.push(location: NavKey) {
  when (location) {
    lastOrNull() -> Unit
    is ListNavKey -> {
      if (this[listIndex] != location) {
        add(listIndex + 1, location)
      }
    }

    is DetailNavKey if location.isContentRoot -> {
      exitDetail()
      add(location)
    }

    else -> add(location)
  }
}

/**
 * Drops the list locations stacked above [location] so that it becomes the displayed list, leaving the
 * detail content above them in place. Does nothing if [location] is not on the stack.
 */
internal fun ListDetailBackStack.popToList(location: ListNavKey) {
  if (!contains(location)) {
    return
  }

  while (this[listIndex] != location) {
    removeAt(listIndex)
  }
}

/**
 * Pops the top entry, whether that is detail content or a pushed list. Returns false at the root; an
 * empty stack cannot be displayed.
 */
internal fun ListDetailBackStack.pop(): Boolean {
  if (size <= 1) {
    return false
  }

  removeAt(lastIndex)
  return true
}

/**
 * Drops everything above the current list location, leaving that list displayed with no detail. Any
 * pushed list beneath it stays.
 */
fun ListDetailBackStack.exitDetail() {
  while (size > listIndex + 1) {
    removeAt(lastIndex)
  }
}
