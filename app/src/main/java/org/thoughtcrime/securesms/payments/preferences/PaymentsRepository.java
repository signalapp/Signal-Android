package org.thoughtcrime.securesms.payments.preferences;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.PaymentTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.Direction;
import org.thoughtcrime.securesms.payments.MobileCoinLedgerWrapper;
import org.thoughtcrime.securesms.payments.Payment;
import org.thoughtcrime.securesms.payments.reconciliation.LedgerReconcile;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * General repository for accessing payment information.
 */
public class PaymentsRepository {

  private static final String TAG = Log.tag(PaymentsRepository.class);

  private final PaymentTable            paymentDatabase;
  private final LiveData<List<Payment>> recentPayments;
  private final LiveData<List<Payment>> recentSentPayments;
  private final LiveData<List<Payment>> recentReceivedPayments;

  public PaymentsRepository() {
    paymentDatabase = SignalDatabase.payments();

    LiveData<List<PaymentTable.PaymentTransaction>> localPayments = paymentDatabase.getAllLive();
    LiveData<MobileCoinLedgerWrapper>               ledger        = SignalStore.payments().liveMobileCoinLedger();

    //noinspection NullableProblems
    this.recentPayments         = LiveDataUtil.mapAsync(LiveDataUtil.combineLatest(localPayments, ledger, Pair::create), p -> reconcile(p.first, p.second));
    this.recentSentPayments     = LiveDataUtil.mapAsync(this.recentPayments, p -> filterPayments(p, Direction.SENT));
    this.recentReceivedPayments = LiveDataUtil.mapAsync(this.recentPayments, p -> filterPayments(p, Direction.RECEIVED));
  }

  @WorkerThread
  private @NonNull List<Payment> reconcile(@NonNull Collection<PaymentTable.PaymentTransaction> paymentTransactions, @NonNull MobileCoinLedgerWrapper ledger) {
    List<Payment> reconcile = LedgerReconcile.reconcile(paymentTransactions, ledger);

    updateDatabaseWithNewBlockInformation(reconcile);

    return reconcile;
  }

  private void updateDatabaseWithNewBlockInformation(@NonNull List<Payment> reconcileOutput) {
    List<LedgerReconcile.BlockOverridePayment> blockOverridePayments = Stream.of(reconcileOutput)
                                                                             .select(LedgerReconcile.BlockOverridePayment.class)
                                                                             .toList();

    if (blockOverridePayments.isEmpty()) {
      return;
    }
    Log.i(TAG, String.format(Locale.US, "%d payments have new block index or timestamp information", blockOverridePayments.size()));

    for (LedgerReconcile.BlockOverridePayment blockOverridePayment : blockOverridePayments) {
      Payment inner    = blockOverridePayment.getInner();
      boolean override = false;
      if (inner.getBlockIndex() != blockOverridePayment.getBlockIndex()) {
        override = true;
      }
      if (inner.getBlockTimestamp() != blockOverridePayment.getBlockTimestamp()) {
        override = true;
      }
      if (!override) {
        Log.w(TAG, "  Unnecessary");
      } else {
        if (paymentDatabase.updateBlockDetails(inner.getUuid(), blockOverridePayment.getBlockIndex(), blockOverridePayment.getBlockTimestamp())) {
          Log.d(TAG, "  Updated block details for " + inner.getUuid());
        } else {
          Log.w(TAG, "  Failed to update block details for " + inner.getUuid());
        }
      }
    }
  }

  public @NonNull LiveData<List<Payment>> getRecentPayments() {
    return recentPayments;
  }

  public @NonNull LiveData<List<Payment>> getRecentSentPayments() {
    return recentSentPayments;
  }

  public @NonNull LiveData<List<Payment>> getRecentReceivedPayments() {
    return recentReceivedPayments;
  }

  private @NonNull List<Payment> filterPayments(@NonNull List<Payment> payments,
                                                @NonNull Direction direction)
  {
    return Stream.of(payments)
                 .filter(p -> p.getDirection() == direction)
                 .toList();
  }
}
