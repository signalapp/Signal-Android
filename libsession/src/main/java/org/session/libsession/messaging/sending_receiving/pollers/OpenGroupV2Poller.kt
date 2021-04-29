package org.session.libsession.messaging.sending_receiving.pollers

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.messaging.open_groups.OpenGroupV2
import org.session.libsession.messaging.threads.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.database.LokiMessageDatabaseProtocol
import org.session.libsignal.utilities.logging.Log
import org.session.libsignal.utilities.successBackground
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class OpenGroupV2Poller(private val openGroup: OpenGroupV2, private val executorService: ScheduledExecutorService? = null) {

    private var hasStarted = false
    @Volatile private var isPollOngoing = false
    var isCaughtUp = false

    private val cancellableFutures = mutableListOf<ScheduledFuture<out Any>>()

    // region Settings
    companion object {
        private val pollForNewMessagesInterval: Long = 10 * 1000
        private val pollForDeletedMessagesInterval: Long = 60 * 1000
        private val pollForModeratorsInterval: Long = 10 * 60 * 1000
    }
    // endregion

    // region Lifecycle
    fun startIfNeeded() {
        if (hasStarted || executorService == null) return
        cancellableFutures += listOf(
                executorService.scheduleAtFixedRate(::pollForNewMessages,0, pollForNewMessagesInterval, TimeUnit.MILLISECONDS),
                executorService.scheduleAtFixedRate(::pollForDeletedMessages,0, pollForDeletedMessagesInterval, TimeUnit.MILLISECONDS),
                executorService.scheduleAtFixedRate(::pollForModerators,0, pollForModeratorsInterval, TimeUnit.MILLISECONDS),
        )
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
    fun pollForNewMessages(): Promise<Unit, Exception> {
        return pollForNewMessages(false)
    }

    private fun pollForNewMessages(isBackgroundPoll: Boolean): Promise<Unit, Exception> {
        if (isPollOngoing) { return Promise.of(Unit) }
        isPollOngoing = true
        val deferred = deferred<Unit, Exception>()
        // Kovenant propagates a context to chained promises, so OpenGroupAPI.sharedContext should be used for all of the below
        OpenGroupAPIV2.getMessages(openGroup.room, openGroup.server).successBackground { messages ->
            // Process messages in the background
            messages.forEach { message ->
                try {
                    val senderPublicKey = message.sender!!
                    // Main message
                    val dataMessageProto = message.toProto()
                    // Content
                    val content = SignalServiceProtos.Content.newBuilder()
                    content.dataMessage = dataMessageProto
                    // Envelope
                    val builder = SignalServiceProtos.Envelope.newBuilder()
                    builder.type = SignalServiceProtos.Envelope.Type.SESSION_MESSAGE
                    builder.source = senderPublicKey
                    builder.sourceDevice = 1
                    builder.content = content.build().toByteString()
                    builder.timestamp = message.sentTimestamp
                    val envelope = builder.build()
                    val job = MessageReceiveJob(envelope.toByteArray(), isBackgroundPoll, message.serverID, openGroup.id)
                    Log.d("Loki", "Scheduling Job $job")
                    if (isBackgroundPoll) {
                        job.executeAsync().always { deferred.resolve(Unit) }
                        // The promise is just used to keep track of when we're done
                    } else {
                        JobQueue.shared.add(job)
                    }
                } catch (e: Exception) {
                    Log.e("Loki", "Exception parsing message", e)
                }
            }
            isCaughtUp = true
            isPollOngoing = false
            deferred.resolve(Unit)
        }.fail {
            Log.e("Loki", "Failed to get messages for group chat with room: ${openGroup.room} on server: ${openGroup.server}.", it)
            isPollOngoing = false
        }
        return deferred.promise
    }

    private fun pollForDeletedMessages() {
        val messagingModule = MessagingModuleConfiguration.shared
        val address = GroupUtil.getEncodedOpenGroupID(openGroup.id.toByteArray())
        val threadId = messagingModule.storage.getThreadIdFor(Address.fromSerialized(address)) ?: return

        OpenGroupAPIV2.getDeletedMessages(openGroup.room, openGroup.server).success { deletedMessageServerIDs ->

            val deletedMessageIDs = deletedMessageServerIDs.mapNotNull { serverId ->
                messagingModule.messageDataProvider.getMessageID(serverId.deletedMessageId, threadId)
            }
            deletedMessageIDs.forEach { (messageId, isSms) ->
                MessagingModuleConfiguration.shared.messageDataProvider.deleteMessage(messageId, isSms)
            }
        }.fail {
            Log.d("Loki", "Failed to get deleted messages for group chat with ID: ${openGroup.room} on server: ${openGroup.server}.")
        }
    }

    private fun pollForModerators() {
        OpenGroupAPIV2.getModerators(openGroup.room, openGroup.server)
    }
    // endregion
}