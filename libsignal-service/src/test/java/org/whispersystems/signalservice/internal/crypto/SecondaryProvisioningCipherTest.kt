/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.crypto

import okio.ByteString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.whispersystems.signalservice.internal.push.ProvisionEnvelope
import org.whispersystems.signalservice.internal.push.ProvisionMessage
import org.whispersystems.signalservice.internal.push.ProvisioningVersion
import java.util.UUID
import kotlin.random.Random

class SecondaryProvisioningCipherTest {

  @Test
  fun decrypt() {
    val provisioningCipher = SecondaryProvisioningCipher.generate(generateIdentityKeyPair())

    val primaryIdentityKeyPair = generateIdentityKeyPair()
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
    assertThat(result, instanceOf(SecondaryProvisioningCipher.ProvisionDecryptResult.Success::class.java))

    val success = result as SecondaryProvisioningCipher.ProvisionDecryptResult.Success

    assertThat(success.uuid.toString(), `is`(message.aci))
    assertThat(success.e164, `is`(message.number))
    assertThat(success.identityKeyPair.serialize(), `is`(primaryIdentityKeyPair.serialize()))
    assertThat(success.profileKey.serialize(), `is`(primaryProfileKey.serialize()))
    assertThat(success.areReadReceiptsEnabled, `is`(message.readReceipts))
    assertThat(success.primaryUserAgent, `is`(message.userAgent))
    assertThat(success.provisioningCode, `is`(message.provisioningCode))
    assertThat(success.provisioningVersion, `is`(message.provisioningVersion))
  }

  companion object {
    fun generateIdentityKeyPair(): IdentityKeyPair {
      val djbKeyPair = Curve.generateKeyPair()
      val djbIdentityKey = IdentityKey(djbKeyPair.publicKey)
      val djbPrivateKey = djbKeyPair.privateKey

      return IdentityKeyPair(djbIdentityKey, djbPrivateKey)
    }

    fun generateProfileKey(): ProfileKey {
      return ProfileKey(Random.nextBytes(32))
    }
  }
}
