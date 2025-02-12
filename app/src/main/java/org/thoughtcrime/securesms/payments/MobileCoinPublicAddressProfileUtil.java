package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;

import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.whispersystems.signalservice.internal.push.PaymentAddress;

import okio.ByteString;

public final class MobileCoinPublicAddressProfileUtil {

  private MobileCoinPublicAddressProfileUtil() {}

  /**
   * Signs the supplied address bytes with the {@link IdentityKeyPair}'s private key and returns a proto that includes it and it's signature.
   */
  public static @NonNull PaymentAddress signPaymentsAddress(@NonNull byte[] publicAddressBytes,
                                                            @NonNull IdentityKeyPair identityKeyPair)
  {
    byte[] signature = identityKeyPair.getPrivateKey().calculateSignature(publicAddressBytes);

    return new PaymentAddress.Builder()
                             .mobileCoin(new PaymentAddress.MobileCoin.Builder()
                                             .publicAddress(ByteString.of(publicAddressBytes))
                                             .signature(ByteString.of(signature))
                                             .build())
                             .build();
  }

  /**
   * Verifies that the payments address is signed with the supplied {@link IdentityKey}.
   * <p>
   * Returns the validated bytes if so, otherwise throws.
   */
  public static @NonNull byte[] verifyPaymentsAddress(@NonNull PaymentAddress paymentAddress,
                                                      @NonNull IdentityKey identityKey)
      throws PaymentsAddressException
  {
    if (paymentAddress.mobileCoin == null) {
      throw new PaymentsAddressException(PaymentsAddressException.Code.NO_ADDRESS);
    }

    if (paymentAddress.mobileCoin.publicAddress == null || paymentAddress.mobileCoin.signature == null) {
      throw new PaymentsAddressException(PaymentsAddressException.Code.INVALID_ADDRESS_SIGNATURE);
    }

    byte[] bytes     = paymentAddress.mobileCoin.publicAddress.toByteArray();
    byte[] signature = paymentAddress.mobileCoin.signature.toByteArray();

    if (signature.length != 64 || !identityKey.getPublicKey().verifySignature(bytes, signature)) {
      throw new PaymentsAddressException(PaymentsAddressException.Code.INVALID_ADDRESS_SIGNATURE);
    }

    return bytes;
  }
}
