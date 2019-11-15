package org.thoughtcrime.securesms.loki

import android.content.Context
import android.os.Handler
import android.util.Log
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.PushDecryptJob
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceGroup
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.api.LokiPublicChat
import org.whispersystems.signalservice.loki.api.LokiPublicChatAPI
import org.whispersystems.signalservice.loki.api.LokiPublicChatMessage
import org.whispersystems.signalservice.loki.api.LokiStorageAPI
import org.whispersystems.signalservice.loki.utilities.get
import org.whispersystems.signalservice.loki.utilities.successBackground
import java.util.*

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
    private fun getDataMessage(message: LokiPublicChatMessage): SignalServiceDataMessage {
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
        return SignalServiceDataMessage(message.timestamp, serviceGroup, attachments, body, false, 0, false, null, false, quote, null, signalLinkPreviews, null)
    }

    private fun pollForNewMessages() {
        fun processIncomingMessage(message: LokiPublicChatMessage) {
            val serviceDataMessage = getDataMessage(message)
            val serviceContent = SignalServiceContent(serviceDataMessage, message.hexEncodedPublicKey, SignalServiceAddress.DEFAULT_DEVICE_ID, message.timestamp, false)
            val senderDisplayName = "${message.displayName} (...${message.hexEncodedPublicKey.takeLast(8)})"
            DatabaseFactory.getLokiUserDatabase(context).setServerDisplayName(group.id, message.hexEncodedPublicKey, senderDisplayName)
            if (serviceDataMessage.quote.isPresent || (serviceDataMessage.attachments.isPresent && serviceDataMessage.attachments.get().size > 0)  || serviceDataMessage.previews.isPresent) {
                PushDecryptJob(context).handleMediaMessage(serviceContent, serviceDataMessage, Optional.absent(), Optional.of(message.serverID))
            } else {
                PushDecryptJob(context).handleTextMessage(serviceContent, serviceDataMessage, Optional.absent(), Optional.of(message.serverID))
            }
        }
        fun processOutgoingMessage(message: LokiPublicChatMessage) {
            val messageServerID = message.serverID ?: return
            val isDuplicate = DatabaseFactory.getLokiMessageDatabase(context).getMessageID(messageServerID) != null
            if (isDuplicate) { return }
            if (message.body.isEmpty() && message.attachments.isEmpty() && message.quote == null) { return }
            val localNumber = TextSecurePreferences.getLocalNumber(context)
            val dataMessage = getDataMessage(message)
            val transcript = SentTranscriptMessage(localNumber, dataMessage.timestamp, dataMessage, dataMessage.expiresInSeconds.toLong(), Collections.singletonMap(localNumber, false))
            transcript.messageServerID = messageServerID
            if (dataMessage.quote.isPresent || (dataMessage.attachments.isPresent && dataMessage.attachments.get().size > 0)  || dataMessage.previews.isPresent) {
                PushDecryptJob(context).handleSynchronizeSentMediaMessage(transcript)
            } else {
                PushDecryptJob(context).handleSynchronizeSentTextMessage(transcript)
            }
        }
        api.getMessages(group.channel, group.server).successBackground { messages ->
            if (messages.isNotEmpty()) {
                val ourDevices = LokiStorageAPI.shared.getAllDevicePublicKeys(userHexEncodedPublicKey).get(setOf())
                // Process messages in the background
                messages.forEach { message ->
                    if (ourDevices.contains(message.hexEncodedPublicKey)) {
                        processOutgoingMessage(message)
                    } else {
                        processIncomingMessage(message)
                    }
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