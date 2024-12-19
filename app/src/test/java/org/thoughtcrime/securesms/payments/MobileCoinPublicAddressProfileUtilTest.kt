package org.thoughtcrime.securesms.payments

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import okio.ByteString
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.test.LibSignalLibraryUtil

class MobileCoinPublicAddressProfileUtilTest {
  @Before
  fun ensureNativeSupported() {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS()
  }

  @Test
  fun can_verify_an_address() {
    val identityKeyPair = IdentityKeyUtil.generateIdentityKeyPair()
    val address = Util.getSecretBytes(100)
    val signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair)

    val paymentsAddress = MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(signedPaymentAddress, identityKeyPair.publicKey)

    assertThat(paymentsAddress).isEqualTo(address)
  }

  @Test
  fun can_not_verify_an_address_with_the_wrong_key() {
    val identityKeyPair = IdentityKeyUtil.generateIdentityKeyPair()
    val wrongPublicKey = IdentityKeyUtil.generateIdentityKeyPair().publicKey
    val address = Util.getSecretBytes(100)
    val signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair)

    assertFailure { MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(signedPaymentAddress, wrongPublicKey) }
      .isInstanceOf<PaymentsAddressException>()
      .hasMessage("Invalid MobileCoin address signature on payments address proto")
  }

  @Test
  fun can_not_verify_a_tampered_signature() {
    val identityKeyPair = IdentityKeyUtil.generateIdentityKeyPair()
    val address = Util.getSecretBytes(100)
    val signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair)
    val mobileCoinAddress = signedPaymentAddress.mobileCoinAddress!!

    val signature = mobileCoinAddress.signature!!.toByteArray()
    signature[0] = (signature[0].toInt() xor 0x01).toByte()
    val tamperedSignature = signedPaymentAddress.newBuilder()
      .mobileCoinAddress(
        mobileCoinAddress
          .newBuilder()
          .signature(ByteString.of(*signature))
          .build()
      )
      .build()

    assertFailure { MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(tamperedSignature, identityKeyPair.publicKey) }
      .isInstanceOf<PaymentsAddressException>()
      .hasMessage("Invalid MobileCoin address signature on payments address proto")
  }

  @Test
  fun can_not_verify_a_tampered_address() {
    val identityKeyPair = IdentityKeyUtil.generateIdentityKeyPair()
    val addressBytes = Util.getSecretBytes(100)
    val signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(addressBytes, identityKeyPair)
    val mobileCoinAddress = signedPaymentAddress.mobileCoinAddress!!

    val address = mobileCoinAddress.address!!.toByteArray()
    address[0] = (address[0].toInt() xor 0x01).toByte()
    val tamperedAddress = signedPaymentAddress.newBuilder()
      .mobileCoinAddress(
        mobileCoinAddress
          .newBuilder()
          .address(ByteString.of(*address))
          .build()
      )
      .build()

    assertFailure { MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(tamperedAddress, identityKeyPair.publicKey) }
      .isInstanceOf<PaymentsAddressException>()
      .hasMessage("Invalid MobileCoin address signature on payments address proto")
  }

  @Test
  fun can_not_verify_a_missing_signature() {
    val identityKeyPair = IdentityKeyUtil.generateIdentityKeyPair()
    val address = Util.getSecretBytes(100)
    val signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair)

    val removedSignature = signedPaymentAddress.newBuilder()
      .mobileCoinAddress(
        signedPaymentAddress.mobileCoinAddress!!
          .newBuilder()
          .signature(null)
          .build()
      )
      .build()

    assertFailure { MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(removedSignature, identityKeyPair.publicKey) }
      .isInstanceOf<PaymentsAddressException>()
      .hasMessage("Invalid MobileCoin address signature on payments address proto")
  }

  @Test
  fun can_not_verify_a_missing_address() {
    val identityKeyPair = IdentityKeyUtil.generateIdentityKeyPair()
    val address = Util.getSecretBytes(100)
    val signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair)

    val removedAddress = signedPaymentAddress.newBuilder()
      .mobileCoinAddress(
        signedPaymentAddress.mobileCoinAddress!!
          .newBuilder()
          .address(null)
          .build()
      )
      .build()

    assertFailure { MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(removedAddress, identityKeyPair.publicKey) }
      .isInstanceOf<PaymentsAddressException>()
      .hasMessage("Invalid MobileCoin address signature on payments address proto")
  }
}
