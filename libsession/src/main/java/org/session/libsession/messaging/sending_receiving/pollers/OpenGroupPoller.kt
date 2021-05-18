package org.session.libsession.messaging.sending_receiving.pollers

import com.google.protobuf.ByteString
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupAPI
import org.session.libsession.messaging.open_groups.OpenGroupMessage
import org.session.libsession.messaging.threads.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.protos.SignalServiceProtos.*
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.successBackground
import java.util.*
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class OpenGroupPoller(private val openGroup: OpenGroup, private val executorService: ScheduledExecutorService? = null) {

    private var hasStarted = false
    @Volatile private var isPollOngoing = false
    var isCaughtUp = false

    private val cancellableFutures = mutableListOf<ScheduledFuture<out Any>>()

    // region Convenience
    private val userHexEncodedPublicKey = MessagingModuleConfiguration.shared.storage.getUserPublicKey() ?: ""
    private var displayNameUpdates = setOf<String>()
    // endregion

    // region Settings
    companion object {
        private val pollForNewMessagesInterval: Long = 10 * 1000
        private val pollForDeletedMessagesInterval: Long = 60 * 1000
        private val pollForModeratorsInterval: Long = 10 * 60 * 1000
        private val pollForDisplayNamesInterval: Long = 60 * 1000
    }
    // endregion

    // region Lifecycle
    fun startIfNeeded() {
        if (hasStarted || executorService == null) return
        cancellableFutures += listOf(
            executorService.scheduleAtFixedRate(::pollForNewMessages,0, pollForNewMessagesInterval, TimeUnit.MILLISECONDS),
            executorService.scheduleAtFixedRate(::pollForDeletedMessages,0, pollForDeletedMessagesInterval, TimeUnit.MILLISECONDS),
            executorService.scheduleAtFixedRate(::pollForModerators,0, pollForModeratorsInterval, TimeUnit.MILLISECONDS),
            executorService.scheduleAtFixedRate(::pollForDisplayNames,0, pollForDisplayNamesInterval, TimeUnit.MILLISECONDS)
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
        return pollForNewMessagesInternal(false)
    }

    private fun pollForNewMessagesInternal(isBackgroundPoll: Boolean): Promise<Unit, Exception> {
        if (isPollOngoing) { return Promise.of(Unit) }
        isPollOngoing = true
        val deferred = deferred<Unit, Exception>()
        // Kovenant propagates a context to chained promises, so OpenGroupAPI.sharedContext should be used for all of the below
        OpenGroupAPI.getMessages(openGroup.channel, openGroup.server).successBackground { messages ->
            // Process messages in the background
            messages.forEach { message ->
                try {
                    val senderPublicKey = message.senderPublicKey
                    fun generateDisplayName(rawDisplayName: String): String {
                        return "$rawDisplayName (...${senderPublicKey.takeLast(8)})"
                    }
                    val senderDisplayName = MessagingModuleConfiguration.shared.storage.getOpenGroupDisplayName(senderPublicKey, openGroup.channel, openGroup.server) ?: generateDisplayName(message.displayName)
                    val id = openGroup.id.toByteArray()
                    // Main message
                    val dataMessageProto = DataMessage.newBuilder()
                    val body = if (message.body == message.timestamp.toString()) { "" } else { message.body }
                    dataMessageProto.setBody(body)
                    dataMessageProto.setTimestamp(message.timestamp)
                    // Attachments
                    val attachmentProtos = message.attachments.mapNotNull { attachment ->
                        try {
                            if (attachment.kind != OpenGroupMessage.Attachment.Kind.Attachment) { return@mapNotNull null }
                            val attachmentProto = AttachmentPointer.newBuilder()
                            attachmentProto.setId(attachment.serverID)
                            attachmentProto.setContentType(attachment.contentType)
                            attachmentProto.setSize(attachment.size)
                            attachmentProto.setFileName(attachment.fileName)
                            attachmentProto.setFlags(attachment.flags)
                            attachmentProto.setWidth(attachment.width)
                            attachmentProto.setHeight(attachment.height)
                            attachment.caption?.let { attachmentProto.setCaption(it) }
                            attachmentProto.setUrl(attachment.url)
                            attachmentProto.build()
                        } catch (e: Exception) {
                            Log.e("Loki","Failed to parse attachment as proto",e)
                            null
                        }
                    }
                    dataMessageProto.addAllAttachments(attachmentProtos)
                    // Link preview
                    val linkPreview = message.attachments.firstOrNull { it.kind == OpenGroupMessage.Attachment.Kind.LinkPreview }
                    if (linkPreview != null) {
                        val linkPreviewProto = DataMessage.Preview.newBuilder()
                        linkPreviewProto.setUrl(linkPreview.linkPreviewURL!!)
                        linkPreviewProto.setTitle(linkPreview.linkPreviewTitle!!)
                        val attachmentProto = AttachmentPointer.newBuilder()
                        attachmentProto.setId(linkPreview.serverID)
                        attachmentProto.setContentType(linkPreview.contentType)
                        attachmentProto.setSize(linkPreview.size)
                        attachmentProto.setFileName(linkPreview.fileName)
                        attachmentProto.setFlags(linkPreview.flags)
                        attachmentProto.setWidth(linkPreview.width)
                        attachmentProto.setHeight(linkPreview.height)
                        linkPreview.caption?.let { attachmentProto.setCaption(it) }
                        attachmentProto.setUrl(linkPreview.url)
                        linkPreviewProto.setImage(attachmentProto.build())
                        dataMessageProto.addPreview(linkPreviewProto.build())
                    }
                    // Quote
                    val quote = message.quote
                    if (quote != null) {
                        val quoteProto = DataMessage.Quote.newBuilder()
                        quoteProto.setId(quote.quotedMessageTimestamp)
                        quoteProto.setAuthor(quote.quoteePublicKey)
                        if (quote.quotedMessageBody != quote.quotedMessageTimestamp.toString()) { quoteProto.setText(quote.quotedMessageBody) }
                        dataMessageProto.setQuote(quoteProto.build())
                    }
                    val messageServerID = message.serverID
                    // Profile
                    val profileProto = DataMessage.LokiProfile.newBuilder()
                    profileProto.setDisplayName(senderDisplayName)
                    val profilePicture = message.profilePicture
                    if (profilePicture != null) {
                        profileProto.setProfilePicture(profilePicture.url)
                        dataMessageProto.setProfileKey(ByteString.copyFrom(profilePicture.profileKey))
                    }
                    dataMessageProto.setProfile(profileProto.build())
                    /* TODO: the signal service proto needs to be synced with iOS
                    // Open group info
                    if (messageServerID != null) {
                        val openGroupProto = PublicChatInfo.newBuilder()
                        openGroupProto.setServerID(messageServerID)
                        dataMessageProto.setPublicChatInfo(openGroupProto.build())
                    }
                    */
                    // Signal group context
                    val groupProto = GroupContext.newBuilder()
                    groupProto.setId(ByteString.copyFrom(id))
                    groupProto.setType(GroupContext.Type.DELIVER)
                    groupProto.setName(openGroup.displayName)
                    dataMessageProto.setGroup(groupProto.build())
                    // Content
                    val content = Content.newBuilder()
                    content.setDataMessage(dataMessageProto.build())
                    // Envelope
                    val builder = Envelope.newBuilder()
                    builder.type = Envelope.Type.SESSION_MESSAGE
                    builder.source = senderPublicKey
                    builder.sourceDevice = 1
                    builder.setContent(content.build().toByteString())
                    builder.timestamp = message.timestamp
                    builder.serverTimestamp = message.serverTimestamp
                    val envelope = builder.build()
                    val job = MessageReceiveJob(envelope.toByteArray(), isBackgroundPoll, messageServerID, openGroup.id)
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
            displayNameUpdates = displayNameUpdates + messages.map { it.senderPublicKey }.toSet() - userHexEncodedPublicKey
            executorService?.schedule(::pollForDisplayNames, 0, TimeUnit.MILLISECONDS)
            isCaughtUp = true
            isPollOngoing = false
            deferred.resolve(Unit)
        }.fail {
            Log.d("Loki", "Failed to get messages for group chat with ID: ${openGroup.channel} on server: ${openGroup.server}.")
            isPollOngoing = false
        }
        return deferred.promise
    }

    private fun pollForDisplayNames() {
        if (displayNameUpdates.isEmpty()) { return }
        val hexEncodedPublicKeys = displayNameUpdates
        displayNameUpdates = setOf()
        OpenGroupAPI.getDisplayNames(hexEncodedPublicKeys, openGroup.server).successBackground { mapping ->
            for (pair in mapping.entries) {
                if (pair.key == userHexEncodedPublicKey) continue
                val senderDisplayName = "${pair.value} (...${pair.key.substring(pair.key.count() - 8)})"
                MessagingModuleConfiguration.shared.storage.setOpenGroupDisplayName(pair.key, openGroup.channel, openGroup.server, senderDisplayName)
            }
        }.fail {
            displayNameUpdates = displayNameUpdates.union(hexEncodedPublicKeys)
        }
    }

    private fun pollForDeletedMessages() {
        val messagingModule = MessagingModuleConfiguration.shared
        val address = GroupUtil.getEncodedOpenGroupID(openGroup.id.toByteArray())
        val threadId = messagingModule.storage.getThreadIdFor(Address.fromSerialized(address)) ?: return
        OpenGroupAPI.getDeletedMessageServerIDs(openGroup.channel, openGroup.server).success { deletedMessageServerIDs ->
            val deletedMessageIDs = deletedMessageServerIDs.mapNotNull { messagingModule.messageDataProvider.getMessageID(it, threadId) }
            deletedMessageIDs.forEach { (messageId, isSms) ->
                MessagingModuleConfiguration.shared.messageDataProvider.deleteMessage(messageId, isSms)
            }
        }.fail {
            Log.d("Loki", "Failed to get deleted messages for group chat with ID: ${openGroup.channel} on server: ${openGroup.server}.")
        }
    }

    private fun pollForModerators() {
        OpenGroupAPI.getModerators(openGroup.channel, openGroup.server)
    }
    // endregion
}