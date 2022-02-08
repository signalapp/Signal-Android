package org.thoughtcrime.securesms.registration.secondary

import com.google.protobuf.ByteString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.whispersystems.signalservice.internal.crypto.PrimaryProvisioningCipher
import org.whispersystems.signalservice.internal.push.ProvisioningProtos
import org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionMessage
import org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisioningVersion
import java.util.UUID

class SecondaryProvisioningCipherTest {

  @Test
  fun decrypt() {
    val provisioningCipher = SecondaryProvisioningCipher.generate()

    val primaryIdentityKeyPair = IdentityKeyUtil.generateIdentityKeyPair()
    val primaryProfileKey = ProfileKeyUtil.createNew()
    val primaryProvisioningCipher = PrimaryProvisioningCipher(provisioningCipher.secondaryDevicePublicKey.publicKey)

    val message = ProvisionMessage.newBuilder()
      .setIdentityKeyPublic(ByteString.copyFrom(primaryIdentityKeyPair.publicKey.serialize()))
      .setIdentityKeyPrivate(ByteString.copyFrom(primaryIdentityKeyPair.privateKey.serialize()))
      .setProvisioningCode("code")
      .setProvisioningVersion(ProvisioningVersion.CURRENT_VALUE)
      .setNumber("+14045555555")
      .setUuid(UUID.randomUUID().toString())
      .setProfileKey(ByteString.copyFrom(primaryProfileKey.serialize()))

    val provisionMessage = ProvisioningProtos.ProvisionEnvelope.parseFrom(primaryProvisioningCipher.encrypt(message.build()))

    val result = provisioningCipher.decrypt(provisionMessage)
    assertThat(result, instanceOf(SecondaryProvisioningCipher.ProvisionDecryptResult.Success::class.java))

    val success = result as SecondaryProvisioningCipher.ProvisionDecryptResult.Success

    assertThat(success.uuid.toString(), `is`(message.uuid))
    assertThat(success.e164, `is`(message.number))
    assertThat(success.identityKeyPair.serialize(), `is`(primaryIdentityKeyPair.serialize()))
    assertThat(success.profileKey.serialize(), `is`(primaryProfileKey.serialize()))
    assertThat(success.areReadReceiptsEnabled, `is`(message.readReceipts))
    assertThat(success.primaryUserAgent, `is`(message.userAgent))
    assertThat(success.provisioningCode, `is`(message.provisioningCode))
    assertThat(success.provisioningVersion, `is`(message.provisioningVersion))
  }
}
