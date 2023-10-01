package org.thoughtcrime.securesms.payments.reconciliation;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Stream;

import org.signal.core.util.MapUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.payments.MobileCoinLedgerWrapper;
import org.thoughtcrime.securesms.payments.Payment;
import org.thoughtcrime.securesms.payments.PaymentDecorator;
import org.thoughtcrime.securesms.payments.ReconstructedPayment;
import org.thoughtcrime.securesms.payments.State;
import org.thoughtcrime.securesms.payments.history.TransactionReconstruction;
import org.thoughtcrime.securesms.payments.proto.PaymentMetaData;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import okio.ByteString;

public final class LedgerReconcile {

  private static final String TAG = Log.tag(LedgerReconcile.class);

  @WorkerThread
  public static @NonNull List<Payment> reconcile(@NonNull Collection<? extends Payment> localPaymentTransactions,
                                                 @NonNull MobileCoinLedgerWrapper ledgerWrapper)
  {
    long start = System.currentTimeMillis();
    try {
      return reconcile(localPaymentTransactions, ledgerWrapper.getAllTxos());
    } finally {
      Log.d(TAG, String.format(Locale.US, "Took %d ms - Ledger %d, Local %d", System.currentTimeMillis() - start, ledgerWrapper.getAllTxos().size(), localPaymentTransactions.size()));
    }
  }

  @WorkerThread
  private static @NonNull List<Payment> reconcile(@NonNull Collection<? extends Payment> allLocalPaymentTransactions,
                                                  @NonNull List<MobileCoinLedgerWrapper.OwnedTxo> allTxOuts)
  {
    List<? extends Payment> nonFailedLocalPayments = Stream.of(allLocalPaymentTransactions).filter(i -> i.getState() != State.FAILED).toList();
    Set<ByteString>         allKnownPublicKeys     = new HashSet<>(nonFailedLocalPayments.size());
    Set<ByteString>         allKnownKeyImages      = new HashSet<>(nonFailedLocalPayments.size());

    for (Payment paymentTransaction : nonFailedLocalPayments) {
      PaymentMetaData.MobileCoinTxoIdentification txoIdentification = paymentTransaction.getPaymentMetaData().mobileCoinTxoIdentification;

      allKnownPublicKeys.addAll(txoIdentification.publicKey);
      allKnownKeyImages.addAll(txoIdentification.keyImages);
    }

    Set<MobileCoinLedgerWrapper.OwnedTxo> knownTxosByKeyImage = Stream.of(allTxOuts)
                                                                      .filter(t -> allKnownKeyImages.contains(t.getKeyImage()))
                                                                      .collect(Collectors.toSet());

    Set<MobileCoinLedgerWrapper.OwnedTxo> knownTxosByPublicKeys = Stream.of(allTxOuts)
                                                                        .filter(t -> allKnownPublicKeys.contains(t.getPublicKey()))
                                                                        .collect(Collectors.toSet());

    // any TXO that we can't pair up the pub key for, we don't have detail for how it got into the account
    Set<MobileCoinLedgerWrapper.OwnedTxo> unknownTxOutsReceived = new HashSet<>(allTxOuts);
    unknownTxOutsReceived.removeAll(knownTxosByPublicKeys);

    // any TXO that we can't pair up the keyimage for, we don't have detail for how it got spent
    Set<MobileCoinLedgerWrapper.OwnedTxo> unknownTxOutsSpent = Stream.of(allTxOuts).filter(MobileCoinLedgerWrapper.OwnedTxo::isSpent).collect(Collectors.toSet());
    unknownTxOutsSpent.removeAll(knownTxosByKeyImage);

    if (unknownTxOutsReceived.isEmpty() && unknownTxOutsSpent.isEmpty()) {
      return Stream.of(allLocalPaymentTransactions).map(t -> (Payment) t).toList();
    }

    List<DetailedTransaction> detailedTransactions = reconstructAllTransactions(unknownTxOutsReceived, unknownTxOutsSpent);

    List<Payment> reconstructedPayments = new ArrayList<>(detailedTransactions.size());

    List<Payment> blockDecoratedLocalPayments = decoratePaymentsWithBlockIndexes(allLocalPaymentTransactions, allTxOuts);

    for (DetailedTransaction detailedTransaction : detailedTransactions) {
      reconstructedPayments.add(new ReconstructedPayment(detailedTransaction.blockDetail.getBlockIndex(),
                                                         detailedTransaction.blockDetail.getBlockTimestampOrZero(),
                                                         detailedTransaction.transaction.getDirection(),
                                                         detailedTransaction.transaction.getValue()));
    }

    Collections.sort(reconstructedPayments, Payment.DESCENDING_BLOCK_INDEX);

    return ZipList.zipList(blockDecoratedLocalPayments, reconstructedPayments, Payment.DESCENDING_BLOCK_INDEX_UNKNOWN_FIRST);
  }

