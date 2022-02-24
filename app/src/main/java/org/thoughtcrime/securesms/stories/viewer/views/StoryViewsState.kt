package org.thoughtcrime.securesms.stories.viewer.views

data class StoryViewsState(
  val loadState: LoadState = LoadState.INIT,
  val views: List<StoryViewItemData> = emptyList()
) {
  enum class LoadState {
    INIT,
    READY
  }
}
