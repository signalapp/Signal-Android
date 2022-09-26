package org.thoughtcrime.securesms.stories.landing

data class StoriesLandingState(
  val storiesLandingItems: List<StoriesLandingItemData> = emptyList(),
  val displayMyStoryItem: Boolean = false,
  val isHiddenContentVisible: Boolean = false,
  val loadingState: LoadingState = LoadingState.INIT,
  val searchQuery: String = ""
) {
  enum class LoadingState {
    INIT,
    LOADED
  }

  val hasNoStories: Boolean = loadingState == LoadingState.LOADED && storiesLandingItems.isEmpty()
}
