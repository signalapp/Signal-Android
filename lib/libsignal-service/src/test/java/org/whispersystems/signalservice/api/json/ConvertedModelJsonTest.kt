/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.json

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Test
import org.signal.core.models.ServiceId
import org.signal.core.util.Base64
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.network.util.JsonUtil
import org.whispersystems.signalservice.api.account.ChangePhoneNumberRequest
import org.whispersystems.signalservice.api.donations.ReceiptCredentialResponseJson
import org.whispersystems.signalservice.api.keys.OneTimePreKeyCounts
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity
import org.whispersystems.signalservice.api.subscriptions.StripeClientSecret
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.DonationIntentResult
import org.whispersystems.signalservice.internal.push.IdentityCheckRequest
import org.whispersystems.signalservice.internal.push.IdentityCheckResponse
import org.whispersystems.signalservice.internal.push.KyberPreKeyEntity
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage
import org.whispersystems.signalservice.internal.push.PreKeyEntity
import org.whispersystems.signalservice.internal.push.PreKeyResponse
import org.whispersystems.signalservice.internal.push.PreKeyState
import org.whispersystems.signalservice.internal.push.RegistrationLockFailure
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration

/**
 * These models moved from Java to Kotlin. Jackson resolves Kotlin constructor properties by name via
 * `jackson-module-kotlin`, so an un-renamed `@JsonProperty` can be dropped — but a renamed one, or a hand-written
 * serializer, cannot. This pins the ones where a conversion mistake would silently change the wire format.
 */
class ConvertedModelJsonTest {

  companion object {
    private const val ACI_STRING = "3f0d3b5e-0b2c-4e1e-9c1b-2a9a8f3f3d10"
  }

  private val mapper = ObjectMapper().findAndRegisterModules()

  private val ecKeyPair = ECKeyPair.generate()
  private val kemKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
  private val identityKey = IdentityKey(ecKeyPair.publicKey)
  private val signature = byteArrayOf(1, 2, 3, 4, 5)

  private val ecPublic = Base64.encodeWithoutPadding(ecKeyPair.publicKey.serialize())
  private val kemPublic = Base64.encodeWithoutPadding(kemKeyPair.publicKey.serialize())
  private val identityKeyBase64 = Base64.encodeWithoutPadding(identityKey.serialize())
  private val signatureBase64 = Base64.encodeWithoutPadding(signature)

  private fun assertJsonEquals(expected: String, actual: String) {
    assertThat(mapper.readTree(actual)).isEqualTo(mapper.readTree(expected))
  }

  /** Keys are base64 with no padding, per the hand-written serializers these carried in Java. */
  @Test
  fun preKeyState() {
    val body = PreKeyState(
      signedPreKey = SignedPreKeyEntity(2, ecKeyPair.publicKey, signature),
      oneTimeEcPreKeys = listOf(PreKeyEntity(1, ecKeyPair.publicKey)),
      lastResortKyberKey = KyberPreKeyEntity(3, kemKeyPair.publicKey, signature),
      oneTimeKyberKeys = listOf(KyberPreKeyEntity(4, kemKeyPair.publicKey, signature))
    )

    assertJsonEquals(
      """{"signedPreKey":{"keyId":2,"publicKey":"$ecPublic","signature":"$signatureBase64"},"preKeys":[{"keyId":1,"publicKey":"$ecPublic"}],"pqLastResortPreKey":{"keyId":3,"publicKey":"$kemPublic","signature":"$signatureBase64"},"pqPreKeys":[{"keyId":4,"publicKey":"$kemPublic","signature":"$signatureBase64"}]}""",
      JsonUtil.toJson(body)
    )
  }

