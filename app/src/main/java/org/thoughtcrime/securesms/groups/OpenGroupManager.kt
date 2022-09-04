package org.thoughtcrime.securesms.groups

import android.content.Context
import androidx.annotation.WorkerThread
import okhttp3.HttpUrl
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.open_groups.GroupMemberRole
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.sending_receiving.pollers.OpenGroupPoller
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.ThreadUtils
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import java.util.concurrent.Executors

object OpenGroupManager {
    private val executorService = Executors.newScheduledThreadPool(4)
    private var pollers = mutableMapOf<String, OpenGroupPoller>() // One for each server
    private var isPolling = false
    private val pollUpdaterLock = Any()

    val isAllCaughtUp: Boolean
        get() {
            pollers.values.forEach { poller ->
                val jobID = poller.secondToLastJob?.id
                jobID?.let {
                    val storage = MessagingModuleConfiguration.shared.storage
                    if (storage.getMessageReceiveJob(jobID) == null) {
                        // If the second to last job is done, it means we are now handling the last job
                        poller.isCaughtUp = true
                        poller.secondToLastJob = null
                    }
                }
                if (!poller.isCaughtUp) { return false }
            }
            return true
        }

    fun startPolling() {
        if (isPolling) { return }
        isPolling = true
        val storage = MessagingModuleConfiguration.shared.storage
        val servers = storage.getAllOpenGroups().values.map { it.server }.toSet()
        servers.forEach { server ->
            pollers[server]?.stop() // Shouldn't be necessary
            val poller = OpenGroupPoller(server, executorService)
            poller.startIfNeeded()
            pollers[server] = poller
        }
    }

    fun stopPolling() {
        synchronized(pollUpdaterLock) {
            pollers.forEach { it.value.stop() }
            pollers.clear()
            isPolling = false
        }
    }

    @WorkerThread
    fun add(server: String, room: String, publicKey: String, context: Context) {
        val openGroupID = "$server.$room"
        var threadID = GroupManager.getOpenGroupThreadID(openGroupID, context)
        val storage = MessagingModuleConfiguration.shared.storage
        val threadDB = DatabaseComponent.get(context).lokiThreadDatabase()
        // Check it it's added already
        val existingOpenGroup = threadDB.getOpenGroupChat(threadID)
        if (existingOpenGroup != null) { return }
        // Clear any existing data if needed
        storage.removeLastDeletionServerID(room, server)
        storage.removeLastMessageServerID(room, server)
        storage.removeLastInboxMessageId(server)
        storage.removeLastOutboxMessageId(server)
        // Store the public key
        storage.setOpenGroupPublicKey(server, publicKey)
        // Get capabilities
        val capabilities = OpenGroupApi.getCapabilities(server).get()
        storage.setServerCapabilities(server, capabilities.capabilities)
        // Get room info
        val info = OpenGroupApi.getRoomInfo(room, server).get()
        storage.setUserCount(room, server, info.activeUsers)
        // Create the group locally if not available already
        if (threadID < 0) {
            threadID = GroupManager.createOpenGroup(openGroupID, context, null, info.name).threadId
        }
        val openGroup = OpenGroup(server, room, info.name, info.infoUpdates, publicKey)
        threadDB.setOpenGroupChat(openGroup, threadID)
    }

    fun restartPollerForServer(server: String) {
        // Start the poller if needed
        synchronized(pollUpdaterLock) {
            pollers[server]?.stop()
            pollers[server]?.startIfNeeded() ?: run {
                val poller = OpenGroupPoller(server, executorService)
                Log.d("Loki", "Starting poller for open group: $server")
                pollers[server] = poller
                poller.startIfNeeded()
            }
        }
    }

    fun delete(server: String, room: String, context: Context) {
        val storage = MessagingModuleConfiguration.shared.storage
        val threadDB = DatabaseComponent.get(context).threadDatabase()
        val openGroupID = "$server.$room"
        val threadID = GroupManager.getOpenGroupThreadID(openGroupID, context)
        val recipient = threadDB.getRecipientForThreadId(threadID) ?: return
        threadDB.setThreadArchived(threadID)
        val groupID = recipient.address.serialize()
        // Stop the poller if needed
        val openGroups = storage.getAllOpenGroups().filter { it.value.server == server }
        if (openGroups.count() == 1) {
            synchronized(pollUpdaterLock) {
                val poller = pollers[server]
                poller?.stop()
                pollers.remove(server)
            }
        }
        // Delete
        storage.removeLastDeletionServerID(room, server)
        storage.removeLastMessageServerID(room, server)
        storage.removeLastInboxMessageId(server)
        storage.removeLastOutboxMessageId(server)
        val lokiThreadDB = DatabaseComponent.get(context).lokiThreadDatabase()
        lokiThreadDB.removeOpenGroupChat(threadID)
        ThreadUtils.queue {
            threadDB.deleteConversation(threadID) // Must be invoked on a background thread
            GroupManager.deleteGroup(groupID, context) // Must be invoked on a background thread
        }
    }

    fun addOpenGroup(urlAsString: String, context: Context) {
        val url = HttpUrl.parse(urlAsString) ?: return
        val server = OpenGroup.getServer(urlAsString)
        val room = url.pathSegments().firstOrNull() ?: return
        val publicKey = url.queryParameter("public_key") ?: return
        add(server.toString().removeSuffix("/"), room, publicKey, context) // assume migrated from calling function
    }

    fun updateOpenGroup(openGroup: OpenGroup, context: Context) {
        val threadDB = DatabaseComponent.get(context).lokiThreadDatabase()
        val openGroupID = "${openGroup.server}.${openGroup.room}"
        val threadID = GroupManager.getOpenGroupThreadID(openGroupID, context)
        threadDB.setOpenGroupChat(openGroup, threadID)
    }

    fun isUserModerator(context: Context, groupId: String, standardPublicKey: String, blindedPublicKey: String? = null): Boolean {
        val memberDatabase = DatabaseComponent.get(context).groupMemberDatabase()
        val standardRoles = memberDatabase.getGroupMemberRoles(groupId, standardPublicKey)
        val blindedRoles = blindedPublicKey?.let { memberDatabase.getGroupMemberRoles(groupId, it) } ?: emptyList()

        // roles to check against
        val moderatorRoles = listOf(
            GroupMemberRole.MODERATOR, GroupMemberRole.ADMIN,
            GroupMemberRole.HIDDEN_MODERATOR, GroupMemberRole.HIDDEN_ADMIN
        )
        return standardRoles.any { it in moderatorRoles } || blindedRoles.any { it in moderatorRoles }
    }

}