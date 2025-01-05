package org.thoughtcrime.securesms.conversationlist

import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel

data class ChatFolderMappingModel(
  val chatFolder: ChatFolderRecord,
  val isSelected: Boolean
) : MappingModel<ChatFolderMappingModel> {
  override fun areItemsTheSame(newItem: ChatFolderMappingModel): Boolean {
    return chatFolder.id == newItem.chatFolder.id
  }

  override fun areContentsTheSame(newItem: ChatFolderMappingModel): Boolean {
    return chatFolder == newItem.chatFolder && isSelected == newItem.isSelected
  }
}