  @Test
  fun preKeyResponse() {
    val json = """{"identityKey":"$identityKeyBase64","devices":[{"deviceId":1,"registrationId":1234,"signedPreKey":{"keyId":2,"publicKey":"$ecPublic","signature":"$signatureBase64"},"preKey":{"keyId":1,"publicKey":"$ecPublic"},"pqPreKey":{"keyId":3,"publicKey":"$kemPublic","signature":"$signatureBase64"}}]}"""

    val decoded = JsonUtil.fromJson(json, PreKeyResponse::class.java)

    assertThat(decoded.identityKey).isEqualTo(identityKey)
    assertThat(decoded.devices).hasSize(1)

    val device = decoded.devices.first()
    assertThat(device.deviceId).isEqualTo(1)
    assertThat(device.registrationId).isEqualTo(1234)
    assertThat(device.preKey?.publicKey).isEqualTo(ecKeyPair.publicKey)
    assertThat(device.signedPreKey?.signature?.toList()).isEqualTo(signature.toList())
    assertThat(device.kyberPreKey?.publicKey).isEqualTo(kemKeyPair.publicKey)
  }

  @Test
  fun oneTimePreKeyCounts() {
    val decoded = JsonUtil.fromJson("""{"count":5,"pqCount":7}""", OneTimePreKeyCounts::class.java)

    assertThat(decoded.ecCount).isEqualTo(5)
    assertThat(decoded.kyberCount).isEqualTo(7)
  }

  @Test
  fun changePhoneNumberRequest() {
    val body = ChangePhoneNumberRequest(
      sessionId = "session",
      recoveryPassword = "recovery",
      number = "+15551234567",
      registrationLock = "reglock",
      pniIdentityKey = identityKey,
      deviceMessages = listOf(OutgoingPushMessage(1, 2, 3, "content")),
      devicePniSignedPrekeys = mapOf("1" to SignedPreKeyEntity(1, ecKeyPair.publicKey, signature)),
      devicePniLastResortKyberPrekeys = mapOf("1" to KyberPreKeyEntity(2, kemKeyPair.publicKey, signature)),
      pniRegistrationIds = mapOf("1" to 1234)
    )

    assertJsonEquals(
      """{"sessionId":"session","recoveryPassword":"recovery","number":"+15551234567","reglock":"reglock","pniIdentityKey":"$identityKeyBase64","deviceMessages":[{"type":1,"destinationDeviceId":2,"destinationRegistrationId":3,"content":"content"}],"devicePniSignedPrekeys":{"1":{"keyId":1,"publicKey":"$ecPublic","signature":"$signatureBase64"}},"devicePniPqLastResortPrekeys":{"1":{"keyId":2,"publicKey":"$kemPublic","signature":"$signatureBase64"}},"pniRegistrationIds":{"1":1234}}""",
      JsonUtil.toJson(body)
    )
  }

  @Test
  fun signalServiceProfile() {
    val json = """{"identityKey":"identity","name":"name","about":"about","aboutEmoji":"emoji","paymentAddress":"AQIDBA==","avatar":"avatar","unidentifiedAccess":"access","unrestrictedUnidentifiedAccess":true,"capabilities":{"storage":true,"ssre2":false,"usernameChangeSyncMessage":true,"optionalPhoneNumber":false},"uuid":"$ACI_STRING","badges":[{"id":"BOOST","category":"donor","name":"Boost","description":"A boost","sprites6":["a","b"],"expiration":1675609546,"visible":true,"duration":100}],"phoneNumberSharing":"sharing"}"""

    val decoded = JsonUtil.fromJson(json, SignalServiceProfile::class.java)

    assertThat(decoded.serviceId).isEqualTo(ServiceId.ACI.parseOrThrow(ACI_STRING))
    assertThat(decoded.paymentAddress?.toList()).isEqualTo(listOf<Byte>(1, 2, 3, 4))
    assertThat(decoded.unrestrictedUnidentifiedAccess).isEqualTo(true)
    assertThat(decoded.capabilities?.storageServiceEncryptionV2).isEqualTo(false)
    assertThat(decoded.capabilities?.usernameSyncMessages).isEqualTo(true)
    assertThat(decoded.badges?.first()?.visible).isEqualTo(true)
    assertThat(decoded.badges?.first()?.sprites6).isEqualTo(listOf("a", "b"))
  }

