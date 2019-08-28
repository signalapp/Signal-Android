package org.thoughtcrime.securesms.loki

import android.content.Context
import android.os.Handler
import android.util.Log
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.PushDecryptJob
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceGroup
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.api.LokiGroupChat
import org.whispersystems.signalservice.loki.api.LokiGroupChatAPI

class LokiGroupChatPoller(private val context: Context, private val group: LokiGroupChat) {
    private val handler = Handler()
    private var hasStarted = false

    private val api: LokiGroupChatAPI
        get() = {
            val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context)
            val lokiAPIDatabase = DatabaseFactory.getLokiAPIDatabase(context)
            val lokiUserDatabase = DatabaseFactory.getLokiUserDatabase(context)
            val userPrivateKey = IdentityKeyUtil.getIdentityKeyPair(context).privateKey.serialize()
            LokiGroupChatAPI(userHexEncodedPublicKey, userPrivateKey, lokiAPIDatabase, lokiUserDatabase)
        }()

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

    companion object {
        private val pollForNewMessagesInterval: Long = 4 * 1000
        private val pollForDeletedMessagesInterval: Long = 32 * 60 * 1000
    }

    fun startIfNeeded() {
        if (hasStarted) return
        pollForNewMessagesTask.run()
        pollForDeletedMessagesTask.run()
        hasStarted = true
    }

    fun stop() {
        handler.removeCallbacks(pollForNewMessagesTask)
        handler.removeCallbacks(pollForDeletedMessagesTask)
        hasStarted = false
    }

    private fun pollForNewMessages() {
        api.getMessages(group.serverID, group.server).success { messages ->
            messages.reversed().map { message ->
                val id = group.id.toByteArray()
                val x1 = SignalServiceGroup(SignalServiceGroup.Type.UPDATE, id, null, null, null)
                val x2 = SignalServiceDataMessage(message.timestamp, x1, null, message.body)
                val senderDisplayName = "${message.displayName} (...${message.hexEncodedPublicKey.takeLast(8)})"
                val x3 = SignalServiceContent(x2, senderDisplayName, SignalServiceAddress.DEFAULT_DEVICE_ID, message.timestamp, false)
                PushDecryptJob(context).handleTextMessage(x3, x2, Optional.absent())
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
}