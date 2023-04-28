package org.thoughtcrime.securesms.payments.reconciliation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;
import com.google.protobuf.ByteString;

import org.junit.BeforeClass;
import org.junit.Test;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.util.ByteUtil;
import org.thoughtcrime.securesms.payments.Direction;
import org.thoughtcrime.securesms.payments.FailureReason;
import org.thoughtcrime.securesms.payments.MobileCoinLedgerWrapper;
import org.thoughtcrime.securesms.payments.Payee;
import org.thoughtcrime.securesms.payments.Payment;
import org.thoughtcrime.securesms.payments.State;
import org.thoughtcrime.securesms.payments.proto.MobileCoinLedger;
import org.thoughtcrime.securesms.payments.proto.PaymentMetaData;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.testutil.LogRecorder;
import org.whispersystems.signalservice.api.payments.Money;
import org.whispersystems.signalservice.api.util.Uint64RangeException;
import org.whispersystems.signalservice.api.util.Uint64Util;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public final class LedgerReconcileTest {

  @BeforeClass
  public static void setup() {
    Log.initialize(new LogRecorder());
  }

  @Test
  public void empty_lists() {
    List<Payment> payments = reconcile(Collections.emptyList(), new MobileCoinLedgerWrapper(MobileCoinLedger.getDefaultInstance()));

    assertEquals(Collections.emptyList(), payments);
  }

  @Test
  public void single_unspent_transaction_on_ledger_only() {
    MobileCoinLedger ledger = ledger(unspentTxo(mob(2.5), keyImage(2), publicKey(3), block(2)));

    List<Payment> payments = reconcile(Collections.emptyList(), new MobileCoinLedgerWrapper(ledger));

    assertEquals(1, payments.size());

    Payment payment = payments.get(0);

    assertEquals(mob(2.5), payment.getAmount());
    assertEquals(mob(2.5), payment.getAmountWithDirection());
    assertEquals(Direction.RECEIVED, payment.getDirection());
    assertEquals(mob(0), payment.getFee());
    assertEquals(Payee.UNKNOWN, payment.getPayee());
    assertEquals(State.SUCCESSFUL, payment.getState());
    assertEquals(UuidUtil.UNKNOWN_UUID, payment.getUuid());
    assertEquals("", payment.getNote());
  }

  @Test
  public void single_spent_transaction_on_ledger() {
    MobileCoinLedger ledger = ledger(spentTxo(mob(10), keyImage(1), publicKey(2), block(1), block(2)));

    List<Payment> payments = reconcile(Collections.emptyList(), new MobileCoinLedgerWrapper(ledger));

    assertEquals(2, payments.size());

    Payment payment1 = payments.get(0);
    assertEquals(mob(10), payment1.getAmount());
    assertEquals(mob(-10), payment1.getAmountWithDirection());
    assertEquals(Direction.SENT, payment1.getDirection());
    assertEquals(mob(0), payment1.getFee());
    assertEquals(Payee.UNKNOWN, payment1.getPayee());
    assertEquals(State.SUCCESSFUL, payment1.getState());
    assertEquals(UuidUtil.UNKNOWN_UUID, payment1.getUuid());
    assertEquals("", payment1.getNote());

    Payment payment2 = payments.get(1);

    assertEquals(mob(10), payment2.getAmount());
    assertEquals(mob(10), payment2.getAmountWithDirection());
    assertEquals(Direction.RECEIVED, payment2.getDirection());
    assertEquals(mob(0), payment2.getFee());
    assertEquals(Payee.UNKNOWN, payment2.getPayee());
    assertEquals(State.SUCCESSFUL, payment2.getState());
    assertEquals(UuidUtil.UNKNOWN_UUID, payment2.getUuid());
    assertEquals("", payment2.getNote());
  }

  @Test
  public void one_received_and_one_spent_transaction_on_ledger_only_different_blocks() {
    MobileCoinLedger ledger = ledger(spentTxo(mob(2.5), keyImage(1), publicKey(2), block(1), block(2)),
                                     unspentTxo(mob(1), keyImage(3), publicKey(4), block(3)));

    List<Payment> payments = reconcile(Collections.emptyList(), new MobileCoinLedgerWrapper(ledger));

    assertEquals(3, payments.size());

    assertEquals(mob(1), payments.get(0).getAmountWithDirection());
    assertEquals(mob(-2.5), payments.get(1).getAmountWithDirection());
    assertEquals(mob(2.5), payments.get(2).getAmountWithDirection());
  }

  @Test
  public void one_received_and_one_spent_transaction_on_ledger_only_same_block_is_treated_as_change() {
    MobileCoinLedger ledger = ledger(spentTxo(mob(2.5), keyImage(1), publicKey(2), block(1), block(2)),
                                     unspentTxo(mob(1), keyImage(3), publicKey(4), block(2)));

    List<Payment> payments = reconcile(Collections.emptyList(), new MobileCoinLedgerWrapper(ledger));

    assertEquals(2, payments.size());

    assertEquals(mob(-1.5), payments.get(0).getAmountWithDirection());
    assertEquals(mob(2.5), payments.get(1).getAmountWithDirection());
  }

  @Test
  public void single_spend_payment_that_can_be_found_on_ledger_reconstructed_receipt() {
    List<Payment> localPayments = Collections.singletonList(payment("sent", mob(-1.5), keyImages(5), publicKeys(4)));

    MobileCoinLedger ledger = ledger(spentTxo(mob(2.5), keyImage(5), publicKey(2), block(1), block(2)),
                                     unspentTxo(mob(1), keyImage(3), publicKey(4), block(2)));

    List<Payment> payments = reconcile(localPayments, new MobileCoinLedgerWrapper(ledger));

    assertEquals(2, payments.size());

    assertEquals(mob(-1.5), payments.get(0).getAmountWithDirection());
    assertEquals(mob(2.5), payments.get(1).getAmountWithDirection());
  }

  @Test
  public void single_receipt_payment_that_can_be_found_on_ledger_reconstructed_spend() {
    List<Payment> localPayments = Collections.singletonList(payment("received", mob(2.5), keyImages(6), publicKeys(2)));

    MobileCoinLedger ledger = ledger(spentTxo(mob(2.5), keyImage(5), publicKey(2), block(1), block(2)));

    List<Payment> payments = reconcile(localPayments, new MobileCoinLedgerWrapper(ledger));

    assertEquals(2, payments.size());

    assertEquals(mob(-2.5), payments.get(0).getAmountWithDirection());
    assertEquals(mob(2.5), payments.get(1).getAmountWithDirection());
    assertEquals(2, payments.get(0).getBlockIndex());
    assertEquals("received", payments.get(1).getNote());
    assertEquals(1, payments.get(1).getBlockIndex());
  }

  @Test
  public void single_receipt_payment_that_can_be_found_on_ledger_reconstructed_spend_with_change() {
    List<Payment> localPayments = Collections.singletonList(payment("received", mob(2.5), keyImages(6), publicKeys(2)));

    MobileCoinLedger ledger = ledger(spentTxo(mob(2.5), keyImage(5), publicKey(2), block(1), block(2)),
                                     unspentTxo(mob(1.5), keyImage(7), publicKey(8), block(2)));

    List<Payment> payments = reconcile(localPayments, new MobileCoinLedgerWrapper(ledger));

    assertEquals(Arrays.asList(mob(-1), mob(2.5)), Stream.of(payments).map(Payment::getAmountWithDirection).toList());
    assertEquals("received", payments.get(1).getNote());
  }

  @Test
  public void single_receipt_payment_that_can_be_found_on_ledger_reconstructed_spend_but_not_with_change_due_to_block() {
    List<Payment> localPayments = Collections.singletonList(payment("received", mob(2.5), keyImages(6), publicKeys(2)));

    MobileCoinLedger ledger = ledger(spentTxo(mob(2.5), keyImage(5), publicKey(2), block(1), block(2)),
                                     unspentTxo(mob(1.5), keyImage(7), publicKey(8), block(3)));

    List<Payment> payments = reconcile(localPayments, new MobileCoinLedgerWrapper(ledger));

    assertEquals(Arrays.asList(mob(1.5), mob(-2.5), mob(2.5)), Stream.of(payments).map(Payment::getAmountWithDirection).toList());
    assertEquals("received", payments.get(2).getNote());
  }

  @Test
  public void unknown_payment_is_first_in_list() {
    List<Payment> localPayments = Collections.singletonList(payment("received", mob(2.5), keyImages(16), publicKeys(12)));

    MobileCoinLedger ledger = ledger(spentTxo(mob(2.5), keyImage(5), publicKey(2), block(1), block(2)),
                                     unspentTxo(mob(1.5), keyImage(7), publicKey(8), block(3)));

    List<Payment> payments = reconcile(localPayments, new MobileCoinLedgerWrapper(ledger));

    assertEquals(Arrays.asList(mob(2.5), mob(1.5), mob(-2.5), mob(2.5)), Stream.of(payments).map(Payment::getAmountWithDirection).toList());
    assertEquals("received", payments.get(0).getNote());
  }

  @Test
  public void unmatched_payment_remains_in_place_behind_matched() {
    List<Payment> localPayments = Arrays.asList(payment("matched payment", mob(10), keyImages(6), publicKeys(2)),
                                                payment("unmatched payment", mob(20), keyImages(16), publicKeys(12)));

    MobileCoinLedger ledger = ledger(spentTxo(mob(2.5), keyImage(5), publicKey(2), block(1), block(2)),
                                     unspentTxo(mob(1.5), keyImage(7), publicKey(8), block(3)));

    List<Payment> payments = reconcile(localPayments, new MobileCoinLedgerWrapper(ledger));

    assertEquals(Arrays.asList(mob(1.5), mob(-2.5), mob(10), mob(20)), Stream.of(payments).map(Payment::getAmountWithDirection).toList());
  }

  @Test
  public void unknown_payment_remains_in_place_behind_known_alternative_block_order() {
    List<Payment> localPayments = Arrays.asList(payment("matched payment", mob(10), keyImages(60), publicKeys(7)),
                                                payment("unmatched payment", mob(20), keyImages(16), publicKeys(12)));

    MobileCoinLedger ledger = ledger(spentTxo(mob(2.5), keyImage(5), publicKey(2), block(10), block(20)),
                                     unspentTxo(mob(10), keyImage(9), publicKey(7), block(15)));

    List<Payment> payments = reconcile(localPayments, new MobileCoinLedgerWrapper(ledger));

    assertEquals(Arrays.asList(20L, 15L, 0L, 10L), Stream.of(payments).map(Payment::getBlockIndex).toList());
    assertEquals(Arrays.asList(mob(-2.5), mob(10), mob(20), mob(2.5)), Stream.of(payments).map(Payment::getAmountWithDirection).toList());
  }

  private static @NonNull List<Payment> reconcile(@NonNull Collection<Payment> localPaymentTransactions,
                                                  @NonNull MobileCoinLedgerWrapper ledger)
  {
    return LedgerReconcile.reconcile(localPaymentTransactions, ledger);
  }

  private MobileCoinLedger.Block block(long blockIndex) {
    return MobileCoinLedger.Block.newBuilder()
                                 .setBlockNumber(blockIndex)
                                 .build();
  }

  private MobileCoinLedger ledger(MobileCoinLedger.OwnedTXO... txos) {
    MobileCoinLedger.Builder builder = MobileCoinLedger.newBuilder();
    for (MobileCoinLedger.OwnedTXO txo : txos) {
      builder.addUnspentTxos(txo);
    }
    return builder.build();
  }

  private MobileCoinLedger.OwnedTXO unspentTxo(Money.MobileCoin mob, ByteString keyImage, ByteString publicKey, MobileCoinLedger.Block receivedBlock) {
    return txo(mob, keyImage, publicKey, receivedBlock).build();
  }

  private MobileCoinLedger.OwnedTXO spentTxo(Money.MobileCoin mob, ByteString keyImage, ByteString publicKey, MobileCoinLedger.Block receivedBlock, MobileCoinLedger.Block spentBlock) {
    return txo(mob, keyImage, publicKey, receivedBlock).setSpentInBlock(spentBlock).build();
  }

  private MobileCoinLedger.OwnedTXO.Builder txo(Money.MobileCoin mob, ByteString keyImage, ByteString publicKey, MobileCoinLedger.Block receivedBlock) {
    if (mob.isNegative()) {
      throw new AssertionError();
    }
    MobileCoinLedger.OwnedTXO.Builder builder = MobileCoinLedger.OwnedTXO.newBuilder()
                                                                         .setReceivedInBlock(receivedBlock)
                                                                         .setKeyImage(keyImage)
                                                                         .setPublicKey(publicKey);
    try {
      builder.setAmount(Uint64Util.bigIntegerToUInt64(mob.toPicoMobBigInteger()));
    } catch (Uint64RangeException e) {
      throw new AssertionError(e);
    }
    return builder;
  }

  private static Payment payment(String note, Money.MobileCoin valueAndDirection, Set<ByteString> keyImages, Set<ByteString> publicKeys) {
    UUID uuid = UUID.randomUUID();

    PaymentMetaData.MobileCoinTxoIdentification.Builder builderForValue = PaymentMetaData.MobileCoinTxoIdentification.newBuilder();

    builderForValue.addAllKeyImages(keyImages);
    builderForValue.addAllPublicKey(publicKeys);

    PaymentMetaData paymentMetaData = PaymentMetaData.newBuilder()
                                                     .setMobileCoinTxoIdentification(builderForValue)
                                                     .build();

    return new Payment() {
      @Override
      public @NonNull UUID getUuid() {
        return uuid;
      }

      @Override
      public @NonNull Payee getPayee() {
        return new Payee(RecipientId.from(1));
      }

      @Override
      public long getBlockIndex() {
        return 0;
      }

      @Override
      public long getBlockTimestamp() {
        return 0;
      }

      @Override
      public long getTimestamp() {
        return 0;
      }

      @Override
      public @NonNull Direction getDirection() {
        return valueAndDirection.isNegative() ? Direction.SENT : Direction.RECEIVED;
      }

      @Override
      public @NonNull State getState() {
        return State.SUCCESSFUL;
      }

      @Override
      public @Nullable FailureReason getFailureReason() {
        return null;
      }

      @Override
      public @NonNull String getNote() {
        return note;
      }

      @Override
      public @NonNull Money getAmount() {
        return valueAndDirection.abs();
      }

      @Override
      public @NonNull Money getFee() {
        return getAmount().toZero();
      }

      @Override
      public @NonNull PaymentMetaData getPaymentMetaData() {
        return paymentMetaData;
      }

      @Override
      public boolean isSeen() {
        return true;
      }
    };
  }

  private static Set<ByteString> keyImages(long... ids) {
    Set<ByteString> idList = new HashSet<>(ids.length);
    for (long id : ids) {
      idList.add(keyImage(id));
    }
    return idList;
  }

  private static Set<ByteString> publicKeys(long... ids) {
    Set<ByteString> idList = new HashSet<>(ids.length);
    for (long id : ids) {
      idList.add(publicKey(id));
    }
    return idList;
  }

  private static ByteString keyImage(long id) {
    return id(0x7f00000000000000L | id);
  }

  private static ByteString publicKey(long id) {
    return id(0x0f00000000000000L | id);
  }

  private static ByteString id(long id) {
    byte[] bytes = ByteUtil.longToByteArray(id);
    return ByteString.copyFrom(bytes);
  }

  private static Money.MobileCoin mob(double value) {
    return Money.mobileCoin(BigDecimal.valueOf(value));
  }

}
