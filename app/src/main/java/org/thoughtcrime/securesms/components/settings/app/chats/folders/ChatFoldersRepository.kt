package org.thoughtcrime.securesms.components.settings.app.chats.folders

import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * Repository for chat folders that handles creation, deletion, listing, etc.,
 */
object ChatFoldersRepository {

  fun getCurrentFolders(): List<ChatFolderRecord> {
    return SignalDatabase.chatFolders.getCurrentChatFolders()
  }

  fun getUnreadCountAndMutedStatusForFolders(folders: List<ChatFolderRecord>): HashMap<Long, Pair<Int, Boolean>> {
    return SignalDatabase.chatFolders.getUnreadCountAndMutedStatusForFolders(folders)
  }

  fun createFolder(folder: ChatFolderRecord, includedRecipients: Set<Recipient>, excludedRecipients: Set<Recipient>) {
    val includedChats = includedRecipients.map { recipient -> SignalDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val excludedChats = excludedRecipients.map { recipient -> SignalDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val updatedFolder = folder.copy(
      includedChats = includedChats,
      excludedChats = excludedChats
    )

    SignalDatabase.chatFolders.createFolder(updatedFolder)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  fun updateFolder(folder: ChatFolderRecord, includedRecipients: Set<Recipient>, excludedRecipients: Set<Recipient>) {
    val includedChats = includedRecipients.map { recipient -> SignalDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val excludedChats = excludedRecipients.map { recipient -> SignalDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val updatedFolder = folder.copy(
      includedChats = includedChats,
      excludedChats = excludedChats
    )

    SignalDatabase.chatFolders.updateFolder(updatedFolder)
    scheduleSync(updatedFolder.id)
  }

  fun deleteFolder(folder: ChatFolderRecord) {
    SignalDatabase.chatFolders.deleteChatFolder(folder)
    scheduleSync(folder.id)
  }

  fun updatePositions(folders: List<ChatFolderRecord>) {
    SignalDatabase.chatFolders.updatePositions(folders)
    folders.forEach { scheduleSync(it.id) }
  }

  fun getFolder(id: Long): ChatFolderRecord {
    return SignalDatabase.chatFolders.getChatFolder(id)!!
  }

  fun getFolderCount(): Int {
    return SignalDatabase.chatFolders.getFolderCount()
  }

  private fun scheduleSync(id: Long) {
    SignalDatabase.chatFolders.markNeedsSync(id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }
}
