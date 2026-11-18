/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.crypto

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Test
import org.signal.core.util.UuidUtil
import org.signal.core.util.toByteArray
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.provisioning.ProvisioningPublicKey
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.registration.proto.RegistrationProvisionMessage
import org.whispersystems.signalservice.internal.push.ProvisionMessage
import org.whispersystems.signalservice.internal.push.ProvisioningVersion
import java.util.UUID
import kotlin.random.Random

class SecondaryProvisioningCipherTest {
  @Test
  fun decrypt() {
    val provisioningCipher = SecondaryProvisioningCipher.generate()

    val primaryIdentityKeyPair = IdentityKeyPair.generate()
    val primaryProfileKey = generateProfileKey()
    val aci = UUID.randomUUID()

    val message = ProvisionMessage(
      aciIdentityKeyPublic = ByteString.of(*primaryIdentityKeyPair.publicKey.serialize()),
      aciIdentityKeyPrivate = ByteString.of(*primaryIdentityKeyPair.privateKey.serialize()),
      provisioningCode = "code",
      provisioningVersion = ProvisioningVersion.CURRENT.value,
      number = "+14045555555",
      aci = aci.toString(),
      profileKey = ByteString.of(*primaryProfileKey.serialize()),
      readReceipts = true,
      aciBinary = aci.toByteArray().toByteString()
    )

    val result = provisioningCipher.decryptProvisionEnvelope(PrimaryProvisioningCipher.encrypt(provisioningCipher.secondaryDevicePublicKey, message))
    assertThat(result).isInstanceOf<SecondaryProvisioningCipher.ProvisioningDecryptResult.Success<ProvisionMessage>>()

    val success = result as SecondaryProvisioningCipher.ProvisioningDecryptResult.Success<ProvisionMessage>

    assertThat(message.aci).isEqualTo(UuidUtil.parseOrThrow(success.message.aci!!).toString())
    assertThat(message.number).isEqualTo(success.message.number)
    assertThat(primaryIdentityKeyPair.serialize()).isEqualTo(IdentityKeyPair(IdentityKey(success.message.aciIdentityKeyPublic!!.toByteArray()), ECPrivateKey(success.message.aciIdentityKeyPrivate!!.toByteArray())).serialize())
    assertThat(primaryProfileKey.serialize()).isEqualTo(ProfileKey(success.message.profileKey!!.toByteArray()).serialize())
    assertThat(message.readReceipts).isEqualTo(success.message.readReceipts == true)
    assertThat(message.userAgent).isEqualTo(success.message.userAgent)
    assertThat(message.provisioningCode).isEqualTo(success.message.provisioningCode!!)
    assertThat(message.provisioningVersion).isEqualTo(success.message.provisioningVersion!!)
    assertThat(message.aciBinary).isEqualTo(UuidUtil.parseOrThrow(success.message.aciBinary!!).toByteArray().toByteString())
  }

  @Test
  fun decryptRegistrationProvisionEnvelope() {
    val provisioningCipher = SecondaryProvisioningCipher.generate()
    val message = RegistrationProvisionMessage(
      e164 = "+14045555555",
      aci = UUID.randomUUID().toByteArray().toByteString(),
      accountEntropyPool = "a".repeat(64),
      restoreMethodToken = "token"
    )

    val envelope = PrimaryProvisioningCipher.encrypt(provisioningCipher.secondaryDevicePublicKey, message)
    val result = provisioningCipher.decryptRegistrationProvisionEnvelope(envelope)

    assertThat(result).isEqualTo(SecondaryProvisioningCipher.ProvisioningDecryptResult.Success(message))
  }

  @Test
  fun encryptToInvalidPublicKeyFails() {
    val message = ProvisionMessage(provisioningCode = "code")

    // Key too short
    assertFailure { PrimaryProvisioningCipher.encrypt(ProvisioningPublicKey(ByteArray(32)), message) }.isInstanceOf<InvalidKeyException>()
    // Key doesn't start with 0x05
    assertFailure { PrimaryProvisioningCipher.encrypt(ProvisioningPublicKey(ByteArray(33)), message) }.isInstanceOf<InvalidKeyException>()
  }

  companion object {
    fun generateProfileKey(): ProfileKey {
      return ProfileKey(Random.nextBytes(32))
    }
  }
}
