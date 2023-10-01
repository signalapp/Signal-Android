package org.thoughtcrime.securesms.registration.secondary

import okio.ByteString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.whispersystems.signalservice.internal.crypto.PrimaryProvisioningCipher
import org.whispersystems.signalservice.internal.push.ProvisionEnvelope
import org.whispersystems.signalservice.internal.push.ProvisionMessage
import org.whispersystems.signalservice.internal.push.ProvisioningVersion
import java.util.UUID

class SecondaryProvisioningCipherTest {

  @Test
  fun decrypt() {
    val provisioningCipher = SecondaryProvisioningCipher.generate()

    val primaryIdentityKeyPair = IdentityKeyUtil.generateIdentityKeyPair()
    val primaryProfileKey = ProfileKeyUtil.createNew()
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
}
