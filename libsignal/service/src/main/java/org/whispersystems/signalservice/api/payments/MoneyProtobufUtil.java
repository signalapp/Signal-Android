package org.whispersystems.signalservice.api.payments;

import org.whispersystems.signalservice.api.util.Uint64RangeException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public final class MoneyProtobufUtil {

  public static SignalServiceProtos.DataMessage.Payment.Amount moneyToPaymentAmount(Money money) {
    SignalServiceProtos.DataMessage.Payment.Amount.Builder builder = SignalServiceProtos.DataMessage.Payment.Amount.newBuilder();

    if (money instanceof Money.MobileCoin) {
      try {
        builder.setMobileCoin(SignalServiceProtos.DataMessage.Payment.Amount.MobileCoin.newBuilder()
                                                                                       .setPicoMob(money.requireMobileCoin()
                                                                                                        .toPicoMobUint64()));
      } catch (Uint64RangeException e) {
        throw new AssertionError(e);
      }
    } else {
      throw new AssertionError();
    }

    return builder.build();
  }

    public static Money paymentAmountToMoney(SignalServiceProtos.DataMessage.Payment.Amount amount) throws UnsupportedCurrencyException {
      switch (amount.getAmountCase()) {
        case MOBILECOIN:
          return Money.picoMobileCoin(amount.getMobileCoin().getPicoMob());
        case AMOUNT_NOT_SET:
        default:
          throw new UnsupportedCurrencyException();
      }
  }
}
