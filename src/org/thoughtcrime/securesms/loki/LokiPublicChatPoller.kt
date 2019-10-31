package org.thoughtcrime.securesms.loki

import android.content.Context
import android.os.Handler
import android.util.Log
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.jobs.PushDecryptJob
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.GroupUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceGroup
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.api.LokiPublicChat
import org.whispersystems.signalservice.loki.api.LokiPublicChatAPI
import org.whispersystems.signalservice.loki.api.LokiPublicChatMessage
import org.whispersystems.signalservice.loki.utilities.successBackground

class LokiPublicChatPoller(private val context: Context, private val group: LokiPublicChat) {
    private val handler = Handler()
    private var hasStarted = false

    // region Convenience
    private val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context)

    private val api: LokiPublicChatAPI
        get() = {
            val userPrivateKey = IdentityKeyUtil.getIdentityKeyPair(context).privateKey.serialize()
            val lokiAPIDatabase = DatabaseFactory.getLokiAPIDatabase(context)
            val lokiUserDatabase = DatabaseFactory.getLokiUserDatabase(context)
            LokiPublicChatAPI(userHexEncodedPublicKey, userPrivateKey, lokiAPIDatabase, lokiUserDatabase)
        }()
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
    // endregion

    // region Settings
    companion object {
        private val pollForNewMessagesInterval: Long = 4 * 1000
        private val pollForDeletedMessagesInterval: Long = 20 * 1000
        private val pollForModeratorsInterval: Long = 10 * 60 * 1000
    }
    // endregion

    // region Lifecycle
    fun startIfNeeded() {
        if (hasStarted) return
        pollForNewMessagesTask.run()
        pollForDeletedMessagesTask.run()
        pollForModeratorsTask.run()
        hasStarted = true
    }

    fun stop() {
        handler.removeCallbacks(pollForNewMessagesTask)
        handler.removeCallbacks(pollForDeletedMessagesTask)
        handler.removeCallbacks(pollForModeratorsTask)
        hasStarted = false
    }
    // endregion

    // region Polling
    private fun pollForNewMessages() {
        fun processIncomingMessage(message: LokiPublicChatMessage) {
            val id = group.id.toByteArray()
            val serviceGroup = SignalServiceGroup(SignalServiceGroup.Type.UPDATE, id, null, null, null)
            val quote = if (message.quote != null) {
                SignalServiceDataMessage.Quote(message.quote!!.quotedMessageTimestamp, SignalServiceAddress(message.quote!!.quoteeHexEncodedPublicKey), message.quote!!.quotedMessageBody, listOf())
            } else {
                null
            }
            val attachments = message.attachments.mapNotNull { attachment ->
                if (attachment.kind != LokiPublicChatMessage.Attachment.Kind.Attachment) { return@mapNotNull null }
                SignalServiceAttachmentPointer(
                    attachment.serverID,
                    attachment.contentType,
                    ByteArray(0),
                    Optional.of(attachment.size),
                    Optional.absent(),
                    attachment.width, attachment.height,
                    Optional.absent(),
                    Optional.of(attachment.fileName),
                    false,
                    Optional.fromNullable(attachment.caption),
                    attachment.url)
            }
            val linkPreview = message.attachments.firstOrNull { it.kind == LokiPublicChatMessage.Attachment.Kind.LinkPreview }
            val signalLinkPreviews = mutableListOf<SignalServiceDataMessage.Preview>()
            if (linkPreview != null) {
                val attachment = SignalServiceAttachmentPointer(
                        linkPreview.serverID,
                        linkPreview.contentType,
                        ByteArray(0),
                        Optional.of(linkPreview.size),
                        Optional.absent(),
                        linkPreview.width, linkPreview.height,
                        Optional.absent(),
                        Optional.of(linkPreview.fileName),
                        false,
                        Optional.fromNullable(linkPreview.caption),
                        linkPreview.url)
                signalLinkPreviews.add(SignalServiceDataMessage.Preview(linkPreview.linkPreviewURL!!, linkPreview.linkPreviewTitle!!, Optional.of(attachment)))
            }
            val body = if (message.body == message.timestamp.toString()) "" else message.body // Workaround for the fact that the back-end doesn't accept messages without a body
            val serviceDataMessage = SignalServiceDataMessage(message.timestamp, serviceGroup, attachments, body, false, 0, false, null, false, quote, null, signalLinkPreviews, null)
            val serviceContent = SignalServiceContent(serviceDataMessage, message.hexEncodedPublicKey, SignalServiceAddress.DEFAULT_DEVICE_ID, message.timestamp, false)
            val senderDisplayName = "${message.displayName} (...${message.hexEncodedPublicKey.takeLast(8)})"
            DatabaseFactory.getLokiUserDatabase(context).setServerDisplayName(group.id, message.hexEncodedPublicKey, senderDisplayName)
            if (quote != null || attachments.count() > 0 || linkPreview != null) {
                PushDecryptJob(context).handleMediaMessage(serviceContent, serviceDataMessage, Optional.absent(), Optional.of(message.serverID))
            } else {
                PushDecryptJob(context).handleTextMessage(serviceContent, serviceDataMessage, Optional.absent(), Optional.of(message.serverID))
            }
        }
        fun processOutgoingMessage(message: LokiPublicChatMessage) {
            val messageServerID = message.serverID ?: return
            val lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(context)
            val isDuplicate = lokiMessageDatabase.getMessageID(messageServerID) != null
            if (isDuplicate) { return }
            if (message.body.isEmpty() && message.attachments.isEmpty() && message.quote == null) { return }
            val id = group.id.toByteArray()
            val mmsDatabase = DatabaseFactory.getMmsDatabase(context)
            val recipient = Recipient.from(context, Address.fromSerialized(GroupUtil.getEncodedId(id, false)), false)
            val quote: QuoteModel?
            if (message.quote != null) {
                quote = QuoteModel(message.quote!!.quotedMessageTimestamp, Address.fromSerialized(message.quote!!.quoteeHexEncodedPublicKey), message.quote!!.quotedMessageBody, false, listOf())
            } else {
                quote = null
            }
            // TODO: Handle attachments correctly for our previous messages
            val body = if (message.body == message.timestamp.toString()) "" else message.body // Workaround for the fact that the back-end doesn't accept messages without a body
            val signalMessage = OutgoingMediaMessage(recipient, body, listOf(), message.timestamp, 0, 0,
               ThreadDatabase.DistributionTypes.DEFAULT, quote, listOf(), listOf(), listOf(), listOf())
            val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
            fun finalize() {
                val messageID = mmsDatabase.insertMessageOutbox(signalMessage, threadID, false, null)
                mmsDatabase.markAsSent(messageID, true)
                mmsDatabase.markUnidentified(messageID, false)
                lokiMessageDatabase.setServerID(messageID, messageServerID)
            }
            val urls = LinkPreviewUtil.findWhitelistedUrls(message.body)
            val urlCount = urls.size
            if (urlCount != 0) {
                val lpr = LinkPreviewRepository(context)
                var count = 0
                urls.forEach { url ->
                    lpr.getLinkPreview(context, url.url) { lp ->
                        Util.runOnMain {
                            count += 1
                            if (lp.isPresent) { signalMessage.linkPreviews.add(lp.get()) }
                            if (count == urlCount) {
                                try {
                                    finalize()
                                } catch (e: Exception) {
                                    // TODO: Handle
                                }

                            }
                        }
                    }
                }
            } else {
                finalize()
            }
        }
        api.getMessages(group.channel, group.server).successBackground { messages ->
            // Process messages in the background
            messages.forEach { message ->
                if (message.hexEncodedPublicKey != userHexEncodedPublicKey) {
                    processIncomingMessage(message)
                } else {
                    processOutgoingMessage(message)
                }
            }
        }.fail {
            Log.d("Loki", "Failed to get messages for group chat with ID: ${group.channel} on server: ${group.server}.")
        }
    }

    private fun pollForDeletedMessages() {
        api.getDeletedMessageServerIDs(group.channel, group.server).success { deletedMessageServerIDs ->
            val lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(context)
            val deletedMessageIDs = deletedMessageServerIDs.mapNotNull { lokiMessageDatabase.getMessageID(it) }
            val smsMessageDatabase = DatabaseFactory.getSmsDatabase(context)
            val mmsMessageDatabase = DatabaseFactory.getMmsDatabase(context)
            deletedMessageIDs.forEach {
                smsMessageDatabase.deleteMessage(it)
                mmsMessageDatabase.delete(it)
            }
        }.fail {
            Log.d("Loki", "Failed to get deleted messages for group chat with ID: ${group.channel} on server: ${group.server}.")
        }
    }

    private fun pollForModerators() {
        api.getModerators(group.channel, group.server)
    }
    // endregion
}