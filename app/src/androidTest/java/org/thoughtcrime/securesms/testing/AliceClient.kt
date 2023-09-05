package org.thoughtcrime.securesms.testing

import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.protocol.BufferedProtocolStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.FakeClientHelpers.toEnvelope
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope

/**
 * Welcome to Alice's Client.
 *
 * Alice represent the Android instrumentation test user. Unlike [BobClient] much less is needed here
 * as it can make use of the standard Signal Android App infrastructure.
 */
class AliceClient(val serviceId: ServiceId, val e164: String, val trustRoot: ECKeyPair) {

  companion object {
    val TAG = Log.tag(AliceClient::class.java)
  }

  private val aliceSenderCertificate = FakeClientHelpers.createCertificateFor(
    trustRoot = trustRoot,
    uuid = serviceId.rawUuid,
    e164 = e164,
    deviceId = 1,
    identityKey = SignalStore.account().aciIdentityKey.publicKey.publicKey,
    expires = 31337
  )

  fun process(envelope: Envelope, serverDeliveredTimestamp: Long) {
    val start = System.currentTimeMillis()
    val bufferedStore = BufferedProtocolStore.create()
    ApplicationDependencies.getIncomingMessageObserver()
      .processEnvelope(bufferedStore, envelope, serverDeliveredTimestamp)
      ?.mapNotNull { it.run() }
      ?.forEach { ApplicationDependencies.getJobManager().add(it) }

    bufferedStore.flushToDisk()
    val end = System.currentTimeMillis()
    Log.d(TAG, "${end - start}")
  }

  fun encrypt(now: Long, destination: Recipient): Envelope {
    return ApplicationDependencies.getSignalServiceMessageSender().getEncryptedMessage(
      SignalServiceAddress(destination.requireServiceId(), destination.requireE164()),
      FakeClientHelpers.getTargetUnidentifiedAccess(ProfileKeyUtil.getSelfProfileKey(), ProfileKey(destination.profileKey), aliceSenderCertificate),
      1,
      FakeClientHelpers.encryptedTextMessage(now),
      false
    ).toEnvelope(now, destination.requireServiceId())
  }
}
