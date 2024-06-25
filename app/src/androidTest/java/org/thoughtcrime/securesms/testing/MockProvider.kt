package org.thoughtcrime.securesms.testing

import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import org.signal.libsignal.protocol.util.Medium
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.DeviceInfoList
import org.whispersystems.signalservice.internal.push.PreKeyEntity
import org.whispersystems.signalservice.internal.push.PreKeyResponse
import org.whispersystems.signalservice.internal.push.PreKeyResponseItem
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataJson
import org.whispersystems.signalservice.internal.push.SenderCertificate
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse
import org.whispersystems.signalservice.internal.push.WhoAmIResponse
import java.security.SecureRandom

/**
 * Warehouse of reusable test data and mock configurations.
 */
object MockProvider {

  val senderCertificate = SenderCertificate().apply { certificate = ByteArray(0) }

  val lockedFailure = PushServiceSocket.RegistrationLockFailure().apply {
    svr1Credentials = AuthCredentials.create("username", "password")
    svr2Credentials = AuthCredentials.create("username", "password")
  }

  val primaryOnlyDeviceList = DeviceInfoList().apply {
    devices = listOf(
      DeviceInfo().apply {
        id = 1
      }
    )
  }

  val sessionMetadataJson = RegistrationSessionMetadataJson(
    id = "asdfasdfasdfasdf",
    nextCall = null,
    nextSms = null,
    nextVerificationAttempt = null,
    allowedToRequestCode = true,
    requestedInformation = emptyList(),
    verified = true
  )

  fun createVerifyAccountResponse(aci: ServiceId, newPni: ServiceId): VerifyAccountResponse {
    return VerifyAccountResponse().apply {
      uuid = aci.toString()
      pni = newPni.toString()
      storageCapable = false
    }
  }

  fun createWhoAmIResponse(aci: ServiceId, pni: ServiceId, e164: String): WhoAmIResponse {
    return WhoAmIResponse().apply {
      this.uuid = aci.toString()
      this.pni = pni.toString()
      this.number = e164
    }
  }

  fun createPreKeyResponse(identity: IdentityKeyPair = SignalStore.account.aciIdentityKey, deviceId: Int): PreKeyResponse {
    val signedPreKeyRecord = PreKeyUtil.generateSignedPreKey(SecureRandom().nextInt(Medium.MAX_VALUE), identity.privateKey)
    val oneTimePreKey = PreKeyRecord(SecureRandom().nextInt(Medium.MAX_VALUE), Curve.generateKeyPair())

    val device = PreKeyResponseItem().apply {
      this.deviceId = deviceId
      registrationId = KeyHelper.generateRegistrationId(false)
      signedPreKey = SignedPreKeyEntity(signedPreKeyRecord.id, signedPreKeyRecord.keyPair.publicKey, signedPreKeyRecord.signature)
      preKey = PreKeyEntity(oneTimePreKey.id, oneTimePreKey.keyPair.publicKey)
    }

    return PreKeyResponse().apply {
      identityKey = identity.publicKey
      devices = listOf(device)
    }
  }
}
