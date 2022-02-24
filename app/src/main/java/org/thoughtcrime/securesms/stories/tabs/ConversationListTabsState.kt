package org.thoughtcrime.securesms.stories.tabs

data class ConversationListTabsState(
  val tab: ConversationListTab = ConversationListTab.CHATS,
  val unreadChatsCount: Long = 0L,
  val unreadStoriesCount: Long = 0L
)
