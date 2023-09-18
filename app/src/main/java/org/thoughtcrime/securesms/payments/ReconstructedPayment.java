package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.payments.proto.PaymentMetaData;
import org.whispersystems.signalservice.api.payments.Money;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.UUID;

public final class ReconstructedPayment implements Payment {

  private final long      blockIndex;
  private final long      blockTimestamp;
  private final Direction direction;
  private final Money     amount;

  public ReconstructedPayment(long blockIndex,
                              long blockTimestamp,
                              @NonNull Direction direction,
                              @NonNull Money amount)
  {
    this.blockIndex     = blockIndex;
    this.blockTimestamp = blockTimestamp;
    this.direction      = direction;
    this.amount         = amount;
  }

  @NonNull
  public @Override UUID getUuid() {
    return UuidUtil.UNKNOWN_UUID;
  }

  @Override
  public @NonNull Payee getPayee() {
    return Payee.UNKNOWN;
  }

  @Override
  public long getBlockIndex() {
    return blockIndex;
  }

  @Override
  public long getTimestamp() {
    return blockTimestamp;
  }

  @Override
  public long getBlockTimestamp() {
    return blockTimestamp;
  }

  @Override
  public @NonNull Direction getDirection() {
    return direction;
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
    return "";
  }

  @Override
  public @NonNull Money getAmount() {
    return amount;
  }

  @Override
  public @NonNull Money getFee() {
    return amount.toZero();
  }

  @Override
  public @NonNull PaymentMetaData getPaymentMetaData() {
    return new PaymentMetaData();
  }

  @Override
  public boolean isSeen() {
    return true;
  }
}
