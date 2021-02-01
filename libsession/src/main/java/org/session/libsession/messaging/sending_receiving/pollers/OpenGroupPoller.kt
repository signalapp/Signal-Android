package org.session.libsession.messaging.sending_receiving.pollers

import android.os.Handler
import com.google.protobuf.ByteString

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.messaging.opengroups.OpenGroup
import org.session.libsession.messaging.opengroups.OpenGroupAPI
import org.session.libsession.messaging.opengroups.OpenGroupMessage

import org.session.libsignal.utilities.successBackground
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.internal.push.SignalServiceProtos.*

import java.util.*

class OpenGroupPoller(private val openGroup: OpenGroup) {
    private val handler by lazy { Handler() }
    private var hasStarted = false
    private var isPollOngoing = false
    public var isCaughtUp = false

    // region Convenience
    private val userHexEncodedPublicKey = MessagingConfiguration.shared.storage.getUserPublicKey() ?: ""
    private var displayNameUpdatees = setOf<String>()
    // endregion

    // region Tasks
    private val pollForNewMessagesTask = object : Runnable {

        override fun run() {
            pollForNewMessages()
            handler.postDelayed(this, pollForNewMessagesInterval)
        }
    }

    private val pollForDeletedMessagesTask = object : Runnable {

        override fun run() {
            pollForDeletedMessages()
            handler.postDelayed(this, pollForDeletedMessagesInterval)
        }
    }

    private val pollForModeratorsTask = object : Runnable {

        override fun run() {
            pollForModerators()
            handler.postDelayed(this, pollForModeratorsInterval)
        }
    }

    private val pollForDisplayNamesTask = object : Runnable {

        override fun run() {
            pollForDisplayNames()
            handler.postDelayed(this, pollForDisplayNamesInterval)
        }
    }
    // endregion

    // region Settings
    companion object {
        private val pollForNewMessagesInterval: Long = 4 * 1000
        private val pollForDeletedMessagesInterval: Long = 60 * 1000
        private val pollForModeratorsInterval: Long = 10 * 60 * 1000
        private val pollForDisplayNamesInterval: Long = 60 * 1000
    }
    // endregion

    // region Lifecycle
    fun startIfNeeded() {
        if (hasStarted) return
        pollForNewMessagesTask.run()
        pollForDeletedMessagesTask.run()
        pollForModeratorsTask.run()
        pollForDisplayNamesTask.run()
        hasStarted = true
    }

    fun stop() {
        handler.removeCallbacks(pollForNewMessagesTask)
        handler.removeCallbacks(pollForDeletedMessagesTask)
        handler.removeCallbacks(pollForModeratorsTask)
        handler.removeCallbacks(pollForDisplayNamesTask)
        hasStarted = false
    }
    // endregion

    // region Polling
    fun pollForNewMessages(): Promise<Unit, Exception> {
        return pollForNewMessages(false)
    }

