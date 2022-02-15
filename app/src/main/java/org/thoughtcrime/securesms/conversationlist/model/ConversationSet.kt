package org.thoughtcrime.securesms.conversationlist.model

class ConversationSet @JvmOverloads constructor(
  private val conversations: Set<Conversation> = emptySet()
) : Set<Conversation> by conversations {

  private val threadIds by lazy {
    conversations.map { it.threadRecord.threadId }
  }

  fun containsThreadId(id: Long): Boolean {
    return id in threadIds
  }
}
