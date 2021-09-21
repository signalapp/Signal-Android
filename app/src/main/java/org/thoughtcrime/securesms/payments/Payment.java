package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.ComparatorCompat;

import org.thoughtcrime.securesms.payments.proto.PaymentMetaData;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.Comparator;
import java.util.UUID;

/**
 * Represents one payment as displayed to the user.
 * <p>
 * It could be from a sent or received Signal payment message or reconstructed.
 */
public interface Payment {
  Comparator<Payment> UNKNOWN_BLOCK_INDEX_FIRST            = (a, b) -> Boolean.compare(b.getBlockIndex() == 0, a.getBlockIndex() == 0);
  Comparator<Payment> ASCENDING_BLOCK_INDEX                = (a, b) -> Long.compare(a.getBlockIndex(), b.getBlockIndex());
  Comparator<Payment> DESCENDING_BLOCK_INDEX               = ComparatorCompat.reversed(ASCENDING_BLOCK_INDEX);
  Comparator<Payment> DESCENDING_BLOCK_INDEX_UNKNOWN_FIRST = ComparatorCompat.chain(UNKNOWN_BLOCK_INDEX_FIRST)
                                                                             .thenComparing(DESCENDING_BLOCK_INDEX);

  @NonNull UUID getUuid();

  @NonNull Payee getPayee();

  long getBlockIndex();

  long getBlockTimestamp();

  long getTimestamp();

  default long getDisplayTimestamp() {
    long blockTimestamp = getBlockTimestamp();
    if (blockTimestamp > 0) {
      return blockTimestamp;
    } else {
      return getTimestamp();
    }
  }

  @NonNull Direction getDirection();

  @NonNull State getState();

  @Nullable FailureReason getFailureReason();

  @NonNull String getNote();

  /**
   * Always >= 0, does not include fee
   */
  @NonNull Money getAmount();

  /**
   * Always >= 0
   */
  @NonNull Money getFee();

  @NonNull PaymentMetaData getPaymentMetaData();

  boolean isSeen();

  /**
   * Negative if sent, positive if received.
   */
  default @NonNull Money getAmountWithDirection() {
    switch (getDirection()) {
      case SENT    : return getAmount().negate();
      case RECEIVED: return getAmount();
      default      : throw new AssertionError();
    }
  }

  /**
   * Negative if sent including fee, positive if received.
   */
  default @NonNull Money getAmountPlusFeeWithDirection() {
    switch (getDirection()) {
      case SENT    : return getAmount().add(getFee()).negate();
      case RECEIVED: return getAmount();
      default      : throw new AssertionError();
    }
  }

  default boolean isDefrag() {
    return getDirection() == Direction.SENT &&
           getPayee().hasRecipientId() &&
           getPayee().requireRecipientId().equals(Recipient.self().getId());
  }
}