    fun pollForNewMessages(isBackgroundPoll: Boolean): Promise<Unit, Exception> {
        if (isPollOngoing) { return Promise.of(Unit) }
        isPollOngoing = true
        val deferred = deferred<Unit, Exception>()
        // Kovenant propagates a context to chained promises, so OpenGroupAPI.sharedContext should be used for all of the below
        OpenGroupAPI.getMessages(openGroup.channel, openGroup.server).successBackground { messages ->
            // Process messages in the background
            messages.forEach { message ->
                val senderPublicKey = message.senderPublicKey
                val wasSentByCurrentUser = (senderPublicKey == userHexEncodedPublicKey)
                fun generateDisplayName(rawDisplayName: String): String {
                    return "${rawDisplayName} (${senderPublicKey.takeLast(8)})"
                }
                val senderDisplayName = MessagingConfiguration.shared.storage.getOpenGroupDisplayName(senderPublicKey, openGroup.channel, openGroup.server) ?: generateDisplayName("Anonymous")
                val id = openGroup.id.toByteArray()
                // Main message
                val dataMessageProto = DataMessage.newBuilder()
                val body = if (message.body == message.timestamp.toString()) { "" } else { message.body }
                dataMessageProto.setBody(body)
                dataMessageProto.setTimestamp(message.timestamp)
                // Attachments
                val attachmentProtos = message.attachments.mapNotNull { attachment ->
                    if (attachment.kind != OpenGroupMessage.Attachment.Kind.Attachment) { return@mapNotNull null }
                    val attachmentProto = AttachmentPointer.newBuilder()
                    attachmentProto.setId(attachment.serverID)
                    attachmentProto.setContentType(attachment.contentType)
                    attachmentProto.setSize(attachment.size)
                    attachmentProto.setFileName(attachment.fileName)
                    attachmentProto.setFlags(attachment.flags)
                    attachmentProto.setWidth(attachment.width)
                    attachmentProto.setHeight(attachment.height)
                    attachment.caption.let { attachmentProto.setCaption(it) }
                    attachmentProto.setUrl(attachment.url)
                    attachmentProto.build()
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
                    linkPreview.caption.let { attachmentProto.setCaption(it) }
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
                val profileProto = LokiUserProfile.newBuilder()
                profileProto.setDisplayName(message.displayName)
                val profilePicture = message.profilePicture
                if (profilePicture != null) {
                    profileProto.setProfilePictureURL(profilePicture.url)
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
                if (!wasSentByCurrentUser) { // Incoming message
                    content.setDataMessage(dataMessageProto.build())
                } else { // Outgoing message
                    // FIXME: This needs to be updated as we removed sync message handling
                    val syncMessageSentBuilder = SyncMessage.Sent.newBuilder()
                    syncMessageSentBuilder.setMessage(dataMessageProto)
                    syncMessageSentBuilder.setDestination(userHexEncodedPublicKey)
                    syncMessageSentBuilder.setTimestamp(message.timestamp)
                    val syncMessageSent = syncMessageSentBuilder.build()
                    val syncMessageBuilder = SyncMessage.newBuilder()
                    syncMessageBuilder.setSent(syncMessageSent)
                    content.setSyncMessage(syncMessageBuilder.build())
                }
                // Envelope
                val builder = Envelope.newBuilder()
                builder.type = Envelope.Type.UNIDENTIFIED_SENDER
                builder.source = senderPublicKey
                builder.sourceDevice = 1
                builder.setContent(content.build().toByteString())
                builder.serverTimestamp = message.serverTimestamp
                val envelope = builder.build()
                val job = MessageReceiveJob(envelope.toByteArray(), isBackgroundPoll, messageServerID, openGroup.id)
                if (isBackgroundPoll) {
                    job.executeAsync().success { deferred.resolve(Unit) }.fail { deferred.resolve(Unit) }
                    // The promise is just used to keep track of when we're done
                } else {
                    JobQueue.shared.add(job)
                    deferred.resolve(Unit)
                }
            }
            isCaughtUp = true
            isPollOngoing = false
        }.fail {
            Log.d("Loki", "Failed to get messages for group chat with ID: ${openGroup.channel} on server: ${openGroup.server}.")
            isPollOngoing = false
        }
        return deferred.promise
    }

    private fun pollForDisplayNames() {
        if (displayNameUpdatees.isEmpty()) { return }
        val hexEncodedPublicKeys = displayNameUpdatees
        displayNameUpdatees = setOf()
        OpenGroupAPI.getDisplayNames(hexEncodedPublicKeys, openGroup.server).successBackground { mapping ->
            for (pair in mapping.entries) {
                val senderDisplayName = "${pair.value} (...${pair.key.takeLast(8)})"
                MessagingConfiguration.shared.storage.setOpenGroupDisplayName(pair.key, openGroup.channel, openGroup.server, senderDisplayName)
            }
        }.fail {
            displayNameUpdatees = displayNameUpdatees.union(hexEncodedPublicKeys)
        }
    }

    private fun pollForDeletedMessages() {
        OpenGroupAPI.getDeletedMessageServerIDs(openGroup.channel, openGroup.server).success { deletedMessageServerIDs ->
            val deletedMessageIDs = deletedMessageServerIDs.mapNotNull { MessagingConfiguration.shared.messageDataProvider.getMessageID(it) }
            deletedMessageIDs.forEach {
                MessagingConfiguration.shared.messageDataProvider.deleteMessage(it)
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