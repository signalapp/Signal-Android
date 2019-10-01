package org.thoughtcrime.securesms.loki

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.logging.Log
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.api.LokiPairingAuthorisation

fun sendAuthorisationMessage(context: Context, contactHexEncodedPublicKey: String, authorisation: LokiPairingAuthorisation) {
  Handler(Looper.getMainLooper()).post {
    val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
    val address = SignalServiceAddress(contactHexEncodedPublicKey)
    val message = SignalServiceDataMessage.newBuilder().withBody("").withPairingAuthorisation(authorisation).build()
    try {
      messageSender.sendMessage(0, address, Optional.absent<UnidentifiedAccessPair>(), message) // The message ID doesn't matter
    } catch (e: Exception) {
      Log.d("Loki", "Failed to send authorisation message to: $contactHexEncodedPublicKey.")
    }
  }
}