  @Test
  fun subscriptionsConfiguration() {
    val json = """{"currencies":{"USD":{"minimum":100,"oneTime":{"1":[500,1000]},"subscription":{"500":500},"backupSubscription":{"201":199},"supportedPaymentMethods":["CARD","PAYPAL"]}},"levels":{"500":{"badge":{"id":"BOOST"}}},"sepaMaximumEuros":10000,"backup":{"levels":{"201":{"storageAllowanceBytes":100,"playProductId":"product","mediaTtlDays":60}},"freeTierMediaDays":30}}"""

    val decoded = JsonUtil.fromJson(json, SubscriptionsConfiguration::class.java)

    assertThat(decoded.currencies["USD"]?.supportedPaymentMethods).isEqualTo(setOf("CARD", "PAYPAL"))
    assertThat(decoded.levels[500]?.badge?.id).isEqualTo("BOOST")
    assertThat(decoded.backupConfiguration.freeTierMediaDays).isEqualTo(30)
    assertThat(decoded.backupConfiguration.backupLevelConfigurationMap[201]?.playProductId).isEqualTo("product")
  }

  @Test
  fun donationIntentResult() {
    val decoded = JsonUtil.fromJson("""{"id":"pi_1234","client_secret":"secret"}""", DonationIntentResult::class.java)

    assertThat(decoded.id).isEqualTo("pi_1234")
    assertThat(decoded.clientSecret).isEqualTo("secret")
  }

  @Test
  fun identityCheckRequest() {
    val pair = IdentityCheckRequest.ServiceIdFingerprintPair(ServiceId.ACI.parseOrThrow(ACI_STRING), identityKey)

    assertJsonEquals(
      """{"elements":[{"uuid":"$ACI_STRING","fingerprint":"${pair.fingerprint}"}]}""",
      JsonUtil.toJson(IdentityCheckRequest(listOf(pair)))
    )
  }

  @Test
  fun identityCheckResponse() {
    val json = """{"elements":[{"uuid":"$ACI_STRING","identityKey":"$identityKeyBase64"}]}"""

    val decoded = JsonUtil.fromJson(json, IdentityCheckResponse::class.java)

    assertThat(decoded.serviceIdKeyPairs?.first()?.serviceId).isEqualTo(ServiceId.ACI.parseOrThrow(ACI_STRING))
    assertThat(decoded.serviceIdKeyPairs?.first()?.identityKey).isEqualTo(identityKey)
  }

  @Test
  fun registrationLockFailure() {
    val json = """{"length":6,"timeRemaining":1234,"backupCredentials":{"username":"user1","password":"pass1"},"svr2Credentials":{"username":"user2","password":"pass2"}}"""

    val decoded = JsonUtil.fromJson(json, RegistrationLockFailure::class.java)

    assertThat(decoded.length).isEqualTo(6)
    assertThat(decoded.timeRemaining).isEqualTo(1234L)
    assertThat(decoded.svr1Credentials?.username()).isEqualTo("user1")
    assertThat(decoded.svr2Credentials?.username()).isEqualTo("user2")
    assertThat(decoded.svr3Credentials).isNull()
  }

  /** The fields are private, so they need `@field:JsonProperty` for Jackson to write them back out. */
  @Test
  fun authCredentials() {
    val credentials = AuthCredentials.create("user", "pass")

    assertJsonEquals("""{"username":"user","password":"pass"}""", JsonUtil.toJson(credentials))
    assertThat(JsonUtil.fromJson("""{"username":"user","password":"pass"}""", AuthCredentials::class.java).asBasic()).isNotNull()
  }

  /**
   * Both of these had a single-argument `@JsonCreator` constructor in Java. Without an explicit mode, such a
   * constructor is *delegating* — the parameter would receive the whole JSON object rather than the named field — so
   * pin that the Kotlin module still binds them by property name.
   */
  @Test
  fun singleArgumentConstructorsBindByName() {
    val secret = JsonUtil.fromJson("""{"clientSecret":"pi_1234_secret_5678"}""", StripeClientSecret::class.java)
    assertThat(secret.clientSecret).isEqualTo("pi_1234_secret_5678")
    assertThat(secret.id).isEqualTo("pi_1234")

    // Not a real zkgroup credential, so it decodes to null rather than throwing. A delegating creator would fail here.
    val credential = JsonUtil.fromJson("""{"receiptCredentialResponse":"AQIDBA=="}""", ReceiptCredentialResponseJson::class.java)
    assertThat(credential.credentialResponse).isNull()
  }
}
