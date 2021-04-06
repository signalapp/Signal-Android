package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.payments.proto.MobileCoinLedger;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

public final class MobileCoinLedgerWrapper {

  private final MobileCoinLedger ledger;
  private final Balance          balance;

  public MobileCoinLedgerWrapper(@NonNull MobileCoinLedger ledger) {
    Money.MobileCoin fullAmount         = Money.picoMobileCoin(ledger.getBalance());
    Money.MobileCoin transferableAmount = Money.picoMobileCoin(ledger.getTransferableBalance());

    this.ledger  = ledger;
    this.balance = new Balance(fullAmount, transferableAmount, ledger.getAsOfTimeStamp());
  }

  public @NonNull Balance getBalance() {
    return balance;
  }

  public byte[] serialize() {
    return ledger.toByteArray();
  }

  public @NonNull List<OwnedTxo> getAllTxos() {
    List<OwnedTxo> txoList = new ArrayList<>(ledger.getSpentTxosCount() + ledger.getUnspentTxosCount());
    addAllMapped(txoList, ledger.getSpentTxosList());
    addAllMapped(txoList, ledger.getUnspentTxosList());
    return txoList;
  }

  private static void addAllMapped(@NonNull List<OwnedTxo> output, @NonNull List<MobileCoinLedger.OwnedTXO> txosList) {
    for (MobileCoinLedger.OwnedTXO ownedTxo : txosList) {
      output.add(new OwnedTxo(ownedTxo));
    }
  }

  public static class OwnedTxo {
    private final MobileCoinLedger.OwnedTXO ownedTXO;

    OwnedTxo(MobileCoinLedger.OwnedTXO ownedTXO) {
      this.ownedTXO = ownedTXO;
    }

    public @NonNull Money.MobileCoin getValue() {
      return Money.picoMobileCoin(ownedTXO.getAmount());
    }

    public @NonNull ByteString getKeyImage() {
      return ownedTXO.getKeyImage();
    }

    public @NonNull ByteString getPublicKey() {
      return ownedTXO.getPublicKey();
    }

    public long getReceivedInBlock() {
      return ownedTXO.getReceivedInBlock().getBlockNumber();
    }

    public @Nullable Long getSpentInBlock() {
      return nullIfZero(ownedTXO.getSpentInBlock().getBlockNumber());
    }

    public boolean isSpent() {
      return ownedTXO.getSpentInBlock().getBlockNumber() != 0;
    }

    public @Nullable Long getReceivedInBlockTimestamp() {
      return nullIfZero(ownedTXO.getReceivedInBlock().getTimestamp());
    }

    public @Nullable Long getSpentInBlockTimestamp() {
      return nullIfZero(ownedTXO.getSpentInBlock().getTimestamp());
    }

    private @Nullable Long nullIfZero(long value) {
      return value == 0 ? null : value;
    }
  }
}
