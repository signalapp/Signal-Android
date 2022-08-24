package org.thoughtcrime.securesms.payments.confirm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.money.FiatMoney;
import org.thoughtcrime.securesms.payments.Payee;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.UUID;

public class ConfirmPaymentState {
  private final Payee     payee;
  private final Money     balance;
  private final Money     amount;
  private final String    note;
  private final Money     fee;
  private final FeeStatus feeStatus;
  private final FiatMoney exchange;
  private final Status    status;
  private final Money     total;
  private final UUID      paymentId;

  public ConfirmPaymentState(@NonNull Payee payee,
                             @NonNull Money amount,
                             @Nullable String note)
  {
    this(payee,
         amount.toZero(),
         amount,
         note,
         amount.toZero(),
         FeeStatus.STILL_LOADING,
         null,
         Status.CONFIRM,
         null);
  }

  private ConfirmPaymentState(@NonNull Payee payee,
                             @NonNull Money balance,
                             @NonNull Money amount,
                             @Nullable String note,
                             @NonNull Money fee,
                             @NonNull FeeStatus feeStatus,
                             @NonNull FiatMoney exchange,
                             @NonNull Status status,
                             @Nullable UUID paymentId)
  {
    this.payee     = payee;
    this.balance   = balance;
    this.amount    = amount;
    this.note      = note;
    this.fee       = fee;
    this.feeStatus = feeStatus;
    this.exchange  = exchange;
    this.status    = status;
    this.paymentId = paymentId;
    this.total     = amount.add(fee);
  }

  public @NonNull Payee getPayee() {
    return payee;
  }

  public @NonNull Money getBalance() {
    return balance;
  }

  public @NonNull Money getAmount() {
    return amount;
  }

  public @Nullable String getNote() {
    return note;
  }

  public @NonNull Money getFee() {
    return fee;
  }

  public @NonNull FeeStatus getFeeStatus() {
    return feeStatus;
  }

  public @Nullable FiatMoney getExchange() {
    return exchange;
  }

  public @NonNull Status getStatus() {
    return status;
  }

  public @NonNull Money getTotal() {
    return total;
  }

  public @Nullable UUID getPaymentId() {
    return paymentId;
  }

  public @NonNull ConfirmPaymentState updateStatus(@NonNull Status status) {
    return new ConfirmPaymentState(this.payee, this.balance, this.amount, this.note, this.fee, this.feeStatus, this.exchange, status, this.paymentId);
  }

  public @NonNull ConfirmPaymentState updateBalance(@NonNull Money balance) {
    return new ConfirmPaymentState(this.payee, balance, this.amount, this.note, this.fee, this.feeStatus, this.exchange, this.status, this.paymentId);
  }

  public @NonNull ConfirmPaymentState updateFee(@NonNull Money fee) {
    return new ConfirmPaymentState(this.payee, this.balance, this.amount, this.note, fee, FeeStatus.SET, this.exchange, this.status, this.paymentId);
  }

  public @NonNull ConfirmPaymentState updateFeeStillLoading() {
    return new ConfirmPaymentState(this.payee, this.balance, this.amount, this.note, this.amount.toZero(), FeeStatus.STILL_LOADING, this.exchange, this.status, this.paymentId);
  }

  public @NonNull ConfirmPaymentState updateFeeError() {
    return new ConfirmPaymentState(this.payee, this.balance, this.amount, this.note, this.amount.toZero(), FeeStatus.ERROR, this.exchange, this.status, this.paymentId);
  }

  public @NonNull ConfirmPaymentState updatePaymentId(@Nullable UUID paymentId) {
    return new ConfirmPaymentState(this.payee, this.balance, this.amount, this.note, this.fee, this.feeStatus, this.exchange, this.status, paymentId);
  }

  public @NonNull ConfirmPaymentState updateExchange(@Nullable FiatMoney exchange) {
    return new ConfirmPaymentState(this.payee, this.balance, this.amount, this.note, this.fee, this.feeStatus, exchange, this.status, this.paymentId);
  }

  public @NonNull ConfirmPaymentState timeout() {
    return new ConfirmPaymentState(this.payee, this.balance, this.amount, this.note, this.fee, this.feeStatus, this.exchange, Status.TIMEOUT, this.paymentId);
  }

  enum Status {
    CONFIRM,
    SUBMITTING,
    PROCESSING,
    DONE,
    ERROR,
    TIMEOUT;

    boolean isTerminalStatus() {
      return this == DONE || this == ERROR || this == TIMEOUT;
    }
  }
  
  enum FeeStatus {
    NOT_SET,
    STILL_LOADING,
    SET,
    ERROR
  }
}
