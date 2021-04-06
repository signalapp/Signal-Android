package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class TransactionSubmissionResult {

  private final PaymentTransactionId transaction;
  private final ErrorCode            code;
  private final boolean              defrag;

  private TransactionSubmissionResult(@Nullable PaymentTransactionId transaction, @NonNull ErrorCode code, boolean defrag) {
    this.transaction = transaction;
    this.code        = code;
    this.defrag      = defrag;
  }

  static TransactionSubmissionResult successfullySubmittedDefrag(@NonNull PaymentTransactionId transaction) {
    return new TransactionSubmissionResult(transaction, ErrorCode.NONE, true);
  }

  static @NonNull TransactionSubmissionResult successfullySubmitted(@NonNull PaymentTransactionId transaction) {
    return new TransactionSubmissionResult(transaction, ErrorCode.NONE, false);
  }

  static @NonNull TransactionSubmissionResult failure(@NonNull ErrorCode code, boolean defrag) {
    return new TransactionSubmissionResult(null, code, defrag);
  }

  public @NonNull PaymentTransactionId getTransactionId() {
    if (transaction == null) {
      throw new IllegalStateException();
    }
    return transaction;
  }

  public @NonNull ErrorCode getErrorCode() {
    return code;
  }

  public boolean isDefrag() {
    return defrag;
  }

  public enum ErrorCode {
    INSUFFICIENT_FUNDS,
    GENERIC_FAILURE,
    NETWORK_FAILURE,
    NONE
  }
}
