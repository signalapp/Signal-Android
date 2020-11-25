package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import androidx.annotation.WorkerThread
import org.greenrobot.eventbus.EventBus
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.util.GroupUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.api.opengroups.PublicChat
import java.lang.Exception
import java.lang.IllegalStateException
import kotlin.jvm.Throws

//TODO Refactor so methods declare specific type of checked exceptions and not generalized Exception.
object OpenGroupUtilities {

    private const val TAG = "OpenGroupUtilities"

    @JvmStatic
    @WorkerThread
    @Throws(Exception::class)
    fun addGroup(context: Context, url: String, channel: Long): PublicChat {
        // Check for an existing group.
        val groupID = PublicChat.getId(channel, url)
        val threadID = GroupManager.getOpenGroupThreadID(groupID, context)
        val openGroup = DatabaseFactory.getLokiThreadDatabase(context).getPublicChat(threadID)
        if (openGroup != null) return openGroup

        // Add the new group.
        val application = ApplicationContext.getInstance(context)
        val displayName = TextSecurePreferences.getProfileName(context)
        val lokiPublicChatAPI = application.publicChatAPI
                ?: throw IllegalStateException("LokiPublicChatAPI is not initialized.")

        val group = application.publicChatManager.addChat(url, channel)

        DatabaseFactory.getLokiAPIDatabase(context).removeLastMessageServerID(channel, url)
        DatabaseFactory.getLokiAPIDatabase(context).removeLastDeletionServerID(channel, url)
        lokiPublicChatAPI.getMessages(channel, url)
        lokiPublicChatAPI.setDisplayName(displayName, url)
        lokiPublicChatAPI.join(channel, url)
        val profileKey: ByteArray = ProfileKeyUtil.getProfileKey(context)
        val profileUrl: String? = TextSecurePreferences.getProfilePictureURL(context)
        lokiPublicChatAPI.setProfilePicture(url, profileKey, profileUrl)
        return group
    }

    /**
     * Pulls the general public chat data from the server and updates related records.
     * Fires [GroupInfoUpdatedEvent] on [EventBus] upon success.
     *
     * Consider using [org.thoughtcrime.securesms.loki.api.PublicChatInfoUpdateWorker] for lazy approach.
     */
    @JvmStatic
    @WorkerThread
    @Throws(Exception::class)
    fun updateGroupInfo(context: Context, url: String, channel: Long) {
        val publicChatAPI = ApplicationContext.getInstance(context).publicChatAPI
                ?: throw IllegalStateException("Public chat API is not initialized!")

        // Check if open group has a related DB record.
        val groupId = GroupUtil.getEncodedOpenGroupId(PublicChat.getId(channel, url).toByteArray())
        if (!DatabaseFactory.getGroupDatabase(context).hasGroup(groupId)) {
            throw IllegalStateException("Attempt to update open group info for non-existent DB record: $groupId")
        }

        val info = publicChatAPI.getChannelInfo(channel, url).get()

        publicChatAPI.updateProfileIfNeeded(channel, url, groupId, info, false)

        EventBus.getDefault().post(GroupInfoUpdatedEvent(url, channel))
    }

    data class GroupInfoUpdatedEvent(val url: String, val channel: Long)
}