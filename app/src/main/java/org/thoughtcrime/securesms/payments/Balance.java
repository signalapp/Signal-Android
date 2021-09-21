package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;

import org.whispersystems.signalservice.api.payments.Money;

public final class Balance {
  private final Money fullAmount;
  private final Money transferableAmount;
  private final long  checkedAt;

  public Balance(@NonNull Money fullAmount, @NonNull Money transferableAmount, long checkedAt) {
    this.fullAmount         = fullAmount;
    this.transferableAmount = transferableAmount;
    this.checkedAt          = checkedAt;
  }

  public @NonNull Money getFullAmount() {
    return fullAmount;
  }

  /**
   * Full amount minus estimated fees required to send all funds.
   */
  public @NonNull Money getTransferableAmount() {
    return transferableAmount;
  }

  public long getCheckedAt() {
    return checkedAt;
  }

  @Override
  public String toString() {
    return "Balance{" +
           "fullAmount=" + fullAmount +
           ", transferableAmount=" + transferableAmount +
           ", checkedAt=" + checkedAt +
           '}';
  }
}