  private static List<Payment> decoratePaymentsWithBlockIndexes(@NonNull Collection<? extends Payment> localPaymentTransactions,
                                                                @NonNull List<MobileCoinLedgerWrapper.OwnedTxo> allTxOuts)
  {
    List<Payment>                                     result         = new ArrayList<>(localPaymentTransactions.size());
    Map<ByteString, MobileCoinLedgerWrapper.OwnedTxo> blockDetailMap = new HashMap<>(allTxOuts.size() * 2);

    for (MobileCoinLedgerWrapper.OwnedTxo txo : allTxOuts) {
      blockDetailMap.put(txo.getPublicKey(), txo);
      blockDetailMap.put(txo.getKeyImage(), txo);
    }

    for (Payment local : localPaymentTransactions) {
      result.add(findBlock(local, blockDetailMap));
    }
    return result;
  }

  private static @NonNull Payment findBlock(@NonNull Payment local, @NonNull Map<ByteString, MobileCoinLedgerWrapper.OwnedTxo> allTxOuts) {
    if (local.getDirection().isReceived()) {
      for (ByteString publicKey : local.getPaymentMetaData().mobileCoinTxoIdentification.publicKey) {
        MobileCoinLedgerWrapper.OwnedTxo ownedTxo = allTxOuts.get(publicKey);

        if (ownedTxo != null) {
          long receivedInBlock          = ownedTxo.getReceivedInBlock();
          long receivedInBlockTimestamp = ownedTxo.getReceivedInBlockTimestamp() != null ? ownedTxo.getReceivedInBlockTimestamp() : 0L;

          return BlockOverridePayment.override(local, receivedInBlock, receivedInBlockTimestamp);
        }
      }
    } else {
      for (ByteString keyImage : local.getPaymentMetaData().mobileCoinTxoIdentification.keyImages) {
        MobileCoinLedgerWrapper.OwnedTxo ownedTxo = allTxOuts.get(keyImage);

        if (ownedTxo != null && ownedTxo.getSpentInBlock() != null) {
          long spentInBlock          = ownedTxo.getSpentInBlock();
          long spentInBlockTimestamp = ownedTxo.getSpentInBlockTimestamp() != null ? ownedTxo.getSpentInBlockTimestamp() : 0L;

          return BlockOverridePayment.override(local, spentInBlock, spentInBlockTimestamp);
        }
      }
    }
    return local;
  }

  public static class BlockDetail {

    public static final Comparator<BlockDetail> BLOCK_INDEX = (a, b) -> Long.compare(a.blockIndex, b.blockIndex);
    private final       long                    blockIndex;

    private final Long blockTimestamp;

    public BlockDetail(long blockIndex, @Nullable Long blockTimestamp) {
      this.blockIndex     = blockIndex;
      this.blockTimestamp = blockTimestamp;
    }

    public long getBlockTimestampOrZero() {
      return blockTimestamp == null ? 0 : blockTimestamp;
    }

    public long getBlockIndex() {
      return blockIndex;
    }
  }

  public static class DetailedTransaction {
    private static final Comparator<DetailedTransaction> BLOCK_INDEX = (a, b) -> BlockDetail.BLOCK_INDEX.compare(a.blockDetail, b.blockDetail);
    private static final Comparator<DetailedTransaction> TRANSACTION = (a, b) -> TransactionReconstruction.Transaction.ORDER.compare(a.transaction, b.transaction);
    public static final  Comparator<DetailedTransaction> ASCENDING   = ComparatorCompat.chain(BLOCK_INDEX)
                                                                                       .thenComparing(TRANSACTION);
    public static final  Comparator<DetailedTransaction> DESCENDING  = ComparatorCompat.reversed(ASCENDING);

