package org.thoughtcrime.securesms.stories.viewer.reply

import java.lang.IllegalArgumentException

/**
 * Implemented by a Fragment who contains a view-pager.
 * Used to notify children when the selected child changes.
 */
interface StoryViewsAndRepliesPagerParent {
  val selectedChild: Child

  enum class Child {
    VIEWS,
    REPLIES;

    companion object {
      fun forIndex(index: Int): Child {
        return when (index) {
          0 -> VIEWS
          1 -> REPLIES
          else -> throw IllegalArgumentException()
        }
      }
    }
  }
}
