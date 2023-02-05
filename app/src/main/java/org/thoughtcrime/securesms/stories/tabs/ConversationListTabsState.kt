package org.thoughtcrime.securesms.stories.tabs

data class ConversationListTabsState(
  val tab: ConversationListTab = ConversationListTab.CHATS,
  val prevTab: ConversationListTab = ConversationListTab.STORIES,
  val unreadMessagesCount: Long = 0L,
  val unreadStoriesCount: Long = 0L,
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
