package org.thoughtcrime.securesms.payments.history;

import androidx.annotation.NonNull;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.thoughtcrime.securesms.payments.Direction;
import org.whispersystems.signalservice.api.payments.Formatter;
import org.whispersystems.signalservice.api.payments.FormatterOptions;
import org.whispersystems.signalservice.api.payments.Money;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static java.util.stream.IntStream.range;

@RunWith(Parameterized.class)
public final class BlockTransactionReconstructionTests {

  private final static Formatter MONEY_FORMATTER = Money.MobileCoin.CURRENCY.getFormatter(FormatterOptions.builder(Locale.US).build());

  @Parameterized.Parameter(0)
  public SpentList spentTransactionOutputs;

  @Parameterized.Parameter(1)
  public UnspentList unspentTransactionOutputs;

  @Parameterized.Parameter(2)
  public SentList expectedSentTransactions;

  @Parameterized.Parameter(3)
  public ReceivedList expectedReceivedTransactions;

  @Parameterized.Parameters(name = "Spent: {0}, Unspent: {1} => Sent: {2}, Received: {3}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
            {spent(1), unspent(), sent(1), received()},
            {spent(1, 2, 3), unspent(), sent(6), received()},
            {spent(range(1, 30).boxed().toArray()), unspent(), sent(435), received()},
            {spent(1, 2, 3), unspent(0.5), sent(5.5), received()},
            {spent(1, 2, 3), unspent(1), sent(5), received()},
            {spent(1, 2, 3), unspent(1.2), sent(4.8), received()},
            {spent(1, 1, 1, 1, 1), unspent(2), sent(3), received()},
            {spent(1, 1, 1, 1, 1), unspent(2, 2), sent(1), received()},
            {spent(1, 1, 1, 1, 1), unspent(4.99), sent(0.01), received()},
            {spent(1), unspent(1), sent(1), received(1)},
            {spent(1, 1, 1, 1, 1), unspent(5.1), sent(5), received(5.1)},
            {spent(1000), unspent(999, 15), sent(1), received(15)},
            {spent(1000), unspent(15, 999), sent(1), received(15)},
            {spent(1, 1, 1, 1, 1), unspent(9, 3, 1), sent(1), received(9)},
            {spent(10, 10), unspent(5, 9, 3, 7, 1), sent(1), received(1, 5)},

            // Zero cases
            {spent(0), unspent(), sent(), received()},
            {spent(), unspent(0), sent(), received()},
            {spent(0), unspent(0), sent(), received()},
            {spent(1), unspent(0, 0.2), sent(0.8), received()},
            });
  }

  @Test
  public void received_transactions_value_and_order() {
    TransactionReconstruction estimate = TransactionReconstruction.estimateBlockLevelActivity(spentTransactionOutputs.getMoney(), unspentTransactionOutputs.getMoney());

    assertEquals(expectedReceivedTransactions.getMoney(), toValueList(estimate.received()));
    assertTrue(Stream.of(estimate.received()).allMatch(t -> t.getDirection() == Direction.RECEIVED));
  }

  @Test
  public void sent_transactions_value() {
    TransactionReconstruction estimate = TransactionReconstruction.estimateBlockLevelActivity(spentTransactionOutputs.getMoney(), unspentTransactionOutputs.getMoney());

    assertEquals(expectedSentTransactions.getMoney(), toValueList(estimate.sent()));
    assertTrue(Stream.of(estimate.sent()).allMatch(t -> t.getDirection() == Direction.SENT));
  }

  @Test
  public void total_in_matches_total_out() {
    Money net = unspentTransactionOutputs.sum().subtract(spentTransactionOutputs.sum());

    TransactionReconstruction estimate = TransactionReconstruction.estimateBlockLevelActivity(spentTransactionOutputs.getMoney(), unspentTransactionOutputs.getMoney());

    Money transactionIn   = Money.MobileCoin.sum(toValueList(estimate.received()));
    Money transactionOut  = Money.MobileCoin.sum(toValueList(estimate.sent()));
    Money netTransactions = transactionIn.subtract(transactionOut);

    assertEquals(net, netTransactions);
  }

  @Test
  public void sum_of_all_transactions() {
    Money net = unspentTransactionOutputs.sum().subtract(spentTransactionOutputs.sum());

    TransactionReconstruction estimate = TransactionReconstruction.estimateBlockLevelActivity(spentTransactionOutputs.getMoney(), unspentTransactionOutputs.getMoney());

    Money netValueWithDirections = Money.MobileCoin.sum(toValueListWithDirection(estimate.getAllTransactions()));

    assertEquals(net, netValueWithDirections);
  }

  @Test
  public void all_transaction_order() {
    TransactionReconstruction estimate = TransactionReconstruction.estimateBlockLevelActivity(spentTransactionOutputs.getMoney(), unspentTransactionOutputs.getMoney());

    assertEquals(sort(estimate.getAllTransactions()), estimate.getAllTransactions());
  }

  private static @NonNull List<TransactionReconstruction.Transaction> sort(@NonNull List<TransactionReconstruction.Transaction> transactions) {
    return Stream.of(transactions)
                 .sorted((o1, o2) -> {
                   if (o1.getDirection() != o2.getDirection()) {
                     if (o1.getDirection() == Direction.RECEIVED) {
                       return -1;
                     } else {
                       return 1;
                     }
                   }
                   return o1.getValue().toPicoMobBigInteger().compareTo(o2.getValue().toPicoMobBigInteger());
                 })
                 .toList();
  }

  private static List<Money.MobileCoin> toValueList(List<TransactionReconstruction.Transaction> received) {
    return Stream.of(received)
                 .map(TransactionReconstruction.Transaction::getValue)
                 .toList();
  }

  private static List<Money.MobileCoin> toValueListWithDirection(List<TransactionReconstruction.Transaction> received) {
    return Stream.of(received)
                 .map(TransactionReconstruction.Transaction::getValueWithDirection)
                 .toList();
  }

  abstract static class MoneyList {

    private final List<Money.MobileCoin> money;

    MoneyList(List<Money.MobileCoin> money) {
      this.money = money;
    }

    List<Money.MobileCoin> getMoney() {
      return money;
    }

    Money.MobileCoin sum() {
      return Money.MobileCoin.sum(money);
    }

    @Override
    public @NonNull String toString() {
      return "[" + Stream.of(money)
                         .map(f -> f.toString(MONEY_FORMATTER))
                         .collect(Collectors.joining(", ")) +
             "]";
    }
  }

  static class SpentList extends MoneyList {
    SpentList(List<Money.MobileCoin> money) {
      super(money);
    }
  }

  static class UnspentList extends MoneyList {
    UnspentList(List<Money.MobileCoin> money) {
      super(money);
    }
  }

  static class SentList extends MoneyList {
    SentList(List<Money.MobileCoin> money) {
      super(money);
    }
  }

  static class ReceivedList extends MoneyList {
    ReceivedList(List<Money.MobileCoin> money) {
      super(money);
    }
  }

  private static SpentList spent(Object... mob) {
    return new SpentList(toMobileCoinList(mob));
  }

  private static UnspentList unspent(Object... mob) {
    return new UnspentList(toMobileCoinList(mob));
  }

  private static SentList sent(Object... mob) {
    return new SentList(toMobileCoinList(mob));
  }

  private static ReceivedList received(Object... mob) {
    return new ReceivedList(toMobileCoinList(mob));
  }

  private static List<Money.MobileCoin> toMobileCoinList(Object[] mob) {
    return Stream.of(mob)
                 .map(value -> Money.mobileCoin(BigDecimal.valueOf(Double.parseDouble(value.toString()))))
                 .toList();
  }
}
