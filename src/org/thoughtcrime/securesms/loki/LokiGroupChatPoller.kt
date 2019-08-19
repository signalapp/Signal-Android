package org.thoughtcrime.securesms.loki

import android.content.Context
import android.os.Handler
import android.util.Log
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.PushDecryptJob
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceGroup
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.api.LokiGroupChatAPI

class LokiGroupChatPoller(private val context: Context, private val groupID: Long) {
    private val handler = Handler()
    private var hasStarted = false

    private val task = object : Runnable {

        override fun run() {
            poll()
            handler.postDelayed(this, pollInterval)
        }
    }

    companion object {
        private val pollInterval: Long = 4 * 1000
    }

    fun startIfNeeded() {
        if (hasStarted) return
        task.run()
        hasStarted = true
    }

    fun stop() {
        handler.removeCallbacks(task)
    }

    private fun poll() {
        val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context)
        val lokiUserDatabase = DatabaseFactory.getLokiUserDatabase(context)
        LokiGroupChatAPI(userHexEncodedPublicKey, lokiUserDatabase).getMessages(groupID).success { messages ->
            messages.reversed().map { message ->
                val id = "${LokiGroupChatAPI.serverURL}.$groupID".toByteArray()
                val x1 = SignalServiceGroup(SignalServiceGroup.Type.UPDATE, id, null, null, null)
                val x2 = SignalServiceDataMessage(message.timestamp, x1, null, message.body)
                val senderDisplayName = "${message.displayName} (...${message.hexEncodedPublicKey.takeLast(8)})"
                val x3 = SignalServiceContent(x2, senderDisplayName, SignalServiceAddress.DEFAULT_DEVICE_ID, message.timestamp, false)
                PushDecryptJob(context).handleTextMessage(x3, x2, Optional.absent())
            }
        }.fail {
            Log.d("Loki", "Failed to get messages for group chat with ID: $groupID.")
        }
    }
}