package org.thoughtcrime.securesms.payments.currency;

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.util.Currency;

public class FiatMoney {
  private final BigDecimal amount;
  private final Currency   currency;
  private final long       timestamp;

  public FiatMoney(@NonNull BigDecimal amount, @NonNull Currency currency) {
    this(amount, currency, 0);
  }

  public FiatMoney(@NonNull BigDecimal amount, @NonNull Currency currency, long timestamp) {
    this.amount    = amount;
    this.currency  = currency;
    this.timestamp = timestamp;
  }

  public @NonNull BigDecimal getAmount() {
    return amount;
  }

  public @NonNull Currency getCurrency() {
    return currency;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
