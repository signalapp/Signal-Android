package org.session.libsession.messaging.sending_receiving.pollers

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.messaging.open_groups.OpenGroupMessageV2
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.successBackground
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class OpenGroupPollerV2(private val server: String, private val executorService: ScheduledExecutorService?) {
    var hasStarted = false
    private var future: ScheduledFuture<*>? = null

    companion object {
        private val pollInterval: Long = 4 * 1000
    }

    fun startIfNeeded() {
        if (hasStarted) { return }
        hasStarted = true
        future = executorService?.schedule(::poll, 0, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        future?.cancel(false)
        hasStarted = false
    }

    fun poll(isBackgroundPoll: Boolean = false): Promise<Unit, Exception> {
        val storage = MessagingModuleConfiguration.shared.storage
        val rooms = storage.getAllV2OpenGroups().values.filter { it.server == server }.map { it.room }
        return OpenGroupAPIV2.compactPoll(rooms, server).successBackground { responses ->
            responses.forEach { (room, response) ->
                val openGroupID = "$server.$room"
                handleNewMessages(openGroupID, response.messages, isBackgroundPoll)
                handleDeletedMessages(openGroupID, response.deletions)
            }
        }.always {
            executorService?.schedule(this@OpenGroupPollerV2::poll, OpenGroupPollerV2.pollInterval, TimeUnit.MILLISECONDS)
        }.map { }
    }

    private fun handleNewMessages(openGroupID: String, messages: List<OpenGroupMessageV2>, isBackgroundPoll: Boolean) {
        if (!hasStarted) { return }
        messages.sortedBy { it.serverID!! }.forEach { message ->
            try {
                val senderPublicKey = message.sender!!
                val builder = SignalServiceProtos.Envelope.newBuilder()
                builder.type = SignalServiceProtos.Envelope.Type.SESSION_MESSAGE
                builder.source = senderPublicKey
                builder.sourceDevice = 1
                builder.content = message.toProto().toByteString()
                builder.timestamp = message.sentTimestamp
                val envelope = builder.build()
                val job = MessageReceiveJob(envelope.toByteArray(), message.serverID, openGroupID)
                if (isBackgroundPoll) {
                    job.executeAsync()
                } else {
                    JobQueue.shared.add(job)
                }
            } catch (e: Exception) {
                Log.e("Loki", "Exception parsing message", e)
            }
        }
    }

    private fun handleDeletedMessages(openGroupID: String, deletedMessageServerIDs: List<Long>) {
        val storage = MessagingModuleConfiguration.shared.storage
        val dataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        val groupID = GroupUtil.getEncodedOpenGroupID(openGroupID.toByteArray())
        val threadID = storage.getThreadIdFor(Address.fromSerialized(groupID)) ?: return
        val deletedMessageIDs = deletedMessageServerIDs.mapNotNull { serverID ->
            val messageID = dataProvider.getMessageID(serverID, threadID)
            if (messageID == null) {
                Log.d("Loki", "Couldn't find message ID for message with serverID: $serverID.")
            }
            messageID
        }
        deletedMessageIDs.forEach { (messageId, isSms) ->
            MessagingModuleConfiguration.shared.messageDataProvider.deleteMessage(messageId, isSms)
        }
    }
}