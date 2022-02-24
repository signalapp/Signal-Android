package org.thoughtcrime.securesms.stories.landing

data class StoriesLandingState(
  val storiesLandingItems: List<StoriesLandingItemData> = emptyList(),
  val displayMyStoryItem: Boolean = false,
  val isHiddenContentVisible: Boolean = false
)
