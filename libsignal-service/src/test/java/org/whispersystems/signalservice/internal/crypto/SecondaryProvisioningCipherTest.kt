/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.crypto

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import okio.ByteString
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.push.ProvisionEnvelope
import org.whispersystems.signalservice.internal.push.ProvisionMessage
import org.whispersystems.signalservice.internal.push.ProvisioningVersion
import java.util.UUID
import kotlin.random.Random

class SecondaryProvisioningCipherTest {
  @Test
  fun decrypt() {
    val provisioningCipher = SecondaryProvisioningCipher.generate(IdentityKeyPair.generate())

    val primaryIdentityKeyPair = IdentityKeyPair.generate()
    val primaryProfileKey = generateProfileKey()
    val primaryProvisioningCipher = PrimaryProvisioningCipher(provisioningCipher.secondaryDevicePublicKey.publicKey)

    val message = ProvisionMessage(
      aciIdentityKeyPublic = ByteString.of(*primaryIdentityKeyPair.publicKey.serialize()),
      aciIdentityKeyPrivate = ByteString.of(*primaryIdentityKeyPair.privateKey.serialize()),
      provisioningCode = "code",
      provisioningVersion = ProvisioningVersion.CURRENT.value,
      number = "+14045555555",
      aci = UUID.randomUUID().toString(),
      profileKey = ByteString.of(*primaryProfileKey.serialize()),
      readReceipts = true
    )

    val provisionMessage = ProvisionEnvelope.ADAPTER.decode(primaryProvisioningCipher.encrypt(message))

    val result = provisioningCipher.decrypt(provisionMessage)
    assertThat(result).isInstanceOf<SecondaryProvisioningCipher.ProvisioningDecryptResult.Success<ProvisionMessage>>()

    val success = result as SecondaryProvisioningCipher.ProvisioningDecryptResult.Success<ProvisionMessage>

    assertThat(message.aci).isEqualTo(UuidUtil.parseOrThrow(success.message.aci).toString())
    assertThat(message.number).isEqualTo(success.message.number)
    assertThat(primaryIdentityKeyPair.serialize()).isEqualTo(IdentityKeyPair(IdentityKey(success.message.aciIdentityKeyPublic!!.toByteArray()), ECPrivateKey(success.message.aciIdentityKeyPrivate!!.toByteArray())).serialize())
    assertThat(primaryProfileKey.serialize()).isEqualTo(ProfileKey(success.message.profileKey!!.toByteArray()).serialize())
    assertThat(message.readReceipts).isEqualTo(success.message.readReceipts == true)
    assertThat(message.userAgent).isEqualTo(success.message.userAgent)
    assertThat(message.provisioningCode).isEqualTo(success.message.provisioningCode!!)
    assertThat(message.provisioningVersion).isEqualTo(success.message.provisioningVersion!!)
  }

  companion object {
    fun generateProfileKey(): ProfileKey {
      return ProfileKey(Random.nextBytes(32))
    }
  }
}