    private final BlockDetail blockDetail;

    private final TransactionReconstruction.Transaction transaction;

    public DetailedTransaction(@NonNull BlockDetail blockDetail, @NonNull TransactionReconstruction.Transaction transaction) {
      this.blockDetail = blockDetail;
      this.transaction = transaction;
    }

  }

  private static @NonNull List<DetailedTransaction> reconstructAllTransactions(@NonNull Set<MobileCoinLedgerWrapper.OwnedTxo> unknownReceived, @NonNull Set<MobileCoinLedgerWrapper.OwnedTxo> unknownSpent) {
    Set<Long> allBlocksWithActivity = Stream.of(unknownReceived)
                                            .map(MobileCoinLedgerWrapper.OwnedTxo::getReceivedInBlock)
                                            .collect(Collectors.toSet());

    allBlocksWithActivity
            .addAll(Stream.of(unknownSpent)
                          .map(MobileCoinLedgerWrapper.OwnedTxo::getSpentInBlock)
                          .collect(Collectors.toSet()));

    Map<Long, List<MobileCoinLedgerWrapper.OwnedTxo>> receivedInBlock = Stream.of(unknownReceived)
                                                                              .collect(Collectors.groupingBy(MobileCoinLedgerWrapper.OwnedTxo::getReceivedInBlock));

    Map<Long, List<MobileCoinLedgerWrapper.OwnedTxo>> spentInBlock = Stream.of(unknownSpent)
                                                                           .filter(MobileCoinLedgerWrapper.OwnedTxo::isSpent)
                                                                           .collect(Collectors.groupingBy(MobileCoinLedgerWrapper.OwnedTxo::getSpentInBlock));

    return Stream.of(allBlocksWithActivity)
                 .sorted((a, b) -> b.compareTo(a))
                 .flatMap(blockIndex -> {
                   List<MobileCoinLedgerWrapper.OwnedTxo> unspent = MapUtil.getOrDefault(receivedInBlock, blockIndex, Collections.emptyList());
                   List<MobileCoinLedgerWrapper.OwnedTxo> spent   = MapUtil.getOrDefault(spentInBlock, blockIndex, Collections.emptyList());

                   if (spent.size() + unspent.size() == 0) {
                     throw new AssertionError();
                   }

                   Long timeStamp = null;
                   if (spent.size() > 0) {
                     timeStamp = spent.get(0).getSpentInBlockTimestamp();
                   }
                   if (timeStamp == null && unspent.size() > 0) {
                     timeStamp = unspent.get(0).getReceivedInBlockTimestamp();
                   }

                   TransactionReconstruction transactionReconstruction = TransactionReconstruction.estimateBlockLevelActivity(toMobileCoinList(spent), toMobileCoinList(unspent));

                   BlockDetail blockDetail = new BlockDetail(blockIndex, timeStamp);
                   return Stream.of(transactionReconstruction.getAllTransactions())
                                .map(t -> new DetailedTransaction(blockDetail, t));
                 })
                 .sorted(DetailedTransaction.DESCENDING)
                 .toList();
  }

  private static @NonNull List<Money.MobileCoin> toMobileCoinList(@NonNull List<MobileCoinLedgerWrapper.OwnedTxo> spent) {
    return Stream.of(spent)
                 .map(MobileCoinLedgerWrapper.OwnedTxo::getValue)
                 .toList();
  }

  public static class BlockOverridePayment extends PaymentDecorator {
    private final long blockIndex;
    private final long blockTimestamp;

    static Payment override(@NonNull Payment payment, long blockIndex, long blockTimestamp) {
      if (payment.getBlockTimestamp() == blockTimestamp && payment.getBlockIndex() == blockIndex) {
        return payment;
      } else {
        return new BlockOverridePayment(payment, blockIndex, blockTimestamp);
      }
    }

    private BlockOverridePayment(@NonNull Payment inner, long blockIndex, long blockTimestamp) {
      super(inner);
      this.blockIndex     = blockIndex;
      this.blockTimestamp = blockTimestamp;
    }

    @Override
    public long getBlockIndex() {
      return blockIndex;
    }

    @Override
    public long getBlockTimestamp() {
      return blockTimestamp != 0 ? blockTimestamp
                                 : super.getBlockTimestamp();
    }
  }
}
