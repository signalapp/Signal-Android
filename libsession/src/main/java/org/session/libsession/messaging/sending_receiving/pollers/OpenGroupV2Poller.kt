package org.session.libsession.messaging.sending_receiving.pollers

import nl.komponents.kovenant.Promise
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.messaging.open_groups.OpenGroupMessageV2
import org.session.libsession.messaging.open_groups.OpenGroupV2
import org.session.libsession.messaging.threads.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.successBackground
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class OpenGroupV2Poller(private val openGroups: List<OpenGroupV2>, private val executorService: ScheduledExecutorService? = null) {

    private var hasStarted = false
    @Volatile private var isPollOngoing = false
    var isCaughtUp = false

    private val cancellableFutures = mutableListOf<ScheduledFuture<out Any>>()

    // use this as a receive time-based window to calculate re-poll interval
    private val receivedQueue = ArrayDeque<Long>(50)

    private fun calculatePollInterval(): Long {
        // sample last default poll time * 2
        while (receivedQueue.size > 50) {
            receivedQueue.removeLast()
        }
        val sampleWindow = System.currentTimeMillis() - pollForNewMessagesInterval * 2
        val numberInSample = receivedQueue.toList().filter { it > sampleWindow }.size.coerceAtLeast(1)
        return ((2 + (50 / numberInSample / 20)*5) * 1000).toLong()
    }

    // region Settings
    companion object {
        private val pollForNewMessagesInterval: Long = 10 * 1000
    }
    // endregion

    // region Lifecycle
    fun startIfNeeded() {
        if (hasStarted || executorService == null) return
        cancellableFutures += executorService.schedule(::compactPoll, 0, TimeUnit.MILLISECONDS)
        hasStarted = true
    }

    fun stop() {
        cancellableFutures.forEach { future ->
            future.cancel(false)
        }
        cancellableFutures.clear()
        hasStarted = false
    }
    // endregion

    // region Polling

    private fun compactPoll(): Promise<Any, Exception> {
        return compactPoll(false)
    }

    fun compactPoll(isBackgroundPoll: Boolean): Promise<Any, Exception> {
        if (isPollOngoing || !hasStarted) return Promise.of(Unit)
        isPollOngoing = true
        val server = openGroups.first().server // assume all the same server
        val rooms = openGroups.map { it.room }
        return OpenGroupAPIV2.compactPoll(rooms = rooms, server).successBackground { results ->
            results.forEach { (room, results) ->
                val serverRoomId = "$server.$room"
                handleNewMessages(serverRoomId, results.messages.sortedBy { it.serverID }, isBackgroundPoll)
                handleDeletedMessages(serverRoomId,results.deletions)
            }
        }.always {
            isPollOngoing = false
            if (!isBackgroundPoll) {
                val delay = calculatePollInterval()
                executorService?.schedule(this@OpenGroupV2Poller::compactPoll, delay, TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun handleNewMessages(serverRoomId: String, newMessages: List<OpenGroupMessageV2>, isBackgroundPoll: Boolean) {
        if (!hasStarted) return
        newMessages.forEach { message ->
            try {
                val senderPublicKey = message.sender!!
                // Main message
                // Envelope
                val builder = SignalServiceProtos.Envelope.newBuilder()
                builder.type = SignalServiceProtos.Envelope.Type.SESSION_MESSAGE
                builder.source = senderPublicKey
                builder.sourceDevice = 1
                builder.content = message.toProto().toByteString()
                builder.timestamp = message.sentTimestamp
                val envelope = builder.build()
                val job = MessageReceiveJob(envelope.toByteArray(), isBackgroundPoll, message.serverID, serverRoomId)
                Log.d("Loki", "Scheduling Job $job")
                if (isBackgroundPoll) {
                    job.executeAsync()
                    // The promise is just used to keep track of when we're done
                } else {
                    JobQueue.shared.add(job)
                }
                receivedQueue.addFirst(message.sentTimestamp)
            } catch (e: Exception) {
                Log.e("Loki", "Exception parsing message", e)
            }
        }
    }

    private fun handleDeletedMessages(serverRoomId: String, deletedMessageServerIDs: List<Long>) {
        val messagingModule = MessagingModuleConfiguration.shared
        val address = GroupUtil.getEncodedOpenGroupID(serverRoomId.toByteArray())
        val threadId = messagingModule.storage.getThreadIdFor(Address.fromSerialized(address)) ?: return

        val deletedMessageIDs = deletedMessageServerIDs.mapNotNull { serverId ->
            val id = messagingModule.messageDataProvider.getMessageID(serverId, threadId)
             if (id == null) {
                 Log.d("Loki", "Couldn't find server ID $serverId")
             }
            id
        }
        deletedMessageIDs.forEach { (messageId, isSms) ->
            MessagingModuleConfiguration.shared.messageDataProvider.deleteMessage(messageId, isSms)
        }
    }
    // endregion
}