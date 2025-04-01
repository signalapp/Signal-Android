package org.thoughtcrime.securesms.stories.tabs

import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.main.MainNavigationDestination
import org.thoughtcrime.securesms.stories.Stories

data class ConversationListTabsState(
  val tab: MainNavigationDestination = MainNavigationDestination.CHATS,
  val prevTab: MainNavigationDestination = if (tab == MainNavigationDestination.CHATS) MainNavigationDestination.STORIES else MainNavigationDestination.CHATS,
  val unreadMessagesCount: Long = 0L,
  val unreadCallsCount: Long = 0L,
  val unreadStoriesCount: Long = 0L,
  val hasFailedStory: Boolean = false,
  val visibilityState: VisibilityState = VisibilityState(),
  val compact: Boolean = SignalStore.settings.useCompactNavigationBar,
  val isStoriesFeatureEnabled: Boolean = Stories.isFeatureEnabled()
) {
  data class VisibilityState(
    val isSearchOpen: Boolean = false,
    val isMultiSelectOpen: Boolean = false,
    val isShowingArchived: Boolean = false
  ) {
    fun isVisible(): Boolean {
      return !isSearchOpen && !isMultiSelectOpen && !isShowingArchived
    }
  }
}
