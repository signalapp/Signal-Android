package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import java.util.List;

/**
 * A payment may be comprised of zero or more defrag transactions and the payment transaction.
 * <p>
 * Or a number of successful transactions and a failed transaction.
 */
public final class PaymentSubmissionResult {

  private final List<TransactionSubmissionResult> defrags;
  private final TransactionSubmissionResult       nonDefrag;
  private final TransactionSubmissionResult       erroredTransaction;

  PaymentSubmissionResult(@NonNull List<TransactionSubmissionResult> transactions) {
    if (transactions.isEmpty()) {
      throw new IllegalStateException();
    }
    this.defrags            = Stream.of(transactions)
                                    .filter(TransactionSubmissionResult::isDefrag)
                                    .toList();
    this.nonDefrag          = Stream.of(transactions)
                                    .filterNot(TransactionSubmissionResult::isDefrag)
                                    .findSingle()
                                    .orElse(null);
    this.erroredTransaction = Stream.of(transactions)
                                    .filter(t -> t.getErrorCode() != TransactionSubmissionResult.ErrorCode.NONE)
                                    .findSingle()
                                    .orElse(null);
  }

  public List<TransactionSubmissionResult> defrags() {
    return defrags;
  }

  public boolean containsDefrags() {
    return defrags.size() > 0;
  }

  public @Nullable TransactionSubmissionResult getNonDefrag() {
    return nonDefrag;
  }

  /**
   * Could return the error that happened during a defrag or the main transaction.
   */
  public TransactionSubmissionResult.ErrorCode getErrorCode() {
    return erroredTransaction != null ? erroredTransaction.getErrorCode()
                                      : TransactionSubmissionResult.ErrorCode.NONE;
  }
}
