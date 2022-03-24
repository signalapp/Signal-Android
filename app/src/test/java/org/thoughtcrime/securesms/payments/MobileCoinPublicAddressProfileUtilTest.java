package org.thoughtcrime.securesms.payments;

import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Test;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

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
    IdentityKeyPair                    identityKeyPair      = IdentityKeyUtil.generateIdentityKeyPair();
    byte[]                             address              = Util.getSecretBytes(100);
    SignalServiceProtos.PaymentAddress signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair);

    byte[] paymentsAddress = MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(signedPaymentAddress, identityKeyPair.getPublicKey());

    assertArrayEquals(address, paymentsAddress);
  }

  @Test
  public void can_not_verify_an_address_with_the_wrong_key() {
    IdentityKeyPair                    identityKeyPair      = IdentityKeyUtil.generateIdentityKeyPair();
    IdentityKey                        wrongPublicKey       = IdentityKeyUtil.generateIdentityKeyPair().getPublicKey();
    byte[]                             address              = Util.getSecretBytes(100);
    SignalServiceProtos.PaymentAddress signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair);

    assertThatThrownBy(() -> MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(signedPaymentAddress, wrongPublicKey))
            .isInstanceOf(PaymentsAddressException.class)
            .hasMessage("Invalid MobileCoin address signature on payments address proto");
  }

  @Test
  public void can_not_verify_a_tampered_signature() {
    IdentityKeyPair                    identityKeyPair      = IdentityKeyUtil.generateIdentityKeyPair();
    byte[]                             address              = Util.getSecretBytes(100);
    SignalServiceProtos.PaymentAddress signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair);

    byte[] signature = signedPaymentAddress.getMobileCoinAddress().getSignature().toByteArray();
    signature[0] = (byte) (signature[0] ^ 0x01);
    SignalServiceProtos.PaymentAddress tamperedSignature = signedPaymentAddress.toBuilder()
                                                                               .setMobileCoinAddress(signedPaymentAddress.getMobileCoinAddress()
                                                                                                                         .toBuilder()
                                                                                                                         .setSignature(ByteString.copyFrom(signature)))
                                                                               .build();

    assertThatThrownBy(() -> MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(tamperedSignature, identityKeyPair.getPublicKey()))
            .isInstanceOf(PaymentsAddressException.class)
            .hasMessage("Invalid MobileCoin address signature on payments address proto");
  }

  @Test
  public void can_not_verify_a_tampered_address() {
    IdentityKeyPair                    identityKeyPair      = IdentityKeyUtil.generateIdentityKeyPair();
    byte[]                             addressBytes         = Util.getSecretBytes(100);
    SignalServiceProtos.PaymentAddress signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(addressBytes, identityKeyPair);

    byte[] address = signedPaymentAddress.getMobileCoinAddress().getAddress().toByteArray();
    address[0] = (byte) (address[0] ^ 0x01);
    SignalServiceProtos.PaymentAddress tamperedAddress = signedPaymentAddress.toBuilder()
                                                                             .setMobileCoinAddress(signedPaymentAddress.getMobileCoinAddress()
                                                                                                                       .toBuilder()
                                                                                                                       .setAddress(ByteString.copyFrom(address)))
                                                                             .build();

    assertThatThrownBy(() -> MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(tamperedAddress, identityKeyPair.getPublicKey()))
            .isInstanceOf(PaymentsAddressException.class)
            .hasMessage("Invalid MobileCoin address signature on payments address proto");
  }

  @Test
  public void can_not_verify_a_missing_signature() {
    IdentityKeyPair                    identityKeyPair      = IdentityKeyUtil.generateIdentityKeyPair();
    byte[]                             address              = Util.getSecretBytes(100);
    SignalServiceProtos.PaymentAddress signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair);

    SignalServiceProtos.PaymentAddress removedSignature = signedPaymentAddress.toBuilder()
                                                                              .setMobileCoinAddress(signedPaymentAddress.getMobileCoinAddress()
                                                                                                                        .toBuilder()
                                                                                                                        .clearSignature())
                                                                              .build();

    assertThatThrownBy(() -> MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(removedSignature, identityKeyPair.getPublicKey()))
            .isInstanceOf(PaymentsAddressException.class)
            .hasMessage("Invalid MobileCoin address signature on payments address proto");
  }
  
  @Test
  public void can_not_verify_a_missing_address() {
    IdentityKeyPair                    identityKeyPair      = IdentityKeyUtil.generateIdentityKeyPair();
    byte[]                             address              = Util.getSecretBytes(100);
    SignalServiceProtos.PaymentAddress signedPaymentAddress = MobileCoinPublicAddressProfileUtil.signPaymentsAddress(address, identityKeyPair);

    SignalServiceProtos.PaymentAddress removedAddress = signedPaymentAddress.toBuilder()
                                                                              .setMobileCoinAddress(signedPaymentAddress.getMobileCoinAddress()
                                                                                                                        .toBuilder()
                                                                                                                        .clearAddress())
                                                                              .build();

    assertThatThrownBy(() -> MobileCoinPublicAddressProfileUtil.verifyPaymentsAddress(removedAddress, identityKeyPair.getPublicKey()))
            .isInstanceOf(PaymentsAddressException.class)
            .hasMessage("Invalid MobileCoin address signature on payments address proto");
  }
}
