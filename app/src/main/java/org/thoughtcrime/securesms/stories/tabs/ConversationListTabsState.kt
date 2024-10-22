package org.thoughtcrime.securesms.stories.tabs

data class ConversationListTabsState(
  val tab: ConversationListTab = ConversationListTab.CHATS,
  val prevTab: ConversationListTab = if (tab == ConversationListTab.CHATS) ConversationListTab.STORIES else ConversationListTab.CHATS,
  val unreadMessagesCount: Long = 0L,
  val unreadCallsCount: Long = 0L,
  val unreadStoriesCount: Long = 0L,
  val hasFailedStory: Boolean = false,
  val visibilityState: VisibilityState = VisibilityState()
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
