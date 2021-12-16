package org.thoughtcrime.securesms.payments.preferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.payments.Payment;
import org.thoughtcrime.securesms.payments.preferences.model.PaymentItem;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.List;

final class PaymentsPagerItemViewModel extends ViewModel {

  private final LiveData<MappingModelList> list;

  PaymentsPagerItemViewModel(@NonNull PaymentCategory paymentCategory, @NonNull PaymentsRepository paymentsRepository) {
    LiveData<List<Payment>> payments;

    switch (paymentCategory) {
      case ALL:
        payments = paymentsRepository.getRecentPayments();
        break;
      case SENT:
        payments = paymentsRepository.getRecentSentPayments();
        break;
      case RECEIVED:
        payments = paymentsRepository.getRecentReceivedPayments();
        break;
      default:
        throw new IllegalArgumentException();
    }

    this.list = LiveDataUtil.mapAsync(payments, PaymentItem::fromPayment);
  }

  @NonNull LiveData<MappingModelList> getList() {
    return list;
  }

  public static final class Factory implements ViewModelProvider.Factory {
    private final PaymentCategory paymentCategory;

    public Factory(@NonNull PaymentCategory paymentCategory) {
      this.paymentCategory = paymentCategory;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new PaymentsPagerItemViewModel(paymentCategory, new PaymentsRepository()));
    }
  }
}
