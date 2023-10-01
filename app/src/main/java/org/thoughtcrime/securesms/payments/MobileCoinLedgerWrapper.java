package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.payments.proto.MobileCoinLedger;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import okio.ByteString;

public final class MobileCoinLedgerWrapper {

  private final MobileCoinLedger ledger;
  private final Balance          balance;

  public MobileCoinLedgerWrapper(@NonNull MobileCoinLedger ledger) {
    Money.MobileCoin fullAmount         = Money.picoMobileCoin(ledger.balance);
    Money.MobileCoin transferableAmount = Money.picoMobileCoin(ledger.transferableBalance);

    this.ledger  = ledger;
    this.balance = new Balance(fullAmount, transferableAmount, ledger.asOfTimeStamp);
  }

  public @NonNull Balance getBalance() {
    return balance;
  }

  public byte[] serialize() {
    return ledger.encode();
  }

  public @NonNull List<OwnedTxo> getAllTxos() {
    List<OwnedTxo> txoList = new ArrayList<>(ledger.spentTxos.size() + ledger.unspentTxos.size());
    addAllMapped(txoList, ledger.spentTxos);
    addAllMapped(txoList, ledger.unspentTxos);
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
      return Money.picoMobileCoin(ownedTXO.amount);
    }

    public @NonNull ByteString getKeyImage() {
      return ownedTXO.keyImage;
    }

    public @NonNull ByteString getPublicKey() {
      return ownedTXO.publicKey;
    }

    public long getReceivedInBlock() {
      return ownedTXO.receivedInBlock != null ? ownedTXO.receivedInBlock.blockNumber : 0;
    }

    public @Nullable Long getSpentInBlock() {
      return ownedTXO.spentInBlock != null ? nullIfZero(ownedTXO.spentInBlock.blockNumber) : null;
    }

    public boolean isSpent() {
      return ownedTXO.spentInBlock != null && ownedTXO.spentInBlock.blockNumber != 0;
    }

    public @Nullable Long getReceivedInBlockTimestamp() {
      return ownedTXO.receivedInBlock != null ? nullIfZero(ownedTXO.receivedInBlock.timestamp) : null;
    }

    public @Nullable Long getSpentInBlockTimestamp() {
      return ownedTXO.spentInBlock != null ? nullIfZero(ownedTXO.spentInBlock.timestamp) : null;
    }

    private @Nullable Long nullIfZero(long value) {
      return value == 0 ? null : value;
    }
  }
}
