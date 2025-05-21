package org.thoughtcrime.securesms.logsubmit

import android.content.Context
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Prints out chat folders settings
 */
class LogSectionChatFolders : LogSection {
  override fun getTitle(): String = "CHAT FOLDERS"

  override fun getContent(context: Context): CharSequence {
    val output = StringBuilder()

    if (Recipient.isSelfSet) {
      val count = SignalDatabase.chatFolders.getFolderCount()
      val hasDefault = SignalDatabase.chatFolders.getCurrentChatFolders().any { folder -> folder.folderType == ChatFolderRecord.FolderType.ALL }
      output.append("Has default all chats         : ${hasDefault}\n")
      output.append("Number of folders (undeleted) : ${count}\n")
    } else {
      output.append("< Self is not set yet >\n")
    }

    return output
  }
}
