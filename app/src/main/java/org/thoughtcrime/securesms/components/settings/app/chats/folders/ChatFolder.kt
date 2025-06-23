package org.thoughtcrime.securesms.components.settings.app.chats.folders

import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Information needed when creating/updating a chat folder. Used in [ChatFoldersSettingsState]
 */
data class ChatFolder(
  val folderRecord: ChatFolderRecord = ChatFolderRecord(),
  val includedRecipients: Set<Recipient> = emptySet(),
  val excludedRecipients: Set<Recipient> = emptySet()
)
