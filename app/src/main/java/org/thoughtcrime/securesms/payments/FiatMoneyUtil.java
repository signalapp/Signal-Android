package org.thoughtcrime.securesms.payments;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import org.signal.core.util.logging.Log;
import org.signal.core.util.money.FiatMoney;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.signalservice.api.payments.Money;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;

public final class FiatMoneyUtil {

  private static final String TAG = Log.tag(FiatMoneyUtil.class);

  private static final char CURRENCY_SYMBOL_PLACE_HOLDER = '\u00A4';
  private static final char NON_BREAKING_WHITESPACE      = '\u00A0';

  private FiatMoneyUtil() {}

  public static @NonNull LiveData<Optional<FiatMoney>> getExchange(@NonNull LiveData<Money> amount) {
    return LiveDataUtil.mapAsync(amount, a -> {
      try {
        return AppDependencies.getPayments()
                              .getCurrencyExchange(false)
                              .getExchangeRate(SignalStore.payments().currentCurrency())
                              .exchange(a);
      } catch (IOException e) {
        Log.w(TAG, e);
        return Optional.empty();
      }
    });
  }

  public static @NonNull String format(@NonNull Resources resources, @NonNull FiatMoney amount) {
    return format(resources, amount, new FormatOptions());
  }

  public static @NonNull String format(@NonNull Resources resources, @NonNull FiatMoney amount, @NonNull FormatOptions options) {
    final NumberFormat formatter;

    if (options.withSymbol) {
      formatter = NumberFormat.getCurrencyInstance();
      formatter.setCurrency(amount.getCurrency());
    } else {
      formatter = NumberFormat.getNumberInstance();
      formatter.setMinimumFractionDigits(amount.getCurrency().getDefaultFractionDigits());
    }

    if (options.trimZerosAfterDecimal) {
      formatter.setMinimumFractionDigits(0);
      formatter.setMaximumFractionDigits(amount.getCurrency().getDefaultFractionDigits());
    }

    String formattedAmount = formatter.format(amount.getAmount());
    if (amount.getTimestamp() > 0 && options.displayTime) {
      return resources.getString(R.string.CurrencyAmountFormatter_s_at_s,
                                 formattedAmount,
                                 DateUtils.getTimeString(AppDependencies.getApplication(), Locale.getDefault(), amount.getTimestamp()));
    }
    return formattedAmount;
  }

  /**
   * Prefixes or postfixes the currency symbol based on the formatter for the currency.
   *
   * @param value String so that you can force trailing zeros.
   */
  public static String manualFormat(@NonNull Currency currency, @NonNull String value) {
    NumberFormat format = NumberFormat.getCurrencyInstance();
    format.setCurrency(currency);

    DecimalFormat        decimalFormat        = (DecimalFormat) format;
    DecimalFormatSymbols decimalFormatSymbols = decimalFormat.getDecimalFormatSymbols();
    String               symbol               = decimalFormatSymbols.getCurrencySymbol();
    String               localizedPattern     = decimalFormat.toLocalizedPattern();
    int                  currencySymbolIndex  = localizedPattern.indexOf(CURRENCY_SYMBOL_PLACE_HOLDER);
    boolean              prefixSymbol         = currencySymbolIndex <= 0;

    if (currencySymbolIndex == 0) {
      char cAfterSymbol = localizedPattern.charAt(currencySymbolIndex + 1);
      if (Character.isWhitespace(cAfterSymbol) || cAfterSymbol == NON_BREAKING_WHITESPACE) {
        symbol = symbol + cAfterSymbol;
      }
    } else if (currencySymbolIndex > 0) {
      char cBeforeSymbol = localizedPattern.charAt(currencySymbolIndex - 1);
      if (Character.isWhitespace(cBeforeSymbol) || cBeforeSymbol == NON_BREAKING_WHITESPACE) {
        symbol = cBeforeSymbol + symbol;
      }
    }

    return prefixSymbol ? symbol + value
                        : value + symbol;
  }

  public static FormatOptions formatOptions() {
    return new FormatOptions();
  }

  public static class FormatOptions {
    private boolean displayTime           = true;
    private boolean withSymbol            = true;
    private boolean trimZerosAfterDecimal = false;

    private FormatOptions() {
    }

    public @NonNull FormatOptions withDisplayTime(boolean enabled) {
      this.displayTime = enabled;
      return this;
    }

    public @NonNull FormatOptions numberOnly() {
      this.withSymbol = false;
      return this;
    }

    public @NonNull FormatOptions trimZerosAfterDecimal() {
      this.trimZerosAfterDecimal = true;
      return this;
    }
  }
}
