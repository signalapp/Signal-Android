package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.currency.CurrencyExchange;
import org.whispersystems.signalservice.api.payments.CurrencyConversion;
import org.whispersystems.signalservice.api.payments.CurrencyConversions;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class Payments {

  private static final String TAG = Log.tag(Payments.class);

  private static final long MINIMUM_ELAPSED_TIME_BETWEEN_REFRESH = TimeUnit.MINUTES.toMillis(1);

  private final MobileCoinConfig mobileCoinConfig;

  private Wallet              wallet;
  private CurrencyConversions currencyConversions;

  public Payments(@NonNull MobileCoinConfig mobileCoinConfig) {
    this.mobileCoinConfig = mobileCoinConfig;
  }

  public synchronized Wallet getWallet() {
    if (wallet != null) {
      return wallet;
    }
    Entropy paymentsEntropy = SignalStore.payments().getPaymentsEntropy();
    wallet = new Wallet(mobileCoinConfig, Objects.requireNonNull(paymentsEntropy));
    return wallet;
  }

  public synchronized void closeWallet() {
    wallet = null;
  }

  @WorkerThread
  public synchronized @NonNull CurrencyExchange getCurrencyExchange(boolean refreshIfAble) throws IOException {
    if (currencyConversions == null || shouldRefresh(refreshIfAble, currencyConversions.getTimestamp())) {
      Log.i(TAG, "Currency conversion data is unavailable or a refresh was requested and available");
      CurrencyConversions newCurrencyConversions = AppDependencies.getSignalServiceAccountManager().getCurrencyConversions();
      if (currencyConversions == null || (newCurrencyConversions != null && newCurrencyConversions.getTimestamp() > currencyConversions.getTimestamp())) {
        currencyConversions = newCurrencyConversions;
      }
    }

    if (currencyConversions != null) {
      for (CurrencyConversion currencyConversion : currencyConversions.getCurrencies()) {
        if ("MOB".equals(currencyConversion.getBase())) {
          return new CurrencyExchange(currencyConversion.getConversions(), currencyConversions.getTimestamp());
        }
      }
    }

    throw new IOException("Unable to retrieve currency conversions");
  }

  private boolean shouldRefresh(boolean refreshIfAble, long lastRefreshTime) {
    return refreshIfAble && System.currentTimeMillis() - lastRefreshTime >= MINIMUM_ELAPSED_TIME_BETWEEN_REFRESH;
  }
}
