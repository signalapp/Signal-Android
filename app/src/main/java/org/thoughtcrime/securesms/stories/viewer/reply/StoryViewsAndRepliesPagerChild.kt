package org.thoughtcrime.securesms.stories.viewer.reply

/**
 * Implemented by a Fragment that may be the child of a view-pager.
 * Used to be notified of page selection changes.
 */
interface StoryViewsAndRepliesPagerChild {
  fun onPageSelected(child: StoryViewsAndRepliesPagerParent.Child)
}
