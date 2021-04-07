package org.thoughtcrime.securesms.payments;

import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.currency.FiatMoney;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.payments.Money;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

public class FiatMoneyUtil {

  private static final String TAG = Log.tag(FiatMoneyUtil.class);

  public static @NonNull LiveData<Optional<FiatMoney>> getExchange(@NonNull LiveData<Money> amount) {
    return LiveDataUtil.mapAsync(amount, a -> {
      try {
        return ApplicationDependencies.getPayments()
                                      .getCurrencyExchange(false)
                                      .getExchangeRate(SignalStore.paymentsValues().currentCurrency())
                                      .exchange(a);
      } catch (IOException e) {
        Log.w(TAG, e);
        return Optional.absent();
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

    String formattedAmount = formatter.format(amount.getAmount());
    if (amount.getTimestamp() > 0 && options.displayTime) {
      return resources.getString(R.string.CurrencyAmountFormatter_s_at_s,
                                 formattedAmount,
                                 DateUtils.getTimeString(ApplicationDependencies.getApplication(), Locale.getDefault(), amount.getTimestamp()));
    }
    return formattedAmount;
  }

  public static FormatOptions formatOptions() {
    return new FormatOptions();
  }

  public static class FormatOptions {
    private boolean displayTime = true;
    private boolean withSymbol  = true;

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
  }
}
