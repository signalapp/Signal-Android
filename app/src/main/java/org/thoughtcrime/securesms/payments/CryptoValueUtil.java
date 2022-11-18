package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.databaseprotos.CryptoValue;
import org.whispersystems.signalservice.api.payments.Money;

import java.math.BigInteger;

/**
 * Converts from database protobuf type {@link CryptoValue} to and from other types.
 */
public final class CryptoValueUtil {

  private CryptoValueUtil() {
  }

  public static @NonNull CryptoValue moneyToCryptoValue(@NonNull Money money) {
    CryptoValue.Builder builder = CryptoValue.newBuilder();

    if (money instanceof Money.MobileCoin) {
      Money.MobileCoin mobileCoin = (Money.MobileCoin) money;
      builder.setMobileCoinValue(CryptoValue.MobileCoinValue
                                            .newBuilder()
                                            .setPicoMobileCoin(mobileCoin.serializeAmountString()));
    }

    return builder.build();
  }

  public static @NonNull Money cryptoValueToMoney(@NonNull CryptoValue amount) {
    CryptoValue.ValueCase valueCase = amount.getValueCase();

    switch (valueCase) {
      case MOBILECOINVALUE:
        return Money.picoMobileCoin(new BigInteger(amount.getMobileCoinValue().getPicoMobileCoin()));
      case VALUE_NOT_SET:
        throw new AssertionError();
    }

    throw new AssertionError();
  }
}
