package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public final class MobileCoinPublicAddressProfileUtil {

  private MobileCoinPublicAddressProfileUtil() {}

  /**
   * Signs the supplied address bytes with the {@link IdentityKeyPair}'s private key and returns a proto that includes it and it's signature.
   */
  public static @NonNull SignalServiceProtos.PaymentAddress signPaymentsAddress(@NonNull byte[] publicAddressBytes,
                                                                                @NonNull IdentityKeyPair identityKeyPair)
  {
    byte[] signature = identityKeyPair.getPrivateKey().calculateSignature(publicAddressBytes);

    return SignalServiceProtos.PaymentAddress.newBuilder()
                                             .setMobileCoinAddress(SignalServiceProtos.PaymentAddress.MobileCoinAddress.newBuilder()
                                                                                                                       .setAddress(ByteString.copyFrom(publicAddressBytes))
                                                                                                                       .setSignature(ByteString.copyFrom(signature)))
                                             .build();
  }

  /**
   * Verifies that the payments address is signed with the supplied {@link IdentityKey}.
   * <p>
   * Returns the validated bytes if so, otherwise throws.
   */
  public static @NonNull byte[] verifyPaymentsAddress(@NonNull SignalServiceProtos.PaymentAddress paymentAddress,
                                                      @NonNull IdentityKey identityKey)
          throws PaymentsAddressException
  {
    if (!paymentAddress.hasMobileCoinAddress()) {
      throw new PaymentsAddressException(PaymentsAddressException.Code.NO_ADDRESS);
    }

    byte[] bytes     = paymentAddress.getMobileCoinAddress().getAddress().toByteArray();
    byte[] signature = paymentAddress.getMobileCoinAddress().getSignature().toByteArray();

    if (signature.length != 64 || !identityKey.getPublicKey().verifySignature(bytes, signature)) {
      throw new PaymentsAddressException(PaymentsAddressException.Code.INVALID_ADDRESS_SIGNATURE);
    }

    return bytes;
  }
}
