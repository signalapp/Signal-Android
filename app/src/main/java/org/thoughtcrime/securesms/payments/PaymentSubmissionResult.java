package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.stream.Collectors;

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
    this.defrags            = transactions.stream()
                                    .filter(TransactionSubmissionResult::isDefrag).collect(Collectors.toList());
    final List<TransactionSubmissionResult> nonDefragTransactions = transactions.stream().filter(x -> !x.isDefrag()).collect(Collectors.toList());
    if (nonDefragTransactions.size() > 1) throw new IllegalStateException("Too many defrag transaction results!");
    this.nonDefrag = nonDefragTransactions.isEmpty() ? null : nonDefragTransactions.get(0);

    final List<TransactionSubmissionResult> erroredTransactions = transactions.stream().filter(
        t -> t.getErrorCode() != TransactionSubmissionResult.ErrorCode.NONE).collect(Collectors.toList());
    if (erroredTransactions.size() > 1) throw new IllegalStateException("Too many errored transaction results!");
    this.erroredTransaction = erroredTransactions.isEmpty() ? null : erroredTransactions.get(0);
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
