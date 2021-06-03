package org.session.libsession.messaging.sending_receiving.pollers

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.messaging.jobs.TrimThreadJob
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
import kotlin.math.max

class OpenGroupPollerV2(private val server: String, private val executorService: ScheduledExecutorService?) {
    var hasStarted = false
    var isCaughtUp = false
    var secondToLastJob: MessageReceiveJob? = null
    private var future: ScheduledFuture<*>? = null

    companion object {
        private val pollInterval: Long = 4 * 1000
        const val maxInactivityPeriod = 14 * 24 * 60 * 60 * 1000
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
                handleNewMessages(room, openGroupID, response.messages, isBackgroundPoll)
                handleDeletedMessages(room, openGroupID, response.deletions)
                if (secondToLastJob == null && !isCaughtUp) {
                    isCaughtUp = true
                }
            }
        }.always {
            executorService?.schedule(this@OpenGroupPollerV2::poll, OpenGroupPollerV2.pollInterval, TimeUnit.MILLISECONDS)
        }.map { }
    }

    private fun handleNewMessages(room: String, openGroupID: String, messages: List<OpenGroupMessageV2>, isBackgroundPoll: Boolean) {
        val storage = MessagingModuleConfiguration.shared.storage
        val groupID = GroupUtil.getEncodedOpenGroupID(openGroupID.toByteArray())
        // check thread still exists
        val threadId = storage.getThreadId(Address.fromSerialized(groupID)) ?: -1
        val threadExists = threadId >= 0
        if (!hasStarted || !threadExists) { return }
        var latestJob: MessageReceiveJob? = null
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
                    if (!isCaughtUp) {
                        secondToLastJob = latestJob
                    }
                    latestJob = job
                }
            } catch (e: Exception) {
                Log.e("Loki", "Exception parsing message", e)
            }
        }
        val currentLastMessageServerID = storage.getLastMessageServerID(room, server) ?: 0
        val actualMax = max(messages.mapNotNull { it.serverID }.maxOrNull() ?: 0, currentLastMessageServerID)
        if (actualMax > 0) {
            storage.setLastMessageServerID(room, server, actualMax)
        }
        JobQueue.shared.add(TrimThreadJob(threadId))
    }

    private fun handleDeletedMessages(room: String, openGroupID: String, deletions: List<OpenGroupAPIV2.MessageDeletion>) {
        val storage = MessagingModuleConfiguration.shared.storage
        val dataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        val groupID = GroupUtil.getEncodedOpenGroupID(openGroupID.toByteArray())
        val threadID = storage.getThreadId(Address.fromSerialized(groupID)) ?: return
        val deletedMessageIDs = deletions.mapNotNull { deletion ->
            val messageID = dataProvider.getMessageID(deletion.deletedMessageServerID, threadID)
            if (messageID == null) {
                Log.d("Loki", "Couldn't find message ID for message with serverID: ${deletion.deletedMessageServerID}.")
            }
            messageID
        }
        deletedMessageIDs.forEach { (messageId, isSms) ->
            MessagingModuleConfiguration.shared.messageDataProvider.deleteMessage(messageId, isSms)
        }
        val currentMax = storage.getLastDeletionServerID(room, server) ?: 0L
        val latestMax = deletions.map { it.id }.maxOrNull() ?: 0L
        if (latestMax > currentMax && latestMax != 0L) {
            storage.setLastDeletionServerID(room, server, latestMax)
        }
    }
}