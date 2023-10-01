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
    CryptoValue.Builder builder = new CryptoValue.Builder();

    if (money instanceof Money.MobileCoin) {
      Money.MobileCoin mobileCoin = (Money.MobileCoin) money;
      builder.mobileCoinValue(new CryptoValue.MobileCoinValue.Builder().picoMobileCoin(mobileCoin.serializeAmountString()).build());
    }

    return builder.build();
  }

  public static @NonNull Money cryptoValueToMoney(@NonNull CryptoValue amount) {
    if (amount.mobileCoinValue != null) {
      return Money.picoMobileCoin(new BigInteger(amount.mobileCoinValue.picoMobileCoin));
    } else {
      throw new AssertionError();
    }
  }
}
