package org.thoughtcrime.securesms.loki

import android.content.Context
import android.os.Handler
import android.util.Log
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.PushDecryptJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.OutgoingTextMessage
import org.thoughtcrime.securesms.util.GroupUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceGroup
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.api.LokiGroupChat
import org.whispersystems.signalservice.loki.api.LokiGroupChatAPI
import org.whispersystems.signalservice.loki.api.LokiGroupMessage

class LokiGroupChatPoller(private val context: Context, private val group: LokiGroupChat) {
    private val handler = Handler()
    private var hasStarted = false

    // region Convenience
    private val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context)

    private val api: LokiGroupChatAPI
        get() = {
            val lokiAPIDatabase = DatabaseFactory.getLokiAPIDatabase(context)
            val lokiUserDatabase = DatabaseFactory.getLokiUserDatabase(context)
            val userPrivateKey = IdentityKeyUtil.getIdentityKeyPair(context).privateKey.serialize()
            LokiGroupChatAPI(userHexEncodedPublicKey, userPrivateKey, lokiAPIDatabase, lokiUserDatabase)
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

    private val pollForModerationPermissionTask = object : Runnable {

        override fun run() {
            pollForModerationPermission()
            handler.postDelayed(this, pollForModerationPermissionInterval)
        }
    }
    // endregion

    // region Settings
    companion object {
        private val pollForNewMessagesInterval: Long = 4 * 1000
        private val pollForDeletedMessagesInterval: Long = 20 * 1000
        private val pollForModerationPermissionInterval: Long = 10 * 60 * 1000
    }
    // endregion

    // region Lifecycle
    fun startIfNeeded() {
        if (hasStarted) return
        pollForNewMessagesTask.run()
        pollForDeletedMessagesTask.run()
        pollForModerationPermissionTask.run()
        hasStarted = true
    }

    fun stop() {
        handler.removeCallbacks(pollForNewMessagesTask)
        handler.removeCallbacks(pollForDeletedMessagesTask)
        handler.removeCallbacks(pollForModerationPermissionTask)
        hasStarted = false
    }
    // endregion

    // region Polling
    private fun pollForNewMessages() {
        fun processIncomingMessage(message: LokiGroupMessage) {
            val id = group.id.toByteArray()
            val x1 = SignalServiceGroup(SignalServiceGroup.Type.UPDATE, id, null, null, null)
            val x2 = SignalServiceDataMessage(message.timestamp, x1, null, message.body)
            val senderDisplayName = "${message.displayName} (...${message.hexEncodedPublicKey.takeLast(8)})"
            val x3 = SignalServiceContent(x2, senderDisplayName, SignalServiceAddress.DEFAULT_DEVICE_ID, message.timestamp, false)
            PushDecryptJob(context).handleTextMessage(x3, x2, Optional.absent(), Optional.of(message.serverID))
        }
        fun processOutgoingMessage(message: LokiGroupMessage) {
            val messageServerID = message.serverID ?: return
            val lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(context)
            val isDuplicate = lokiMessageDatabase.getMessageID(messageServerID) != null
            if (isDuplicate) { return }
            val id = group.id.toByteArray()
            val smsDatabase = DatabaseFactory.getSmsDatabase(context)
            val recipient = Recipient.from(context, Address.fromSerialized(GroupUtil.getEncodedId(id, false)), false)
            val signalMessage = OutgoingTextMessage(recipient, message.body, 0, 0)
            val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
            val messageID = smsDatabase.insertMessageOutbox(threadID, signalMessage, false, System.currentTimeMillis(), null)
            smsDatabase.markAsSent(messageID, true)
            smsDatabase.markUnidentified(messageID, false)
            lokiMessageDatabase.setServerID(messageID, messageServerID)
        }
        api.getMessages(group.serverID, group.server).success { messages ->
            messages.reversed().forEach { message ->
                if (message.hexEncodedPublicKey != userHexEncodedPublicKey) {
                    processIncomingMessage(message)
                } else {
                    processOutgoingMessage(message)
                }
            }
        }.fail {
            Log.d("Loki", "Failed to get messages for group chat with ID: ${group.serverID} on server: ${group.server}.")
        }
    }

    private fun pollForDeletedMessages() {
        api.getDeletedMessageServerIDs(group.serverID, group.server).success { deletedMessageServerIDs ->
            val lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(context)
            val deletedMessageIDs = deletedMessageServerIDs.mapNotNull { lokiMessageDatabase.getMessageID(it) }
            val smsMessageDatabase = DatabaseFactory.getSmsDatabase(context)
            val mmsMessageDatabase = DatabaseFactory.getMmsDatabase(context)
            deletedMessageIDs.forEach {
                smsMessageDatabase.deleteMessage(it)
                mmsMessageDatabase.delete(it)
            }
        }.fail {
            Log.d("Loki", "Failed to get deleted messages for group chat with ID: ${group.serverID} on server: ${group.server}.")
        }
    }

    private fun pollForModerationPermission() {
        api.userHasModerationPermission(group.serverID, group.server).success { isModerator ->
            val lokiAPIDatabase = DatabaseFactory.getLokiAPIDatabase(context)
            lokiAPIDatabase.setIsModerator(group.serverID, group.server, isModerator)
        }
    }
    // endregion
}