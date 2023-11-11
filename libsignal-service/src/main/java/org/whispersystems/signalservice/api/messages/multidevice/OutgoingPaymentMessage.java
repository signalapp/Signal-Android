package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.signalservice.api.payments.Money;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.List;
import java.util.Optional;

import okio.ByteString;

public final class OutgoingPaymentMessage {

  private final Optional<ServiceId> recipient;
  private final Money.MobileCoin    amount;
  private final Money.MobileCoin    fee;
  private final ByteString          receipt;
  private final long                blockIndex;
  private final long                blockTimestamp;
  private final Optional<byte[]>    address;
  private final Optional<String>    note;
  private final List<ByteString>    publicKeys;
  private final List<ByteString>    keyImages;

  public OutgoingPaymentMessage(Optional<ServiceId> recipient,
                                Money.MobileCoin amount,
                                Money.MobileCoin fee,
                                ByteString receipt,
                                long blockIndex,
                                long blockTimestamp,
                                Optional<byte[]> address,
                                Optional<String> note,
                                List<ByteString> publicKeys,
                                List<ByteString> keyImages)
  {
    this.recipient      = recipient;
    this.amount         = amount;
    this.fee            = fee;
    this.receipt        = receipt;
    this.blockIndex     = blockIndex;
    this.blockTimestamp = blockTimestamp;
    this.address        = address;
    this.note           = note;
    this.publicKeys     = publicKeys;
    this.keyImages      = keyImages;
  }

  public Optional<ServiceId> getRecipient() {
    return recipient;
  }

  public Money.MobileCoin getAmount() {
    return amount;
  }

  public ByteString getReceipt() {
    return receipt;
  }

  public Money.MobileCoin getFee() {
    return fee;
  }

  public long getBlockIndex() {
    return blockIndex;
  }

  public long getBlockTimestamp() {
    return blockTimestamp;
  }

  public Optional<byte[]> getAddress() {
    return address;
  }

  public Optional<String> getNote() {
    return note;
  }

  public List<ByteString> getPublicKeys() {
    return publicKeys;
  }

  public List<ByteString> getKeyImages() {
    return keyImages;
  }
}

