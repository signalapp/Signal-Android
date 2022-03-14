package org.thoughtcrime.securesms.payments.preferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.money.FiatMoney;
import org.thoughtcrime.securesms.payments.currency.CurrencyExchange;
import org.thoughtcrime.securesms.payments.preferences.model.PaymentItem;

import java.util.Collections;
import java.util.List;

public class PaymentsHomeState {
  private final PaymentsState     paymentsState;
  private final FiatMoney         exchangeAmount;
  private final List<PaymentItem> requests;
  private final List<PaymentItem> payments;
  private final int               totalPayments;
  private final CurrencyExchange  currencyExchange;
  private final LoadState         exchangeRateLoadState;
  private final boolean           recentPaymentsLoaded;

  public PaymentsHomeState(@NonNull PaymentsState paymentsState) {
    this(paymentsState,
         null,
         Collections.emptyList(),
         Collections.emptyList(),
         0,
         new CurrencyExchange(Collections.emptyMap(), 0),
         LoadState.INITIAL,
         false);
  }

  public PaymentsHomeState(@NonNull PaymentsState paymentsState,
                           @Nullable FiatMoney exchangeAmount,
                           @NonNull List<PaymentItem> requests,
                           @NonNull List<PaymentItem> payments,
                           int totalPayments,
                           @NonNull CurrencyExchange currencyExchange,
                           @NonNull LoadState exchangeRateLoadState,
                           boolean recentPaymentsLoaded)
  {
    this.paymentsState         = paymentsState;
    this.exchangeAmount        = exchangeAmount;
    this.requests              = requests;
    this.payments              = payments;
    this.totalPayments         = totalPayments;
    this.currencyExchange      = currencyExchange;
    this.exchangeRateLoadState = exchangeRateLoadState;
    this.recentPaymentsLoaded  = recentPaymentsLoaded;
  }

  public @NonNull PaymentsState getPaymentsState() {
    return paymentsState;
  }

  public @Nullable FiatMoney getExchangeAmount() {
    return exchangeAmount;
  }

  public @NonNull List<PaymentItem> getRequests() {
    return requests;
  }

  public @NonNull List<PaymentItem> getPayments() {
    return payments;
  }

  public int getTotalPayments() {
    return totalPayments;
  }

  public @NonNull CurrencyExchange getCurrencyExchange() {
    return currencyExchange;
  }

  public @NonNull LoadState getExchangeRateLoadState() {
    return exchangeRateLoadState;
  }

  public boolean isRecentPaymentsLoaded() {
    return recentPaymentsLoaded;
  }

  public @NonNull PaymentsHomeState updatePaymentsEnabled(@NonNull PaymentsState paymentsEnabled) {
    return new PaymentsHomeState(paymentsEnabled,
                                 this.exchangeAmount,
                                 this.requests,
                                 this.payments,
                                 this.totalPayments,
                                 this.currencyExchange,
                                 this.exchangeRateLoadState,
                                 this.recentPaymentsLoaded);
  }

  public @NonNull PaymentsHomeState updatePayments(@NonNull List<PaymentItem> payments, int totalPayments) {
    return new PaymentsHomeState(this.paymentsState,
                                 this.exchangeAmount,
                                 this.requests,
                                 payments,
                                 totalPayments,
                                 this.currencyExchange,
                                 this.exchangeRateLoadState,
                                 true);
  }

  public @NonNull PaymentsHomeState updateCurrencyAmount(@Nullable FiatMoney exchangeAmount) {
    return new PaymentsHomeState(this.paymentsState,
                                 exchangeAmount,
                                 this.requests,
                                 this.payments,
                                 this.totalPayments,
                                 this.currencyExchange,
                                 this.exchangeRateLoadState,
                                 this.recentPaymentsLoaded);
  }

  public @NonNull PaymentsHomeState updateExchangeRateLoadState(@NonNull LoadState exchangeRateLoadState) {
    return new PaymentsHomeState(this.paymentsState,
                                 this.exchangeAmount,
                                 this.requests,
                                 this.payments,
                                 this.totalPayments,
                                 this.currencyExchange,
                                 exchangeRateLoadState,
                                 this.recentPaymentsLoaded);
  }

  public @NonNull PaymentsHomeState updateCurrencyExchange(@NonNull CurrencyExchange currencyExchange, @NonNull LoadState exchangeRateLoadState) {
    return new PaymentsHomeState(this.paymentsState,
                                 this.exchangeAmount,
                                 this.requests,
                                 this.payments,
                                 this.totalPayments,
                                 currencyExchange,
                                 exchangeRateLoadState,
                                 this.recentPaymentsLoaded);
  }

  public enum PaymentsState {
    NOT_ACTIVATED,
    ACTIVATING,
    ACTIVATED,
    DEACTIVATING,
    ACTIVATE_NOT_ALLOWED
  }
}
