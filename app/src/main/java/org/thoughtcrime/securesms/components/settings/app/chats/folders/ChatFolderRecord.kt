package org.thoughtcrime.securesms.components.settings.app.chats.folders

import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Represents an entry in the [org.thoughtcrime.securesms.database.ChatFolderTables].
 */
data class ChatFolderRecord(
  val id: Long = -1,
  val name: String = "",
  val position: Int = -1,
  val includedChats: List<Long> = emptyList(),
  val excludedChats: List<Long> = emptyList(),
  val includedRecipients: Set<Recipient> = emptySet(),
  val excludedRecipients: Set<Recipient> = emptySet(),
  val showUnread: Boolean = false,
  val showMutedChats: Boolean = true,
  val showIndividualChats: Boolean = false,
  val showGroupChats: Boolean = false,
  val isMuted: Boolean = false,
  val folderType: FolderType = FolderType.CUSTOM,
  val unreadCount: Int = 0
) {
  enum class FolderType(val value: Int) {
    /** Folder containing all chats */
    ALL(0),

    /** Folder containing all 1:1 chats */
    INDIVIDUAL(1),

    /** Folder containing group chats */
    GROUP(2),

    /** Folder containing unread chats. */
    UNREAD(3),

    /** Folder containing custom chosen chats */
    CUSTOM(4);

    companion object {
      fun deserialize(value: Int): FolderType {
        return entries.firstOrNull { it.value == value } ?: CUSTOM
      }
    }
  }
}
