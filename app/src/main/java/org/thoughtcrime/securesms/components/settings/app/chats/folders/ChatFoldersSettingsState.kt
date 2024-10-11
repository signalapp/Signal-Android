package org.thoughtcrime.securesms.components.settings.app.chats.folders

import org.thoughtcrime.securesms.contacts.paged.ChatType
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Information about chat folders. Used in [ChatFoldersViewModel].
 */
data class ChatFoldersSettingsState(
  val folders: List<ChatFolderRecord> = emptyList(),
  val suggestedFolders: List<ChatFolderRecord> = emptyList(),
  val originalFolder: ChatFolderRecord = ChatFolderRecord(),
  val currentFolder: ChatFolderRecord = ChatFolderRecord(),
  val showDeleteDialog: Boolean = false,
  val showConfirmationDialog: Boolean = false,
  val pendingIncludedRecipients: Set<RecipientId> = emptySet(),
  val pendingExcludedRecipients: Set<RecipientId> = emptySet(),
  val pendingChatTypes: Set<ChatType> = emptySet()
)
