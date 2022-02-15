package org.thoughtcrime.securesms.payments.preferences.model;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.payments.preferences.PaymentsHomeState;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;

public class IntroducingPayments implements MappingModel<IntroducingPayments> {
  private PaymentsHomeState.PaymentsState paymentsState;

  public IntroducingPayments(@NonNull PaymentsHomeState.PaymentsState paymentsState) {
    this.paymentsState = paymentsState;
  }

  @Override
  public boolean areItemsTheSame(@NonNull IntroducingPayments newItem) {
    return true;
  }

  @Override
  public boolean areContentsTheSame(@NonNull IntroducingPayments newItem) {
    return this.paymentsState == newItem.paymentsState;
  }

  public boolean isActivating() {
    return paymentsState == PaymentsHomeState.PaymentsState.ACTIVATING;
  }
}
