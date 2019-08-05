package org.thoughtcrime.securesms.loki

import android.content.Context
import android.util.Log
import org.thoughtcrime.securesms.jobs.PushDecryptJob
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceGroup
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.api.LokiGroupChatAPI

object LokiGroupChatPoller {

    @JvmStatic
    fun poll(context: Context, groupID: Long) {
        LokiGroupChatAPI.getMessages(groupID).success { messages ->
            messages.map { message ->
                val id = "loki-group-chat-$groupID".toByteArray()
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