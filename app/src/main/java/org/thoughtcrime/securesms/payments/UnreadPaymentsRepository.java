package org.thoughtcrime.securesms.payments;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;

import java.util.UUID;
import java.util.concurrent.Executor;

public class UnreadPaymentsRepository {

  private static final Executor EXECUTOR = SignalExecutors.BOUNDED;

  public void markAllPaymentsSeen() {
    EXECUTOR.execute(this::markAllPaymentsSeenInternal);
  }

  public void markPaymentSeen(@NonNull UUID paymentId) {
    EXECUTOR.execute(() -> markPaymentSeenInternal(paymentId));
  }

  @WorkerThread
  private void markAllPaymentsSeenInternal() {
    Context context = AppDependencies.getApplication();
    SignalDatabase.payments().markAllSeen();
  }

  @WorkerThread
  private void markPaymentSeenInternal(@NonNull UUID paymentId) {
    Context context = AppDependencies.getApplication();
    SignalDatabase.payments().markPaymentSeen(paymentId);
  }

}
