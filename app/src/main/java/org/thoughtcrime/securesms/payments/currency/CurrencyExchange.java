package org.thoughtcrime.securesms.payments.currency;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.signal.core.util.money.FiatMoney;
import org.whispersystems.signalservice.api.payments.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CurrencyExchange {
  private final Map<String, BigDecimal> conversions;
  private final List<Currency>          supportedCurrencies;
  private final long                    timestamp;

  public CurrencyExchange(@NonNull Map<String, Double> conversions, long timestamp) {
    this.conversions         = new HashMap<>(conversions.size());
    this.supportedCurrencies = new ArrayList<>(conversions.size());
    this.timestamp           = timestamp;

    for (Map.Entry<String, Double> entry : conversions.entrySet()) {
      if (entry.getValue() != null) {
        this.conversions.put(entry.getKey(), BigDecimal.valueOf(entry.getValue()));

        Currency currency = CurrencyUtil.getCurrencyByCurrencyCode(entry.getKey());
        if (currency != null && SupportedCurrencies.ALL.contains(currency.getCurrencyCode())) {
          supportedCurrencies.add(currency);
        }
      }
    }
  }

  public @NonNull ExchangeRate getExchangeRate(@NonNull Currency currency) {
    return new ExchangeRate(currency, conversions.get(currency.getCurrencyCode()), timestamp);
  }

  public @NonNull List<Currency> getSupportedCurrencies() {
    return supportedCurrencies;
  }

  public static final class ExchangeRate {

    private final Currency   currency;
    private final BigDecimal rate;
    private final long       timestamp;

    @VisibleForTesting ExchangeRate(@NonNull Currency currency, @Nullable BigDecimal rate, long timestamp) {
      this.currency  = currency;
      this.rate      = rate;
      this.timestamp = timestamp;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public @NonNull Optional<FiatMoney> exchange(@NonNull Money money) {
      BigDecimal amount = money.requireMobileCoin().toBigDecimal();

      if (rate != null) {
        return Optional.of(new FiatMoney(amount.multiply(rate).setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN), currency, timestamp));
      }
      return Optional.empty();
    }

    public @NonNull Optional<Money> exchange(@NonNull FiatMoney fiatMoney) {
      if (rate != null) {
        return Optional.of(Money.mobileCoin(fiatMoney.getAmount().setScale(12, RoundingMode.HALF_EVEN).divide(rate, RoundingMode.HALF_EVEN)));
      } else {
        return Optional.empty();
      }
    }
  }
}
