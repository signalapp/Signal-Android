package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.then
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.api.opengroups.PublicChat

object OpenGroupUtilities {

  @JvmStatic fun addGroup(context: Context, url: String, channel: Long): Promise<PublicChat, Exception> {
    // Check for an existing group
    val groupID = PublicChat.getId(channel, url)
    val threadID = GroupManager.getOpenGroupThreadID(groupID, context)
    val openGroup = DatabaseFactory.getLokiThreadDatabase(context).getPublicChat(threadID)
    if (openGroup != null) { return Promise.of(openGroup) }
    // Add the new group
    val application = ApplicationContext.getInstance(context)
    val displayName = TextSecurePreferences.getProfileName(context)
    val lokiPublicChatAPI = application.publicChatAPI ?: throw Error("LokiPublicChatAPI is not initialized.")
    return application.lokiPublicChatManager.addChat(url, channel).then { group ->
      DatabaseFactory.getLokiAPIDatabase(context).removeLastMessageServerID(channel, url)
      DatabaseFactory.getLokiAPIDatabase(context).removeLastDeletionServerID(channel, url)
      lokiPublicChatAPI.getMessages(channel, url)
      lokiPublicChatAPI.setDisplayName(displayName, url)
      lokiPublicChatAPI.join(channel, url)
      val profileKey: ByteArray = ProfileKeyUtil.getProfileKey(context)
      val profileUrl: String? = TextSecurePreferences.getProfilePictureURL(context)
      lokiPublicChatAPI.setProfilePicture(url, profileKey, profileUrl)
      group
    }
  }
}