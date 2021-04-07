package org.thoughtcrime.securesms.payments.history;

import androidx.annotation.NonNull;

import com.annimon.stream.ComparatorCompat;

import org.thoughtcrime.securesms.payments.Direction;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class TransactionReconstruction {
  private final          List<Transaction> received;
  @NonNull private final List<Transaction> allTransactions;
  private final          List<Transaction> sent;

  /**
   * Given some unaccounted for TXO values within the same block separated into {@param spent} and {@param unspent}, estimates a sensible grouping for display
   * to the user.
   */
  public static TransactionReconstruction estimateBlockLevelActivity(@NonNull List<Money.MobileCoin> spent,
                                                                     @NonNull List<Money.MobileCoin> unspent)
  {
    Money.MobileCoin totalSpent = Money.MobileCoin.sum(spent);

    List<Money.MobileCoin> unspentDescending = new ArrayList<>(unspent);
    Collections.sort(unspentDescending, Money.MobileCoin.DESCENDING);

    List<Transaction> received = new ArrayList<>(unspent.size());

    for (Money.MobileCoin unspentValue : unspentDescending) {
      if (unspentValue.lessThan(totalSpent)) {
        totalSpent = totalSpent.subtract(unspentValue).requireMobileCoin();
      } else if (unspentValue.isPositive()) {
        received.add(new Transaction(unspentValue, Direction.RECEIVED));
      }
    }

    List<Transaction> sent = totalSpent.isPositive() ? Collections.singletonList(new Transaction(totalSpent, Direction.SENT))
                                                     : Collections.emptyList();

    Collections.sort(received, Transaction.ORDER);

    List<Transaction> allTransactions = new ArrayList<>(sent.size() + received.size());
    allTransactions.addAll(sent);
    allTransactions.addAll(received);
    Collections.sort(allTransactions, Transaction.ORDER);

    return new TransactionReconstruction(sent, received, allTransactions);
  }

  private TransactionReconstruction(@NonNull List<Transaction> sent,
                                    @NonNull List<Transaction> received,
                                    @NonNull List<Transaction> allTransactions)
  {
    this.sent            = sent;
    this.received        = received;
    this.allTransactions = allTransactions;
  }

  public @NonNull List<Transaction> received() {
    return new ArrayList<>(received);
  }

  public @NonNull List<Transaction> sent() {
    return new ArrayList<>(sent);
  }

  public @NonNull List<Transaction> getAllTransactions() {
    return new ArrayList<>(allTransactions);
  }

  public static final class Transaction {
    private static final Comparator<Transaction> RECEIVED_FIRST = (a, b) -> b.getDirection().compareTo(a.direction);
    private static final Comparator<Transaction> ABSOLUTE_SIZE  = (a, b) -> Money.MobileCoin.ASCENDING.compare(a.value, b.value);

    /**
     * Received first so that if going through a list and keeping a running balance, the order of transactions will not cause that balance to go into negative.
     * <p>
     * Then smaller first is just to show more important ones higher on a reversed list.
     */
    public static final Comparator<Transaction> ORDER = ComparatorCompat.chain(RECEIVED_FIRST)
                                                                        .thenComparing(ABSOLUTE_SIZE);

    private final Money.MobileCoin value;
    private final Direction        direction;

    private Transaction(@NonNull Money.MobileCoin value,
                        @NonNull Direction direction)
    {
      this.value     = value;
      this.direction = direction;
    }

    public @NonNull Money.MobileCoin getValue() {
      return value;
    }

    public @NonNull Direction getDirection() {
      return direction;
    }

    public @NonNull Money.MobileCoin getValueWithDirection() {
      return direction == Direction.SENT ? value.negate() : value;
    }

    @Override
    public @NonNull String toString() {
      return "Transaction{" +
             value +
             ", " + direction +
             '}';
    }
  }
}
