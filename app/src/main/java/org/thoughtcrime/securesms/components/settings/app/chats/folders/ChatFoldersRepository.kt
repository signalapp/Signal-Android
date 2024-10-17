package org.thoughtcrime.securesms.components.settings.app.chats.folders

import org.thoughtcrime.securesms.database.SignalDatabase

/**
 * Repository for chat folders that handles creation, deletion, listing, etc.,
 */
object ChatFoldersRepository {

  fun getCurrentFolders(includeUnreadCount: Boolean = false): List<ChatFolderRecord> {
    return SignalDatabase.chatFolders.getChatFolders(includeUnreadCount)
  }

  fun createFolder(folder: ChatFolderRecord) {
    val includedChats = folder.includedRecipients.map { recipient -> SignalDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val excludedChats = folder.excludedRecipients.map { recipient -> SignalDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val updatedFolder = folder.copy(
      includedChats = includedChats,
      excludedChats = excludedChats
    )

    SignalDatabase.chatFolders.createFolder(updatedFolder)
  }

  fun updateFolder(folder: ChatFolderRecord) {
    val includedChats = folder.includedRecipients.map { recipient -> SignalDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val excludedChats = folder.excludedRecipients.map { recipient -> SignalDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val updatedFolder = folder.copy(
      includedChats = includedChats,
      excludedChats = excludedChats
    )

    SignalDatabase.chatFolders.updateFolder(updatedFolder)
  }

  fun deleteFolder(folder: ChatFolderRecord) {
    SignalDatabase.chatFolders.deleteChatFolder(folder)
  }

  fun updatePositions(folders: List<ChatFolderRecord>) {
    SignalDatabase.chatFolders.updatePositions(folders)
  }

  fun getFolder(id: Long): ChatFolderRecord {
    return SignalDatabase.chatFolders.getChatFolder(id)
  }
}
