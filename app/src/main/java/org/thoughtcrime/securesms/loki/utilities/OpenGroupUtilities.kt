package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import androidx.annotation.WorkerThread
import org.greenrobot.eventbus.EventBus
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupAPI
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.messaging.open_groups.OpenGroupV2
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.ProfileKeyUtil
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.groups.GroupManager
import java.util.*

//TODO Refactor so methods declare specific type of checked exceptions and not generalized Exception.
object OpenGroupUtilities {

    private const val TAG = "OpenGroupUtilities"

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
        // Check if open group has a related DB record.
        val groupId = GroupUtil.getEncodedOpenGroupID(OpenGroup.getId(channel, url).toByteArray())
        if (!DatabaseFactory.getGroupDatabase(context).hasGroup(groupId)) {
            throw IllegalStateException("Attempt to update open group info for non-existent DB record: $groupId")
        }

        val info = OpenGroupAPI.getChannelInfo(channel, url).get()

        OpenGroupAPI.updateProfileIfNeeded(channel, url, groupId, info, false)

        EventBus.getDefault().post(GroupInfoUpdatedEvent(url, channel))
    }

    @JvmStatic
    @WorkerThread
    @Throws(Exception::class)
    fun updateGroupInfo(context: Context, server: String, room: String) {
        val groupId = GroupUtil.getEncodedOpenGroupID("$server.$room".toByteArray())
        if (!DatabaseFactory.getGroupDatabase(context).hasGroup(groupId)) {
            throw IllegalStateException("Attempt to update open group info for non-existent DB record: $groupId")
        }

        val info = OpenGroupAPIV2.getInfo(room, server).get() // store info again?
        OpenGroupAPIV2.getMemberCount(room, server).get()

        EventBus.getDefault().post(GroupInfoUpdatedEvent(server, room = room))
    }

    /**
     * Return a generated name for users in the style of `$name (...$hex.takeLast(8))` for public groups
     */
    @JvmStatic
    fun getDisplayName(recipient: Recipient): String {
        return String.format(Locale.ROOT, PUBLIC_GROUP_STRING_FORMAT, recipient.name, recipient.address.serialize().takeLast(8))
    }

    const val PUBLIC_GROUP_STRING_FORMAT = "%s (...%s)"

    data class GroupInfoUpdatedEvent(val url: String, val channel: Long = -1, val room: String = "")
}