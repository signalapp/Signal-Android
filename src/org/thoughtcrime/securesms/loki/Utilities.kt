package org.thoughtcrime.securesms.loki

import android.content.Context
import android.os.Handler
import android.os.Looper
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.logging.Log
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.api.LokiPairingAuthorisation

fun sendAuthorisationMessage(context: Context, contactHexEncodedPublicKey: String, authorisation: LokiPairingAuthorisation): Promise<Unit, Exception> {
  val deferred = deferred<Unit, Exception>()
  Handler(Looper.getMainLooper()).post {
    val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
    val address = SignalServiceAddress(contactHexEncodedPublicKey)
    // A REQUEST should always act as a friend request. A GRANT should always be replying back as a normal message.
    val message = SignalServiceDataMessage.newBuilder().withBody("").withPairingAuthorisation(authorisation).asFriendRequest(authorisation.type == LokiPairingAuthorisation.Type.REQUEST).build()
    try {
      Log.d("Loki", "Sending authorisation message to $contactHexEncodedPublicKey")
      val result = messageSender.sendMessage(0, address, Optional.absent<UnidentifiedAccessPair>(), message)
      if (result.success == null) {
        val exception = when {
          result.isNetworkFailure -> "Failed to send authorisation message because of a Network Error"
          else -> "Failed to send authorisation message"
        }

        throw Exception(exception)
      }

      deferred.resolve(Unit)
    } catch (e: Exception) {
      Log.d("Loki", "Failed to send authorisation message to: $contactHexEncodedPublicKey.")
      deferred.reject(e)
    }
  }

  return deferred.promise
}