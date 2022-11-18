package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.payments.proto.PaymentMetaData;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.UUID;

public abstract class PaymentDecorator implements Payment {

  private final Payment inner;

  public PaymentDecorator(@NonNull Payment inner) {
    this.inner = inner;
  }

  public @NonNull Payment getInner() {
    return inner;
  }

  @Override
  public @NonNull UUID getUuid() {
    return inner.getUuid();
  }

  @Override
  public @NonNull Payee getPayee() {
    return inner.getPayee();
  }

  @Override
  public long getBlockIndex() {
    return inner.getBlockIndex();
  }

  @Override
  public long getBlockTimestamp() {
    return inner.getBlockTimestamp();
  }

  @Override
  public long getTimestamp() {
    return inner.getTimestamp();
  }

  @Override
  public @NonNull Direction getDirection() {
    return inner.getDirection();
  }

  @Override
  public @NonNull State getState() {
    return inner.getState();
  }

  @Override
  public @Nullable FailureReason getFailureReason() {
    return inner.getFailureReason();
  }

  @Override
  public @NonNull String getNote() {
    return inner.getNote();
  }

  @Override
  public @NonNull Money getAmount() {
    return inner.getAmount();
  }

  @Override
  public @NonNull Money getFee() {
    return inner.getFee();
  }

  @Override
  public @NonNull PaymentMetaData getPaymentMetaData() {
    return inner.getPaymentMetaData();
  }

  @Override
  public boolean isSeen() {
    return inner.isSeen();
  }
}
