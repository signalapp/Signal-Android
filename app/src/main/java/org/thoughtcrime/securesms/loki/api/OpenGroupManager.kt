package org.thoughtcrime.securesms.loki.api

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.WorkerThread
import okhttp3.HttpUrl
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.messaging.open_groups.OpenGroupV2
import org.session.libsession.messaging.sending_receiving.pollers.OpenGroupPollerV2
import org.session.libsession.utilities.Util
import org.session.libsignal.utilities.ThreadUtils
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.util.BitmapUtil
import java.util.concurrent.Executors

object OpenGroupManager {
    private val executorService = Executors.newScheduledThreadPool(4)
    private var pollers = mutableMapOf<String, OpenGroupPollerV2>() // One for each server
    private var isPolling = false

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
        val servers = storage.getAllV2OpenGroups().values.map { it.server }.toSet()
        servers.forEach { server ->
            pollers[server]?.stop() // Shouldn't be necessary
            val poller = OpenGroupPollerV2(server, executorService)
            poller.startIfNeeded()
            pollers[server] = poller
        }
    }

    fun stopPolling() {
        pollers.forEach { it.value.stop() }
        pollers.clear()
    }

    @WorkerThread
    fun add(server: String, room: String, publicKey: String, context: Context) {
        val openGroupID = "$server.$room"
        var threadID = GroupManager.getOpenGroupThreadID(openGroupID, context)
        val storage = MessagingModuleConfiguration.shared.storage
        val threadDB = DatabaseFactory.getLokiThreadDatabase(context)
        // Check it it's added already
        val existingOpenGroup = threadDB.getOpenGroupChat(threadID)
        if (existingOpenGroup != null) { return }
        // Clear any existing data if needed
        storage.removeLastDeletionServerID(room, server)
        storage.removeLastMessageServerID(room, server)
        // Store the public key
        storage.setOpenGroupPublicKey(server,publicKey)
        // Get an auth token
        OpenGroupAPIV2.getAuthToken(room, server).get()
        // Get group info
        val info = OpenGroupAPIV2.getInfo(room, server).get()
        // Download the group image
        // FIXME: Don't wait for the image to download
        val image: Bitmap?
        if (threadID < 0) {
            val profilePictureAsByteArray = try {
                OpenGroupAPIV2.downloadOpenGroupProfilePicture(info.id, server).get()
            } catch (e: Exception) {
                null
            }
            image = BitmapUtil.fromByteArray(profilePictureAsByteArray)
            // Create the group locally
            threadID = GroupManager.createOpenGroup(openGroupID, context, image, info.name).threadId
        }
        val openGroup = OpenGroupV2(server, room, info.name, publicKey)
        threadDB.setOpenGroupChat(openGroup, threadID)
        // Start the poller if needed
        pollers[server]?.startIfNeeded() ?: run {
            val poller = OpenGroupPollerV2(server, executorService)
            Util.runOnMain { poller.startIfNeeded() }
            pollers[server] = poller
        }
    }

    fun delete(server: String, room: String, context: Context) {
        val storage = MessagingModuleConfiguration.shared.storage
        val threadDB = DatabaseFactory.getThreadDatabase(context)
        val openGroupID = "$server.$room"
        val threadID = GroupManager.getOpenGroupThreadID(openGroupID, context)
        val recipient = threadDB.getRecipientForThreadId(threadID) ?: return
        val groupID = recipient.address.serialize()
        // Stop the poller if needed
        val openGroups = storage.getAllV2OpenGroups().filter { it.value.server == server }
        if (openGroups.count() == 1) {
            val poller = pollers[server]
            poller?.stop()
            pollers.remove(server)
        }
        // Delete
        storage.removeLastDeletionServerID(room, server)
        storage.removeLastMessageServerID(room, server)
        val lokiThreadDB = DatabaseFactory.getLokiThreadDatabase(context)
        lokiThreadDB.removeOpenGroupChat(threadID)
        ThreadUtils.queue {
            GroupManager.deleteGroup(groupID, context) // Must be invoked on a background thread
        }
    }

    fun addOpenGroup(urlAsString: String, context: Context) {
        val url = HttpUrl.parse(urlAsString) ?: return
        val builder = HttpUrl.Builder().scheme(url.scheme()).host(url.host())
        if (url.port() != 80 || url.port() != 443) {
            // Non-standard port; add to server
            builder.port(url.port())
        }
        val server = builder.build()
        val room = url.pathSegments().firstOrNull() ?: return
        val publicKey = url.queryParameter("public_key") ?: return
        add(server.toString().removeSuffix("/"), room, publicKey, context)
    }
}