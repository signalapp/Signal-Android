package org.thoughtcrime.securesms.payments;

import org.junit.Before;
import org.junit.Test;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.internal.push.PaymentAddress;

import okio.ByteString;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertArrayEquals;
import static org.whispersystems.signalservice.test.LibSignalLibraryUtil.assumeLibSignalSupportedOnOS;

public final class MobileCoinPublicAddressProfileUtilTest {

  @Before
  public void ensureNativeSupported() {
    assumeLibSignalSupportedOnOS();
  }

  @Test
  public void can_verify_an_address() throws PaymentsAddressException {
    IdentityKeyPair identityKeyPair      = IdentityKeyUtil.generateIdentityKeyPair();
    byte[]          address              = Util.getSecretBytes(100);
    PaymentAddress  signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair);

    byte[] paymentsAddress = MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(signedPaymentAddress, identityKeyPair.getPublicKey());

    assertArrayEquals(address, paymentsAddress);
  }

  @Test
  public void can_not_verify_an_address_with_the_wrong_key() {
    IdentityKeyPair identityKeyPair      = IdentityKeyUtil.generateIdentityKeyPair();
    IdentityKey     wrongPublicKey       = IdentityKeyUtil.generateIdentityKeyPair().getPublicKey();
    byte[]          address              = Util.getSecretBytes(100);
    PaymentAddress  signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair);

    assertThatThrownBy(() -> MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(signedPaymentAddress, wrongPublicKey))
        .isInstanceOf(PaymentsAddressException.class)
        .hasMessage("Invalid MobileCoin address signature on payments address proto");
  }

  @Test
  public void can_not_verify_a_tampered_signature() {
    IdentityKeyPair identityKeyPair      = IdentityKeyUtil.generateIdentityKeyPair();
    byte[]          address              = Util.getSecretBytes(100);
    PaymentAddress  signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair);

    byte[] signature = signedPaymentAddress.mobileCoinAddress.signature.toByteArray();
    signature[0] = (byte) (signature[0] ^ 0x01);
    PaymentAddress tamperedSignature = signedPaymentAddress.newBuilder()
                                                           .mobileCoinAddress(signedPaymentAddress.mobileCoinAddress
                                                                                  .newBuilder()
                                                                                  .signature(ByteString.of(signature))
                                                                                  .build())
                                                           .build();

    assertThatThrownBy(() -> MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(tamperedSignature, identityKeyPair.getPublicKey()))
        .isInstanceOf(PaymentsAddressException.class)
        .hasMessage("Invalid MobileCoin address signature on payments address proto");
  }

  @Test
  public void can_not_verify_a_tampered_address() {
    IdentityKeyPair identityKeyPair      = IdentityKeyUtil.generateIdentityKeyPair();
    byte[]          addressBytes         = Util.getSecretBytes(100);
    PaymentAddress  signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(addressBytes, identityKeyPair);

    byte[] address = signedPaymentAddress.mobileCoinAddress.address.toByteArray();
    address[0] = (byte) (address[0] ^ 0x01);
    PaymentAddress tamperedAddress = signedPaymentAddress.newBuilder()
                                                         .mobileCoinAddress(signedPaymentAddress.mobileCoinAddress
                                                                                .newBuilder()
                                                                                .address(ByteString.of(address))
                                                                                .build())
                                                         .build();

    assertThatThrownBy(() -> MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(tamperedAddress, identityKeyPair.getPublicKey()))
        .isInstanceOf(PaymentsAddressException.class)
        .hasMessage("Invalid MobileCoin address signature on payments address proto");
  }

  @Test
  public void can_not_verify_a_missing_signature() {
    IdentityKeyPair identityKeyPair      = IdentityKeyUtil.generateIdentityKeyPair();
    byte[]          address              = Util.getSecretBytes(100);
    PaymentAddress  signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair);

    PaymentAddress removedSignature = signedPaymentAddress.newBuilder()
                                                          .mobileCoinAddress(signedPaymentAddress.mobileCoinAddress
                                                                                 .newBuilder()
                                                                                 .signature(null)
                                                                                 .build())
                                                          .build();

    assertThatThrownBy(() -> MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(removedSignature, identityKeyPair.getPublicKey()))
        .isInstanceOf(PaymentsAddressException.class)
        .hasMessage("Invalid MobileCoin address signature on payments address proto");
  }

  @Test
  public void can_not_verify_a_missing_address() {
    IdentityKeyPair identityKeyPair      = IdentityKeyUtil.generateIdentityKeyPair();
    byte[]          address              = Util.getSecretBytes(100);
    PaymentAddress  signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair);

    PaymentAddress removedAddress = signedPaymentAddress.newBuilder()
                                                        .mobileCoinAddress(signedPaymentAddress.mobileCoinAddress
                                                                               .newBuilder()
                                                                               .address(null)
                                                                               .build())
                                                        .build();

    assertThatThrownBy(() -> MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(removedAddress, identityKeyPair.getPublicKey()))
        .isInstanceOf(PaymentsAddressException.class)
        .hasMessage("Invalid MobileCoin address signature on payments address proto");
  }
}
