@file:JvmName("MultiDeviceUtilities")
package org.thoughtcrime.securesms.loki

import android.content.Context
import android.os.Handler
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.toFailVoid
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.api.LokiStorageAPI
import org.whispersystems.signalservice.loki.api.PairingAuthorisation
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus
import org.whispersystems.signalservice.loki.utilities.retryIfNeeded

/*
 All functions within this class, excluding the ones which return promises, BLOCK the thread! Don't run them on the main thread!
 */

fun getAllDeviceFriendRequestStatuses(context: Context, hexEncodedPublicKey: String): Map<String, LokiThreadFriendRequestStatus> {
  val lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(context)
  val keys = LokiStorageAPI.shared.getAllDevicePublicKeys(hexEncodedPublicKey)
  val map = mutableMapOf<String, LokiThreadFriendRequestStatus>()
  for (devicePublicKey in keys) {
    val device = Recipient.from(context, Address.fromSerialized(devicePublicKey), false)
    val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(device)
    val friendRequestStatus = if (threadID < 0) LokiThreadFriendRequestStatus.NONE else lokiThreadDatabase.getFriendRequestStatus(threadID)
    map[devicePublicKey] = friendRequestStatus
  }
  return map
}

fun getAllDevicePublicKeysWithFriendStatus(context: Context, hexEncodedPublicKey: String): Promise<Map<String, Boolean>, Unit> {
  val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context)
  return LokiStorageAPI.shared.getAllDevicePublicKeysAsync(hexEncodedPublicKey).map { keys ->
    val devices = keys.toMutableSet()
    if (hexEncodedPublicKey != userHexEncodedPublicKey) {
      devices.remove(userHexEncodedPublicKey)
    }
    val friends = getFriendPublicKeys(context, devices)
    val friendMap = mutableMapOf<String, Boolean>()
    for (device in devices) {
      friendMap[device] = friends.contains(device)
    }
    friendMap
  }.toFailVoid()
}

fun getFriendCount(context: Context, devices: Set<String>): Int {
  return getFriendPublicKeys(context, devices).count()
}

fun shouldAutomaticallyBecomeFriendsWithDevice(publicKey: String, context: Context): Boolean {
  val lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(context)
  val storageAPI = LokiStorageAPI.shared

  // If the public key doesn't have any other devices then go through regular friend request logic
  val primaryDevicePublicKey = storageAPI.getPrimaryDevicePublicKey(publicKey) ?: return false

  // If this is one of our devices then we should become friends
  if (isOneOfOurDevices(context, publicKey)) {
    return true
  }

  // If we are friends with the primary device then we should become friends
  val primaryDevice = Recipient.from(context, Address.fromSerialized(primaryDevicePublicKey), false)
  val primaryDeviceThreadID = DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(primaryDevice)
  return primaryDeviceThreadID >= 0 && lokiThreadDatabase.getFriendRequestStatus(primaryDeviceThreadID) == LokiThreadFriendRequestStatus.FRIENDS
}

fun sendPairingAuthorisationMessage(context: Context, contactHexEncodedPublicKey: String, authorisation: PairingAuthorisation): Promise<Unit, Exception> {
  val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
  val address = SignalServiceAddress(contactHexEncodedPublicKey)
  val message = SignalServiceDataMessage.newBuilder().withBody("").withPairingAuthorisation(authorisation)
  // A REQUEST should always act as a friend request. A GRANT should always be replying back as a normal message.
  if (authorisation.type == PairingAuthorisation.Type.REQUEST) {
    val preKeyBundle = DatabaseFactory.getLokiPreKeyBundleDatabase(context).generatePreKeyBundle(address.number)
    message.asFriendRequest(true).withPreKeyBundle(preKeyBundle)
  }
  return try {
    Log.d("Loki", "Sending authorisation message to: $contactHexEncodedPublicKey.")
    val result = messageSender.sendMessage(0, address, Optional.absent<UnidentifiedAccessPair>(), message.build())
    if (result.success == null) {
      val exception = when {
        result.isNetworkFailure -> "Failed to send authorisation message due to a network error."
        else -> "Failed to send authorisation message."
      }
      throw Exception(exception)
    }
    Promise.ofSuccess(Unit)
  } catch (e: Exception) {
    Log.d("Loki", "Failed to send authorisation message to: $contactHexEncodedPublicKey.")
    Promise.ofFail(e)
  }
}

fun signAndSendPairingAuthorisationMessage(context: Context, pairingAuthorisation: PairingAuthorisation) {
  val userPrivateKey = IdentityKeyUtil.getIdentityKeyPair(context).privateKey.serialize()
  val signedPairingAuthorisation = pairingAuthorisation.sign(PairingAuthorisation.Type.GRANT, userPrivateKey)
  if (signedPairingAuthorisation == null || signedPairingAuthorisation.type != PairingAuthorisation.Type.GRANT) {
    Log.d("Loki", "Failed to sign pairing authorization.")
    return
  }
  retryIfNeeded(8) {
    sendPairingAuthorisationMessage(context, pairingAuthorisation.secondaryDevicePublicKey, signedPairingAuthorisation).get()
  }.fail {
    Log.d("Loki", "Failed to send pairing authorization message to ${pairingAuthorisation.secondaryDevicePublicKey}.")
  }
  DatabaseFactory.getLokiAPIDatabase(context).insertOrUpdatePairingAuthorisation(signedPairingAuthorisation)
  // Call function after a short delay
  Handler().postDelayed({
    LokiStorageAPI.shared.updateUserDeviceMappings().fail {
      Log.w("Loki", "Failed to update device mapping")
    }
  }, 100)

}

fun shouldSendSycMessage(context: Context, address: Address): Boolean {
  if (address.isGroup || address.isEmail || address.isMmsGroup) {
    return false
  }

  // Don't send sync messages if it's one of our devices
  return !isOneOfOurDevices(context, address)
}

fun isOneOfOurDevices(context: Context, publicKey: String): Boolean {
  return isOneOfOurDevices(context, Address.fromSerialized(publicKey))
}

fun isOneOfOurDevices(context: Context, address: Address): Boolean {
  if (address.isGroup || address.isEmail || address.isMmsGroup) {
    return false
  }

  val ourPublicKey = TextSecurePreferences.getLocalNumber(context)
  val devices = LokiStorageAPI.shared.getAllDevicePublicKeys(ourPublicKey)
  return devices.contains(address.serialize())
}

fun isFriendsWithAnyLinkedDevice(context: Context, recipient: Recipient): Boolean {
  if (recipient.isGroupRecipient) return true

  val map = getAllDeviceFriendRequestStatuses(context, recipient.address.serialize())
  for (status in map.values) {
    if (status == LokiThreadFriendRequestStatus.FRIENDS) {
      return true
    }
  }
  return false
}