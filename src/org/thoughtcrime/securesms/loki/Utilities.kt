package org.thoughtcrime.securesms.loki

import android.content.Context
import android.os.Handler
import android.os.Looper
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.api.LokiPairingAuthorisation
import org.whispersystems.signalservice.loki.api.LokiStorageAPI
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus

fun shouldAutomaticallyBecomeFriendsWithDevice(pubKey: String, context: Context): Promise<Boolean, Unit> {
  val lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(context)
  val storageAPI = LokiStorageAPI.shared ?: return Promise.ofSuccess(false)
  // we need to check if the sender is a secondary device.
  // If it is then we need to check if we have its primary device as our friend
  // If so then we add them automatically as a friend

  val deferred = deferred<Boolean, Unit>()
  storageAPI.getPrimaryDevice(pubKey).success { primaryDevicePubKey ->
    // Make sure we have a primary device
    if (primaryDevicePubKey == null) {
      deferred.resolve(false)
      return@success
    }

    val ourPubKey = TextSecurePreferences.getLocalNumber(context)

    if (primaryDevicePubKey == ourPubKey) {
      // If the friend request is from our secondary device then we need to confirm and check that we have it registered.
      // If we do then add it
      storageAPI.getSecondaryDevices(ourPubKey).success { secondaryDevices ->
        // We should become friends if the pubKey is in our secondary device list
        deferred.resolve(secondaryDevices.contains(pubKey))
      }.fail {
        deferred.resolve(false)
      }

      return@success
    }

    // If we have a thread then the id will be >= 0
    val primaryDevice = Recipient.from(context, Address.fromSerialized(primaryDevicePubKey), false)
    val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(primaryDevice)
    if (threadID < 0) {
      deferred.resolve(false)
      return@success
    }

    // We should become friends if the primary device is our friend
    deferred.resolve(lokiThreadDatabase.getFriendRequestStatus(threadID) == LokiThreadFriendRequestStatus.FRIENDS)
  }

  return deferred.promise
}

fun sendAuthorisationMessage(context: Context, contactHexEncodedPublicKey: String, authorisation: LokiPairingAuthorisation): Promise<Unit, Exception> {
  val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
  val address = SignalServiceAddress(contactHexEncodedPublicKey)
  val message = SignalServiceDataMessage.newBuilder().withBody("").withPairingAuthorisation(authorisation)

  // A REQUEST should always act as a friend request. A GRANT should always be replying back as a normal message.
  if (authorisation.type == LokiPairingAuthorisation.Type.REQUEST) {
    val preKeyBundle = DatabaseFactory.getLokiPreKeyBundleDatabase(context).generatePreKeyBundle(address.number)
    message.asFriendRequest(true).withPreKeyBundle(preKeyBundle)
  }

  return try {
    Log.d("Loki", "Sending authorisation message to $contactHexEncodedPublicKey")
    val result = messageSender.sendMessage(0, address, Optional.absent<UnidentifiedAccessPair>(), message.build())
    if (result.success == null) {
      val exception = when {
        result.isNetworkFailure -> "Failed to send authorisation message because of a Network Error"
        else -> "Failed to send authorisation message"
      }

      throw Exception(exception)
    }
    Promise.ofSuccess(Unit)
  } catch (e: Exception) {
    Log.d("Loki", "Failed to send authorisation message to: $contactHexEncodedPublicKey.")
    Promise.ofFail(e)
  }
}