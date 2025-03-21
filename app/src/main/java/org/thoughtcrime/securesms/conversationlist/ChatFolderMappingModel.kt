package org.thoughtcrime.securesms.conversationlist

import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel

/**
 * Mapping model of folders used in [ChatFolderAdapter]
 */
data class ChatFolderMappingModel(
  val chatFolder: ChatFolderRecord,
  val unreadCount: Int,
  val isMuted: Boolean,
  val isSelected: Boolean
) : MappingModel<ChatFolderMappingModel> {
  override fun areItemsTheSame(newItem: ChatFolderMappingModel): Boolean {
    return chatFolder.id == newItem.chatFolder.id
  }

  override fun areContentsTheSame(newItem: ChatFolderMappingModel): Boolean {
    return chatFolder == newItem.chatFolder &&
      unreadCount == newItem.unreadCount &&
      isMuted == newItem.isMuted &&
      isSelected == newItem.isSelected
  }
}
