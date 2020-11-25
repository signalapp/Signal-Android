package org.thoughtcrime.securesms.loki.api

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.api.messages.SignalServiceEnvelope
import org.session.libsignal.service.internal.util.Base64
import org.session.libsignal.service.loki.api.MessageWrapper

class PushNotificationService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("Loki", "New FCM token: $token.")
        val userPublicKey = TextSecurePreferences.getLocalNumber(this) ?: return
        LokiPushNotificationManager.register(token, userPublicKey, this, false)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("Loki", "Received a push notification.")
        val base64EncodedData = message.data?.get("ENCRYPTED_DATA")
        val data = base64EncodedData?.let { Base64.decode(it) }
        if (data != null) {
            try {
                val envelope = MessageWrapper.unwrap(data)
                PushContentReceiveJob(this).processEnvelope(SignalServiceEnvelope(envelope), true)
            } catch (e: Exception) {
                Log.d("Loki", "Failed to unwrap data for message due to error: $e.")
            }
        } else {
            Log.d("Loki", "Failed to decode data for message.")
            val builder = NotificationCompat.Builder(this, NotificationChannels.OTHER)
                    .setSmallIcon(network.loki.messenger.R.drawable.ic_notification)
                    .setColor(this.getResources().getColor(network.loki.messenger.R.color.textsecure_primary))
                    .setContentTitle("Session")
                    .setContentText("You've got a new message.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
            with(NotificationManagerCompat.from(this)) {
                notify(11111, builder.build())
            }
        }
    }

    override fun onDeletedMessages() {
        org.thoughtcrime.securesms.logging.Log.d("Loki", "Called onDeletedMessages.")
        super.onDeletedMessages()
        val token = TextSecurePreferences.getFCMToken(this)
        val userPublicKey = TextSecurePreferences.getLocalNumber(this) ?: return
        LokiPushNotificationManager.register(token, userPublicKey, this, true)
    }
}