package org.thoughtcrime.securesms.payments.preferences.model;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.payments.preferences.PaymentType;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;

public class SeeAll implements MappingModel<SeeAll> {

  private final PaymentType paymentType;

  public SeeAll(PaymentType paymentType) {
    this.paymentType = paymentType;
  }

  public @NonNull PaymentType getPaymentType() {
    return paymentType;
  }

  @Override
  public boolean areItemsTheSame(@NonNull SeeAll newItem) {
    return paymentType == newItem.paymentType;
  }

  @Override
  public boolean areContentsTheSame(@NonNull SeeAll newItem) {
    return areItemsTheSame(newItem);
  }
}
