package org.thoughtcrime.securesms.stories.viewer.views

import org.thoughtcrime.securesms.recipients.Recipient

data class StoryViewsState(
  val loadState: LoadState = LoadState.INIT,
  val storyRecipient: Recipient? = null,
  val views: List<StoryViewItemData> = emptyList()
) {
  enum class LoadState {
    INIT,
    READY,
    DISABLED
  }
}
