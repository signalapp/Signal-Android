package org.thoughtcrime.securesms.payments.currency;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.payments.Payments;
import org.thoughtcrime.securesms.util.AsynchronousCallback;

import java.io.IOException;

public final class CurrencyExchangeRepository {

  private static final String TAG = Log.tag(CurrencyExchangeRepository.class);

  private final Payments payments;

  public CurrencyExchangeRepository(@NonNull Payments payments) {
    this.payments = payments;
  }

  @AnyThread
  public void getCurrencyExchange(@NonNull AsynchronousCallback.WorkerThread<CurrencyExchange, Throwable> callback, boolean refreshIfAble) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        callback.onComplete(payments.getCurrencyExchange(refreshIfAble));
      } catch (IOException e) {
        Log.w(TAG, e);
        callback.onError(e);
      }
    });
  }
}
